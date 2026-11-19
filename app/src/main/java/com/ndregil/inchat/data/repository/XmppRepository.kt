package com.ndregil.inchat.data.repository

import androidx.room.withTransaction
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
import com.ndregil.inchat.data.model.ServerDiscoResult
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
import com.ndregil.inchat.ui.i18n.getAppStrings
import com.ndregil.inchat.data.network.RosterManager
import kotlinx.coroutines.flow.flowOn
import com.ndregil.inchat.data.network.ServiceDiscoveryManager
import com.ndregil.inchat.data.network.XmppConnectionManager
import com.ndregil.inchat.data.network.XmppConnectionService
import com.ndregil.inchat.data.network.XmppProtocolEngine
import com.ndregil.inchat.data.network.fcm.FcmPushManager
import com.ndregil.inchat.data.network.fcm.PushMessagePayload
import com.ndregil.inchat.data.network.OmemoSupportResult
import com.ndregil.inchat.data.network.OmemoUnsupportedContactException
import org.jxmpp.jid.impl.JidCreate
import org.jivesoftware.smackx.delay.DelayInformationManager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

sealed class SendMessageOutcome {
    object Sent : SendMessageOutcome()
    data class OmemoUnsupported(val reason: String, val failedMessageId: String) : SendMessageOutcome()
    data class Failed(val reason: String, val failedMessageId: String?) : SendMessageOutcome()
}

