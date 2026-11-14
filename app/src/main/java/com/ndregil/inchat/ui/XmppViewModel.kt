package com.ndregil.inchat.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ndregil.inchat.data.local.AppDatabase
import com.ndregil.inchat.data.local.entity.RoomConfigEntity
import com.ndregil.inchat.data.model.ConnectionStatus
import com.ndregil.inchat.data.model.DiscoveredRoom
import com.ndregil.inchat.data.model.MessageType
import com.ndregil.inchat.data.model.OccupantRole
import com.ndregil.inchat.data.model.PublicDirectoryRoom
import com.ndregil.inchat.data.model.RoomOccupant
import com.ndregil.inchat.data.model.RosterContact
import com.ndregil.inchat.data.model.ServerDiscoResult
import com.ndregil.inchat.data.model.StanzaLog
import com.ndregil.inchat.data.model.UserPresence
import com.ndregil.inchat.data.model.XmppAccount
import com.ndregil.inchat.data.model.XmppMessage
import com.ndregil.inchat.data.model.XmppRoom
import com.ndregil.inchat.data.repository.XmppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

sealed class RegistrationState {
    object Idle : RegistrationState()
    object Registering : RegistrationState()
    data class Success(val jid: String, val autoLoggingIn: Boolean) : RegistrationState()
    data class Error(val message: String) : RegistrationState()
    data class CaptchaChallengeRequired(val challenge: com.ndregil.inchat.data.network.CaptchaChallengeData) : RegistrationState()
}

@OptIn(ExperimentalCoroutinesApi::class)
class XmppViewModel(application: Application) : AndroidViewModel(application) {

    val repository = XmppRepository.getInstance(application)

    val connectionStatus: StateFlow<ConnectionStatus> = repository.connectionStatus
    val connectionError: StateFlow<String?> = repository.connectionError
    val currentAccount: StateFlow<XmppAccount> = repository.currentAccount
    val stanzaLogs: StateFlow<List<StanzaLog>> = repository.stanzaLogs
    val allRoomOccupants: StateFlow<Map<String, List<RoomOccupant>>> = repository.roomOccupants
    val rosterContacts: StateFlow<List<RosterContact>> = repository.rosterContacts
    val rosterPresenceMap: StateFlow<Map<String, UserPresence>> = repository.rosterPresenceMap
    val isRosterLoading: StateFlow<Boolean> = repository.isRosterLoading
    val autoApproveSubscription: StateFlow<Boolean> = repository.autoApproveSubscription
    fun setAutoApproveSubscription(enabled: Boolean) {
        repository.setAutoApproveSubscription(enabled)
    }
    val myPresence: StateFlow<UserPresence> = repository.userPresence
    val myStatusMessage: StateFlow<String> = repository.userStatusMessage
    val currentCall: StateFlow<com.ndregil.inchat.data.model.CallInfo?> = repository.currentCall
    
    val myAvatar: StateFlow<ByteArray?> = combine(currentAccount, repository.smackAvatarManager.avatarUpdates) { acc, _ ->
        repository.smackAvatarManager.getAvatar(acc.jid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val translationPreferences: StateFlow<com.ndregil.inchat.data.model.TranslationPreferences> =
        repository.translationPreferences.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            com.ndregil.inchat.data.model.TranslationPreferences()
        )

    fun updateTranslationPreferences(prefs: com.ndregil.inchat.data.model.TranslationPreferences) {
        viewModelScope.launch {
            repository.updateTranslationPreferences(prefs)
        }
    }

    fun translateMessage(messageId: String) {
        viewModelScope.launch {
            val targetLang = translationPreferences.value.targetLanguageCode
            repository.translateMessage(messageId, targetLang)
        }
    }

    val autoJoinToastStatus = MutableStateFlow<String?>(null)
    fun clearAutoJoinToastStatus() { autoJoinToastStatus.value = null }

    val isSyncingBookmarks = MutableStateFlow(false)
    fun syncBookmarks() {
        viewModelScope.launch {
            isSyncingBookmarks.value = true
            try {
                repository.syncServerBookmarks()
                autoJoinToastStatus.value = "Favorit berhasil disinkronkan dari server."
            } catch (e: Exception) {
                Log.e("XmppViewModel", "Gagal sinkronisasi favorit: ${e.message}")
                autoJoinToastStatus.value = "Gagal sinkronisasi: ${e.message}"
            } finally {
                isSyncingBookmarks.value = false
            }
        }
    }

    private val _avatarRevision = MutableStateFlow(0)
    val avatarRevision: StateFlow<Int> = _avatarRevision.asStateFlow()

    fun getRoomAvatar(roomJid: String): ByteArray? {
        return repository.getRoomAvatar(roomJid)
    }

    val ownersList = MutableStateFlow<List<com.ndregil.inchat.data.model.MucAffiliate>>(emptyList())
    val adminsList = MutableStateFlow<List<com.ndregil.inchat.data.model.MucAffiliate>>(emptyList())
    val membersList = MutableStateFlow<List<com.ndregil.inchat.data.model.MucAffiliate>>(emptyList())
    val outcastsList = MutableStateFlow<List<com.ndregil.inchat.data.model.MucAffiliate>>(emptyList())
    val isLoadingAffiliations = MutableStateFlow(false)

    val themePreferences: StateFlow<com.ndregil.inchat.data.model.ThemePreferences> = repository.themePreferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.ndregil.inchat.data.model.ThemePreferences()
    )

    fun updateThemePreferences(prefs: com.ndregil.inchat.data.model.ThemePreferences) {
        viewModelScope.launch {
            repository.updateThemePreferences(prefs)
        }
    }

    init {
        viewModelScope.launch {
            repository.autoJoinEvents.collect { msg ->
                autoJoinToastStatus.value = msg
            }
        }
        viewModelScope.launch {
            repository.smackAvatarManager.avatarUpdates.collect {
                _avatarRevision.value += 1
            }
        }
    }

