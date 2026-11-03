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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

sealed class RegistrationState {
    object Idle : RegistrationState()
    object Registering : RegistrationState()
    data class Success(val jid: String, val autoLoggingIn: Boolean) : RegistrationState()
    data class Error(val message: String) : RegistrationState()
    data class CaptchaChallengeRequired(val challenge: com.ndregil.inchat.data.network.CaptchaChallengeData) : RegistrationState()
    data class RedirectRequired(val url: String, val message: String) : RegistrationState()
}

@OptIn(ExperimentalCoroutinesApi::class)
class XmppViewModel(application: Application) : AndroidViewModel(application) {

    val repository = XmppRepository.getInstance(application)

    val connectionStatus: StateFlow<ConnectionStatus> = repository.connectionStatus
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

    fun getRoomAvatar(roomJid: String): ByteArray? {
        return repository.getRoomAvatar(roomJid)
    }

    val ownersList = MutableStateFlow<List<com.ndregil.inchat.data.model.MucAffiliate>>(emptyList())
    val adminsList = MutableStateFlow<List<com.ndregil.inchat.data.model.MucAffiliate>>(emptyList())
    val membersList = MutableStateFlow<List<com.ndregil.inchat.data.model.MucAffiliate>>(emptyList())
    val outcastsList = MutableStateFlow<List<com.ndregil.inchat.data.model.MucAffiliate>>(emptyList())
    val isLoadingAffiliations = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            repository.autoJoinEvents.collect { msg ->
                autoJoinToastStatus.value = msg
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

    // Room Configurations (persisted in Room DB)
    val roomConfigs: StateFlow<List<RoomConfigEntity>> = repository.roomConfigs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Filtered rooms
    val filteredRooms: StateFlow<List<XmppRoom>> = combine(allRooms, _searchQuery) { rooms, query ->
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

    // Saved / Bookmarked rooms
    val savedRooms: StateFlow<List<XmppRoom>> = combine(allRooms, roomConfigs) { rooms, configs ->
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

    // Active room object with graceful fallback
    val activeRoom: StateFlow<XmppRoom?> = combine(allRooms, _selectedRoomJid) { rooms, jid ->
        if (jid == null) rooms.firstOrNull() else (rooms.find { it.jid == jid } ?: rooms.firstOrNull())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // Messages for active room
    val activeRoomMessages: StateFlow<List<XmppMessage>> = activeRoom.flatMapLatest { room ->
        if (room != null) {
            repository.getMessagesForRoom(room.jid)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

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

    // Theme state
    private val _isDarkMode = MutableStateFlow<Boolean?>(null) // null means follow system
    val isDarkMode: StateFlow<Boolean?> = _isDarkMode.asStateFlow()

    fun toggleDarkMode(currentSystemDark: Boolean) {
        val current = _isDarkMode.value ?: currentSystemDark
        _isDarkMode.value = !current
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
        roomName: String? = null,
        description: String? = null,
        subject: String? = null,
        isPersistent: Boolean = true,
        isPublic: Boolean = true,
        isMembersOnly: Boolean = false,
        isModerated: Boolean = false,
        allowSubjectChange: Boolean = true,
        password: String? = null
    ) {
        viewModelScope.launch {
            isRoomConfigLoading.value = true
            roomConfigStatus.value = "Menyimpan konfigurasi ruangan MUC..."
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
                password = password
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

    // Active input text
    val currentInputText = MutableStateFlow("")

    init {
        // App will wait for user to configure account in AccountConnectionScreen
        loadDirectory()
        discoverLocalServices()
    }

    fun setReplyMessage(msg: XmppMessage) {
        replyingToMessage.value = msg
        val mention = "@${msg.senderNickname} "
        val current = currentInputText.value
        if (current.isBlank()) {
            currentInputText.value = mention
        } else if (!current.startsWith(mention.trim())) {
            currentInputText.value = "$mention$current"
        }
    }

    fun clearReplyMessage() {
        replyingToMessage.value = null
    }

    fun quoteMessage(msg: XmppMessage) {
        replyingToMessage.value = msg
        val fullBody = msg.body.trim()
        val quotePrefix = ">@${msg.senderNickname} $fullBody\n\n"
        val current = currentInputText.value.trimStart()
        currentInputText.value = if (current.isNotBlank()) "$quotePrefix$current" else quotePrefix
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
        viewModelScope.launch {
            repository.markRoomAsRead(jid)
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
                whisperTargetOccupant.value = null
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

    fun shareLocation(latitude: Double, longitude: Double) {
        val room = activeRoom.value ?: return
        val myNick = room.myNickname.ifBlank { currentAccount.value.nickname }
        val locationText = "📍 Berbagi Lokasi GPS\ngeo:$latitude,$longitude?z=16"
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
        viewModelScope.launch {
            val room = allRooms.value.find { it.jid == roomJid }
            val nick = room?.myNickname ?: currentAccount.value.nickname
            repository.leaveRoom(roomJid, nick)
            if (_selectedRoomJid.value == roomJid) {
                val other = allRooms.value.firstOrNull { it.jid != roomJid && it.isJoined }
                _selectedRoomJid.value = other?.jid
            }
        }
    }

    fun deleteRoom(roomJid: String) {
        viewModelScope.launch {
            repository.deleteRoom(roomJid)
            if (_selectedRoomJid.value == roomJid) {
                _selectedRoomJid.value = allRooms.value.firstOrNull { it.jid != roomJid }?.jid
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
                        is com.ndregil.inchat.data.network.RegistrationRedirectException -> {
                            _registrationState.value = RegistrationState.RedirectRequired(error.url, error.message)
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

    fun disconnect() {
        viewModelScope.launch {
            repository.disconnect()
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
            repository.updateRoomNickname(roomJid, nickname)
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
}

