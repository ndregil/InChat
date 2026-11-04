package com.ndregil.inchat.data.repository

import com.ndregil.inchat.data.local.AppDatabase
import android.util.Log
import com.ndregil.inchat.data.local.entity.AccountEntity
import com.ndregil.inchat.data.local.entity.ContactEntity
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
import com.ndregil.inchat.data.network.fcm.FcmPushManager
import com.ndregil.inchat.data.network.fcm.PushMessagePayload
import org.jxmpp.jid.impl.JidCreate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class XmppRepository(
    private val context: android.content.Context,
    private val database: AppDatabase,
    private val scope: CoroutineScope
) {
    private val connectionMutex = Mutex()

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
    val connectionError: StateFlow<String?> = smackConnectionManager.lastError
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
    val autoApproveSubscription: StateFlow<Boolean> = smackRosterManager.autoApproveSubscription
    fun setAutoApproveSubscription(enabled: Boolean) {
        smackRosterManager.setAutoApproveSubscription(enabled)
    }

    private val _userPresence = kotlinx.coroutines.flow.MutableStateFlow(UserPresence.ONLINE)
    val userPresence: StateFlow<UserPresence> = _userPresence.asStateFlow()

    private val _userStatusMessage = kotlinx.coroutines.flow.MutableStateFlow("Active on InChat")
    val userStatusMessage: StateFlow<String> = _userStatusMessage.asStateFlow()

    private val _autoJoinEvents = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val autoJoinEvents: SharedFlow<String> = _autoJoinEvents.asSharedFlow()

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

        // Collect connection status to register direct 1-on-1 chat & whisper stanza listener
        scope.launch {
            try {
                smackConnectionManager.connectionStatus.collect { status ->
                    if (status == ConnectionStatus.CONNECTED) {
                        setupIncomingStanzaListener()
                    }
                }
            } catch (e: Exception) {
                // Handled safely
            }
        }

        // Restore contacts from Room Database on launch for offline availability across restarts
        scope.launch {
            try {
                val storedContacts = database.contactDao().getAllContactsList()
                if (storedContacts.isNotEmpty()) {
                    Log.i("XmppRepository", "Restored ${storedContacts.size} contacts from local database.")
                    smackRosterManager.setInitialContacts(storedContacts.map { it.toModel() })
                }
            } catch (e: Exception) {
                Log.w("XmppRepository", "Error restoring contacts from database: ${e.message}")
            }
        }

        // Persist roster contact updates to local Room Database
        scope.launch {
            try {
                smackRosterManager.contacts.collect { contactsList ->
                    if (contactsList.isNotEmpty()) {
                        val entities = contactsList.map { ContactEntity.fromModel(it) }
                        database.contactDao().insertOrUpdateContacts(entities)
                    }
                }
            } catch (e: Exception) {
                Log.w("XmppRepository", "Error syncing contacts to database: ${e.message}")
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
                // Do not auto-reconnect if last failure was an authentication error (wrong password/not-authorized)
                val currentError = smackConnectionManager.lastError.value
                if (currentError != null && currentError.contains("Autentikasi gagal", ignoreCase = true)) {
                    Log.w("XmppRepository", "Skipping background reconnect because authentication credentials failed: $currentError")
                    return@launch
                }

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

    suspend fun connect(account: XmppAccount, password: String = "") = connectionMutex.withLock {
        // If there is an active authentication error and no NEW password was provided in this call, abort to prevent infinite loop
        val lastErr = smackConnectionManager.lastError.value
        if (lastErr != null && lastErr.contains("Autentikasi gagal", ignoreCase = true) && password.isBlank()) {
            Log.w("XmppRepository", "Aborting connection attempt: authentication previously failed and no new password provided.")
            return@withLock
        }

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
                return@withLock
            }
        }
        
        // 1. Connect via Smack (Real XMPP)
        val smackResult = smackConnectionManager.connectAndLogin(
            com.ndregil.inchat.data.network.XmppCredentials.fromAccount(account, pwdToSave)
        )

        if (smackResult.isFailure) {
            val err = smackResult.exceptionOrNull()
            if (err is org.jivesoftware.smack.sasl.SASLErrorException || (err?.localizedMessage?.contains("not-authorized", ignoreCase = true) == true)) {
                // Clear invalid saved password in database so background checks won't auto-retry with bad password
                database.accountDao().saveAccount(account.toEntity(""))
            }
        }
        
        // 2. Do not open a conflicting parallel raw socket connection if Smack is handling the real XMPP session
        if (smackResult.isFailure && account.isDemoMode) {
            try {
                engine.connect(account, pwdToSave)
            } catch (e: Exception) {
                // Handled safely
            }
        }

        if (smackResult.isSuccess) {
            val conn = smackResult.getOrNull()
            if (conn != null) {
                smackAvatarManager.onConnectionReady(conn)
                setupReceiptListener(conn)
                setupIncomingStanzaListener()
            }

            // Initialize and synchronize XMPP Roster contacts (RFC 6121, XEP-0115, XEP-0172)
            scope.launch {
                try {
                    smackRosterManager.initializeRoster()
                    smackRosterManager.loadContacts(forceReload = true)
                } catch (e: Exception) {
                    Log.e("XmppRepository", "Error during roster sync: ${e.message}")
                }
            }

            // Sync server-side bookmarks and auto-join
            scope.launch {
                try {
                    // Small delay to ensure server feature discovery and connection stability
                    kotlinx.coroutines.delay(2000) 
                    syncServerBookmarks()
                    autoJoinConfiguredRooms()
                } catch (e: Exception) {
                    Log.e("XmppRepository", "Error during bookmark sync/autojoin: ${e.message}")
                }
            }
        }
    }

    /**
     * Synchronizes server-side bookmarks (XEP-0048) with the local database.
     */
    suspend fun syncServerBookmarks() {
        if (!smackConnectionManager.isConnected()) {
            Log.w("XmppRepository", "Cannot sync bookmarks: Not connected")
            return
        }
        
        Log.i("XmppRepository", "Syncing server-side bookmarks (XEP-0048)...")
        val result = smackMucManager.fetchBookmarks()
        result.onSuccess { bookmarks ->
            Log.i("XmppRepository", "Fetched ${bookmarks.size} bookmarks from server.")
            for (bm in bookmarks) {
                val roomJid = bm.jid.toString()
                Log.d("XmppRepository", "Processing bookmark: $roomJid (autoJoin=${bm.isAutoJoin})")
                val name = bm.name ?: roomJid.substringBefore("@")
                val nick = bm.nickname?.toString() ?: currentAccount.value.nickname
                val autoJoin = bm.isAutoJoin
                
                // Save to local database if not exists or update
                saveRoomLocally(
                    roomJid = roomJid,
                    roomName = name,
                    nickname = nick,
                    isBookmarked = true,
                    isAutoJoin = autoJoin,
                    password = bm.password
                )
            }
        }
    }

    private suspend fun saveRoomLocally(
        roomJid: String,
        roomName: String,
        nickname: String,
        isBookmarked: Boolean,
        isAutoJoin: Boolean = true,
        password: String? = null
    ) {
        val existingRoom = database.roomDao().getRoomByJid(roomJid)
        if (existingRoom != null) {
            database.roomDao().insertOrUpdateRoom(
                existingRoom.copy(
                    name = roomName,
                    myNickname = nickname,
                    isBookmarked = isBookmarked,
                    isAutoJoin = isAutoJoin,
                    password = password ?: existingRoom.password
                )
            )
        } else {
            val newRoom = RoomEntity(
                jid = roomJid,
                name = roomName,
                topic = "Sinkronisasi Favorit",
                myNickname = nickname,
                occupantCount = 1,
                unreadCount = 0,
                lastMessageText = "Sinkronisasi dari server",
                lastMessageTime = System.currentTimeMillis(),
                isJoined = false,
                isBookmarked = isBookmarked,
                myRole = OccupantRole.MEMBER,
                myAffiliation = OccupantAffiliation.MEMBER,
                colorHex = randomColorForRoom(roomJid),
                password = password,
                isAutoJoin = isAutoJoin
            )
            database.roomDao().insertOrUpdateRoom(newRoom)
        }

        val existingConfig = database.roomConfigDao().getRoomConfigSync(roomJid)
        val config = (existingConfig ?: RoomConfigEntity(
            roomJid = roomJid,
            roomName = roomName,
            nickname = nickname,
            isBookmarked = isBookmarked,
            isAutoJoin = isAutoJoin,
            password = password
        )).copy(
            roomName = roomName,
            nickname = nickname,
            isBookmarked = isBookmarked,
            isAutoJoin = isAutoJoin,
            password = password ?: existingConfig?.password
        )
        database.roomConfigDao().insertOrUpdateConfig(config)
    }

    suspend fun publishProfileAvatar(bytes: ByteArray): Result<Unit> {
        return smackAvatarManager.publishAvatar(bytes)
    }

    suspend fun updateRoomAvatar(roomJid: String, bytes: ByteArray): Result<Unit> {
        return smackAvatarManager.updateRoomAvatar(roomJid, bytes)
    }

    fun getRoomAvatar(roomJid: String): ByteArray? {
        return smackAvatarManager.getAvatar(roomJid)
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

    suspend fun submitCaptchaAnswer(
        challenge: com.ndregil.inchat.data.network.CaptchaChallengeData,
        captchaAnswer: String
    ): Result<String> {
        return smackConnectionManager.submitCaptchaAnswer(challenge, captchaAnswer)
    }

    suspend fun disconnect() {
        try {
            smackRosterManager.release()
        } catch (_: Exception) {}
        try {
            smackConnectionManager.disconnect()
        } catch (e: Exception) {
            // Handled gracefully
        }
        engine.disconnect()
        _currentAccount.value = XmppAccount()
    }

    suspend fun signOut() {
        try {
            smackRosterManager.release()
        } catch (_: Exception) {}
        try {
            smackConnectionManager.disconnect()
        } catch (_: Exception) {}
        try {
            engine.disconnect()
        } catch (_: Exception) {}
        database.accountDao().clearAccounts()
        _currentAccount.value = XmppAccount()
    }

    suspend fun exitApp() {
        try {
            smackRosterManager.release()
        } catch (_: Exception) {}
        try {
            smackConnectionManager.disconnect()
        } catch (_: Exception) {}
        try {
            engine.disconnect()
        } catch (_: Exception) {}
    }

    private var lastAttachedConnection: org.jivesoftware.smack.XMPPConnection? = null
    private val processedStanzaIds = java.util.Collections.synchronizedSet(java.util.HashSet<String>())

    private fun setupIncomingStanzaListener() {
        val conn = smackConnectionManager.getRawConnection() ?: return
        if (!conn.isAuthenticated || lastAttachedConnection == conn) return
        lastAttachedConnection = conn

        val filter = org.jivesoftware.smack.filter.StanzaTypeFilter(org.jivesoftware.smack.packet.Message::class.java)
        conn.addAsyncStanzaListener({ stanza ->
            val message = stanza as? org.jivesoftware.smack.packet.Message ?: return@addAsyncStanzaListener
            val hasOmemo = message.hasExtension("encrypted", "urn:xmpp:omemo:2") ||
                    message.hasExtension("encrypted", "eu.siacs.conversations.axolotl")
            val hasOpenPgp = message.hasExtension("openpgp", "urn:xmpp:openpgp:0")

            var bodyText = message.body
            if (bodyText.isNullOrBlank()) {
                if (hasOmemo) {
                    bodyText = "🔒 [Pesan Terenkripsi OMEMO]"
                } else if (hasOpenPgp) {
                    bodyText = "🔒 [Pesan Terenkripsi OpenPGP]"
                } else {
                    return@addAsyncStanzaListener
                }
            }

            val stanzaId = message.stanzaId
            if (!stanzaId.isNullOrBlank()) {
                if (processedStanzaIds.contains(stanzaId)) return@addAsyncStanzaListener
                if (processedStanzaIds.size > 500) processedStanzaIds.clear()
                processedStanzaIds.add(stanzaId)
            }

            val fromJid = message.from ?: return@addAsyncStanzaListener
            val fromStr = fromJid.toString()
            val fromBareJid = fromJid.asBareJid().toString()
            val myJid = currentAccount.value.jid
            val myBareJid = if (myJid.contains("@")) myJid.substringBefore("@") + "@" + myJid.substringAfter("@").substringBefore("/") else myJid

            // Ignore messages sent by self on this same resource connection
            if (fromBareJid.equals(myBareJid, ignoreCase = true) && fromStr.equals(conn.user?.toString(), ignoreCase = true)) {
                return@addAsyncStanzaListener
            }

            // Skip standard MUC groupchat stanzas (handled by MultiUserChat listeners)
            if (message.type == org.jivesoftware.smack.packet.Message.Type.groupchat) {
                return@addAsyncStanzaListener
            }

            val resourcePart = (fromJid as? org.jxmpp.jid.FullJid)?.resourcepart?.toString()
                ?: fromStr.substringAfterLast("/").takeIf { fromStr.contains("/") && !it.equals(fromBareJid, ignoreCase = true) }

            scope.launch(Dispatchers.IO) {
                val roomEntity = database.roomDao().getRoomByJid(fromBareJid)
                val isMucContext = smackMucManager.isRoomJoined(fromBareJid) ||
                        fromBareJid.contains("conference.", ignoreCase = true) ||
                        fromBareJid.contains("muc.", ignoreCase = true) ||
                        roomEntity != null ||
                        message.type == org.jivesoftware.smack.packet.Message.Type.groupchat

                // CASE 1: Private WHISPER Message in MUC Room or MUC context
                if (isMucContext && !fromBareJid.equals(myBareJid, ignoreCase = true)) {
                    val roomJid = fromBareJid
                    val senderNick = resourcePart ?: fromStr.substringAfterLast("/").takeIf { it.isNotBlank() } ?: fromBareJid.substringBefore("@")
                    val myRoomNick = roomEntity?.myNickname ?: currentAccount.value.nickname

                    // Skip if sender is ourselves inside MUC
                    if (senderNick.equals(myRoomNick, ignoreCase = true)) return@launch

                    val avatar = smackAvatarManager.getAvatar(fromStr) ?: smackAvatarManager.getAvatar(roomJid)

                    val xmppMsg = XmppMessage(
                        id = stanzaId ?: java.util.UUID.randomUUID().toString(),
                        roomJid = roomJid,
                        senderNickname = senderNick,
                        senderJid = fromStr,
                        senderAvatar = avatar,
                        body = bodyText,
                        timestamp = System.currentTimeMillis(),
                        isMine = false,
                        messageType = MessageType.WHISPER,
                        status = DeliveryStatus.DELIVERED,
                        recipientNickname = myRoomNick
                    )

                    Log.i("XmppRepository", "Received WHISPER from '$senderNick' in room [$roomJid]: $bodyText")
                    processIncomingMessage(xmppMsg)
                    return@launch
                }

                // CASE 2: 1-on-1 Direct Chat message from a contact or non-contact JID
                if (fromBareJid.equals(myBareJid, ignoreCase = true)) {
                    return@launch
                }

                val contactMatch = rosterContacts.value.find { it.jid.equals(fromBareJid, ignoreCase = true) }
                val senderNick = contactMatch?.name?.takeIf { it.isNotBlank() }
                    ?: fromBareJid.substringBefore("@")

                val avatar = smackAvatarManager.getAvatar(fromBareJid)

                val xmppMsg = XmppMessage(
                    id = stanzaId ?: java.util.UUID.randomUUID().toString(),
                    roomJid = fromBareJid,
                    senderNickname = senderNick,
                    senderJid = fromBareJid,
                    senderAvatar = avatar,
                    body = bodyText,
                    timestamp = System.currentTimeMillis(),
                    isMine = false,
                    messageType = MessageType.CHAT,
                    status = DeliveryStatus.DELIVERED
                )

                Log.i("XmppRepository", "Received 1-on-1 direct message from [$fromBareJid] ('$senderNick'): $bodyText")
                processIncomingMessage(xmppMsg)
            }
        }, filter)

        Log.i("XmppRepository", "StanzaListener attached to connection ${conn.user} for direct messages & whispers")
    }

    private fun setupReceiptListener(connection: org.jivesoftware.smack.XMPPConnection) {
        val receiptManager = org.jivesoftware.smackx.receipts.DeliveryReceiptManager.getInstanceFor(connection)
        receiptManager.addReceiptReceivedListener { fromJid, toJid, receiptId, receipt ->
            Log.d("XmppRepository", "Received delivery receipt for message $receiptId from $fromJid")
            scope.launch {
                try {
                    database.messageDao().updateMessageStatus(receiptId, DeliveryStatus.DELIVERED)
                } catch (e: Exception) {
                    Log.e("XmppRepository", "Failed to update message status on receipt: ${e.message}")
                }
            }
        }
    }

    private suspend fun processIncomingMessage(msg: XmppMessage) {
        try {
            database.messageDao().insertMessage(msg.toEntity())

            // Update room last message and unread count
            val existing = database.roomDao().getRoomByJid(msg.roomJid)
            if (existing != null) {
                val updated = existing.copy(
                    lastMessageText = when(msg.messageType) {
                        MessageType.WHISPER -> "[Bisikan] ${msg.body}"
                        MessageType.CHAT -> msg.body
                        MessageType.FILE -> "[Berkas] ${msg.fileName ?: "Lampiran"}"
                        MessageType.VOICE -> "[Pesan Suara]"
                        MessageType.ACTION -> "* ${msg.senderNickname} ${msg.body}"
                        else -> msg.body
                    },
                    lastMessageTime = msg.timestamp,
                    unreadCount = if (msg.isMine) existing.unreadCount else existing.unreadCount + 1
                )
                database.roomDao().insertOrUpdateRoom(updated)
            } else {
                // Auto-create 1-on-1 chat room for incoming direct message
                val name = if (msg.senderNickname.isNotBlank() && msg.senderNickname != "System") msg.senderNickname else msg.roomJid.substringBefore("@")
                val newRoom = RoomEntity(
                    jid = msg.roomJid,
                    name = name,
                    topic = "Obrolan Langsung (1-on-1)",
                    myNickname = currentAccount.value.nickname.ifBlank { "User" },
                    occupantCount = 2,
                    unreadCount = if (msg.isMine) 0 else 1,
                    lastMessageText = when(msg.messageType) {
                        MessageType.WHISPER -> "[Bisikan] ${msg.body}"
                        MessageType.FILE -> "[Berkas] ${msg.fileName ?: "Lampiran"}"
                        MessageType.VOICE -> "[Pesan Suara]"
                        MessageType.ACTION -> "* ${msg.senderNickname} ${msg.body}"
                        else -> msg.body
                    },
                    lastMessageTime = msg.timestamp,
                    isJoined = true,
                    isBookmarked = true,
                    myRole = OccupantRole.MEMBER,
                    myAffiliation = OccupantAffiliation.MEMBER,
                    colorHex = randomColorForRoom(msg.roomJid)
                )
                database.roomDao().insertOrUpdateRoom(newRoom)
            }

            // Post notification for incoming messages (MUC, Whisper, 1-on-1 Direct Chat)
            if (!msg.isMine &&
                msg.messageType != MessageType.SYSTEM_JOIN &&
                msg.messageType != MessageType.SYSTEM_LEAVE &&
                msg.messageType != MessageType.SYSTEM_STATUS &&
                msg.messageType != MessageType.SYSTEM_TOPIC &&
                (msg.body.isNotBlank() || !msg.fileName.isNullOrBlank())
            ) {
                // Discard stale history replay older than 30 seconds
                val isRecent = (System.currentTimeMillis() - msg.timestamp) < 30_000L
                if (isRecent) {
                    val roomConfig = database.roomConfigDao().getRoomConfigSync(msg.roomJid)
                    val globalEnabled = FcmPushManager.isPushEnabled.value
                    val notificationMode = roomConfig?.notificationMode ?: 0 // 0: All, 1: Mentions, 2: Muted

                    val isMention = msg.body.contains(currentAccount.value.nickname, ignoreCase = true) ||
                                  msg.body.contains(currentAccount.value.username, ignoreCase = true)

                    val shouldNotify = globalEnabled && when (notificationMode) {
                        0 -> true // All messages
                        1 -> isMention // Mentions only
                        2 -> false // Muted
                        else -> true
                    }

                    if (shouldNotify) {
                        val isDirect = msg.messageType == MessageType.CHAT ||
                                (!msg.roomJid.contains("conference.", ignoreCase = true) && !msg.roomJid.contains("muc.", ignoreCase = true))
                        val roomName = existing?.name ?: msg.roomJid.substringBefore("@")
                        val senderName = msg.senderNickname.ifBlank { msg.senderJid.substringBefore("@") }

                        val bodySnippet = when (msg.messageType) {
                            MessageType.WHISPER -> "[Bisikan] ${msg.body}"
                            MessageType.FILE -> "[Berkas] ${msg.fileName ?: "Lampiran"}"
                            MessageType.VOICE -> "[Pesan Suara]"
                            MessageType.ACTION -> "* $senderName ${msg.body}"
                            else -> msg.body
                        }

                        val payload = PushMessagePayload(
                            messageId = msg.id,
                            roomJid = msg.roomJid,
                            senderNickname = senderName,
                            body = bodySnippet,
                            isWhisper = msg.messageType == MessageType.WHISPER,
                            isDirect = isDirect,
                            roomTitle = roomName,
                            timestamp = msg.timestamp,
                            isMention = isMention
                        )
                        FcmPushManager.showSystemNotification(context, payload)
                    }
                }
            }
        } catch (e: Exception) {
            // Handled safely
        }
    }

    suspend fun joinRoom(roomJid: String, name: String, nickname: String, topic: String = "", password: String = ""): Result<Unit> {
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

        // Persist room configuration
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
        var smackResult: Result<Unit> = Result.success(Unit)
        if (smackConnectionManager.isConnected()) {
            val mucResult = smackMucManager.joinRoom(roomJid, nickname, password)
            if (mucResult.isFailure) {
                val exception = mucResult.exceptionOrNull()
                smackResult = Result.failure(exception!!)
                
                // If it's not a MUC service, disable auto-join to prevent infinite loop on reconnection
                if (exception.cause is org.jivesoftware.smackx.muc.MultiUserChatException.NotAMucServiceException) {
                    Log.i("XmppRepository", "Disabling auto-join for non-MUC JID: $roomJid")
                    updateRoomAutoJoin(roomJid, false)
                }
            }
        }

        engine.joinRoom(roomJid, nickname, password)
        return smackResult
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
        val joinedNames = mutableListOf<String>()

        for (cfg in autoJoinConfigs) {
            joinedJids.add(cfg.roomJid)
            val res = joinRoom(
                roomJid = cfg.roomJid,
                name = cfg.roomName,
                nickname = cfg.nickname.ifBlank { currentAccount.value.nickname.ifBlank { currentAccount.value.username.ifBlank { "User" } } },
                topic = cfg.subject,
                password = cfg.password.orEmpty()
            )
            if (res.isSuccess) {
                joinedNames.add(cfg.roomName.ifBlank { cfg.roomJid.substringBefore("@") })
            }
        }

        for (room in savedRooms) {
            if (!joinedJids.contains(room.jid) && (room.isAutoJoin || room.isJoined)) {
                val res = joinRoom(
                    roomJid = room.jid,
                    name = room.name,
                    nickname = room.myNickname.ifBlank { currentAccount.value.nickname.ifBlank { currentAccount.value.username.ifBlank { "User" } } },
                    topic = room.topic,
                    password = room.password.orEmpty()
                )
                if (res.isSuccess) {
                    joinedNames.add(room.name.ifBlank { room.jid.substringBefore("@") })
                }
            }
        }

        if (joinedNames.isNotEmpty()) {
            val namesStr = joinedNames.distinct().joinToString(", ")
            _autoJoinEvents.emit("Berhasil bergabung otomatis ke room favorit: $namesStr")
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

        // Sync with server (XEP-0048 Bookmarks)
        if (smackConnectionManager.isConnected()) {
            if (newBookmarkState) {
                val autoJoin = config?.isAutoJoin ?: room?.isAutoJoin ?: true
                val pass = config?.password ?: room?.password
                smackMucManager.saveBookmark(
                    roomJid = roomJid,
                    name = room?.name ?: roomJid.substringBefore("@"),
                    nickname = room?.myNickname ?: currentAccount.value.nickname,
                    password = pass,
                    autoJoin = autoJoin
                )
            } else {
                smackMucManager.removeBookmark(roomJid)
            }
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

        // Sync with server
        if (smackConnectionManager.isConnected()) {
            smackMucManager.saveBookmark(
                roomJid = cleanJid,
                name = name,
                nickname = nick,
                password = password,
                autoJoin = true
            )
        }
    }

    suspend fun removeSavedRoom(roomJid: String) {
        database.roomDao().updateBookmark(roomJid, false)
        database.roomConfigDao().updateBookmark(roomJid, false)
        
        if (smackConnectionManager.isConnected()) {
            smackMucManager.removeBookmark(roomJid)
        }
    }

    suspend fun updateRoomAutoJoin(roomJid: String, isAutoJoin: Boolean) {
        database.roomConfigDao().updateAutoJoin(roomJid, isAutoJoin)
        database.roomDao().updateAutoJoin(roomJid, isAutoJoin)

        // Sync autojoin flag to server bookmark (XEP-0048) if room is bookmarked
        if (smackConnectionManager.isConnected()) {
            val cfg = database.roomConfigDao().getRoomConfigSync(roomJid)
            val room = database.roomDao().getRoomByJid(roomJid)
            val isBookmarked = cfg?.isBookmarked ?: room?.isBookmarked ?: false
            if (isBookmarked) {
                smackMucManager.saveBookmark(
                    roomJid = roomJid,
                    name = cfg?.roomName ?: room?.name ?: roomJid.substringBefore("@"),
                    nickname = cfg?.nickname ?: room?.myNickname ?: currentAccount.value.nickname,
                    password = cfg?.password ?: room?.password,
                    autoJoin = isAutoJoin
                )
            }
        }
    }

    suspend fun updateRoomPassword(roomJid: String, password: String?) {
        database.roomConfigDao().updatePassword(roomJid, password)
        database.roomDao().updatePassword(roomJid, password)
    }

    suspend fun configureRoom(
        roomJid: String,
        roomName: String? = null,
        description: String? = null,
        subject: String? = null,
        isPersistent: Boolean = true,
        isPublic: Boolean = true,
        isMembersOnly: Boolean = false,
        isModerated: Boolean = false,
        allowSubjectChange: Boolean = true,
        password: String? = null,
        maxUsers: Int? = null,
        enableLogging: Boolean = true,
        allowInvites: Boolean = true,
        whois: String? = null
    ): Result<Unit> {
        val result = smackMucManager.configureRoom(
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
        if (result.isSuccess) {
            val existing = database.roomDao().getRoomByJid(roomJid)
            if (existing != null) {
                database.roomDao().insertOrUpdateRoom(
                    existing.copy(
                        name = roomName ?: existing.name,
                        topic = subject ?: description ?: existing.topic
                    )
                )
            }
        }
        return result
    }

    suspend fun getRoomInfo(roomJid: String): Result<org.jivesoftware.smackx.muc.RoomInfo> {
        return smackMucManager.getRoomInfo(roomJid)
    }

    suspend fun updateRoomNickname(roomJid: String, nickname: String): Result<Unit> {
        database.roomConfigDao().updateNickname(roomJid, nickname)
        database.roomDao().updateNickname(roomJid, nickname)
        
        return if (smackConnectionManager.isConnected() && smackMucManager.isRoomJoined(roomJid)) {
            smackMucManager.changeNickname(roomJid, nickname)
        } else {
            Result.success(Unit)
        }
    }

    suspend fun updateRoomThemeAndWallpaper(roomJid: String, bubbleTheme: String, wallpaperStyle: String) {
        database.roomConfigDao().updateThemeAndWallpaper(roomJid, bubbleTheme, wallpaperStyle)
        database.roomDao().updateThemeAndWallpaper(roomJid, bubbleTheme, wallpaperStyle)
    }

    suspend fun updateRoomColor(roomJid: String, colorHex: String) {
        database.roomConfigDao().updateColor(roomJid, colorHex)
        database.roomDao().updateColor(roomJid, colorHex)
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
        val cleanJid = roomJid.trim()
        val myJid = currentAccount.value.jid
        val myAvatar = smackAvatarManager.getAvatar(myJid)

        // 1. If Smack MUC is connected and room joined, send via Smack MUC
        if (smackConnectionManager.isConnected() && smackMucManager.isRoomJoined(cleanJid)) {
            try {
                smackMucManager.sendMessage(cleanJid, text)
                return
            } catch (e: Exception) {
                // Handled gracefully, fall back
            }
        } else if (smackConnectionManager.isConnected() && smackConnectionManager.isAuthenticated()) {
            // 2. Direct 1-on-1 XMPP Chat Stanza (RFC 6121)
            try {
                val conn = smackConnectionManager.getRawConnection()
                if (conn != null) {
                    val toJid = org.jxmpp.jid.impl.JidCreate.from(cleanJid)
                    val msgStanza = org.jivesoftware.smack.packet.Message(toJid, org.jivesoftware.smack.packet.Message.Type.chat).apply {
                        body = text
                        stanzaId = java.util.UUID.randomUUID().toString()
                    }
                    conn.sendStanza(msgStanza)
                    Log.i("XmppRepository", "Successfully sent 1-on-1 direct XMPP stanza to $cleanJid")
                }
            } catch (e: Exception) {
                Log.w("XmppRepository", "Failed to send 1-on-1 direct stanza to $cleanJid: ${e.message}")
            }
        }

        // Save sent message locally in Room DB
        val msg = engine.sendGroupMessage(cleanJid, text, myNickname).copy(
            senderJid = myJid,
            senderAvatar = myAvatar,
            messageType = MessageType.CHAT,
            replyToMessageId = replyToMessageId,
            replyToSender = replyToSender,
            replyToText = replyToText
        )
        database.messageDao().insertMessage(msg.toEntity())

        val existing = database.roomDao().getRoomByJid(cleanJid)
        if (existing != null) {
            database.roomDao().insertOrUpdateRoom(
                existing.copy(
                    lastMessageText = text,
                    lastMessageTime = msg.timestamp
                )
            )
        } else {
            // Auto-create room entity for 1-on-1 chat
            val newRoom = RoomEntity(
                jid = cleanJid,
                name = cleanJid.substringBefore("@"),
                topic = "Obrolan Langsung (1-on-1)",
                myNickname = myNickname,
                occupantCount = 2,
                unreadCount = 0,
                lastMessageText = text,
                lastMessageTime = msg.timestamp,
                isJoined = true,
                isBookmarked = true,
                myRole = OccupantRole.MEMBER,
                myAffiliation = OccupantAffiliation.MEMBER,
                colorHex = randomColorForRoom(cleanJid)
            )
            database.roomDao().insertOrUpdateRoom(newRoom)
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

        val existing = database.roomDao().getRoomByJid(roomJid)
        if (existing != null) {
            database.roomDao().insertOrUpdateRoom(
                existing.copy(
                    lastMessageText = "[Bisikan ke @$targetNickname] $text",
                    lastMessageTime = msg.timestamp
                )
            )
        }
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

    suspend fun sendVoiceMessage(
        roomJid: String,
        audioFile: java.io.File,
        durationSeconds: Int,
        myNickname: String
    ) {
        val fileName = audioFile.name
        val fileSize = audioFile.length()
        val mimeType = "audio/mp4"

        // 1. Try uploading via XEP-0363 if connected
        var finalUrl = "file://${audioFile.absolutePath}"
        if (smackConnectionManager.isConnected()) {
            val connection = smackConnectionManager.getRawConnection()
            if (connection != null) {
                val uploadResult = smackFileUploadManager.uploadFile(
                    connection,
                    android.net.Uri.fromFile(audioFile),
                    fileName,
                    fileSize,
                    mimeType
                )
                uploadResult.onSuccess { uploadedUrl ->
                    finalUrl = uploadedUrl
                }.onFailure {
                    android.util.Log.w("XmppRepository", "XEP-0363 voice upload fallback to local URI: ${it.message}")
                }
            }
        }

        // 2. Build XmppMessage
        val msgId = "voice_${System.currentTimeMillis()}"
        val body = "🎤 Pesan Suara (${durationSeconds}d)\n$finalUrl"
        val myJid = smackConnectionManager.getRawConnection()?.user?.asBareJid()?.toString() ?: "$roomJid/$myNickname"

        val msg = XmppMessage(
            id = msgId,
            roomJid = roomJid,
            senderJid = myJid,
            senderNickname = myNickname,
            body = body,
            timestamp = System.currentTimeMillis(),
            isMine = true,
            messageType = MessageType.VOICE,
            status = DeliveryStatus.SENT,
            fileUrl = finalUrl,
            fileName = fileName,
            fileMimeType = mimeType,
            fileSize = fileSize,
            voiceDurationSeconds = durationSeconds
        )

        // Save to local database
        database.messageDao().insertMessage(msg.toEntity())

        // 3. Send via Smack MUC if room joined
        if (smackMucManager.isRoomJoined(roomJid)) {
            try {
                smackMucManager.sendMessage(roomJid, body)
            } catch (e: Exception) {
                android.util.Log.e("XmppRepository", "Gagal mengirim voice message ke MUC: ${e.message}")
            }
        }
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
        // 1. If a specific query is provided, or if we want global discovery, use search.jabber.network REST API
        if (query.isNotBlank() || serviceJid == null) {
            val searchResult = jabberSearchManager.searchRooms(query).getOrNull()
            if (!searchResult.isNullOrEmpty()) {
                return searchResult
            }
        }

        // 2. Fallback to traditional XEP-0030 Service Discovery on the server
        val result = smackDiscoManager.discoverRooms(serviceJid)
        return result.getOrElse { emptyList() }
    }

    suspend fun discoverServerRooms(serviceJid: String? = null): List<DiscoveredRoom> {
        val result = smackDiscoManager.discoverRooms(serviceJid)
        return result.getOrElse { emptyList() }
    }

    suspend fun loadRosterContacts(): List<RosterContact> {
        return smackRosterManager.loadContacts(forceReload = true).getOrElse { smackRosterManager.contacts.value }
    }

    suspend fun addRosterContact(jid: String, name: String, group: String? = null): Result<Unit> {
        return smackRosterManager.addContact(jid, name, group)
    }

    suspend fun removeRosterContact(jid: String): Result<Unit> {
        val result = smackRosterManager.removeContact(jid)
        try {
            database.contactDao().deleteContact(jid.trim())
        } catch (e: Exception) {
            Log.w("XmppRepository", "Error deleting contact from database: ${e.message}")
        }
        return result
    }

    suspend fun renameRosterContact(jid: String, newName: String): Result<Unit> {
        return smackRosterManager.renameContact(jid, newName)
    }

    suspend fun addRosterContactToGroup(jid: String, group: String): Result<Unit> {
        return smackRosterManager.addContactToGroup(jid, group)
    }

    suspend fun acceptSubscriptionRequest(jid: String): Result<Unit> {
        return smackRosterManager.acceptSubscriptionRequest(jid)
    }

    suspend fun rejectSubscriptionRequest(jid: String): Result<Unit> {
        return smackRosterManager.rejectSubscriptionRequest(jid)
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

    suspend fun grantNone(roomJid: String, jid: String) = smackMucManager.grantNone(roomJid, jid)
    suspend fun grantModerator(roomJid: String, nickname: String) = smackMucManager.grantModerator(roomJid, nickname)
    suspend fun revokeModerator(roomJid: String, nickname: String) = smackMucManager.revokeModerator(roomJid, nickname)
    suspend fun grantMembership(roomJid: String, jid: String) = smackMucManager.grantMembership(roomJid, jid)
    suspend fun revokeMembership(roomJid: String, jid: String) = smackMucManager.revokeMembership(roomJid, jid)
    suspend fun grantAdmin(roomJid: String, jid: String) = smackMucManager.grantAdmin(roomJid, jid)
    suspend fun revokeAdmin(roomJid: String, jid: String) = smackMucManager.revokeAdmin(roomJid, jid)
    suspend fun grantOwnership(roomJid: String, jid: String) = smackMucManager.grantOwnership(roomJid, jid)
    suspend fun revokeOwnership(roomJid: String, jid: String) = smackMucManager.revokeOwnership(roomJid, jid)
    suspend fun grantVoice(roomJid: String, nickname: String) = smackMucManager.grantVoice(roomJid, nickname)
    suspend fun revokeVoice(roomJid: String, nickname: String) = smackMucManager.revokeVoice(roomJid, nickname)
    suspend fun banUser(roomJid: String, jid: String, reason: String?) = smackMucManager.banUser(roomJid, jid, reason)
    suspend fun unbanUser(roomJid: String, jid: String) = smackMucManager.unbanUser(roomJid, jid)
    suspend fun kickParticipant(roomJid: String, nickname: String, reason: String?) = smackMucManager.kickParticipant(roomJid, nickname, reason)

    suspend fun getOwners(roomJid: String) = smackMucManager.getOwners(roomJid)
    suspend fun getAdmins(roomJid: String) = smackMucManager.getAdmins(roomJid)
    suspend fun getMembers(roomJid: String) = smackMucManager.getMembers(roomJid)
    suspend fun getOutcasts(roomJid: String) = smackMucManager.getOutcasts(roomJid)

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
    isAutoJoin = isAutoJoin,
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
    replyToText = replyToText,
    voiceDurationSeconds = voiceDurationSeconds
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
    replyToText = replyToText,
    voiceDurationSeconds = voiceDurationSeconds
)
