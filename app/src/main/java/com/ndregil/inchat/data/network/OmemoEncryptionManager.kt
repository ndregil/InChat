package com.ndregil.inchat.data.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.ndregil.inchat.data.model.DeliveryStatus
import com.ndregil.inchat.data.model.MessageType
import com.ndregil.inchat.data.model.XmppMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.packet.StanzaError
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smackx.omemo.OmemoConfiguration
import org.jivesoftware.smackx.omemo.OmemoManager
import org.jivesoftware.smackx.omemo.OmemoMessage
import org.jivesoftware.smackx.omemo.internal.OmemoDevice
import org.jivesoftware.smackx.omemo.listener.OmemoMessageListener
import org.jivesoftware.smackx.omemo.signal.SignalOmemoService
import org.jivesoftware.smackx.omemo.trust.OmemoFingerprint
import org.jivesoftware.smackx.omemo.trust.OmemoTrustCallback
import org.jivesoftware.smackx.omemo.trust.TrustState
import org.jxmpp.jid.impl.JidCreate

class OmemoUnsupportedContactException(message: String) : Exception(message)
class OmemoEncryptionException(message: String) : Exception(message)

sealed class OmemoSupportResult {
    data class Supported(val deviceCount: Int) : OmemoSupportResult()
    data class Unsupported(val reason: String) : OmemoSupportResult()
    data class Error(val reason: String) : OmemoSupportResult()
}

class OmemoEncryptionManager(private val context: Context) : OmemoTrustCallback {

    private val prefs: SharedPreferences = context.getSharedPreferences("omemo_prefs", Context.MODE_PRIVATE)
    private val trustPrefs: SharedPreferences = context.getSharedPreferences("omemo_trust_prefs", Context.MODE_PRIVATE)
    private val roomOmemoPrefs: SharedPreferences = context.getSharedPreferences("omemo_room_prefs", Context.MODE_PRIVATE)

    private val _omemoEnabledJids = MutableStateFlow(loadPersistedEnabledJids())
    val omemoEnabledJids: StateFlow<Set<String>> = _omemoEnabledJids.asStateFlow()

    // Bare JID -> Boolean? (true = memiliki perangkat OMEMO, false = daftar perangkat kosong / tidak mendukung, null = belum dicek)
    private val _contactOmemoSupport = MutableStateFlow<Map<String, Boolean?>>(emptyMap())
    val contactOmemoSupport: StateFlow<Map<String, Boolean?>> = _contactOmemoSupport.asStateFlow()

    private val _autoTrustDevices = MutableStateFlow(prefs.getBoolean("auto_trust", true))
    val autoTrustDevices: StateFlow<Boolean> = _autoTrustDevices.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private var omemoManager: OmemoManager? = null
    private var activeConnection: XMPPConnection? = null

    private val incomingOmemoListener = object : OmemoMessageListener {
        override fun onOmemoMessageReceived(
            stanza: org.jivesoftware.smack.packet.Stanza,
            decryptedMessage: OmemoMessage.Received
        ) {
            handleIncomingOmemoMessage(
                stanza = stanza,
                decrypted = decryptedMessage,
                isCarbon = false,
                isOutgoingCarbon = false
            )
        }

        override fun onOmemoCarbonCopyReceived(
            direction: org.jivesoftware.smackx.carbons.packet.CarbonExtension.Direction,
            carbonCopy: Message,
            wrappingMessage: Message,
            decryptedCarbonCopy: OmemoMessage.Received
        ) {
            val isOutgoingCarbon =
                direction == org.jivesoftware.smackx.carbons.packet.CarbonExtension.Direction.sent
            handleIncomingOmemoMessage(
                stanza = carbonCopy,
                decrypted = decryptedCarbonCopy,
                isCarbon = true,
                isOutgoingCarbon = isOutgoingCarbon
            )
        }
    }

    init {
        initializeOmemoService()
    }

    /**
     * Catatan OMEMO 1-on-1 (XEP-0384):
     * Untuk chat 1-on-1, OMEMO bekerja pada bare JID (tanpa resource, contoh: "user@server",
     * bukan "user@server/jTalk"). Semua pengecekan daftar perangkat OMEMO, status dukungan,
     * preferensi saklar, dan enkripsi wajib memakai bare JID.
     */
    fun toBareJid(jid: String): String = jid.trim().substringBefore("/").lowercase()

