package com.ndregil.inchat.data.network

import android.util.Log
import com.ndregil.inchat.data.model.DeliveryStatus
import com.ndregil.inchat.data.model.MessageType
import com.ndregil.inchat.data.model.OccupantAffiliation
import com.ndregil.inchat.data.model.OccupantRole
import com.ndregil.inchat.data.model.PublicDirectoryRoom
import com.ndregil.inchat.data.model.RoomOccupant
import com.ndregil.inchat.data.model.UserPresence
import com.ndregil.inchat.data.model.XmppMessage
import com.ndregil.inchat.ui.i18n.AppStrings
import com.ndregil.inchat.ui.i18n.getAppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.jivesoftware.smack.MessageListener
import org.jivesoftware.smack.PresenceListener
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.packet.Presence
import org.jivesoftware.smack.packet.PresenceBuilder
import org.jivesoftware.smackx.muc.MUCAffiliation
import org.jivesoftware.smackx.muc.MUCRole
import org.jivesoftware.smackx.muc.MultiUserChat
import org.jivesoftware.smackx.muc.MultiUserChatManager as SmackMultiUserChatManager
import org.jivesoftware.smackx.muc.Occupant
import org.jivesoftware.smackx.muc.ParticipantStatusListener
import org.jivesoftware.smackx.muc.RoomInfo
import org.jivesoftware.smackx.muc.SubjectUpdatedListener
import org.jivesoftware.smackx.bookmarks.BookmarkManager
import org.jivesoftware.smackx.bookmarks.BookmarkedConference
import org.jxmpp.jid.EntityBareJid
import org.jxmpp.jid.EntityFullJid
import org.jxmpp.jid.Jid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.jid.parts.Resourcepart
import org.jivesoftware.smack.packet.StanzaError
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.jivesoftware.smackx.mam.MamManager
import org.jivesoftware.smackx.mam.MamManager.MamQuery
import java.util.Date

/**
 * Manager class responsible for handling Multi-User Chat (MUC) operations:
 * - Joining existing MUC rooms with nickname and enter configuration
 * - Creating and configuring new MUC rooms (XEP-0045)
 * - Leaving MUC rooms
 * - Configuring room options (title, topic, persistence, password)
 * - Sending groupchat messages and private whispers
 * - Managing occupant rosters and presence in rooms
 * - Updating and listening to room subject/topic changes
 * - Discovering hosted public MUC rooms
 */
private val Jid.resourceOrNull: Resourcepart?
    get() = (this as? org.jxmpp.jid.FullJid)?.resourcepart

