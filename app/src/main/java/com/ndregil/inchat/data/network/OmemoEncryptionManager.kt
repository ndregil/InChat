package com.ndregil.inchat.data.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.ndregil.inchat.data.model.DeliveryStatus
import com.ndregil.inchat.data.model.MessageType
import com.ndregil.inchat.data.model.XmppMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smackx.mam.MamManager
import org.jivesoftware.smackx.muc.MultiUserChat
import org.jivesoftware.smackx.omemo.OmemoConfiguration
import org.jivesoftware.smackx.omemo.OmemoManager
import org.jivesoftware.smackx.omemo.OmemoMessage
import org.jivesoftware.smackx.omemo.internal.OmemoDevice
import org.jivesoftware.smackx.omemo.listener.OmemoMessageListener
import org.jivesoftware.smackx.omemo.listener.OmemoMucMessageListener
import org.jivesoftware.smackx.omemo.signal.SignalCachingOmemoStore
import org.jivesoftware.smackx.omemo.signal.SignalFileBasedOmemoStore
import org.jivesoftware.smackx.omemo.signal.SignalOmemoService
import org.jivesoftware.smackx.omemo.trust.OmemoFingerprint
import org.jivesoftware.smackx.omemo.trust.OmemoTrustCallback
import org.jivesoftware.smackx.omemo.trust.TrustState
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.impl.JidCreate
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class OmemoEncryptionManager(private val context: Context) : OmemoTrustCallback {

    private val prefs: SharedPreferences = context.getSharedPreferences("omemo_prefs", Context.MODE_PRIVATE)
    private val trustPrefs: SharedPreferences = context.getSharedPreferences("omemo_trust_prefs", Context.MODE_PRIVATE)
    private val roomOmemoPrefs: SharedPreferences = context.getSharedPreferences("omemo_room_prefs", Context.MODE_PRIVATE)

    private val _omemoEnabledJids = MutableStateFlow<Set<String>>(emptySet())
    val omemoEnabledJids: StateFlow<Set<String>> = _omemoEnabledJids.asStateFlow()

    private val _autoTrustDevices = MutableStateFlow(prefs.getBoolean("auto_trust", true))
    val autoTrustDevices: StateFlow<Boolean> = _autoTrustDevices.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private var omemoManager: OmemoManager? = null
    private var activeConnection: XMPPConnection? = null

    init {
        initializeOmemoService()
    }

    private fun initializeOmemoService() {
        try {
            if (!SignalOmemoService.isServiceRegistered()) {
                SignalOmemoService.acknowledgeLicense()
                SignalOmemoService.setup()
            }

            OmemoConfiguration.setDeleteStaleDevices(true)
            OmemoConfiguration.setDeleteStaleDevicesAfterHours(168) // 7 days
            OmemoConfiguration.setRenewOldSignedPreKeys(true)
            OmemoConfiguration.setRenewOldSignedPreKeysAfterHours(168)
            OmemoConfiguration.setAddOmemoHintBody(true)
            OmemoConfiguration.setRepairBrokenSessionsWithPrekeyMessages(true)
            OmemoConfiguration.setCompleteSessionWithEmptyMessage(true)

            _isInitialized.value = true
            Log.i(TAG, "OMEMO Signal service successfully initialized.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize OMEMO Signal service: ${e.message}", e)
        }
    }

    fun isOmemoEnabledFor(roomOrContactJid: String): Boolean {
        val clean = roomOrContactJid.trim().lowercase()
        return roomOmemoPrefs.getBoolean(clean, false)
    }

    fun setOmemoEnabledFor(roomOrContactJid: String, enabled: Boolean) {
        val clean = roomOrContactJid.trim().lowercase()
        roomOmemoPrefs.edit().putBoolean(clean, enabled).apply()
        val current = _omemoEnabledJids.value.toMutableSet()
        if (enabled) current.add(clean) else current.remove(clean)
        _omemoEnabledJids.value = current
    }

    fun setAutoTrustDevices(autoTrust: Boolean) {
        _autoTrustDevices.value = autoTrust
        prefs.edit().putBoolean("auto_trust", autoTrust).apply()
    }

    suspend fun onConnectionReady(connection: XMPPTCPConnection, currentAccountJid: String) = withContext(Dispatchers.IO) {
        try {
            activeConnection = connection
            val manager = OmemoManager.getInstanceFor(connection)
            manager.initialize()
            manager.setTrustCallback(this@OmemoEncryptionManager)
            omemoManager = manager

            // Register incoming OMEMO message listener
            manager.addOmemoMessageListener(object : OmemoMessageListener {
                override fun onOmemoMessageReceived(stanza: org.jivesoftware.smack.packet.Stanza, decryptedMessage: OmemoMessage.Received) {
                    handleIncomingOmemoMessage(stanza, decryptedMessage, false)
                }

                override fun onOmemoCarbonCopyReceived(
                    direction: org.jivesoftware.smackx.carbons.packet.CarbonExtension.Direction,
                    carbonCopy: Message,
                    wrappingMessage: Message,
                    decryptedCarbonCopy: OmemoMessage.Received
                ) {
                    handleIncomingOmemoMessage(wrappingMessage, decryptedCarbonCopy, true)
                }
            })

            // Register incoming MUC OMEMO listener
            manager.addOmemoMucMessageListener(object : OmemoMucMessageListener {
                override fun onOmemoMucMessageReceived(muc: MultiUserChat, stanza: org.jivesoftware.smack.packet.Stanza, decryptedMessage: OmemoMessage.Received) {
                    handleIncomingOmemoMucMessage(muc, stanza, decryptedMessage)
                }
            })

            Log.i(TAG, "OMEMO Manager initialized and listeners attached for $currentAccountJid")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize OMEMO Manager on connection ready: ${e.message}", e)
        }
    }

    fun cleanup() {
        try {
            omemoManager?.let { mgr ->
                // Clean up listeners if needed
            }
        } catch (_: Exception) {}
        omemoManager = null
        activeConnection = null
    }

    // --- Trust Callback Implementation (OmemoTrustCallback) ---
    override fun getTrust(device: OmemoDevice, fingerprint: OmemoFingerprint): TrustState {
        val key = "${device.jid}:${device.deviceId}:${fingerprint.toString()}"
        val stored = trustPrefs.getString(key, null)
        if (stored != null) {
            return try {
                TrustState.valueOf(stored)
            } catch (_: Exception) {
                TrustState.undecided
            }
        }
        // If Auto-trust (BTBV) is enabled, automatically trust undecided identities
        if (_autoTrustDevices.value) {
            setTrust(device, fingerprint, TrustState.trusted)
            return TrustState.trusted
        }
        return TrustState.undecided
    }

    override fun setTrust(device: OmemoDevice, fingerprint: OmemoFingerprint, state: TrustState) {
        val key = "${device.jid}:${device.deviceId}:${fingerprint.toString()}"
        trustPrefs.edit().putString(key, state.name).apply()
        try {
            omemoManager?.let { mgr ->
                if (state == TrustState.trusted) {
                    mgr.trustOmemoIdentity(device, fingerprint)
                } else if (state == TrustState.untrusted) {
                    mgr.distrustOmemoIdentity(device, fingerprint)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update OMEMO trust in OmemoManager: ${e.message}")
        }
    }

    fun getOwnFingerprint(): String? {
        return try {
            omemoManager?.ownFingerprint?.toString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get own fingerprint: ${e.message}")
            null
        }
    }

    fun getOwnDeviceId(): Int? {
        return try {
            omemoManager?.deviceId
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getActiveFingerprints(bareJidStr: String): Map<OmemoDevice, String> = withContext(Dispatchers.IO) {
        val map = mutableMapOf<OmemoDevice, String>()
        try {
            val mgr = omemoManager ?: return@withContext emptyMap()
            val bareJid = JidCreate.entityBareFrom(bareJidStr)
            mgr.requestDeviceListUpdateFor(bareJid)
            val active = mgr.getActiveFingerprints(bareJid)
            for ((device, fp) in active) {
                map[device] = fp.toString()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get active fingerprints for $bareJidStr: ${e.message}")
        }
        return@withContext map
    }

    suspend fun encryptDirectMessage(
        targetBareJidStr: String,
        plaintext: String,
        stanzaId: String? = null,
        oobUrl: String? = null
    ): Result<Message> = withContext(Dispatchers.IO) {
        try {
            val mgr = omemoManager ?: return@withContext Result.failure(IllegalStateException("OMEMO belum diinisialisasi."))
            val bareJid = JidCreate.entityBareFrom(targetBareJidStr.trim())
            
            mgr.requestDeviceListUpdateFor(bareJid)

            val sentMsg = try {
                mgr.encrypt(bareJid, plaintext)
            } catch (e: org.jivesoftware.smackx.omemo.exceptions.UndecidedOmemoIdentityException) {
                if (_autoTrustDevices.value) {
                    // Auto-trust undecided devices and retry
                    val undecided = e.undecidedDevices
                    for (dev in undecided) {
                        try {
                            val fps = mgr.getActiveFingerprints(dev.jid)
                            val fp = fps[dev]
                            if (fp != null) {
                                setTrust(dev, fp, TrustState.trusted)
                            }
                        } catch (_: Exception) {}
                    }
                    mgr.encrypt(bareJid, plaintext)
                } else {
                    return@withContext Result.failure(Exception("Perangkat baru belum dipercaya (Undecided OMEMO Identity). Aktifkan BTBV atau percayai perangkat kontak di menu OMEMO."))
                }
            }

            val builtMsg = sentMsg.buildMessage(org.jivesoftware.smack.packet.MessageBuilder.buildMessage(), bareJid) as Message
            if (!stanzaId.isNullOrBlank()) {
                builtMsg.stanzaId = stanzaId
            }
            builtMsg.to = bareJid
            builtMsg.type = Message.Type.chat

            if (!oobUrl.isNullOrBlank()) {
                builtMsg.addExtension(
                    org.jivesoftware.smack.packet.StandardExtensionElement
                        .builder("x", "jabber:x:oob")
                        .addElement("url", oobUrl.trim())
                        .build()
                )
            }
            org.jivesoftware.smackx.receipts.DeliveryReceiptRequest.addTo(builtMsg)

            Result.success(builtMsg)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengenkripsi pesan OMEMO langsung ke $targetBareJidStr: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun encryptMucMessage(
        muc: MultiUserChat,
        plaintext: String,
        stanzaId: String? = null,
        oobUrl: String? = null
    ): Result<Message> = withContext(Dispatchers.IO) {
        try {
            val mgr = omemoManager ?: return@withContext Result.failure(IllegalStateException("OMEMO belum diinisialisasi."))
            
            val sentMsg = try {
                mgr.encrypt(muc, plaintext)
            } catch (e: org.jivesoftware.smackx.omemo.exceptions.UndecidedOmemoIdentityException) {
                if (_autoTrustDevices.value) {
                    val undecided = e.undecidedDevices
                    for (dev in undecided) {
                        try {
                            val fps = mgr.getActiveFingerprints(dev.jid)
                            val fp = fps[dev]
                            if (fp != null) {
                                setTrust(dev, fp, TrustState.trusted)
                            }
                        } catch (_: Exception) {}
                    }
                    mgr.encrypt(muc, plaintext)
                } else {
                    return@withContext Result.failure(Exception("Perangkat baru di room belum dipercaya (Undecided OMEMO Identity)."))
                }
            }

            val builtMsg = sentMsg.buildMessage(org.jivesoftware.smack.packet.MessageBuilder.buildMessage(), muc.room) as Message
            if (!stanzaId.isNullOrBlank()) {
                builtMsg.stanzaId = stanzaId
            }
            builtMsg.to = muc.room
            builtMsg.type = Message.Type.groupchat

            if (!oobUrl.isNullOrBlank()) {
                builtMsg.addExtension(
                    org.jivesoftware.smack.packet.StandardExtensionElement
                        .builder("x", "jabber:x:oob")
                        .addElement("url", oobUrl.trim())
                        .build()
                )
            }
            org.jivesoftware.smackx.receipts.DeliveryReceiptRequest.addTo(builtMsg)

            Result.success(builtMsg)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengenkripsi pesan OMEMO MUC: ${e.message}", e)
            Result.failure(e)
        }
    }

    var onDecryptedMessageReceived: ((XmppMessage, Boolean) -> Unit)? = null

    private fun handleIncomingOmemoMessage(stanza: org.jivesoftware.smack.packet.Stanza, decrypted: OmemoMessage.Received, isCarbon: Boolean) {
        try {
            if (decrypted.isKeyTransportMessage()) {
                // Key transport stanzas contain ratchet key updates without user text, ignore display
                return
            }
            val body = decrypted.body
            if (body.isNullOrBlank()) return

            val fromJid = stanza.from ?: return
            val roomJid = fromJid.asBareJid().toString()
            val senderNick = fromJid.resourceOrNull?.toString() ?: roomJid.substringBefore("@")

            val urls = com.ndregil.inchat.util.MediaUrlUtils.extractUrls(body)
            val bodyImageUrl = urls.firstOrNull { com.ndregil.inchat.util.MediaUrlUtils.isKnownImageUrl(it) }

            val preParsedOobUrl = if (stanza.hasExtension("x", "jabber:x:oob")) {
                val xml = stanza.toXML().toString()
                Regex("""<x[^>]*xmlns=['"]jabber:x:oob['"][^>]*>\s*<url>([^<]+)</url>""", RegexOption.IGNORE_CASE)
                    .find(xml)?.groupValues?.get(1)?.trim()
            } else null

            val isOobImage = preParsedOobUrl != null && com.ndregil.inchat.util.MediaUrlUtils.isKnownImageUrl(preParsedOobUrl)
            val isVoice = body.contains("🎤 Pesan Suara", ignoreCase = true) ||
                urls.any { com.ndregil.inchat.util.MediaUrlUtils.isVoiceOrAudioUrl(it) }
            val isImage = isOobImage || bodyImageUrl != null
            val isFile = isImage || preParsedOobUrl != null

            val fileUrl = preParsedOobUrl ?: bodyImageUrl ?: if (isVoice) urls.firstOrNull() else null
            val inferredFileName = if (isFile && !fileUrl.isNullOrBlank()) {
                fileUrl.substringAfterLast('/').substringBefore('?').substringBefore('#')
                    .takeIf { it.isNotBlank() } ?: "Lampiran"
            } else null

            val voiceDuration = if (isVoice) {
                Regex("\\((\\d+)d\\)").find(body)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 5
            } else null

            val messageType = when {
                isVoice -> MessageType.VOICE
                isFile -> MessageType.FILE
                else -> MessageType.CHAT
            }

            val timestamp = org.jivesoftware.smackx.delay.DelayInformationManager.getDelayInformation(stanza)?.stamp?.time ?: System.currentTimeMillis()
            val stanzaId = stanza.stanzaId?.ifBlank { null } ?: "omemo_${timestamp}_${senderNick.hashCode()}"

            val xmppMsg = XmppMessage(
                id = stanzaId,
                roomJid = roomJid,
                senderJid = fromJid.toString(),
                senderNickname = senderNick,
                body = body,
                timestamp = timestamp,
                isMine = false,
                messageType = messageType,
                status = DeliveryStatus.DELIVERED,
                fileUrl = fileUrl,
                fileMimeType = when {
                    isImage -> fileUrl?.let { com.ndregil.inchat.util.MediaUrlUtils.inferImageMimeType(it) } ?: "image/*"
                    isVoice -> "audio/mp4"
                    isFile -> fileUrl?.let { com.ndregil.inchat.util.MediaUrlUtils.inferFileMimeType(it) } ?: "application/octet-stream"
                    else -> null
                },
                fileName = inferredFileName,
                voiceDurationSeconds = voiceDuration,
                isEncrypted = true
            )

            onDecryptedMessageReceived?.invoke(xmppMsg, false)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memproses pesan OMEMO masuk: ${e.message}", e)
        }
    }

    private fun handleIncomingOmemoMucMessage(muc: MultiUserChat, stanza: org.jivesoftware.smack.packet.Stanza, decrypted: OmemoMessage.Received) {
        try {
            if (decrypted.isKeyTransportMessage()) return
            val body = decrypted.body
            if (body.isNullOrBlank()) return

            val roomJid = muc.room.toString()
            val fromJid = stanza.from ?: return
            val senderNick = fromJid.resourceOrNull?.toString() ?: fromJid.asBareJid().toString().substringBefore("@")

            val urls = com.ndregil.inchat.util.MediaUrlUtils.extractUrls(body)
            val bodyImageUrl = urls.firstOrNull { com.ndregil.inchat.util.MediaUrlUtils.isKnownImageUrl(it) }
            val preParsedOobUrl = if (stanza.hasExtension("x", "jabber:x:oob")) {
                val xml = stanza.toXML().toString()
                Regex("""<x[^>]*xmlns=['"]jabber:x:oob['"][^>]*>\s*<url>([^<]+)</url>""", RegexOption.IGNORE_CASE)
                    .find(xml)?.groupValues?.get(1)?.trim()
            } else null

            val isOobImage = preParsedOobUrl != null && com.ndregil.inchat.util.MediaUrlUtils.isKnownImageUrl(preParsedOobUrl)
            val isVoice = body.contains("🎤 Pesan Suara", ignoreCase = true) ||
                urls.any { com.ndregil.inchat.util.MediaUrlUtils.isVoiceOrAudioUrl(it) }
            val isImage = isOobImage || bodyImageUrl != null
            val isFile = isImage || preParsedOobUrl != null

            val fileUrl = preParsedOobUrl ?: bodyImageUrl ?: if (isVoice) urls.firstOrNull() else null
            val inferredFileName = if (isFile && !fileUrl.isNullOrBlank()) {
                fileUrl.substringAfterLast('/').substringBefore('?').substringBefore('#')
                    .takeIf { it.isNotBlank() } ?: "Lampiran"
            } else null

            val voiceDuration = if (isVoice) {
                Regex("\\((\\d+)d\\)").find(body)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 5
            } else null

            val messageType = when {
                isVoice -> MessageType.VOICE
                isFile -> MessageType.FILE
                else -> MessageType.GROUPCHAT
            }

            val timestamp = org.jivesoftware.smackx.delay.DelayInformationManager.getDelayInformation(stanza)?.stamp?.time ?: System.currentTimeMillis()
            val stanzaId = stanza.stanzaId?.ifBlank { null } ?: "omemo_muc_${timestamp}_${senderNick.hashCode()}"

            val xmppMsg = XmppMessage(
                id = stanzaId,
                roomJid = roomJid,
                senderJid = fromJid.toString(),
                senderNickname = senderNick,
                body = body,
                timestamp = timestamp,
                isMine = false,
                messageType = messageType,
                status = DeliveryStatus.DELIVERED,
                fileUrl = fileUrl,
                fileMimeType = when {
                    isImage -> fileUrl?.let { com.ndregil.inchat.util.MediaUrlUtils.inferImageMimeType(it) } ?: "image/*"
                    isVoice -> "audio/mp4"
                    isFile -> fileUrl?.let { com.ndregil.inchat.util.MediaUrlUtils.inferFileMimeType(it) } ?: "application/octet-stream"
                    else -> null
                },
                fileName = inferredFileName,
                voiceDurationSeconds = voiceDuration,
                isEncrypted = true
            )

            onDecryptedMessageReceived?.invoke(xmppMsg, true)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memproses pesan MUC OMEMO masuk: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "OmemoEncryptionManager"
    }
}
