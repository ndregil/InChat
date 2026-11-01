package com.ndregil.inchat.data.repository

import com.ndregil.inchat.data.local.AppDatabase
import com.ndregil.inchat.data.local.entity.AccountEntity
import com.ndregil.inchat.data.local.entity.MessageEntity
import com.ndregil.inchat.data.local.entity.RoomConfigEntity
import com.ndregil.inchat.data.local.entity.RoomEntity
import com.ndregil.inchat.data.model.CallInfo
import com.ndregil.inchat.data.model.ConnectionStatus
import com.ndregil.inchat.data.model.DeliveryStatus
import com.ndregil.inchat.data.model.DiscoveredRoom
import com.ndregil.inchat.data.model.MessageType
import com.ndregil.inchat.data.model.OccupantAffiliation
import com.ndregil.inchat.data.model.OccupantRole
import com.ndregil.inchat.data.model.PublicDirectoryRoom
import com.ndregil.inchat.data.model.RoomOccupant
import com.ndregil.inchat.data.model.StanzaLog
import com.ndregil.inchat.data.model.XmppAccount
import com.ndregil.inchat.data.model.XmppMessage
import com.ndregil.inchat.data.model.XmppRoom
import com.ndregil.inchat.data.model.RosterContact
import com.ndregil.inchat.data.model.UserPresence
import com.ndregil.inchat.data.network.XmppAvatarManager
import com.ndregil.inchat.data.network.HttpFileUploadManager
import com.ndregil.inchat.data.network.JingleCallManager
import com.ndregil.inchat.data.network.MultiUserChatManager
import com.ndregil.inchat.data.network.RosterManager
import com.ndregil.inchat.data.network.ServiceDiscoveryManager
import com.ndregil.inchat.data.network.XmppConnectionManager
import com.ndregil.inchat.data.network.XmppProtocolEngine
import org.jxmpp.jid.impl.JidCreate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class XmppRepository(
    private val context: android.content.Context,
    private val database: AppDatabase,
    private val scope: CoroutineScope
) {
    companion object {
        @Volatile
        private var INSTANCE: XmppRepository? = null

        fun getInstance(context: android.content.Context): XmppRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val appContext = context.applicationContext
                    val db = AppDatabase.getDatabase(appContext)
                    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                    XmppRepository(appContext, db, appScope).also { INSTANCE = it }
                }
            }
        }
    }

    val engine = XmppProtocolEngine(scope)
    val smackConnectionManager = XmppConnectionManager()
    val smackAvatarManager = XmppAvatarManager(smackConnectionManager, scope)
    val smackMucManager = MultiUserChatManager(smackConnectionManager, smackAvatarManager, scope)
    val smackDiscoManager = ServiceDiscoveryManager(smackConnectionManager)
    val smackRosterManager = RosterManager(smackConnectionManager, smackAvatarManager, scope)
    val smackFileUploadManager = com.ndregil.inchat.data.network.FileTransferManager(context)
    val smackCallManager = JingleCallManager(smackConnectionManager, scope)
    val jabberSearchManager = com.ndregil.inchat.data.network.JabberNetworkSearchManager()
    val backupManager = com.ndregil.inchat.data.local.backup.ChatBackupManager(context, database)

    val connectionStatus: StateFlow<ConnectionStatus> = smackConnectionManager.connectionStatus
    private val _currentAccount = MutableStateFlow(XmppAccount())
    val currentAccount: StateFlow<XmppAccount> = _currentAccount.asStateFlow()
    val stanzaLogs: StateFlow<List<StanzaLog>> = engine.stanzaLogs
    val roomOccupants: StateFlow<Map<String, List<RoomOccupant>>> = smackMucManager.roomOccupants
    val currentCall: StateFlow<CallInfo?> = smackCallManager.currentCall
    val discoveredRooms: StateFlow<List<DiscoveredRoom>> = smackDiscoManager.discoveredRooms
    val isDiscovering: StateFlow<Boolean> = smackDiscoManager.isDiscovering
    val mucServiceDomains: StateFlow<List<String>> = smackDiscoManager.mucServiceDomains
    val rosterContacts: StateFlow<List<RosterContact>> = smackRosterManager.contacts
    val rosterPresenceMap: StateFlow<Map<String, UserPresence>> = smackRosterManager.presenceMap
    val isRosterLoading: StateFlow<Boolean> = smackRosterManager.isLoading

    private val _userPresence = kotlinx.coroutines.flow.MutableStateFlow(UserPresence.ONLINE)
    val userPresence: StateFlow<UserPresence> = _userPresence.asStateFlow()

    private val _userStatusMessage = kotlinx.coroutines.flow.MutableStateFlow("Active on InChat")
    val userStatusMessage: StateFlow<String> = _userStatusMessage.asStateFlow()

    val roomDao = database.roomDao()
    val messageDao = database.messageDao()
    val chatMessageDao = database.chatMessageDao()
    val roomConfigDao = database.roomConfigDao()

    val rooms: Flow<List<XmppRoom>> = database.roomDao().getAllRooms().map { entities ->
        entities.map { it.toModel() }
    }

    val roomConfigs: Flow<List<RoomConfigEntity>> = database.roomConfigDao().getAllRoomConfigs()


    init {
        // Collect incoming network messages from engine and store into Room database
        scope.launch {
            try {
                engine.incomingMessages.collect { msg ->
                    processIncomingMessage(msg)
                }
            } catch (e: Exception) {
                // Handled safely
            }
        }

        // Collect incoming messages from Smack MultiUserChatManager MessageListener
        scope.launch {
            try {
                smackMucManager.incomingMessages.collect { msg ->
                    processIncomingMessage(msg)
                }
            } catch (e: Exception) {
                // Handled safely
            }
        }

        // Collect MUC room topic updates
        scope.launch {
            try {
                smackMucManager.topicUpdates.collect { (roomJid, newTopic) ->
                    database.roomDao().updateTopic(roomJid, newTopic)
                }
            } catch (e: Exception) {
                // Handled safely
            }
        }

        // Auto-login on app launch if active account exists in database
        scope.launch {
            try {
                val savedAccountEntity = database.accountDao().getActiveAccountSync()
                if (savedAccountEntity != null && savedAccountEntity.jid.isNotBlank()) {
                    val accountModel = savedAccountEntity.toModel()
                    _currentAccount.value = accountModel
                    val savedPassword = savedAccountEntity.password
                    connect(accountModel, savedPassword)
                }
            } catch (e: Exception) {
                // Handled gracefully
            }
        }
    }

    /**
     * Checks if the connection dropped while running in the background and reconnects using saved credentials.
     */
    fun checkAndReconnectIfNeeded() {
        scope.launch {
            try {
                if (!smackConnectionManager.isConnected() || !smackConnectionManager.isAuthenticated()) {
                    val savedAccountEntity = database.accountDao().getActiveAccountSync()
                    if (savedAccountEntity != null && savedAccountEntity.jid.isNotBlank()) {
                        val accountModel = savedAccountEntity.toModel()
                        _currentAccount.value = accountModel
                        val savedPassword = savedAccountEntity.password
                        connect(accountModel, savedPassword)
                    }
                }
            } catch (e: Exception) {
                // Handled gracefully
            }
        }
    }

    private suspend fun seedDefaultRoomsIfEmpty() {
        // Function removed to avoid simulated rooms
    }

    fun getMessagesForRoom(roomJid: String): Flow<List<XmppMessage>> {
        return combine(
            database.messageDao().getMessagesForRoom(roomJid),
            smackAvatarManager.avatarUpdates.onStart { emit("") }
        ) { list, _ ->
            list.map { entity ->
                val model = entity.toModel()
                val myJid = currentAccount.value.jid
                val avatarJid = if (model.isMine && myJid.isNotBlank()) myJid else model.senderJid
                model.copy(senderAvatar = smackAvatarManager.getAvatar(avatarJid))
            }
        }
    }

    suspend fun markRoomAsRead(roomJid: String) {
        database.roomDao().markRoomAsRead(roomJid)
    }

    suspend fun connect(account: XmppAccount, password: String = "") {
        val existing = database.accountDao().getActiveAccountSync()
        val pwdToSave = if (password.isNotBlank()) password else (existing?.password ?: "")
        database.accountDao().saveAccount(account.toEntity(pwdToSave))
        _currentAccount.value = account

        // Check if Smack is already connected to this account
        if (smackConnectionManager.isConnected() && smackConnectionManager.isAuthenticated()) {
            val creds = smackConnectionManager.currentCredentials
            if (creds.username.equals(account.username, ignoreCase = true) &&
                creds.domain.equals(account.domain, ignoreCase = true)) {
                // Already connected! Do not disconnect.
                return
            }
        }
        
        // 1. Connect via Smack (Real XMPP)
        val smackResult = smackConnectionManager.connectAndLogin(
            com.ndregil.inchat.data.network.XmppCredentials.fromAccount(account, pwdToSave)
        )
        
        // 2. Connect via Engine (Logs & raw stream) safely
        try {
            engine.connect(account, pwdToSave)
        } catch (e: Exception) {
            // Handled safely
        }

        if (smackResult.isSuccess) {
            val conn = smackResult.getOrNull()
            if (conn != null) {
                smackAvatarManager.onConnectionReady(conn)
            }

            // Auto-join configured rooms upon successful connection
            scope.launch {
                try {
                    autoJoinConfiguredRooms()
                } catch (e: Exception) {
                    // Handled safely
                }
            }
        }
    }

    suspend fun publishProfileAvatar(bytes: ByteArray): Result<Unit> {
        return smackAvatarManager.publishAvatar(bytes)
    }


    suspend fun registerAccount(
        username: String,
        password: String,
        domain: String,
        host: String = "",
        port: Int = 5222,
        nickname: String = "",
        email: String = "",
        useTls: Boolean = true
    ): Result<String> {
        return smackConnectionManager.registerAccount(
            username = username,
            password = password,
            domain = domain,
            host = host,
            port = port,
            nickname = nickname,
            email = email,
            useTls = useTls
        )
    }

    suspend fun disconnect() {
        try {
            smackConnectionManager.disconnect()
        } catch (e: Exception) {
            // Handled gracefully
        }
        engine.disconnect()
        _currentAccount.value = XmppAccount()
    }

    private suspend fun processIncomingMessage(msg: XmppMessage) {
        try {
            database.messageDao().insertMessage(msg.toEntity())

            // Update room last message and unread count
            val existing = database.roomDao().getRoomByJid(msg.roomJid)
            if (existing != null) {
                val updated = existing.copy(
                    lastMessageText = if (msg.messageType == MessageType.WHISPER) "[Bisikan] ${msg.body}" else msg.body,
                    lastMessageTime = msg.timestamp,
                    unreadCount = if (msg.isMine) existing.unreadCount else existing.unreadCount + 1
                )
                database.roomDao().insertOrUpdateRoom(updated)
            }
        } catch (e: Exception) {
            // Handled safely
        }
    }

    suspend fun joinRoom(roomJid: String, name: String, nickname: String, topic: String = "", password: String = "") {
        val existing = database.roomDao().getRoomByJid(roomJid)
        val roomEntity = existing?.copy(
            isJoined = true,
            myNickname = nickname,
            topic = if (topic.isNotBlank()) topic else existing.topic,
            password = if (password.isNotBlank()) password else existing.password
        ) ?: RoomEntity(
            jid = roomJid,
            name = if (name.isNotBlank()) name else "# " + roomJid.substringBefore("@"),
            topic = topic,
            myNickname = nickname,
            occupantCount = 1,
            unreadCount = 0,
            lastMessageText = "Baru saja bergabung",
            lastMessageTime = System.currentTimeMillis(),
            isJoined = true,
            isBookmarked = true,
            myRole = OccupantRole.MEMBER,
            myAffiliation = OccupantAffiliation.MEMBER,
            colorHex = randomColorForRoom(roomJid),
            password = if (password.isNotBlank()) password else null,
            isAutoJoin = true
        )
        database.roomDao().insertOrUpdateRoom(roomEntity)

        // Persist room configuration so room settings and credentials survive app restarts
        val existingConfig = database.roomConfigDao().getRoomConfigSync(roomJid)
        val config = existingConfig?.copy(
            roomName = if (name.isNotBlank()) name else existingConfig.roomName,
            nickname = nickname,
            password = if (password.isNotBlank()) password else existingConfig.password,
            subject = if (topic.isNotBlank()) topic else existingConfig.subject,
            lastJoinedAt = System.currentTimeMillis()
        ) ?: RoomConfigEntity(
            roomJid = roomJid,
            roomName = if (name.isNotBlank()) name else "# " + roomJid.substringBefore("@"),
            nickname = nickname,
            password = if (password.isNotBlank()) password else null,
            isAutoJoin = true,
            notificationsEnabled = true,
            subject = topic,
            colorHex = randomColorForRoom(roomJid),
            lastJoinedAt = System.currentTimeMillis()
        )
        database.roomConfigDao().insertOrUpdateConfig(config)

        // Join via Smack MUC if Smack connection is established
        if (smackConnectionManager.isConnected()) {
            try {
                smackMucManager.joinRoom(roomJid, nickname, password)
            } catch (e: Exception) {
                // Handled gracefully
            }
        }

        engine.joinRoom(roomJid, nickname, password)
    }

    suspend fun leaveRoom(roomJid: String, nickname: String) {
        val existing = database.roomDao().getRoomByJid(roomJid)
        if (existing != null) {
            database.roomDao().insertOrUpdateRoom(existing.copy(isJoined = false))
        }

        if (smackConnectionManager.isConnected()) {
            try {
                smackMucManager.leaveRoom(roomJid)
            } catch (e: Exception) {
                // Handled gracefully
            }
        }

        engine.leaveRoom(roomJid, nickname)
    }

    suspend fun deleteRoom(roomJid: String) {
        database.roomDao().deleteRoom(roomJid)
        database.roomConfigDao().deleteConfig(roomJid)
        database.messageDao().clearRoomMessages(roomJid)
    }

    /**
     * Automatically rejoins rooms configured with isAutoJoin = true or saved in Room database using persisted credentials.
     */
    suspend fun autoJoinConfiguredRooms() {
        val autoJoinConfigs = database.roomConfigDao().getAutoJoinRoomConfigsList()
        val savedRooms = database.roomDao().getAllRoomsList()
        val joinedJids = mutableSetOf<String>()

        for (cfg in autoJoinConfigs) {
            joinedJids.add(cfg.roomJid)
            joinRoom(
                roomJid = cfg.roomJid,
                name = cfg.roomName,
                nickname = cfg.nickname.ifBlank { currentAccount.value.nickname.ifBlank { currentAccount.value.username.ifBlank { "User" } } },
                topic = cfg.subject,
                password = cfg.password.orEmpty()
            )
        }

        for (room in savedRooms) {
            if (!joinedJids.contains(room.jid) && (room.isJoined || room.isBookmarked || room.isAutoJoin)) {
                joinRoom(
                    roomJid = room.jid,
                    name = room.name,
                    nickname = room.myNickname.ifBlank { currentAccount.value.nickname.ifBlank { currentAccount.value.username.ifBlank { "User" } } },
                    topic = room.topic,
                    password = room.password.orEmpty()
                )
            }
        }
    }

    fun getRoomConfig(roomJid: String): Flow<RoomConfigEntity?> {
        return database.roomConfigDao().getRoomConfig(roomJid)
    }

    suspend fun getRoomConfigSync(roomJid: String): RoomConfigEntity? {
        return database.roomConfigDao().getRoomConfigSync(roomJid)
    }

    suspend fun saveRoomConfig(config: RoomConfigEntity) {
        database.roomConfigDao().insertOrUpdateConfig(config)
        database.roomDao().updateAutoJoin(config.roomJid, config.isAutoJoin)
        if (config.password != null) {
            database.roomDao().updatePassword(config.roomJid, config.password)
        }
    }

    suspend fun toggleSaveRoom(roomJid: String) {
        val room = database.roomDao().getRoomByJid(roomJid)
        val isCurrentlyBookmarked = room?.isBookmarked ?: false
        val newBookmarkState = !isCurrentlyBookmarked

        if (room != null) {
            database.roomDao().updateBookmark(roomJid, newBookmarkState)
        }

        val config = database.roomConfigDao().getRoomConfigSync(roomJid)
        if (config != null) {
            database.roomConfigDao().updateBookmark(roomJid, newBookmarkState)
        } else {
            val newConfig = RoomConfigEntity(
                roomJid = roomJid,
                roomName = room?.name ?: roomJid.substringBefore("@"),
                nickname = room?.myNickname ?: currentAccount.value.nickname,
                isBookmarked = newBookmarkState
            )
            database.roomConfigDao().insertOrUpdateConfig(newConfig)
        }
    }

    suspend fun saveRoom(
        roomJid: String,
        roomName: String,
        nickname: String,
        topic: String = "",
        password: String? = null
    ) {
        val cleanJid = roomJid.trim()
        val name = if (roomName.isNotBlank()) roomName.trim() else "# " + cleanJid.substringBefore("@")
        val nick = if (nickname.isNotBlank()) nickname.trim() else currentAccount.value.nickname.ifBlank { "User" }

        val existingConfig = database.roomConfigDao().getRoomConfigSync(cleanJid)
        val config = (existingConfig ?: RoomConfigEntity(
            roomJid = cleanJid,
            roomName = name,
            nickname = nick,
            password = password,
            subject = topic,
            isBookmarked = true,
            isAutoJoin = true
        )).copy(
            roomName = name,
            nickname = nick,
            password = password ?: existingConfig?.password,
            subject = if (topic.isNotBlank()) topic else (existingConfig?.subject ?: ""),
            isBookmarked = true
        )
        database.roomConfigDao().insertOrUpdateConfig(config)

        val existingRoom = database.roomDao().getRoomByJid(cleanJid)
        if (existingRoom != null) {
            database.roomDao().insertOrUpdateRoom(
                existingRoom.copy(
                    name = name,
                    topic = if (topic.isNotBlank()) topic else existingRoom.topic,
                    myNickname = nick,
                    password = password ?: existingRoom.password,
                    isBookmarked = true
                )
            )
        } else {
            val newRoom = RoomEntity(
                jid = cleanJid,
                name = name,
                topic = topic.ifBlank { "Ruangan Tersimpan" },
                myNickname = nick,
                occupantCount = 1,
                unreadCount = 0,
                lastMessageText = "Ruangan disimpan ke favorit",
                lastMessageTime = System.currentTimeMillis(),
                isJoined = false,
                isBookmarked = true,
                myRole = OccupantRole.PARTICIPANT,
                myAffiliation = OccupantAffiliation.MEMBER,
                colorHex = randomColorForRoom(cleanJid),
                password = password
            )
            database.roomDao().insertOrUpdateRoom(newRoom)
        }
    }

    suspend fun removeSavedRoom(roomJid: String) {
        database.roomDao().updateBookmark(roomJid, false)
        database.roomConfigDao().updateBookmark(roomJid, false)
    }

    suspend fun updateRoomAutoJoin(roomJid: String, isAutoJoin: Boolean) {
        database.roomConfigDao().updateAutoJoin(roomJid, isAutoJoin)
        database.roomDao().updateAutoJoin(roomJid, isAutoJoin)
    }

    suspend fun updateRoomPassword(roomJid: String, password: String?) {
        database.roomConfigDao().updatePassword(roomJid, password)
        database.roomDao().updatePassword(roomJid, password)
    }

    suspend fun configureRoom(
        roomJid: String,
        roomName: String? = null,
        topic: String? = null,
        isPersistent: Boolean = true,
        isPublic: Boolean = true,
        password: String? = null
    ): Result<Unit> {
        return smackMucManager.configureRoom(roomJid, roomName, topic, isPersistent, isPublic, password)
    }

    suspend fun getRoomInfo(roomJid: String): Result<org.jivesoftware.smackx.muc.RoomInfo> {
        return smackMucManager.getRoomInfo(roomJid)
    }

    suspend fun updateRoomNickname(roomJid: String, nickname: String) {
        database.roomConfigDao().updateNickname(roomJid, nickname)
    }

    suspend fun updateRoomThemeAndWallpaper(roomJid: String, bubbleTheme: String, wallpaperStyle: String) {
        database.roomConfigDao().updateThemeAndWallpaper(roomJid, bubbleTheme, wallpaperStyle)
        database.roomDao().updateThemeAndWallpaper(roomJid, bubbleTheme, wallpaperStyle)
    }

    suspend fun clearRoomChatHistory(roomJid: String) {
        database.messageDao().clearRoomMessages(roomJid)
    }

    fun searchRoomChatHistory(roomJid: String, query: String): Flow<List<XmppMessage>> {
        return database.messageDao().searchMessages(roomJid, query).map { list ->
            list.map { it.toModel() }
        }
    }

    suspend fun updateMessageDeliveryStatus(messageId: String, status: DeliveryStatus) {
        database.messageDao().updateMessageStatus(messageId, status)
    }


    suspend fun sendMessage(
        roomJid: String,
        text: String,
        myNickname: String,
        replyToMessageId: String? = null,
        replyToSender: String? = null,
        replyToText: String? = null
    ) {
        // If Smack MUC is connected and room joined, send via Smack MUC
        if (smackConnectionManager.isConnected() && smackMucManager.isRoomJoined(roomJid)) {
            try {
                smackMucManager.sendMessage(roomJid, text)
                return
            } catch (e: Exception) {
                // Handled gracefully, fall back to offline engine
            }
        }

        val myJid = currentAccount.value.jid
        val myAvatar = smackAvatarManager.getAvatar(myJid)
        
        val msg = engine.sendGroupMessage(roomJid, text, myNickname).copy(
            senderJid = myJid,
            senderAvatar = myAvatar,
            replyToMessageId = replyToMessageId,
            replyToSender = replyToSender,
            replyToText = replyToText
        )
        database.messageDao().insertMessage(msg.toEntity())
        
        val existing = database.roomDao().getRoomByJid(roomJid)
        if (existing != null) {
            database.roomDao().insertOrUpdateRoom(
                existing.copy(
                    lastMessageText = text,
                    lastMessageTime = msg.timestamp
                )
            )
        }
    }

    suspend fun querySoftwareVersion(targetJid: String): Result<com.ndregil.inchat.data.model.ClientCaps> {
        return smackConnectionManager.querySoftwareVersion(targetJid)
    }

    suspend fun sendWhisper(roomJid: String, targetNickname: String, text: String, myNickname: String) {
        // If Smack MUC is connected and room joined, send whisper via Smack
        if (smackConnectionManager.isConnected() && smackMucManager.isRoomJoined(roomJid)) {
            try {
                smackMucManager.sendWhisper(roomJid, targetNickname, text)
            } catch (e: Exception) {
                // Handled gracefully
            }
        }

        val myJid = currentAccount.value.jid
        val myAvatar = smackAvatarManager.getAvatar(myJid)

        val msg = engine.sendPrivateWhisper(roomJid, targetNickname, text, myNickname).copy(senderAvatar = myAvatar)
        database.messageDao().insertMessage(msg.toEntity())
    }

    suspend fun updateRoomTopic(roomJid: String, newTopic: String, myNickname: String) {
        database.roomDao().updateTopic(roomJid, newTopic)

        if (smackConnectionManager.isConnected() && smackMucManager.isRoomJoined(roomJid)) {
            try {
                smackMucManager.changeSubject(roomJid, newTopic)
            } catch (e: Exception) {
                // Handled gracefully
            }
        }

    engine.changeRoomTopic(roomJid, newTopic, myNickname)
    }

    suspend fun sendFile(
        roomJid: String,
        uri: android.net.Uri,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        myNickname: String
    ) {
        // 1. Upload via XEP-0363 if connected
        if (!smackConnectionManager.isConnected()) {
             android.util.Log.e("XmppRepository", "Gagal upload: Tidak terhubung ke server XMPP")
             return
        }

        val connection = smackConnectionManager.getRawConnection() ?: return
        
        // Use real XEP-0363 upload
        val uploadResult = smackFileUploadManager.uploadFile(connection, uri, fileName, fileSize, mimeType)
        
        uploadResult.fold(
            onSuccess = { url ->
                // 2. Send message via engine (local storage)
                val msg = engine.sendGroupFile(roomJid, url, fileName, mimeType, fileSize, myNickname).copy(
                    senderJid = connection.user.asBareJid().toString()
                )
                database.messageDao().insertMessage(msg.toEntity())

                // 3. Send via Smack MUC to the room
                if (smackMucManager.isRoomJoined(roomJid)) {
                    try {
                        smackMucManager.sendMessage(roomJid, url)
                    } catch (e: Exception) {
                        android.util.Log.e("XmppRepository", "Gagal mengirim URL file ke MUC: ${e.message}")
                    }
                }
            },
            onFailure = { error ->
                android.util.Log.e("XmppRepository", "XEP-0363 Upload failed: ${error.message}", error)
                // We could insert a system message or failed message here
            }
        )
    }

    suspend fun initiateCall(roomJid: String, callType: String, myNickname: String, targetNickname: String? = null) {
        val msgBody = if (targetNickname != null) {
            "Memulai panggilan $callType ke @$targetNickname..."
        } else {
            "Memulai panggilan $callType grup..."
        }
        
        val msg = engine.sendCallSignalling(roomJid, msgBody, myNickname)
        database.messageDao().insertMessage(msg.toEntity())
        
        if (smackConnectionManager.isConnected()) {
            val targetJid = if (targetNickname != null) "$roomJid/$targetNickname" else roomJid
            smackCallManager.initiateCall(targetJid, targetNickname ?: "Group", callType)
            
            if (smackMucManager.isRoomJoined(roomJid)) {
                try {
                    smackMucManager.sendMessage(roomJid, msgBody)
                } catch (e: Exception) {
                    // Handled gracefully
                }
            }
        }
    }

    suspend fun initiate1on1Call(targetJid: String, nickname: String, callType: String) {
        if (smackConnectionManager.isConnected()) {
            smackCallManager.initiateCall(targetJid, nickname, callType)
        }
    }

    suspend fun sendFileToContact(targetJid: String, uri: android.net.Uri) {
        if (smackConnectionManager.isConnected()) {
            val connection = smackConnectionManager.getRawConnection() ?: return
            
            // In a real app we'd get file details from content resolver
            val fileName = "file_${System.currentTimeMillis()}"
            val fileSize = 1024L // Placeholder
            val mimeType = "application/octet-stream"

            val uploadResult = smackFileUploadManager.uploadFile(connection, uri, fileName, fileSize, mimeType)
            val url = uploadResult.getOrNull() ?: return
            
            // Send URL as message to contact
            val message = org.jivesoftware.smack.packet.Message(JidCreate.from(targetJid), org.jivesoftware.smack.packet.Message.Type.chat)
            message.body = "Membagikan file: $url"
            connection.sendStanza(message)
        }
    }

    suspend fun exportBackup(targetUri: android.net.Uri? = null, passphrase: String = ""): Result<String> {
        return backupManager.exportBackup(targetUri, passphrase)
    }

    suspend fun importBackup(sourceUri: android.net.Uri, passphrase: String = ""): Result<String> {
        return backupManager.importBackup(sourceUri, passphrase)
    }

    suspend fun queryDirectory(): List<PublicDirectoryRoom> {
        val discoRooms = smackDiscoManager.discoverRooms().getOrNull()
        if (!discoRooms.isNullOrEmpty()) {
            return discoRooms.map { it.toPublicDirectoryRoom() }
        }
        return engine.queryPublicDirectory()
    }

    suspend fun discoverRooms(query: String = "", serviceJid: String? = null): List<DiscoveredRoom> {
        // If a specific query is provided, or if we want global discovery, use search.jabber.network
        if (query.isNotBlank() || serviceJid == null) {
            val searchResult = jabberSearchManager.searchRooms(query).getOrNull()
            if (!searchResult.isNullOrEmpty()) {
                return searchResult
            }
        }

        // Fallback to traditional XEP-0030 Service Discovery on the server
        val result = smackDiscoManager.discoverRooms(serviceJid)
        return result.getOrElse { emptyList() }
    }

    suspend fun loadRosterContacts(): List<RosterContact> {
        return smackRosterManager.loadContacts().getOrElse { smackRosterManager.contacts.value }
    }

    suspend fun addRosterContact(jid: String, name: String, group: String? = null): Result<Unit> {
        return smackRosterManager.addContact(jid, name, group)
    }

    suspend fun removeRosterContact(jid: String): Result<Unit> {
        return smackRosterManager.removeContact(jid)
    }

    suspend fun renameRosterContact(jid: String, newName: String): Result<Unit> {
        return smackRosterManager.renameContact(jid, newName)
    }

    suspend fun addRosterContactToGroup(jid: String, group: String): Result<Unit> {
        return smackRosterManager.addContactToGroup(jid, group)
    }

    fun getContactPresence(jid: String): UserPresence {
        return smackRosterManager.getContactPresence(jid)
    }

    fun refreshRoomOccupants(roomJid: String) {
        smackMucManager.refreshOccupants(roomJid)
    }

    suspend fun updateUserPresence(presence: UserPresence, statusMessage: String) {
        _userPresence.value = presence
        _userStatusMessage.value = statusMessage

        val myNick = currentAccount.value.nickname

        // 1. Update Smack TCP Connection presence (broadcasts to all roster contacts via XMPP server)
        try {
            smackConnectionManager.updatePresence(presence, statusMessage)
        } catch (e: Exception) {
            // Handled safely
        }

        // 2. Broadcast presence to all joined MUC rooms in Smack
        try {
            smackMucManager.updatePresenceInRooms(presence, statusMessage)
        } catch (e: Exception) {
            // Handled safely
        }

        // 3. Update protocol engine (multi-room occupant simulation and raw stanzas)
        try {
            engine.updateUserPresence(presence, statusMessage, myNick)
        } catch (e: Exception) {
            // Handled safely
        }
    }

    fun sendRawStanza(xml: String) {
        engine.sendRawStanza(xml)
    }

    suspend fun grantModerator(roomJid: String, nickname: String) = smackMucManager.grantModerator(roomJid, nickname)
    suspend fun revokeModerator(roomJid: String, nickname: String) = smackMucManager.revokeModerator(roomJid, nickname)
    suspend fun grantMembership(roomJid: String, jid: String) = smackMucManager.grantMembership(roomJid, jid)
    suspend fun revokeMembership(roomJid: String, jid: String) = smackMucManager.revokeMembership(roomJid, jid)
    suspend fun grantAdmin(roomJid: String, jid: String) = smackMucManager.grantAdmin(roomJid, jid)
    suspend fun revokeAdmin(roomJid: String, jid: String) = smackMucManager.revokeAdmin(roomJid, jid)
    suspend fun banUser(roomJid: String, jid: String, reason: String?) = smackMucManager.banUser(roomJid, jid, reason)
    suspend fun kickParticipant(roomJid: String, nickname: String, reason: String?) = smackMucManager.kickParticipant(roomJid, nickname, reason)

    private fun randomColorForRoom(roomJid: String): String {
        val colors = listOf("#0284C7", "#0D9488", "#7C3AED", "#D97706", "#DC2626", "#2563EB", "#059669")
        val index = kotlin.math.abs(roomJid.hashCode()) % colors.size
        return colors[index]
    }
}

