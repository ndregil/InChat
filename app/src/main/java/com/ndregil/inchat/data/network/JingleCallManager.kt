package com.ndregil.inchat.data.network

import android.util.Log
import com.ndregil.inchat.data.model.CallInfo
import com.ndregil.inchat.data.model.CallState
import com.ndregil.inchat.data.model.ConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jivesoftware.smack.StanzaListener
import org.jivesoftware.smack.filter.StanzaTypeFilter
import org.jivesoftware.smack.iqrequest.AbstractIqRequestHandler
import org.jivesoftware.smack.iqrequest.IQRequestHandler
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.packet.MessageBuilder
import org.jivesoftware.smack.packet.StandardExtensionElement
import org.jivesoftware.smack.packet.XmlEnvironment
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.Jid
import org.jxmpp.jid.impl.JidCreate
import java.util.UUID

/**
 * Jingle & XEP-0353 Call Manager for Voice ("Audio") and Video calls.
 * - Registers an IQRequestHandler for urn:xmpp:jingle:1 (IQ.Type.set) so Smack acknowledges
 *   incoming Jingle IQs with <iq type='result'/> instead of service-unavailable.
 * - Supports XEP-0353 (urn:xmpp:jingle-message:0) propose/proceed/reject/retract/accept
 *   to ring bare JIDs across devices and transition to Full JID Jingle sessions.
 * - Resolves target Full JID via Smack Roster presences for RFC-compliant Jingle IQ routing.
 */
