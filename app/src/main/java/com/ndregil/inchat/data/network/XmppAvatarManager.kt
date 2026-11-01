package com.ndregil.inchat.data.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
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
    private val scope: CoroutineScope
) {
    private val TAG = "XmppAvatarManager"

    // In-memory cache: Avatar key -> Avatar ByteArray
    private val avatarCache = ConcurrentHashMap<String, ByteArray>()

    // Negative cache: track JIDs with no avatar to avoid spamming network
    private val noAvatarSet = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    // Flow to notify subscribers when a new avatar is fetched
    private val _avatarUpdates = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val avatarUpdates: SharedFlow<String> = _avatarUpdates.asSharedFlow()

    private var currentConnection: XMPPTCPConnection? = null

    fun onConnectionReady(connection: XMPPTCPConnection) {
        currentConnection = connection
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
                _avatarUpdates.emit(key)
                _avatarUpdates.emit(fromJid)
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
            _avatarUpdates.emit(key)
            _avatarUpdates.emit(myJid)

            Log.i(TAG, "Published new PEP avatar with hash: $hash")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish PEP avatar: ${e.message}", e)
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
               domain.startsWith("muc.") ||
               domain.contains(".conference.") ||
               domain.contains(".muc.") ||
               bareJid.lowercase().contains("@conference.") ||
               bareJid.lowercase().contains("@muc.")
    }

    /**
     * Retrieves an avatar from cache, or triggers a fetch if not cached.
     * @return The cached avatar bytes, or null if not yet available.
     */
    fun getAvatar(jid: String): ByteArray? {
        if (jid.isBlank()) return null
        val key = resolveAvatarKey(jid)
        if (key.isEmpty()) return null
        val cached = avatarCache[key]
        if (cached == null && !noAvatarSet.contains(key)) {
            fetchAvatarAsync(jid, key)
        }
        return cached
    }

    /**
     * Triggers an asynchronous fetch of the VCard avatar for the given JID.
     */
    fun fetchAvatarAsync(jid: String, cacheKey: String = resolveAvatarKey(jid)) {
        if (cacheKey.isEmpty()) return
        if (avatarCache.containsKey(cacheKey) || noAvatarSet.contains(cacheKey)) return

        scope.launch {
            try {
                val avatar = fetchAvatarInternal(jid)
                if (avatar != null) {
                    avatarCache[cacheKey] = avatar
                    _avatarUpdates.emit(cacheKey)
                    _avatarUpdates.emit(jid)
                } else {
                    noAvatarSet.add(cacheKey)
                }
            } catch (e: Exception) {
                noAvatarSet.add(cacheKey)
                Log.w(TAG, "Failed to fetch avatar for $jid: ${e.message}")
            }
        }
    }

    private suspend fun fetchAvatarInternal(targetJid: String): ByteArray? = withContext(Dispatchers.IO) {
        val connection = connectionManager.getRawConnection() ?: return@withContext null
        if (!connection.isAuthenticated) return@withContext null

        try {
            val bareJidStr = JidCreate.bareFrom(targetJid.trim()).toString()

            // 1. Try PEP (XEP-0084) pull first
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

            // 2. Fallback to VCard (XEP-0153 / vcard-temp)
            try {
                val jidObj = JidCreate.from(targetJid.trim())
                val vCardManager = VCardManager.getInstanceFor(connection)
                val bareJidObj = jidObj.asBareJid().asEntityBareJidIfPossible()
                if (bareJidObj != null) {
                    val vCard = vCardManager.loadVCard(bareJidObj)
                    if (vCard?.avatar != null) {
                        Log.i(TAG, "Successfully fetched VCard avatar for $targetJid")
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
    }
}
