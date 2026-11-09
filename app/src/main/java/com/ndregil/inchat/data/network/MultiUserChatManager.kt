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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
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
    companion object {
        private const val TAG = "MultiUserChatManager"
        private const val DEFAULT_HISTORY_STANZAS = 30
    }

    // Active joined MUC instances mapped by Bare JID string
    private val activeMucs = ConcurrentHashMap<String, MultiUserChat>()

    // Nicknames used when entering rooms (mapped by room JID string)
    private val roomNicknames = ConcurrentHashMap<String, String>()

    // MessageListener instances registered per room
    private val roomMessageListeners = ConcurrentHashMap<String, MutableList<MessageListener>>()
    
    // PresenceListener instances registered per room (StanzaListeners)
    private val roomPresenceListeners = ConcurrentHashMap<String, MutableList<org.jivesoftware.smack.StanzaListener>>()

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

    private val updateJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    init {
        // Listen for avatar updates to refresh UI automatically
        scope.launch {
            avatarManager.avatarUpdates.collect { updatedBareJid ->
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
        maxHistoryStanzas: Int = DEFAULT_HISTORY_STANZAS
    ): Result<MultiUserChat> = withContext(Dispatchers.IO) {
        try {
            val smackMucManager = getSmackMucManager()
            val bareJid = JidCreate.entityBareFrom(roomJid.trim())
            val nick = Resourcepart.from(nickname.trim().ifBlank { connectionManager.credentials.value.username.ifBlank { "User" } })

            val muc = smackMucManager.getMultiUserChat(bareJid)

            // If already joined, return existing instance
            if (muc.isJoined) {
                Log.d(TAG, "Already joined room $roomJid")
                activeMucs[roomJid] = muc
                roomNicknames[roomJid] = nick.toString()
                _joinedRooms.value = activeMucs.keys.toSet()
                updateOccupantsForRoom(roomJid, muc)
                return@withContext Result.success(muc)
            }

            // Build enter configuration
            val enterConfigBuilder = muc.getEnterConfigurationBuilder(nick)
            if (!password.isNullOrBlank()) {
                enterConfigBuilder.withPassword(password)
            }
            if (maxHistoryStanzas > 0) {
                enterConfigBuilder.requestMaxStanzasHistory(maxHistoryStanzas)
            } else {
                enterConfigBuilder.requestNoHistory()
            }

            Log.i(TAG, "Joining MUC room $roomJid as '$nick'...")
            try {
                muc.join(enterConfigBuilder.build())
            } catch (e: org.jivesoftware.smack.XMPPException.XMPPErrorException) {
                if (e.stanzaError.condition == StanzaError.Condition.conflict) {
                    Log.w(TAG, "Nickname '$nick' conflict in $roomJid, retrying with unique suffix...")
                    val newNick = Resourcepart.from("$nick" + "_" + (1000..9999).random())
                    val newEnterConfigBuilder = muc.getEnterConfigurationBuilder(newNick)
                    if (!password.isNullOrBlank()) {
                        newEnterConfigBuilder.withPassword(password)
                    }
                    if (maxHistoryStanzas > 0) {
                        newEnterConfigBuilder.requestMaxStanzasHistory(maxHistoryStanzas)
                    } else {
                        newEnterConfigBuilder.requestNoHistory()
                    }
                    muc.join(newEnterConfigBuilder.build())
                } else {
                    throw e
                }
            }

            // Register listeners
            registerMucListeners(roomJid, muc, nick.toString())

            activeMucs[roomJid] = muc
            roomNicknames[roomJid] = nick.toString()
            _joinedRooms.value = activeMucs.keys.toSet()

            updateOccupantsForRoom(roomJid, muc)
            Log.i(TAG, "Successfully joined room $roomJid")

            Result.success(muc)
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

            registerMucListeners(roomJid, muc, nick.toString())

            activeMucs[roomJid] = muc
            roomNicknames[roomJid] = nick.toString()
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
        val muc = activeMucs[roomJid] ?: return@withContext Result.failure(
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
        val muc = activeMucs[roomJid]
        if (muc == null) {
            Log.w(TAG, "Room $roomJid is not in active joined rooms list.")
            return@withContext Result.success(Unit)
        }

        try {
            Log.i(TAG, "Leaving MUC room $roomJid...")
            if (muc.isJoined) {
                muc.leave()
            }
            // Remove registered listeners
            roomMessageListeners.remove(roomJid)?.forEach { listener ->
                try {
                    muc.removeMessageListener(listener)
                } catch (e: Exception) {
                    // Ignore listener cleanup errors on leave
                }
            }
            
            // Remove presence listeners
            roomPresenceListeners.remove(roomJid)?.forEach { listener ->
                try {
                    connectionManager.getRawConnection()?.removeAsyncStanzaListener(listener)
                } catch (e: Exception) {
                    // Ignore listener cleanup errors on leave
                }
            }
            
            activeMucs.remove(roomJid)
            roomNicknames.remove(roomJid)
            _joinedRooms.value = activeMucs.keys.toSet()

            // Remove room from occupants map
            val currentOccupants = _roomOccupants.value.toMutableMap()
            currentOccupants.remove(roomJid)
            _roomOccupants.value = currentOccupants

            Log.i(TAG, "Left room $roomJid successfully.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error leaving room $roomJid: ${e.message}", e)
            // Even if network exception occurs, unregister locally
            activeMucs.remove(roomJid)
            roomNicknames.remove(roomJid)
            _joinedRooms.value = activeMucs.keys.toSet()
            Result.failure(e)
        }
    }

    /**
     * Changes or updates the room topic / subject.
     */
    suspend fun changeSubject(roomJid: String, newTopic: String): Result<Unit> = withContext(Dispatchers.IO) {
        val muc = activeMucs[roomJid] ?: return@withContext Result.failure(
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
        val muc = activeMucs[roomJid] ?: return@withContext Result.failure(
            IllegalStateException("Room $roomJid is not joined.")
        )
        try {
            val nick = Resourcepart.from(newNickname.trim())
            muc.changeNickname(nick)
            roomNicknames[roomJid] = nick.toString()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change nickname in $roomJid to '$newNickname': ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Sends a groupchat message to all participants in the MUC room.
     */
    suspend fun sendMessage(roomJid: String, body: String): Result<Unit> = withContext(Dispatchers.IO) {
        val muc = activeMucs[roomJid] ?: return@withContext Result.failure(
            IllegalStateException("Ruangan $roomJid belum dimasuki / tidak aktif.")
        )
        try {
            muc.sendMessage(body)
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

            for ((roomJid, muc) in activeMucs) {
                if (muc.isJoined) {
                    val nick = roomNicknames[roomJid] ?: conn.user?.resourceOrEmpty?.toString() ?: connectionManager.credentials.value.username.ifBlank { "User" }
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
        activeMucs.clear()
        roomNicknames.clear()
        roomMessageListeners.clear()
        _joinedRooms.value = emptySet()
        _roomOccupants.value = emptyMap()
    }

    /**
     * Checks if a room is currently joined.
     */
    fun isRoomJoined(roomJid: String): Boolean = activeMucs[roomJid]?.isJoined == true

    /**
     * Retrieves the active [MultiUserChat] instance for a room, or null if not joined.
     */
    fun getMultiUserChat(roomJid: String): MultiUserChat? = activeMucs[roomJid]

    /**
     * Returns current cached occupant list for a room.
     */
    fun getOccupants(roomJid: String): List<RoomOccupant> = _roomOccupants.value[roomJid] ?: emptyList()

    /**
     * Attaches a [MessageListener] implementation to a joined MUC room.
     *
     * @param roomJid Room bare JID
     * @param listener The MessageListener instance
     * @return true if successfully attached to the room
     */
    fun addMessageListener(roomJid: String, listener: MessageListener): Boolean {
        val muc = activeMucs[roomJid] ?: return false
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
        val muc = activeMucs[roomJid] ?: return false
        val success = muc.removeMessageListener(listener)
        roomMessageListeners[roomJid]?.remove(listener)
        return success
    }

    /**
     * Grants moderator role to a user in the room.
     */
    suspend fun grantModerator(roomJid: String, nickname: String): Result<Unit> = withContext(Dispatchers.IO) {
        val muc = activeMucs[roomJid] ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
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
        val muc = activeMucs[roomJid] ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
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
        val muc = activeMucs[roomJid] ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
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
        val muc = activeMucs[roomJid] ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
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
        val muc = activeMucs[roomJid] ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
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
        val muc = activeMucs[roomJid] ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
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
        val muc = activeMucs[roomJid] ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
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
        val muc = activeMucs[roomJid] ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
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
        val muc = activeMucs[roomJid] ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
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
        val muc = activeMucs[roomJid] ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
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
        val muc = activeMucs[roomJid] ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
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
        val muc = activeMucs[roomJid] ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
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
        val muc = activeMucs[roomJid] ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
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
        val muc = activeMucs[roomJid] ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
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
        val muc = activeMucs[roomJid] ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
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
        val muc = activeMucs[roomJid] ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
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
        val muc = activeMucs[roomJid] ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
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
        val muc = activeMucs[roomJid] ?: return@withContext Result.failure(IllegalStateException("Room not joined"))
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
        val muc = activeMucs[roomJid] ?: return null
        val listener = MucMessageListener(
            roomJid = roomJid,
            myNicknameProvider = { roomNicknames[roomJid] ?: "" },
            onMessageProcessed = { msg ->
                val senderJid = msg.senderJid
                val avatar = avatarManager.getAvatar(senderJid)
                val cachedCaps = getOccupantCaps(roomJid, msg.senderNickname)
                val finalCaps = if (cachedCaps != null && cachedCaps.iconResId != null) {
                    cachedCaps
                } else {
                    msg.senderClientCaps
                }
                onMessageReceived(msg.copy(senderAvatar = avatar, senderClientCaps = finalCaps))
            }
        )
        if (muc.addMessageListener(listener)) {
            roomMessageListeners.getOrPut(roomJid) { mutableListOf() }.add(listener)
            return listener
        }
        return null
    }

    /**
     * Attaches message, presence, and subject listeners to a [MultiUserChat] instance.
     */
    private fun registerMucListeners(roomJid: String, muc: MultiUserChat, myNickname: String) {
        // Instantiate and hook our dedicated MucMessageListener implementation
        val mucMessageListener = MucMessageListener(
            roomJid = roomJid,
            myNicknameProvider = { roomNicknames[roomJid] ?: myNickname },
            onMessageProcessed = { xmppMessage ->
                val senderJid = xmppMessage.senderJid
                val avatar = avatarManager.getAvatar(senderJid)
                val cachedCaps = getOccupantCaps(roomJid, xmppMessage.senderNickname)
                val finalCaps = if (cachedCaps != null && cachedCaps.iconResId != null) {
                    cachedCaps
                } else {
                    xmppMessage.senderClientCaps
                }
                _incomingMessages.tryEmit(xmppMessage.copy(senderAvatar = avatar, senderClientCaps = finalCaps))
            },
            onTopicUpdated = { jid, newTopic ->
                _topicUpdates.tryEmit(Pair(jid, newTopic))
            }
        )
        muc.addMessageListener(mucMessageListener)
        roomMessageListeners.getOrPut(roomJid) { mutableListOf() }.add(mucMessageListener)

        // Participant status listener (catches join/leave/kick/ban/role/nick events like BombusMod)
        muc.addParticipantStatusListener(object : org.jivesoftware.smackx.muc.ParticipantStatusListener {
            override fun joined(participant: EntityFullJid?) {
                Log.d(TAG, "MUC [$roomJid] Participant joined: $participant")
                updateOccupantsForRoom(roomJid, muc)
                
                val nick = participant?.resourceOrNull?.toString() ?: "Seseorang"
                val occ = muc.getOccupant(participant)
                val roleName = when (occ?.role) {
                    MUCRole.moderator -> "Moderator"
                    MUCRole.participant -> "Partisipan"
                    MUCRole.visitor -> "Pengunjung"
                    else -> "Partisipan"
                }
                val affName = when (occ?.affiliation) {
                    MUCAffiliation.owner -> " (Owner)"
                    MUCAffiliation.admin -> " (Admin)"
                    MUCAffiliation.member -> " (Member)"
                    else -> ""
                }
                val statusMsg = muc.getOccupantPresence(participant)?.status?.takeIf { it.isNotBlank() }?.let { " [\"$it\"]" } ?: ""
                val systemMsg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = "[$nick] bergabung ke ruangan sebagai $roleName$affName$statusMsg",
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.SYSTEM_JOIN
                )
                _incomingMessages.tryEmit(systemMsg)
            }

            override fun left(participant: EntityFullJid?) {
                Log.d(TAG, "MUC [$roomJid] Participant left: $participant")
                updateOccupantsForRoom(roomJid, muc)
                
                val nick = participant?.resourceOrNull?.toString() ?: "Seseorang"
                roomOccupantPresenceCache[roomJid]?.remove(nick)
                val systemMsg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = "[$nick] telah meninggalkan ruangan.",
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.SYSTEM_LEAVE
                )
                _incomingMessages.tryEmit(systemMsg)
            }

            override fun kicked(participant: EntityFullJid?, actor: org.jxmpp.jid.Jid?, reason: String?) {
                updateOccupantsForRoom(roomJid, muc)
                val nick = participant?.resourceOrNull?.toString() ?: "Seseorang"
                val actorNick = actor?.resourceOrNull?.toString() ?: actor?.toString() ?: "Admin"
                val reasonStr = if (!reason.isNullOrBlank()) " (Alasan: $reason)" else ""
                val msg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = "[$nick] dikeluarkan dari ruangan (Kicked) oleh $actorNick$reasonStr",
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.MODERATION
                )
                _incomingMessages.tryEmit(msg)
            }

            override fun banned(participant: EntityFullJid?, actor: org.jxmpp.jid.Jid?, reason: String?) {
                updateOccupantsForRoom(roomJid, muc)
                val nick = participant?.resourceOrNull?.toString() ?: "Seseorang"
                val actorNick = actor?.resourceOrNull?.toString() ?: actor?.toString() ?: "Admin"
                val reasonStr = if (!reason.isNullOrBlank()) " (Alasan: $reason)" else ""
                val msg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = "• [$nick] dilarang masuk ke ruangan (Banned) oleh $actorNick$reasonStr",
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.MODERATION
                )
                _incomingMessages.tryEmit(msg)
            }

            override fun voiceGranted(participant: EntityFullJid?) {
                updateOccupantsForRoom(roomJid, muc)
                val nick = participant?.resourceOrNull?.toString() ?: "Seseorang"
                val msg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = "• [$nick] diberikan hak bicara (Voice).",
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.MODERATION
                )
                _incomingMessages.tryEmit(msg)
            }

            override fun voiceRevoked(participant: EntityFullJid?) {
                updateOccupantsForRoom(roomJid, muc)
                val nick = participant?.resourceOrNull?.toString() ?: "Seseorang"
                val msg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = "• Hak bicara (Voice) [$nick] dicabut (Muted).",
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.MODERATION
                )
                _incomingMessages.tryEmit(msg)
            }

            override fun membershipGranted(participant: EntityFullJid?) {
                updateOccupantsForRoom(roomJid, muc)
                val nick = participant?.resourceOrNull?.toString() ?: "Seseorang"
                val msg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = "• [$nick] mendapatkan status Keanggotaan Resmi (Member).",
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.MODERATION
                )
                _incomingMessages.tryEmit(msg)
            }

            override fun membershipRevoked(participant: EntityFullJid?) {
                updateOccupantsForRoom(roomJid, muc)
                val nick = participant?.resourceOrNull?.toString() ?: "Seseorang"
                val msg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = "• Status Keanggotaan (Member) [$nick] telah dicabut.",
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.MODERATION
                )
                _incomingMessages.tryEmit(msg)
            }

            override fun moderatorGranted(participant: EntityFullJid?) {
                updateOccupantsForRoom(roomJid, muc)
                val nick = participant?.resourceOrNull?.toString() ?: "Seseorang"
                val msg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = "• [$nick] diangkat menjadi Moderator ruangan.",
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.MODERATION
                )
                _incomingMessages.tryEmit(msg)
            }

            override fun moderatorRevoked(participant: EntityFullJid?) {
                updateOccupantsForRoom(roomJid, muc)
                val nick = participant?.resourceOrNull?.toString() ?: "Seseorang"
                val msg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = "• Hak Moderator [$nick] telah dicabut.",
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.MODERATION
                )
                _incomingMessages.tryEmit(msg)
            }

            override fun ownershipGranted(participant: EntityFullJid?) {
                updateOccupantsForRoom(roomJid, muc)
                val nick = participant?.resourceOrNull?.toString() ?: "Seseorang"
                val msg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = "• [$nick] sekarang adalah Owner ruangan.",
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.MODERATION
                )
                _incomingMessages.tryEmit(msg)
            }

            override fun ownershipRevoked(participant: EntityFullJid?) {
                updateOccupantsForRoom(roomJid, muc)
                val nick = participant?.resourceOrNull?.toString() ?: "Seseorang"
                val msg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = "• Hak Owner [$nick] telah dicabut.",
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.MODERATION
                )
                _incomingMessages.tryEmit(msg)
            }

            override fun adminGranted(participant: EntityFullJid?) {
                updateOccupantsForRoom(roomJid, muc)
                val nick = participant?.resourceOrNull?.toString() ?: "Seseorang"
                val msg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = "• [$nick] diangkat menjadi Admin ruangan.",
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.MODERATION
                )
                _incomingMessages.tryEmit(msg)
            }

            override fun adminRevoked(participant: EntityFullJid?) {
                updateOccupantsForRoom(roomJid, muc)
                val nick = participant?.resourceOrNull?.toString() ?: "Seseorang"
                val msg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = "• Hak Admin [$nick] telah dicabut.",
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.MODERATION
                )
                _incomingMessages.tryEmit(msg)
            }

            override fun nicknameChanged(participant: EntityFullJid?, newNickname: Resourcepart?) {
                updateOccupantsForRoom(roomJid, muc)
                val oldNick = participant?.resourceOrNull?.toString() ?: "Seseorang"
                val newNick = newNickname?.toString() ?: "Nama Baru"
                val msg = XmppMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    roomJid = roomJid,
                    senderJid = roomJid,
                    senderNickname = "",
                    body = "[$oldNick] mengganti nickname menjadi [$newNick].",
                    timestamp = System.currentTimeMillis(),
                    messageType = com.ndregil.inchat.data.model.MessageType.SYSTEM_STATUS
                )
                _incomingMessages.tryEmit(msg)
            }
        })

        // Subject / Topic change listener
        muc.addSubjectUpdatedListener(SubjectUpdatedListener { newSubject, from ->
            if (newSubject != null) {
                Log.i(TAG, "MUC [$roomJid] Subject updated to '$newSubject' by $from")
                _topicUpdates.tryEmit(Pair(roomJid, newSubject))
            }
        })

        // Participant presence listener for real-time presence (Online, Chat, Away, DND, Offline, status message)
        val presenceListener = org.jivesoftware.smack.StanzaListener { stanza ->
            val presence = stanza as Presence
            val fromJid = presence.from
            if (fromJid != null && fromJid.asBareJid().toString().equals(roomJid, ignoreCase = true)) {
                val nick = fromJid.resourceOrNull?.toString() ?: return@StanzaListener

                // Update real-time occupant presence list
                updateOccupantsForRoom(roomJid, muc)

                val userPresence = presence.toUserPresence()
                val customStatus = presence.status ?: ""

                val roomCache = roomOccupantPresenceCache.getOrPut(roomJid) { mutableMapOf() }
                val prev = roomCache[nick]
                val currentPair = Pair(userPresence, customStatus)

                if (prev != currentPair) {
                    roomCache[nick] = currentPair

                    // Only emit status message if this is a subsequent change (avoid initial spam on join)
                    if (prev != null && nick != myNickname) {
                        val modeName = when (presence.mode) {
                            Presence.Mode.away -> "Away"
                            Presence.Mode.dnd -> "Jangan Ganggu (DND)"
                            Presence.Mode.chat -> "Bebas Mengobrol"
                            Presence.Mode.xa -> "Extended Away"
                            else -> if (presence.type == Presence.Type.unavailable) "Offline" else "Online"
                        }
                        val statusSnippet = if (customStatus.isNotBlank()) " (\"$customStatus\")" else ""
                        val presenceText = if (presence.type == Presence.Type.unavailable) {
                            "[$nick] sekarang Offline$statusSnippet"
                        } else {
                            "[$nick] sekarang [$modeName]$statusSnippet"
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
        roomPresenceListeners.getOrPut(roomJid) { mutableListOf() }.add(presenceListener)

        // Also add direct ParticipantListener on MUC
        try {
            muc.addParticipantListener { presence ->
                updateOccupantsForRoom(roomJid, muc)
            }
        } catch (e: Exception) {
            // Handled safely
        }
    }

    /**
     * Public method to force a refresh of the occupant list for a room.
     */
    fun refreshOccupants(roomJid: String) {
        val muc = activeMucs[roomJid] ?: return
        updateOccupantsForRoom(roomJid, muc)
    }

    /**
     * Retrieves cached ClientCaps for an occupant in a room.
     */
    fun getOccupantCaps(roomJid: String, nickname: String): com.ndregil.inchat.data.model.ClientCaps? {
        return _roomOccupants.value[roomJid]?.firstOrNull { it.nickname.equals(nickname, ignoreCase = true) }?.clientCaps
    }

    /**
     * Refreshes the cached list of occupants for a specific room with debouncing.
     */
    private fun updateOccupantsForRoom(roomJid: String, muc: MultiUserChat) {
        // Cancel previous pending update for this room
        updateJobs[roomJid]?.cancel()
        
        updateJobs[roomJid] = scope.launch(Dispatchers.Default) {
            // Small debounce to batch multiple rapid presence stanzas (e.g. on join)
            kotlinx.coroutines.delay(150)
            
            try {
                val myNickname = roomNicknames[roomJid] ?: ""
                val occupantsList = mutableListOf<RoomOccupant>()

                for (occupantJid in muc.occupants) {
                    val occupant: Occupant? = muc.getOccupant(occupantJid)
                    val nickname = occupant?.nick?.toString()
                        ?: occupantJid.resourceOrNull?.toString()
                        ?: occupantJid.toString().substringAfterLast("/")

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
                    val userPresence = smackPresence?.toUserPresence() ?: UserPresence.ONLINE
                    val statusMessage = smackPresence?.status ?: ""
                    val defaultStatus = when (userPresence) {
                        UserPresence.ONLINE -> "Online"
                        UserPresence.CHAT -> "Bebas Chat"
                        UserPresence.AWAY -> "Away"
                        UserPresence.XA -> "Extended Away"
                        UserPresence.DND -> "Jangan Ganggu"
                        UserPresence.OFFLINE -> "Offline"
                    }
                    val myJid = connectionManager.credentials.value.jid
                    val realJid = if (isMe && myJid.isNotBlank()) {
                        myJid
                    } else {
                        occupant?.jid?.asBareJid()?.toString() ?: occupantJid.toString()
                    }

                    val extractedCaps = ClientRegistryManager.extractCapsFromPresence(smackPresence)

                    val occupantCaps = if (isMe) {
                        com.ndregil.inchat.data.model.ClientCaps.resolveFor(
                            name = "InChat",
                            jid = realJid,
                            node = "http://ndregil.inchat"
                        )
                    } else {
                        com.ndregil.inchat.data.model.ClientCaps.resolveFor(
                            name = nickname,
                            jid = realJid,
                            node = extractedCaps?.first ?: "",
                            ver = extractedCaps?.second ?: ""
                        )
                    }

                    // Fetch avatar (will trigger fetch if missing, with fallback for current user)
                    // Memory cache check is fast, DB/Network fetch is async in Manager
                    val avatar = (if (realJid.isNotBlank()) avatarManager.getAvatar(realJid) else null)
                        ?: (if (isMe && myJid.isNotBlank()) avatarManager.getAvatar(myJid) else null)

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
                            avatar = avatar,
                            clientCaps = occupantCaps
                        )
                    )
                }

                val currentMap = _roomOccupants.value.toMutableMap()
                currentMap[roomJid] = occupantsList
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
        since: Long,
        limit: Int = 50
    ): Result<List<org.jivesoftware.smack.packet.Message>> = withContext(Dispatchers.IO) {
        try {
            val conn = connectionManager.getRawConnection() ?: return@withContext Result.failure(Exception("Not connected"))
            val bareJid = JidCreate.entityBareFrom(roomJid)
            val mamManager = MamManager.getInstanceFor(conn, bareJid)
            val queryArgsBuilder = MamManager.MamQueryArgs.builder()
                .setResultPageSize(limit)
            val query = mamManager.queryArchive(queryArgsBuilder.build())
            Result.success(query.messages)
        } catch (e: org.jivesoftware.smack.XMPPException.XMPPErrorException) {
            if (e.stanzaError?.condition == org.jivesoftware.smack.packet.StanzaError.Condition.feature_not_implemented) {
                Log.i(TAG, "MAM not supported by $roomJid, skipping history fetch.")
                Result.success(emptyList())
            } else {
                Log.e(TAG, "Failed to fetch MAM history for $roomJid: ${e.message}")
                Result.failure(e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch MAM history for $roomJid: ${e.message}")
            Result.failure(e)
        }
    }
}