class XmppRepository(
    private val context: android.content.Context,
    private val database: AppDatabase,
    private val scope: CoroutineScope
) {
    private val connectionMutex = Mutex()
    private val incomingMessageMutex = Mutex()
    private val autoJoinMutex = Mutex()
    private val listenerAttachLock = Any()
    private val translationInFlight = ConcurrentHashMap.newKeySet<String>()
    private val translationAttempted = ConcurrentHashMap<String, String>()
    private val autoTranslateSemaphore = Semaphore(4)

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

    init {
        try {
            org.jivesoftware.smack.android.AndroidSmackInitializer.initialize(context)
        } catch (e: Exception) {
            Log.w("XmppRepository", "AndroidSmackInitializer note: ${e.message}")
        }
        try {
            org.jivesoftware.smack.util.dns.minidns.MiniDnsResolver.setup()
        } catch (e: Exception) {
            Log.w("XmppRepository", "MiniDnsResolver setup note: ${e.message}")
        }
    }

    val engine = XmppProtocolEngine(scope)
    val smackConnectionManager = XmppConnectionManager()
    val smackAvatarManager = XmppAvatarManager(smackConnectionManager, database.avatarDao(), scope)
    val smackMucManager = MultiUserChatManager(smackConnectionManager, smackAvatarManager, scope)
    val smackDiscoManager = ServiceDiscoveryManager(smackConnectionManager)
    val smackRosterManager = RosterManager(smackConnectionManager, smackAvatarManager, scope)
    val smackFileUploadManager = com.ndregil.inchat.data.network.FileTransferManager(context)
    val smackCallManager = JingleCallManager(smackConnectionManager, scope)
    val jabberSearchManager = com.ndregil.inchat.data.network.JabberNetworkSearchManager()
    val backupManager = com.ndregil.inchat.data.local.backup.ChatBackupManager(context, database)
    val themePreferencesManager = com.ndregil.inchat.data.local.ThemePreferencesManager(context)
    val translationService = com.ndregil.inchat.data.network.TranslationService()
    val translationPreferencesManager = com.ndregil.inchat.data.local.TranslationPreferencesManager(context)
    val omemoEncryptionManager = com.ndregil.inchat.data.network.OmemoEncryptionManager(context)

    private val mamSyncJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private var currentAppLanguage = "system"

    val themePreferences: Flow<com.ndregil.inchat.data.model.ThemePreferences> = themePreferencesManager.themePreferences
    suspend fun updateThemePreferences(prefs: com.ndregil.inchat.data.model.ThemePreferences) {
        themePreferencesManager.updateThemePreferences(prefs)
    }

    val translationPreferences: Flow<com.ndregil.inchat.data.model.TranslationPreferences> = translationPreferencesManager.translationPreferences
    suspend fun updateTranslationPreferences(prefs: com.ndregil.inchat.data.model.TranslationPreferences) {
        translationPreferencesManager.updatePreferences(prefs)
    }

    suspend fun translateMessage(messageId: String, targetLanguage: String = "id"): String? {
        val message = database.messageDao().getMessageByIdSync(messageId) ?: return null
        if (message.body.isBlank()) return null
        val translated = translationService.translateText(
            text = message.body,
            targetLangCode = targetLanguage,
            skipHeuristics = true
        )
        if (!translated.isNullOrBlank()) {
            database.messageDao().updateMessageTranslation(messageId, translated, targetLanguage)
            translationAttempted[messageId] = targetLanguage
        }
        return translated
    }

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
    val serverDiscoResult: StateFlow<ServerDiscoResult> = smackDiscoManager.serverDiscoResult
    val isDiscoServerLoading: StateFlow<Boolean> = smackDiscoManager.isDiscoServerLoading
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

    private val _isUploadingFile = MutableStateFlow(false)
    val isUploadingFile: StateFlow<Boolean> = _isUploadingFile.asStateFlow()

    val roomDao = database.roomDao()
    val messageDao = database.messageDao()
    val chatMessageDao = database.chatMessageDao()
    val roomConfigDao = database.roomConfigDao()

    val rooms: Flow<List<XmppRoom>> = database.roomDao().getAllRooms().map { entities ->
        entities.map { it.toModel() }
    }

    val roomConfigs: Flow<List<RoomConfigEntity>> = database.roomConfigDao().getAllRoomConfigs()

    val connectionService = XmppConnectionService(context, this, scope)

    init {
        connectionService.start()
        smackConnectionManager.onBeforeConnectPrepared = { conn ->
            omemoEncryptionManager.prepareForConnection(conn)
        }
        smackConnectionManager.onReconnectedAuthenticated = { conn ->
            scope.launch(Dispatchers.IO) {
                try {
                    smackAvatarManager.onConnectionReady(conn)
                    setupReceiptListener(conn)
                    setupIncomingStanzaListener()
                    kotlinx.coroutines.withTimeoutOrNull(12_000L) {
                        val acctJid = currentAccount.value.jid.ifBlank { conn.user?.asBareJid()?.toString().orEmpty() }
                        omemoEncryptionManager.onConnectionReady(conn, acctJid)
                    }
                    smackConnectionManager.updatePresence(_userPresence.value, _userStatusMessage.value)
                } catch (e: Exception) {
                    Log.w("XmppRepository", "Reconnect OMEMO/presence setup warning: ${e.message}")
                }
            }
        }
        omemoEncryptionManager.onDecryptedMessageReceived = { xmppMsg, _ ->
            scope.launch(Dispatchers.IO) {
                val bareRoom = omemoEncryptionManager.toBareJid(xmppMsg.roomJid)
                val contactMatch = rosterContacts.value.find {
                    it.jid.substringBefore("/").equals(bareRoom, ignoreCase = true) ||
                        it.jid.substringBefore("@").equals(bareRoom.substringBefore("@"), ignoreCase = true)
                }
                val resolvedNick = if (xmppMsg.isMine) {
                    currentAccount.value.nickname.ifBlank { currentAccount.value.username }
                } else {
                    contactMatch?.name?.takeIf { it.isNotBlank() }
                        ?: contactMatch?.nickname?.takeIf { it.isNotBlank() }
                        ?: xmppMsg.senderNickname.ifBlank { bareRoom.substringBefore("@") }
                }
                val resolvedCaps = if (xmppMsg.isMine) {
                    xmppMsg.senderClientCaps
                } else {
                    contactMatch?.clientCaps ?: xmppMsg.senderClientCaps
                }
                processIncomingMessage(
                    xmppMsg.copy(
                        senderNickname = resolvedNick,
                        senderClientCaps = resolvedCaps
                    )
                )
            }
        }
        // Collect theme preferences to sync current language
        scope.launch {
            try {
                themePreferencesManager.themePreferences.collect { prefs ->
                    currentAppLanguage = prefs.appLanguage
                    smackMucManager.activeLanguageCode = prefs.appLanguage
                }
            } catch (e: Exception) {
                // Handled safely
            }
        }
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

        // Collect MUC occupant changes to update user's role/affiliation and occupantCount
        scope.launch {
            try {
                smackMucManager.roomOccupants.collect { occupantsMap ->
                    occupantsMap.forEach { (roomJid, occupants) ->
                        if (occupants.isNotEmpty()) {
                            database.roomDao().updateOccupantCount(roomJid, occupants.size)
                        }
                        val me = occupants.find { it.isMe }
                        if (me != null) {
                            database.roomDao().updateOccupantRoleAndAffiliation(roomJid, me.role, me.affiliation)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("XmppRepository", "Error syncing occupant role/affiliation: ${e.message}")
            }
        }

        // Collect connection status to register direct 1-on-1 chat & whisper stanza listener
        scope.launch {
            try {
                smackConnectionManager.connectionStatus.collect { status ->
                    if (status == ConnectionStatus.CONNECTED) {
                        Log.i("XmppRepository", "Connection established, setting up listeners and rejoining rooms.")
                        setupIncomingStanzaListener()
                        
                        // REJOIN logic: Join rooms that were previously joined or are marked for auto-join
                        scope.launch {
                            kotlinx.coroutines.delay(2000) // Give the connection a moment to stabilize
                            autoJoinConfiguredRooms()
                            refreshAllRoomOccupantCounts()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("XmppRepository", "Error in connection status observer: ${e.message}")
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
                    if (currentAccount.value.isDemoMode) return@collect
                    if (contactsList.isNotEmpty()) {
                        val entities = contactsList.map { ContactEntity.fromModel(it) }
                        database.contactDao().insertOrUpdateContacts(entities)
                        if (smackRosterManager.isLoaded.value && smackConnectionManager.isAuthenticated()) {
                            database.contactDao().deleteContactsNotIn(contactsList.map { it.jid })
                        }
                    } else if (smackRosterManager.isLoaded.value && smackConnectionManager.isAuthenticated()) {
                        database.contactDao().clearAllContacts()
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

    fun getMessagesForRoom(roomJid: String, maxCount: Int = 30): Flow<List<XmppMessage>> {
        if (roomJid.isBlank()) return flowOf(emptyList())
        val roomFlow = try {
            database.messageDao().getRecentMessagesForRoom(roomJid, maxCount)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e("XmppRepository", "Error accessing messageDao for $roomJid", e)
            flowOf(emptyList())
        }
        return combine(
            roomFlow,
            translationPreferencesManager.translationPreferences.distinctUntilChanged()
        ) { entities, prefs ->
            val list = entities ?: emptyList()
            if (list.isNotEmpty()) {
                val isGroup = isMucJid(roomJid)
                val shouldTranslate = prefs.isAutoTranslateEnabled &&
                        if (isGroup) prefs.translateRooms else prefs.translateContacts

                if (shouldTranslate) {
                    val targetLang = prefs.targetLanguageCode
                    val untranslated = list.filter { entity ->
                        val translateThis = if (entity.isMine) prefs.translateOutgoing else true
                        val needsTranslationForTarget = entity.translatedBody.isNullOrBlank() ||
                            !entity.translationLanguage.equals(targetLang, ignoreCase = true)
                        val alreadyAttemptedForTarget =
                            translationAttempted[entity.id]?.equals(targetLang, ignoreCase = true) == true
                        translateThis &&
                            needsTranslationForTarget &&
                            !alreadyAttemptedForTarget &&
                            entity.body.isNotBlank() &&
                            entity.messageType != MessageType.FILE &&
                            entity.messageType != MessageType.VOICE &&
                            entity.messageType != MessageType.CALL_SIGNALLING &&
                            entity.messageType != MessageType.SYSTEM_JOIN &&
                            entity.messageType != MessageType.SYSTEM_LEAVE
                    }

                    for (entity in untranslated) {
                        val flightKey = "${entity.id}:$targetLang"
                        if (!translationInFlight.add(flightKey)) continue
                        scope.launch(Dispatchers.IO) {
                            try {
                                autoTranslateSemaphore.withPermit {
                                    val translated = translationService.translateText(entity.body, targetLang)
                                    translationAttempted[entity.id] = targetLang
                                    if (!translated.isNullOrBlank()) {
                                        database.messageDao().updateMessageTranslation(entity.id, translated, targetLang)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w("XmppRepository", "Room auto-translate item error: ${e.message}")
                            } finally {
                                translationInFlight.remove(flightKey)
                            }
                        }
                    }
                }
            }

            val myJid = currentAccount.value.jid
            list.map { entity ->
                val model = entity.toModel()
                val avatarJid = if (model.isMine && myJid.isNotBlank()) myJid else model.senderJid
                model.copy(avatarKey = avatarJid)
            }
        }.flowOn(Dispatchers.IO)
    }

    suspend fun markRoomAsRead(roomJid: String) {
        database.roomDao().markRoomAsRead(roomJid)
        database.messageDao().updateMineMessagesStatusForRoom(roomJid, DeliveryStatus.READ)
    }

    suspend fun connect(account: XmppAccount, password: String = "") = connectionMutex.withLock {
        // If there is an active authentication error and no NEW password was provided in this call, abort to prevent infinite loop
        val lastErr = smackConnectionManager.lastError.value
        if (lastErr != null && lastErr.contains("Autentikasi gagal", ignoreCase = true) && password.isBlank()) {
            Log.w("XmppRepository", "Aborting connection attempt: authentication previously failed and no new password provided.")
            return@withLock
        }

        val cleanDomain = com.ndregil.inchat.data.network.XmppConnectionManager.sanitizeDomain(account.domain)
        val cleanHost = com.ndregil.inchat.data.network.XmppConnectionManager.sanitizeHost(account.host, cleanDomain)
        val cleanJid = com.ndregil.inchat.data.network.XmppConnectionManager.sanitizeJid(account.jid)
        val sanitizedAccount = account.copy(
            jid = cleanJid,
            domain = cleanDomain,
            host = cleanHost
        )

        val existing = database.accountDao().getActiveAccountSync()
        val isDifferentAccount = existing != null && existing.jid.isNotBlank() &&
                !existing.jid.equals(sanitizedAccount.jid, ignoreCase = true)

        if (isDifferentAccount) {
            Log.i("XmppRepository", "Ganti akun terdeteksi dari '${existing.jid}' ke '${sanitizedAccount.jid}'. Membersihkan sesi lama, room, favorit, dan kontak.")
            try {
                connectionService.release()
                smackRosterManager.release()
                smackMucManager.leaveAllRooms()
                smackConnectionManager.disconnect()
            } catch (e: Exception) {
                Log.w("XmppRepository", "Pembersihan sesi lama gagal: ${e.message}")
            }
            try { engine.disconnect() } catch (_: Exception) {}

            // Bersihkan data lokal yang terkait dengan akun lama (room, favorit, config, kontak, pesan)
            database.roomDao().clearAllRooms()
            database.roomConfigDao().clearAllConfigs()
            database.contactDao().clearAllContacts()
            database.messageDao().clearAllMessages()
            database.storyDao().clearAllStories()
            database.avatarDao().clearAllAvatars()
            smackAvatarManager.clearCache(clearDatabase = false)
            database.accountDao().clearAccounts()
            smackMucManager.clearServerBookmarks()
        }

        val pwdToSave = if (password.isNotBlank()) password else (if (!isDifferentAccount) (existing?.password ?: "") else "")
        database.accountDao().saveAccount(sanitizedAccount.toEntity(pwdToSave))
        _currentAccount.value = sanitizedAccount

        // Check if Smack is already connected to this account
        if (smackConnectionManager.isConnected() && smackConnectionManager.isAuthenticated()) {
            val creds = smackConnectionManager.currentCredentials
            if (creds.username.equals(sanitizedAccount.username, ignoreCase = true) &&
                creds.domain.equals(sanitizedAccount.domain, ignoreCase = true)) {
                // Already connected! Do not disconnect.
                return@withLock
            }
        }
        
        // 1. Connect via Smack (Real XMPP)
        val smackResult = smackConnectionManager.connectAndLogin(
            com.ndregil.inchat.data.network.XmppCredentials.fromAccount(sanitizedAccount, pwdToSave)
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
            smackRosterManager.loadDemoContactsIfEmpty()
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
                try {
                    kotlinx.coroutines.withTimeoutOrNull(12_000L) {
                        omemoEncryptionManager.onConnectionReady(conn, sanitizedAccount.jid)
                    }
                } catch (e: Exception) {
                    Log.w("XmppRepository", "OMEMO init warning before initial presence: ${e.message}")
                }
                try {
                    smackConnectionManager.updatePresence(_userPresence.value, _userStatusMessage.value)
                } catch (e: Exception) {
                    Log.w("XmppRepository", "Initial presence warning: ${e.message}")
                }
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
                // 1. Join local rooms immediately for best responsiveness
                launch {
                    // Give session a moment to stabilize
                    kotlinx.coroutines.delay(1000) 
                    autoJoinConfiguredRooms()
                }
                
                // 2. Sync bookmarks from server in parallel (may discover new rooms)
                launch {
                    try {
                        // Increase delay slightly to allow disco and other startup stanzas to clear
                        kotlinx.coroutines.delay(2500) 
                        val newRoomsFound = syncServerBookmarks()
                        if (newRoomsFound) {
                            Log.i("XmppRepository", "New auto-join rooms discovered from server, triggering second join pass...")
                            autoJoinConfiguredRooms()
                        }
                    } catch (e: Exception) {
                        Log.e("XmppRepository", "Error syncing bookmarks: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Synchronizes server-side bookmarks (XEP-0048) with the local database.
     * Returns true if any rooms were newly added or updated to auto-join.
     */
    suspend fun syncServerBookmarks(): Boolean {
        if (!smackConnectionManager.isConnected()) {
            Log.w("XmppRepository", "Cannot sync bookmarks: Not connected")
            return false
        }
        
        var addedNew = false
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
                
                // Check if this is a new room or auto-join changed
                val existing = database.roomDao().getRoomByJid(roomJid)
                if (existing == null || (!existing.isAutoJoin && autoJoin)) {
                    addedNew = true
                }

                // Save to local database if not exists or update
                saveRoomLocally(
                    roomJid = roomJid,
                    roomName = name,
                    nickname = nick,
                    isBookmarked = true,
                    isAutoJoin = autoJoin,
                    password = bm.password
                )
                smackAvatarManager.getAvatar(roomJid)
            }
        }
        return addedNew
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
                isAutoJoin = isAutoJoin,
                isGroupChat = isMucJid(roomJid)
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
            password = password,
            isGroupChat = isMucJid(roomJid)
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
        captchaAnswer: String,
        extraFieldValues: Map<String, String> = emptyMap()
    ): Result<String> {
        return smackConnectionManager.submitCaptchaAnswer(challenge, captchaAnswer, extraFieldValues)
    }

    suspend fun disconnect() {
        omemoEncryptionManager.cleanup()
        try {
            connectionService.release()
            smackRosterManager.release()
            smackMucManager.leaveAllRooms()
        } catch (_: Exception) {}
        try {
            smackConnectionManager.disconnect()
        } catch (e: Exception) {
            // Handled gracefully
        }
        try { engine.disconnect() } catch (_: Exception) {}
        _currentAccount.value = XmppAccount()
    }

    suspend fun signOut() {
        omemoEncryptionManager.cleanup()
        try {
            connectionService.release()
            smackRosterManager.release()
            smackMucManager.leaveAllRooms()
        } catch (_: Exception) {}
        try {
            smackConnectionManager.disconnect()
        } catch (_: Exception) {}
        try {
            engine.disconnect()
        } catch (_: Exception) {}
        database.accountDao().clearAccounts()
        database.roomDao().clearAllRooms()
        database.messageDao().clearAllMessages()
        database.roomConfigDao().clearAllConfigs()
        database.contactDao().clearAllContacts()
        database.storyDao().clearAllStories()
        database.avatarDao().clearAllAvatars()
        smackAvatarManager.clearCache(clearDatabase = false)
        smackMucManager.clearServerBookmarks()
        _currentAccount.value = XmppAccount()
    }

    suspend fun prepareSwitchAccount() {
        omemoEncryptionManager.cleanup()
        Log.i("XmppRepository", "Mempersiapkan ganti akun: memutuskan sesi aktif dan membersihkan sesi lama.")
        try {
            connectionService.release()
            smackRosterManager.release()
            smackMucManager.leaveAllRooms()
        } catch (_: Exception) {}
        try {
            smackConnectionManager.disconnect()
        } catch (_: Exception) {}
        try {
            engine.disconnect()
        } catch (_: Exception) {}
        database.accountDao().clearAccounts()
        database.roomDao().clearAllRooms()
        database.messageDao().clearAllMessages()
        database.roomConfigDao().clearAllConfigs()
        database.contactDao().clearAllContacts()
        database.storyDao().clearAllStories()
        database.avatarDao().clearAllAvatars()
        smackAvatarManager.clearCache(clearDatabase = false)
        smackMucManager.clearServerBookmarks()
        _currentAccount.value = XmppAccount()
    }

    suspend fun exitApp() {
        try {
            connectionService.release()
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
    private var lastAttachedReceiptConnection: org.jivesoftware.smack.XMPPConnection? = null
    private val processedStanzaIds = java.util.Collections.synchronizedSet(java.util.HashSet<String>())

    private fun setupIncomingStanzaListener() {
        val conn = smackConnectionManager.getRawConnection() ?: return
        synchronized(listenerAttachLock) {
            if (!conn.isAuthenticated || lastAttachedConnection == conn) return
            lastAttachedConnection = conn
        }

        val filter = org.jivesoftware.smack.filter.StanzaTypeFilter(org.jivesoftware.smack.packet.Message::class.java)
        conn.addAsyncStanzaListener({ stanza ->
            val message = stanza as? org.jivesoftware.smack.packet.Message ?: return@addAsyncStanzaListener

            // Handle XEP-0184 Delivery Receipts (<received xmlns='urn:xmpp:receipts' id='...'/>)
            val receiptExt = message.getExtension("received", "urn:xmpp:receipts") as? org.jivesoftware.smackx.receipts.DeliveryReceipt
            val displayedExt = message.getExtension("displayed", "urn:xmpp:chat-markers:0") as? org.jivesoftware.smack.packet.StandardExtensionElement

            val cachedXml: String? by lazy(LazyThreadSafetyMode.NONE) {
                message.toXML().toString()
            }

            val receiptId = receiptExt?.id
                ?: if (message.hasExtension("received", "urn:xmpp:receipts")) {
                    val xml = cachedXml ?: ""
                    Regex("""<received[^>]*xmlns=['"]urn:xmpp:receipts['"][^>]*id=['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE).find(xml)?.groupValues?.get(1)
                        ?: Regex("""<received[^>]*id=['"]([^'"]+)['"][^>]*xmlns=['"]urn:xmpp:receipts['"]""", RegexOption.IGNORE_CASE).find(xml)?.groupValues?.get(1)
                } else null

            if (!receiptId.isNullOrBlank()) {
                scope.launch(Dispatchers.IO) {
                    try {
                        database.messageDao().updateMessageStatus(receiptId, DeliveryStatus.DELIVERED)
                        Log.d("XmppRepository", "Received delivery receipt for message $receiptId -> DELIVERED")
                    } catch (e: Exception) {
                        Log.e("XmppRepository", "Failed to update receipt status: ${e.message}")
                    }
                }
            }

            // Handle XEP-0333 Chat Markers (<displayed xmlns='urn:xmpp:chat-markers:0' id='...'/>)
            val displayedId = displayedExt?.getAttributeValue("id")
                ?: if (message.hasExtension("displayed", "urn:xmpp:chat-markers:0")) {
                    Regex("""<displayed[^>]*id=['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE).find(cachedXml ?: "")?.groupValues?.get(1)
                } else null
            if (!displayedId.isNullOrBlank()) {
                scope.launch(Dispatchers.IO) {
                    try {
                        database.messageDao().updateMessageStatus(displayedId, DeliveryStatus.READ)
                        Log.d("XmppRepository", "Received read chat marker for message $displayedId -> READ")
                    } catch (e: Exception) {
                        Log.e("XmppRepository", "Failed to update chat marker status: ${e.message}")
                    }
                }
            }

            val omemoLegacyExt = message.getExtension<org.jivesoftware.smack.packet.ExtensionElement>(
                "encrypted",
                "eu.siacs.conversations.axolotl"
            )
            val hasOmemoLegacy = omemoLegacyExt != null
            val hasOmemo2 = message.hasExtension("encrypted", "urn:xmpp:omemo:2")
            val hasOmemo = hasOmemoLegacy || hasOmemo2
            val isOmemoKeyTransport = (omemoLegacyExt as? org.jivesoftware.smackx.omemo.element.OmemoElement)?.isKeyTransportElement == true
            val hasOpenPgp = message.hasExtension("openpgp", "urn:xmpp:openpgp:0")
            val preParsedOobUrl = if (message.hasExtension("x", "jabber:x:oob")) {
                val xml = cachedXml ?: ""
                Regex("""<x[^>]*xmlns=['"]jabber:x:oob['"][^>]*>\s*<url>([^<]+)</url>""", RegexOption.IGNORE_CASE)
                    .find(xml)?.groupValues?.get(1)?.trim()
            } else null

            // Jangan buat gelembung palsu untuk pesan key-transport / ratchet-repair OMEMO tanpa isi
            if (hasOmemo && isOmemoKeyTransport && message.body.isNullOrBlank()) {
                return@addAsyncStanzaListener
            }

            var bodyText = message.body
            if (bodyText.isNullOrBlank()) {
                if (!preParsedOobUrl.isNullOrBlank()) {
                    bodyText = preParsedOobUrl
                } else if (hasOmemo) {
                    bodyText = "🔒 [Pesan Terenkripsi OMEMO]"
                } else if (hasOpenPgp) {
                    bodyText = "🔒 [Pesan Terenkripsi OpenPGP]"
                } else {
                    return@addAsyncStanzaListener
                }
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

            val stanzaId = message.stanzaId

            // Untuk pesan 1-on-1 OMEMO, biarkan OmemoManager mendekripsi terlebih dahulu via OmemoMessageListener.
            // Jangan langsung menyimpan fallback body / placeholder dengan stanzaId yang sama, karena akan
            // membuat processIncomingMessage menduplikasi & membuang hasil dekripsi asli!
            if (hasOmemoLegacy && !fromBareJid.contains("conference.", ignoreCase = true) && !fromBareJid.contains("muc.", ignoreCase = true)) {
                if (!isOmemoKeyTransport && !stanzaId.isNullOrBlank() && !fromBareJid.equals(myBareJid, ignoreCase = true)) {
                    scope.launch(Dispatchers.IO) {
                        kotlinx.coroutines.delay(3500L)
                        if (database.messageDao().getMessageByIdSync(stanzaId) == null) {
                            val cleanBare = fromBareJid.substringBefore("/").trim()
                            val contactMatch = rosterContacts.value.find {
                                it.jid.equals(cleanBare, ignoreCase = true) ||
                                    it.jid.substringBefore("@").equals(cleanBare.substringBefore("@"), ignoreCase = true)
                            }
                            val fallbackNick = contactMatch?.name?.takeIf { it.isNotBlank() }
                                ?: contactMatch?.nickname?.takeIf { it.isNotBlank() }
                                ?: cleanBare.substringBefore("@")
                            val fallbackMsg = XmppMessage(
                                id = stanzaId,
                                roomJid = fromBareJid,
                                senderNickname = fallbackNick,
                                senderJid = fromStr,
                                avatarKey = fromStr,
                                senderClientCaps = contactMatch?.clientCaps
                                    ?: com.ndregil.inchat.data.model.ClientCaps.resolveFor(fallbackNick, fromStr),
                                body = "🔒 [Pesan Terenkripsi OMEMO - tidak dapat didekripsi]",
                                timestamp = org.jivesoftware.smackx.delay.DelayInformationManager.getDelayInformation(message)?.stamp?.time
                                    ?: System.currentTimeMillis(),
                                isMine = false,
                                messageType = MessageType.CHAT,
                                status = DeliveryStatus.DELIVERED,
                                isEncrypted = false
                            )
                            Log.w("XmppRepository", "OMEMO decryption timeout/failure for stanza $stanzaId from $fromBareJid; inserting fallback notice.")
                            processIncomingMessage(fallbackMsg)
                        }
                    }
                }
                return@addAsyncStanzaListener
            }

            if (!stanzaId.isNullOrBlank()) {
                if (processedStanzaIds.contains(stanzaId)) return@addAsyncStanzaListener
                if (processedStanzaIds.size > 500) processedStanzaIds.clear()
                processedStanzaIds.add(stanzaId)
            }

            // Intercept Story updates from contacts
            if (message.subject == "INCHAT_STORY" || bodyText.startsWith("[STORY_UPDATE]")) {
                val jsonStr = bodyText.removePrefix("[STORY_UPDATE]").trim()
                scope.launch(Dispatchers.IO) {
                    try {
                        val json = org.json.JSONObject(jsonStr)
                        val storyId = json.optString("id", java.util.UUID.randomUUID().toString())
                        val senderName = json.optString("senderName", fromBareJid.substringBefore("@"))
                        val text = json.optString("text", "")
                        val mediaUrl = json.optString("mediaUrl", "").takeIf { it.isNotBlank() }
                        val mediaType = json.optString("mediaType", "TEXT")
                        val bgColor = json.optString("bgColor", "#0284C7")
                        val timestamp = json.optLong("timestamp", System.currentTimeMillis())

                        val storyEntity = com.ndregil.inchat.data.local.entity.StoryEntity(
                            id = storyId,
                            senderJid = fromBareJid,
                            senderName = senderName,
                            text = text,
                            mediaUrl = mediaUrl,
                            mediaType = mediaType,
                            backgroundColorHex = bgColor,
                            timestamp = timestamp,
                            isMine = false,
                            isViewed = false
                        )
                        database.storyDao().insertOrUpdateStory(storyEntity)
                        Log.i("XmppRepository", "Saved incoming story update from $fromBareJid")
                    } catch (e: Exception) {
                        Log.w("XmppRepository", "Failed to parse incoming story update: ${e.message}")
                    }
                }
                return@addAsyncStanzaListener
            }

            // Intercept Story view notifications from contacts
            if (message.subject == "INCHAT_STORY_VIEW" || bodyText.startsWith("[STORY_VIEWED]")) {
                val jsonStr = bodyText.removePrefix("[STORY_VIEWED]").trim()
                scope.launch(Dispatchers.IO) {
                    try {
                        val json = org.json.JSONObject(jsonStr)
                        val storyId = json.getString("storyId")
                        val viewerJid = json.getString("viewerJid")
                        val viewerName = json.optString("viewerName", viewerJid.substringBefore("@"))
                        val timestamp = json.optLong("timestamp", System.currentTimeMillis())

                        val viewerEntity = com.ndregil.inchat.data.local.entity.StoryViewerEntity(
                            id = java.util.UUID.randomUUID().toString(),
                            storyId = storyId,
                            viewerJid = viewerJid,
                            viewerName = viewerName,
                            viewedAt = timestamp
                        )
                        database.storyDao().insertOrUpdateViewer(viewerEntity)
                        Log.i("XmppRepository", "Saved incoming story view notification for story $storyId from $viewerJid")
                    } catch (e: Exception) {
                        Log.w("XmppRepository", "Failed to parse incoming story view notification: ${e.message}")
                    }
                }
                return@addAsyncStanzaListener
            }

            val resourcePart = (fromJid as? org.jxmpp.jid.FullJid)?.resourcepart?.toString()
                ?: fromStr.substringAfterLast("/").takeIf { fromStr.contains("/") && !it.equals(fromBareJid, ignoreCase = true) }

            scope.launch(Dispatchers.IO) {
                val urls = com.ndregil.inchat.util.MediaUrlUtils.extractUrls(bodyText)
                val bodyImageUrl = urls.firstOrNull { com.ndregil.inchat.util.MediaUrlUtils.isKnownImageUrl(it) }

                // XEP-0066 OOB Detection
                val oobUrl = if (message.hasExtension("x", "jabber:x:oob")) {
                    val xml = cachedXml ?: message.toXML().toString()
                    Regex("""<x[^>]*xmlns=['"]jabber:x:oob['"][^>]*>\s*<url>([^<]+)</url>""", RegexOption.IGNORE_CASE)
                        .find(xml)?.groupValues?.get(1)?.trim()
                } else null
                val isOobImage = oobUrl != null && com.ndregil.inchat.util.MediaUrlUtils.isKnownImageUrl(oobUrl)

                val isVoice = bodyText.contains("🎤 Pesan Suara", ignoreCase = true) ||
                    urls.any { com.ndregil.inchat.util.MediaUrlUtils.isVoiceOrAudioUrl(it) }

                val isImage = isOobImage || bodyImageUrl != null
                val isFile = isImage || oobUrl != null

                val fileUrl = oobUrl ?: bodyImageUrl ?: if (isVoice) urls.firstOrNull() else null
                val inferredFileName = if (isFile && !fileUrl.isNullOrBlank()) {
                    fileUrl.substringAfterLast('/').substringBefore('?').substringBefore('#')
                        .takeIf { it.isNotBlank() } ?: "Lampiran"
                } else null
                val inferredFileMime = when {
                    isImage -> fileUrl?.let { com.ndregil.inchat.util.MediaUrlUtils.inferImageMimeType(it) } ?: "image/*"
                    isVoice -> "audio/mp4"
                    isFile -> fileUrl?.let { com.ndregil.inchat.util.MediaUrlUtils.inferFileMimeType(it) } ?: "application/octet-stream"
                    else -> null
                }

                val voiceDuration = if (isVoice) {
                    val durationMatch = Regex("\\((\\d+)d\\)").find(bodyText)
                    durationMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 5
                } else null

                val roomEntity = database.roomDao().getRoomByJid(fromBareJid)
                val isMucContext = smackMucManager.isRoomJoined(fromBareJid) ||
                        fromBareJid.contains("conference.", ignoreCase = true) ||
                        fromBareJid.contains("muc.", ignoreCase = true) ||
                        (roomEntity?.isGroupChat == true) ||
                        message.type == org.jivesoftware.smack.packet.Message.Type.groupchat

                // CASE 1: Private WHISPER Message in MUC Room or MUC context
                if (isMucContext && !fromBareJid.equals(myBareJid, ignoreCase = true)) {
                    val roomJid = fromBareJid
                    val senderNick = resourcePart ?: fromStr.substringAfterLast("/").takeIf { it.isNotBlank() } ?: fromBareJid.substringBefore("@")
                    val myRoomNick = roomEntity?.myNickname ?: currentAccount.value.nickname

                    // Skip if sender is ourselves inside MUC
                    if (senderNick.equals(myRoomNick, ignoreCase = true)) return@launch

                    val avatar = smackAvatarManager.getAvatar(fromStr) ?: smackAvatarManager.getAvatar(roomJid)
                    val cachedCaps = smackMucManager.getOccupantCaps(roomJid, senderNick)

                    val xmppMsg = XmppMessage(
                        id = stanzaId ?: java.util.UUID.randomUUID().toString(),
                        roomJid = roomJid,
                        senderNickname = senderNick,
                        senderJid = fromStr,
                        avatarKey = fromStr,
                        senderClientCaps = cachedCaps ?: com.ndregil.inchat.data.model.ClientCaps.resolveFor(senderNick, fromStr),
                        body = bodyText,
                        timestamp = System.currentTimeMillis(),
                        isMine = false,
                        messageType = when {
                            isVoice -> MessageType.VOICE
                            isFile -> MessageType.FILE
                            else -> MessageType.WHISPER
                        },
                        status = DeliveryStatus.DELIVERED,
                        recipientNickname = myRoomNick,
                        fileUrl = fileUrl,
                        fileMimeType = inferredFileMime,
                        fileName = inferredFileName,
                        voiceDurationSeconds = voiceDuration
                    )

                    Log.i("XmppRepository", "Received WHISPER from '$senderNick' in room [$roomJid]: $bodyText (isImage=$isImage)")
                    processIncomingMessage(xmppMsg)
                    return@launch
                }

                // CASE 2: 1-on-1 Direct Chat message from a contact or non-contact JID
                val isSentByMe = fromBareJid.equals(myBareJid, ignoreCase = true)
                val targetRoomJid = if (isSentByMe) {
                    message.to?.asBareJid()?.toString()
                } else {
                    fromBareJid
                }

                if (targetRoomJid == null || targetRoomJid.equals(myBareJid, ignoreCase = true)) {
                    return@launch
                }

                val cleanTargetBareJid = targetRoomJid.substringBefore("/").trim()
                val contactMatch = rosterContacts.value.find { 
                    it.jid.equals(cleanTargetBareJid, ignoreCase = true) || 
                    it.jid.substringBefore("@").equals(cleanTargetBareJid.substringBefore("@"), ignoreCase = true) 
                }
                val senderNick = if (isSentByMe) {
                    currentAccount.value.nickname.ifBlank { currentAccount.value.username }
                } else {
                    contactMatch?.name?.takeIf { it.isNotBlank() } 
                        ?: contactMatch?.nickname?.takeIf { it.isNotBlank() } 
                        ?: cleanTargetBareJid.substringBefore("@")
                }

                val avatar = smackAvatarManager.getAvatar(targetRoomJid)
                val cachedCaps = if (isMucContext) {
                    smackMucManager.getOccupantCaps(targetRoomJid, senderNick)
                } else {
                    contactMatch?.clientCaps
                }

                val xmppMsg = XmppMessage(
                    id = stanzaId ?: java.util.UUID.randomUUID().toString(),
                    roomJid = targetRoomJid,
                    senderNickname = senderNick,
                    senderJid = if (isSentByMe) myJid else fromStr,
                    avatarKey = if (isSentByMe) myJid else fromStr,
                    senderClientCaps = cachedCaps ?: com.ndregil.inchat.data.model.ClientCaps.resolveFor(senderNick, if (isSentByMe) myJid else fromStr),
                    body = bodyText,
                    timestamp = System.currentTimeMillis(),
                    isMine = isSentByMe,
                    messageType = when {
                        isVoice -> MessageType.VOICE
                        isFile -> MessageType.FILE
                        else -> MessageType.CHAT
                    },
                    status = DeliveryStatus.DELIVERED,
                    fileUrl = fileUrl,
                    fileMimeType = inferredFileMime,
                    fileName = inferredFileName,
                    voiceDurationSeconds = voiceDuration
                )

                Log.i("XmppRepository", "Received 1-on-1 direct message (isMine=$isSentByMe) for [$targetRoomJid]: $bodyText (isImage=$isImage)")
                processIncomingMessage(xmppMsg)
            }
        }, filter)

        Log.i("XmppRepository", "StanzaListener attached to connection ${conn.user} for direct messages & whispers")
    }

    private fun setupReceiptListener(connection: org.jivesoftware.smack.XMPPConnection) {
        synchronized(listenerAttachLock) {
            if (lastAttachedReceiptConnection == connection) return
            lastAttachedReceiptConnection = connection
        }
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

    private suspend fun processIncomingMessage(msg: XmppMessage) = incomingMessageMutex.withLock {
        try {
            val canonicalRoomJid = database.roomDao().getRoomByJid(msg.roomJid)?.jid ?: msg.roomJid.trim()

            // Deduplication by ID
            if (msg.id.isNotBlank()) {
                val byId = database.messageDao().getMessageByIdSync(msg.id)
                if (byId != null) {
                    if (!byId.isEncrypted && msg.isEncrypted && !byId.isMine) {
                        val updatedEntity = msg.copy(roomJid = canonicalRoomJid).toEntity()
                        database.messageDao().insertMessage(updatedEntity)
                        database.roomDao().updateLastMessage(canonicalRoomJid, updatedEntity.body, updatedEntity.timestamp, 0)
                        Log.i("XmppRepository", "Updated placeholder OMEMO message ${msg.id} with decrypted plaintext")
                        return@withLock
                    }
                    Log.d("XmppRepository", "Deduplicating message by ID: ${msg.id}")
                    if (byId.isMine && byId.status == DeliveryStatus.SENDING) {
                        database.messageDao().updateMessageStatus(byId.id, DeliveryStatus.SENT)
                    }
                    return@withLock
                }
            }

            // Re-verify whether this MUC reflection is from our live joined nickname
            val liveMucNick = smackMucManager.getMultiUserChat(canonicalRoomJid)?.nickname?.toString()
            val storedRoomNick = database.roomDao().getRoomByJid(canonicalRoomJid)?.myNickname
            val effectiveIsMine = msg.isMine ||
                (!liveMucNick.isNullOrBlank() && msg.senderNickname.equals(liveMucNick, ignoreCase = true)) ||
                (!storedRoomNick.isNullOrBlank() && msg.senderNickname.equals(storedRoomNick, ignoreCase = true))

            // Enhanced Client Caps Resolution: Try global cache if not identified or is placeholder
            val withCaps = if (msg.senderClientCaps.iconResId == null) {
                val cached = com.ndregil.inchat.data.network.ClientRegistryManager.getCachedCaps(msg.senderJid)
                if (cached != null) msg.copy(senderClientCaps = cached) else msg
            } else msg

            val finalMsg = withCaps.copy(
                roomJid = canonicalRoomJid,
                isMine = effectiveIsMine
            )

            // Deduplication for outgoing echoes/reflections (isMine == true)
            if (finalMsg.isMine) {
                val recentMine = database.messageDao().findRecentOutgoingMessage(finalMsg.roomJid, finalMsg.body)
                if (recentMine != null && kotlin.math.abs(recentMine.timestamp - finalMsg.timestamp) < 120_000L) {
                    Log.d("XmppRepository", "Deduplicating outgoing echo message by content/time: ${finalMsg.id}")
                    if (recentMine.status == DeliveryStatus.SENDING) {
                        database.messageDao().updateMessageStatus(recentMine.id, DeliveryStatus.SENT)
                    }
                    return@withLock
                }

                if (!finalMsg.fileUrl.isNullOrBlank()) {
                    val recentByUrl = database.messageDao().findRecentOutgoingByFileUrl(finalMsg.roomJid, finalMsg.fileUrl)
                    if (recentByUrl != null && kotlin.math.abs(recentByUrl.timestamp - finalMsg.timestamp) < 120_000L) {
                        Log.d("XmppRepository", "Deduplicating outgoing file/image echo by fileUrl: ${finalMsg.fileUrl}")
                        if (recentByUrl.status == DeliveryStatus.SENDING) {
                            database.messageDao().updateMessageStatus(recentByUrl.id, DeliveryStatus.SENT)
                        }
                        return@withLock
                    }
                }

                val latestMine = database.messageDao().findLatestMineMessage(finalMsg.roomJid)
                if (latestMine != null && kotlin.math.abs(latestMine.timestamp - finalMsg.timestamp) < 60_000L &&
                    (latestMine.body.trim() == finalMsg.body.trim() || latestMine.translatedBody?.trim() == finalMsg.body.trim())
                ) {
                    Log.d("XmppRepository", "Deduplicating outgoing echo message by trimmed content/time: ${finalMsg.id}")
                    if (latestMine.status == DeliveryStatus.SENDING) {
                        database.messageDao().updateMessageStatus(latestMine.id, DeliveryStatus.SENT)
                    }
                    return@withLock
                }
            }

            // Deduplication by recent message content/timestamp
            val existing = database.messageDao().findRecentMessage(finalMsg.roomJid, finalMsg.body, finalMsg.isMine)
            if (existing != null && kotlin.math.abs(existing.timestamp - finalMsg.timestamp) < 60_000L) {
                Log.d("XmppRepository", "Deduplicating message by content/time: ${finalMsg.id}")
                return@withLock
            }

            // 0. Ensure room exists in database so it appears in Chat list
            if (database.roomDao().getRoomByJid(finalMsg.roomJid) == null) {
                val isGroup = finalMsg.messageType == MessageType.GROUPCHAT || isMucJid(finalMsg.roomJid)
                val defaultName = if (isGroup) "# " + finalMsg.roomJid.substringBefore("@") else finalMsg.senderNickname.ifBlank { finalMsg.roomJid.substringBefore("@") }
                val newRoom = RoomEntity(
                    jid = finalMsg.roomJid,
                    name = defaultName,
                    topic = if (isGroup) "Ruangan MUC" else "Obrolan Langsung (1-on-1)",
                    myNickname = currentAccount.value.nickname,
                    occupantCount = if (isGroup) 1 else 2,
                    unreadCount = 0,
                    lastMessageText = "",
                    lastMessageTime = finalMsg.timestamp,
                    isJoined = isGroup,
                    isBookmarked = false,
                    myRole = if (isGroup) OccupantRole.PARTICIPANT else OccupantRole.NONE,
                    myAffiliation = if (isGroup) OccupantAffiliation.MEMBER else OccupantAffiliation.NONE,
                    colorHex = randomColorForRoom(finalMsg.roomJid),
                    isAutoJoin = isGroup,
                    isGroupChat = isGroup
                )
                database.roomDao().insertOrUpdateRoom(newRoom)
            }

            // 1. Efficient Insert
            database.messageDao().insertMessage(finalMsg.toEntity())

            // Auto-translate incoming message if enabled (no history cap)
            if (!finalMsg.isMine && finalMsg.body.isNotBlank() &&
                finalMsg.messageType != MessageType.FILE &&
                finalMsg.messageType != MessageType.VOICE &&
                finalMsg.messageType != MessageType.CALL_SIGNALLING &&
                finalMsg.messageType != MessageType.SYSTEM_JOIN &&
                finalMsg.messageType != MessageType.SYSTEM_LEAVE
            ) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val prefs = translationPreferencesManager.translationPreferences.first()
                        val isGroup = isMucJid(finalMsg.roomJid)
                        val shouldTranslate = prefs.isAutoTranslateEnabled &&
                                if (isGroup) prefs.translateRooms else prefs.translateContacts

                        if (shouldTranslate) {
                            val targetLang = prefs.targetLanguageCode
                            val flightKey = "${finalMsg.id}:$targetLang"
                            if (translationInFlight.add(flightKey)) {
                                try {
                                    autoTranslateSemaphore.withPermit {
                                        val translated = translationService.translateText(finalMsg.body, targetLang)
                                        translationAttempted[finalMsg.id] = targetLang
                                        if (!translated.isNullOrBlank()) {
                                            database.messageDao().updateMessageTranslation(finalMsg.id, translated, targetLang)
                                        }
                                    }
                                } finally {
                                    translationInFlight.remove(flightKey)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("XmppRepository", "Auto-translate error: ${e.message}")
                    }
                }
            }

            // 2. Optimized Room Update
            val lastText = when(finalMsg.messageType) {
                MessageType.WHISPER -> "[Bisikan] ${finalMsg.body}"
                MessageType.CHAT -> finalMsg.body
                MessageType.FILE -> "[Berkas] ${finalMsg.fileName ?: "Lampiran"}"
                MessageType.VOICE -> "[Pesan Suara]"
                MessageType.ACTION -> "* ${finalMsg.senderNickname} ${finalMsg.body}"
                else -> finalMsg.body
            }
            
            // Only increment unread if NOT mine AND NOT history
            val unreadIncrement = if (finalMsg.isMine || finalMsg.isHistory) 0 else 1
            
            // Use specialized update query to avoid overwriting lastMessageTime if older history
            val updatedRows = database.roomDao().updateLastMessage(finalMsg.roomJid, lastText, finalMsg.timestamp, unreadIncrement)
            if (updatedRows == 0 && unreadIncrement > 0) {
                database.roomDao().incrementUnreadCount(finalMsg.roomJid, unreadIncrement)
            }

            // 3. Conditional Notification (Skip history, skip system messages)
            if (!finalMsg.isMine && !finalMsg.isHistory &&
                finalMsg.messageType != MessageType.SYSTEM_JOIN &&
                finalMsg.messageType != MessageType.SYSTEM_LEAVE &&
                finalMsg.messageType != MessageType.SYSTEM_STATUS &&
                finalMsg.messageType != MessageType.SYSTEM_TOPIC &&
                (finalMsg.body.isNotBlank() || !finalMsg.fileName.isNullOrBlank())
            ) {
                val isRecent = (System.currentTimeMillis() - finalMsg.timestamp) < 30_000L
                if (isRecent) {
                    val roomConfig = database.roomConfigDao().getRoomConfigSync(finalMsg.roomJid)
                    val globalEnabled = FcmPushManager.isPushEnabled.value
                    val notificationMode = roomConfig?.notificationMode ?: 0 // 0: All, 1: Mentions, 2: Muted

                    val isMention = finalMsg.body.contains(currentAccount.value.nickname, ignoreCase = true) ||
                                  finalMsg.body.contains(currentAccount.value.username, ignoreCase = true)

                    val shouldNotify = globalEnabled && when (notificationMode) {
                        0 -> true // All messages
                        1 -> isMention // Mentions only
                        2 -> false // Muted
                        else -> true
                    }

                    if (shouldNotify) {
                        val isDirect = finalMsg.messageType == MessageType.CHAT ||
                                (!finalMsg.roomJid.contains("conference.", ignoreCase = true) && !finalMsg.roomJid.contains("muc.", ignoreCase = true))
                        val senderName = finalMsg.senderNickname.ifBlank { finalMsg.senderJid.substringBefore("@") }

                        val payload = PushMessagePayload(
                            messageId = finalMsg.id,
                            roomJid = finalMsg.roomJid,
                            senderNickname = senderName,
                            body = lastText,
                            isWhisper = finalMsg.messageType == MessageType.WHISPER,
                            isDirect = isDirect,
                            roomTitle = finalMsg.senderNickname, // Fallback title
                            timestamp = finalMsg.timestamp,
                            isMention = isMention
                        )
                        FcmPushManager.showSystemNotification(context, payload)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("XmppRepository", "Error processing message: ${e.message}")
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
            lastMessageText = getAppStrings(currentAppLanguage).sysJustJoined,
            lastMessageTime = System.currentTimeMillis(),
            isJoined = true,
            isBookmarked = true,
            myRole = OccupantRole.MEMBER,
            myAffiliation = OccupantAffiliation.MEMBER,
            colorHex = randomColorForRoom(roomJid),
            password = if (password.isNotBlank()) password else null,
            isAutoJoin = true,
            isGroupChat = isMucJid(roomJid)
        )
        database.roomDao().insertOrUpdateRoom(roomEntity)

        // Persist room configuration
        val existingConfig = database.roomConfigDao().getRoomConfigSync(roomJid)
        val config = existingConfig?.copy(
            roomName = if (name.isNotBlank()) name else existingConfig.roomName,
            nickname = nickname,
            password = if (password.isNotBlank()) password else existingConfig.password,
            subject = if (topic.isNotBlank()) topic else existingConfig.subject,
            isAutoJoin = true, // Force autojoin for manually joined rooms
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
            lastJoinedAt = System.currentTimeMillis(),
            isGroupChat = isMucJid(roomJid)
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
            } else {
                val actualNick = mucResult.getOrNull()?.nickname?.toString()?.takeIf { it.isNotBlank() }
                if (actualNick != null && !actualNick.equals(nickname, ignoreCase = true)) {
                    database.roomDao().updateNickname(roomJid, actualNick)
                }
                val liveCount = smackMucManager.getRoomOccupantsCount(roomJid)
                if (liveCount > 0) {
                    database.roomDao().updateOccupantCount(roomJid, liveCount)
                }
                // If join succeeded, trigger MAM history sync to fill gaps since last seen message
                // Delay MAM sync slightly to let the room presence stabilize and avoid flooding the connection
                scope.launch {
                    try {
                        kotlinx.coroutines.delay(1500)
                        syncMamHistory(roomJid, limit = 100)
                    } catch (e: Exception) {
                        Log.w("XmppRepository", "MAM Sync failed for $roomJid: ${e.message}")
                    }
                }
            }
        }

        engine.joinRoom(roomJid, nickname, password)
        return smackResult
    }

    suspend fun leaveRoom(roomJid: String, nickname: String) {
        val existing = database.roomDao().getRoomByJid(roomJid)
        if (existing != null) {
            if (existing.isBookmarked) {
                // Respect the user's distinction: leave only affects current session (isJoined)
                // It should NOT touch isAutoJoin or isBookmarked.
                database.roomDao().insertOrUpdateRoom(existing.copy(isJoined = false))
            } else {
                // If not bookmarked, completely remove from active rooms list
                database.roomDao().deleteRoom(roomJid)
                database.roomConfigDao().deleteConfig(roomJid)
            }
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
        // 1. Leave the room on the server & network first
        try {
            val nick = currentAccount.value.nickname.ifBlank { "User" }
            leaveRoom(roomJid, nick)
        } catch (_: Exception) {}

        // 2. Remove bookmark (penanda room) from server (XEP-0048) and local database
        try {
            removeSavedRoom(roomJid)
        } catch (_: Exception) {}

        // 3. Delete room and config from database
        database.roomDao().deleteRoom(roomJid)
        database.roomConfigDao().deleteConfig(roomJid)
        database.messageDao().clearRoomMessages(roomJid)
    }

    /**
     * Automatically rejoins rooms configured with isAutoJoin = true or saved in Room database using persisted credentials.
     */
    suspend fun autoJoinConfiguredRooms() = kotlinx.coroutines.withContext(Dispatchers.IO) {
        autoJoinMutex.withLock {
            Log.i("XmppRepository", "Starting auto-join for configured rooms...")
            val autoJoinConfigs = database.roomConfigDao().getAutoJoinRoomConfigsList()
            val savedRooms = database.roomDao().getAllRoomsList()

            val roomsToJoin = linkedMapOf<String, Triple<String, String, String?>>() // normalized jid -> (name, nick, pass)
            val originalJidByNorm = mutableMapOf<String, String>()

            for (cfg in autoJoinConfigs) {
                val norm = cfg.roomJid.trim().lowercase()
                val nick = cfg.nickname.ifBlank { currentAccount.value.nickname.ifBlank { currentAccount.value.username.ifBlank { "User" } } }
                roomsToJoin[norm] = Triple(cfg.roomName, nick, cfg.password)
                originalJidByNorm.putIfAbsent(norm, cfg.roomJid)
            }

            for (room in savedRooms) {
                val norm = room.jid.trim().lowercase()
                if (!roomsToJoin.containsKey(norm) && (room.isAutoJoin || room.isJoined)) {
                    val nick = room.myNickname.ifBlank { currentAccount.value.nickname.ifBlank { currentAccount.value.username.ifBlank { "User" } } }
                    roomsToJoin[norm] = Triple(room.name, nick, room.password)
                    originalJidByNorm.putIfAbsent(norm, room.jid)
                }
            }

            if (roomsToJoin.isEmpty()) {
                Log.d("XmppRepository", "No rooms configured for auto-join.")
                return@withLock
            }

            val joinedNames = java.util.Collections.synchronizedList(mutableListOf<String>())

            // Join rooms sequentially with a small delay to avoid overwhelming the server and connection
            for ((norm, data) in roomsToJoin) {
                val jid = originalJidByNorm[norm] ?: norm
                if (smackMucManager.isRoomJoined(jid)) {
                    Log.d("XmppRepository", "Skipping auto-join for already-joined room $jid")
                    continue
                }
                val (name, nick, pass) = data
                try {
                    Log.d("XmppRepository", "Attempting auto-join for $jid as $nick...")
                    val res = joinRoom(
                        roomJid = jid,
                        name = name,
                        nickname = nick,
                        password = pass.orEmpty()
                    )
                    if (res.isSuccess) {
                        joinedNames.add(name.ifBlank { jid.substringBefore("@") })
                        Log.i("XmppRepository", "Successfully auto-joined $jid")
                    } else {
                        Log.w("XmppRepository", "Auto-join failed for $jid: ${res.exceptionOrNull()?.message}")
                    }
                    // Small delay between joins to keep connection stable and avoid flood
                    kotlinx.coroutines.delay(800)
                } catch (e: Exception) {
                    Log.w("XmppRepository", "Auto-join Exception for $jid: ${e.message}")
                }
            }

            if (joinedNames.isNotEmpty()) {
                val namesStr = joinedNames.distinct().joinToString(", ")
                _autoJoinEvents.emit("Berhasil bergabung otomatis ke room: $namesStr")
                Log.i("XmppRepository", "Auto-join completed for: $namesStr")
            }
        }
    }

    private suspend fun deduplicateAndInsertMamMessages(
        canonicalRoomJid: String,
        xmppMessages: List<XmppMessage>,
        updateRoomLastMessage: Boolean
    ): Int = incomingMessageMutex.withLock {
        val toInsert = mutableListOf<MessageEntity>()
        val seenIds = mutableSetOf<String>()
        for (msg in xmppMessages) {
            if (!seenIds.add(msg.id)) continue
            if (database.messageDao().getMessageByIdSync(msg.id) != null) continue
            if (msg.isMine) {
                val recentMine = database.messageDao().findRecentOutgoingMessage(canonicalRoomJid, msg.body)
                if (recentMine != null && kotlin.math.abs(recentMine.timestamp - msg.timestamp) < 120_000L) {
                    continue
                }
                if (!msg.fileUrl.isNullOrBlank()) {
                    val recentByUrl = database.messageDao().findRecentOutgoingByFileUrl(canonicalRoomJid, msg.fileUrl)
                    if (recentByUrl != null && kotlin.math.abs(recentByUrl.timestamp - msg.timestamp) < 120_000L) {
                        continue
                    }
                }
            }
            val recentSame = database.messageDao().findRecentMessage(canonicalRoomJid, msg.body, msg.isMine)
            if (recentSame != null && kotlin.math.abs(recentSame.timestamp - msg.timestamp) < 120_000L) {
                continue
            }
            toInsert.add(msg.copy(roomJid = canonicalRoomJid).toEntity())
        }
        if (toInsert.isNotEmpty()) {
            val latest = if (updateRoomLastMessage) toInsert.maxByOrNull { it.timestamp } else null
            database.withTransaction {
                database.messageDao().insertAll(toInsert)
                if (latest != null) {
                    database.roomDao().updateLastMessage(canonicalRoomJid, latest.body, latest.timestamp, 0)
                }
            }
        }
        toInsert.size
    }

    suspend fun syncMamHistory(roomJid: String, limit: Int = 100) {
        if (!smackConnectionManager.isConnected()) return

        val canonicalJid = database.roomDao().getRoomByJid(roomJid)?.jid ?: roomJid.trim()
        val jobKey = canonicalJid.lowercase()

        // Deduplicate sync requests for the same room
        mamSyncJobs[jobKey]?.cancel()
        val job = scope.launch(Dispatchers.IO) {
            try {
                val latestTimestamp = database.messageDao().getLatestMessageTimestamp(canonicalJid) ?: 0L
                Log.i("XmppRepository", "Starting MAM Sync for $canonicalJid since ${java.util.Date(latestTimestamp)} (limit: $limit)")

                val result = smackMucManager.fetchMamHistory(canonicalJid, since = latestTimestamp, before = 0L, limit = limit)
                result.onSuccess { messages ->
                    if (messages.isEmpty()) {
                        Log.d("XmppRepository", "No new MAM messages for $canonicalJid")
                        return@onSuccess
                    }

                    Log.i("XmppRepository", "Processing ${messages.size} MAM messages for $canonicalJid")
                    val roomEntity = database.roomDao().getRoomByJid(canonicalJid)
                    val liveNick = smackMucManager.getMultiUserChat(canonicalJid)?.nickname?.toString()?.takeIf { it.isNotBlank() }
                    val myNickname = liveNick ?: roomEntity?.myNickname ?: currentAccount.value.nickname
                    val myJid = currentAccount.value.jid

                    val xmppMessages = mutableListOf<XmppMessage>()
                    messages.forEach { smackMsg ->
                        try {
                            val fromJid = smackMsg.from
                            val senderNick = if (isMucJid(canonicalJid)) {
                                fromJid?.resourceOrNull?.toString()
                                    ?: fromJid?.asBareJid()?.toString()?.substringBefore("@")
                                    ?: "User"
                            } else {
                                fromJid?.asBareJid()?.toString()?.substringBefore("@") ?: "User"
                            }

                            val isMine = if (isMucJid(canonicalJid)) {
                                senderNick.equals(myNickname, ignoreCase = true)
                            } else {
                                fromJid?.asBareJid()?.toString()?.equals(myJid.substringBefore("/"), ignoreCase = true) == true
                            }

                            val delayInfo = DelayInformationManager.getDelayInformation(smackMsg)
                            val timestamp = delayInfo?.stamp?.time ?: System.currentTimeMillis()

                            val omemoExt = smackMsg.getExtension("encrypted", "eu.siacs.conversations.axolotl")
                                as? org.jivesoftware.smackx.omemo.element.OmemoElement
                            if (omemoExt != null && omemoExt.isKeyTransportElement) {
                                return@forEach
                            }

                            val existingId = smackMsg.stanzaId?.ifBlank { null }
                            if (omemoExt != null && existingId != null && database.messageDao().getMessageByIdSync(existingId) != null) {
                                return@forEach
                            }

                            var isEncryptedMsg = false
                            val body = if (omemoExt != null && !isMucJid(canonicalJid)) {
                                val decrypted = omemoEncryptionManager.tryDecryptStanza(smackMsg)
                                if (decrypted.isSuccess) {
                                    isEncryptedMsg = true
                                    decrypted.getOrThrow()
                                } else {
                                    "🔒 [Pesan Terenkripsi OMEMO]"
                                }
                            } else {
                                smackMsg.body ?: return@forEach
                            }

                            val stanzaId = existingId
                                ?: "${canonicalJid}_${timestamp}_${senderNick.hashCode()}_${body.hashCode()}"

                            val xmppMsg = XmppMessage(
                                id = stanzaId,
                                roomJid = canonicalJid,
                                senderJid = fromJid?.toString() ?: canonicalJid,
                                senderNickname = senderNick,
                                body = body,
                                timestamp = timestamp,
                                isMine = isMine,
                                messageType = if (smackMsg.type == org.jivesoftware.smack.packet.Message.Type.groupchat) MessageType.GROUPCHAT else MessageType.CHAT,
                                status = DeliveryStatus.DELIVERED,
                                isHistory = true,
                                isEncrypted = isEncryptedMsg
                            )
                            xmppMessages.add(xmppMsg)
                        } catch (e: Exception) {
                            Log.w("XmppRepository", "Error parsing MAM message: ${e.message}")
                        }
                    }

                    if (xmppMessages.isNotEmpty()) {
                        deduplicateAndInsertMamMessages(
                            canonicalRoomJid = canonicalJid,
                            xmppMessages = xmppMessages,
                            updateRoomLastMessage = true
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w("XmppRepository", "MAM Sync exception for $canonicalJid: ${e.message}")
            } finally {
                mamSyncJobs.remove(jobKey)
            }
        }
        mamSyncJobs[jobKey] = job
    }

    suspend fun loadOlderMamHistory(roomJid: String, limit: Int = 100): Int {
        if (!smackConnectionManager.isConnected()) return 0

        val canonicalJid = database.roomDao().getRoomByJid(roomJid)?.jid ?: roomJid.trim()
        val earliestTimestamp = database.messageDao().getEarliestMessageTimestamp(canonicalJid) ?: 0L
        Log.i("XmppRepository", "Starting older MAM Sync for $canonicalJid before ${java.util.Date(earliestTimestamp)} (limit: $limit)")

        val beforeParam = if (earliestTimestamp > 0L) earliestTimestamp - 1 else 0L
        val result = smackMucManager.fetchMamHistory(canonicalJid, since = 0L, before = beforeParam, limit = limit)
        var countAdded = 0
        result.onSuccess { messages ->
            if (messages.isEmpty()) {
                Log.d("XmppRepository", "No older MAM messages found for $canonicalJid")
                return@onSuccess
            }

            Log.i("XmppRepository", "Processing ${messages.size} older MAM messages for $canonicalJid")
            val roomEntity = database.roomDao().getRoomByJid(canonicalJid)
            val liveNick = smackMucManager.getMultiUserChat(canonicalJid)?.nickname?.toString()?.takeIf { it.isNotBlank() }
            val myNickname = liveNick ?: roomEntity?.myNickname ?: currentAccount.value.nickname
            val myJid = currentAccount.value.jid

            val xmppMessages = mutableListOf<XmppMessage>()
            messages.forEach { smackMsg ->
                try {
                    val fromJid = smackMsg.from
                    val senderNick = if (isMucJid(canonicalJid)) {
                        fromJid?.resourceOrNull?.toString()
                            ?: fromJid?.asBareJid()?.toString()?.substringBefore("@")
                            ?: "User"
                    } else {
                        fromJid?.asBareJid()?.toString()?.substringBefore("@") ?: "User"
                    }

                    val isMine = if (isMucJid(canonicalJid)) {
                        senderNick.equals(myNickname, ignoreCase = true)
                    } else {
                        fromJid?.asBareJid()?.toString()?.equals(myJid.substringBefore("/"), ignoreCase = true) == true
                    }

                    val delayInfo = DelayInformationManager.getDelayInformation(smackMsg)
                    val timestamp = delayInfo?.stamp?.time ?: System.currentTimeMillis()

                    val omemoExt = smackMsg.getExtension("encrypted", "eu.siacs.conversations.axolotl")
                        as? org.jivesoftware.smackx.omemo.element.OmemoElement
                    if (omemoExt != null && omemoExt.isKeyTransportElement) {
                        return@forEach
                    }

                    val existingId = smackMsg.stanzaId?.ifBlank { null }
                    if (omemoExt != null && existingId != null && database.messageDao().getMessageByIdSync(existingId) != null) {
                        return@forEach
                    }

                    var isEncryptedMsg = false
                    val body = if (omemoExt != null && !isMucJid(canonicalJid)) {
                        val decrypted = omemoEncryptionManager.tryDecryptStanza(smackMsg)
                        if (decrypted.isSuccess) {
                            isEncryptedMsg = true
                            decrypted.getOrThrow()
                        } else {
                            "🔒 [Pesan Terenkripsi OMEMO]"
                        }
                    } else {
                        smackMsg.body ?: return@forEach
                    }

                    val stanzaId = existingId
                        ?: "${canonicalJid}_${timestamp}_${senderNick.hashCode()}_${body.hashCode()}"

                    val xmppMsg = XmppMessage(
                        id = stanzaId,
                        roomJid = canonicalJid,
                        senderJid = fromJid?.toString() ?: canonicalJid,
                        senderNickname = senderNick,
                        body = body,
                        timestamp = timestamp,
                        isMine = isMine,
                        messageType = if (smackMsg.type == org.jivesoftware.smack.packet.Message.Type.groupchat) MessageType.GROUPCHAT else MessageType.CHAT,
                        status = DeliveryStatus.DELIVERED,
                        isHistory = true,
                        isEncrypted = isEncryptedMsg
                    )
                    xmppMessages.add(xmppMsg)
                } catch (e: Exception) {
                    Log.w("XmppRepository", "Error parsing older MAM message: ${e.message}")
                }
            }

            if (xmppMessages.isNotEmpty()) {
                countAdded = deduplicateAndInsertMamMessages(
                    canonicalRoomJid = canonicalJid,
                    xmppMessages = xmppMessages,
                    updateRoomLastMessage = false
                )
            }
        }
        return countAdded
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
            isAutoJoin = true,
            isGroupChat = isMucJid(cleanJid)
        )).copy(
            roomName = name,
            nickname = nick,
            password = password ?: existingConfig?.password,
            subject = if (topic.isNotBlank()) topic else (existingConfig?.subject ?: ""),
            isBookmarked = true,
            isAutoJoin = true // Ensure it autojoins if saved manually
        )
        database.roomConfigDao().insertOrUpdateConfig(config)
        smackAvatarManager.getAvatar(cleanJid)

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
                password = password,
                isAutoJoin = true,
                isGroupChat = isMucJid(cleanJid)
            )
            database.roomDao().insertOrUpdateRoom(newRoom)
        }

        // Sync MUC room bookmark with server only for actual MUC rooms (never for 1-on-1 contacts)
        if (isMucJid(cleanJid) && smackConnectionManager.isConnected()) {
            smackMucManager.saveBookmark(
                roomJid = cleanJid,
                name = name,
                nickname = nick,
                password = password,
                autoJoin = true
            )
        }
    }

    suspend fun ensureDirectChatRoom(
        contactJid: String,
        displayName: String,
        myNickname: String
    ) = withContext(Dispatchers.IO) {
        val cleanJid = contactJid.substringBefore("/").trim()
        if (cleanJid.isBlank()) return@withContext
        val name = displayName.trim().ifBlank { cleanJid.substringBefore("@") }
        val nick = myNickname.trim().ifBlank { currentAccount.value.nickname.ifBlank { "User" } }

        val existingRoom = database.roomDao().getRoomByJid(cleanJid)
        if (existingRoom != null) {
            if (existingRoom.name != name && name.isNotBlank()) {
                database.roomDao().insertOrUpdateRoom(existingRoom.copy(name = name, myNickname = nick))
            }
        } else {
            val newRoom = RoomEntity(
                jid = cleanJid,
                name = name,
                topic = "Obrolan Langsung (1-on-1)",
                myNickname = nick,
                occupantCount = 2,
                unreadCount = 0,
                lastMessageText = "",
                lastMessageTime = System.currentTimeMillis(),
                isJoined = true,
                isBookmarked = false,
                myRole = OccupantRole.MEMBER,
                myAffiliation = OccupantAffiliation.MEMBER,
                colorHex = randomColorForRoom(cleanJid),
                isAutoJoin = false,
                isGroupChat = false
            )
            database.roomDao().insertOrUpdateRoom(newRoom)
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
        val room = database.roomDao().getRoomByJid(roomJid)
        if (room != null) {
            if (!room.isGroupChat && !room.isBookmarked) {
                database.roomDao().deleteRoom(roomJid)
            } else {
                database.roomDao().updateLastMessage(roomJid, "", 0L, 0)
                database.roomDao().resetUnreadCount(roomJid)
            }
        }
    }

    suspend fun closeConversation(roomJid: String) {
        val room = database.roomDao().getRoomByJid(roomJid)
        if (room != null) {
            if (!room.isGroupChat && !room.isBookmarked) {
                database.roomDao().deleteRoom(roomJid)
            } else {
                database.roomDao().updateLastMessage(roomJid, "", 0L, 0)
                database.roomDao().resetUnreadCount(roomJid)
            }
        }
    }

    fun searchRoomChatHistory(roomJid: String, query: String): Flow<List<XmppMessage>> {
        return database.messageDao().searchMessages(roomJid, query).map { list ->
            list.map { it.toModel() }
        }
    }

    suspend fun updateMessageDeliveryStatus(messageId: String, status: DeliveryStatus) {
        database.messageDao().updateMessageStatus(messageId, status)
    }


    suspend fun deleteMessageById(id: String) = withContext(Dispatchers.IO) {
        try {
            database.messageDao().deleteMessage(id)
        } catch (e: Exception) {
            Log.w("XmppRepository", "Failed to delete message $id: ${e.message}")
        }
    }

    suspend fun sendMessage(
        roomJid: String,
        text: String,
        myNickname: String,
        replyToMessageId: String? = null,
        replyToSender: String? = null,
        replyToText: String? = null,
        allowPlaintextOnce: Boolean = false
    ): SendMessageOutcome = withContext(Dispatchers.IO) {
        val rawCleanJid = roomJid.trim()
        if (rawCleanJid.isBlank() || text.isBlank()) return@withContext SendMessageOutcome.Failed("Pesan kosong.", null)
        val cleanJid = database.roomDao().getRoomByJid(rawCleanJid)?.jid ?: rawCleanJid

        val myJid = currentAccount.value.jid
        val isGroup = isMucJid(cleanJid)
        // Catatan OMEMO: chat 1-on-1 selalu beroperasi pada bare JID (tanpa /resource)
        val bareTargetJid = if (!isGroup) omemoEncryptionManager.toBareJid(cleanJid) else cleanJid
        val urls = com.ndregil.inchat.util.MediaUrlUtils.extractUrls(text)
        val standaloneImageUrl = urls.firstOrNull { com.ndregil.inchat.util.MediaUrlUtils.isKnownImageUrl(it) }
        val isStandaloneUrl = text.trim().startsWith("http", ignoreCase = true) && urls.size == 1 && !text.trim().contains(" ")
        val stanzaId = java.util.UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val isImage = standaloneImageUrl != null

        val isOmemoActive = !isGroup && !allowPlaintextOnce && omemoEncryptionManager.isOmemoEnabledFor(bareTargetJid)

        // 1. Simpan ke Room terlebih dahulu dengan isEncrypted = false (hanya diubah true bila stanza OMEMO benar-benar terkirim)
        val initialMsg = XmppMessage(
            id = stanzaId,
            roomJid = cleanJid,
            senderJid = myJid,
            senderNickname = myNickname,
            body = text,
            timestamp = timestamp,
            isMine = true,
            messageType = when {
                isImage && isStandaloneUrl -> MessageType.FILE
                isGroup -> MessageType.GROUPCHAT
                else -> MessageType.CHAT
            },
            status = DeliveryStatus.SENDING,
            replyToMessageId = replyToMessageId,
            replyToSender = replyToSender,
            replyToText = replyToText,
            fileUrl = standaloneImageUrl ?: if (isStandaloneUrl) urls.firstOrNull() else null,
            fileMimeType = when {
                standaloneImageUrl != null -> com.ndregil.inchat.util.MediaUrlUtils.inferImageMimeType(standaloneImageUrl)
                isStandaloneUrl -> "application/octet-stream"
                else -> null
            },
            fileName = if (isStandaloneUrl) "Lampiran" else null,
            isEncrypted = false
        )

        incomingMessageMutex.withLock {
            try {
                database.messageDao().insertMessage(initialMsg.toEntity())
                val existing = database.roomDao().getRoomByJid(cleanJid)
                val previewText = if (isImage && isStandaloneUrl) "[Gambar] $text" else text
                if (existing != null) {
                    database.roomDao().updateLastMessage(cleanJid, previewText, timestamp, 0)
                } else {
                    val newRoom = RoomEntity(
                        jid = cleanJid,
                        name = cleanJid.substringBefore("@"),
                        topic = if (isGroup) "Ruangan MUC" else "Obrolan Langsung (1-on-1)",
                        myNickname = myNickname,
                        occupantCount = if (isGroup) 1 else 2,
                        unreadCount = 0,
                        lastMessageText = previewText,
                        lastMessageTime = timestamp,
                        isJoined = true,
                        isBookmarked = false,
                        myRole = if (isGroup) OccupantRole.PARTICIPANT else OccupantRole.MEMBER,
                        myAffiliation = OccupantAffiliation.MEMBER,
                        colorHex = randomColorForRoom(cleanJid),
                        isAutoJoin = isGroup,
                        isGroupChat = isGroup
                    )
                    database.roomDao().insertOrUpdateRoom(newRoom)
                }
            } catch (e: Exception) {
                Log.e("XmppRepository", "Failed optimistic local insert for $cleanJid: ${e.message}")
            }
        }

        // 2. Check if outgoing translation is enabled (works independently without incoming/scope limits)
        var textToSend = text
        var targetLangCode: String? = null
        try {
            val prefs = translationPreferencesManager.translationPreferences.first()
            if (!isStandaloneUrl && prefs.translateOutgoing) {
                val translated = translationService.translateText(
                    text = text,
                    targetLangCode = prefs.targetLanguageCode,
                    skipHeuristics = true
                )
                translationAttempted[stanzaId] = prefs.targetLanguageCode
                if (!translated.isNullOrBlank()) {
                    textToSend = translated
                    targetLangCode = prefs.targetLanguageCode
                }
            }
        } catch (e: Exception) {
            Log.w("XmppRepository", "Failed to translate outgoing message: ${e.message}")
        }

        // Persist translatedBody BEFORE sending so fast MUC echoes match translatedBody immediately
        if (textToSend != text && targetLangCode != null) {
            try {
                database.messageDao().updateMessageTranslation(stanzaId, textToSend, targetLangCode)
            } catch (e: Exception) {
                Log.w("XmppRepository", "Failed to pre-update outgoing translation: ${e.message}")
            }
        }

        val isUrlFinal = textToSend.trim().startsWith("http")

        // 3. Jalur ketat OMEMO 1-on-1: bila saklar OMEMO aktif, DILARANG keras fallback ke plaintext dalam kondisi apa pun!
        if (isOmemoActive && !isGroup) {
            val connection = if (smackConnectionManager.isConnected() && smackConnectionManager.isAuthenticated()) {
                smackConnectionManager.getRawConnection()
            } else null

            if (connection == null) {
                val reason = "Koneksi XMPP terputus. Pesan OMEMO tidak dikirim dalam teks biasa."
                database.messageDao().updateMessageFailure(
                    stanzaId,
                    DeliveryStatus.FAILED,
                    false,
                    "$text\n⚠️ Gagal OMEMO: $reason"
                )
                database.roomDao().updateLastMessage(cleanJid, "⚠️ Gagal OMEMO: $text", timestamp, 0)
                if (!currentAccount.value.isDemoMode) checkAndReconnectIfNeeded()
                return@withContext SendMessageOutcome.Failed(reason, stanzaId)
            }

            val encRes = omemoEncryptionManager.encryptDirectMessage(
                targetBareJidStr = bareTargetJid,
                plaintext = textToSend,
                stanzaId = stanzaId,
                oobUrl = if (isUrlFinal && isStandaloneUrl) textToSend.trim() else null
            )

            if (encRes.isSuccess) {
                return@withContext try {
                    connection.sendStanza(encRes.getOrNull()!!)
                    database.messageDao().updateMessageStatusAndEncrypted(stanzaId, DeliveryStatus.SENT, true)
                    Log.i("XmppRepository", "Sent 1-on-1 OMEMO encrypted stanza to $bareTargetJid (id=$stanzaId)")
                    SendMessageOutcome.Sent
                } catch (e: Exception) {
                    val reason = "Gagal mengirim stanza OMEMO: ${e.message ?: "kesalahan jaringan"}"
                    Log.e("XmppRepository", "Failed sending OMEMO stanza to $bareTargetJid: $reason", e)
                    database.messageDao().updateMessageFailure(
                        stanzaId,
                        DeliveryStatus.FAILED,
                        false,
                        "$text\n⚠️ Gagal OMEMO: $reason"
                    )
                    database.roomDao().updateLastMessage(cleanJid, "⚠️ Gagal OMEMO: $text", timestamp, 0)
                    SendMessageOutcome.Failed(reason, stanzaId)
                }
            } else {
                val ex = encRes.exceptionOrNull()
                val reason = ex?.message ?: "Gagal mengenkripsi pesan OMEMO."
                Log.w("XmppRepository", "OMEMO encryption blocked/failed for $bareTargetJid (NO plaintext fallback): $reason")
                database.messageDao().updateMessageFailure(
                    stanzaId,
                    DeliveryStatus.FAILED,
                    false,
                    "$text\n⚠️ Gagal OMEMO: $reason"
                )
                database.roomDao().updateLastMessage(cleanJid, "⚠️ Gagal OMEMO: $text", timestamp, 0)
                return@withContext if (ex is OmemoUnsupportedContactException) {
                    SendMessageOutcome.OmemoUnsupported(reason, stanzaId)
                } else {
                    SendMessageOutcome.Failed(reason, stanzaId)
                }
            }
        }

        var isSentSuccessfully = false

        // 4. Send via Smack MUC if joined room
        if (!isSentSuccessfully && smackConnectionManager.isConnected() && smackMucManager.isRoomJoined(cleanJid)) {
            val sendRes = smackMucManager.sendMessage(
                roomJid = cleanJid,
                body = textToSend,
                stanzaId = stanzaId,
                oobUrl = if (isUrlFinal && isStandaloneUrl) textToSend.trim() else null
            )
            isSentSuccessfully = sendRes.isSuccess
            if (!isSentSuccessfully) {
                Log.w("XmppRepository", "Failed to send MUC message to $cleanJid: ${sendRes.exceptionOrNull()?.message}")
            }
        }

        // 4. Direct 1-on-1 XMPP Chat Stanza (RFC 6121) — hanya bila OMEMO tidak aktif atau pengguna eksplisit memilih kirim tanpa enkripsi untuk pesan ini
        if (!isSentSuccessfully && smackConnectionManager.isConnected() && smackConnectionManager.isAuthenticated()) {
            try {
                val conn = smackConnectionManager.getRawConnection()
                if (conn != null) {
                    val targetJid = org.jxmpp.jid.impl.JidCreate.from(bareTargetJid)
                    val msgStanza = org.jivesoftware.smack.packet.Message(targetJid, org.jivesoftware.smack.packet.Message.Type.chat).apply {
                        body = textToSend
                        setStanzaId(stanzaId)
                        if (isUrlFinal) {
                            val oob = org.jivesoftware.smack.packet.StandardExtensionElement
                                .builder("x", "jabber:x:oob")
                                .addElement("url", textToSend.trim())
                                .build()
                            addExtension(oob)
                        }
                        if (!hasExtension(
                                org.jivesoftware.smackx.receipts.DeliveryReceiptRequest.ELEMENT,
                                org.jivesoftware.smackx.receipts.DeliveryReceipt.NAMESPACE
                            )
                        ) {
                            org.jivesoftware.smackx.receipts.DeliveryReceiptRequest.addTo(this)
                        }
                    }
                    conn.sendStanza(msgStanza)
                    isSentSuccessfully = true
                    Log.i("XmppRepository", "Sent 1-on-1 direct XMPP stanza to $bareTargetJid (id=$stanzaId, allowPlaintextOnce=$allowPlaintextOnce)")
                }
            } catch (e: Exception) {
                Log.w("XmppRepository", "Failed to send 1-on-1 direct stanza to $bareTargetJid: ${e.message}")
            }
        }

        // 5. Fallback to raw engine ONLY in demo mode (bila OMEMO tidak aktif)
        if (!isSentSuccessfully && currentAccount.value.isDemoMode) {
            try {
                if (isUrlFinal && !isGroup) {
                    engine.sendDirectOobMessage(cleanJid, textToSend, myNickname)
                } else {
                    engine.sendGroupMessage(cleanJid, textToSend, myNickname)
                }
                isSentSuccessfully = true
            } catch (e: Exception) {
                Log.e("XmppRepository", "Demo fallback send failed for $cleanJid: ${e.message}")
            }
        }

        // 6. Update delivery status (only if still SENDING so fast DELIVERED receipt is preserved)
        try {
            database.messageDao().updateMessageStatusIfSending(
                stanzaId,
                if (isSentSuccessfully) DeliveryStatus.SENT else DeliveryStatus.FAILED
            )
            if (!isSentSuccessfully && !currentAccount.value.isDemoMode) {
                checkAndReconnectIfNeeded()
            }
        } catch (e: Exception) {
            Log.e("XmppRepository", "Failed updating status for $stanzaId: ${e.message}")
        }

        return@withContext if (isSentSuccessfully) {
            SendMessageOutcome.Sent
        } else {
            SendMessageOutcome.Failed("Gagal mengirim pesan ke $cleanJid", stanzaId)
        }
    }

    suspend fun querySoftwareVersion(targetJid: String): Result<com.ndregil.inchat.data.model.ClientCaps> {
        return smackConnectionManager.querySoftwareVersion(targetJid)
    }

    suspend fun loadVCard(targetJid: String): org.jivesoftware.smackx.vcardtemp.packet.VCard? {
        return smackConnectionManager.loadVCard(targetJid)
    }


    suspend fun sendWhisper(roomJid: String, targetNickname: String, text: String, myNickname: String) {
        var textToSend = text
        var targetLangCode: String? = null
        try {
            val prefs = translationPreferencesManager.translationPreferences.first()
            if (prefs.translateOutgoing && !com.ndregil.inchat.data.network.TranslationService.isStandaloneNonTranslatableLink(text)) {
                val translated = translationService.translateText(
                    text = text,
                    targetLangCode = prefs.targetLanguageCode,
                    skipHeuristics = true
                )
                if (!translated.isNullOrBlank()) {
                    textToSend = translated
                    targetLangCode = prefs.targetLanguageCode
                }
            }
        } catch (e: Exception) {
            Log.w("XmppRepository", "Failed to translate outgoing whisper: ${e.message}")
        }

        // If Smack MUC is connected and room joined, send whisper via Smack
        if (smackConnectionManager.isConnected() && smackMucManager.isRoomJoined(roomJid)) {
            try {
                smackMucManager.sendWhisper(roomJid, targetNickname, textToSend)
            } catch (e: Exception) {
                // Handled gracefully
            }
        }

        val myJid = currentAccount.value.jid

        val baseMsg = engine.sendPrivateWhisper(roomJid, targetNickname, text, myNickname).copy(avatarKey = myJid)
        val msg = if (textToSend != text && targetLangCode != null) {
            baseMsg.copy(translatedBody = textToSend, translationLanguage = targetLangCode)
        } else baseMsg
        database.messageDao().insertMessage(msg.toEntity())

        try {
            val existing = database.roomDao().getRoomByJid(roomJid)
            if (existing != null) {
                database.roomDao().updateLastMessage(
                    roomJid,
                    "[Bisikan ke @$targetNickname] $textToSend",
                    msg.timestamp,
                    0
                )
            } else {
                val newRoom = RoomEntity(
                    jid = roomJid,
                    name = roomJid.substringBefore("@"),
                    topic = "Ruangan MUC",
                    myNickname = myNickname,
                    occupantCount = 1,
                    unreadCount = 0,
                    lastMessageText = "[Bisikan ke @$targetNickname] $textToSend",
                    lastMessageTime = msg.timestamp,
                    isJoined = true,
                    isBookmarked = true,
                    myRole = OccupantRole.PARTICIPANT,
                    myAffiliation = OccupantAffiliation.MEMBER,
                    colorHex = randomColorForRoom(roomJid),
                    isAutoJoin = true,
                    isGroupChat = true
                )
                database.roomDao().insertOrUpdateRoom(newRoom)
            }
        } catch (e: Exception) {
            Log.e("XmppRepository", "Failed to update room last message for whisper: ${e.message}")
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
        myNickname: String,
        description: String = "",
        allowPlaintextOnce: Boolean = false
    ): SendMessageOutcome = withContext(Dispatchers.IO) {
        val rawCleanJid = roomJid.trim()
        if (rawCleanJid.isBlank()) return@withContext SendMessageOutcome.Failed("JID tujuan kosong.", null)
        val cleanJid = database.roomDao().getRoomByJid(rawCleanJid)?.jid ?: rawCleanJid
        val isGroup = isMucJid(cleanJid) || smackMucManager.isRoomJoined(cleanJid)
        val bareTargetJid = if (!isGroup) omemoEncryptionManager.toBareJid(cleanJid) else cleanJid
        val isOmemoActive = !isGroup && !allowPlaintextOnce && omemoEncryptionManager.isOmemoEnabledFor(bareTargetJid)

        val stanzaId = "file_${java.util.UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        val myJidFallback = currentAccount.value.jid
        val displayNickFallback = myNickname.ifBlank {
            currentAccount.value.nickname.ifBlank { currentAccount.value.username }
        }

        suspend fun recordFailedFileMessage(reason: String) {
            val trimmedDesc = description.trim()
            val label = if (trimmedDesc.isNotBlank()) "[Berkas] $fileName: $trimmedDesc" else "[Berkas] $fileName"
            val failedMsg = XmppMessage(
                id = stanzaId,
                roomJid = cleanJid,
                senderJid = myJidFallback,
                senderNickname = displayNickFallback,
                avatarKey = myJidFallback,
                body = "$label\n⚠️ Gagal OMEMO: $reason",
                timestamp = now,
                isMine = true,
                messageType = MessageType.FILE,
                status = DeliveryStatus.FAILED,
                fileName = fileName,
                fileMimeType = mimeType,
                fileSize = fileSize,
                isEncrypted = false
            )
            incomingMessageMutex.withLock {
                database.messageDao().insertMessage(failedMsg.toEntity())
                val existingRoom = database.roomDao().getRoomByJid(cleanJid)
                if (existingRoom != null) {
                    database.roomDao().updateLastMessage(cleanJid, "⚠️ Gagal OMEMO: [Berkas] $fileName", now, 0)
                }
            }
        }

        if (!smackConnectionManager.isConnected()) {
            val reason = "Tidak terhubung ke server XMPP."
            Log.e("XmppRepository", "Gagal upload: $reason")
            if (isOmemoActive) {
                recordFailedFileMessage(reason)
            } else {
                _autoJoinEvents.emit("Gagal mengirim berkas: tidak terhubung ke server XMPP.")
            }
            return@withContext SendMessageOutcome.Failed(reason, if (isOmemoActive) stanzaId else null)
        }

        val connection = smackConnectionManager.getRawConnection()
        if (connection == null || !connection.isAuthenticated) {
            val reason = "Sesi XMPP belum terautentikasi."
            if (isOmemoActive) {
                recordFailedFileMessage(reason)
            } else {
                _autoJoinEvents.emit("Gagal mengirim berkas: sesi XMPP belum terautentikasi.")
            }
            return@withContext SendMessageOutcome.Failed(reason, if (isOmemoActive) stanzaId else null)
        }

        // Jalur ketat OMEMO: periksa perangkat OMEMO kontak SEBELUM mengunggah berkas agar berkas tidak diunggah/dikirim polos
        if (isOmemoActive) {
            when (val support = omemoEncryptionManager.checkOmemoSupport(bareTargetJid, forceRefresh = true)) {
                is OmemoSupportResult.Unsupported -> {
                    recordFailedFileMessage(support.reason)
                    return@withContext SendMessageOutcome.OmemoUnsupported(support.reason, stanzaId)
                }
                is OmemoSupportResult.Error -> {
                    recordFailedFileMessage(support.reason)
                    _autoJoinEvents.emit("Berkas tidak dikirim (OMEMO): ${support.reason}")
                    return@withContext SendMessageOutcome.Failed(support.reason, stanzaId)
                }
                is OmemoSupportResult.Supported -> Unit
            }
        }

        _isUploadingFile.value = true
        try {
            val uploadResult = smackFileUploadManager.uploadFileDetailed(
                connection = connection,
                uri = uri,
                fileName = fileName,
                fileSize = fileSize,
                mimeType = mimeType
            )

            uploadResult.fold(
                onSuccess = { uploaded ->
                    val url = uploaded.url
                    val trimmedDesc = description.trim()
                    val bodyText = if (trimmedDesc.isNotBlank()) "$trimmedDesc\n$url" else url
                    val myJid = connection.user?.asBareJid()?.toString() ?: myJidFallback
                    val displayNick = displayNickFallback

                    val msg = XmppMessage(
                        id = stanzaId,
                        roomJid = cleanJid,
                        senderJid = myJid,
                        senderNickname = displayNick,
                        avatarKey = myJid,
                        body = bodyText,
                        timestamp = now,
                        isMine = true,
                        messageType = MessageType.FILE,
                        status = DeliveryStatus.SENDING,
                        fileUrl = url,
                        fileName = uploaded.fileName,
                        fileMimeType = uploaded.mimeType,
                        fileSize = uploaded.fileSize,
                        isEncrypted = false
                    )

                    val previewText = if (trimmedDesc.isNotBlank()) {
                        "[Berkas] ${uploaded.fileName}: $trimmedDesc"
                    } else {
                        "[Berkas] ${uploaded.fileName}"
                    }

                    incomingMessageMutex.withLock {
                        database.messageDao().insertMessage(msg.toEntity())
                        val existingRoom = database.roomDao().getRoomByJid(cleanJid)
                        if (existingRoom != null) {
                            database.roomDao().updateLastMessage(cleanJid, previewText, now, 0)
                        } else {
                            database.roomDao().insertOrUpdateRoom(
                                RoomEntity(
                                    jid = cleanJid,
                                    name = cleanJid.substringBefore("@"),
                                    topic = if (isGroup) "Ruangan MUC" else "Obrolan Langsung (1-on-1)",
                                    myNickname = displayNick,
                                    occupantCount = if (isGroup) 1 else 2,
                                    unreadCount = 0,
                                    lastMessageText = previewText,
                                    lastMessageTime = now,
                                    isJoined = isGroup,
                                    isBookmarked = false,
                                    myRole = if (isGroup) OccupantRole.PARTICIPANT else OccupantRole.MEMBER,
                                    myAffiliation = OccupantAffiliation.MEMBER,
                                    colorHex = randomColorForRoom(cleanJid),
                                    isAutoJoin = isGroup,
                                    isGroupChat = isGroup
                                )
                            )
                        }
                    }

                    if (isOmemoActive && !isGroup) {
                        val encRes = omemoEncryptionManager.encryptDirectMessage(
                            targetBareJidStr = bareTargetJid,
                            plaintext = bodyText,
                            stanzaId = stanzaId,
                            oobUrl = null
                        )
                        if (encRes.isSuccess) {
                            try {
                                connection.sendStanza(encRes.getOrNull()!!)
                                database.messageDao().updateMessageStatusAndEncrypted(stanzaId, DeliveryStatus.SENT, true)
                                return@fold SendMessageOutcome.Sent
                            } catch (e: Exception) {
                                val reason = "Gagal mengirim berkas OMEMO: ${e.message ?: "kesalahan jaringan"}"
                                database.messageDao().updateMessageFailure(
                                    stanzaId,
                                    DeliveryStatus.FAILED,
                                    false,
                                    "$bodyText\n⚠️ Gagal OMEMO: $reason"
                                )
                                return@fold SendMessageOutcome.Failed(reason, stanzaId)
                            }
                        } else {
                            val ex = encRes.exceptionOrNull()
                            val reason = ex?.message ?: "Gagal mengenkripsi berkas dengan OMEMO."
                            database.messageDao().updateMessageFailure(
                                stanzaId,
                                DeliveryStatus.FAILED,
                                false,
                                "$bodyText\n⚠️ Gagal OMEMO: $reason"
                            )
                            return@fold if (ex is OmemoUnsupportedContactException) {
                                SendMessageOutcome.OmemoUnsupported(reason, stanzaId)
                            } else {
                                _autoJoinEvents.emit("Gagal OMEMO: $reason")
                                SendMessageOutcome.Failed(reason, stanzaId)
                            }
                        }
                    }

                    var sentSuccessfully = false
                    var failureReason = "Gagal mengirim berkas."
                    if (smackMucManager.isRoomJoined(cleanJid)) {
                        val sendRes = smackMucManager.sendMessage(
                            roomJid = cleanJid,
                            body = bodyText,
                            stanzaId = stanzaId,
                            oobUrl = url
                        )
                        sentSuccessfully = sendRes.isSuccess
                        if (!sentSuccessfully) {
                            failureReason = sendRes.exceptionOrNull()?.message ?: "Gagal mengirim ke room"
                            Log.e("XmppRepository", "Gagal mengirim URL file ke MUC: $failureReason")
                            _autoJoinEvents.emit("Berkas terunggah, namun gagal dikirim ke room: $failureReason")
                        }
                    } else if (!isMucJid(cleanJid)) {
                        try {
                            val targetJid = org.jxmpp.jid.impl.JidCreate.from(bareTargetJid)
                            val directMsg = org.jivesoftware.smack.packet.Message(
                                targetJid,
                                org.jivesoftware.smack.packet.Message.Type.chat
                            ).apply {
                                body = bodyText
                                setStanzaId(stanzaId)
                                addExtension(
                                    org.jivesoftware.smack.packet.StandardExtensionElement
                                        .builder("x", "jabber:x:oob")
                                        .addElement("url", url)
                                        .build()
                                )
                                org.jivesoftware.smackx.receipts.DeliveryReceiptRequest.addTo(this)
                            }
                            connection.sendStanza(directMsg)
                            sentSuccessfully = true
                        } catch (e: Exception) {
                            failureReason = e.localizedMessage ?: "Kesalahan tidak diketahui"
                            Log.e("XmppRepository", "Gagal mengirim URL file ke kontak: ${e.message}")
                            _autoJoinEvents.emit("Gagal mengirim berkas ke kontak: $failureReason")
                        }
                    } else {
                        failureReason = "Anda belum bergabung ke room $cleanJid."
                        _autoJoinEvents.emit("Berkas terunggah, tetapi Anda belum bergabung ke room $cleanJid.")
                    }

                    database.messageDao().updateMessageStatusIfSending(
                        stanzaId,
                        if (sentSuccessfully) DeliveryStatus.SENT else DeliveryStatus.FAILED
                    )
                    if (sentSuccessfully) SendMessageOutcome.Sent else SendMessageOutcome.Failed(failureReason, stanzaId)
                },
                onFailure = { error ->
                    val errMsg = error.localizedMessage ?: "Kesalahan tidak diketahui"
                    Log.e("XmppRepository", "XEP-0363 Upload failed: $errMsg", error)
                    if (isOmemoActive) {
                        recordFailedFileMessage("Gagal mengunggah berkas: $errMsg")
                    } else {
                        _autoJoinEvents.emit("Gagal mengunggah berkas: $errMsg")
                    }
                    SendMessageOutcome.Failed("Gagal mengunggah berkas: $errMsg", if (isOmemoActive) stanzaId else null)
                }
            )
        } finally {
            _isUploadingFile.value = false
        }
    }

    suspend fun sendVoiceMessage(
        roomJid: String,
        audioFile: java.io.File,
        durationSeconds: Int,
        myNickname: String,
        allowPlaintextOnce: Boolean = false
    ): SendMessageOutcome = withContext(Dispatchers.IO) {
        val rawCleanJid = roomJid.trim()
        val cleanJid = database.roomDao().getRoomByJid(rawCleanJid)?.jid ?: rawCleanJid
        val isGroup = isMucJid(cleanJid) || smackMucManager.isRoomJoined(cleanJid)
        val bareTargetJid = if (!isGroup) omemoEncryptionManager.toBareJid(cleanJid) else cleanJid
        val isOmemoActive = !isGroup && !allowPlaintextOnce && omemoEncryptionManager.isOmemoEnabledFor(bareTargetJid)

        val fileName = audioFile.name
        val fileSize = audioFile.length()
        val mimeType = "audio/mp4"
        val msgId = "voice_${java.util.UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        val myJidFallback = currentAccount.value.jid

        suspend fun insertFailedVoice(reason: String, url: String = "file://${audioFile.absolutePath}") {
            val baseLabel = "🎤 Pesan Suara (${durationSeconds}d)"
            val failedMsg = XmppMessage(
                id = msgId,
                roomJid = cleanJid,
                senderJid = myJidFallback,
                senderNickname = myNickname,
                avatarKey = myJidFallback,
                body = "$baseLabel\n⚠️ Gagal OMEMO: $reason",
                timestamp = now,
                isMine = true,
                messageType = MessageType.VOICE,
                status = DeliveryStatus.FAILED,
                fileUrl = url,
                fileName = fileName,
                fileMimeType = mimeType,
                fileSize = fileSize,
                voiceDurationSeconds = durationSeconds,
                isEncrypted = false
            )
            incomingMessageMutex.withLock {
                database.messageDao().insertMessage(failedMsg.toEntity())
                database.roomDao().updateLastMessage(cleanJid, "⚠️ Gagal OMEMO: [Pesan Suara]", now, 0)
            }
        }

        val connection = if (smackConnectionManager.isConnected() && smackConnectionManager.isAuthenticated()) {
            smackConnectionManager.getRawConnection()
        } else null

        if (isOmemoActive) {
            if (connection == null) {
                val reason = "Koneksi XMPP terputus. Pesan suara OMEMO tidak dikirim."
                insertFailedVoice(reason)
                return@withContext SendMessageOutcome.Failed(reason, msgId)
            }
            when (val support = omemoEncryptionManager.checkOmemoSupport(bareTargetJid, forceRefresh = true)) {
                is OmemoSupportResult.Unsupported -> {
                    insertFailedVoice(support.reason)
                    return@withContext SendMessageOutcome.OmemoUnsupported(support.reason, msgId)
                }
                is OmemoSupportResult.Error -> {
                    insertFailedVoice(support.reason)
                    _autoJoinEvents.emit("Pesan suara tidak dikirim (OMEMO): ${support.reason}")
                    return@withContext SendMessageOutcome.Failed(support.reason, msgId)
                }
                is OmemoSupportResult.Supported -> Unit
            }
        }

        var finalUrl = "file://${audioFile.absolutePath}"
        var uploadFailedReason: String? = null
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
                uploadFailedReason = it.message ?: "Gagal mengunggah pesan suara"
                Log.w("XmppRepository", "XEP-0363 voice upload fallback to local URI: ${it.message}")
            }
        }

        if (isOmemoActive && !finalUrl.startsWith("http", ignoreCase = true)) {
            val reason = uploadFailedReason ?: "Gagal mengunggah pesan suara ke server XEP-0363."
            insertFailedVoice(reason)
            _autoJoinEvents.emit("Pesan suara tidak dikirim (OMEMO): $reason")
            return@withContext SendMessageOutcome.Failed(reason, msgId)
        }

        val body = "🎤 Pesan Suara (${durationSeconds}d)\n$finalUrl"
        val myJid = connection?.user?.asBareJid()?.toString() ?: "$cleanJid/$myNickname"

        val msg = XmppMessage(
            id = msgId,
            roomJid = cleanJid,
            senderJid = myJid,
            senderNickname = myNickname,
            avatarKey = myJid,
            body = body,
            timestamp = now,
            isMine = true,
            messageType = MessageType.VOICE,
            status = DeliveryStatus.SENDING,
            fileUrl = finalUrl,
            fileName = fileName,
            fileMimeType = mimeType,
            fileSize = fileSize,
            voiceDurationSeconds = durationSeconds,
            isEncrypted = false
        )

        incomingMessageMutex.withLock {
            database.messageDao().insertMessage(msg.toEntity())
            database.roomDao().updateLastMessage(cleanJid, "[Pesan Suara]", now, 0)
        }

        if (isOmemoActive && !isGroup) {
            if (connection == null || !connection.isAuthenticated) {
                val reason = "Koneksi XMPP terputus. Pesan suara OMEMO tidak dikirim."
                database.messageDao().updateMessageFailure(msgId, DeliveryStatus.FAILED, false, "$body\n⚠️ Gagal OMEMO: $reason")
                return@withContext SendMessageOutcome.Failed(reason, msgId)
            }
            val encRes = omemoEncryptionManager.encryptDirectMessage(
                targetBareJidStr = bareTargetJid,
                plaintext = body,
                stanzaId = msgId,
                oobUrl = null
            )
            return@withContext if (encRes.isSuccess) {
                try {
                    connection.sendStanza(encRes.getOrNull()!!)
                    database.messageDao().updateMessageStatusAndEncrypted(msgId, DeliveryStatus.SENT, true)
                    SendMessageOutcome.Sent
                } catch (e: Exception) {
                    val reason = "Gagal mengirim pesan suara OMEMO: ${e.message ?: "kesalahan jaringan"}"
                    database.messageDao().updateMessageFailure(msgId, DeliveryStatus.FAILED, false, "$body\n⚠️ Gagal OMEMO: $reason")
                    SendMessageOutcome.Failed(reason, msgId)
                }
            } else {
                val ex = encRes.exceptionOrNull()
                val reason = ex?.message ?: "Gagal mengenkripsi pesan suara OMEMO."
                database.messageDao().updateMessageFailure(msgId, DeliveryStatus.FAILED, false, "$body\n⚠️ Gagal OMEMO: $reason")
                if (ex is OmemoUnsupportedContactException) {
                    SendMessageOutcome.OmemoUnsupported(reason, msgId)
                } else {
                    _autoJoinEvents.emit("Gagal OMEMO: $reason")
                    SendMessageOutcome.Failed(reason, msgId)
                }
            }
        }

        var sentOk = false
        val oobHttpUrl = finalUrl.takeIf { it.startsWith("http", ignoreCase = true) }
        if (smackMucManager.isRoomJoined(cleanJid)) {
            val res = smackMucManager.sendMessage(
                roomJid = cleanJid,
                body = body,
                stanzaId = msgId,
                oobUrl = oobHttpUrl
            )
            sentOk = res.isSuccess
        } else if (!isMucJid(cleanJid) && connection != null && connection.isAuthenticated) {
            try {
                val targetJid = org.jxmpp.jid.impl.JidCreate.from(bareTargetJid)
                val directMsg = org.jivesoftware.smack.packet.Message(targetJid, org.jivesoftware.smack.packet.Message.Type.chat).apply {
                    this.body = body
                    setStanzaId(msgId)
                    if (oobHttpUrl != null) {
                        addExtension(
                            org.jivesoftware.smack.packet.StandardExtensionElement
                                .builder("x", "jabber:x:oob")
                                .addElement("url", oobHttpUrl)
                                .build()
                        )
                    }
                    org.jivesoftware.smackx.receipts.DeliveryReceiptRequest.addTo(this)
                }
                connection.sendStanza(directMsg)
                sentOk = true
            } catch (e: Exception) {
                Log.e("XmppRepository", "Gagal mengirim voice message ke kontak: ${e.message}")
            }
        }
        database.messageDao().updateMessageStatusIfSending(
            msgId,
            if (sentOk) DeliveryStatus.SENT else DeliveryStatus.FAILED
        )
        return@withContext if (sentOk) {
            SendMessageOutcome.Sent
        } else {
            SendMessageOutcome.Failed("Gagal mengirim pesan suara", msgId)
        }
    }

    suspend fun initiateCall(
        roomJid: String,
        callType: String,
        myNickname: String,
        targetNickname: String? = null
    ): Result<Unit> {
        if (!isMucJid(roomJid)) {
            val room = database.roomDao().getRoomByJid(roomJid)
            val contactName = room?.name?.takeIf { it.isNotBlank() } ?: roomJid.substringBefore("@")
            return initiate1on1Call(roomJid, contactName, callType)
        }

        if (!smackConnectionManager.isConnected()) {
            return Result.failure(IllegalStateException("Belum terhubung ke server XMPP."))
        }

        val normalizedLabel = if (callType.equals("Video", ignoreCase = true)) "Video" else "Audio"
        val targetJid = if (!targetNickname.isNullOrBlank()) "$roomJid/$targetNickname" else roomJid
        val callResult = smackCallManager.initiateCall(targetJid, targetNickname ?: roomJid.substringBefore("@"), normalizedLabel)

        if (callResult.isSuccess && smackMucManager.isRoomJoined(roomJid)) {
            val msgBody = if (!targetNickname.isNullOrBlank()) {
                "Memulai panggilan $normalizedLabel ke @$targetNickname..."
            } else {
                "Memulai panggilan $normalizedLabel grup..."
            }
            try {
                smackMucManager.sendMessage(roomJid, msgBody)
            } catch (_: Exception) {
            }
        }
        return callResult
    }

    suspend fun initiate1on1Call(targetJid: String, nickname: String, callType: String): Result<Unit> {
        if (!smackConnectionManager.isConnected()) {
            return Result.failure(IllegalStateException("Belum terhubung ke server XMPP."))
        }
        return smackCallManager.initiateCall(targetJid, nickname, callType)
    }

    suspend fun sendFileToContact(
        targetJid: String,
        uri: android.net.Uri,
        fileName: String? = null,
        fileSize: Long = 0L,
        mimeType: String? = null,
        description: String = "",
        allowPlaintextOnce: Boolean = false
    ): SendMessageOutcome = withContext(Dispatchers.IO) {
        val cleanTarget = targetJid.trim().substringBefore("/")
        if (cleanTarget.isBlank()) return@withContext SendMessageOutcome.Failed("JID kontak kosong.", null)
        val myNick = currentAccount.value.nickname.ifBlank {
            currentAccount.value.username.ifBlank { "User" }
        }
        val contactName = rosterContacts.value.find {
            it.jid.equals(cleanTarget, ignoreCase = true)
        }?.displayName ?: cleanTarget.substringBefore("@")

        ensureDirectChatRoom(
            contactJid = cleanTarget,
            displayName = contactName,
            myNickname = myNick
        )

        val meta = smackFileUploadManager.resolveUriMetadata(
            uri = uri,
            suggestedName = fileName,
            suggestedSize = fileSize,
            suggestedMimeType = mimeType
        )
        sendFile(
            roomJid = cleanTarget,
            uri = uri,
            fileName = meta.fileName,
            fileSize = meta.fileSize,
            mimeType = meta.mimeType,
            myNickname = myNick,
            description = description,
            allowPlaintextOnce = allowPlaintextOnce
        )
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

    suspend fun discoServer(targetJid: String, node: String? = null): ServerDiscoResult {
        return smackDiscoManager.discoServer(targetJid, node)
    }

    suspend fun loadRosterContacts(): List<RosterContact> {
        return smackRosterManager.loadContacts(forceReload = true).getOrElse { smackRosterManager.contacts.value }
    }

    suspend fun addRosterContact(jid: String, name: String, group: String? = null): Result<Unit> {
        return smackRosterManager.addContact(
            jidString = jid,
            name = name,
            groupName = group,
            isDemoMode = currentAccount.value.isDemoMode
        )
    }

    suspend fun resendSubscriptionRequest(jid: String): Result<Unit> {
        return smackRosterManager.sendSubscriptionRequest(jid)
    }

    suspend fun removeRosterContact(jid: String): Result<Unit> {
        val result = smackRosterManager.removeContact(jid)
        try {
            val bareJid = jid.trim().removePrefix("xmpp:").substringBefore("?").substringBefore("/")
            database.contactDao().deleteContact(bareJid)
            if (bareJid != jid.trim()) {
                database.contactDao().deleteContact(jid.trim())
            }
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

    // --- STORIES / STATUS (WhatsApp-style ephemeral updates) ---
    val stories: Flow<List<com.ndregil.inchat.data.model.UserStory>> = combine(
        database.storyDao().getActiveStories(System.currentTimeMillis() - 24 * 60 * 60 * 1000L),
        database.storyDao().getAllStoryViewers(),
        smackAvatarManager.avatarUpdates.onStart { emit("") }
    ) { entities, allViewers, _ ->
        entities.map { entity ->
            val avatar = smackAvatarManager.getAvatar(entity.senderJid)
            val viewers = allViewers.filter { it.storyId == entity.id }.map { it.toModel() }
            entity.toModel(avatar, viewers)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Sends a notification to the story owner that the user has viewed their status update.
     */
    suspend fun sendStoryViewedNotification(story: com.ndregil.inchat.data.model.UserStory) = withContext(Dispatchers.IO) {
        try {
            val conn = smackConnectionManager.getRawConnection() ?: return@withContext
            if (!smackConnectionManager.isConnected()) return@withContext

            val myJid = currentAccount.value.jid
            val myNick = currentAccount.value.nickname.ifBlank { myJid.substringBefore("@") }

            val viewJson = org.json.JSONObject().apply {
                put("storyId", story.id)
                put("viewerJid", myJid)
                put("viewerName", myNick)
                put("timestamp", System.currentTimeMillis())
            }.toString()

            val msg = org.jivesoftware.smack.packet.Message(
                org.jxmpp.jid.impl.JidCreate.from(story.senderJid),
                org.jivesoftware.smack.packet.Message.Type.chat
            ).apply {
                subject = "INCHAT_STORY_VIEW"
                body = "[STORY_VIEWED] $viewJson"
            }
            conn.sendStanza(msg)
            Log.i("XmppRepository", "Sent story viewed notification to ${story.senderJid} for story ${story.id}")
        } catch (e: Exception) {
            Log.w("XmppRepository", "Failed to send story viewed notification: ${e.message}")
        }
    }

    suspend fun publishStory(
        text: String,
        mediaUri: android.net.Uri? = null,
        backgroundColorHex: String = "#0284C7"
    ): Result<com.ndregil.inchat.data.model.UserStory> = withContext(Dispatchers.IO) {
        try {
            val myJid = currentAccount.value.jid
            val myNick = currentAccount.value.nickname.ifBlank { myJid.substringBefore("@") }
            val storyId = java.util.UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            var mediaUrl: String? = null
            var mediaType = com.ndregil.inchat.data.model.StoryMediaType.TEXT

            if (mediaUri != null) {
                // 1. Always copy to persistent internal app storage so it can be viewed locally even if offline or if server upload fails/lacks XEP-0363
                try {
                    val storiesDir = java.io.File(context.filesDir, "stories").apply { mkdirs() }
                    val localFile = java.io.File(storiesDir, "story_${storyId}.jpg")
                    context.contentResolver.openInputStream(mediaUri)?.use { input ->
                        localFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (localFile.exists() && localFile.length() > 0) {
                        mediaUrl = localFile.absolutePath
                        mediaType = com.ndregil.inchat.data.model.StoryMediaType.IMAGE
                        Log.i("XmppRepository", "Saved local story image to: ${localFile.absolutePath} (${localFile.length()} bytes)")
                    }
                } catch (e: Exception) {
                    Log.w("XmppRepository", "Failed saving local story image copy: ${e.message}")
                }

                // 2. Try uploading via XEP-0363 HTTP File Upload so remote contacts can view it
                try {
                    val conn = smackConnectionManager.getRawConnection()
                    if (conn != null && smackConnectionManager.isConnected()) {
                        val fileName = "story_${storyId}.jpg"
                        val localFile = java.io.File(context.filesDir, "stories/story_${storyId}.jpg")
                        val fileSize = if (localFile.exists()) localFile.length() else 1024L
                        val uploadResult = smackFileUploadManager.uploadFile(conn, mediaUri, fileName, fileSize, "image/jpeg")
                        val remoteUrl = uploadResult.getOrNull()
                        if (!remoteUrl.isNullOrBlank()) {
                            mediaUrl = remoteUrl
                            mediaType = com.ndregil.inchat.data.model.StoryMediaType.IMAGE
                            Log.i("XmppRepository", "Uploaded story image via XEP-0363: $remoteUrl")
                        }
                    }
                } catch (e: Exception) {
                    Log.w("XmppRepository", "XEP-0363 remote upload for story failed or not supported: ${e.message}")
                }

                // If mediaUrl is still set (either local or remote), it is an IMAGE story
                if (mediaUrl != null) {
                    mediaType = com.ndregil.inchat.data.model.StoryMediaType.IMAGE
                }
            }

            val story = com.ndregil.inchat.data.model.UserStory(
                id = storyId,
                senderJid = myJid,
                senderName = myNick,
                text = text,
                mediaUrl = mediaUrl,
                mediaType = mediaType,
                backgroundColorHex = backgroundColorHex,
                timestamp = now,
                isMine = true,
                isViewed = true
            )

            // Save to local database
            database.storyDao().insertOrUpdateStory(
                com.ndregil.inchat.data.local.entity.StoryEntity.fromModel(story)
            )

            // Also broadcast presence status message if text is present
            if (text.isNotBlank()) {
                updateUserPresence(userPresence.value, text)
            }

            // Broadcast story announcement to online roster contacts via XMPP Message
            val conn = smackConnectionManager.getRawConnection()
            if (conn != null && smackConnectionManager.isConnected()) {
                val storyJson = org.json.JSONObject().apply {
                    put("id", storyId)
                    put("senderName", myNick)
                    put("text", text)
                    if (mediaUrl != null) put("mediaUrl", mediaUrl)
                    put("mediaType", mediaType.name)
                    put("bgColor", backgroundColorHex)
                    put("timestamp", now)
                }.toString()

                val contacts = rosterContacts.value
                contacts.forEach { contact ->
                    try {
                        val msg = org.jivesoftware.smack.packet.Message(
                            org.jxmpp.jid.impl.JidCreate.from(contact.jid),
                            org.jivesoftware.smack.packet.Message.Type.chat
                        )
                        msg.subject = "INCHAT_STORY"
                        msg.body = "[STORY_UPDATE]$storyJson"
                        conn.sendStanza(msg)
                    } catch (e: Exception) {
                        Log.w("XmppRepository", "Could not send story to ${contact.jid}: ${e.message}")
                    }
                }
            }

            Result.success(story)
        } catch (e: Exception) {
            Log.e("XmppRepository", "Failed to publish story: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun markStoryViewed(storyId: String) {
        database.storyDao().markStoryViewed(storyId)
    }

    suspend fun deleteStory(storyId: String) {
        database.storyDao().deleteStory(storyId)
    }

    suspend fun rejectSubscriptionRequest(jid: String): Result<Unit> {
        return smackRosterManager.rejectSubscriptionRequest(jid)
    }

    fun getContactPresence(jid: String): UserPresence {
        return smackRosterManager.getContactPresence(jid)
    }

    fun refreshRoomOccupants(roomJid: String) {
        smackMucManager.refreshOccupants(roomJid)
        val liveCount = smackMucManager.getRoomOccupantsCount(roomJid)
        if (liveCount > 0) {
            scope.launch(Dispatchers.IO) {
                database.roomDao().updateOccupantCount(roomJid, liveCount)
            }
        }
    }

    suspend fun refreshAllRoomOccupantCounts() = withContext(Dispatchers.IO) {
        try {
            val rooms = database.roomDao().getAllRoomsList().filter { it.isGroupChat }
            for (room in rooms) {
                val liveCount = smackMucManager.getRoomOccupantsCount(room.jid)
                if (liveCount > 0) {
                    database.roomDao().updateOccupantCount(room.jid, liveCount)
                } else if (smackConnectionManager.isConnected()) {
                    val queried = smackMucManager.queryRoomOccupantsCount(room.jid)
                    if (queried > 0) {
                        database.roomDao().updateOccupantCount(room.jid, queried)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("XmppRepository", "Error refreshing room occupant counts: ${e.message}")
        }
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

    fun isMucJid(jid: String): Boolean {
        val domain = jid.substringAfter("@", "")
        return domain.contains("conference", true) || 
               domain.contains("muc.", true) || 
               domain.contains("chat.", true) ||
               domain.contains("rooms.", true)
    }

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
    wallpaperStyle = wallpaperStyle,
    isGroupChat = isGroupChat,
    password = password
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
    voiceDurationSeconds = voiceDurationSeconds,
    translatedBody = translatedBody,
    translationLanguage = translationLanguage,
    isEncrypted = isEncrypted
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
    voiceDurationSeconds = voiceDurationSeconds,
    translatedBody = translatedBody,
    translationLanguage = translationLanguage,
    isEncrypted = isEncrypted
)
