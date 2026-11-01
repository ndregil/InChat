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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

sealed class RegistrationState {
    object Idle : RegistrationState()
    object Registering : RegistrationState()
    data class Success(val jid: String, val autoLoggingIn: Boolean) : RegistrationState()
    data class Error(val message: String) : RegistrationState()
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
    val myPresence: StateFlow<UserPresence> = repository.userPresence
    val myStatusMessage: StateFlow<String> = repository.userStatusMessage
    val currentCall: StateFlow<com.ndregil.inchat.data.model.CallInfo?> = repository.currentCall
    
    val myAvatar: StateFlow<ByteArray?> = combine(currentAccount, repository.smackAvatarManager.avatarUpdates) { acc, _ ->
        repository.smackAvatarManager.getAvatar(acc.jid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
                val domain = currentAccount.value.domain
                if (domain.isNotBlank()) {
                    val result = repository.smackDiscoManager.discoverItems(domain)
                    _discoveredServices.value = result.getOrElse { emptyList() }
                }
            } catch (e: Exception) {
                // Error handled
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
            val validJid = if (roomJid.contains("@")) roomJid else "$roomJid@conference.xmpp.today"
            val validNick = if (nickname.isNotBlank()) nickname else currentAccount.value.nickname
            repository.joinRoom(validJid, roomName, validNick, topic, password)
            if (saveToBookmarks) {
                repository.saveRoom(validJid, roomName, validNick, topic, password)
            }
            _selectedRoomJid.value = validJid
            isJoinCreateDialogOpen.value = false
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
                    _registrationState.value = RegistrationState.Error(
                        error.localizedMessage ?: "Gagal mendaftar akun JID baru di server"
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

    fun grantModerator(roomJid: String, nickname: String) = viewModelScope.launch { repository.grantModerator(roomJid, nickname) }
    fun revokeModerator(roomJid: String, nickname: String) = viewModelScope.launch { repository.revokeModerator(roomJid, nickname) }
    fun grantMembership(roomJid: String, jid: String) = viewModelScope.launch { repository.grantMembership(roomJid, jid) }
    fun revokeMembership(roomJid: String, jid: String) = viewModelScope.launch { repository.revokeMembership(roomJid, jid) }
    fun grantAdmin(roomJid: String, jid: String) = viewModelScope.launch { repository.grantAdmin(roomJid, jid) }
    fun revokeAdmin(roomJid: String, jid: String) = viewModelScope.launch { repository.revokeAdmin(roomJid, jid) }
    fun banUser(roomJid: String, jid: String, reason: String?) = viewModelScope.launch { repository.banUser(roomJid, jid, reason) }
    fun kickParticipant(roomJid: String, nickname: String, reason: String?) = viewModelScope.launch { repository.kickParticipant(roomJid, nickname, reason) }
}

