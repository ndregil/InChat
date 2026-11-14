package com.ndregil.inchat.data.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.StanzaListener
import org.jivesoftware.smack.filter.StanzaTypeFilter
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.packet.Presence
import org.jivesoftware.smack.packet.Element
import org.jivesoftware.smack.packet.XmlEnvironment
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smackx.pubsub.EventElement
import org.jivesoftware.smackx.pubsub.ItemsExtension
import org.jivesoftware.smackx.pubsub.LeafNode
import org.jivesoftware.smackx.pubsub.PayloadItem
import org.jivesoftware.smackx.pubsub.PubSubManager
import org.jivesoftware.smackx.pubsub.SimplePayload
import org.jivesoftware.smackx.vcardtemp.VCardManager
import org.jivesoftware.smackx.vcardtemp.packet.VCard
import org.jxmpp.jid.impl.JidCreate
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Manager class for fetching and caching user and room avatars via XMPP vCard-temp (XEP-0054),
 * vCard-Based Avatars (XEP-0153), and User Avatar PEP/PubSub (XEP-0084).
 * Caches avatars in memory and Room SQLite to avoid redundant network requests.
 */
class XmppAvatarManager(
    private val connectionManager: XmppConnectionManager,
    private val avatarDao: com.ndregil.inchat.data.local.dao.AvatarDao,
    private val scope: CoroutineScope
) {
    private val TAG = "XmppAvatarManager"

    // In-memory cache: Avatar key -> Avatar ByteArray
    private val avatarCache = ConcurrentHashMap<String, ByteArray>()

    // Track last known XEP-0153 photo hash per JID so changes invalidate cache
    private val photoHashCache = ConcurrentHashMap<String, String>()

    // Negative cache: track JIDs confirmed to have no avatar (never poison on timeouts)
    private val noAvatarSet = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    // In-flight fetches to avoid duplicate concurrent queries for the same JID
    private val inFlightSet = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    // Flow to notify subscribers when a new avatar is fetched
    private val _avatarUpdates = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val avatarUpdates: SharedFlow<String> = _avatarUpdates.asSharedFlow()

    private val fetchSemaphore = kotlinx.coroutines.sync.Semaphore(10)
    private var currentConnection: XMPPTCPConnection? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    init {
        scope.launch(Dispatchers.IO) {
            try {
                val dbAvatars = avatarDao.getAllAvatars()
                dbAvatars.forEach { entity ->
                    if (entity.avatarBytes.isNotEmpty()) {
                        avatarCache[entity.jid] = entity.avatarBytes
                        if (entity.jid.contains("/")) {
                            val nick = entity.jid.substringAfter("/").trim()
                            if (nick.isNotEmpty()) {
                                avatarCache[nick.lowercase()] = entity.avatarBytes
                            }
                        }
                    }
                }
                Log.i(TAG, "Preloaded ${dbAvatars.size} avatars from SQLite into memory cache.")
                if (dbAvatars.isNotEmpty()) {
                    _avatarUpdates.emit("preloaded")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to preload avatars from database: ${e.message}")
            }
        }
    }

    fun onConnectionReady(connection: XMPPTCPConnection) {
        currentConnection = connection
        noAvatarSet.clear()
        inFlightSet.clear()
        setupPepAndVCardUpdateListeners(connection)
    }

    private fun extractPayloadXml(payload: Any?): String {
        if (payload == null) return ""
        return try {
            when (payload) {
                is SimplePayload -> payload.toXML().toString()
                is Element -> payload.toXML(XmlEnvironment.EMPTY).toString()
                else -> payload.toString()
            }
        } catch (_: Exception) {
            payload.toString()
        }
    }

    private fun setupPepAndVCardUpdateListeners(connection: XMPPTCPConnection) {
        val pepListener = StanzaListener { stanza ->
            val msg = stanza as Message
            val event = try {
                EventElement.from(msg)
            } catch (_: Exception) {
                null
            }

            if (event != null) {
                val from = msg.from?.asBareJid()?.toString() ?: return@StanzaListener
                event.extensions.filterIsInstance<ItemsExtension>().forEach { items ->
                    if (items.node == "urn:xmpp:avatar:metadata") {
                        handleMetadataEvent(from, items)
                    }
                }
            }
        }
        connection.addAsyncStanzaListener(pepListener, StanzaTypeFilter(Message::class.java))

        // Listen for XEP-0153 (<x xmlns='vcard-temp:x:update'><photo>HASH</photo></x>) in presence
        val presenceListener = StanzaListener { stanza ->
            val presence = stanza as Presence
            val vcardUpdate = presence.getExtension("x", "vcard-temp:x:update") ?: return@StanzaListener
            val xml = extractPayloadXml(vcardUpdate)
            val hashMatch = Regex("<photo[^>]*>([^<]+)</photo>").find(xml)
            val hash = hashMatch?.groupValues?.get(1)?.trim() ?: return@StanzaListener
            val fromJid = presence.from?.toString() ?: return@StanzaListener
            val bareJid = presence.from?.asBareJid()?.toString() ?: fromJid.substringBefore("/")

            if (hash.isNotBlank()) {
                val targetKey = resolveAvatarKey(fromJid)
                val prevHash = photoHashCache[targetKey]
                if (prevHash != hash || !avatarCache.containsKey(targetKey)) {
                    photoHashCache[targetKey] = hash
                    noAvatarSet.remove(targetKey)
                    fetchAvatarAsync(fromJid, targetKey)
                }
                if (isMucDomain(bareJid) && !avatarCache.containsKey(bareJid.lowercase())) {
                    noAvatarSet.remove(bareJid.lowercase())
                }
            }
        }
        connection.addAsyncStanzaListener(presenceListener, StanzaTypeFilter(Presence::class.java))
    }

    private fun handleMetadataEvent(fromJid: String, items: ItemsExtension) {
        val item = items.items.firstOrNull() as? PayloadItem<*> ?: return
        val payload = extractPayloadXml(item.payload)

        val hashRegex = "id=['\"]([^'\"]+)['\"]".toRegex()
        val urlRegex = "url=['\"]([^'\"]+)['\"]".toRegex()
        val hash = hashRegex.find(payload)?.groupValues?.get(1)
        val url = urlRegex.find(payload)?.groupValues?.get(1)

        if (!hash.isNullOrBlank() || !url.isNullOrBlank()) {
            Log.d(TAG, "Received avatar metadata for $fromJid (hash=$hash, url=$url)")
            scope.launch {
                if (!hash.isNullOrBlank()) {
                    fetchPepAvatarData(fromJid, hash, url)
                } else if (!url.isNullOrBlank()) {
                    downloadHttpAvatar(url)?.let { bytes ->
                        storeAvatarBytes(fromJid, bytes)
                    }
                }
            }
        } else {
            val key = resolveAvatarKey(fromJid)
            avatarCache.remove(key)
            scope.launch { _avatarUpdates.emit(fromJid) }
        }
    }

    private suspend fun storeAvatarBytes(jid: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val key = resolveAvatarKey(jid)
        if (key.isEmpty()) return

        avatarCache[key] = bytes
        noAvatarSet.remove(key)

        val nickname = if (jid.contains("/")) jid.substringAfter("/").trim() else ""
        val nickKey = nickname.lowercase()
        if (nickKey.isNotEmpty()) {
            avatarCache[nickKey] = bytes
            noAvatarSet.remove(nickKey)
        }

        avatarDao.insertAvatar(com.ndregil.inchat.data.local.entity.AvatarEntity(key, bytes))
        if (nickKey.isNotEmpty()) {
            avatarDao.insertAvatar(com.ndregil.inchat.data.local.entity.AvatarEntity(nickKey, bytes))
        }

        _avatarUpdates.emit(key)
        _avatarUpdates.emit(jid)
        if (nickKey.isNotEmpty()) _avatarUpdates.emit(nickKey)
    }

    private suspend fun fetchPepAvatarData(
        fromJid: String,
        hash: String,
        fallbackUrl: String? = null
    ) = withContext(Dispatchers.IO) {
        val connection = currentConnection ?: return@withContext
        try {
            val pubSubManager = PubSubManager.getInstanceFor(connection, JidCreate.bareFrom(fromJid))
            val node = pubSubManager.getLeafNode("urn:xmpp:avatar:data")
            val items = node.getItems<PayloadItem<SimplePayload>>(listOf(hash))
            val item = items.firstOrNull() as? PayloadItem<*>
            val payload = extractPayloadXml(item?.payload)

            val bytes = decodeBase64DataXml(payload)
                ?: if (!fallbackUrl.isNullOrBlank()) downloadHttpAvatar(fallbackUrl) else null

            if (bytes != null && bytes.isNotEmpty()) {
                storeAvatarBytes(fromJid, bytes)
                Log.i(TAG, "Successfully fetched PEP avatar for $fromJid (size: ${bytes.size} bytes)")
                return@withContext
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch PEP avatar data for $fromJid: ${e.message}")
            if (!fallbackUrl.isNullOrBlank()) {
                downloadHttpAvatar(fallbackUrl)?.let { bytes ->
                    storeAvatarBytes(fromJid, bytes)
                    return@withContext
                }
            }
            fetchAvatarAsync(fromJid)
        }
    }

    private fun decodeBase64DataXml(xml: String): ByteArray? {
        if (xml.isBlank()) return null
        val dataStart = xml.indexOf(">") + 1
        val dataEnd = xml.lastIndexOf("<")
        if (dataStart in 1 until dataEnd) {
            val base64Data = xml.substring(dataStart, dataEnd)
                .replace("\\s+".toRegex(), "")
            if (base64Data.isNotBlank()) {
                return try {
                    android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                        .takeIf { it.isNotEmpty() }
                } catch (_: Exception) {
                    null
                }
            }
        }
        return null
    }

    private fun downloadHttpAvatar(url: String): ByteArray? {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://", ignoreCase = true) &&
            !cleanUrl.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }
        return try {
            val req = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", "InChat/1.0 (Android; XMPP)")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                bytes.takeIf { it.isNotEmpty() && it.size <= 2 * 1024 * 1024 }
            }
        } catch (e: Exception) {
            Log.d(TAG, "HTTP avatar download failed for $cleanUrl: ${e.message}")
            null
        }
    }

    suspend fun publishAvatar(bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        val connection = currentConnection ?: return@withContext Result.failure(IllegalStateException("Not connected"))
        try {
            val hash = sha1Hash(bytes)
            val pubSubManager = PubSubManager.getInstanceFor(connection)

            // 1. Publish Data
            val dataNode = try {
                pubSubManager.getLeafNode("urn:xmpp:avatar:data")
            } catch (_: Exception) {
                pubSubManager.createNode("urn:xmpp:avatar:data") as LeafNode
            }

            val base64Data = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val dataPayload = SimplePayload("<data xmlns='urn:xmpp:avatar:data'>$base64Data</data>")
            dataNode.publish(PayloadItem(hash, dataPayload))

            // 2. Publish Metadata
            val metadataNode = try {
                pubSubManager.getLeafNode("urn:xmpp:avatar:metadata")
            } catch (_: Exception) {
                pubSubManager.createNode("urn:xmpp:avatar:metadata") as LeafNode
            }

            val metadataPayload = SimplePayload(
                "<metadata xmlns='urn:xmpp:avatar:metadata'>" +
                    "<info bytes='${bytes.size}' id='$hash' type='image/png' />" +
                    "</metadata>"
            )
            metadataNode.publish(PayloadItem(hash, metadataPayload))

            // 3. Also save to VCard as legacy fallback (XEP-0153)
            try {
                val vCardManager = VCardManager.getInstanceFor(connection)
                val vCard = vCardManager.loadVCard() ?: VCard()
                vCard.avatar = bytes
                vCardManager.saveVCard(vCard)
                Log.i(TAG, "Also saved avatar to VCard for legacy fallback")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save VCard avatar fallback: ${e.message}")
            }

            val myJid = connection.user.asBareJid().toString()
            storeAvatarBytes(myJid, bytes)

            Log.i(TAG, "Published new PEP avatar with hash: $hash")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish PEP avatar: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Updates and publishes a custom room avatar for a MUC room using XEP-0054 / XEP-0084.
     */
    suspend fun updateRoomAvatar(roomJid: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val bareKey = roomJid.substringBefore("/").trim().lowercase()
            val rawKey = roomJid.trim().lowercase()
            avatarCache[bareKey] = bytes
            avatarCache[rawKey] = bytes
            noAvatarSet.remove(bareKey)
            noAvatarSet.remove(rawKey)
            _avatarUpdates.emit(bareKey)
            _avatarUpdates.emit(rawKey)
            _avatarUpdates.emit(roomJid)

            val connection = currentConnection
            if (connection != null && connection.isConnected) {
                // 1. Try updating room vCard (XEP-0054 / vCard-temp for rooms)
                try {
                    val roomJidObj = JidCreate.from(bareKey)
                    val vcardIq = VCard()
                    vcardIq.to = roomJidObj
                    vcardIq.type = org.jivesoftware.smack.packet.IQ.Type.set
                    vcardIq.setAvatar(bytes, "image/png")
                    connection.sendIqRequestAndWaitForResponse<org.jivesoftware.smack.packet.IQ>(vcardIq)
                    Log.i(TAG, "Successfully updated room vCard avatar for $bareKey")
                } catch (e: Exception) {
                    Log.w(TAG, "Room vCard avatar update for $bareKey failed: ${e.message}")
                }

                // 2. Try PubSub (XEP-0084) on room JID if supported
                try {
                    val pubSubManager = PubSubManager.getInstanceFor(connection, JidCreate.bareFrom(bareKey))
                    val hash = sha1Hash(bytes)
                    val base64Data = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

                    val dataNode = try {
                        pubSubManager.getLeafNode("urn:xmpp:avatar:data")
                    } catch (_: Exception) {
                        pubSubManager.createNode("urn:xmpp:avatar:data") as LeafNode
                    }
                    dataNode.publish(PayloadItem(hash, SimplePayload("<data xmlns='urn:xmpp:avatar:data'>$base64Data</data>")))

                    val metadataNode = try {
                        pubSubManager.getLeafNode("urn:xmpp:avatar:metadata")
                    } catch (_: Exception) {
                        pubSubManager.createNode("urn:xmpp:avatar:metadata") as LeafNode
                    }
                    val metadataPayload = SimplePayload(
                        "<metadata xmlns='urn:xmpp:avatar:metadata'>" +
                            "<info bytes='${bytes.size}' id='$hash' type='image/png' />" +
                            "</metadata>"
                    )
                    metadataNode.publish(PayloadItem(hash, metadataPayload))
                    Log.i(TAG, "Successfully published room avatar XEP-0084 for $bareKey")
                } catch (e: Exception) {
                    Log.w(TAG, "Room PubSub/XEP-0084 avatar publish note for $bareKey: ${e.message}")
                }
            }

            avatarDao.insertAvatar(com.ndregil.inchat.data.local.entity.AvatarEntity(bareKey, bytes))
            if (bareKey != rawKey) {
                avatarDao.insertAvatar(com.ndregil.inchat.data.local.entity.AvatarEntity(rawKey, bytes))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update room avatar for $roomJid: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun sha1Hash(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun resolveAvatarKey(jid: String): String {
        val clean = jid.trim()
        if (clean.isEmpty()) return ""
        if (!clean.contains("/")) {
            return clean.lowercase()
        }
        val bare = clean.substringBefore("/").lowercase()
        if (isMucDomain(bare)) {
            return clean.lowercase()
        }
        return bare
    }

    private fun isMucDomain(bareJid: String): Boolean {
        val domain = bareJid.substringAfter("@", "").lowercase()
        return domain.startsWith("conference.") ||
            domain.startsWith("conf.") ||
            domain.startsWith("muc.") ||
            domain.startsWith("chat.") ||
            domain.startsWith("rooms.") ||
            domain.startsWith("room.") ||
            domain.startsWith("group.") ||
            domain.startsWith("groups.") ||
            domain.startsWith("salons.") ||
            domain.startsWith("channel.") ||
            domain.startsWith("channels.") ||
            domain.startsWith("discuss.") ||
            domain.contains(".conference.") ||
            domain.contains(".conf.") ||
            domain.contains(".muc.") ||
            domain.contains(".chat.") ||
            domain.contains(".rooms.")
    }

    /**
     * Retrieves an avatar from cache, or triggers a fetch if not cached.
     * Searches by full JID key, bare JID key, and nickname fallback (for occupants).
     * @return The cached avatar bytes, or null if not yet available.
     */
    fun getAvatar(jid: String): ByteArray? {
        if (jid.isBlank()) return null
        val key = resolveAvatarKey(jid)
        if (key.isEmpty()) return null

        val memoryCached = avatarCache[key]
        if (memoryCached != null) return memoryCached

        // Fallback search by nickname / resourcepart only when this looks like an occupant/user
        if (jid.contains("/") || !jid.contains("@")) {
            val nickname = if (jid.contains("/")) jid.substringAfter("/").trim() else jid.trim()
            if (nickname.isNotEmpty()) {
                val nickKey = nickname.lowercase()
                val nickCached = avatarCache[nickKey]
                if (nickCached != null) {
                    avatarCache[key] = nickCached
                    return nickCached
                }
                val entry = avatarCache.entries.firstOrNull {
                    it.key.endsWith("/$nickKey", ignoreCase = true) || it.key.equals(nickKey, ignoreCase = true)
                }
                if (entry != null) {
                    val bytes = entry.value
                    avatarCache[key] = bytes
                    avatarCache[nickKey] = bytes
                    return bytes
                }
            }
        }

        if (!noAvatarSet.contains(key)) {
            fetchAvatarAsync(jid, key)
        }
        return null
    }

    /**
     * Triggers an asynchronous fetch of the VCard / PEP avatar for the given JID.
     */
    fun fetchAvatarAsync(jid: String, cacheKey: String = resolveAvatarKey(jid)) {
        if (cacheKey.isEmpty()) return
        if (avatarCache.containsKey(cacheKey) || noAvatarSet.contains(cacheKey)) return
        if (!inFlightSet.add(cacheKey)) return

        scope.launch {
            try {
                val nickname = if (jid.contains("/")) jid.substringAfter("/").trim() else ""
                val nickKey = nickname.lowercase()

                // 1. Check SQLite cache first
                val dbAvatar = avatarDao.getAvatarByJid(cacheKey)
                    ?: if (nickKey.isNotEmpty() && !jid.contains("@")) avatarDao.getAvatarByJid(nickKey) else null

                if (dbAvatar != null && dbAvatar.avatarBytes.isNotEmpty()) {
                    avatarCache[cacheKey] = dbAvatar.avatarBytes
                    if (nickKey.isNotEmpty() && jid.contains("/")) avatarCache[nickKey] = dbAvatar.avatarBytes
                    _avatarUpdates.emit(cacheKey)
                    _avatarUpdates.emit(jid)
                    if (nickKey.isNotEmpty() && jid.contains("/")) _avatarUpdates.emit(nickKey)
                    return@launch
                }

                // Do not poison noAvatarSet if not connected/authenticated yet
                val conn = currentConnection
                if (conn == null || !conn.isAuthenticated) {
                    return@launch
                }

                // 2. Fetch from network with bounded throttling & timeouts
                fetchSemaphore.withPermit {
                    val outcome = fetchAvatarInternal(jid)
                    if (outcome.bytes != null && outcome.bytes.isNotEmpty()) {
                        storeAvatarBytes(jid, outcome.bytes)
                    } else if (outcome.confirmedMissing && currentConnection?.isAuthenticated == true) {
                        noAvatarSet.add(cacheKey)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch avatar for $jid: ${e.message}")
            } finally {
                inFlightSet.remove(cacheKey)
            }
        }
    }

    private data class AvatarFetchOutcome(
        val bytes: ByteArray? = null,
        val confirmedMissing: Boolean = false
    )

    private fun extractAvatarFromVCard(vCard: VCard?): ByteArray? {
        if (vCard == null) return null

        // 1. Standard Smack decoded binary avatar (<BINVAL>)
        val direct = vCard.avatar
        if (direct != null && direct.isNotEmpty()) return direct

        // 2. Inspect raw XML for <BINVAL> or <EXTVAL> (external HTTP avatar URL)
        val xml = try {
            vCard.toXML(XmlEnvironment.EMPTY).toString()
        } catch (_: Exception) {
            ""
        }

        if (xml.isNotBlank()) {
            val binval = Regex("<BINVAL[^>]*>(.*?)</BINVAL>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                .find(xml)?.groupValues?.get(1)
                ?.replace("\\s+".toRegex(), "")
            if (!binval.isNullOrBlank()) {
                val decoded = try {
                    android.util.Base64.decode(binval, android.util.Base64.DEFAULT)
                } catch (_: Exception) {
                    null
                }
                if (decoded != null && decoded.isNotEmpty()) return decoded
            }

            val extval = vCard.getField("EXTVAL")?.trim()?.takeIf { it.isNotBlank() }
                ?: Regex("<EXTVAL[^>]*>(.*?)</EXTVAL>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                    .find(xml)?.groupValues?.get(1)?.trim()
            if (!extval.isNullOrBlank()) {
                downloadHttpAvatar(extval)?.let { return it }
            }
        }

        return null
    }

    private suspend fun fetchAvatarInternal(targetJid: String): AvatarFetchOutcome = withContext(Dispatchers.IO) {
        val connection = currentConnection ?: return@withContext AvatarFetchOutcome()
        if (!connection.isAuthenticated) return@withContext AvatarFetchOutcome()

        var sawTimeoutOrTransientError = false
        try {
            val fullJid = JidCreate.from(targetJid.trim())
            val bareJid = fullJid.asBareJid()
            val bareJidStr = bareJid.toString()
            val isMuc = isMucDomain(bareJidStr)
            val hasResource = fullJid.hasResource()

            // 1. If this is a MUC room bare JID or MUC occupant JID, query XEP-0054 vCard-temp directly
            if (isMuc) {
                try {
                    val resultVCard = withTimeoutOrNull(4_500L) {
                        val bareEntity = bareJid.asEntityBareJidIfPossible()
                        if (!hasResource && bareEntity != null) {
                            try {
                                VCardManager.getInstanceFor(connection).loadVCard(bareEntity)
                            } catch (_: Exception) {
                                val vcardIq = VCard().apply {
                                    to = fullJid
                                    type = org.jivesoftware.smack.packet.IQ.Type.get
                                }
                                connection.sendIqRequestAndWaitForResponse<VCard>(vcardIq)
                            }
                        } else {
                            val vcardIq = VCard().apply {
                                to = fullJid
                                type = org.jivesoftware.smack.packet.IQ.Type.get
                            }
                            connection.sendIqRequestAndWaitForResponse<VCard>(vcardIq)
                        }
                    }

                    if (resultVCard == null) {
                        sawTimeoutOrTransientError = true
                    } else {
                        val bytes = extractAvatarFromVCard(resultVCard)
                        if (bytes != null && bytes.isNotEmpty()) {
                            Log.i(TAG, "Fetched XEP-0054 vCard avatar for MUC $targetJid (${bytes.size} bytes)")
                            return@withContext AvatarFetchOutcome(bytes = bytes)
                        }
                    }
                } catch (e: SmackException.NoResponseException) {
                    sawTimeoutOrTransientError = true
                    Log.d(TAG, "MUC vCard timeout for $targetJid: ${e.message}")
                } catch (e: TimeoutCancellationException) {
                    sawTimeoutOrTransientError = true
                } catch (e: Exception) {
                    Log.d(TAG, "MUC vCard fetch note for $targetJid: ${e.message}")
                }

                // MUC occupants only use occupant vCard (not the room's PEP node)
                if (hasResource) {
                    return@withContext AvatarFetchOutcome(confirmedMissing = !sawTimeoutOrTransientError)
                }
            }

            // 2. Try XEP-0084 PEP / PubSub (works for users and MUC rooms supporting PubSub avatars)
            try {
                val pepBytes = withTimeoutOrNull(3_500L) {
                    val pubSubManager = PubSubManager.getInstanceFor(connection, JidCreate.bareFrom(bareJidStr))
                    val metaNode = pubSubManager.getLeafNode("urn:xmpp:avatar:metadata")
                    val metaItems = metaNode.getItems<PayloadItem<SimplePayload>>()
                    val latestMetaItem = metaItems.lastOrNull() as? PayloadItem<*>
                    val metaPayload = extractPayloadXml(latestMetaItem?.payload)

                    val hash = "id=['\"]([^'\"]+)['\"]".toRegex().find(metaPayload)?.groupValues?.get(1)
                    val infoUrl = "url=['\"]([^'\"]+)['\"]".toRegex().find(metaPayload)?.groupValues?.get(1)

                    if (!hash.isNullOrBlank()) {
                        val dataBytes = try {
                            val dataNode = pubSubManager.getLeafNode("urn:xmpp:avatar:data")
                            val dataItems = dataNode.getItems<PayloadItem<SimplePayload>>(listOf(hash))
                            val dataItem = dataItems.firstOrNull() as? PayloadItem<*>
                            val payload = extractPayloadXml(dataItem?.payload)
                            decodeBase64DataXml(payload)
                        } catch (_: Exception) {
                            null
                        }
                        dataBytes ?: if (!infoUrl.isNullOrBlank()) downloadHttpAvatar(infoUrl) else null
                    } else if (!infoUrl.isNullOrBlank()) {
                        downloadHttpAvatar(infoUrl)
                    } else {
                        null
                    }
                }

                if (pepBytes != null && pepBytes.isNotEmpty()) {
                    Log.i(TAG, "Fetched XEP-0084 PEP avatar for $targetJid (${pepBytes.size} bytes)")
                    return@withContext AvatarFetchOutcome(bytes = pepBytes)
                }
            } catch (e: SmackException.NoResponseException) {
                sawTimeoutOrTransientError = true
            } catch (e: TimeoutCancellationException) {
                sawTimeoutOrTransientError = true
            } catch (e: Exception) {
                Log.d(TAG, "PEP avatar pull skipped for $targetJid: ${e.message}")
            }

            // 3. Standard user vCard-temp fallback (skip duplicate call if already queried as MUC above)
            if (!isMuc) {
                try {
                    val bareJidObj = bareJid.asEntityBareJidIfPossible()
                    if (bareJidObj != null) {
                        val vCard = withTimeoutOrNull(4_500L) {
                            VCardManager.getInstanceFor(connection).loadVCard(bareJidObj)
                        }
                        if (vCard == null) {
                            sawTimeoutOrTransientError = true
                        } else {
                            val bytes = extractAvatarFromVCard(vCard)
                            if (bytes != null && bytes.isNotEmpty()) {
                                Log.i(TAG, "Fetched XEP-0054 vCard avatar for $targetJid (${bytes.size} bytes)")
                                return@withContext AvatarFetchOutcome(bytes = bytes)
                            }
                        }
                    }
                } catch (e: SmackException.NoResponseException) {
                    sawTimeoutOrTransientError = true
                } catch (e: Exception) {
                    Log.d(TAG, "VCard avatar fetch failed for $targetJid: ${e.message}")
                }
            }

            AvatarFetchOutcome(confirmedMissing = !sawTimeoutOrTransientError)
        } catch (e: Exception) {
            Log.d(TAG, "No avatar found for $targetJid: ${e.message}")
            AvatarFetchOutcome(confirmedMissing = false)
        }
    }

    /**
     * Clears the in-memory avatar cache.
     */
    fun clearCache() {
        avatarCache.clear()
        photoHashCache.clear()
        noAvatarSet.clear()
        scope.launch {
            avatarDao.clearAllAvatars()
        }
    }
}