private fun AccountEntity.toModel(): XmppAccount = XmppAccount(
    jid = jid,
    username = username,
    domain = domain,
    nickname = nickname,
    host = host,
    port = port,
    useTls = useTls,
    isDemoMode = isDemoMode,
    statusMessage = statusMessage
)

private fun XmppAccount.toEntity(password: String = ""): AccountEntity = AccountEntity(
    jid = jid,
    username = username,
    domain = domain,
    nickname = nickname,
    host = host,
    port = port,
    useTls = useTls,
    isDemoMode = isDemoMode,
    statusMessage = statusMessage,
    password = password,
    isActive = true
)

private fun RoomEntity.toModel(): XmppRoom = XmppRoom(
    jid = jid,
    name = name,
    topic = topic,
    myNickname = myNickname,
    occupantCount = occupantCount,
    unreadCount = unreadCount,
    lastMessageText = lastMessageText,
    lastMessageTime = lastMessageTime,
    isJoined = isJoined,
    isBookmarked = isBookmarked,
    myRole = myRole,
    myAffiliation = myAffiliation,
    colorHex = colorHex,
    bubbleTheme = bubbleTheme,
    wallpaperStyle = wallpaperStyle
)

private fun MessageEntity.toModel(): XmppMessage = XmppMessage(
    id = id,
    roomJid = roomJid,
    senderJid = senderJid,
    senderNickname = senderNickname,
    body = body,
    timestamp = timestamp,
    isMine = isMine,
    messageType = messageType,
    status = status,
    recipientNickname = recipientNickname,
    fileUrl = fileUrl,
    fileMimeType = fileMimeType,
    fileName = fileName,
    fileSize = fileSize,
    replyToMessageId = replyToMessageId,
    replyToSender = replyToSender,
    replyToText = replyToText
)

private fun XmppMessage.toEntity(): MessageEntity = MessageEntity(
    id = id,
    roomJid = roomJid,
    senderJid = senderJid,
    senderNickname = senderNickname,
    body = body,
    timestamp = timestamp,
    isMine = isMine,
    messageType = messageType,
    status = status,
    recipientNickname = recipientNickname,
    fileUrl = fileUrl,
    fileMimeType = fileMimeType,
    fileName = fileName,
    fileSize = fileSize,
    replyToMessageId = replyToMessageId,
    replyToSender = replyToSender,
    replyToText = replyToText
)
