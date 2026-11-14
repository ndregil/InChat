package com.ndregil.inchat.data.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jivesoftware.smack.StanzaListener
import org.jivesoftware.smack.filter.StanzaTypeFilter
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smackx.pubsub.EventElement
import org.jivesoftware.smackx.pubsub.ItemsExtension
import org.jivesoftware.smackx.pubsub.LeafNode
import org.jivesoftware.smackx.pubsub.PayloadItem
import org.jivesoftware.smackx.pubsub.PubSubManager
import org.jivesoftware.smackx.pubsub.SimplePayload
import org.jivesoftware.smackx.vcardtemp.VCardManager
import org.jxmpp.jid.impl.JidCreate
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager class for fetching and caching user and room avatars via XMPP VCards and PEP (XEP-0084).
 * Caches avatars in memory to avoid redundant network requests.
 */
class XmppAvatarManager(
    private val connectionManager: XmppConnectionManager,
    private val avatarDao: com.ndregil.inchat.data.local.dao.AvatarDao,
    private val scope: CoroutineScope
) {
    private val TAG = "XmppAvatarManager"

    // In-memory cache: Avatar key -> Avatar ByteArray
    private val avatarCache = ConcurrentHashMap<String, ByteArray>()

    // Negative cache: track JIDs with no avatar to avoid spamming network
    private val noAvatarSet = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    // In-flight fetches to avoid duplicate concurrent queries for the same JID
    private val inFlightSet = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    // Flow to notify subscribers when a new avatar is fetched
    private val _avatarUpdates = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val avatarUpdates: SharedFlow<String> = _avatarUpdates.asSharedFlow()

    private val fetchSemaphore = kotlinx.coroutines.sync.Semaphore(5) // Max 5 concurrent fetches
    private var currentConnection: XMPPTCPConnection? = null

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
        // Do not clear avatarCache anymore, we want persistent avatars
        setupPep(connection)
    }

    private fun setupPep(connection: XMPPTCPConnection) {
        val filter = StanzaTypeFilter(Message::class.java)
        val pepListener = StanzaListener { stanza ->
            val msg = stanza as Message
            val event = try {
                EventElement.from(msg)
            } catch (e: Exception) { null }

            if (event != null) {
                val from = msg.from?.asBareJid()?.toString() ?: return@StanzaListener
                event.extensions.filterIsInstance<ItemsExtension>().forEach { items ->
                    if (items.node == "urn:xmpp:avatar:metadata") {
                        handleMetadataEvent(from, items)
                    }
                }
            }
        }
        connection.addAsyncStanzaListener(pepListener, filter)
    }

    private fun handleMetadataEvent(fromJid: String, items: ItemsExtension) {
        val item = items.items.firstOrNull() as? PayloadItem<*> ?: return
        val payload = item.payload?.toString() ?: ""
        
        // Extract hash from <info id='...' />
        val hashRegex = "id=['\"]([^'\"]+)['\"]".toRegex()
        val match = hashRegex.find(payload)
        val hash = match?.groupValues?.get(1)

        if (!hash.isNullOrBlank()) {
            Log.d(TAG, "Received avatar metadata for $fromJid with hash: $hash")
            scope.launch {
                fetchPepAvatarData(fromJid, hash)
            }
        } else {
            // No avatar metadata means avatar was removed
            avatarCache.remove(resolveAvatarKey(fromJid))
            scope.launch { _avatarUpdates.emit(fromJid) }
        }
    }

    private suspend fun fetchPepAvatarData(fromJid: String, hash: String) = withContext(Dispatchers.IO) {
        val connection = currentConnection ?: return@withContext
        try {
            val pubSubManager = PubSubManager.getInstanceFor(connection, JidCreate.bareFrom(fromJid))
            val node = pubSubManager.getLeafNode("urn:xmpp:avatar:data")
            val items = node.getItems<PayloadItem<SimplePayload>>(listOf(hash))
            val item = items.firstOrNull() as? PayloadItem<*>
            val payload = item?.payload?.toString() ?: ""

            // Simple XML parser to get data inside <data xmlns='urn:xmpp:avatar:data'>...</data>
            val dataStart = payload.indexOf(">") + 1
            val dataEnd = payload.lastIndexOf("<")
            if (dataStart in 1 until dataEnd) {
                val base64Data = payload.substring(dataStart, dataEnd).trim().replace("\n", "").replace("\r", "")
                val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                
                val key = resolveAvatarKey(fromJid)
                avatarCache[key] = bytes
                noAvatarSet.remove(key)
                
                val nickname = if (fromJid.contains("/")) fromJid.substringAfter("/").trim() else ""
                val nickKey = nickname.lowercase()
                if (nickKey.isNotEmpty()) {
                    avatarCache[nickKey] = bytes
                    noAvatarSet.remove(nickKey)
                }

                // Save to database
                scope.launch {
                    avatarDao.insertAvatar(com.ndregil.inchat.data.local.entity.AvatarEntity(key, bytes))
                    if (nickKey.isNotEmpty()) {
                        avatarDao.insertAvatar(com.ndregil.inchat.data.local.entity.AvatarEntity(nickKey, bytes))
                    }
                }

                _avatarUpdates.emit(key)
                _avatarUpdates.emit(fromJid)
                if (nickKey.isNotEmpty()) _avatarUpdates.emit(nickKey)
                Log.i(TAG, "Successfully fetched PEP avatar for $fromJid (size: ${bytes.size} bytes)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch PEP avatar data for $fromJid: ${e.message}")
            // Fallback to VCard if PEP fails
            fetchAvatarAsync(fromJid)
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
            } catch (e: Exception) {
                pubSubManager.createNode("urn:xmpp:avatar:data") as LeafNode
            }
            
            val base64Data = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val dataPayload = SimplePayload("<data xmlns='urn:xmpp:avatar:data'>$base64Data</data>")
            dataNode.publish(PayloadItem(hash, dataPayload))

            // 2. Publish Metadata
            val metadataNode = try {
                pubSubManager.getLeafNode("urn:xmpp:avatar:metadata")
            } catch (e: Exception) {
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
                val vCard = vCardManager.loadVCard() ?: org.jivesoftware.smackx.vcardtemp.packet.VCard()
                vCard.avatar = bytes
                vCardManager.saveVCard(vCard)
                Log.i(TAG, "Also saved avatar to VCard for legacy fallback")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save VCard avatar fallback: ${e.message}")
            }

            // Update local cache
            val myJid = connection.user.asBareJid().toString()
            val key = resolveAvatarKey(myJid)
            avatarCache[key] = bytes
            
            // Save to database
            scope.launch {
                avatarDao.insertAvatar(com.ndregil.inchat.data.local.entity.AvatarEntity(key, bytes))
            }

            _avatarUpdates.emit(key)
            _avatarUpdates.emit(myJid)

            Log.i(TAG, "Published new PEP avatar with hash: $hash")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish PEP avatar: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Updates and publishes a custom room avatar for a MUC room using XEP-0084 (User Avatar) / PubSub.
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
                // 1. Try updating room vCard (XEP-0153 / vCard-temp for rooms)
                try {
                    val roomJidObj = JidCreate.from(bareKey)
                    val vcardIq = org.jivesoftware.smackx.vcardtemp.packet.VCard()
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
                    } catch (e: Exception) {
                        pubSubManager.createNode("urn:xmpp:avatar:data") as LeafNode
                    }
                    dataNode.publish(PayloadItem(hash, SimplePayload("<data xmlns='urn:xmpp:avatar:data'>$base64Data</data>")))

                    val metadataNode = try {
                        pubSubManager.getLeafNode("urn:xmpp:avatar:metadata")
                    } catch (e: Exception) {
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

            // Save to database
            scope.launch {
                avatarDao.insertAvatar(com.ndregil.inchat.data.local.entity.AvatarEntity(bareKey, bytes))
                if (bareKey != rawKey) {
                    avatarDao.insertAvatar(com.ndregil.inchat.data.local.entity.AvatarEntity(rawKey, bytes))
                }
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
               domain.contains(".conference.") ||
               domain.contains(".muc.") ||
               domain.contains(".chat.") ||
               bareJid.lowercase().contains("@conference.") ||
               bareJid.lowercase().contains("@muc.") ||
               bareJid.lowercase().contains("@chat.") ||
               bareJid.lowercase().contains("@rooms.")
    }

    /**
     * Retrieves an avatar from cache, or triggers a fetch if not cached.
     * Searches by full JID key, bare JID key, and nickname fallback.
     * @return The cached avatar bytes, or null if not yet available.
     */
    fun getAvatar(jid: String): ByteArray? {
        if (jid.isBlank()) return null
        val key = resolveAvatarKey(jid)
        if (key.isEmpty()) return null
        
        val memoryCached = avatarCache[key]
        if (memoryCached != null) return memoryCached

        // Fallback search by nickname / resourcepart across rooms
        val nickname = if (jid.contains("/")) jid.substringAfter("/").trim() else jid.trim()
        if (nickname.isNotEmpty()) {
            val nickKey = nickname.lowercase()
            val nickCached = avatarCache[nickKey]
            if (nickCached != null) {
                avatarCache[key] = nickCached
                return nickCached
            }
            // Search cache keys ending in /$nickKey or matching nickKey
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

        if (!noAvatarSet.contains(key)) {
            // Check database and trigger network fetch if missing
            fetchAvatarAsync(jid, key)
        }
        return null
    }

    /**
     * Triggers an asynchronous fetch of the VCard avatar for the given JID.
     */
    fun fetchAvatarAsync(jid: String, cacheKey: String = resolveAvatarKey(jid)) {
        if (cacheKey.isEmpty()) return
        if (avatarCache.containsKey(cacheKey) || noAvatarSet.contains(cacheKey)) return
        if (!inFlightSet.add(cacheKey)) return

        scope.launch {
            try {
                val nickname = if (jid.contains("/")) jid.substringAfter("/").trim() else jid.trim()
                val nickKey = nickname.lowercase()

                // 1. Check database first
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

                // Do not poison noAvatarSet if we are not connected/authenticated yet
                val conn = currentConnection
                if (conn == null || !conn.isAuthenticated) {
                    return@launch
                }

                // 2. Fetch from network with throttling
                fetchSemaphore.withPermit {
                    val avatar = fetchAvatarInternal(jid)
                    if (avatar != null && avatar.isNotEmpty()) {
                        avatarCache[cacheKey] = avatar
                        avatarDao.insertAvatar(com.ndregil.inchat.data.local.entity.AvatarEntity(cacheKey, avatar))
                        if (nickKey.isNotEmpty() && jid.contains("/")) {
                            avatarCache[nickKey] = avatar
                            avatarDao.insertAvatar(com.ndregil.inchat.data.local.entity.AvatarEntity(nickKey, avatar))
                        }
                        _avatarUpdates.emit(cacheKey)
                        _avatarUpdates.emit(jid)
                        if (nickKey.isNotEmpty() && jid.contains("/")) _avatarUpdates.emit(nickKey)
                    } else if (currentConnection?.isAuthenticated == true) {
                        noAvatarSet.add(cacheKey)
                    }
                }
            } catch (e: Exception) {
                if (currentConnection?.isAuthenticated == true) {
                    noAvatarSet.add(cacheKey)
                }
                Log.w(TAG, "Failed to fetch avatar for $jid: ${e.message}")
            } finally {
                inFlightSet.remove(cacheKey)
            }
        }
    }

    private suspend fun fetchAvatarInternal(targetJid: String): ByteArray? = withContext(Dispatchers.IO) {
        val connection = currentConnection ?: return@withContext null
        if (!connection.isAuthenticated) return@withContext null

        try {
            val fullJid = JidCreate.from(targetJid.trim())
            val bareJidStr = fullJid.asBareJid().toString()
            val isMuc = isMucDomain(bareJidStr)
            val hasResource = fullJid.hasResource()

            // 1. If this is a MUC occupant JID (e.g. room@conference.example.org/nickname) or MUC room bare JID (room@conference.example.org),
            // fetch XEP-0054 (vCard-temp) directly from the room or occupant JID.
            if (isMuc) {
                try {
                    val vcardIq = org.jivesoftware.smackx.vcardtemp.packet.VCard()
                    vcardIq.to = fullJid
                    vcardIq.type = org.jivesoftware.smack.packet.IQ.Type.get
                    val resultVCard = connection.sendIqRequestAndWaitForResponse<org.jivesoftware.smackx.vcardtemp.packet.VCard>(vcardIq)
                    if (resultVCard != null && resultVCard.avatar != null && resultVCard.avatar.isNotEmpty()) {
                        Log.i(TAG, "Successfully fetched XEP-0054 (vCard-temp) avatar for MUC JID $targetJid (${resultVCard.avatar.size} bytes)")
                        return@withContext resultVCard.avatar
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "XEP-0054 vCard-temp fetch for MUC JID $targetJid failed: ${e.message}")
                }
                if (hasResource) {
                    return@withContext null
                }
            }

            // 2. For standard user JIDs or bare room JIDs:
            // Try PEP (XEP-0084) pull first
            try {
                val pubSubManager = PubSubManager.getInstanceFor(connection, JidCreate.bareFrom(bareJidStr))
                val metaNode = pubSubManager.getLeafNode("urn:xmpp:avatar:metadata")
                val metaItems = metaNode.getItems<PayloadItem<SimplePayload>>()
                val latestMetaItem = metaItems.lastOrNull() as? PayloadItem<*>
                val metaPayload = latestMetaItem?.payload?.toString() ?: ""

                val hashRegex = "id=['\"]([^'\"]+)['\"]".toRegex()
                val match = hashRegex.find(metaPayload)
                val hash = match?.groupValues?.get(1)

                if (!hash.isNullOrBlank()) {
                    val dataNode = pubSubManager.getLeafNode("urn:xmpp:avatar:data")
                    val dataItems = dataNode.getItems<PayloadItem<SimplePayload>>(listOf(hash))
                    val dataItem = dataItems.firstOrNull() as? PayloadItem<*>
                    val payload = dataItem?.payload?.toString() ?: ""

                    val dataStart = payload.indexOf(">") + 1
                    val dataEnd = payload.lastIndexOf("<")
                    if (dataStart in 1 until dataEnd) {
                        val base64Data = payload.substring(dataStart, dataEnd).trim().replace("\n", "").replace("\r", "")
                        val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                        if (bytes.isNotEmpty()) {
                            Log.i(TAG, "Successfully fetched PEP avatar for $targetJid via pull (size: ${bytes.size} bytes)")
                            return@withContext bytes
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "PEP avatar pull failed for $targetJid: ${e.message}, falling back to VCard...")
            }

            // 3. Fallback to XEP-0054 (vCard-temp) for bare user JIDs
            try {
                val bareJidObj = fullJid.asBareJid().asEntityBareJidIfPossible()
                if (bareJidObj != null) {
                    val vCardManager = VCardManager.getInstanceFor(connection)
                    val vCard = vCardManager.loadVCard(bareJidObj)
                    if (vCard?.avatar != null && vCard.avatar.isNotEmpty()) {
                        Log.i(TAG, "Successfully fetched XEP-0054 (vCard-temp) avatar for $targetJid (${vCard.avatar.size} bytes)")
                        return@withContext vCard.avatar
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "VCard avatar fetch failed for $targetJid: ${e.message}")
            }

            null
        } catch (e: Exception) {
            Log.d(TAG, "No avatar found for $targetJid: ${e.message}")
            null
        }
    }

    /**
     * Clears the in-memory avatar cache.
     */
    fun clearCache() {
        avatarCache.clear()
        noAvatarSet.clear()
        scope.launch {
            avatarDao.clearAllAvatars()
        }
    }
}