class JingleCallManager(
    private val connectionManager: XmppConnectionManager,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "JingleCallManager"
        private const val NS_JINGLE = "urn:xmpp:jingle:1"
        private const val NS_JINGLE_RTP = "urn:xmpp:jingle:apps:rtp:1"
        private const val NS_JINGLE_MESSAGE = "urn:xmpp:jingle-message:0"
        private const val RING_TIMEOUT_MS = 45_000L

        private val ATTR_ACTION_REGEX = Regex("action=['\"]([^'\"]+)['\"]")
        private val ATTR_SID_REGEX = Regex("sid=['\"]([^'\"]+)['\"]")
        private val ATTR_MEDIA_REGEX = Regex("media=['\"]([^'\"]+)['\"]")
    }

    private val _currentCall = MutableStateFlow<CallInfo?>(null)
    val currentCall: StateFlow<CallInfo?> = _currentCall.asStateFlow()

    private var durationJob: Job? = null
    private var ringTimeoutJob: Job? = null

    @Volatile
    private var registeredConnection: XMPPTCPConnection? = null
    private var jingleIqHandler: AbstractIqRequestHandler? = null
    private var jingleMessageListener: StanzaListener? = null

    // Track whether current call has full JID and/or used XEP-0353 message initiation
    @Volatile
    private var activeFullPeerJid: Jid? = null
    @Volatile
    private var activeBarePeerJid: BareJid? = null
    @Volatile
    private var jingleSessionInitiated: Boolean = false

    init {
        scope.launch {
            connectionManager.connectionStatus.collect { status ->
                when (status) {
                    ConnectionStatus.CONNECTED -> setupListeners()
                    ConnectionStatus.DISCONNECTED, ConnectionStatus.ERROR -> {
                        val call = _currentCall.value
                        if (call != null && call.state != CallState.ENDED) {
                            finishCallWithStatus(call.callId, "Koneksi XMPP terputus")
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    @Synchronized
    private fun setupListeners() {
        val connection = connectionManager.getRawConnection() ?: return
        if (registeredConnection === connection && jingleIqHandler != null && jingleMessageListener != null) {
            return
        }

        // Remove previous handlers if reconnecting
        registeredConnection?.let { oldConn ->
            jingleIqHandler?.let { try { oldConn.unregisterIQRequestHandler(it) } catch (_: Exception) {} }
            jingleMessageListener?.let { try { oldConn.removeAsyncStanzaListener(it) } catch (_: Exception) {} }
        }

        val iqHandler = object : AbstractIqRequestHandler(
            "jingle",
            NS_JINGLE,
            IQ.Type.set,
            IQRequestHandler.Mode.async
        ) {
            override fun handleIQRequest(iqRequest: IQ): IQ {
                handleJingleStanza(iqRequest)
                return IQ.createResultIQ(iqRequest)
            }
        }

        val msgListener = StanzaListener { stanza ->
            val msg = stanza as? Message ?: return@StanzaListener
            handleJingleMessageStanza(msg)
        }

        connection.registerIQRequestHandler(iqHandler)
        connection.addAsyncStanzaListener(msgListener, StanzaTypeFilter(Message::class.java))

        registeredConnection = connection
        jingleIqHandler = iqHandler
        jingleMessageListener = msgListener
        Log.i(TAG, "Registered Jingle IQRequestHandler and XEP-0353 Message listener")
    }

    private fun handleJingleMessageStanza(msg: Message) {
        val ext = msg.extensions.firstOrNull { it.namespace == NS_JINGLE_MESSAGE } ?: return
        val xml = ext.toXML(XmlEnvironment.EMPTY).toString()
        val elemName = ext.elementName
        val sid = Regex("id=['\"]([^'\"]+)['\"]").find(xml)?.groupValues?.get(1) ?: return
        val fromJid = msg.from ?: return
        val fromBare = fromJid.asBareJid()

        Log.d(TAG, "Received XEP-0353 <$elemName id='$sid'> from $fromJid")

        when (elemName) {
            "propose" -> {
                val connection = connectionManager.getRawConnection()
                val myBare = connection?.user?.asBareJid()
                if (myBare != null && fromBare == myBare) {
                    // Own carbon from another device
                    return
                }
                if (_currentCall.value != null) {
                    // Already in a call -> send busy reject
                    sendJingleMessageAction(fromBare, "reject", sid)
                    return
                }
                val isVideo = ATTR_MEDIA_REGEX.find(xml)?.groupValues?.get(1)?.equals("video", ignoreCase = true) == true ||
                    xml.contains("video", ignoreCase = true)
                val callType = if (isVideo) "Video" else "Audio"
                activeFullPeerJid = if (fromJid.hasResource()) fromJid else null
                activeBarePeerJid = fromBare
                jingleSessionInitiated = false
                _currentCall.value = CallInfo(
                    callId = sid,
                    remoteJid = fromBare.toString(),
                    remoteNickname = fromBare.localpartOrNull?.asUnescapedString() ?: fromBare.toString(),
                    callType = callType,
                    state = CallState.INCOMING_RINGING,
                    isVideoEnabled = isVideo,
                    isSpeakerOn = isVideo,
                    statusDetail = "Panggilan masuk..."
                )
                startRingTimeout(sid)
            }
            "proceed" -> {
                val call = _currentCall.value
                if (call != null && call.callId == sid && call.state == CallState.OUTGOING_RINGING) {
                    activeFullPeerJid = fromJid
                    if (!jingleSessionInitiated) {
                        sendJingleSessionInitiate(fromJid, sid, call.callType)
                    }
                    _currentCall.value = call.copy(
                        state = CallState.CONNECTED,
                        startTime = System.currentTimeMillis(),
                        statusDetail = "Terhubung"
                    )
                    ringTimeoutJob?.cancel()
                    startDurationCounter()
                }
            }
            "accept" -> {
                val call = _currentCall.value
                if (call != null && call.callId == sid && call.state == CallState.INCOMING_RINGING) {
                    // Accepted from another device
                    finishCallWithStatus(sid, "Dijawab di perangkat lain")
                }
            }
            "reject", "retract" -> {
                val call = _currentCall.value
                if (call != null && call.callId == sid) {
                    val reasonText = if (elemName == "reject") "Panggilan ditolak" else "Panggilan dibatalkan"
                    finishCallWithStatus(sid, reasonText)
                }
            }
        }
    }

    private fun handleJingleStanza(iq: IQ) {
        val jingleXml = try {
            val childXml = iq.childElementXML?.toString()
            if (!childXml.isNullOrBlank()) childXml else iq.toXML(XmlEnvironment.EMPTY).toString()
        } catch (_: Exception) {
            iq.toXML(XmlEnvironment.EMPTY).toString()
        }

        val action = ATTR_ACTION_REGEX.find(jingleXml)?.groupValues?.get(1) ?: return
        val sid = ATTR_SID_REGEX.find(jingleXml)?.groupValues?.get(1) ?: return
        val fromJid = iq.from ?: return
        val fromBare = fromJid.asBareJid()

        Log.d(TAG, "Received Jingle IQ: action=$action, sid=$sid, from=$fromJid")

        when (action) {
            "session-initiate" -> {
                val existing = _currentCall.value
                if (existing != null && existing.callId == sid) {
                    // Already tracking this call (e.g. via XEP-0353 propose/proceed)
                    activeFullPeerJid = fromJid
                    jingleSessionInitiated = true
                    return
                }
                if (existing != null) {
                    sendTerminateToJid(fromJid, sid, "busy")
                    return
                }
                val isVideo = ATTR_MEDIA_REGEX.find(jingleXml)?.groupValues?.get(1)?.equals("video", ignoreCase = true) == true ||
                    jingleXml.contains("video", ignoreCase = true)
                val callType = if (isVideo) "Video" else "Audio"
                activeFullPeerJid = fromJid
                activeBarePeerJid = fromBare
                jingleSessionInitiated = true
                _currentCall.value = CallInfo(
                    callId = sid,
                    remoteJid = fromBare.toString(),
                    remoteNickname = fromBare.localpartOrNull?.asUnescapedString() ?: fromBare.toString(),
                    callType = callType,
                    state = CallState.INCOMING_RINGING,
                    isVideoEnabled = isVideo,
                    isSpeakerOn = isVideo,
                    statusDetail = "Panggilan masuk..."
                )
                startRingTimeout(sid)
            }
            "session-accept" -> {
                val call = _currentCall.value
                if (call != null && call.callId == sid) {
                    activeFullPeerJid = fromJid
                    ringTimeoutJob?.cancel()
                    _currentCall.value = call.copy(
                        state = CallState.CONNECTED,
                        startTime = System.currentTimeMillis(),
                        statusDetail = "Terhubung"
                    )
                    startDurationCounter()
                }
            }
            "session-terminate" -> {
                val call = _currentCall.value
                if (call != null && call.callId == sid) {
                    val detail = when {
                        jingleXml.contains("<decline") -> "Panggilan ditolak"
                        jingleXml.contains("<busy") -> "Sedang sibuk"
                        jingleXml.contains("<timeout") -> "Tidak ada jawaban"
                        else -> "Panggilan berakhir"
                    }
                    finishCallWithStatus(sid, detail)
                }
            }
        }
    }

    fun initiateCall(remoteJid: String, remoteNickname: String, callType: String): Result<Unit> {
        if (_currentCall.value != null) {
            return Result.failure(IllegalStateException("Sedang ada panggilan aktif."))
        }

        val connection = connectionManager.getRawConnection()
        if (connection == null || !connection.isAuthenticated) {
            return Result.failure(IllegalStateException("Belum terhubung ke server XMPP."))
        }

        setupListeners()

        val parsedJid = try {
            JidCreate.from(remoteJid.trim())
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Format JID tujuan tidak valid."))
        }

        val bareJid = parsedJid.asBareJid()
        val isVideo = callType.equals("Video", ignoreCase = true)
        val normalizedType = if (isVideo) "Video" else "Audio"
        val mediaTag = if (isVideo) "video" else "audio"

        // Resolve full JID from Roster if bare JID was passed
        val fullTargetJid: Jid? = if (parsedJid.hasResource()) {
            parsedJid
        } else {
            resolveBestFullJid(connection, bareJid)
        }

        val callId = UUID.randomUUID().toString()
        activeBarePeerJid = bareJid
        activeFullPeerJid = fullTargetJid
        jingleSessionInitiated = false

        val cleanNick = remoteNickname.trim().ifBlank {
            bareJid.localpartOrNull?.asUnescapedString() ?: bareJid.toString()
        }

        _currentCall.value = CallInfo(
            callId = callId,
            remoteJid = bareJid.toString(),
            remoteNickname = cleanNick,
            callType = normalizedType,
            state = CallState.OUTGOING_RINGING,
            isVideoEnabled = isVideo,
            isSpeakerOn = isVideo,
            statusDetail = if (fullTargetJid != null) "Menghubungi..." else "Memanggil perangkat kontak..."
        )

        startRingTimeout(callId)

        // 1. Send XEP-0353 <propose/> to bare JID so all online devices of peer ring
        sendJingleMessagePropose(bareJid, callId, mediaTag)

        // 2. If a full JID resource is online, also send RFC-compliant Jingle session-initiate IQ
        if (fullTargetJid != null) {
            sendJingleSessionInitiate(fullTargetJid, callId, normalizedType)
        }

        return Result.success(Unit)
    }

    private fun resolveBestFullJid(connection: XMPPTCPConnection, bareJid: BareJid): Jid? {
        return try {
            val roster = Roster.getInstanceFor(connection)
            val presences = roster.getPresences(bareJid).filter { it.isAvailable && it.from?.hasResource() == true }
            if (presences.isEmpty()) return null

            val sdm = ServiceDiscoveryManager.getInstanceFor(connection)
            // Prefer resource that advertises Jingle support if known
            val jingleCapable = presences.firstOrNull { presence ->
                try {
                    val info = org.jivesoftware.smackx.caps.EntityCapsManager.getDiscoverInfoByUser(presence.from)
                    info?.containsFeature(NS_JINGLE) == true || info?.containsFeature(NS_JINGLE_MESSAGE) == true
                } catch (_: Exception) {
                    false
                }
            }
            (jingleCapable ?: presences.maxByOrNull { it.priority })?.from
        } catch (e: Exception) {
            Log.w(TAG, "Failed resolving full JID for $bareJid: ${e.message}")
            null
        }
    }

    private fun sendJingleSessionInitiate(targetFullJid: Jid, callId: String, callType: String) {
        val connection = connectionManager.getRawConnection() ?: return
        val isVideo = callType.equals("Video", ignoreCase = true)
        val mediaTag = if (isVideo) "video" else "audio"
        val payloadName = if (isVideo) "VP8" else "OPUS"
        val clockRate = if (isVideo) "90000" else "48000"

        jingleSessionInitiated = true
        val innerXml = """
            <content creator='initiator' name='$mediaTag'>
                <description xmlns='$NS_JINGLE_RTP' media='$mediaTag'>
                    <payload-type id='96' name='$payloadName' clockrate='$clockRate'/>
                </description>
            </content>
        """.trimIndent()

        sendJingleIq(
            toJid = targetFullJid,
            action = "session-initiate",
            sid = callId,
            initiator = connection.user?.toString(),
            innerXml = innerXml,
            onError = { errMsg ->
                Log.w(TAG, "Jingle IQ session-initiate error from $targetFullJid: $errMsg")
            }
        )
    }

    private fun sendJingleMessagePropose(bareJid: BareJid, callId: String, mediaTag: String) {
        val connection = connectionManager.getRawConnection() ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val desc = StandardExtensionElement.builder("description", NS_JINGLE_RTP)
                    .addAttribute("media", mediaTag)
                    .build()
                val propose = StandardExtensionElement.builder("propose", NS_JINGLE_MESSAGE)
                    .addAttribute("id", callId)
                    .addElement(desc)
                    .build()
                val msg = MessageBuilder.buildMessage()
                    .ofType(Message.Type.chat)
                    .to(bareJid)
                    .addExtension(propose)
                    .build()
                connection.sendStanza(msg)
            } catch (e: Exception) {
                Log.w(TAG, "Failed sending XEP-0353 propose: ${e.message}")
            }
        }
    }

    private fun sendJingleMessageAction(targetJid: Jid, actionElement: String, callId: String) {
        val connection = connectionManager.getRawConnection() ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val ext = StandardExtensionElement.builder(actionElement, NS_JINGLE_MESSAGE)
                    .addAttribute("id", callId)
                    .build()
                val msg = MessageBuilder.buildMessage()
                    .ofType(Message.Type.chat)
                    .to(targetJid)
                    .addExtension(ext)
                    .build()
                connection.sendStanza(msg)
            } catch (e: Exception) {
                Log.w(TAG, "Failed sending XEP-0353 $actionElement: ${e.message}")
            }
        }
    }

    fun acceptCall() {
        val call = _currentCall.value ?: return
        val connection = connectionManager.getRawConnection()
        if (call.state != CallState.INCOMING_RINGING) return

        ringTimeoutJob?.cancel()

        val targetJid = activeFullPeerJid ?: try {
            JidCreate.from(call.remoteJid)
        } catch (_: Exception) {
            null
        }

        if (connection != null && connection.isAuthenticated && targetJid != null) {
            val mediaTag = if (call.callType.equals("Video", ignoreCase = true)) "video" else "audio"

            // Send XEP-0353 <proceed/> (for callers using Jingle Message Initiation)
            activeBarePeerJid?.let { bare ->
                sendJingleMessageAction(bare, "proceed", call.callId)
            }

            if (jingleSessionInitiated && targetJid.hasResource()) {
                val innerXml = """
                    <content creator='initiator' name='$mediaTag'>
                        <description xmlns='$NS_JINGLE_RTP' media='$mediaTag'/>
                    </content>
                """.trimIndent()
                sendJingleIq(
                    toJid = targetJid,
                    action = "session-accept",
                    sid = call.callId,
                    responder = connection.user?.toString(),
                    innerXml = innerXml
                )
            }
        }

        _currentCall.value = call.copy(
            state = CallState.CONNECTED,
            startTime = System.currentTimeMillis(),
            statusDetail = "Terhubung"
        )
        startDurationCounter()
    }

    fun rejectCall() {
        val call = _currentCall.value ?: return
        ringTimeoutJob?.cancel()
        durationJob?.cancel()
        activeBarePeerJid?.let { bare ->
            sendJingleMessageAction(bare, "reject", call.callId)
        }
        sendTerminate(call, "decline")
        activeFullPeerJid = null
        activeBarePeerJid = null
        jingleSessionInitiated = false
        _currentCall.value = null
    }

    fun endCall() {
        val call = _currentCall.value ?: return
        ringTimeoutJob?.cancel()
        durationJob?.cancel()
        if (call.state == CallState.OUTGOING_RINGING) {
            activeBarePeerJid?.let { bare ->
                sendJingleMessageAction(bare, "retract", call.callId)
            }
            sendTerminate(call, "cancel")
        } else {
            sendTerminate(call, "success")
        }
        finishCallWithStatus(call.callId, "Panggilan berakhir")
    }

    fun dismissEndedCall() {
        val call = _currentCall.value ?: return
        if (call.state == CallState.ENDED) {
            ringTimeoutJob?.cancel()
            durationJob?.cancel()
            activeFullPeerJid = null
            activeBarePeerJid = null
            jingleSessionInitiated = false
            _currentCall.value = null
        }
    }

    private fun finishCallWithStatus(callId: String, detail: String) {
        ringTimeoutJob?.cancel()
        durationJob?.cancel()
        val call = _currentCall.value ?: return
        if (call.callId != callId) return
        _currentCall.value = call.copy(
            state = CallState.ENDED,
            statusDetail = detail
        )
        activeFullPeerJid = null
        activeBarePeerJid = null
        jingleSessionInitiated = false
        scope.launch {
            delay(1800)
            if (_currentCall.value?.callId == callId && _currentCall.value?.state == CallState.ENDED) {
                _currentCall.value = null
            }
        }
    }

    private fun startRingTimeout(callId: String) {
        ringTimeoutJob?.cancel()
        ringTimeoutJob = scope.launch {
            delay(RING_TIMEOUT_MS)
            val call = _currentCall.value
            if (call != null && call.callId == callId &&
                (call.state == CallState.OUTGOING_RINGING || call.state == CallState.INCOMING_RINGING)
            ) {
                if (call.state == CallState.OUTGOING_RINGING) {
                    activeBarePeerJid?.let { sendJingleMessageAction(it, "retract", callId) }
                    sendTerminate(call, "timeout")
                } else {
                    activeBarePeerJid?.let { sendJingleMessageAction(it, "reject", callId) }
                    sendTerminate(call, "timeout")
                }
                finishCallWithStatus(callId, "Tidak ada jawaban")
            }
        }
    }

    private fun sendTerminate(call: CallInfo, reason: String) {
        val targetJid = activeFullPeerJid ?: try {
            JidCreate.from(call.remoteJid)
        } catch (_: Exception) {
            null
        } ?: return
        if (jingleSessionInitiated && targetJid.hasResource()) {
            sendTerminateToJid(targetJid, call.callId, reason)
        }
    }

    private fun sendTerminateToJid(targetJid: Jid, sid: String, reason: String) {
        val innerXml = "<reason><$reason/></reason>"
        sendJingleIq(
            toJid = targetJid,
            action = "session-terminate",
            sid = sid,
            innerXml = innerXml
        )
    }

    private fun sendJingleIq(
        toJid: Jid,
        action: String,
        sid: String,
        initiator: String? = null,
        responder: String? = null,
        innerXml: String,
        onError: ((String) -> Unit)? = null
    ) {
        val connection = connectionManager.getRawConnection() ?: return
        val iq = object : IQ("jingle", NS_JINGLE) {
            override fun getIQChildElementBuilder(xml: IQChildElementXmlStringBuilder): IQChildElementXmlStringBuilder {
                xml.attribute("action", action)
                xml.attribute("sid", sid)
                initiator?.let { xml.attribute("initiator", it) }
                responder?.let { xml.attribute("responder", it) }
                xml.rightAngleBracket()
                xml.append(innerXml)
                return xml
            }
        }
        iq.to = toJid
        iq.type = IQ.Type.set

        scope.launch(Dispatchers.IO) {
            try {
                if (onError != null) {
                    connection.sendIqRequestAndWaitForResponse<IQ>(iq)
                } else {
                    connection.sendStanza(iq)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Jingle IQ send failed to $toJid: ${e.message}")
                onError?.invoke(e.localizedMessage ?: "Error IQ")
            }
        }
    }

    private fun startDurationCounter() {
        durationJob?.cancel()
        durationJob = scope.launch(Dispatchers.Default) {
            while (true) {
                delay(1000)
                val call = _currentCall.value ?: break
                if (call.state == CallState.CONNECTED) {
                    val duration = ((System.currentTimeMillis() - call.startTime) / 1000).toInt()
                    _currentCall.value = call.copy(durationSeconds = duration)
                } else {
                    break
                }
            }
        }
    }

    fun toggleMute() {
        val call = _currentCall.value ?: return
        _currentCall.value = call.copy(isAudioMuted = !call.isAudioMuted)
    }

    fun toggleVideo() {
        val call = _currentCall.value ?: return
        if (call.callType.equals("Video", ignoreCase = true)) {
            _currentCall.value = call.copy(isVideoEnabled = !call.isVideoEnabled)
        }
    }

    fun toggleSpeaker() {
        val call = _currentCall.value ?: return
        _currentCall.value = call.copy(isSpeakerOn = !call.isSpeakerOn)
    }

    fun switchCamera() {
        val call = _currentCall.value ?: return
        if (call.callType.equals("Video", ignoreCase = true)) {
            _currentCall.value = call.copy(isFrontCamera = !call.isFrontCamera)
        }
    }
}
