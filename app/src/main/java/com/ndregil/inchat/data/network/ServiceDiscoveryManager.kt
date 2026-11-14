package com.ndregil.inchat.data.network

import android.util.Log
import com.ndregil.inchat.data.model.DiscoveredItem
import com.ndregil.inchat.data.model.DiscoveredRoom
import com.ndregil.inchat.data.model.ServerDiscoResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager as SmackServiceDiscoveryManager
import org.jivesoftware.smackx.disco.packet.DiscoverInfo
import org.jivesoftware.smackx.disco.packet.DiscoverItems
import org.jivesoftware.smackx.muc.MultiUserChatManager as SmackMultiUserChatManager
import org.jivesoftware.smackx.muc.RoomInfo
import org.jxmpp.jid.DomainBareJid
import org.jxmpp.jid.EntityBareJid
import org.jxmpp.jid.impl.JidCreate

/**
 * Manager class implementing XMPP Service Discovery (XEP-0030) and MUC Room Discovery.
 *
 * Allows querying:
 * - Hosted items (disco#items) on server entities and conference components
 * - Identity and feature information (disco#info)
 * - MUC service domains (e.g. conference.domain.com)
 * - Fast manual server disco (e.g. jabber.ru, conference.jabber.ru)
 * - Available public chat rooms with background RoomInfo enrichment
 */