    private fun loadPersistedEnabledJids(): Set<String> {
        return roomOmemoPrefs.all.entries
            .filter { it.value == true }
            .map { toBareJid(it.key) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun initializeOmemoService() {
        try {
            if (org.jivesoftware.smack.provider.ProviderManager.getExtensionProvider(
                    "encrypted",
                    "eu.siacs.conversations.axolotl"
                ) == null
            ) {
                org.jivesoftware.smackx.omemo.OmemoInitializer().initialize()
            }

            if (!SignalOmemoService.isServiceRegistered()) {
                SignalOmemoService.acknowledgeLicense()
                SignalOmemoService.setup()
            }

            val omemoDir = java.io.File(context.filesDir, "omemo").apply { mkdirs() }
            try {
                val signalService = org.jivesoftware.smackx.omemo.OmemoService.getInstance() as SignalOmemoService
                signalService.setOmemoStoreBackend(
                    org.jivesoftware.smackx.omemo.signal.SignalFileBasedOmemoStore(omemoDir)
                )
                Log.i(TAG, "OMEMO file store configured at ${omemoDir.absolutePath}")
            } catch (e: IllegalStateException) {
                Log.d(TAG, "OMEMO store backend already configured: ${e.message}")
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
        val cleanBare = toBareJid(roomOrContactJid)
        if (cleanBare.isBlank()) return false
        return roomOmemoPrefs.getBoolean(cleanBare, false)
    }

    fun setOmemoEnabledFor(roomOrContactJid: String, enabled: Boolean) {
        val cleanBare = toBareJid(roomOrContactJid)
        if (cleanBare.isBlank()) return
        roomOmemoPrefs.edit().putBoolean(cleanBare, enabled).apply()
        val current = _omemoEnabledJids.value.toMutableSet()
        if (enabled) current.add(cleanBare) else current.remove(cleanBare)
        _omemoEnabledJids.value = current
    }

    fun setAutoTrustDevices(autoTrust: Boolean) {
        _autoTrustDevices.value = autoTrust
        prefs.edit().putBoolean("auto_trust", autoTrust).apply()
    }

    private fun ensureTrustCallback(manager: OmemoManager) {
        try {
            manager.setTrustCallback(this@OmemoEncryptionManager)
        } catch (e: IllegalStateException) {
            // Smack's OmemoManager.setTrustCallback throws if TrustCallback was already attached
            // (for example in prepareForConnection or prior to a stream reconnect).
            Log.d(TAG, "TrustCallback already attached to OmemoManager: ${e.message}")
        }
    }

    /**
     * Pasang OmemoManager + TrustCallback + OmemoMessageListener sebelum login/presence
     * agar pesan OMEMO offline maupun selama inisialisasi tidak terlewat.
     */
    fun prepareForConnection(connection: XMPPConnection) {
        try {
            if (!SignalOmemoService.isServiceRegistered()) {
                initializeOmemoService()
            }
            activeConnection = connection
            val manager = OmemoManager.getInstanceFor(connection)
            ensureTrustCallback(manager)
            manager.removeOmemoMessageListener(incomingOmemoListener)
            manager.addOmemoMessageListener(incomingOmemoListener)
            omemoManager = manager
            Log.d(TAG, "Prepared OmemoManager and 1-on-1 listener prior to login/presence")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to pre-attach OmemoManager before login: ${e.message}")
        }
    }

    suspend fun onConnectionReady(connection: XMPPTCPConnection, currentAccountJid: String) = withContext(Dispatchers.IO) {
        try {
            if (!SignalOmemoService.isServiceRegistered()) {
                initializeOmemoService()
            }
            activeConnection = connection
            val manager = OmemoManager.getInstanceFor(connection)
            ensureTrustCallback(manager)
            // Pasang listener SEBELUM initialize() dan hindari duplikasi listener pada koneksi yang sama
            manager.removeOmemoMessageListener(incomingOmemoListener)
            manager.addOmemoMessageListener(incomingOmemoListener)
            omemoManager = manager

            manager.initialize()
            Log.i(
                TAG,
                "OMEMO Manager initialized for $currentAccountJid (ownJid=${manager.ownJid}, deviceId=${manager.deviceId})"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize OMEMO Manager on connection ready: ${e.message}", e)
        }
    }

    fun cleanup() {
        try {
            omemoManager?.removeOmemoMessageListener(incomingOmemoListener)
        } catch (_: Exception) {
        }
        omemoManager = null
        activeConnection = null
        _contactOmemoSupport.value = emptyMap()
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
        if (_autoTrustDevices.value) {
            trustPrefs.edit().putString(key, TrustState.trusted.name).apply()
            return TrustState.trusted
        }
        return TrustState.undecided
    }

    override fun setTrust(device: OmemoDevice, fingerprint: OmemoFingerprint, state: TrustState) {
        val key = "${device.jid}:${device.deviceId}:${fingerprint.toString()}"
        trustPrefs.edit().putString(key, state.name).apply()
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

    /**
     * Memeriksa dukungan OMEMO kontak secara nyata ke daftar perangkat PEP menggunakan bare JID.
     * Jika daftar perangkat kosong (atau node PEP devicelist tidak ditemukan), kontak dianggap TIDAK mendukung OMEMO.
     */
    suspend fun checkOmemoSupport(targetJid: String, forceRefresh: Boolean = true): OmemoSupportResult = withContext(Dispatchers.IO) {
        val cleanBare = toBareJid(targetJid)
        if (cleanBare.isBlank() || !cleanBare.contains("@")) {
            return@withContext OmemoSupportResult.Error("JID kontak tidak valid.")
        }

        val mgr = omemoManager
        val conn = activeConnection
        if (mgr == null || conn == null || !conn.isConnected || !conn.isAuthenticated) {
            return@withContext OmemoSupportResult.Error("Sesi OMEMO belum siap atau koneksi XMPP terputus.")
        }

        try {
            val bareJid = JidCreate.entityBareFrom(cleanBare)
            if (forceRefresh) {
                try {
                    withTimeout(DEVICE_LIST_TIMEOUT_MS) {
                        mgr.requestDeviceListUpdateFor(bareJid)
                    }
                } catch (timeoutEx: TimeoutCancellationException) {
                    Log.w(TAG, "Timeout checking OMEMO device list for $cleanBare", timeoutEx)
                    return@withContext OmemoSupportResult.Error("Timeout saat memeriksa daftar perangkat OMEMO kontak.")
                } catch (xmppEx: XMPPException.XMPPErrorException) {
                    val cond = xmppEx.stanzaError?.condition
                    if (cond == StanzaError.Condition.item_not_found ||
                        cond == StanzaError.Condition.service_unavailable ||
                        cond == StanzaError.Condition.feature_not_implemented
                    ) {
                        // Server menyatakan node PEP OMEMO belum pernah dipublikasikan kontak
                        Log.i(TAG, "Kontak $cleanBare tidak memiliki node devicelist OMEMO ($cond)")
                    } else {
                        Log.w(TAG, "XMPP error checking OMEMO devices for $cleanBare: ${xmppEx.message}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error refreshing OMEMO device list for $cleanBare: ${e.message}")
                }
            }

            val active = mgr.getActiveFingerprints(bareJid)
            val hasDevices = active.isNotEmpty()
            _contactOmemoSupport.value = _contactOmemoSupport.value + (cleanBare to hasDevices)

            return@withContext if (hasDevices) {
                OmemoSupportResult.Supported(active.size)
            } else {
                OmemoSupportResult.Unsupported("Kontak ini tidak mendukung OMEMO. Pesan tidak dikirim.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal memeriksa dukungan OMEMO untuk $cleanBare: ${e.message}", e)
            return@withContext OmemoSupportResult.Error("Gagal memeriksa perangkat OMEMO: ${e.message ?: "kesalahan tidak diketahui"}")
        }
    }

    suspend fun getActiveFingerprints(bareJidStr: String): Map<OmemoDevice, String> = withContext(Dispatchers.IO) {
        val cleanBare = toBareJid(bareJidStr)
        val map = mutableMapOf<OmemoDevice, String>()
        try {
            val mgr = omemoManager ?: return@withContext emptyMap()
            val bareJid = JidCreate.entityBareFrom(cleanBare)
            try {
                withTimeout(DEVICE_LIST_TIMEOUT_MS) {
                    mgr.requestDeviceListUpdateFor(bareJid)
                }
            } catch (_: Exception) {
                // Tetap periksa daftar perangkat lokal/terbaru di bawah
            }
            val active = mgr.getActiveFingerprints(bareJid)
            for ((device, fp) in active) {
                map[device] = fp.toString()
            }
            _contactOmemoSupport.value = _contactOmemoSupport.value + (cleanBare to map.isNotEmpty())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get active fingerprints for $cleanBare: ${e.message}")
        }
        return@withContext map
    }

    suspend fun encryptDirectMessage(
        targetBareJidStr: String,
        plaintext: String,
        stanzaId: String? = null,
        oobUrl: String? = null
    ): Result<Message> = withContext(Dispatchers.IO) {
        val cleanBare = toBareJid(targetBareJidStr)
        if (cleanBare.isBlank() || !cleanBare.contains("@")) {
            return@withContext Result.failure(OmemoEncryptionException("JID kontak tidak valid untuk OMEMO."))
        }

        val mgr = omemoManager
        val conn = activeConnection
        if (mgr == null || conn == null || !conn.isConnected || !conn.isAuthenticated) {
            return@withContext Result.failure(OmemoEncryptionException("Sesi OMEMO belum siap atau koneksi XMPP terputus."))
        }

        // 1. Cek dukungan perangkat OMEMO kontak (wajib pakai bare JID, tanpa resource)
        when (val support = checkOmemoSupport(cleanBare, forceRefresh = true)) {
            is OmemoSupportResult.Unsupported -> {
                return@withContext Result.failure(OmemoUnsupportedContactException(support.reason))
            }
            is OmemoSupportResult.Error -> {
                return@withContext Result.failure(OmemoEncryptionException(support.reason))
            }
            is OmemoSupportResult.Supported -> Unit
        }

        try {
            val bareJid = JidCreate.entityBareFrom(cleanBare)
            // Pastikan ulang daftar perangkat masih tidak kosong tepat sebelum enkripsi
            if (mgr.getActiveFingerprints(bareJid).isEmpty()) {
                _contactOmemoSupport.value = _contactOmemoSupport.value + (cleanBare to false)
                return@withContext Result.failure(
                    OmemoUnsupportedContactException("Kontak ini tidak mendukung OMEMO. Pesan tidak dikirim.")
                )
            }

            val sentMsg = withTimeout(ENCRYPT_TIMEOUT_MS) {
                try {
                    mgr.encrypt(bareJid, plaintext)
                } catch (e: Exception) {
                    if (_autoTrustDevices.value) {
                        val fps = mgr.getActiveFingerprints(bareJid)
                        if (fps.isEmpty()) {
                            _contactOmemoSupport.value = _contactOmemoSupport.value + (cleanBare to false)
                            throw OmemoUnsupportedContactException("Kontak ini tidak mendukung OMEMO. Pesan tidak dikirim.")
                        }
                        for ((dev, fp) in fps) {
                            setTrust(dev, fp, TrustState.trusted)
                        }
                        mgr.encrypt(bareJid, plaintext)
                    } else if (e is org.jivesoftware.smackx.omemo.exceptions.UndecidedOmemoIdentityException) {
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
                        throw e
                    }
                }
            }

            val builtMsg = sentMsg.buildMessage(org.jivesoftware.smack.packet.MessageBuilder.buildMessage(), bareJid) as Message
            if (!stanzaId.isNullOrBlank()) {
                builtMsg.stanzaId = stanzaId
            }
            builtMsg.to = bareJid
            builtMsg.type = Message.Type.chat

            // Jangan bocorkan OOB URL di luar payload terenkripsi saat OMEMO aktif
            org.jivesoftware.smackx.receipts.DeliveryReceiptRequest.addTo(builtMsg)

            Result.success(builtMsg)
        } catch (timeoutEx: TimeoutCancellationException) {
            Log.e(TAG, "Timeout enkripsi OMEMO ke $cleanBare", timeoutEx)
            Result.failure(OmemoEncryptionException("Timeout saat membuat sesi/mengenkripsi pesan OMEMO."))
        } catch (unsupportedEx: OmemoUnsupportedContactException) {
            Result.failure(unsupportedEx)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengenkripsi pesan OMEMO langsung ke $cleanBare: ${e.message}", e)
            Result.failure(OmemoEncryptionException("Gagal mengenkripsi OMEMO: ${e.message ?: "kesalahan Smack"}"))
        }
    }

    var onDecryptedMessageReceived: ((XmppMessage, Boolean) -> Unit)? = null

    fun tryDecryptStanza(stanza: Message): Result<String> {
        return try {
            val mgr = omemoManager
                ?: return Result.failure(OmemoEncryptionException("Sesi OMEMO belum siap."))
            val omemoElement = stanza.getExtension(
                "encrypted",
                "eu.siacs.conversations.axolotl"
            ) as? org.jivesoftware.smackx.omemo.element.OmemoElement
                ?: return Result.failure(OmemoEncryptionException("Elemen OMEMO tidak ditemukan."))
            if (omemoElement.isKeyTransportElement) {
                return Result.failure(OmemoEncryptionException("Pesan key-transport OMEMO tanpa payload."))
            }
            val senderBareJid = stanza.from?.asBareJid()
                ?: return Result.failure(OmemoEncryptionException("Pengirim tidak valid."))
            val received = mgr.decrypt(senderBareJid, omemoElement)
            val body = received.body
            if (body.isNullOrBlank()) {
                Result.failure(OmemoEncryptionException("Body hasil dekripsi kosong."))
            } else {
                Result.success(body)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal mendekripsi stanza MAM OMEMO (${stanza.stanzaId}): ${e.message}")
            Result.failure(e)
        }
    }

    private fun handleIncomingOmemoMessage(
        stanza: org.jivesoftware.smack.packet.Stanza,
        decrypted: OmemoMessage.Received,
        isCarbon: Boolean,
        isOutgoingCarbon: Boolean = false
    ) {
        try {
            if (decrypted.isKeyTransportMessage()) {
                return
            }
            val body = decrypted.body
            if (body.isNullOrBlank()) return

            val fromJid = stanza.from ?: return
            val ownBareJid = omemoManager?.ownJid?.toString()
                ?: activeConnection?.user?.asBareJid()?.toString()

            val roomBareJid = if (isCarbon && isOutgoingCarbon) {
                stanza.to?.asBareJid()?.toString() ?: return
            } else {
                fromJid.asBareJid().toString()
            }

            // Abaikan jika echo ke bare JID milik sendiri tanpa lawan bicara yang jelas
            if (!ownBareJid.isNullOrBlank() && roomBareJid.equals(ownBareJid, ignoreCase = true)) {
                return
            }

            // Untuk chat 1-on-1, jangan pakai resource (misal Conversations.xxxx) sebagai nama pengirim
            val senderNick = if (isCarbon && isOutgoingCarbon) {
                ownBareJid?.substringBefore("@") ?: ""
            } else {
                roomBareJid.substringBefore("@")
            }

            val urls = com.ndregil.inchat.util.MediaUrlUtils.extractUrls(body)
            val bodyImageUrl = urls.firstOrNull { com.ndregil.inchat.util.MediaUrlUtils.isKnownImageUrl(it) }
            val bodyFirstUrl = urls.firstOrNull()

            val preParsedOobUrl = if (stanza.hasExtension("x", "jabber:x:oob")) {
                val xml = stanza.toXML().toString()
                Regex("""<x[^>]*xmlns=['"]jabber:x:oob['"][^>]*>\s*<url>([^<]+)</url>""", RegexOption.IGNORE_CASE)
                    .find(xml)?.groupValues?.get(1)?.trim()
            } else null

            val isOobImage = preParsedOobUrl != null && com.ndregil.inchat.util.MediaUrlUtils.isKnownImageUrl(preParsedOobUrl)
            val isVoice = body.contains("🎤 Pesan Suara", ignoreCase = true) ||
                urls.any { com.ndregil.inchat.util.MediaUrlUtils.isVoiceOrAudioUrl(it) }
            val isImage = isOobImage || bodyImageUrl != null
            val isFile = isImage || preParsedOobUrl != null || (!isVoice && bodyFirstUrl != null && !body.contains("maps.google.com"))

            val fileUrl = preParsedOobUrl ?: bodyImageUrl ?: if (isVoice || isFile) bodyFirstUrl else null
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
            val stanzaId = stanza.stanzaId?.ifBlank { null } ?: "omemo_${timestamp}_${roomBareJid.hashCode()}"

            val xmppMsg = XmppMessage(
                id = stanzaId,
                roomJid = roomBareJid,
                senderJid = fromJid.toString(),
                senderNickname = senderNick,
                body = body,
                timestamp = timestamp,
                isMine = isCarbon && isOutgoingCarbon,
                messageType = messageType,
                status = if (isCarbon && isOutgoingCarbon) DeliveryStatus.SENT else DeliveryStatus.DELIVERED,
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

            Log.i(TAG, "Decrypted OMEMO 1-on-1 message from $fromJid (id=$stanzaId, isCarbon=$isCarbon)")
            onDecryptedMessageReceived?.invoke(xmppMsg, false)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memproses pesan OMEMO masuk: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "OmemoEncryptionManager"
        private const val DEVICE_LIST_TIMEOUT_MS = 10_000L
        private const val ENCRYPT_TIMEOUT_MS = 15_000L
    }
}