class MultiUserChatManager(
    private val connectionManager: XmppConnectionManager,
    private val avatarManager: XmppAvatarManager,
    private val scope: kotlinx.coroutines.CoroutineScope
) {
    var activeLanguageCode: String = "system"

    private fun getCurrentStrings(): AppStrings {
        return getAppStrings(activeLanguageCode)
    }

    companion object {
        private const val TAG = "MultiUserChatManager"
        private const val DEFAULT_HISTORY_STANZAS = 100
    }

    // Active joined MUC instances mapped by Bare JID string
    private val activeMucs = ConcurrentHashMap<String, MultiUserChat>()

    // Connection instance each active MUC was joined on
    private val activeMucConnections = ConcurrentHashMap<String, org.jivesoftware.smack.XMPPConnection>()

    // Nicknames used when entering rooms (mapped by room JID string)
    private val roomNicknames = ConcurrentHashMap<String, String>()

    // MessageListener instances registered per room
    private val roomMessageListeners = ConcurrentHashMap<String, MutableList<MessageListener>>()

    // ParticipantStatusListener instances registered per room
    private val roomParticipantListeners = ConcurrentHashMap<String, MutableList<ParticipantStatusListener>>()

    // SubjectUpdatedListener instances registered per room
    private val roomSubjectListeners = ConcurrentHashMap<String, MutableList<SubjectUpdatedListener>>()
    
    // PresenceListener instances registered per room (StanzaListeners)
    private val roomPresenceListeners = ConcurrentHashMap<String, MutableList<org.jivesoftware.smack.StanzaListener>>()

    // Per-room join lock to prevent concurrent joins attaching duplicate listeners
    private val joinMutexes = ConcurrentHashMap<String, Mutex>()

    // State flow of joined room JIDs
    private val _joinedRooms = MutableStateFlow<Set<String>>(emptySet())
    val joinedRooms: StateFlow<Set<String>> = _joinedRooms.asStateFlow()

    // Shared flow of incoming messages across all joined MUC rooms
    private val _incomingMessages = MutableSharedFlow<XmppMessage>(extraBufferCapacity = 128)
    val incomingMessages: SharedFlow<XmppMessage> = _incomingMessages.asSharedFlow()

    // State flow of occupants mapped by room JID string
    private val _roomOccupants = MutableStateFlow<Map<String, List<RoomOccupant>>>(emptyMap())
    val roomOccupants: StateFlow<Map<String, List<RoomOccupant>>> = _roomOccupants.asStateFlow()

    // Flow of room topic updates: Pair(roomJid, newTopic)
    private val _topicUpdates = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 32)
    val topicUpdates: SharedFlow<Pair<String, String>> = _topicUpdates.asSharedFlow()

    // State flow of bookmarks from the server (XEP-0048)
    private val _serverBookmarks = MutableStateFlow<List<BookmarkedConference>>(emptyList())
    val serverBookmarks: StateFlow<List<BookmarkedConference>> = _serverBookmarks.asStateFlow()

    // Cache of occupant presence states per room: roomJid -> (nickname -> Pair(UserPresence, statusText))
    private val roomOccupantPresenceCache = java.util.concurrent.ConcurrentHashMap<String, MutableMap<String, Pair<com.ndregil.inchat.data.model.UserPresence, String>>>()

    // Real-time occupant client caps cache per room: roomJid -> (nickname -> ClientCaps)
    private val roomOccupantCapsCache = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<String, com.ndregil.inchat.data.model.ClientCaps>>()

    private val updateJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    
    // Limit concurrent MAM archive queries to avoid overwhelming the connection/server
    private val mamSemaphore = kotlinx.coroutines.sync.Semaphore(2)

    init {
        // Listen for avatar updates to refresh UI automatically
        scope.launch {
            avatarManager.avatarUpdates
                .debounce(500)
                .collect { updatedBareJid ->
                    refreshRoomsWithJid(updatedBareJid)
                }
        }
    }

    private fun refreshRoomsWithJid(bareJid: String) {
        activeMucs.forEach { (roomJid, muc) ->
            // In MUC, real JIDs might not be visible unless you have high privilege
            // But we can still refresh the whole room list if any occupant might match
            updateOccupantsForRoom(roomJid, muc)
        }
    }

    /**
     * Helper to obtain the underlying Smack MultiUserChatManager.
     */
    private fun getSmackMucManager(): SmackMultiUserChatManager {
        val conn = connectionManager.getRawConnection()
            ?: throw IllegalStateException("XMPP connection is not established. Please connect first.")
        if (!conn.isAuthenticated) {
            throw IllegalStateException("XMPP user is not authenticated. Please log in first.")
        }
        return SmackMultiUserChatManager.getInstanceFor(conn)
    }

    /**
     * Joins an existing MUC room, or creates it if it does not yet exist.
     *
     * @param roomJid Bare JID of the room, e.g. "tech@conference.example.org"
     * @param nickname User's display nickname inside this MUC room
     * @param password Optional room password for password-protected rooms
     * @param maxHistoryStanzas Number of past messages to request upon entering
     */
    suspend fun joinRoom(
        roomJid: String,
        nickname: String,
        password: String? = null,
        maxHistoryStanzas: Int = DEFAULT_HISTORY_STANZAS,
        isAutoRejoin: Boolean = false
    ): Result<MultiUserChat> = withContext(Dispatchers.IO) {
        try {
            val bareJid = JidCreate.entityBareFrom(roomJid.trim())
            val norm = bareJid.toString().lowercase()
            val joinMutex = joinMutexes.computeIfAbsent(norm) { Mutex() }
            val effectiveHistoryStanzas = if (isAutoRejoin) 0 else maxHistoryStanzas

            joinMutex.withLock {
                val conn = connectionManager.getRawConnection()
                    ?: throw IllegalStateException("XMPP connection is not established. Please connect first.")
                if (!conn.isConnected || !conn.isAuthenticated) {
                    throw IllegalStateException("XMPP user is not authenticated. Please log in first.")
                }
                val smackMucManager = SmackMultiUserChatManager.getInstanceFor(conn)
                val nick = Resourcepart.from(nickname.trim().ifBlank { connectionManager.credentials.value.username.ifBlank { "User" } })
                val muc = smackMucManager.getMultiUserChat(bareJid)

                // Only consider joined if tracked for this exact authenticated connection AND Smack says joined
                val trackedConn = activeMucConnections[norm] ?: activeMucConnections[roomJid]
                val alreadyJoinedCurrentSession = trackedConn === conn &&
                    (activeMucs[norm] === muc || activeMucs[roomJid] === muc) &&
                    muc.isJoined

                if (alreadyJoinedCurrentSession) {
                    Log.d(TAG, "Already joined room $roomJid")
                    val actualNick = muc.nickname?.toString()?.takeIf { it.isNotBlank() } ?: nick.toString()
                    activeMucs[roomJid] = muc
                    activeMucs[norm] = muc
                    activeMucConnections[roomJid] = conn
                    activeMucConnections[norm] = conn
                    roomNicknames[roomJid] = actualNick
                    roomNicknames[norm] = actualNick
                    if (roomMessageListeners[norm].isNullOrEmpty() && roomMessageListeners[roomJid].isNullOrEmpty()) {
                        registerMucListeners(bareJid.toString(), muc, actualNick)
                    }
                    _joinedRooms.value = activeMucs.keys.toSet()
                    updateOccupantsForRoom(bareJid.toString(), muc)
                    avatarManager.getAvatar(roomJid)
                    return@withLock Result.success(muc)
                }

                // Reset stale Smack local state (myRoomJid != null from a dead session without SM resumption)
                // so muc.join() immediately sends join presence without waiting 30s in Smack's leaveSync().
                if (muc.isJoined) {
                    Log.w(TAG, "Detected stale muc.isJoined=true for $roomJid without active session; resetting local MUC state before re-join.")
                    resetSmackMucLocalState(muc)
                }

                // Build enter configuration
                val liveCapsVer = try {
                    connectionManager.getRawConnection()?.let {
                        org.jivesoftware.smackx.caps.EntityCapsManager.getInstanceFor(it)?.capsVersionAndHash
                    }
                } catch (_: Exception) { null }
                val capsNodeExt = org.jivesoftware.smackx.caps.packet.CapsExtension(
                    "http://ndregil.inchat",
                    liveCapsVer?.version ?: "kFwX23eY765qL90mN12aBcDeFgH=",
                    liveCapsVer?.hash ?: "sha-1"
                )
                fun buildEnterConfig(targetNick: Resourcepart) = muc.getEnterConfigurationBuilder(targetNick).apply {
                    withPresence { pb ->
                        pb.overrideExtension(capsNodeExt)
                    }
                    if (!password.isNullOrBlank()) {
                        withPassword(password)
                    }
                    if (effectiveHistoryStanzas > 0) {
                        requestMaxStanzasHistory(effectiveHistoryStanzas)
                    } else {
                        requestNoHistory()
                    }
                }.build()

                Log.i(TAG, "Joining MUC room $roomJid as '$nick' (historyStanzas=$effectiveHistoryStanzas, isAutoRejoin=$isAutoRejoin)...")
                var effectiveNick = nick
                try {
                    muc.join(buildEnterConfig(nick))
                } catch (e: org.jivesoftware.smack.XMPPException.XMPPErrorException) {
                    if (e.stanzaError?.condition == StanzaError.Condition.conflict) {
                        Log.w(TAG, "Nickname '$nick' conflict (409) in $roomJid, waiting 2500ms before retrying original nick...")
                        kotlinx.coroutines.delay(2500)
                        if (muc.isJoined) {
                            resetSmackMucLocalState(muc)
                        }
                        try {
                            muc.join(buildEnterConfig(nick))
                            Log.i(TAG, "Retry with original nick '$nick' succeeded in $roomJid.")
                        } catch (e2: org.jivesoftware.smack.XMPPException.XMPPErrorException) {
                            if (e2.stanzaError?.condition == StanzaError.Condition.conflict) {
                                val newNick = Resourcepart.from("$nick" + "_" + (1000..9999).random())
                                Log.w(TAG, "Original nick '$nick' still conflicted in $roomJid, retrying with unique suffix '$newNick'...")
                                if (muc.isJoined) {
                                    resetSmackMucLocalState(muc)
                                }
                                muc.join(buildEnterConfig(newNick))
                                effectiveNick = newNick
                            } else {
                                throw e2
                            }
                        }
                    } else {
                        throw e
                    }
                }

                val actualNick = muc.nickname?.toString()?.takeIf { it.isNotBlank() } ?: effectiveNick.toString()

                activeMucs[roomJid] = muc
                activeMucs[norm] = muc
                activeMucConnections[roomJid] = conn
                activeMucConnections[norm] = conn
                roomNicknames[roomJid] = actualNick
                roomNicknames[norm] = actualNick
                _joinedRooms.value = activeMucs.keys.toSet()

                // Register listeners after cleaning up any stale ones for this room
                registerMucListeners(bareJid.toString(), muc, actualNick)

                updateOccupantsForRoom(bareJid.toString(), muc)
                avatarManager.getAvatar(roomJid)
                Log.i(TAG, "Successfully joined room $roomJid as '$actualNick'")

                Result.success(muc)
            }
        } catch (e: org.jivesoftware.smackx.muc.MultiUserChatException.NotAMucServiceException) {
            Log.w(TAG, "Not a MUC service: $roomJid")
            Result.failure(Exception("Domain '${JidCreate.entityBareFrom(roomJid).domain}' tidak menyediakan layanan MUC (XEP-45). Jika ini adalah akun pribadi, gunakan 'Obrolan Langsung'.", e))
        } catch (e: org.jivesoftware.smack.SmackException.NoResponseException) {
            Log.w(TAG, "MUC join timeout: $roomJid")
            Result.failure(Exception("Server tidak merespon permintaan masuk ke room $roomJid (Timeout). Coba beberapa saat lagi atau periksa koneksi internet Anda.", e))
        } catch (e: org.jivesoftware.smack.XMPPException.XMPPErrorException) {
            val condition = e.stanzaError?.condition?.toString() ?: "unknown"
            val text = e.stanzaError?.descriptiveText ?: "Terjadi kesalahan pada server"
            Log.w(TAG, "MUC join error: $roomJid (condition: $condition)")
            val userMsg = when (condition) {
                "forbidden" -> "Anda tidak diizinkan masuk ke ruangan ini (Dilarang/Banned)."
                "registration-required" -> "Ruangan ini membutuhkan registrasi atau keanggotaan terlebih dahulu."
                "not-authorized" -> "Password ruangan salah atau diperlukan autentikasi tambahan."
                "conflict" -> "Nickname sudah digunakan. Silakan gunakan nickname lain."
                else -> "Gagal masuk ke room: $text ($condition)"
            }
            Result.failure(Exception(userMsg, e))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to join MUC room $roomJid: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Creates a new MUC room and configures it with initial settings.
     *
     * @param roomJid Bare JID for the new room (e.g. "newroom@conference.example.org")
     * @param nickname Creator's nickname inside the room
     * @param roomName Natural title / human-readable name of the room
     * @param topic Initial room description or subject
     * @param isPersistent Whether the room should persist after all users leave
     * @param isPublic Whether the room should be listed in public discovery
     * @param password Optional password protection for the room
     */
    suspend fun createAndConfigureRoom(
        roomJid: String,
        nickname: String,
        roomName: String? = null,
        topic: String? = null,
        isPersistent: Boolean = true,
        isPublic: Boolean = true,
        password: String? = null
    ): Result<MultiUserChat> = withContext(Dispatchers.IO) {
        try {
            val smackMucManager = getSmackMucManager()
            val bareJid = JidCreate.entityBareFrom(roomJid.trim())
            val nick = Resourcepart.from(nickname.trim().ifBlank { connectionManager.credentials.value.username.ifBlank { "User" } })

            val muc = smackMucManager.getMultiUserChat(bareJid)

            Log.i(TAG, "Creating new MUC room $roomJid with nickname '$nick'...")
            val createHandle = muc.create(nick)

            // Submit room configuration form to unlock room for other users
            try {
                val configForm = muc.configurationForm
                val answerForm = configForm.fillableForm

                if (!roomName.isNullOrBlank()) {
                    answerForm.setAnswer("muc#roomconfig_roomname", roomName)
                }
                if (!topic.isNullOrBlank()) {
                    answerForm.setAnswer("muc#roomconfig_roomdesc", topic)
                }

                answerForm.setAnswer("muc#roomconfig_persistentroom", isPersistent)
                answerForm.setAnswer("muc#roomconfig_publicroom", isPublic)

                if (!password.isNullOrBlank()) {
                    answerForm.setAnswer("muc#roomconfig_passwordprotectedroom", true)
                    answerForm.setAnswer("muc#roomconfig_roomsecret", password)
                }

                muc.sendConfigurationForm(answerForm)
                Log.i(TAG, "Configuration form submitted successfully for room $roomJid")
            } catch (formException: Exception) {
                Log.w(TAG, "Could not apply custom config form, falling back to instant room: ${formException.message}")
                try {
                    createHandle.makeInstant()
                } catch (e: Exception) {
                    Log.w(TAG, "Instant room fallback: ${e.message}")
                }
            }

            // Set initial topic if specified
            if (!topic.isNullOrBlank()) {
                try {
                    muc.changeSubject(topic)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set initial topic: ${e.message}")
                }
            }

            val norm = bareJid.toString().lowercase()
            val conn = connectionManager.getRawConnection()
            registerMucListeners(roomJid, muc, nick.toString())

            activeMucs[roomJid] = muc
            activeMucs[norm] = muc
            if (conn != null) {
                activeMucConnections[roomJid] = conn
                activeMucConnections[norm] = conn
            }
            roomNicknames[roomJid] = nick.toString()
            roomNicknames[norm] = nick.toString()
            _joinedRooms.value = activeMucs.keys.toSet()

            updateOccupantsForRoom(roomJid, muc)
            Log.i(TAG, "Room $roomJid successfully created and configured.")

            Result.success(muc)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create and configure room $roomJid: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Configures an existing MUC room (title, description, persistence, password).
     */
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
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val muc = getMultiUserChat(roomJid) ?: return@withContext Result.failure(
            IllegalStateException("Ruangan $roomJid belum dimasuki / tidak aktif.")
        )
        try {
            val configForm = muc.configurationForm
            val answerForm = configForm.fillableForm

            if (!roomName.isNullOrBlank()) {
                try { answerForm.setAnswer("muc#roomconfig_roomname", roomName) } catch (e: Exception) { Log.d(TAG, "roomname field skipped: ${e.message}") }
            }
            if (!description.isNullOrBlank()) {
                try { answerForm.setAnswer("muc#roomconfig_roomdesc", description) } catch (e: Exception) { Log.d(TAG, "roomdesc field skipped: ${e.message}") }
            }
            try { answerForm.setAnswer("muc#roomconfig_persistentroom", isPersistent) } catch (e: Exception) { Log.d(TAG, "persistentroom field skipped: ${e.message}") }
            try { answerForm.setAnswer("muc#roomconfig_publicroom", isPublic) } catch (e: Exception) { Log.d(TAG, "publicroom field skipped: ${e.message}") }
            try { answerForm.setAnswer("muc#roomconfig_membersonly", isMembersOnly) } catch (e: Exception) { Log.d(TAG, "membersonly field skipped: ${e.message}") }
            try { answerForm.setAnswer("muc#roomconfig_moderatedroom", isModerated) } catch (e: Exception) { Log.d(TAG, "moderatedroom field skipped: ${e.message}") }
            try { answerForm.setAnswer("muc#roomconfig_changesubject", allowSubjectChange) } catch (e: Exception) { Log.d(TAG, "changesubject field skipped: ${e.message}") }
            try { answerForm.setAnswer("muc#roomconfig_enablelogging", enableLogging) } catch (e: Exception) { Log.d(TAG, "enablelogging field skipped: ${e.message}") }
            try { answerForm.setAnswer("muc#roomconfig_allowinvites", allowInvites) } catch (e: Exception) { Log.d(TAG, "allowinvites field skipped: ${e.message}") }

            if (maxUsers != null) {
                try {
                    answerForm.setAnswer("muc#roomconfig_maxusers", maxUsers.toString())
                } catch (e: Exception) {
                    try {
                        answerForm.setAnswer("muc#roomconfig_maxusers", listOf(maxUsers.toString()))
                    } catch (e2: Exception) {
                        Log.d(TAG, "maxusers field skipped: ${e2.message}")
                    }
                }
            }

            if (!whois.isNullOrBlank()) {
                try {
                    answerForm.setAnswer("muc#roomconfig_whois", listOf(whois))
                } catch (e: Exception) {
                    try {
                        answerForm.setAnswer("muc#roomconfig_whois", whois)
                    } catch (e2: Exception) {
                        Log.d(TAG, "whois field skipped: ${e2.message}")
                    }
                }
            }

            if (!password.isNullOrBlank()) {
                try {
                    answerForm.setAnswer("muc#roomconfig_passwordprotectedroom", true)
                    answerForm.setAnswer("muc#roomconfig_roomsecret", password)
                } catch (e: Exception) { Log.d(TAG, "passwordprotectedroom field skipped: ${e.message}") }
            } else {
                try {
                    answerForm.setAnswer("muc#roomconfig_passwordprotectedroom", false)
                } catch (e: Exception) { Log.d(TAG, "passwordprotectedroom reset skipped: ${e.message}") }
            }

            muc.sendConfigurationForm(answerForm)

            if (!subject.isNullOrBlank()) {
                try {
                    muc.changeSubject(subject)
                    _topicUpdates.tryEmit(Pair(roomJid, subject))
                } catch (e: Exception) {
                    Log.w(TAG, "Could not update subject via XEP-0045 changeSubject: ${e.message}")
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure room $roomJid: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Leaves an active MUC room and cleans up listeners and occupant state.
     */
    suspend fun leaveRoom(roomJid: String): Result<Unit> = withContext(Dispatchers.IO) {
        val bareJidStr = try { JidCreate.entityBareFrom(roomJid.trim()).toString() } catch (_: Exception) { roomJid.trim() }
        val matchedKey = activeMucs.keys.find { it.equals(roomJid.trim(), ignoreCase = true) || it.equals(bareJidStr, ignoreCase = true) }
        val muc = matchedKey?.let { activeMucs[it] } ?: try {
            getSmackMucManager().getMultiUserChat(JidCreate.entityBareFrom(bareJidStr))
        } catch (_: Exception) { null }

        Log.i(TAG, "Leaving MUC room $roomJid (bare: $bareJidStr)...")

        // 1. Leave via Smack MUC instance
        try {
            if (muc != null && muc.isJoined) {
                muc.leave()
            }
        } catch (e: Exception) {
            Log.w(TAG, "muc.leave() error for $roomJid: ${e.message}")
        }

        // 2. Explicitly send unavailable presence (XEP-0045 Section 7.14) to guarantee room exit
        try {
            val conn = connectionManager.getRawConnection()
            if (conn != null && conn.isConnected) {
                val nick = (if (matchedKey != null) roomNicknames[matchedKey] else null)
                    ?: roomNicknames[roomJid]
                    ?: roomNicknames[bareJidStr]
                    ?: muc?.nickname?.toString()
                    ?: connectionManager.credentials.value.username
                if (nick.isNotBlank()) {
                    val fullRoomJid = JidCreate.entityFullFrom("$bareJidStr/$nick")
                    val unavailPresence = org.jivesoftware.smack.packet.PresenceBuilder.buildPresence()
                        .ofType(org.jivesoftware.smack.packet.Presence.Type.unavailable)
                        .to(fullRoomJid)
                        .build()
                    conn.sendStanza(unavailPresence)
                    Log.i(TAG, "Sent explicit unavailable presence to $fullRoomJid")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed sending explicit unavailable presence to $roomJid: ${e.message}")
        }

        // 3. Remove registered listeners
        val keysToRemove = setOfNotNull(roomJid, bareJidStr, bareJidStr.lowercase(), matchedKey)
        detachMucListeners(keysToRemove, muc)
        for (key in keysToRemove) {
            activeMucs.remove(key)
            activeMucConnections.remove(key)
            roomNicknames.remove(key)
            roomOccupantCapsCache.remove(key)
            roomOccupantPresenceCache.remove(key)
        }
        _joinedRooms.value = activeMucs.keys.toSet()

        // 4. Remove room from occupants map
        val currentOccupants = _roomOccupants.value.toMutableMap()
        currentOccupants.remove(roomJid)
        currentOccupants.remove(bareJidStr)
        if (matchedKey != null) currentOccupants.remove(matchedKey)
        _roomOccupants.value = currentOccupants

        // 5. Clear occupant caps cache for this room
        roomOccupantCapsCache.keys.filter { it.startsWith("$bareJidStr/") || it.startsWith("$roomJid/") }.forEach {
            roomOccupantCapsCache.remove(it)
        }

        Log.i(TAG, "Left room $roomJid successfully.")
        Result.success(Unit)
    }

    /**
     * Changes or updates the room topic / subject.
     */
    suspend fun changeSubject(roomJid: String, newTopic: String): Result<Unit> = withContext(Dispatchers.IO) {
        val muc = getMultiUserChat(roomJid) ?: return@withContext Result.failure(
            IllegalStateException("Room $roomJid is not joined.")
        )
        try {
            muc.changeSubject(newTopic)
            _topicUpdates.tryEmit(Pair(roomJid, newTopic))
            Result.success(Unit)
        } catch (e: org.jivesoftware.smack.SmackException.NoResponseException) {
            Log.w(TAG, "Timeout waiting for subject change confirmation for $roomJid, assuming it might have succeeded: ${e.message}")
            // Even on timeout, we can assume the request was sent; subject updates will be received via listeners if it succeeds
            _topicUpdates.tryEmit(Pair(roomJid, newTopic))
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change room subject for $roomJid: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Changes user nickname in a joined MUC room (XEP-0045 7.6).
     */
    suspend fun changeNickname(roomJid: String, newNickname: String): Result<Unit> = withContext(Dispatchers.IO) {
        val muc = getMultiUserChat(roomJid) ?: return@withContext Result.failure(
            IllegalStateException("Room $roomJid is not joined.")
        )
        try {
            val nick = Resourcepart.from(newNickname.trim())
            muc.changeNickname(nick)
            val norm = roomJid.trim().lowercase()
            roomNicknames[roomJid] = nick.toString()
            roomNicknames[norm] = nick.toString()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change nickname in $roomJid to '$newNickname': ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Sends a groupchat message to all participants in the MUC room.
     */
    suspend fun sendMessage(
        roomJid: String,
        body: String,
        stanzaId: String? = null,
        oobUrl: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val muc = getMultiUserChat(roomJid) ?: return@withContext Result.failure(
            IllegalStateException("Ruangan $roomJid belum dimasuki / tidak aktif.")
        )
        try {
            val message = muc.createMessage().apply {
                this.body = body
                if (!stanzaId.isNullOrBlank()) {
                    this.stanzaId = stanzaId
                }
                if (!oobUrl.isNullOrBlank()) {
                    addExtension(
                        org.jivesoftware.smack.packet.StandardExtensionElement
                            .builder("x", "jabber:x:oob")
                            .addElement("url", oobUrl.trim())
                            .build()
                    )
                }
                org.jivesoftware.smackx.receipts.DeliveryReceiptRequest.addTo(this)
            }
            muc.sendMessage(message)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message to $roomJid: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Sends a private whisper message to a specific occupant inside the MUC room (XEP-0045 7.5).
     */
    suspend fun sendWhisper(
        roomJid: String,
        targetNickname: String,
        body: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val conn = connectionManager.getRawConnection()
            ?: return@withContext Result.failure(IllegalStateException("No active XMPP connection."))

        try {
            val targetFullJid: EntityFullJid = JidCreate.entityFullFrom("$roomJid/$targetNickname")
            val message = Message(targetFullJid, Message.Type.chat).apply {
                this.body = body
                org.jivesoftware.smackx.receipts.DeliveryReceiptRequest.addTo(this)
            }
            conn.sendStanza(message)
            Log.d(TAG, "Sent whisper to $targetNickname in $roomJid: $body")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send whisper to $targetNickname in $roomJid: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Broadcasts updated presence to all active joined MUC rooms.
     */
    suspend fun updatePresenceInRooms(
        presence: UserPresence,
        statusMessage: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val conn = connectionManager.getRawConnection()
        if (conn == null || !conn.isAuthenticated) {
            return@withContext Result.success(Unit)
        }

        try {
            val presenceMode = when (presence) {
                UserPresence.ONLINE -> Presence.Mode.available
                UserPresence.CHAT -> Presence.Mode.chat
                UserPresence.AWAY -> Presence.Mode.away
                UserPresence.XA -> Presence.Mode.xa
                UserPresence.DND -> Presence.Mode.dnd
                UserPresence.OFFLINE -> null
            }

            val sentRooms = HashSet<String>()
            for ((roomJid, muc) in activeMucs) {
                val norm = roomJid.trim().lowercase()
                if (!sentRooms.add(norm)) continue
                if (isMucValidForCurrentSession(roomJid, muc)) {
                    val nick = roomNicknames[norm] ?: roomNicknames[roomJid] ?: conn.user?.resourceOrEmpty?.toString() ?: connectionManager.credentials.value.username.ifBlank { "User" }
                    val roomFullJid = JidCreate.entityFullFrom("$roomJid/$nick")
                    val pres = if (presence == UserPresence.OFFLINE) {
                        PresenceBuilder.buildPresence()
                            .ofType(Presence.Type.unavailable)
                            .to(roomFullJid)
                            .setStatus(statusMessage)
                            .build()
                    } else {
                        val builder = PresenceBuilder.buildPresence()
                            .ofType(Presence.Type.available)
                            .to(roomFullJid)
                            .setStatus(statusMessage)
                        if (presenceMode != null) {
                            builder.setMode(presenceMode)
                        }
                        builder.build()
                    }
                    conn.sendStanza(pres)
                    Log.d(TAG, "Broadcasted presence to MUC room $roomJid: $presence")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed updating presence in MUC rooms: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Queries detailed room information from the server (subject, occupants count, etc.).
     */
    suspend fun getRoomInfo(roomJid: String): Result<RoomInfo> = withContext(Dispatchers.IO) {
        try {
            val smackMucManager = getSmackMucManager()
            val bareJid = JidCreate.entityBareFrom(roomJid.trim())
            val info = smackMucManager.getRoomInfo(bareJid)
            Result.success(info)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get room info for $roomJid: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Discovers hosted public MUC rooms from a conference service domain.
     * Defaults to "conference.<domain>" if domain is not provided.
     */
    suspend fun discoverPublicRooms(
        mucServiceDomain: String? = null
    ): Result<List<PublicDirectoryRoom>> = withContext(Dispatchers.IO) {
        try {
            val smackMucManager = getSmackMucManager()
            val domain = mucServiceDomain?.trim() ?: run {
                val accountDomain = connectionManager.credentials.value.domain
                "conference.$accountDomain"
            }

            val domainBareJid = JidCreate.domainBareFrom(domain)
            val hostedRoomsMap = smackMucManager.getRoomsHostedBy(domainBareJid)

            val directoryRooms = hostedRoomsMap.values.map { hosted ->
                val jidStr = hosted.jid.toString()
                var occupantCount = 0
                var topic = ""
                try {
                    val info = smackMucManager.getRoomInfo(hosted.jid)
                    occupantCount = info.occupantsCount
                    topic = info.description ?: info.subject ?: ""
                } catch (e: Exception) {
                    // Ignore individual room info query failures
                }

                PublicDirectoryRoom(
                    jid = jidStr,
                    name = hosted.name ?: jidStr.substringBefore("@"),
                    description = topic.ifBlank { "Ruang obrolan publik di $domain" },
                    occupantsCount = occupantCount
                )
            }

            Log.i(TAG, "Discovered ${directoryRooms.size} hosted rooms on $domain")
            Result.success(directoryRooms)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to discover public rooms on $mucServiceDomain: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches MUC bookmarks from the server (XEP-0048).
     */
    suspend fun fetchBookmarks(): Result<List<BookmarkedConference>> = withContext(Dispatchers.IO) {
        val conn = connectionManager.getRawConnection()
            ?: return@withContext Result.failure(IllegalStateException("No active XMPP connection."))
        if (!conn.isAuthenticated) {
            return@withContext Result.failure(IllegalStateException("User not authenticated."))
        }

        try {
            val bookmarkManager = BookmarkManager.getBookmarkManager(conn)
            val bookmarks = bookmarkManager.bookmarkedConferences
            _serverBookmarks.value = bookmarks
            Log.i(TAG, "Successfully fetched ${bookmarks.size} bookmarks from server.")
            Result.success(bookmarks)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch bookmarks from server: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Saves a MUC room bookmark to the server (XEP-0048).
     */
    suspend fun saveBookmark(
        roomJid: String,
        name: String,
        nickname: String? = null,
        password: String? = null,
        autoJoin: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val conn = connectionManager.getRawConnection()
            ?: return@withContext Result.failure(IllegalStateException("No active XMPP connection."))

        try {
            val bookmarkManager = BookmarkManager.getBookmarkManager(conn)
            val cleanJidStr = roomJid.substringBefore("/").trim()
            val bareJid = JidCreate.entityBareFrom(cleanJidStr)
            val nick = nickname?.trim()?.takeIf { it.isNotEmpty() }?.let { Resourcepart.from(it) }
            val pass = password?.trim()?.takeIf { it.isNotEmpty() }
            val bookmarkName = name.trim().ifEmpty { bareJid.localpartOrNull?.toString() ?: "Room" }
            
            bookmarkManager.addBookmarkedConference(bookmarkName, bareJid, autoJoin, nick, pass)
            Log.i(TAG, "Successfully saved bookmark for $cleanJidStr to server (autojoin=$autoJoin).")
            fetchBookmarks() // Refresh local state
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bookmark to server for $roomJid: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Removes a MUC room bookmark from the server (XEP-0048).
     */
    suspend fun removeBookmark(roomJid: String): Result<Unit> = withContext(Dispatchers.IO) {
        val conn = connectionManager.getRawConnection()
            ?: return@withContext Result.failure(IllegalStateException("No active XMPP connection."))

        try {
            val bookmarkManager = BookmarkManager.getBookmarkManager(conn)
            val cleanJidStr = roomJid.substringBefore("/").trim()
            val bareJid = JidCreate.entityBareFrom(cleanJidStr)
            bookmarkManager.removeBookmarkedConference(bareJid)
            Log.i(TAG, "Successfully removed bookmark for $cleanJidStr from server.")
            fetchBookmarks() // Refresh local state
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove bookmark from server for $roomJid: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Leaves all active MUC rooms gracefully.
     */
    suspend fun leaveAllRooms() = withContext(Dispatchers.IO) {
        for ((jid, muc) in activeMucs) {
            try {
                if (muc.isJoined) {
                    muc.leave()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error leaving room $jid during teardown: ${e.message}")
            }
        }
        updateJobs.values.forEach { it.cancel() }
        updateJobs.clear()
        activeMucs.clear()
        activeMucConnections.clear()
        roomNicknames.clear()
        roomMessageListeners.clear()
        roomParticipantListeners.clear()
        roomSubjectListeners.clear()
        roomPresenceListeners.clear()
        _joinedRooms.value = emptySet()
        _roomOccupants.value = emptyMap()
        _serverBookmarks.value = emptyList()
        roomOccupantPresenceCache.clear()
        roomOccupantCapsCache.clear()
    }

    /**
     * Clears cached server bookmarks.
     */
    fun clearServerBookmarks() {
        _serverBookmarks.value = emptyList()
    }

    private fun resetSmackMucLocalState(muc: MultiUserChat) {
        try {
            val userHasLeftMethod = MultiUserChat::class.java.getDeclaredMethod("userHasLeft")
            userHasLeftMethod.isAccessible = true
            userHasLeftMethod.invoke(muc)
            Log.i(TAG, "resetSmackMucLocalState: invoked userHasLeft() for ${muc.room}")
            return
        } catch (e: Exception) {
            Log.w(TAG, "resetSmackMucLocalState: userHasLeft() reflection failed for ${muc.room}: ${e.message}")
        }
        try {
            val myRoomJidField = MultiUserChat::class.java.getDeclaredField("myRoomJid")
            myRoomJidField.isAccessible = true
            myRoomJidField.set(muc, null)
            Log.i(TAG, "resetSmackMucLocalState: cleared myRoomJid field for ${muc.room}")
            return
        } catch (e: Exception) {
            Log.w(TAG, "resetSmackMucLocalState: myRoomJid reflection failed for ${muc.room}: ${e.message}")
        }
        try {
            Log.w(TAG, "resetSmackMucLocalState: falling back to muc.leave() for ${muc.room}")
            muc.leave()
        } catch (_: Exception) {
        }
    }

    /**
     * Clears only UI-visible occupant lists and presence/caps caches on connection drop,
     * preserving active MUC session tracking and listeners so XEP-0198 resumption stays intact.
     */
    fun clearAllOccupantsAndCache() {
        updateJobs.values.forEach { it.cancel() }
        updateJobs.clear()
        _roomOccupants.value = emptyMap()
        roomOccupantPresenceCache.clear()
        roomOccupantCapsCache.clear()
        Log.i(TAG, "Cleared MUC UI occupants and caches (active MUC session & listeners preserved for resumption).")
    }

    /**
     * Full MUC session reset invoked when starting a fresh non-resumed session or replacing the connection.
     * Clears activeMucs, activeMucConnections, listeners, updateJobs, and stale Smack local state while preserving roomNicknames.
     */
    fun resetMucSession() {
        Log.i(
            TAG,
            "Resetting full MUC session (activeMucs=${activeMucs.size}, activeConns=${activeMucConnections.size}, preservedNicknames=${roomNicknames.size})."
        )
        updateJobs.values.forEach { it.cancel() }
        updateJobs.clear()

        val mucSnapshot = activeMucs.toMap()
        val connSnapshot = activeMucConnections.toMap()
        val allKeys = (mucSnapshot.keys + roomMessageListeners.keys + roomParticipantListeners.keys +
            roomSubjectListeners.keys + roomPresenceListeners.keys).toSet()

        for (key in allKeys) {
            val muc = mucSnapshot[key] ?: mucSnapshot[key.lowercase()]
            val conn = connSnapshot[key] ?: connSnapshot[key.lowercase()] ?: connectionManager.getRawConnection()
            roomMessageListeners.remove(key)?.forEach { listener ->
                try { muc?.removeMessageListener(listener) } catch (_: Exception) {}
            }
            roomParticipantListeners.remove(key)?.forEach { listener ->
                try { muc?.removeParticipantStatusListener(listener) } catch (_: Exception) {}
            }
            roomSubjectListeners.remove(key)?.forEach { listener ->
                try { muc?.removeSubjectUpdatedListener(listener) } catch (_: Exception) {}
            }
            roomPresenceListeners.remove(key)?.forEach { listener ->
                try { conn?.removeAsyncStanzaListener(listener) } catch (_: Exception) {}
                try { connectionManager.getRawConnection()?.removeAsyncStanzaListener(listener) } catch (_: Exception) {}
            }
        }

        mucSnapshot.values.distinct().forEach { muc ->
            if (muc.isJoined) {
                resetSmackMucLocalState(muc)
            }
        }

        activeMucs.clear()
        activeMucConnections.clear()
        _joinedRooms.value = emptySet()
        _roomOccupants.value = emptyMap()
        roomOccupantPresenceCache.clear()
        roomOccupantCapsCache.clear()
    }

    private fun findActiveMucEntry(roomJid: String): Pair<String, MultiUserChat>? {
        val trimmed = roomJid.trim()
        val norm = trimmed.lowercase()
        activeMucs[norm]?.let { return norm to it }
        activeMucs[trimmed]?.let { return trimmed to it }
        return activeMucs.entries.firstOrNull { it.key.equals(trimmed, ignoreCase = true) }
            ?.let { it.key to it.value }
    }

    private fun isMucValidForCurrentSession(key: String, muc: MultiUserChat): Boolean {
        val currentConn = connectionManager.getRawConnection() ?: return false
        if (!currentConn.isConnected || !currentConn.isAuthenticated) return false
        val mucConn = activeMucConnections[key] ?: activeMucConnections[key.lowercase()]
        if (mucConn !== currentConn) return false
        return muc.isJoined
    }

    /**
     * Checks if a room is currently joined on the active authenticated session.
     */
    fun isRoomJoined(roomJid: String): Boolean {
        val (key, muc) = findActiveMucEntry(roomJid) ?: return false
        return isMucValidForCurrentSession(key, muc)
    }

    /**
     * Retrieves the active [MultiUserChat] instance for a room on the current session, or null if not joined.
     */
    fun getMultiUserChat(roomJid: String): MultiUserChat? {
        val (key, muc) = findActiveMucEntry(roomJid) ?: return null
        return if (isMucValidForCurrentSession(key, muc)) muc else null
    }

    /**
     * Returns current cached occupant list for a room.
     */
    fun getOccupants(roomJid: String): List<RoomOccupant> {
        val norm = roomJid.trim().lowercase()
        return _roomOccupants.value[norm] ?: _roomOccupants.value[roomJid] ?: _roomOccupants.value.entries.firstOrNull { it.key.equals(roomJid.trim(), ignoreCase = true) }?.value ?: emptyList()
    }

    /**
     * Attaches a [MessageListener] implementation to a joined MUC room.
     *
     * @param roomJid Room bare JID
     * @param listener The MessageListener instance
     * @return true if successfully attached to the room
     */
    fun addMessageListener(roomJid: String, listener: MessageListener): Boolean {
        val muc = getMultiUserChat(roomJid) ?: return false
        val success = muc.addMessageListener(listener)
        if (success) {
            roomMessageListeners.getOrPut(roomJid) { mutableListOf() }.add(listener)
        }
        return success
    }

    /**
     * Detaches a [MessageListener] from a joined MUC room.
     */
    fun removeMessageListener(roomJid: String, listener: MessageListener): Boolean {
        val muc = getMultiUserChat(roomJid) ?: return false
        val success = muc.removeMessageListener(listener)
        roomMessageListeners[roomJid]?.remove(listener)
        roomMessageListeners[roomJid.trim().lowercase()]?.remove(listener)
        return success
    }

    /**
     * Grants moderator role to a user in the room.
     */
    suspend fun grantModerator(roomJid: String, nickname: String): Result<Unit> = withContext(Dispatchers.IO) {
        val muc = getMultiUserChat(roomJid) ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
        try {
            muc.grantModerator(Resourcepart.from(nickname))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Revokes moderator role from a user in the room.
     */
    suspend fun revokeModerator(roomJid: String, nickname: String): Result<Unit> = withContext(Dispatchers.IO) {
        val muc = getMultiUserChat(roomJid) ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
        try {
            muc.revokeModerator(Resourcepart.from(nickname))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Grants membership affiliation to a user.
     */
    suspend fun grantMembership(roomJid: String, jid: String): Result<Unit> = withContext(Dispatchers.IO) {
        val muc = getMultiUserChat(roomJid) ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
        try {
            muc.grantMembership(JidCreate.entityBareFrom(jid))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Revokes membership affiliation from a user.
     */
    suspend fun revokeMembership(roomJid: String, jid: String): Result<Unit> = withContext(Dispatchers.IO) {
        val muc = getMultiUserChat(roomJid) ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
        try {
            muc.revokeMembership(JidCreate.entityBareFrom(jid))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Grants admin affiliation to a user.
     */
    suspend fun grantAdmin(roomJid: String, jid: String): Result<Unit> = withContext(Dispatchers.IO) {
        val muc = getMultiUserChat(roomJid) ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
        try {
            muc.grantAdmin(JidCreate.entityBareFrom(jid))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Revokes admin affiliation from a user.
     */
    suspend fun revokeAdmin(roomJid: String, jid: String): Result<Unit> = withContext(Dispatchers.IO) {
        val muc = getMultiUserChat(roomJid) ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
        try {
            muc.revokeAdmin(JidCreate.entityBareFrom(jid))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Bans a user from the room.
     */
    suspend fun banUser(roomJid: String, jid: String, reason: String?): Result<Unit> = withContext(Dispatchers.IO) {
        val muc = getMultiUserChat(roomJid) ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
        try {
            muc.banUser(JidCreate.entityBareFrom(jid), reason)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Kicks a user from the room.
     */
    suspend fun kickParticipant(roomJid: String, nickname: String, reason: String?): Result<Unit> = withContext(Dispatchers.IO) {
        val muc = getMultiUserChat(roomJid) ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
        try {
            muc.kickParticipant(Resourcepart.from(nickname), reason)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Grants owner affiliation to a user.
     */
    suspend fun grantOwnership(roomJid: String, jid: String): Result<Unit> = withContext(Dispatchers.IO) {
        val muc = getMultiUserChat(roomJid) ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
        try {
            muc.grantOwnership(JidCreate.entityBareFrom(jid))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Revokes owner affiliation from a user.
     */
    suspend fun revokeOwnership(roomJid: String, jid: String): Result<Unit> = withContext(Dispatchers.IO) {
        val muc = getMultiUserChat(roomJid) ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
        try {
            muc.revokeOwnership(JidCreate.entityBareFrom(jid))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Grants voice role to a visitor (makes participant).
     */
    suspend fun grantVoice(roomJid: String, nickname: String): Result<Unit> = withContext(Dispatchers.IO) {
        val muc = getMultiUserChat(roomJid) ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
        try {
            muc.grantVoice(Resourcepart.from(nickname))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Revokes voice role from a participant (makes visitor).
     */
    suspend fun revokeVoice(roomJid: String, nickname: String): Result<Unit> = withContext(Dispatchers.IO) {
        val muc = getMultiUserChat(roomJid) ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
        try {
            muc.revokeVoice(Resourcepart.from(nickname))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sets a user's affiliation to none by revoking ownership, admin, and membership/outcast status.
     */
    suspend fun grantNone(roomJid: String, jid: String): Result<Unit> = withContext(Dispatchers.IO) {
        val muc = getMultiUserChat(roomJid) ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
        try {
            val entityJid = JidCreate.entityBareFrom(jid)
            try { muc.revokeOwnership(entityJid) } catch (e: Exception) {}
            try { muc.revokeAdmin(entityJid) } catch (e: Exception) {}
            try { muc.revokeMembership(entityJid) } catch (e: Exception) {}
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Unbans a user.
     */
    suspend fun unbanUser(roomJid: String, jid: String): Result<Unit> = withContext(Dispatchers.IO) {
        val muc = getMultiUserChat(roomJid) ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
        try {
            val entityJid = JidCreate.entityBareFrom(jid)
            try { muc.revokeMembership(entityJid) } catch (e: Exception) {}
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches owner affiliates list from room.
     */
    suspend fun getOwners(roomJid: String): Result<List<com.ndregil.inchat.data.model.MucAffiliate>> = withContext(Dispatchers.IO) {
        val muc = getMultiUserChat(roomJid) ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
        try {
            val list = muc.owners.map {
                com.ndregil.inchat.data.model.MucAffiliate(
                    jid = it.jid?.toString() ?: "",
                    nickname = it.nick?.toString(),
                    affiliation = "owner",
                    role = it.role?.toString() ?: "none"
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches admin affiliates list from room.
     */
    suspend fun getAdmins(roomJid: String): Result<List<com.ndregil.inchat.data.model.MucAffiliate>> = withContext(Dispatchers.IO) {
        val muc = getMultiUserChat(roomJid) ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
        try {
            val list = muc.admins.map {
                com.ndregil.inchat.data.model.MucAffiliate(
                    jid = it.jid?.toString() ?: "",
                    nickname = it.nick?.toString(),
                    affiliation = "admin",
                    role = it.role?.toString() ?: "none"
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches member affiliates list from room.
     */
    suspend fun getMembers(roomJid: String): Result<List<com.ndregil.inchat.data.model.MucAffiliate>> = withContext(Dispatchers.IO) {
        val muc = getMultiUserChat(roomJid) ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
        try {
            val list = muc.members.map {
                com.ndregil.inchat.data.model.MucAffiliate(
                    jid = it.jid?.toString() ?: "",
                    nickname = it.nick?.toString(),
                    affiliation = "member",
                    role = it.role?.toString() ?: "none"
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches outcast (banned) affiliates list from room.
     */
    suspend fun getOutcasts(roomJid: String): Result<List<com.ndregil.inchat.data.model.MucAffiliate>> = withContext(Dispatchers.IO) {
        val muc = getMultiUserChat(roomJid) ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
        try {
            val list = muc.outcasts.map {
                com.ndregil.inchat.data.model.MucAffiliate(
                    jid = it.jid?.toString() ?: "",
                    nickname = it.nick?.toString(),
                    affiliation = "outcast",
                    role = it.role?.toString() ?: "none"
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Hooks a new [MucMessageListener] into a joined MUC room to process incoming messages.
     *
     * @param roomJid Room bare JID
     * @param onMessageReceived Callback receiving parsed [XmppMessage] stanzas
     * @return The configured [MucMessageListener], or null if room is not joined
     */
    fun hookMessageListener(
        roomJid: String,
        onMessageReceived: (XmppMessage) -> Unit
    ): MucMessageListener? {
        val muc = getMultiUserChat(roomJid) ?: return null
        val listener = MucMessageListener(
            roomJid = roomJid,
            myNicknameProvider = { roomNicknames[roomJid] ?: roomNicknames[roomJid.trim().lowercase()] ?: "" },
            onMessageProcessed = { msg ->
                val senderJid = msg.senderJid
                val avatar = avatarManager.getAvatar(senderJid) ?: avatarManager.getAvatar(msg.senderNickname)
                val cachedCaps = getOccupantCaps(roomJid, msg.senderNickname)
                val finalCaps = if (cachedCaps != null && cachedCaps.iconResId != null) {
                    cachedCaps
                } else if (msg.senderClientCaps.iconResId != null) {
                    msg.senderClientCaps
                } else {
                    com.ndregil.inchat.data.model.ClientCaps.resolveFor(
                        name = msg.senderNickname,
                        jid = senderJid
                    )
                }
                onMessageReceived(msg.copy(avatarKey = senderJid, senderClientCaps = finalCaps))
            }
        )
        if (muc.addMessageListener(listener)) {
            roomMessageListeners.getOrPut(roomJid) { mutableListOf() }.add(listener)
            return listener
        }
        return null
    }

    private fun detachMucListeners(keys: Set<String>, muc: MultiUserChat?) {
        for (key in keys) {
            val storedConn = activeMucConnections[key] ?: activeMucConnections[key.lowercase()]
            roomMessageListeners.remove(key)?.forEach { listener ->
                try { muc?.removeMessageListener(listener) } catch (_: Exception) {}
            }
            roomParticipantListeners.remove(key)?.forEach { listener ->
                try { muc?.removeParticipantStatusListener(listener) } catch (_: Exception) {}
            }
            roomSubjectListeners.remove(key)?.forEach { listener ->
                try { muc?.removeSubjectUpdatedListener(listener) } catch (_: Exception) {}
            }
            roomPresenceListeners.remove(key)?.forEach { listener ->
                try { storedConn?.removeAsyncStanzaListener(listener) } catch (_: Exception) {}
                try { connectionManager.getRawConnection()?.removeAsyncStanzaListener(listener) } catch (_: Exception) {}
            }
        }
    }

    /**
     * Attaches message, presence, and subject listeners to a [MultiUserChat] instance.
     */
    private fun registerMucListeners(roomJid: String, muc: MultiUserChat, myNickname: String) {
        val norm = roomJid.trim().lowercase()
        detachMucListeners(setOf(roomJid, norm), muc)

        // Instantiate and hook our dedicated MucMessageListener implementation
        val mucMessageListener = MucMessageListener(
            roomJid = roomJid,
            myNicknameProvider = {
                muc.nickname?.toString()?.takeIf { it.isNotBlank() }
                    ?: roomNicknames[norm]
                    ?: roomNicknames[roomJid]
                    ?: myNickname
            },
            onMessageProcessed = { xmppMessage ->
                val senderJid = xmppMessage.senderJid
                val cachedCaps = getOccupantCaps(roomJid, xmppMessage.senderNickname)
                val finalCaps = if (cachedCaps != null && cachedCaps.iconResId != null) {
                    cachedCaps
                } else if (xmppMessage.senderClientCaps.iconResId != null) {
                    xmppMessage.senderClientCaps
                } else {
                    com.ndregil.inchat.data.model.ClientCaps.resolveFor(
                        name = xmppMessage.senderNickname,
                        jid = senderJid
                    )
                }
                _incomingMessages.tryEmit(xmppMessage.copy(avatarKey = senderJid, senderClientCaps = finalCaps))
            },
            onTopicUpdated = { jid, newTopic ->
                _topicUpdates.tryEmit(Pair(jid, newTopic))
            }
        )
        muc.addMessageListener(mucMessageListener)
        roomMessageListeners.getOrPut(norm) { mutableListOf() }.add(mucMessageListener)

        // Participant status listener (catches join/leave/kick/ban/role/nick events like BombusMod)
        val participantStatusListener = object : org.jivesoftware.smackx.muc.ParticipantStatusListener {
            override fun joined(participant: EntityFullJid?) {
                Log.d(TAG, "MUC [$roomJid] Participant joined: $participant")
                updateOccupantsForRoom(roomJid, muc)
                
                val strings = getCurrentStrings()
                val nick = participant?.resourceOrNull?.toString() ?: strings.personFallback
                val occ = muc.getOccupant(participant)
                val roleName = when (occ?.role) {
                    MUCRole.moderator -> strings.roleModerator
                    MUCRole.participant -> strings.participantsDesc
                    MUCRole.visitor -> strings.roomMemberFallback
                    else -> strings.participantsDesc
                }
                val affName = when (occ?.affiliation) {
                    MUCAffiliation.owner -> " (${strings.roleOwner})"
                    MUCAffiliation.admin -> " (${strings.roleAdmin})"
                    MUCAffiliation.member -> " (${strings.roleMember})"
                    else -> ""
                }
                val statusMsg = muc.getOccupantPresence(participant)?.status?.takeIf { it.isNotBlank() }?.let { " [\"$it\"]" } ?: ""
                val body = String.format(strings.sysJoinedRoom, nick, roleName, affName, statusMsg)
                val systemMsg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = body,
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.SYSTEM_JOIN
                )
                _incomingMessages.tryEmit(systemMsg)
            }

            override fun left(participant: EntityFullJid?) {
                Log.d(TAG, "MUC [$roomJid] Participant left: $participant")
                updateOccupantsForRoom(roomJid, muc)
                
                val strings = getCurrentStrings()
                val nick = participant?.resourceOrNull?.toString() ?: strings.personFallback
                roomOccupantPresenceCache[roomJid]?.remove(nick)
                val body = String.format(strings.sysLeftRoom, nick)
                val systemMsg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = body,
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.SYSTEM_LEAVE
                )
                _incomingMessages.tryEmit(systemMsg)
            }

            override fun kicked(participant: EntityFullJid?, actor: org.jxmpp.jid.Jid?, reason: String?) {
                updateOccupantsForRoom(roomJid, muc)
                val strings = getCurrentStrings()
                val nick = participant?.resourceOrNull?.toString() ?: strings.personFallback
                val actorNick = actor?.resourceOrNull?.toString() ?: actor?.toString() ?: strings.adminFallback
                val reasonStr = if (!reason.isNullOrBlank()) strings.reasonPrefix.format(reason) else ""
                val body = String.format(strings.sysKicked, nick, actorNick, reasonStr)
                val msg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = body,
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.MODERATION
                )
                _incomingMessages.tryEmit(msg)
            }

            override fun banned(participant: EntityFullJid?, actor: org.jxmpp.jid.Jid?, reason: String?) {
                updateOccupantsForRoom(roomJid, muc)
                val strings = getCurrentStrings()
                val nick = participant?.resourceOrNull?.toString() ?: strings.personFallback
                val actorNick = actor?.resourceOrNull?.toString() ?: actor?.toString() ?: strings.adminFallback
                val reasonStr = if (!reason.isNullOrBlank()) strings.reasonPrefix.format(reason) else ""
                val body = String.format(strings.sysBanned, nick, actorNick, reasonStr)
                val msg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = body,
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.MODERATION
                )
                _incomingMessages.tryEmit(msg)
            }

            override fun voiceGranted(participant: EntityFullJid?) {
                updateOccupantsForRoom(roomJid, muc)
                val strings = getCurrentStrings()
                val nick = participant?.resourceOrNull?.toString() ?: strings.personFallback
                val body = String.format(strings.sysVoiceGranted, nick)
                val msg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = body,
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.MODERATION
                )
                _incomingMessages.tryEmit(msg)
            }

            override fun voiceRevoked(participant: EntityFullJid?) {
                updateOccupantsForRoom(roomJid, muc)
                val strings = getCurrentStrings()
                val nick = participant?.resourceOrNull?.toString() ?: strings.personFallback
                val body = String.format(strings.sysVoiceRevoked, nick)
                val msg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = body,
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.MODERATION
                )
                _incomingMessages.tryEmit(msg)
            }

            override fun membershipGranted(participant: EntityFullJid?) {
                updateOccupantsForRoom(roomJid, muc)
                val strings = getCurrentStrings()
                val nick = participant?.resourceOrNull?.toString() ?: strings.personFallback
                val body = String.format(strings.sysMembershipGranted, nick)
                val msg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = body,
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.MODERATION
                )
                _incomingMessages.tryEmit(msg)
            }

            override fun membershipRevoked(participant: EntityFullJid?) {
                updateOccupantsForRoom(roomJid, muc)
                val strings = getCurrentStrings()
                val nick = participant?.resourceOrNull?.toString() ?: strings.personFallback
                val body = String.format(strings.sysMembershipRevoked, nick)
                val msg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = body,
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.MODERATION
                )
                _incomingMessages.tryEmit(msg)
            }

            override fun moderatorGranted(participant: EntityFullJid?) {
                updateOccupantsForRoom(roomJid, muc)
                val strings = getCurrentStrings()
                val nick = participant?.resourceOrNull?.toString() ?: strings.personFallback
                val body = String.format(strings.sysModeratorGranted, nick)
                val msg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = body,
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.MODERATION
                )
                _incomingMessages.tryEmit(msg)
            }

            override fun moderatorRevoked(participant: EntityFullJid?) {
                updateOccupantsForRoom(roomJid, muc)
                val strings = getCurrentStrings()
                val nick = participant?.resourceOrNull?.toString() ?: strings.personFallback
                val body = String.format(strings.sysModeratorRevoked, nick)
                val msg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = body,
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.MODERATION
                )
                _incomingMessages.tryEmit(msg)
            }

            override fun ownershipGranted(participant: EntityFullJid?) {
                updateOccupantsForRoom(roomJid, muc)
                val strings = getCurrentStrings()
                val nick = participant?.resourceOrNull?.toString() ?: strings.personFallback
                val body = String.format(strings.sysOwnershipGranted, nick)
                val msg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = body,
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.MODERATION
                )
                _incomingMessages.tryEmit(msg)
            }

            override fun ownershipRevoked(participant: EntityFullJid?) {
                updateOccupantsForRoom(roomJid, muc)
                val strings = getCurrentStrings()
                val nick = participant?.resourceOrNull?.toString() ?: strings.personFallback
                val body = String.format(strings.sysOwnershipRevoked, nick)
                val msg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = body,
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.MODERATION
                )
                _incomingMessages.tryEmit(msg)
            }

            override fun adminGranted(participant: EntityFullJid?) {
                updateOccupantsForRoom(roomJid, muc)
                val strings = getCurrentStrings()
                val nick = participant?.resourceOrNull?.toString() ?: strings.personFallback
                val body = String.format(strings.sysAdminGranted, nick)
                val msg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = body,
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.MODERATION
                )
                _incomingMessages.tryEmit(msg)
            }

            override fun adminRevoked(participant: EntityFullJid?) {
                updateOccupantsForRoom(roomJid, muc)
                val strings = getCurrentStrings()
                val nick = participant?.resourceOrNull?.toString() ?: strings.personFallback
                val body = String.format(strings.sysAdminRevoked, nick)
                val msg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = body,
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.MODERATION
                )
                _incomingMessages.tryEmit(msg)
            }

            override fun nicknameChanged(participant: EntityFullJid?, newNickname: Resourcepart?) {
                updateOccupantsForRoom(roomJid, muc)
                val strings = getCurrentStrings()
                val oldNick = participant?.resourceOrNull?.toString() ?: strings.personFallback
                val newNick = newNickname?.toString() ?: strings.nicknamePlaceholder
                val body = String.format(strings.sysNicknameChanged, oldNick, newNick)
                val msg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = body,
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.SYSTEM_STATUS
                )
                _incomingMessages.tryEmit(msg)
            }
        }
        muc.addParticipantStatusListener(participantStatusListener)
        roomParticipantListeners.getOrPut(norm) { mutableListOf() }.add(participantStatusListener)

        // Subject / Topic change listener
        val subjectListener = SubjectUpdatedListener { newSubject, from ->
            if (newSubject != null) {
                Log.i(TAG, "MUC [$roomJid] Subject updated to '$newSubject' by $from")
                _topicUpdates.tryEmit(Pair(roomJid, newSubject))
            }
        }
        muc.addSubjectUpdatedListener(subjectListener)
        roomSubjectListeners.getOrPut(norm) { mutableListOf() }.add(subjectListener)

        // Participant presence listener for real-time presence (Online, Chat, Away, DND, Offline, status message)
        val presenceListener = org.jivesoftware.smack.StanzaListener { stanza ->
            val presence = stanza as Presence
            val fromJid = presence.from
            if (fromJid != null && fromJid.asBareJid().toString().equals(roomJid, ignoreCase = true)) {
                val nick = fromJid.resourceOrNull?.toString() ?: return@StanzaListener

                // Extract XEP-0115 Entity Capabilities directly from raw incoming presence
                val extracted = ClientRegistryManager.extractCapsFromPresence(presence)
                
                // Also try to extract real JID from MUCUser extension for better caching
                val mucUser = org.jivesoftware.smackx.muc.packet.MUCUser.from(presence)
                val realJid = mucUser?.item?.jid?.toString() ?: fromJid.toString()

                var capsChanged = false
                if (extracted != null) {
                    val (node, ver) = extracted
                    val caps = resolvePresenceCaps(fromJid, realJid, node, ver)
                    val roomCapsMap = roomOccupantCapsCache.computeIfAbsent(roomJid) { java.util.concurrent.ConcurrentHashMap() }
                    if (caps.iconResId != null) {
                        val prevCaps = roomCapsMap[nick]
                        if (prevCaps != caps) {
                            roomCapsMap[nick] = caps
                            capsChanged = true
                        }
                        ClientRegistryManager.putCachedCaps(realJid, caps)
                        if (!realJid.equals(fromJid.toString(), ignoreCase = true)) {
                            ClientRegistryManager.putCachedCaps(fromJid.toString(), caps)
                        }
                    } else {
                        if (roomCapsMap.remove(nick) != null) {
                            capsChanged = true
                        }
                    }
                }

                val userPresence = presence.toUserPresence()
                val customStatus = presence.status ?: ""

                val roomCache = roomOccupantPresenceCache.getOrPut(roomJid) { mutableMapOf() }
                val prev = roomCache[nick]
                val currentPair = Pair(userPresence, customStatus)
                val presenceChanged = prev != currentPair

                // Update real-time occupant presence list only when presence or caps actually changed
                if (presenceChanged || capsChanged) {
                    updateOccupantsForRoom(roomJid, muc)
                }

                if (presenceChanged) {
                    roomCache[nick] = currentPair

                    // Only emit status message if this is a subsequent change (avoid initial spam on join)
                    val effectiveMyNick = muc.nickname?.toString()?.takeIf { it.isNotBlank() }
                        ?: roomNicknames[norm]
                        ?: roomNicknames[roomJid]
                        ?: myNickname
                    if (prev != null && !nick.equals(effectiveMyNick, ignoreCase = true)) {
                        val strings = getCurrentStrings()
                        val modeName = when (presence.mode) {
                            Presence.Mode.away -> strings.presenceAway
                            Presence.Mode.dnd -> strings.presenceDnd
                            Presence.Mode.chat -> strings.presenceChat
                            Presence.Mode.xa -> strings.presenceXa
                            else -> if (presence.type == Presence.Type.unavailable) strings.presenceOffline else strings.presenceOnline
                        }
                        val statusSnippet = if (customStatus.isNotBlank()) " (\"$customStatus\")" else ""
                        val presenceText = if (presence.type == Presence.Type.unavailable) {
                            String.format(strings.sysPresenceOffline, nick, statusSnippet)
                        } else {
                            String.format(strings.sysPresenceMode, nick, modeName, statusSnippet)
                        }

                        val systemMsg = XmppMessage(
                            id = java.util.UUID.randomUUID().toString(),
                            roomJid = roomJid,
                            senderJid = roomJid,
                            senderNickname = "",
                            body = presenceText,
                            timestamp = System.currentTimeMillis(),
                            messageType = if (presence.type == Presence.Type.unavailable) com.ndregil.inchat.data.model.MessageType.SYSTEM_LEAVE else com.ndregil.inchat.data.model.MessageType.SYSTEM_STATUS
                        )
                        _incomingMessages.tryEmit(systemMsg)
                    }
                }
            }
        }
        val presenceFilter = org.jivesoftware.smack.filter.StanzaTypeFilter(Presence::class.java)
        connectionManager.getRawConnection()?.addAsyncStanzaListener(presenceListener, presenceFilter)
        roomPresenceListeners.getOrPut(norm) { mutableListOf() }.add(presenceListener)
    }

    /**
     * Public method to force a refresh of the occupant list for a room.
     */
    fun refreshOccupants(roomJid: String) {
        val muc = getMultiUserChat(roomJid) ?: return
        updateOccupantsForRoom(roomJid, muc)
    }

    /**
     * Retrieves the current count of occupants for a room.
     */
    fun getRoomOccupantsCount(roomJid: String): Int {
        val norm = roomJid.trim().lowercase()
        val list = _roomOccupants.value[norm] ?: _roomOccupants.value[roomJid] ?: _roomOccupants.value.entries.firstOrNull { it.key.equals(roomJid.trim(), ignoreCase = true) }?.value
        if (!list.isNullOrEmpty()) return list.size
        val muc = getMultiUserChat(roomJid)
        if (muc != null && muc.isJoined && muc.occupantsCount > 0) {
            return muc.occupantsCount
        }
        return 0
    }

    /**
     * Queries the occupant count from server info via XEP-0045/XEP-0030 disco.
     */
    suspend fun queryRoomOccupantsCount(roomJid: String): Int = withContext(Dispatchers.IO) {
        try {
            val live = getRoomOccupantsCount(roomJid)
            if (live > 0) return@withContext live
            val conn = connectionManager.getRawConnection() ?: return@withContext -1
            if (!conn.isAuthenticated) return@withContext -1
            val bareJid = JidCreate.entityBareFrom(roomJid.trim())
            val smackMucManager = getSmackMucManager()
            val info = smackMucManager.getRoomInfo(bareJid)
            val count = info.occupantsCount
            if (count >= 0) count else -1
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Retrieves cached ClientCaps for an occupant in a room.
     */
    fun getOccupantCaps(roomJid: String, nickname: String): com.ndregil.inchat.data.model.ClientCaps? {
        val norm = roomJid.trim().lowercase()
        val roomCached = roomOccupantCapsCache[norm]?.get(nickname) ?: roomOccupantCapsCache[roomJid]?.get(nickname)
        if (roomCached != null) return roomCached
        val occupants = getOccupants(roomJid)
        val occupant = occupants.firstOrNull { it.nickname.equals(nickname, ignoreCase = true) }
        return occupant?.clientCaps
    }

    /**
     * Legacy hook decoupled from client capabilities.
     * Software version querying (XEP-0092) is solely performed on-demand via user interaction.
     */
    fun queryOccupantVersionIfNeeded(roomJid: String, nickname: String) {
        // Strictly decoupled: XEP-0092 is on-demand only and never alters presence capabilities.
    }

    private fun resolvePresenceCaps(
        presenceFrom: Jid?,
        realJid: String,
        node: String,
        ver: String
    ): com.ndregil.inchat.data.model.ClientCaps {
        val nick = presenceFrom?.resourceOrNull?.toString() ?: ""
        val effectiveJid = presenceFrom?.toString() ?: realJid
        val resolved = com.ndregil.inchat.data.model.ClientCaps.resolveFor(
            name = nick,
            jid = effectiveJid,
            node = node,
            ver = ver
        )
        if (resolved.iconResId != null) return resolved

        // Secondary check via Smack's local EntityCapsManager disco#info cache (pure memory lookup):
        // recognizes peer InChat clients if they advertised identity name="InChat".
        if (presenceFrom != null) {
            try {
                val discoInfo = org.jivesoftware.smackx.caps.EntityCapsManager.getDiscoverInfoByUser(presenceFrom)
                val identityName = discoInfo?.identities?.firstOrNull { !it.name.isNullOrBlank() }?.name
                if (!identityName.isNullOrBlank()) {
                    val fromDisco = com.ndregil.inchat.data.model.ClientCaps.resolveFor(
                        name = identityName,
                        jid = realJid,
                        node = if (ClientRegistryManager.isInChatExact(identityName)) "http://ndregil.inchat" else node,
                        ver = ver
                    )
                    if (fromDisco.iconResId != null) return fromDisco
                }
            } catch (_: Exception) {}
        }
        return resolved
    }

    /**
     * Refreshes the cached list of occupants for a specific room with debouncing.
     */
    private fun updateOccupantsForRoom(roomJid: String, muc: MultiUserChat) {
        // Cancel previous pending update for this room
        updateJobs[roomJid]?.cancel()
        
        updateJobs[roomJid] = scope.launch(Dispatchers.Default) {
            // Debounce to batch multiple rapid presence stanzas (e.g. on join)
            kotlinx.coroutines.delay(400)
            
            try {
                val myNickname = muc.nickname?.toString()?.takeIf { it.isNotBlank() }
                    ?: roomNicknames[roomJid]
                    ?: roomNicknames[roomJid.trim().lowercase()]
                    ?: ""
                val occupantsList = mutableListOf<RoomOccupant>()
                val seenNicknames = mutableSetOf<String>()
                val myJid = connectionManager.credentials.value.jid

                val occupantJids = muc.occupants

                for (occupantJid in occupantJids) {
                    val occupant: Occupant? = muc.getOccupant(occupantJid)
                    val nickname = occupant?.nick?.toString()
                        ?: occupantJid.resourceOrNull?.toString()
                        ?: occupantJid.toString().substringAfterLast("/")

                    if (nickname.isBlank() || seenNicknames.contains(nickname.lowercase())) continue
                    seenNicknames.add(nickname.lowercase())

                    val isMe = nickname.equals(myNickname, ignoreCase = true)

                    val role = when {
                        occupant?.affiliation == MUCAffiliation.owner -> OccupantRole.OWNER
                        occupant?.affiliation == MUCAffiliation.admin -> OccupantRole.ADMIN
                        occupant?.role == MUCRole.moderator -> OccupantRole.ADMIN
                        occupant?.role == MUCRole.participant -> OccupantRole.PARTICIPANT
                        occupant?.role == MUCRole.visitor -> OccupantRole.VISITOR
                        else -> OccupantRole.MEMBER
                    }

                    val affiliation = when (occupant?.affiliation) {
                        MUCAffiliation.owner -> OccupantAffiliation.OWNER
                        MUCAffiliation.admin -> OccupantAffiliation.ADMIN
                        MUCAffiliation.member -> OccupantAffiliation.MEMBER
                        MUCAffiliation.outcast -> OccupantAffiliation.OUTCAST
                        else -> OccupantAffiliation.NONE
                    }

                    val smackPresence = muc.getOccupantPresence(occupantJid)
                    val userPresence = smackPresence?.toUserPresence() ?: UserPresence.OFFLINE
                    val statusMessage = smackPresence?.status ?: ""
                    val defaultStatus = when (userPresence) {
                        UserPresence.ONLINE -> "Online"
                        UserPresence.CHAT -> "Bebas Chat"
                        UserPresence.AWAY -> "Away"
                        UserPresence.XA -> "Extended Away"
                        UserPresence.DND -> "Jangan Ganggu"
                        UserPresence.OFFLINE -> "Offline"
                    }
                    val mucUser = smackPresence?.let { org.jivesoftware.smackx.muc.packet.MUCUser.from(it) }
                    val realJid = if (isMe && myJid.isNotBlank()) {
                        myJid
                    } else {
                        mucUser?.item?.jid?.toString() ?: occupant?.jid?.toString() ?: occupantJid.toString()
                    }

                    val extractedCaps = ClientRegistryManager.extractCapsFromPresence(smackPresence)
                    val verHash = extractedCaps?.second

                    // Check cached caps as fallback when presence doesn't carry <c>
                    val roomCached = roomOccupantCapsCache[roomJid]?.get(nickname)
                    val globalCached = ClientRegistryManager.getCachedCaps(realJid, verHash)
                        ?: ClientRegistryManager.getCachedCaps(occupantJid.toString(), verHash)

                    val cachedCaps = when {
                        roomCached != null && roomCached.iconResId != null -> roomCached
                        globalCached != null && globalCached.iconResId != null -> globalCached
                        else -> null
                    }

                    val myCapsVer = if (isMe) {
                        try {
                            connectionManager.getRawConnection()?.let {
                                org.jivesoftware.smackx.caps.EntityCapsManager.getInstanceFor(it)?.capsVersionAndHash?.version
                            }
                        } catch (_: Exception) { null }
                    } else null

                    val occupantCaps: com.ndregil.inchat.data.model.ClientCaps = when {
                        isMe -> {
                            com.ndregil.inchat.data.model.ClientCaps.resolveFor(
                                name = "InChat",
                                jid = realJid,
                                node = "http://ndregil.inchat",
                                ver = myCapsVer ?: "kFwX23eY765qL90mN12aBcDeFgH="
                            )
                        }
                        extractedCaps != null -> {
                            val caps = resolvePresenceCaps(occupantJid, realJid, extractedCaps.first, extractedCaps.second)
                            if (caps.iconResId != null) {
                                roomOccupantCapsCache.computeIfAbsent(roomJid) { java.util.concurrent.ConcurrentHashMap() }[nickname] = caps
                                if (realJid.isNotBlank()) {
                                    ClientRegistryManager.putCachedCaps(realJid, caps)
                                }
                                ClientRegistryManager.putCachedCaps(occupantJid.toString(), caps)
                                caps
                            } else if (cachedCaps != null && cachedCaps.iconResId != null && cachedCaps.ver == extractedCaps.second) {
                                cachedCaps
                            } else {
                                caps
                            }
                        }
                        cachedCaps != null && cachedCaps.iconResId != null -> {
                            cachedCaps
                        }
                        else -> {
                            val targetJid = if (realJid.isNotBlank() && realJid.contains("/")) realJid else occupantJid.toString()
                            com.ndregil.inchat.data.model.ClientCaps.resolveFor(
                                name = nickname,
                                jid = targetJid,
                                node = ""
                            )
                        }
                    }

                    val avatarKey = realJid.ifBlank { occupantJid.toString() }

                    occupantsList.add(
                        RoomOccupant(
                            nickname = nickname,
                            jid = realJid,
                            fullJid = occupantJid.toString(),
                            role = role,
                            affiliation = affiliation,
                            presence = userPresence,
                            statusText = statusMessage.ifBlank { if (isMe) "Kamu" else defaultStatus },
                            isMe = isMe,
                            avatarKey = avatarKey,
                            clientCaps = occupantCaps
                        )
                    )
                }

                val currentMap = _roomOccupants.value.toMutableMap()
                currentMap[roomJid] = occupantsList
                val normJid = roomJid.trim().lowercase()
                currentMap[normJid] = occupantsList
                _roomOccupants.value = currentMap
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.w(TAG, "Error updating occupants for $roomJid: ${e.message}")
                }
            }
        }
    }

    /**
     * Fetches historical messages from the server using XEP-0313 (MAM).
     *
     * @param roomJid Bare JID of the room or contact
     * @param since Timestamp to start fetching from (exclusive)
     * @param limit Maximum number of messages to fetch
     */
    suspend fun fetchMamHistory(
        roomJid: String,
        since: Long = 0L,
        before: Long = 0L,
        limit: Int = 50
    ): Result<List<org.jivesoftware.smack.packet.Message>> = withContext(Dispatchers.IO) {
        try {
            val conn = connectionManager.getRawConnection() ?: return@withContext Result.failure(Exception("Not connected"))
            val bareJid = JidCreate.entityBareFrom(roomJid)
            val domain = bareJid.domain.toString()
            val isMuc = getMultiUserChat(roomJid) != null ||
                domain.contains("conference", ignoreCase = true) ||
                domain.contains("muc.", ignoreCase = true) ||
                domain.contains("chat.", ignoreCase = true) ||
                domain.contains("rooms.", ignoreCase = true)
            val mamManager = if (isMuc) {
                MamManager.getInstanceFor(conn, bareJid)
            } else {
                MamManager.getInstanceFor(conn)
            }
            val queryArgsBuilder = MamManager.MamQueryArgs.builder()
            if (!isMuc) {
                queryArgsBuilder.limitResultsToJid(bareJid)
            }
            if (since > 0L) {
                queryArgsBuilder.limitResultsSince(java.util.Date(since))
                queryArgsBuilder.setResultPageSize(limit)
            } else if (before > 0L) {
                queryArgsBuilder.limitResultsBefore(java.util.Date(before))
                queryArgsBuilder.setResultPageSize(limit)
            } else {
                // Fetch the MOST RECENT (latest) messages from the server archive
                queryArgsBuilder.queryLastPage()
                queryArgsBuilder.setResultPageSize(limit)
            }
            val query = mamSemaphore.withPermit {
                mamManager.queryArchive(queryArgsBuilder.build())
            }
            Result.success(query.messages)
        } catch (e: org.jivesoftware.smack.SmackException.NoResponseException) {
            Log.w(TAG, "MAM history fetch TIMEOUT for $roomJid (30s). Server may be busy or archive is large.")
            Result.failure(e)
        } catch (e: org.jivesoftware.smack.XMPPException.XMPPErrorException) {
            if (e.stanzaError?.condition == org.jivesoftware.smack.packet.StanzaError.Condition.feature_not_implemented) {
                Log.i(TAG, "MAM not supported by $roomJid, skipping history fetch.")
                Result.success(emptyList())
            } else {
                Log.e(TAG, "Failed to fetch MAM history for $roomJid: ${e.message}")
                Result.failure(e)
            }
        } catch (e: Exception) {
            val msg = e.message ?: "Unknown error"
            if (msg.contains("Broken pipe") || msg.contains("not connected")) {
                Log.w(TAG, "MAM fetch failed for $roomJid due to connection issue: $msg")
            } else {
                Log.e(TAG, "Failed to fetch MAM history for $roomJid: $msg")
            }
            Result.failure(e)
        }
    }
}