    // Search filter query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Raw rooms list
    val allRooms: StateFlow<List<XmppRoom>> = repository.rooms.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val groupRooms: StateFlow<List<XmppRoom>> = allRooms.map { rooms ->
        rooms.filter { it.isGroupChat }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val directChats: StateFlow<List<XmppRoom>> = allRooms.map { rooms ->
        rooms.filter { room ->
            !room.isGroupChat && 
            room.lastMessageText.isNotBlank() && 
            room.lastMessageText != "Sinkronisasi dari server" &&
            room.lastMessageText != "Ruangan disimpan ke favorit" &&
            room.lastMessageText != "Baru saja bergabung" &&
            room.lastMessageText != "Sinkronisasi Favorit" &&
            room.lastMessageText != "Obrolan Langsung (1-on-1)" &&
            room.lastMessageText != "Ruangan MUC"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Room Configurations (persisted in Room DB)
    val roomConfigs: StateFlow<List<RoomConfigEntity>> = repository.roomConfigs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Filtered rooms
    val filteredRooms: StateFlow<List<XmppRoom>> = combine(groupRooms, _searchQuery) { rooms, query ->
        if (query.isBlank()) {
            rooms
        } else {
            rooms.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.jid.contains(query, ignoreCase = true) ||
                it.topic.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val filteredDirectChats: StateFlow<List<XmppRoom>> = combine(directChats, _searchQuery) { rooms, query ->
        if (query.isBlank()) {
            rooms
        } else {
            rooms.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.jid.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Saved / Bookmarked rooms
    val savedRooms: StateFlow<List<XmppRoom>> = combine(groupRooms, roomConfigs) { rooms, configs ->
        val bookmarkedConfigJids = configs.filter { it.isBookmarked }.map { it.roomJid }.toSet()
        rooms.filter { it.isBookmarked || bookmarkedConfigJids.contains(it.jid) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val savedDirectChats: StateFlow<List<XmppRoom>> = combine(directChats, roomConfigs) { rooms, configs ->
        val bookmarkedConfigJids = configs.filter { it.isBookmarked }.map { it.roomJid }.toSet()
        rooms.filter { it.isBookmarked || bookmarkedConfigJids.contains(it.jid) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Currently selected / active room JID
    private val _selectedRoomJid = MutableStateFlow<String?>(null)
    val selectedRoomJid: StateFlow<String?> = _selectedRoomJid.asStateFlow()

    // Active room object with graceful fallback or virtual room for new chats
    val activeRoom: StateFlow<XmppRoom?> = combine(allRooms, _selectedRoomJid) { rooms, jid ->
        if (jid == null) {
            return@combine null
        }
        
        val found = rooms.find { it.jid == jid }
        if (found != null) return@combine found
        
        // If JID is specified but not in DB yet, create a "virtual" placeholder room
        // so that messages can be sent and received immediately.
        val isGroup = jid.contains("conference.", ignoreCase = true) || jid.contains("muc.", ignoreCase = true)
        XmppRoom(
            jid = jid,
            name = jid.substringBefore("@"),
            topic = if (isGroup) "Ruangan MUC" else "Obrolan Langsung (1-on-1)",
            isGroupChat = isGroup,
            isJoined = isGroup,
            isBookmarked = false,
            lastMessageText = ""
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly, // Use Eagerly to keep activeRoom ready
        initialValue = null
    )

    private val _roomMessageLimits = MutableStateFlow<Map<String, Int>>(emptyMap())
    val isLoadingOlderMessages = MutableStateFlow(false)

    // Messages for active room
    val activeRoomMessages: StateFlow<List<XmppMessage>> = combine(activeRoom, _roomMessageLimits) { room, limits ->
        room to (limits[room?.jid] ?: 100)
    }.transformLatest { (room, limit) ->
        val repo = repository
        if (room != null && repo != null && room.jid.isNotBlank()) {
            try {
                val flow = repo.getMessagesForRoom(room.jid, maxCount = limit)
                if (flow != null) {
                    emitAll(flow)
                } else {
                    Log.e("XmppViewModel", "repository.getMessagesForRoom returned null flow for ${room.jid}")
                    emit(emptyList())
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e("XmppViewModel", "Error getting messages flow for ${room.jid}", e)
                emit(emptyList())
            }
        } else {
            emit(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun loadOlderMessagesForActiveRoom(onComplete: (Int) -> Unit = {}) {
        val room = activeRoom.value ?: return
        val currentLimit = _roomMessageLimits.value[room.jid] ?: 100
        if (currentLimit >= 300) {
            onComplete(0)
            return
        }

        val newLimit = (currentLimit + 50).coerceAtMost(300)
        _roomMessageLimits.value = _roomMessageLimits.value + (room.jid to newLimit)


        viewModelScope.launch {
            isLoadingOlderMessages.value = true
            try {
                val fetchedCount = repository.loadOlderMamHistory(room.jid, limit = 100)
                onComplete(fetchedCount)
            } catch (e: Exception) {
                Log.w("XmppViewModel", "Gagal memuat MAM history terdahulu untuk ${room.jid}: ${e.message}")
                onComplete(0)
            } finally {
                isLoadingOlderMessages.value = false
            }
        }
    }

    // Occupants for active room
    val activeRoomOccupants: StateFlow<List<RoomOccupant>> = combine(allRoomOccupants, activeRoom) { map, room ->
        if (room != null) map[room.jid] ?: emptyList() else emptyList()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Moderation History for active room
    val activeRoomModerationHistory: StateFlow<List<XmppMessage>> = activeRoomMessages.map { list ->
        list.filter { it.messageType == MessageType.MODERATION }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Public Directory & XEP-0030 Service Discovery
    private val _directoryRooms = MutableStateFlow<List<PublicDirectoryRoom>>(emptyList())
    val directoryRooms: StateFlow<List<PublicDirectoryRoom>> = _directoryRooms.asStateFlow()

    private val _isDirectoryLoading = MutableStateFlow(false)
    val isDirectoryLoading: StateFlow<Boolean> = _isDirectoryLoading.asStateFlow()

    val discoveredRooms: StateFlow<List<DiscoveredRoom>> = repository.discoveredRooms
    val isDiscovering: StateFlow<Boolean> = repository.isDiscovering
    val mucServiceDomains: StateFlow<List<String>> = repository.mucServiceDomains
    val serverDiscoResult: StateFlow<ServerDiscoResult> = repository.serverDiscoResult
    val isDiscoServerLoading: StateFlow<Boolean> = repository.isDiscoServerLoading

    // Local Service Discovery
    private val _discoveredServices = MutableStateFlow<List<com.ndregil.inchat.data.model.DiscoveredItem>>(emptyList())
    val discoveredServices: StateFlow<List<com.ndregil.inchat.data.model.DiscoveredItem>> = _discoveredServices.asStateFlow()

    private val _isServicesLoading = MutableStateFlow(false)
    val isServicesLoading: StateFlow<Boolean> = _isServicesLoading.asStateFlow()

    fun discoverLocalServices() {
        viewModelScope.launch {
            _isServicesLoading.value = true
            try {
                val domain = currentAccount.value.domain.ifBlank { "trashserver.net" }
                val result = repository.smackDiscoManager.discoverItems(domain)
                val items = result.getOrElse { emptyList() }
                if (items.isNotEmpty()) {
                    _discoveredServices.value = items
                } else {
                    _discoveredServices.value = listOf(
                        com.ndregil.inchat.data.model.DiscoveredItem(
                            jid = "conference.$domain",
                            name = "Multi-User Chat (MUC)",
                            node = "rooms"
                        ),
                        com.ndregil.inchat.data.model.DiscoveredItem(
                            jid = "upload.$domain",
                            name = "HTTP File Upload (XEP-0363)",
                            node = "upload"
                        ),
                        com.ndregil.inchat.data.model.DiscoveredItem(
                            jid = "proxy.$domain",
                            name = "SOCKS5 Bytestreams Proxy",
                            node = "bytestreams"
                        ),
                        com.ndregil.inchat.data.model.DiscoveredItem(
                            jid = "pubsub.$domain",
                            name = "Publish-Subscribe (PubSub / PEP)",
                            node = "pubsub"
                        ),
                        com.ndregil.inchat.data.model.DiscoveredItem(
                            jid = "vjud.$domain",
                            name = "User Directory (JUD / XEP-0055)",
                            node = "directory"
                        )
                    )
                }
            } catch (e: Exception) {
                val domain = currentAccount.value.domain.ifBlank { "trashserver.net" }
                _discoveredServices.value = listOf(
                    com.ndregil.inchat.data.model.DiscoveredItem(
                        jid = "conference.$domain",
                        name = "Multi-User Chat (MUC)",
                        node = "rooms"
                    ),
                    com.ndregil.inchat.data.model.DiscoveredItem(
                        jid = "upload.$domain",
                        name = "HTTP File Upload (XEP-0363)",
                        node = "upload"
                    ),
                    com.ndregil.inchat.data.model.DiscoveredItem(
                        jid = "proxy.$domain",
                        name = "SOCKS5 Bytestreams Proxy",
                        node = "bytestreams"
                    ),
                    com.ndregil.inchat.data.model.DiscoveredItem(
                        jid = "pubsub.$domain",
                        name = "Publish-Subscribe (PubSub / PEP)",
                        node = "pubsub"
                    ),
                    com.ndregil.inchat.data.model.DiscoveredItem(
                        jid = "vjud.$domain",
                        name = "User Directory (JUD / XEP-0055)",
                        node = "directory"
                    )
                )
            } finally {
                _isServicesLoading.value = false
            }
        }
    }

    // UI state flags
    val isRosterSheetOpen = MutableStateFlow(false)
    val isTopicModalOpen = MutableStateFlow(false)
    val isJoinCreateDialogOpen = MutableStateFlow(false)
    val isStanzaConsoleOpen = MutableStateFlow(false)
    val isThemeWallpaperDialogOpen = MutableStateFlow(false)
    val isRoomConfigOpen = MutableStateFlow(false)
    val isAdminPanelOpen = MutableStateFlow(false)
    val roomAdminInfo = MutableStateFlow<org.jivesoftware.smackx.muc.RoomInfo?>(null)
    val whisperTargetOccupant = MutableStateFlow<RoomOccupant?>(null)
    val replyingToMessage = MutableStateFlow<XmppMessage?>(null)

    // Theme state - observe persisted themePreferences and update DataStore
    val isDarkMode: StateFlow<Boolean?> = themePreferences.map { it.isDarkModeOverride }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun toggleDarkMode(currentSystemDark: Boolean) {
        val currentEffectiveDark = isDarkMode.value ?: currentSystemDark
        val newDarkOverride = !currentEffectiveDark
        viewModelScope.launch {
            val currentPrefs = themePreferences.value
            repository.updateThemePreferences(currentPrefs.copy(isDarkModeOverride = newDarkOverride))
        }
    }

    // ...

    val roomConfigStatus = MutableStateFlow<String?>(null)
    val isRoomConfigLoading = MutableStateFlow(false)

    val joinStatus = MutableStateFlow<String?>(null)
    val isJoinLoading = MutableStateFlow(false)

    fun clearJoinStatus() {
        joinStatus.value = null
    }

    fun clearRoomConfigStatus() {
        roomConfigStatus.value = null
    }
    fun configureRoom(
        roomJid: String,
        roomName: String?,
        description: String?,
        subject: String?,
        isPersistent: Boolean,
        isPublic: Boolean,
        isMembersOnly: Boolean,
        isModerated: Boolean = false,
        allowSubjectChange: Boolean = true,
        password: String? = null,
        notificationMode: Int = 0,
        maxUsers: Int? = null,
        enableLogging: Boolean = true,
        allowInvites: Boolean = true,
        whois: String? = null
    ) {
        viewModelScope.launch {
            isRoomConfigLoading.value = true
            roomConfigStatus.value = "Menyimpan konfigurasi ruangan MUC..."
            
            // Persist notification preference locally
            val config = repository.getRoomConfigSync(roomJid)
            if (config != null) {
                repository.saveRoomConfig(config.copy(notificationMode = notificationMode))
            }

            val result = repository.configureRoom(
                roomJid = roomJid,
                roomName = roomName,
                description = description,
                subject = subject,
                isPersistent = isPersistent,
                isPublic = isPublic,
                isMembersOnly = isMembersOnly,
                isModerated = isModerated,
                allowSubjectChange = allowSubjectChange,
                password = password,
                maxUsers = maxUsers,
                enableLogging = enableLogging,
                allowInvites = allowInvites,
                whois = whois
            )
            result.fold(
                onSuccess = {
                    roomConfigStatus.value = "Konfigurasi ruangan berhasil diperbarui!"
                    isRoomConfigOpen.value = false
                    isAdminPanelOpen.value = false
                },
                onFailure = { err ->
                    roomConfigStatus.value = "Gagal memperbarui ruangan: ${err.localizedMessage}"
                }
            )
            isRoomConfigLoading.value = false
        }
    }

    fun openAdminPanel(roomJid: String) {
        viewModelScope.launch {
            val info = repository.getRoomInfo(roomJid)
            roomAdminInfo.value = info.getOrNull()
            isAdminPanelOpen.value = true
        }
    }

    fun loadRoomInfo(roomJid: String) {
        viewModelScope.launch {
            val info = repository.getRoomInfo(roomJid)
            roomAdminInfo.value = info.getOrNull()
        }
    }

    // Active input text
    val currentInputText = MutableStateFlow("")

    init {
        // App will wait for user to configure account in AccountConnectionScreen
        loadDirectory()
        discoverLocalServices()
    }

    fun setReplyMessage(msg: XmppMessage) {
        replyingToMessage.value = msg
        val cleanNick = msg.senderNickname.replace("@System", "").trim()
        val mention = if (cleanNick.isBlank()) "@user " else "@$cleanNick "
        val current = currentInputText.value.trimStart()
        val cleanCurrent = current.replace(Regex("^@[^ ]+\\s*"), "").trimStart()
        currentInputText.value = if (cleanCurrent.isBlank()) {
            mention
        } else {
            "$mention$cleanCurrent"
        }
    }

    fun clearReplyMessage() {
        replyingToMessage.value = null
        currentInputText.value = ""
    }

    fun quoteMessage(msg: XmppMessage) {
        replyingToMessage.value = msg
        val fullBody = msg.body.trim()
        val cleanNick = msg.senderNickname.replace("@System", "").trim()
        val dateStr = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(msg.timestamp))
        val mention = if (cleanNick.isBlank()) "" else "@$cleanNick "
        val quoteText = "${mention}($dateStr) \"$fullBody\"\n\n"
        
        val current = currentInputText.value.trimStart()
        val cleanCurrent = current.replace(Regex("^@[^ ]+\\s*\\([^)]+\\)\\s*\"[^\"]*\"\\s*\\n*"), "").trimStart()
        currentInputText.value = if (cleanCurrent.isNotBlank()) {
            "$quoteText$cleanCurrent"
        } else {
            quoteText
        }
    }

    fun insertMention(nickname: String) {
        val mention = "@$nickname "
        val current = currentInputText.value
        if (current.isBlank()) {
            currentInputText.value = mention
        } else if (!current.contains("@$nickname")) {
            currentInputText.value = "$mention$current"
        }
    }

    fun selectRoom(jid: String) {
        _selectedRoomJid.value = jid
        if (!_roomMessageLimits.value.containsKey(jid)) {
            _roomMessageLimits.value = _roomMessageLimits.value + (jid to 30)
        }
        viewModelScope.launch {
            // Check if it's already in the room list and if it's NOT joined yet
            val room = allRooms.value.find { it.jid == jid }
            if (room != null && room.isGroupChat && !room.isJoined) {
                Log.i("XmppViewModel", "Selected room $jid is not joined. Triggering manual join...")
                // Trigger manual join
                joinOrCreateRoom(
                    roomJid = room.jid,
                    roomName = room.name,
                    nickname = room.myNickname,
                    topic = room.topic,
                    password = room.password ?: "",
                    saveToBookmarks = room.isBookmarked
                )
            }

            repository.markRoomAsRead(jid)
            try {
                repository.syncMamHistory(jid)
            } catch (e: Exception) {
                Log.w("XmppViewModel", "MAM sync failed on selectRoom for $jid: ${e.message}")
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        val room = activeRoom.value ?: return
        if (trimmed.isBlank()) return

        val myNick = room.myNickname.ifBlank { currentAccount.value.nickname }
        val replyTarget = replyingToMessage.value

        viewModelScope.launch {
            val whisperTarget = whisperTargetOccupant.value
            if (whisperTarget != null) {
                repository.sendWhisper(room.jid, whisperTarget.nickname, trimmed, myNick)
                // Retain whisper target so the private whisper conversation continues smoothly!
            } else {
                repository.sendMessage(
                    roomJid = room.jid,
                    text = trimmed,
                    myNickname = myNick,
                    replyToMessageId = replyTarget?.id,
                    replyToSender = replyTarget?.senderNickname,
                    replyToText = replyTarget?.body
                )
            }
            replyingToMessage.value = null
            currentInputText.value = ""
        }
    }

    fun sendFile(uri: android.net.Uri, fileName: String, fileSize: Long, mimeType: String) {
        val room = activeRoom.value ?: return
        val myNick = room.myNickname.ifBlank { currentAccount.value.nickname }
        viewModelScope.launch {
            repository.sendFile(room.jid, uri, fileName, fileSize, mimeType, myNick)
        }
    }

    fun sendVoiceMessage(audioFile: java.io.File, durationSeconds: Int) {
        val room = activeRoom.value ?: return
        val myNick = room.myNickname.ifBlank { currentAccount.value.nickname }
        viewModelScope.launch {
            repository.sendVoiceMessage(room.jid, audioFile, durationSeconds, myNick)
        }
    }

    fun shareLocation(latitude: Double, longitude: Double, label: String = "Lokasi Terkini") {
        val room = activeRoom.value ?: return
        val myNick = room.myNickname.ifBlank { currentAccount.value.nickname }
        val locationText = "📍 $label\nhttps://maps.google.com/?q=$latitude,$longitude\ngeo:$latitude,$longitude?z=16"
        viewModelScope.launch {
            repository.sendMessage(
                roomJid = room.jid,
                text = locationText,
                myNickname = myNick
            )
        }
    }

    fun initiateCall(callType: String) {
        val room = activeRoom.value ?: return
        val myNick = room.myNickname.ifBlank { currentAccount.value.nickname }
        val targetNick = whisperTargetOccupant.value?.nickname
        
        viewModelScope.launch {
            repository.initiateCall(room.jid, callType, myNick, targetNick)
        }
    }

    fun initiate1on1Call(targetJid: String, nickname: String, callType: String) {
        viewModelScope.launch {
            repository.initiate1on1Call(targetJid, nickname, callType)
        }
    }

    fun sendFileToContact(targetJid: String, uri: android.net.Uri) {
        viewModelScope.launch {
            repository.sendFileToContact(targetJid, uri)
        }
    }

    fun querySoftwareVersion(targetJid: String, onResult: (Result<com.ndregil.inchat.data.model.ClientCaps>) -> Unit) {
        viewModelScope.launch {
            val result = repository.querySoftwareVersion(targetJid)
            onResult(result)
        }
    }

    fun acceptCall() {
        repository.smackCallManager.acceptCall()
    }

    fun rejectCall() {
        repository.smackCallManager.rejectCall()
    }

    fun endCall() {
        repository.smackCallManager.endCall()
    }

    fun startDirectChat(targetJid: String, displayName: String = "") {
        viewModelScope.launch {
            val cleanJid = targetJid.trim()
            if (cleanJid.isBlank()) return@launch
            val name = displayName.trim().ifBlank { cleanJid.substringBefore("@") }
            val myNick = currentAccount.value.nickname.ifBlank { currentAccount.value.username.ifBlank { "User" } }
            repository.saveRoom(
                roomJid = cleanJid,
                roomName = name,
                nickname = myNick,
                topic = "Obrolan Langsung (1-on-1)",
                password = null
            )
            _selectedRoomJid.value = cleanJid
        }
    }

    fun toggleMute() {
        repository.smackCallManager.toggleMute()
    }

    fun toggleVideo() {
        repository.smackCallManager.toggleVideo()
    }

    fun joinOrCreateRoom(
        roomJid: String,
        roomName: String,
        nickname: String,
        topic: String = "",
        password: String = "",
        saveToBookmarks: Boolean = true
    ) {
        viewModelScope.launch {
            val trimmedJid = roomJid.trim()
            if (trimmedJid.isBlank()) return@launch

            isJoinLoading.value = true
            joinStatus.value = "Menghubungkan ke $trimmedJid..."

            val validJid = when {
                trimmedJid.contains("@") -> {
                    val parts = trimmedJid.split("@")
                    val domain = parts.getOrNull(1) ?: ""
                    // Typical MUC services have 'conference', 'muc', 'chat', 'room' in subdomain
                    val looksLikeMuc = domain.contains("conference", true) || 
                                      domain.contains("muc.", true) || 
                                      domain.contains("chat.", true) ||
                                      domain.contains("rooms.", true)
                    
                    if (!looksLikeMuc && !trimmedJid.contains("conference.xmpp.today")) {
                        Log.d("XmppViewModel", "JID $trimmedJid might not be a MUC service. Warning user.")
                        // We could show a specific warning UI here, but for now we'll just proceed and let the error handling catch it.
                    }
                    trimmedJid
                }
                else -> "$trimmedJid@conference.xmpp.today"
            }

            val validNick = if (nickname.isNotBlank()) nickname.trim() else currentAccount.value.nickname
            val result = repository.joinRoom(validJid, roomName.trim(), validNick, topic.trim(), password)
            
            result.fold(
                onSuccess = {
                    if (saveToBookmarks) {
                        repository.saveRoom(validJid, roomName.trim(), validNick, topic.trim(), password)
                    }
                    _selectedRoomJid.value = validJid
                    isJoinCreateDialogOpen.value = false
                    joinStatus.value = null
                },
                onFailure = { err ->
                    var msg = err.message ?: "Terjadi kesalahan tidak diketahui"
                    if (msg.contains("tidak menyediakan layanan MUC")) {
                        msg += "\n\nTip: Jika ini adalah JID teman Anda, gunakan fitur 'Mulai Obrolan Langsung' dari tab Kontak."
                    }
                    joinStatus.value = "Gagal bergabung: $msg"
                    Log.e("XmppViewModel", "Join failed for $validJid", err)
                    
                    // If dialog is not open (e.g. joined from Directory), show a Toast
                    if (!isJoinCreateDialogOpen.value) {
                        android.widget.Toast.makeText(getApplication(), "Gagal bergabung: $msg", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            )
            isJoinLoading.value = false
        }
    }

    fun joinRoom(roomJid: String, nickname: String, password: String? = null) {
        val room = allRooms.value.find { it.jid == roomJid }
        val roomName = room?.name ?: roomJid.substringBefore("@")
        val save = room?.isBookmarked ?: true
        joinOrCreateRoom(
            roomJid = roomJid,
            roomName = roomName,
            nickname = nickname,
            password = password ?: (room?.password ?: ""),
            saveToBookmarks = save
        )
    }

    fun toggleSaveRoom(roomJid: String) {
        viewModelScope.launch {
            repository.toggleSaveRoom(roomJid)
        }
    }

    fun saveRoom(
        roomJid: String,
        roomName: String,
        nickname: String,
        topic: String = "",
        password: String? = null
    ) {
        viewModelScope.launch {
            val validJid = if (roomJid.contains("@")) roomJid else "$roomJid@conference.xmpp.today"
            val validNick = if (nickname.isNotBlank()) nickname else currentAccount.value.nickname
            repository.saveRoom(validJid, roomName, validNick, topic, password)
        }
    }

    fun removeSavedRoom(roomJid: String) {
        viewModelScope.launch {
            repository.removeSavedRoom(roomJid)
        }
    }

    fun leaveRoom(roomJid: String) {
        val currentRooms = allRooms.value
        // Find another room to switch to (ONLY joined rooms)
        val nextRoomJid = currentRooms.firstOrNull { it.jid != roomJid && it.isJoined }?.jid
            
        // Move to the next room window or back to list immediately
        if (_selectedRoomJid.value == roomJid) {
            _selectedRoomJid.value = nextRoomJid
        }
        
        viewModelScope.launch {
            val room = currentRooms.find { it.jid == roomJid }
            val nick = room?.myNickname ?: currentAccount.value.nickname
            repository.leaveRoom(roomJid, nick)
        }
    }

    fun deleteRoom(roomJid: String) {
        viewModelScope.launch {
            repository.deleteRoom(roomJid)
            if (_selectedRoomJid.value == roomJid) {
                _selectedRoomJid.value = allRooms.value.firstOrNull { it.jid != roomJid && it.isJoined }?.jid
                    ?: allRooms.value.firstOrNull { it.jid != roomJid }?.jid
            }
        }
    }

    fun updateTopic(newTopic: String) {
        val room = activeRoom.value ?: return
        viewModelScope.launch {
            val myNick = room.myNickname.ifBlank { currentAccount.value.nickname }
            repository.updateRoomTopic(room.jid, newTopic, myNick)
            isTopicModalOpen.value = false
        }
    }

    fun loadDirectory(query: String = "", serviceJid: String? = null) {
        viewModelScope.launch {
            _isDirectoryLoading.value = true
            try {
                // If query is provided, we use the global search service
                val rooms = repository.discoverRooms(query = query, serviceJid = serviceJid)
                _directoryRooms.value = rooms.map { it.toPublicDirectoryRoom() }
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isDirectoryLoading.value = false
            }
        }
    }

    /**
     * Triggers XEP-0030 Service Discovery on the server or specific MUC service.
     */
    fun discoverRooms(serviceJid: String? = null) {
        loadDirectory(serviceJid = serviceJid)
    }

    /**
     * Directly browses MUC rooms from the server via XEP-0030 disco#items & disco#info.
     */
    fun discoverServerRooms(serviceJid: String? = null) {
        viewModelScope.launch {
            try {
                repository.discoverServerRooms(serviceJid)
            } catch (_: Exception) {}
        }
    }

    /**
     * Performs manual XMPP Service Discovery (disco#info + disco#items) on an arbitrary domain or service JID
     * (e.g. jabber.ru, conference.jabber.ru).
     */
    fun discoServer(targetJid: String, node: String? = null) {
        viewModelScope.launch {
            try {
                repository.discoServer(targetJid, node)
            } catch (_: Exception) {}
        }
    }

    // Backup & Restore state
    val backupStatus = MutableStateFlow<String?>(null)
    val isBackupLoading = MutableStateFlow(false)

    fun exportBackup(targetUri: android.net.Uri? = null, passphrase: String = "") {
        viewModelScope.launch {
            isBackupLoading.value = true
            backupStatus.value = null
            val result = repository.exportBackup(targetUri, passphrase)
            result.fold(
                onSuccess = { msg -> backupStatus.value = msg },
                onFailure = { err -> backupStatus.value = "Gagal ekspor: ${err.localizedMessage}" }
            )
            isBackupLoading.value = false
        }
    }

    fun importBackup(sourceUri: android.net.Uri, passphrase: String = "") {
        viewModelScope.launch {
            isBackupLoading.value = true
            backupStatus.value = null
            val result = repository.importBackup(sourceUri, passphrase)
            result.fold(
                onSuccess = { msg -> backupStatus.value = msg },
                onFailure = { err -> backupStatus.value = err.localizedMessage ?: "Gagal memulihkan cadangan" }
            )
            isBackupLoading.value = false
        }
    }

    fun clearBackupStatus() {
        backupStatus.value = null
    }


    // Registration state
    private val _registrationState = MutableStateFlow<RegistrationState>(RegistrationState.Idle)
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    fun resetRegistrationState() {
        _registrationState.value = RegistrationState.Idle
    }

    fun registerAccount(
        username: String,
        password: String,
        domain: String,
        host: String = "",
        port: Int = 5222,
        nickname: String = "",
        email: String = "",
        useTls: Boolean = true,
        autoLogin: Boolean = true
    ) {
        viewModelScope.launch {
            _registrationState.value = RegistrationState.Registering
            val result = repository.registerAccount(
                username = username,
                password = password,
                domain = domain,
                host = host,
                port = port,
                nickname = nickname,
                email = email,
                useTls = useTls
            )

            result.fold(
                onSuccess = { registeredJid ->
                    _registrationState.value = RegistrationState.Success(registeredJid, autoLogin)
                    if (autoLogin) {
                        val newAccount = XmppAccount(
                            jid = registeredJid,
                            username = username.trim(),
                            domain = domain.trim(),
                            nickname = nickname.trim().ifBlank { username.trim() },
                            host = host.trim().ifBlank { domain.trim() },
                            port = port,
                            useTls = useTls,
                            isDemoMode = false,
                            statusMessage = "Aktif di InChat"
                        )
                        connectAccount(newAccount, password)
                    }
                },
                onFailure = { error ->
                    when (error) {
                        is com.ndregil.inchat.data.network.CaptchaRequiredException -> {
                            _registrationState.value = RegistrationState.CaptchaChallengeRequired(error.challenge)
                        }
                        is com.ndregil.inchat.data.network.RegistrationNotAllowedException -> {
                            _registrationState.value = RegistrationState.Error(
                                error.message.ifBlank { "Pendaftaran langsung (In-Band) ditolak server. Silakan coba server lain seperti xmpp.jp." }
                            )
                        }
                        else -> {
                            _registrationState.value = RegistrationState.Error(
                                error.localizedMessage ?: "Gagal mendaftar akun JID baru di server"
                            )
                        }
                    }
                }
            )
        }
    }

    fun submitCaptchaAnswer(
        challenge: com.ndregil.inchat.data.network.CaptchaChallengeData,
        captchaAnswer: String,
        autoLogin: Boolean = true
    ) {
        viewModelScope.launch {
            _registrationState.value = RegistrationState.Registering
            val result = repository.submitCaptchaAnswer(challenge, captchaAnswer)
            result.fold(
                onSuccess = { registeredJid ->
                    _registrationState.value = RegistrationState.Success(registeredJid, autoLogin)
                    if (autoLogin) {
                        val newAccount = XmppAccount(
                            jid = registeredJid,
                            username = challenge.username,
                            domain = challenge.domain,
                            nickname = challenge.nickname.ifBlank { challenge.username },
                            host = challenge.host.ifBlank { challenge.domain },
                            port = challenge.port,
                            useTls = challenge.useTls,
                            isDemoMode = false,
                            statusMessage = "Aktif di InChat"
                        )
                        connectAccount(newAccount, challenge.password)
                    }
                },
                onFailure = { error ->
                    _registrationState.value = RegistrationState.Error(
                        error.localizedMessage ?: "Jawaban CAPTCHA salah atau ditolak server"
                    )
                }
            )
        }
    }

    fun createInstantAccount(
        username: String,
        domain: String = "trashserver.net",
        nickname: String = ""
    ) {
        viewModelScope.launch {
            val cleanUser = username.trim().ifBlank { "guest_" + (1000..9999).random() }
            val cleanDom = domain.trim().ifBlank { "trashserver.net" }
            val jid = "$cleanUser@$cleanDom"
            val nick = nickname.trim().ifBlank { cleanUser }

            val guestAccount = XmppAccount(
                jid = jid,
                username = cleanUser,
                domain = cleanDom,
                nickname = nick,
                host = cleanDom,
                port = 5222,
                useTls = true,
                isDemoMode = true,
                statusMessage = "Mode Tamu / Eksplorasi InChat"
            )

            connectAccount(guestAccount, "")
        }
    }

    val isAvatarUpdating = MutableStateFlow(false)
    val avatarUpdateStatus = MutableStateFlow<String?>(null)

    fun clearAvatarUpdateStatus() {
        avatarUpdateStatus.value = null
    }

    fun updateAvatar(uri: android.net.Uri) {
        viewModelScope.launch {
            isAvatarUpdating.value = true
            avatarUpdateStatus.value = "Memproses foto profil..."
            val statusMsg = withContext(Dispatchers.IO) {
                try {
                    val app = getApplication<Application>()
                    val inputStream = app.contentResolver.openInputStream(uri)
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (originalBitmap == null) {
                        return@withContext "Gagal membaca gambar dari galeri."
                    }

                    // Scaled dimensions (max 256x256 px to comply with XMPP PubSub stanza limits)
                    val maxDim = 256
                    val width = originalBitmap.width
                    val height = originalBitmap.height
                    val (newWidth, newHeight) = if (width > height) {
                        val ratio = height.toFloat() / width.toFloat()
                        maxDim to (maxDim * ratio).toInt()
                    } else {
                        val ratio = width.toFloat() / height.toFloat()
                        (maxDim * ratio).toInt() to maxDim
                    }

                    val scaledBitmap = Bitmap.createScaledBitmap(
                        originalBitmap,
                        newWidth.coerceAtLeast(1),
                        newHeight.coerceAtLeast(1),
                        true
                    )

                    val byteArrayOutputStream = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.PNG, 90, byteArrayOutputStream)
                    val bytes = byteArrayOutputStream.toByteArray()

                    if (scaledBitmap != originalBitmap) {
                        scaledBitmap.recycle()
                    }
                    originalBitmap.recycle()

                    Log.i("XmppViewModel", "Compressed avatar size: ${bytes.size} bytes for PEP/vCard")

                    val result = repository.publishProfileAvatar(bytes)
                    if (result.isSuccess) {
                        "Foto profil berhasil diperbarui!"
                    } else {
                        "Gagal memperbarui foto profil: ${result.exceptionOrNull()?.localizedMessage}"
                    }
                } catch (e: Exception) {
                    Log.e("XmppViewModel", "Gagal mengunggah foto profil", e)
                    "Error memperbarui foto profil: ${e.localizedMessage}"
                }
            }
            avatarUpdateStatus.value = statusMsg
            isAvatarUpdating.value = false
        }
    }

    fun updateRoomAvatar(roomJid: String, roomName: String, uri: android.net.Uri) {
        viewModelScope.launch {
            isAvatarUpdating.value = true
            avatarUpdateStatus.value = "Memproses foto avatar room $roomName..."
            val statusMsg = withContext(Dispatchers.IO) {
                try {
                    val app = getApplication<Application>()
                    val inputStream = app.contentResolver.openInputStream(uri)
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (originalBitmap == null) {
                        return@withContext "Gagal membaca gambar dari galeri."
                    }

                    val maxDim = 256
                    val width = originalBitmap.width
                    val height = originalBitmap.height
                    val (newWidth, newHeight) = if (width > height) {
                        val ratio = height.toFloat() / width.toFloat()
                        maxDim to (maxDim * ratio).toInt()
                    } else {
                        val ratio = width.toFloat() / height.toFloat()
                        (maxDim * ratio).toInt() to maxDim
                    }

                    val scaledBitmap = Bitmap.createScaledBitmap(
                        originalBitmap,
                        newWidth.coerceAtLeast(1),
                        newHeight.coerceAtLeast(1),
                        true
                    )

                    val byteArrayOutputStream = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.PNG, 90, byteArrayOutputStream)
                    val bytes = byteArrayOutputStream.toByteArray()

                    if (scaledBitmap != originalBitmap) {
                        scaledBitmap.recycle()
                    }
                    originalBitmap.recycle()

                    val result = repository.updateRoomAvatar(roomJid, bytes)
                    if (result.isSuccess) {
                        "Foto avatar room $roomName berhasil diperbarui!"
                    } else {
                        "Gagal memperbarui avatar room: ${result.exceptionOrNull()?.localizedMessage}"
                    }
                } catch (e: Exception) {
                    Log.e("XmppViewModel", "Gagal memperbarui avatar room", e)
                    "Error memperbarui avatar room: ${e.localizedMessage}"
                }
            }
            avatarUpdateStatus.value = statusMsg
            isAvatarUpdating.value = false
        }
    }

    fun connectAccount(account: XmppAccount, password: String = "") {
        viewModelScope.launch {
            repository.connect(account, password)
        }
    }

    fun reconnect() {
        viewModelScope.launch {
            repository.checkAndReconnectIfNeeded()
        }
    }

    fun checkAndReconnectIfNeeded() {
        reconnect()
    }

    fun disconnect() {
        viewModelScope.launch {
            repository.disconnect()
        }
    }

    fun switchAccount(onReadyForNewAccount: () -> Unit = {}) {
        viewModelScope.launch {
            onReadyForNewAccount()
        }
    }

    fun signOut(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                com.ndregil.inchat.data.network.fcm.XmppForegroundService.stopService(getApplication())
            } catch (e: Exception) {
                Log.e("XmppViewModel", "Error stopping service on sign out: ${e.message}")
            }
            repository.signOut()
            onComplete()
        }
    }

    fun exitApp(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                com.ndregil.inchat.data.network.fcm.XmppForegroundService.stopService(getApplication())
            } catch (e: Exception) {
                Log.e("XmppViewModel", "Error stopping service on exit: ${e.message}")
            }
            repository.exitApp()
            onComplete()
        }
    }

    fun sendRawXmlStanza(xml: String) {
        repository.sendRawStanza(xml)
    }

    fun setWhisperTarget(occupant: RoomOccupant?) {
        whisperTargetOccupant.value = occupant
        isRosterSheetOpen.value = false
    }

    /**
     * Hooks a custom MessageListener callback to receive incoming MUC messages directly in UI.
     */
    fun hookMucMessageListener(
        roomJid: String,
        onMessage: (XmppMessage) -> Unit
    ): com.ndregil.inchat.data.network.MucMessageListener? {
        return repository.smackMucManager.hookMessageListener(roomJid, onMessage)
    }

    /**
     * Roster & Presence methods (Smack Roster API)
     */
    fun refreshRoster() {
        viewModelScope.launch {
            repository.loadRosterContacts()
        }
    }

    fun addRosterContact(jid: String, name: String, group: String? = null) {
        viewModelScope.launch {
            repository.addRosterContact(jid, name, group)
        }
    }

    fun removeRosterContact(jid: String) {
        viewModelScope.launch {
            repository.removeRosterContact(jid)
        }
    }

    fun renameRosterContact(jid: String, newName: String) {
        viewModelScope.launch {
            repository.renameRosterContact(jid, newName)
        }
    }

    fun addRosterContactToGroup(jid: String, group: String) {
        viewModelScope.launch {
            repository.addRosterContactToGroup(jid, group)
        }
    }

    fun acceptSubscriptionRequest(jid: String) {
        viewModelScope.launch {
            repository.acceptSubscriptionRequest(jid)
        }
    }

    fun rejectSubscriptionRequest(jid: String) {
        viewModelScope.launch {
            repository.rejectSubscriptionRequest(jid)
        }
    }

    fun getContactPresence(jid: String): UserPresence = repository.getContactPresence(jid)

    fun refreshRoomOccupants() {
        activeRoom.value?.let { room ->
            repository.refreshRoomOccupants(room.jid)
        }
    }

    fun updateMyPresence(presence: UserPresence, statusMessage: String? = null) {
        viewModelScope.launch {
            val msg = statusMessage ?: myStatusMessage.value
            repository.updateUserPresence(presence, msg)
        }
    }

    /**
     * Room Configuration & Chat History Persistence operations
     */
    fun updateRoomAutoJoin(roomJid: String, isAutoJoin: Boolean) {
        viewModelScope.launch {
            repository.updateRoomAutoJoin(roomJid, isAutoJoin)
        }
    }

    fun updateRoomPassword(roomJid: String, password: String?) {
        viewModelScope.launch {
            repository.updateRoomPassword(roomJid, password)
        }
    }

    fun updateRoomNickname(roomJid: String, nickname: String) {
        viewModelScope.launch {
            val result = repository.updateRoomNickname(roomJid, nickname)
            if (result.isFailure) {
                Log.e("XmppViewModel", "Gagal mengganti nickname di room $roomJid: ${result.exceptionOrNull()?.message}")
                // Optionally show a toast or update a status flow
            }
        }
    }

    fun updateRoomThemeAndWallpaper(roomJid: String, bubbleTheme: String, wallpaperStyle: String) {
        viewModelScope.launch {
            repository.updateRoomThemeAndWallpaper(roomJid, bubbleTheme, wallpaperStyle)
            isThemeWallpaperDialogOpen.value = false
        }
    }

    fun updateRoomColor(roomJid: String, colorHex: String) {
        viewModelScope.launch {
            repository.updateRoomColor(roomJid, colorHex)
        }
    }

    fun saveRoomConfig(config: RoomConfigEntity) {
        viewModelScope.launch {
            repository.saveRoomConfig(config)
        }
    }

    fun clearRoomChatHistory(roomJid: String) {
        viewModelScope.launch {
            repository.clearRoomChatHistory(roomJid)
            if (_selectedRoomJid.value == roomJid) {
                _selectedRoomJid.value = null
            }
        }
    }

    fun closeConversation(roomJid: String) {
        viewModelScope.launch {
            repository.closeConversation(roomJid)
            if (_selectedRoomJid.value == roomJid) {
                _selectedRoomJid.value = null
            }
        }
    }

    fun autoJoinConfiguredRooms() {
        viewModelScope.launch {
            repository.autoJoinConfiguredRooms()
        }
    }

    fun loadRoomAffiliations(roomJid: String) {
        viewModelScope.launch {
            isLoadingAffiliations.value = true
            val owners = repository.getOwners(roomJid).getOrElse { emptyList() }
            val admins = repository.getAdmins(roomJid).getOrElse { emptyList() }
            val members = repository.getMembers(roomJid).getOrElse { emptyList() }
            val outcasts = repository.getOutcasts(roomJid).getOrElse { emptyList() }
            
            ownersList.value = owners
            adminsList.value = admins
            membersList.value = members
            outcastsList.value = outcasts
            isLoadingAffiliations.value = false
        }
    }

    fun grantOwnership(roomJid: String, jid: String) = viewModelScope.launch {
        repository.grantOwnership(roomJid, jid)
        loadRoomAffiliations(roomJid)
    }

    fun revokeOwnership(roomJid: String, jid: String) = viewModelScope.launch {
        repository.revokeOwnership(roomJid, jid)
        loadRoomAffiliations(roomJid)
    }

    fun grantVoice(roomJid: String, nickname: String) = viewModelScope.launch {
        repository.grantVoice(roomJid, nickname)
    }

    fun revokeVoice(roomJid: String, nickname: String) = viewModelScope.launch {
        repository.revokeVoice(roomJid, nickname)
    }

    fun grantNone(roomJid: String, jid: String) = viewModelScope.launch {
        repository.grantNone(roomJid, jid)
        loadRoomAffiliations(roomJid)
    }

    fun unbanUser(roomJid: String, jid: String) = viewModelScope.launch {
        repository.unbanUser(roomJid, jid)
        loadRoomAffiliations(roomJid)
    }

    fun grantModerator(roomJid: String, nickname: String) = viewModelScope.launch { repository.grantModerator(roomJid, nickname) }
    fun revokeModerator(roomJid: String, nickname: String) = viewModelScope.launch { repository.revokeModerator(roomJid, nickname) }
    fun grantMembership(roomJid: String, jid: String) = viewModelScope.launch {
        repository.grantMembership(roomJid, jid)
        loadRoomAffiliations(roomJid)
    }
    fun revokeMembership(roomJid: String, jid: String) = viewModelScope.launch {
        repository.revokeMembership(roomJid, jid)
        loadRoomAffiliations(roomJid)
    }
    fun grantAdmin(roomJid: String, jid: String) = viewModelScope.launch {
        repository.grantAdmin(roomJid, jid)
        loadRoomAffiliations(roomJid)
    }
    fun revokeAdmin(roomJid: String, jid: String) = viewModelScope.launch {
        repository.revokeAdmin(roomJid, jid)
        loadRoomAffiliations(roomJid)
    }
    fun banUser(roomJid: String, jid: String, reason: String?) = viewModelScope.launch {
        repository.banUser(roomJid, jid, reason)
        loadRoomAffiliations(roomJid)
    }
    fun kickParticipant(roomJid: String, nickname: String, reason: String?) = viewModelScope.launch { repository.kickParticipant(roomJid, nickname, reason) }

    // --- Stories / Status (WhatsApp style) ---
    val stories: StateFlow<List<com.ndregil.inchat.data.model.UserStory>> = repository.stories.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val isPublishingStory = MutableStateFlow(false)
    val storyPublishError = MutableStateFlow<String?>(null)

    fun publishStory(text: String, mediaUri: android.net.Uri? = null, backgroundColorHex: String = "#0284C7") {
        viewModelScope.launch {
            isPublishingStory.value = true
            storyPublishError.value = null
            val result = repository.publishStory(text, mediaUri, backgroundColorHex)
            if (result.isFailure) {
                storyPublishError.value = result.exceptionOrNull()?.message ?: "Gagal membagikan story"
            }
            isPublishingStory.value = false
        }
    }

    fun markStoryViewed(storyId: String) {
        viewModelScope.launch {
            val story = stories.value.find { it.id == storyId }
            repository.markStoryViewed(storyId)
            if (story != null && !story.isMine) {
                repository.sendStoryViewedNotification(story)
            }
        }
    }

    fun deleteStory(storyId: String) {
        viewModelScope.launch {
            repository.deleteStory(storyId)
        }
    }
}