class ServiceDiscoveryManager(
    private val connectionManager: XmppConnectionManager
) {
    companion object {
        private const val TAG = "ServiceDiscoveryManager"
        const val MUC_FEATURE_NAMESPACE = "http://jabber.org/protocol/muc"
        const val DISCO_INFO_NAMESPACE = "http://jabber.org/protocol/disco#info"
        const val DISCO_ITEMS_NAMESPACE = "http://jabber.org/protocol/disco#items"
        private const val DISCO_TIMEOUT_MS = 8_000L
        private const val ROOM_INFO_ENRICH_LIMIT = 12
        private const val ROOM_INFO_ENRICH_TIMEOUT_MS = 2_500L
        private val TRAILING_COUNT_REGEX = Regex("""^(.+?)\s*\((\d+)\)\s*$""")
    }

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var enrichmentJob: Job? = null

    // State flow of discovered rooms
    private val _discoveredRooms = MutableStateFlow<List<DiscoveredRoom>>(emptyList())
    val discoveredRooms: StateFlow<List<DiscoveredRoom>> = _discoveredRooms.asStateFlow()

    // State flow of discovered MUC service domains
    private val _mucServiceDomains = MutableStateFlow<List<String>>(emptyList())
    val mucServiceDomains: StateFlow<List<String>> = _mucServiceDomains.asStateFlow()

    // Loading status
    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    // Manual Server Disco state
    private val _serverDiscoResult = MutableStateFlow(ServerDiscoResult())
    val serverDiscoResult: StateFlow<ServerDiscoResult> = _serverDiscoResult.asStateFlow()

    private val _isDiscoServerLoading = MutableStateFlow(false)
    val isDiscoServerLoading: StateFlow<Boolean> = _isDiscoServerLoading.asStateFlow()

    /**
     * Helper to obtain Smack's underlying ServiceDiscoveryManager.
     */
    private fun getSmackDiscoManager(): SmackServiceDiscoveryManager {
        val conn = connectionManager.getRawConnection()
            ?: throw IllegalStateException("XMPP connection is not established. Please connect first.")
        if (!conn.isAuthenticated) {
            throw IllegalStateException("XMPP user is not authenticated. Please log in first.")
        }
        return SmackServiceDiscoveryManager.getInstanceFor(conn)
    }

    /**
     * Helper to obtain Smack's MultiUserChatManager for room info & service domain discovery.
     */
    private fun getSmackMucManager(): SmackMultiUserChatManager {
        val conn = connectionManager.getRawConnection()
            ?: throw IllegalStateException("XMPP connection is not established. Please connect first.")
        if (!conn.isAuthenticated) {
            throw IllegalStateException("XMPP user is not authenticated. Please log in first.")
        }
        return SmackMultiUserChatManager.getInstanceFor(conn)
    }

    private fun parseRoomItem(item: DiscoverItems.Item, serviceJid: String): DiscoveredRoom {
        val itemJid = item.entityID.toString()
        val rawName = item.name?.trim().orEmpty()
        var cleanName = rawName.ifBlank { itemJid.substringBefore("@") }
        var parsedOccupants = 0

        if (rawName.isNotBlank()) {
            TRAILING_COUNT_REGEX.matchEntire(rawName)?.let { match ->
                val baseName = match.groupValues[1].trim()
                val count = match.groupValues[2].toIntOrNull()
                if (baseName.isNotBlank() && count != null) {
                    cleanName = baseName
                    parsedOccupants = count
                }
            }
        }

        return DiscoveredRoom(
            jid = itemJid,
            name = cleanName,
            serviceJid = serviceJid,
            description = "",
            occupantsCount = parsedOccupants,
            subject = ""
        )
    }

    /**
     * Performs fast XMPP Service Discovery (disco#info + disco#items) on any target domain or service JID
     * (e.g., "jabber.ru" or "conference.jabber.ru").
     */
    suspend fun discoServer(rawTargetJid: String, node: String? = null): ServerDiscoResult = withContext(Dispatchers.IO) {
        val cleanedTarget = rawTargetJid
            .trim()
            .removePrefix("xmpp:")
            .substringBefore("?")
            .trimEnd('/')
            .lowercase()

        if (cleanedTarget.isBlank()) {
            val emptyResult = ServerDiscoResult(errorMessage = "Masukkan domain server XMPP (contoh: jabber.ru)")
            _serverDiscoResult.value = emptyResult
            return@withContext emptyResult
        }

        if (!connectionManager.isConnected() || !connectionManager.isAuthenticated()) {
            val offlineResult = ServerDiscoResult(
                targetJid = cleanedTarget,
                errorMessage = "Belum terhubung ke server XMPP. Silakan login terlebih dahulu."
            )
            _serverDiscoResult.value = offlineResult
            return@withContext offlineResult
        }

        enrichmentJob?.cancel()
        _isDiscoServerLoading.value = true

        try {
            val discoManager = getSmackDiscoManager()
            val jid = JidCreate.from(cleanedTarget)
            val cleanNode = node?.takeIf { it.isNotBlank() }

            val (infoResult, itemsResult) = coroutineScope {
                val infoDeferred = async {
                    withTimeoutOrNull(DISCO_TIMEOUT_MS) {
                        runCatching {
                            if (cleanNode != null) discoManager.discoverInfo(jid, cleanNode)
                            else discoManager.discoverInfo(jid)
                        }.getOrNull()
                    }
                }
                val itemsDeferred = async {
                    withTimeoutOrNull(DISCO_TIMEOUT_MS) {
                        runCatching {
                            if (cleanNode != null) discoManager.discoverItems(jid, cleanNode)
                            else discoManager.discoverItems(jid)
                        }
                    }
                }
                infoDeferred.await() to itemsDeferred.await()
            }

            val identityLabel = infoResult?.identities
                ?.firstOrNull()
                ?.let { identity ->
                    val namePart = identity.name?.takeIf { it.isNotBlank() }
                    val typePart = listOfNotNull(identity.category, identity.type).joinToString("/")
                    namePart ?: typePart
                }
                .orEmpty()

            val featuresList = infoResult?.features?.mapNotNull { it.`var` } ?: emptyList()

            if (itemsResult == null) {
                val timeoutResult = ServerDiscoResult(
                    targetJid = cleanedTarget,
                    serverIdentity = identityLabel,
                    features = featuresList,
                    errorMessage = "Waktu koneksi ke $cleanedTarget habis (timeout). Server mungkin lambat atau tidak merespons."
                )
                _serverDiscoResult.value = timeoutResult
                return@withContext timeoutResult
            }

            val discoverItems = itemsResult.getOrNull()
            if (discoverItems == null) {
                val ex = itemsResult.exceptionOrNull()
                val errResult = ServerDiscoResult(
                    targetJid = cleanedTarget,
                    serverIdentity = identityLabel,
                    features = featuresList,
                    errorMessage = if (featuresList.isNotEmpty()) {
                        null
                    } else {
                        "Gagal disco ke $cleanedTarget: ${ex?.localizedMessage ?: "Server menolak atau tidak mendukung disco#items"}"
                    }
                )
                _serverDiscoResult.value = errResult
                return@withContext errResult
            }

            val services = mutableListOf<DiscoveredItem>()
            val rooms = mutableListOf<DiscoveredRoom>()

            for (item in discoverItems.items) {
                val itemJid = item.entityID.toString()
                if (itemJid.contains("@")) {
                    rooms.add(parseRoomItem(item, cleanedTarget))
                } else {
                    services.add(
                        DiscoveredItem(
                            jid = itemJid,
                            name = item.name?.takeIf { it.isNotBlank() } ?: itemJid,
                            node = item.node
                        )
                    )
                }
            }

            val result = ServerDiscoResult(
                targetJid = cleanedTarget,
                services = services,
                rooms = rooms,
                serverIdentity = identityLabel,
                features = featuresList,
                errorMessage = null
            )
            _serverDiscoResult.value = result
            Log.i(TAG, "Disco $cleanedTarget completed: ${services.size} services, ${rooms.size} rooms")

            // Enrich up to the first ROOM_INFO_ENRICH_LIMIT rooms in background without blocking UI loading state
            if (rooms.isNotEmpty() && rooms.size <= 40) {
                launchRoomEnrichmentForServerDisco(cleanedTarget, rooms)
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "discoServer error for $cleanedTarget: ${e.message}", e)
            val errResult = ServerDiscoResult(
                targetJid = cleanedTarget,
                errorMessage = "Gagal disco ke $cleanedTarget: ${e.localizedMessage ?: "Terjadi kesalahan"}"
            )
            _serverDiscoResult.value = errResult
            errResult
        } finally {
            _isDiscoServerLoading.value = false
        }
    }

    private fun launchRoomEnrichmentForServerDisco(targetJid: String, initialRooms: List<DiscoveredRoom>) {
        enrichmentJob = backgroundScope.launch {
            try {
                if (!connectionManager.isConnected() || !connectionManager.isAuthenticated()) return@launch
                val mucManager = getSmackMucManager()
                val toEnrich = initialRooms.take(ROOM_INFO_ENRICH_LIMIT)

                val enrichedMap = withTimeoutOrNull(ROOM_INFO_ENRICH_TIMEOUT_MS) {
                    coroutineScope {
                        toEnrich.map { room ->
                            async {
                                try {
                                    val info = mucManager.getRoomInfo(JidCreate.entityBareFrom(room.jid))
                                    room.jid to room.copy(
                                        name = info.name?.takeIf { it.isNotBlank() } ?: room.name,
                                        description = info.description ?: room.description,
                                        occupantsCount = if (info.occupantsCount >= 0) info.occupantsCount else room.occupantsCount,
                                        subject = info.subject ?: room.subject,
                                        isPasswordProtected = info.isPasswordProtected,
                                        isMembersOnly = info.isMembersOnly,
                                        isPersistent = info.isPersistent
                                    )
                                } catch (_: Exception) {
                                    null
                                }
                            }
                        }.awaitAll().filterNotNull().toMap()
                    }
                } ?: emptyMap()

                if (enrichedMap.isNotEmpty() && _serverDiscoResult.value.targetJid == targetJid) {
                    val updatedRooms = _serverDiscoResult.value.rooms.map { room ->
                        enrichedMap[room.jid] ?: room
                    }
                    _serverDiscoResult.value = _serverDiscoResult.value.copy(rooms = updatedRooms)
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Queries MUC service domains hosted on the connected XMPP server (e.g., "conference.example.org").
     * Uses XEP-0030 service discovery and MUC feature matching.
     */
    suspend fun findMucServices(): Result<List<String>> = withContext(Dispatchers.IO) {
        if (!connectionManager.isConnected() || !connectionManager.isAuthenticated()) {
            val fallbackDomain = "conference.${connectionManager.currentCredentials.domain}"
            val list = listOf(fallbackDomain)
            _mucServiceDomains.value = list
            return@withContext Result.success(list)
        }

        try {
            val smackMucManager = getSmackMucManager()
            val domains: List<DomainBareJid> = withTimeoutOrNull(DISCO_TIMEOUT_MS) {
                smackMucManager.mucServiceDomains
            } ?: emptyList()
            val domainStrings = domains.map { it.toString() }

            val resultList = if (domainStrings.isNotEmpty()) {
                domainStrings
            } else {
                listOf("conference.${connectionManager.currentCredentials.domain}")
            }

            _mucServiceDomains.value = resultList
            Log.d(TAG, "Discovered MUC service domains: $resultList")
            Result.success(resultList)
        } catch (e: Exception) {
            Log.w(TAG, "Error finding MUC services via Smack: ${e.message}")
            val fallbackDomain = "conference.${connectionManager.currentCredentials.domain}"
            val fallbackList = listOf(fallbackDomain)
            _mucServiceDomains.value = fallbackList
            Result.success(fallbackList)
        }
    }

    /**
     * Queries items (XEP-0030 disco#items) hosted under a given JID.
     */
    suspend fun discoverItems(targetJid: String): Result<List<DiscoveredItem>> = withContext(Dispatchers.IO) {
        if (!connectionManager.isConnected() || !connectionManager.isAuthenticated()) {
            return@withContext Result.failure(IllegalStateException("Not connected or authenticated"))
        }

        try {
            val discoManager = getSmackDiscoManager()
            val jid = JidCreate.from(targetJid.trim())
            val discoItems: DiscoverItems = withTimeoutOrNull(DISCO_TIMEOUT_MS) {
                discoManager.discoverItems(jid)
            } ?: return@withContext Result.failure(Exception("Server tidak merespon permintaan daftar item (Timeout)."))

            val items = discoItems.items.map { item ->
                DiscoveredItem(
                    jid = item.entityID.toString(),
                    name = item.name ?: item.entityID.toString().substringBefore("@"),
                    node = item.node
                )
            }
            Result.success(items)
        } catch (e: org.jivesoftware.smack.SmackException.NoResponseException) {
            Log.e(TAG, "Discovery items timeout on $targetJid: ${e.message}")
            Result.failure(Exception("Server tidak merespon permintaan daftar item (Timeout).", e))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to discover items on $targetJid: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Queries information (XEP-0030 disco#info) for a specific entity JID.
     */
    suspend fun discoverInfo(targetJid: String): Result<DiscoverInfo> = withContext(Dispatchers.IO) {
        if (!connectionManager.isConnected() || !connectionManager.isAuthenticated()) {
            return@withContext Result.failure(IllegalStateException("Not connected or authenticated"))
        }

        try {
            val discoManager = getSmackDiscoManager()
            val jid = JidCreate.from(targetJid.trim())
            val info = withTimeoutOrNull(DISCO_TIMEOUT_MS) {
                discoManager.discoverInfo(jid)
            } ?: return@withContext Result.failure(Exception("Server tidak merespon permintaan informasi (Timeout)."))
            Result.success(info)
        } catch (e: org.jivesoftware.smack.SmackException.NoResponseException) {
            Log.e(TAG, "Discovery info timeout on $targetJid: ${e.message}")
            Result.failure(Exception("Server tidak merespon permintaan informasi (Timeout).", e))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to discover info for $targetJid: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Checks if a target JID advertises support for a specific feature namespace.
     */
    suspend fun supportsFeature(targetJid: String, featureNamespace: String): Boolean = withContext(Dispatchers.IO) {
        if (!connectionManager.isConnected() || !connectionManager.isAuthenticated()) {
            return@withContext false
        }
        try {
            val discoManager = getSmackDiscoManager()
            val jid = JidCreate.from(targetJid.trim())
            withTimeoutOrNull(DISCO_TIMEOUT_MS) {
                discoManager.supportsFeature(jid, featureNamespace)
            } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Error checking feature support: ${e.message}")
            false
        }
    }

    /**
     * Fetches detailed information for a specific MUC room using XEP-0045/XEP-0030 RoomInfo.
     */
    suspend fun getRoomDetails(roomJid: String): Result<DiscoveredRoom> = withContext(Dispatchers.IO) {
        if (!connectionManager.isConnected() || !connectionManager.isAuthenticated()) {
            return@withContext Result.failure(IllegalStateException("Not connected or authenticated"))
        }

        try {
            val mucManager = getSmackMucManager()
            val bareJid: EntityBareJid = JidCreate.entityBareFrom(roomJid.trim())
            val roomInfo: RoomInfo = withTimeoutOrNull(DISCO_TIMEOUT_MS) {
                mucManager.getRoomInfo(bareJid)
            } ?: return@withContext Result.failure(Exception("Timeout mengambil detail room"))

            val discoveredRoom = DiscoveredRoom(
                jid = roomInfo.room.toString(),
                name = roomInfo.name ?: roomJid.substringBefore("@"),
                serviceJid = bareJid.domain.toString(),
                description = roomInfo.description ?: "",
                occupantsCount = if (roomInfo.occupantsCount >= 0) roomInfo.occupantsCount else 0,
                subject = roomInfo.subject ?: "",
                isPasswordProtected = roomInfo.isPasswordProtected,
                isMembersOnly = roomInfo.isMembersOnly,
                isPersistent = roomInfo.isPersistent
            )

            Result.success(discoveredRoom)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch room info for $roomJid: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Discovers available MUC chat rooms on the server or a specific MUC service quickly via disco#items.
     * Avoids blocking UI on sequential per-room RoomInfo roundtrips.
     */
    suspend fun discoverRooms(serviceJid: String? = null): Result<List<DiscoveredRoom>> = withContext(Dispatchers.IO) {
        _isDiscovering.value = true
        try {
            if (!connectionManager.isConnected() || !connectionManager.isAuthenticated()) {
                val sampleRooms = getFallbackDiscoveredRooms()
                _discoveredRooms.value = sampleRooms
                return@withContext Result.success(sampleRooms)
            }

            val discoManager = getSmackDiscoManager()

            val servicesToQuery = if (serviceJid != null && serviceJid.isNotBlank()) {
                listOf(serviceJid.trim())
            } else {
                val foundServices = findMucServices().getOrNull()
                if (!foundServices.isNullOrEmpty()) {
                    foundServices
                } else {
                    listOf("conference.${connectionManager.currentCredentials.domain}")
                }
            }

            val allDiscoveredRooms = mutableListOf<DiscoveredRoom>()

            for (service in servicesToQuery) {
                try {
                    val serviceDomainJid = JidCreate.from(service)
                    val discoverItems = withTimeoutOrNull(DISCO_TIMEOUT_MS) {
                        discoManager.discoverItems(serviceDomainJid)
                    } ?: continue

                    for (item in discoverItems.items) {
                        val itemJid = item.entityID.toString()
                        if (itemJid.contains("@")) {
                            allDiscoveredRooms.add(parseRoomItem(item, service))
                        }
                    }
                } catch (serviceEx: Exception) {
                    Log.w(TAG, "Failed to query rooms from service $service: ${serviceEx.message}")
                }
            }

            _discoveredRooms.value = allDiscoveredRooms
            Log.i(TAG, "Fast-discovered ${allDiscoveredRooms.size} MUC rooms via disco#items")
            Result.success(allDiscoveredRooms)
        } catch (e: Exception) {
            Log.e(TAG, "Service discovery failed: ${e.message}", e)
            val fallback = getFallbackDiscoveredRooms()
            _discoveredRooms.value = fallback
            Result.success(fallback)
        } finally {
            _isDiscovering.value = false
        }
    }

    /**
     * Fallback standard rooms for disconnected or initial display.
     */
    private fun getFallbackDiscoveredRooms(): List<DiscoveredRoom> {
        return emptyList()
    }
}
