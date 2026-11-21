package com.ndregil.inchat.data.network

import android.util.Log
import com.ndregil.inchat.data.model.RosterContact
import com.ndregil.inchat.data.model.UserPresence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jivesoftware.smack.packet.Presence
import org.jivesoftware.smack.packet.PresenceBuilder
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smack.roster.RosterEntry
import org.jivesoftware.smack.roster.RosterGroup
import org.jivesoftware.smack.roster.RosterListener
import org.jivesoftware.smack.roster.RosterLoadedListener
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.Jid
import org.jxmpp.jid.impl.JidCreate

/**
 * Manager class using Smack's Roster API to fetch, add, remove, rename, and
 * monitor online/offline presence status of XMPP contacts.
 *
 * Implements [RosterListener] to observe real-time roster updates and presence broadcasts.
 * Includes demo fallback mode for offline testing and rapid prototyping.
 */
class RosterManager(
    private val connectionManager: XmppConnectionManager,
    private val avatarManager: XmppAvatarManager,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    companion object {
        private const val TAG = "RosterManager"

        val DEMO_CONTACTS = listOf(
            RosterContact(
                jid = "smartng@jabber.org",
                name = "SmartNG.1",
                presence = UserPresence.ONLINE,
                statusMessage = "Available for direct messaging",
                groups = listOf("Jabber", "Admins"),
                clientCaps = com.ndregil.inchat.data.model.ClientCaps.resolveFor("SmartNG.1", "smartng@jabber.org", node = "http://conversations.im", ver = "2.9.8"),
                pepActivity = "Working",
                pepStatus = "Available"
            ),
            RosterContact(
                jid = "tfox@conference.org",
                name = "tfox",
                presence = UserPresence.DND,
                statusMessage = "In a meeting - Do Not Disturb",
                groups = listOf("Devs"),
                clientCaps = com.ndregil.inchat.data.model.ClientCaps.resolveFor("tfox", "tfox@conference.org", node = "http://psi-plus.com", ver = "1.5.0"),
                pepMood = "Focused",
                pepStatus = "Busy"
            ),
            RosterContact(
                jid = "i_duthe@jabber.net",
                name = "i_duthe",
                presence = UserPresence.AWAY,
                statusMessage = "Stepped out for coffee",
                groups = listOf("Friends"),
                clientCaps = com.ndregil.inchat.data.model.ClientCaps.resolveFor("i_duthe", "i_duthe@jabber.net", node = "http://miranda-ng.org/caps", ver = "0.96"),
                pepTune = "Daft Punk - One More Time",
                pepStatus = "Away"
            )
        )
    }

    // Reactive StateFlows for UI observation
    private val _contacts = MutableStateFlow<List<RosterContact>>(emptyList())
    val contacts: StateFlow<List<RosterContact>> = _contacts.asStateFlow()

    private val _presenceMap = MutableStateFlow<Map<String, UserPresence>>(emptyMap())
    val presenceMap: StateFlow<Map<String, UserPresence>> = _presenceMap.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _autoApproveSubscription = MutableStateFlow(true)
    val autoApproveSubscription: StateFlow<Boolean> = _autoApproveSubscription.asStateFlow()

    fun setInitialContacts(persistedContacts: List<RosterContact>) {
        if (persistedContacts.isNotEmpty()) {
            _contacts.value = persistedContacts
            _presenceMap.value = persistedContacts.associate { it.jid to it.presence }
            _isLoaded.value = true
        }
    }

    fun loadDemoContactsIfEmpty() {
        if (_contacts.value.isEmpty()) {
            _contacts.value = DEMO_CONTACTS
            _presenceMap.value = DEMO_CONTACTS.associate { it.jid to it.presence }
            _isLoaded.value = true
        }
    }

    fun setAutoApproveSubscription(enabled: Boolean) {
        _autoApproveSubscription.value = enabled
        activeRoster?.let { roster ->
            roster.subscriptionMode = if (enabled) Roster.SubscriptionMode.accept_all else Roster.SubscriptionMode.manual
        }
    }

    // Active Smack Roster instance & attached connection listeners
    @Volatile
    private var activeRoster: Roster? = null

    @Volatile
    private var attachedListenerConnection: org.jivesoftware.smack.tcp.XMPPTCPConnection? = null
    private var pepStanzaListener: org.jivesoftware.smack.StanzaListener? = null
    private var subscriptionStanzaListener: org.jivesoftware.smack.StanzaListener? = null

    private val queriedContactVersions = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    init {
        coroutineScope.launch {
            avatarManager.avatarUpdates.collect { updatedJid ->
                if (_contacts.value.isEmpty()) return@collect
                if (updatedJid.isNotBlank()) {
                    val newAvatar = avatarManager.getAvatar(updatedJid)
                    if (newAvatar != null) {
                        _contacts.value = _contacts.value.map { contact ->
                            if (contact.jid.equals(updatedJid, ignoreCase = true)) {
                                contact.copy(avatar = newAvatar)
                            } else contact
                        }
                    }
                } else if (activeRoster?.isLoaded == true) {
                    refreshContactsFromRoster()
                }
            }
        }
    }

    private fun queryContactVersionIfNeeded(fullJid: String, bareJid: String) {
        if (!queriedContactVersions.add(fullJid)) return
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val result = connectionManager.querySoftwareVersion(fullJid)
                if (result.isSuccess) {
                    val caps = result.getOrNull()
                    if (caps != null && caps.iconResId != null) {
                        Log.i(TAG, "Resolved software version for contact $bareJid: ${caps.clientName} (icon=${caps.iconResId})")
                        _contacts.value = _contacts.value.map { contact ->
                            if (contact.jid.equals(bareJid, ignoreCase = true)) {
                                contact.copy(clientCaps = caps)
                            } else {
                                contact
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Contact version query failed for $fullJid: ${e.message}")
            }
        }
    }

    // Listener for Roster events
    private val rosterListener = object : RosterListener {
        override fun entriesAdded(addresses: Collection<Jid>) {
            Log.d(TAG, "Roster entries added: ${addresses.map { it.toString() }}")
            coroutineScope.launch {
                refreshContactsFromRoster()
            }
        }

        override fun entriesUpdated(addresses: Collection<Jid>) {
            Log.d(TAG, "Roster entries updated: ${addresses.map { it.toString() }}")
            coroutineScope.launch {
                refreshContactsFromRoster()
            }
        }

        override fun entriesDeleted(addresses: Collection<Jid>) {
            Log.d(TAG, "Roster entries deleted: ${addresses.map { it.toString() }}")
            val deletedBareJids = addresses.map { it.asBareJid().toString() }.toSet()
            _contacts.value = _contacts.value.filterNot { it.jid in deletedBareJids }
            _presenceMap.value = _presenceMap.value.filterKeys { it !in deletedBareJids }
        }

        override fun presenceChanged(presence: Presence) {
            val fromJid = presence.from?.asBareJid()?.toString() ?: return
            val fullJid = presence.from?.toString() ?: fromJid
            val userPresence = presence.toUserPresence()
            val statusMsg = presence.status ?: ""

            // XEP-0115: Entity Capabilities
            val extractedCaps = ClientRegistryManager.extractCapsFromPresence(presence)

            // XEP-0172: User Nickname
            val extractedNick = extractNickname(presence)

            val existing = _contacts.value.find { it.jid.equals(fromJid, ignoreCase = true) }

            val contactName = existing?.name ?: extractedNick ?: ""
            var contactCaps = com.ndregil.inchat.data.model.ClientCaps.resolveFor(
                name = contactName,
                jid = fullJid,
                node = extractedCaps?.first ?: existing?.clientCaps?.rawNode ?: "",
                ver = extractedCaps?.second ?: existing?.clientCaps?.ver ?: ""
            )
            if (contactCaps.iconResId == null && presence.from != null) {
                try {
                    val discoInfo = org.jivesoftware.smackx.caps.EntityCapsManager.getDiscoverInfoByUser(presence.from)
                    val identityName = discoInfo?.identities?.firstOrNull { !it.name.isNullOrBlank() }?.name
                    if (!identityName.isNullOrBlank()) {
                        val resolvedFromDisco = com.ndregil.inchat.data.model.ClientCaps.resolveFor(
                            name = identityName,
                            jid = fullJid,
                            node = if (ClientRegistryManager.isInChatExact(identityName)) "http://ndregil.inchat" else (extractedCaps?.first ?: ""),
                            ver = extractedCaps?.second ?: ""
                        )
                        if (resolvedFromDisco.iconResId != null) {
                            contactCaps = resolvedFromDisco
                        }
                    }
                } catch (_: Exception) {}
            }
            if (contactCaps.isIdentified()) {
                ClientRegistryManager.putCachedCaps(fullJid, contactCaps)
                ClientRegistryManager.putCachedCaps(fromJid, contactCaps)
            }

            Log.d(TAG, "Presence changed for $fromJid: $userPresence ($statusMsg), nick: $extractedNick, caps: ${contactCaps.clientName}")

            // Update presence map
            _presenceMap.value = _presenceMap.value + (fromJid to userPresence)

            // Update contact in contacts list with XEP-0115 caps and XEP-0172 nickname
            _contacts.value = _contacts.value.map { contact ->
                if (contact.jid.equals(fromJid, ignoreCase = true)) {
                    contact.copy(
                        presence = userPresence,
                        statusMessage = if (statusMsg.isNotBlank()) statusMsg else contact.statusMessage,
                        clientCaps = contactCaps,
                        nickname = extractedNick ?: contact.nickname
                    )
                } else {
                    contact
                }
            }
        }
    }

    private val rosterLoadedListener = object : RosterLoadedListener {
        override fun onRosterLoaded(roster: Roster) {
            Log.i(TAG, "Smack Roster loaded callback received. Entry count: ${roster.entries.size}")
            _isLoaded.value = true
            coroutineScope.launch {
                refreshContactsFromRoster()
            }
        }

        override fun onRosterLoadingFailed(exception: Exception) {
            Log.e(TAG, "Smack Roster loading failed: ${exception.message}", exception)
            _isLoading.value = false
        }
    }

    private fun setupPepStanzaListener(connection: org.jivesoftware.smack.tcp.XMPPTCPConnection) {
        pepStanzaListener?.let { oldListener ->
            try { attachedListenerConnection?.removeAsyncStanzaListener(oldListener) } catch (_: Exception) {}
            try { connection.removeAsyncStanzaListener(oldListener) } catch (_: Exception) {}
        }
        val filter = org.jivesoftware.smack.filter.StanzaTypeFilter(org.jivesoftware.smack.packet.Message::class.java)
        val listener = org.jivesoftware.smack.StanzaListener { stanza ->
            val msg = stanza as? org.jivesoftware.smack.packet.Message ?: return@StanzaListener
            val fromJid = msg.from?.asBareJid()?.toString() ?: return@StanzaListener

            val event = try {
                org.jivesoftware.smackx.pubsub.EventElement.from(msg)
            } catch (e: Exception) { null } ?: return@StanzaListener

            var activity: String? = null
            var mood: String? = null
            var tune: String? = null
            var pepStatus: String? = null
            var pepNick: String? = null

            event.extensions.filterIsInstance<org.jivesoftware.smackx.pubsub.ItemsExtension>().forEach { items ->
                when (items.node) {
                    "http://jabber.org/protocol/tune" -> {
                        val item = items.items.firstOrNull() as? org.jivesoftware.smackx.pubsub.PayloadItem<*>
                        val xml = item?.payload?.toString() ?: ""
                        val artist = "artist>([^<]+)<".toRegex().find(xml)?.groupValues?.get(1)
                        val title = "title>([^<]+)<".toRegex().find(xml)?.groupValues?.get(1)
                        if (!title.isNullOrBlank()) {
                            tune = if (!artist.isNullOrBlank()) "$artist - $title" else title
                        }
                    }
                    "http://jabber.org/protocol/activity" -> {
                        val item = items.items.firstOrNull() as? org.jivesoftware.smackx.pubsub.PayloadItem<*>
                        val xml = item?.payload?.toString() ?: ""
                        val act = "text>([^<]+)<".toRegex().find(xml)?.groupValues?.get(1)
                        if (!act.isNullOrBlank()) activity = act
                    }
                    "http://jabber.org/protocol/mood" -> {
                        val item = items.items.firstOrNull() as? org.jivesoftware.smackx.pubsub.PayloadItem<*>
                        val xml = item?.payload?.toString() ?: ""
                        val md = "text>([^<]+)<".toRegex().find(xml)?.groupValues?.get(1)
                        if (!md.isNullOrBlank()) mood = md
                    }
                    "http://jabber.org/protocol/status" -> {
                        val item = items.items.firstOrNull() as? org.jivesoftware.smackx.pubsub.PayloadItem<*>
                        val xml = item?.payload?.toString() ?: ""
                        val st = "status>([^<]+)<".toRegex().find(xml)?.groupValues?.get(1)
                        if (!st.isNullOrBlank()) pepStatus = st
                    }
                    "http://jabber.org/protocol/nick" -> {
                        val item = items.items.firstOrNull() as? org.jivesoftware.smackx.pubsub.PayloadItem<*>
                        val xml = item?.payload?.toString() ?: ""
                        val nk = "<nick[^>]*>([^<]+)</nick>".toRegex().find(xml)?.groupValues?.get(1)
                            ?: "<text[^>]*>([^<]+)</text>".toRegex().find(xml)?.groupValues?.get(1)
                        if (!nk.isNullOrBlank()) pepNick = nk.trim()
                    }
                }
            }

            if (activity != null || mood != null || tune != null || pepStatus != null || pepNick != null) {
                _contacts.value = _contacts.value.map { contact ->
                    if (contact.jid.equals(fromJid, ignoreCase = true)) {
                        contact.copy(
                            pepActivity = activity ?: contact.pepActivity,
                            pepMood = mood ?: contact.pepMood,
                            pepTune = tune ?: contact.pepTune,
                            pepStatus = pepStatus ?: contact.pepStatus,
                            statusMessage = pepStatus ?: contact.statusMessage,
                            nickname = pepNick ?: contact.nickname
                        )
                    } else contact
                }
            }
        }
        pepStanzaListener = listener
        attachedListenerConnection = connection
        connection.addAsyncStanzaListener(listener, filter)
    }

    private fun setupSubscriptionListener(connection: org.jivesoftware.smack.tcp.XMPPTCPConnection) {
        subscriptionStanzaListener?.let { oldListener ->
            try { attachedListenerConnection?.removeAsyncStanzaListener(oldListener) } catch (_: Exception) {}
            try { connection.removeAsyncStanzaListener(oldListener) } catch (_: Exception) {}
        }
        val filter = org.jivesoftware.smack.filter.StanzaTypeFilter(org.jivesoftware.smack.packet.Presence::class.java)
        val listener = org.jivesoftware.smack.StanzaListener { stanza ->
            val presence = stanza as? org.jivesoftware.smack.packet.Presence ?: return@StanzaListener
            when (presence.type) {
                org.jivesoftware.smack.packet.Presence.Type.subscribe -> {
                    if (!_autoApproveSubscription.value) {
                        Log.i(TAG, "Auto-approve subscription is disabled. Ignoring auto-accept for $presence")
                        return@StanzaListener
                    }
                    val fromJid = presence.from?.asBareJid() ?: return@StanzaListener
                    Log.i(TAG, "Auto-accepting subscription request from $fromJid with mutual subscription")
                    try {
                        // 1. Accept incoming subscription request
                        val response = PresenceBuilder.buildPresence()
                            .ofType(org.jivesoftware.smack.packet.Presence.Type.subscribed)
                            .to(fromJid)
                            .build()
                        connection.sendStanza(response)

                        // 2. Request mutual presence subscription back
                        val subscribeBack = PresenceBuilder.buildPresence()
                            .ofType(org.jivesoftware.smack.packet.Presence.Type.subscribe)
                            .to(fromJid)
                            .build()
                        connection.sendStanza(subscribeBack)

                        val roster = activeRoster ?: Roster.getInstanceFor(connection)
                        if (!roster.contains(fromJid)) {
                            roster.createItem(fromJid, fromJid.localpartOrNull?.asUnescapedString() ?: fromJid.toString(), null)
                        }
                        coroutineScope.launch {
                            refreshContactsFromRoster()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to auto-accept subscription: ${e.message}", e)
                    }
                }
                org.jivesoftware.smack.packet.Presence.Type.subscribed -> {
                    Log.i(TAG, "Subscription accepted by peer: ${presence.from}")
                    coroutineScope.launch {
                        refreshContactsFromRoster()
                    }
                }
                org.jivesoftware.smack.packet.Presence.Type.unsubscribed -> {
                    Log.i(TAG, "Subscription removed by peer: ${presence.from}")
                    coroutineScope.launch {
                        refreshContactsFromRoster()
                    }
                }
                else -> {
                    // Handled by rosterListener
                }
            }
        }
        subscriptionStanzaListener = listener
        attachedListenerConnection = connection
        connection.addAsyncStanzaListener(listener, filter)
    }

    @Synchronized
    private fun ensureRosterAttached(connection: org.jivesoftware.smack.tcp.XMPPTCPConnection): Roster {
        val roster = Roster.getInstanceFor(connection)
        if (activeRoster !== roster) {
            try {
                activeRoster?.removeRosterListener(rosterListener)
                activeRoster?.removeRosterLoadedListener(rosterLoadedListener)
            } catch (_: Exception) {}
            activeRoster = roster
            roster.subscriptionMode = if (_autoApproveSubscription.value) {
                Roster.SubscriptionMode.accept_all
            } else {
                Roster.SubscriptionMode.manual
            }
            roster.removeRosterListener(rosterListener)
            roster.addRosterListener(rosterListener)
            roster.removeRosterLoadedListener(rosterLoadedListener)
            roster.addRosterLoadedListener(rosterLoadedListener)
        }
        if (attachedListenerConnection !== connection || pepStanzaListener == null || subscriptionStanzaListener == null) {
            setupPepStanzaListener(connection)
            setupSubscriptionListener(connection)
        }
        return roster
    }

    /**
     * Initializes the Smack Roster for the active connection, sets subscription mode,
     * and attaches listeners.
     */
    suspend fun initializeRoster(): Result<Unit> = withContext(Dispatchers.IO) {
        val connection = connectionManager.getRawConnection()
        if (connection == null || !connection.isAuthenticated) {
            Log.d(TAG, "Not connected or authenticated. Skipping Smack Roster init.")
            _isLoaded.value = true
            return@withContext Result.success(Unit)
        }

        try {
            _isLoading.value = true
            _lastError.value = null

            val roster = ensureRosterAttached(connection)

            if (roster.isLoaded) {
                _isLoaded.value = true
                refreshContactsFromRoster()
            } else {
                try {
                    roster.reload()
                } catch (e: Exception) {
                    Log.w(TAG, "Initial roster reload warning: ${e.message}")
                }
            }

            _isLoading.value = false
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Smack Roster: ${e.message}", e)
            _lastError.value = e.localizedMessage ?: "Roster initialization failure"
            _isLoading.value = false
            Result.failure(e)
        }
    }

    /**
     * Fetches or refreshes contacts list from Smack Roster, with offline fallback.
     */
    suspend fun loadContacts(forceReload: Boolean = false): Result<List<RosterContact>> = withContext(Dispatchers.IO) {
        val connection = connectionManager.getRawConnection()
        if (connection == null || !connection.isAuthenticated) {
            _isLoaded.value = true
            return@withContext Result.success(_contacts.value)
        }

        try {
            _isLoading.value = true
            _lastError.value = null

            val roster = ensureRosterAttached(connection)

            if (!roster.isLoaded || forceReload) {
                try {
                    roster.reload()
                } catch (e: Exception) {
                    Log.w(TAG, "Roster reload caught non-fatal exception: ${e.message}")
                }
            }

            val contactsList = refreshContactsFromRoster()
            _isLoading.value = false
            _isLoaded.value = true
            Result.success(contactsList)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching contacts from Roster: ${e.message}", e)
            _lastError.value = e.localizedMessage ?: "Failed to load contacts"
            _isLoading.value = false
            Result.failure(e)
        }
    }

    /**
     * Adds a new contact (XEP-0160 / RFC 6121) to the roster, assigning optional group,
     * and sends a subscription request stanza.
     */
    suspend fun addContact(
        jidString: String,
        name: String,
        groupName: String? = null,
        isDemoMode: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val normalizedInput = jidString.trim()
            .removePrefix("xmpp:")
            .substringBefore("?")
            .substringBefore("/")
            .trim()
        if (normalizedInput.isBlank() || !normalizedInput.contains("@")) {
            return@withContext Result.failure(
                IllegalArgumentException("Format JID tidak valid: '$normalizedInput'. Gunakan format user@domain.")
            )
        }

        val bareJid: BareJid = try {
            JidCreate.bareFrom(normalizedInput)
        } catch (e: Exception) {
            return@withContext Result.failure(
                IllegalArgumentException("Format JID tidak valid: '$normalizedInput'")
            )
        }

        val bareJidStr = bareJid.toString()
        if (!bareJid.hasLocalpart()) {
            return@withContext Result.failure(
                IllegalArgumentException("Format JID harus menyertakan nama pengguna (user@domain).")
            )
        }

        val cleanName = name.trim().ifBlank { bareJid.localpartOrNull?.asUnescapedString() ?: bareJidStr }
        val cleanGroup = groupName?.trim()?.ifBlank { null }
        val connection = connectionManager.getRawConnection()

        if (connection != null && connection.isAuthenticated) {
            if (connection.user?.asBareJid()?.equals(bareJid) == true) {
                return@withContext Result.failure(
                    IllegalArgumentException("Tidak dapat menambahkan akun Anda sendiri sebagai kontak.")
                )
            }
            try {
                val roster = ensureRosterAttached(connection)
                if (!roster.isLoaded) {
                    try {
                        roster.reloadAndWait()
                    } catch (e: Exception) {
                        Log.w(TAG, "Roster reloadAndWait warning before addContact: ${e.message}")
                    }
                }

                val existingEntry = roster.getEntry(bareJid)
                if (existingEntry != null) {
                    // Contact already exists in Smack roster: update display name / group & re-send subscribe if needed
                    if (name.trim().isNotBlank() && existingEntry.name != cleanName) {
                        existingEntry.name = cleanName
                    }
                    if (cleanGroup != null) {
                        val grp = roster.getGroup(cleanGroup) ?: roster.createGroup(cleanGroup)
                        if (!grp.contains(existingEntry)) {
                            grp.addEntry(existingEntry)
                        }
                    }
                    if (existingEntry.type != org.jivesoftware.smack.roster.packet.RosterPacket.ItemType.to &&
                        existingEntry.type != org.jivesoftware.smack.roster.packet.RosterPacket.ItemType.both
                    ) {
                        val subscribePresence = PresenceBuilder.buildPresence()
                            .ofType(Presence.Type.subscribe)
                            .to(bareJid)
                            .build()
                        connection.sendStanza(subscribePresence)
                    }
                } else {
                    val groups = if (cleanGroup != null) arrayOf(cleanGroup) else null
                    roster.createItem(bareJid, cleanName, groups)

                    val subscribePresence = PresenceBuilder.buildPresence()
                        .ofType(Presence.Type.subscribe)
                        .to(bareJid)
                        .build()
                    connection.sendStanza(subscribePresence)
                }

                Log.i(TAG, "Contact added to roster and subscription sent: $bareJidStr")

                val existingContact = _contacts.value.find { it.jid.equals(bareJidStr, ignoreCase = true) }
                val immediateContact = (existingContact ?: RosterContact(
                    jid = bareJidStr,
                    name = cleanName,
                    presence = UserPresence.OFFLINE,
                    statusMessage = "Menunggu konfirmasi...",
                    groups = if (cleanGroup != null) listOf(cleanGroup) else emptyList(),
                    subscriptionType = "none",
                    isPendingApproval = true
                )).copy(
                    name = if (name.trim().isNotBlank()) cleanName else (existingContact?.name?.ifBlank { cleanName } ?: cleanName),
                    groups = if (cleanGroup != null) ((existingContact?.groups ?: emptyList()) + cleanGroup).distinct() else (existingContact?.groups ?: emptyList())
                )

                // Update state immediately first so UI shows the contact without waiting for Roster Push
                _contacts.value = _contacts.value.filterNot { it.jid.equals(bareJidStr, ignoreCase = true) } + immediateContact
                _presenceMap.value = _presenceMap.value + (bareJidStr to immediateContact.presence)

                // Only rebuild from Smack Roster if the server's Roster Push has already populated the entry
                if (roster.getEntry(bareJid) != null) {
                    refreshContactsFromRoster()
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add contact '$bareJidStr' to Smack roster: ${e.message}", e)
                val msg = when (e) {
                    is org.jivesoftware.smack.XMPPException.XMPPErrorException ->
                        "Server menolak kontak (${e.stanzaError.condition}): ${e.stanzaError.descriptiveText ?: e.message}"
                    is org.jivesoftware.smack.SmackException.NoResponseException ->
                        "Server XMPP tidak merespons tepat waktu. Coba lagi."
                    is org.jivesoftware.smack.SmackException.NotConnectedException ->
                        "Koneksi XMPP terputus. Tunggu hingga terhubung kembali."
                    else -> e.localizedMessage ?: "Gagal menambahkan kontak"
                }
                _lastError.value = msg
                Result.failure(Exception(msg, e))
            }
        } else if (isDemoMode) {
            val newContact = RosterContact(
                jid = bareJidStr,
                name = cleanName,
                presence = UserPresence.ONLINE,
                statusMessage = "Ditambahkan di sesi lokal",
                groups = if (cleanGroup != null) listOf(cleanGroup) else listOf("Kontak"),
                subscriptionType = "both"
            )
            _contacts.value = _contacts.value.filterNot { it.jid.equals(bareJidStr, ignoreCase = true) } + newContact
            _presenceMap.value = _presenceMap.value + (bareJidStr to UserPresence.ONLINE)
            Result.success(Unit)
        } else {
            val msg = "Belum terhubung ke server XMPP. Pastikan akun sudah terhubung sebelum menambah kontak."
            _lastError.value = msg
            Result.failure(IllegalStateException(msg))
        }
    }

    /**
     * Removes an entry from the XMPP roster and sends an unsubscribe stanza.
     */
    suspend fun removeContact(jidString: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanJid = jidString.trim()
        val bareJid = try {
            JidCreate.bareFrom(cleanJid)
        } catch (e: Exception) {
            return@withContext Result.failure(IllegalArgumentException("Invalid JID: $cleanJid"))
        }

        val connection = connectionManager.getRawConnection()
        if (connection != null && connection.isAuthenticated) {
            try {
                val roster = ensureRosterAttached(connection)
                val entry = roster.getEntry(bareJid)
                if (entry != null) {
                    roster.removeEntry(entry)
                }

                // Send unsubscribe stanza
                val unsubscribePresence = PresenceBuilder.buildPresence()
                    .ofType(Presence.Type.unsubscribe)
                    .to(bareJid)
                    .build()
                connection.sendStanza(unsubscribePresence)

                val bareJidStr = bareJid.toString()
                _contacts.value = _contacts.value.filterNot { it.jid.equals(bareJidStr, ignoreCase = true) }
                _presenceMap.value = _presenceMap.value.filterKeys { !it.equals(bareJidStr, ignoreCase = true) }

                Log.i(TAG, "Contact removed from roster: $bareJid")
                refreshContactsFromRoster()
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove contact $cleanJid: ${e.message}", e)
                _lastError.value = e.localizedMessage ?: "Failed to remove contact"
                Result.failure(e)
            }
        } else {
            // Offline demo removal
            _contacts.value = _contacts.value.filterNot { it.jid.equals(cleanJid, ignoreCase = true) }
            _presenceMap.value = _presenceMap.value.filterKeys { !it.equals(cleanJid, ignoreCase = true) }
            Result.success(Unit)
        }
    }

    /**
     * Renames a contact's display name in the roster.
     */
    suspend fun renameContact(jidString: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Name cannot be blank"))
        }

        val cleanJid = jidString.trim()
        val connection = connectionManager.getRawConnection()

        if (connection != null && connection.isAuthenticated) {
            try {
                val bareJid = JidCreate.bareFrom(cleanJid)
                val roster = ensureRosterAttached(connection)
                val entry = roster.getEntry(bareJid)
                if (entry != null) {
                    entry.name = cleanName
                    Log.i(TAG, "Contact renamed in Smack roster: $bareJid -> $cleanName")
                }
                refreshContactsFromRoster()
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to rename contact $cleanJid: ${e.message}", e)
                Result.failure(e)
            }
        } else {
            // Offline demo update
            _contacts.value = _contacts.value.map { contact ->
                if (contact.jid.equals(cleanJid, ignoreCase = true)) {
                    contact.copy(name = cleanName)
                } else {
                    contact
                }
            }
            Result.success(Unit)
        }
    }

    /**
     * Adds a contact to a specified group in the roster.
     */
    suspend fun addContactToGroup(jidString: String, groupName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanGroup = groupName.trim()
        if (cleanGroup.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Group name cannot be blank"))
        }

        val cleanJid = jidString.trim()
        val connection = connectionManager.getRawConnection()

        if (connection != null && connection.isAuthenticated) {
            try {
                val bareJid = JidCreate.bareFrom(cleanJid)
                val roster = ensureRosterAttached(connection)
                val entry = roster.getEntry(bareJid)
                    ?: return@withContext Result.failure(IllegalStateException("Contact not found in roster"))

                var group = roster.getGroup(cleanGroup)
                if (group == null) {
                    group = roster.createGroup(cleanGroup)
                }
                group.addEntry(entry)

                Log.i(TAG, "Added contact $cleanJid to group $cleanGroup")
                refreshContactsFromRoster()
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to assign group: ${e.message}", e)
                Result.failure(e)
            }
        } else {
            _contacts.value = _contacts.value.map { contact ->
                if (contact.jid.equals(cleanJid, ignoreCase = true)) {
                    val updatedGroups = (contact.groups + cleanGroup).distinct()
                    contact.copy(groups = updatedGroups)
                } else {
                    contact
                }
            }
            Result.success(Unit)
        }
    }

    /**
     * Queries presence for a given JID from cache or Smack Roster.
     */
    fun getContactPresence(jidString: String): UserPresence {
        _presenceMap.value[jidString]?.let { return it }

        val connection = connectionManager.getRawConnection()
        if (connection != null && connection.isAuthenticated) {
            try {
                val bareJid = JidCreate.bareFrom(jidString)
                val roster = Roster.getInstanceFor(connection)
                val presence = roster.getPresence(bareJid)
                val userPresence = presence.toUserPresence()
                _presenceMap.value = _presenceMap.value + (jidString to userPresence)
                return userPresence
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }
        return UserPresence.OFFLINE
    }

    /**
     * Sends a manual subscription request to a peer.
     */
    suspend fun sendSubscriptionRequest(jidString: String): Result<Unit> = withContext(Dispatchers.IO) {
        val connection = connectionManager.getRawConnection()
            ?: return@withContext Result.failure(IllegalStateException("Connection not available"))

        try {
            val bareJid = JidCreate.bareFrom(jidString.trim())
            val presence = PresenceBuilder.buildPresence()
                .ofType(Presence.Type.subscribe)
                .to(bareJid)
                .build()
            connection.sendStanza(presence)
            Log.i(TAG, "Sent subscription request to $bareJid")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed sending subscription request: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Accepts an incoming subscription request and requests mutual subscription back.
     */
    suspend fun acceptSubscriptionRequest(jidString: String): Result<Unit> = withContext(Dispatchers.IO) {
        val connection = connectionManager.getRawConnection()
            ?: return@withContext Result.failure(IllegalStateException("Connection not available"))

        try {
            val bareJid = JidCreate.bareFrom(jidString.trim())
            
            // 1. Send subscribed response
            val response = PresenceBuilder.buildPresence()
                .ofType(Presence.Type.subscribed)
                .to(bareJid)
                .build()
            connection.sendStanza(response)

            // 2. Send subscribe back for mutual presence
            val subscribeBack = PresenceBuilder.buildPresence()
                .ofType(Presence.Type.subscribe)
                .to(bareJid)
                .build()
            connection.sendStanza(subscribeBack)

            val roster = activeRoster ?: Roster.getInstanceFor(connection)
            if (!roster.contains(bareJid)) {
                roster.createItem(bareJid, bareJid.localpartOrNull?.asUnescapedString() ?: bareJid.toString(), null)
            }
            refreshContactsFromRoster()
            Log.i(TAG, "Accepted subscription request from $bareJid")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed accepting subscription request: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Rejects an incoming subscription request or revokes subscription.
     */
    suspend fun rejectSubscriptionRequest(jidString: String): Result<Unit> = withContext(Dispatchers.IO) {
        val connection = connectionManager.getRawConnection()
            ?: return@withContext Result.failure(IllegalStateException("Connection not available"))

        try {
            val bareJid = JidCreate.bareFrom(jidString.trim())
            val response = PresenceBuilder.buildPresence()
                .ofType(Presence.Type.unsubscribed)
                .to(bareJid)
                .build()
            connection.sendStanza(response)
            refreshContactsFromRoster()
            Log.i(TAG, "Rejected subscription request from $bareJid")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed rejecting subscription request: ${e.message}", e)
            Result.failure(e)
        }
    }


    /**
     * Internal helper to convert Smack Roster entries to [RosterContact] list
     * and update [_contacts] and [_presenceMap].
     */
    private fun refreshContactsFromRoster(): List<RosterContact> {
        val roster = activeRoster ?: return _contacts.value

        val entries: Set<RosterEntry> = try {
            roster.entries
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch entries from roster: ${e.message}")
            emptySet()
        }

        if (!roster.isLoaded && entries.isEmpty()) {
            return _contacts.value
        }

        if (entries.isEmpty() && !connectionManager.isAuthenticated()) {
            return _contacts.value
        }

        val existingMap = _contacts.value.associateBy { it.jid.lowercase() }

        val mappedContacts = entries.map { entry ->
            val bareJid = entry.jid
            val jidString = bareJid.toString()
            val existing = existingMap[jidString.lowercase()]
            val presence = roster.getPresence(bareJid)
            val userPresence = presence.toUserPresence()
            val statusMsg = presence.status ?: existing?.statusMessage ?: ""
            val groupNames = entry.groups.map { it.name }

            // XEP-0115 Entity Capabilities
            val extractedCaps = ClientRegistryManager.extractCapsFromPresence(presence)

            // XEP-0172 User Nickname
            val nickFromPresence = extractNickname(presence) ?: existing?.nickname

            val effectiveName = nickFromPresence ?: entry.name ?: existing?.name ?: jidString.substringBefore("@")

            var contactCaps = com.ndregil.inchat.data.model.ClientCaps.resolveFor(
                name = effectiveName,
                jid = presence.from?.toString() ?: jidString,
                node = extractedCaps?.first ?: existing?.clientCaps?.rawNode ?: "",
                ver = extractedCaps?.second ?: existing?.clientCaps?.ver ?: ""
            )
            if (contactCaps.iconResId == null && presence.from != null) {
                try {
                    val discoInfo = org.jivesoftware.smackx.caps.EntityCapsManager.getDiscoverInfoByUser(presence.from)
                    val identityName = discoInfo?.identities?.firstOrNull { !it.name.isNullOrBlank() }?.name
                    if (!identityName.isNullOrBlank()) {
                        val resolvedFromDisco = com.ndregil.inchat.data.model.ClientCaps.resolveFor(
                            name = identityName,
                            jid = presence.from?.toString() ?: jidString,
                            node = if (ClientRegistryManager.isInChatExact(identityName)) "http://ndregil.inchat" else (extractedCaps?.first ?: ""),
                            ver = extractedCaps?.second ?: ""
                        )
                        if (resolvedFromDisco.iconResId != null) {
                            contactCaps = resolvedFromDisco
                        }
                    }
                } catch (_: Exception) {}
            }
            if (contactCaps.isIdentified()) {
                presence.from?.toString()?.let { ClientRegistryManager.putCachedCaps(it, contactCaps) }
                ClientRegistryManager.putCachedCaps(jidString, contactCaps)
            }

            val resolvedName = entry.name?.takeIf { it.isNotBlank() }
                ?: existing?.name?.takeIf { it.isNotBlank() }
                ?: ""

            RosterContact(
                jid = jidString,
                name = resolvedName,
                presence = userPresence,
                statusMessage = statusMsg,
                groups = groupNames,
                subscriptionType = entry.type.name.lowercase(),
                isPendingApproval = entry.isSubscriptionPending ||
                    entry.type == org.jivesoftware.smack.roster.packet.RosterPacket.ItemType.none ||
                    entry.type == org.jivesoftware.smack.roster.packet.RosterPacket.ItemType.from,
                avatar = avatarManager.getAvatar(jidString) ?: existing?.avatar,
                clientCaps = contactCaps,
                pepActivity = existing?.pepActivity,
                pepMood = existing?.pepMood,
                pepTune = existing?.pepTune,
                pepStatus = existing?.pepStatus,
                nickname = nickFromPresence
            )
        }

        _contacts.value = mappedContacts
        _presenceMap.value = mappedContacts.associate { it.jid to it.presence }
        return mappedContacts
    }

    /**
     * Extracts user nickname from presence stanza in accordance with XEP-0172.
     */
    fun extractNickname(presence: Presence?): String? {
        if (presence == null) return null
        // 1. Try Smack Nick extension (org.jivesoftware.smackx.nick.packet.Nick)
        try {
            val nick = presence.getExtension(org.jivesoftware.smackx.nick.packet.Nick::class.java)
            if (nick != null && !nick.name.isNullOrBlank()) {
                return nick.name.trim()
            }
        } catch (_: Throwable) {}
        // 2. Try XML extension parsing for xmlns='http://jabber.org/protocol/nick'
        try {
            val ext = presence.extensions.firstOrNull { it.elementName == "nick" && it.namespace == "http://jabber.org/protocol/nick" }
            if (ext is org.jivesoftware.smackx.nick.packet.Nick) {
                return ext.name?.trim()
            }
            if (ext != null) {
                val xml = ext.toXML(null as org.jivesoftware.smack.packet.XmlEnvironment?).toString()
                val match = "<nick[^>]*>([^<]+)</nick>".toRegex().find(xml)?.groupValues?.get(1)
                if (!match.isNullOrBlank()) {
                    return match.trim()
                }
            }
        } catch (_: Throwable) {}
        return null
    }

    fun clearPresenceMap() {
        _presenceMap.value = emptyMap()
        if (_contacts.value.any { it.presence != UserPresence.OFFLINE }) {
            _contacts.value = _contacts.value.map {
                if (it.presence == UserPresence.OFFLINE) it else it.copy(presence = UserPresence.OFFLINE)
            }
        }
    }

    /**
     * Cleans up listeners when disconnecting.
     */
    fun release() {
        try {
            activeRoster?.removeRosterListener(rosterListener)
            activeRoster?.removeRosterLoadedListener(rosterLoadedListener)
            pepStanzaListener?.let { attachedListenerConnection?.removeAsyncStanzaListener(it) }
            subscriptionStanzaListener?.let { attachedListenerConnection?.removeAsyncStanzaListener(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Exception during RosterManager release: ${e.message}")
        } finally {
            activeRoster = null
            attachedListenerConnection = null
            pepStanzaListener = null
            subscriptionStanzaListener = null
            _isLoaded.value = false
            _contacts.value = emptyList()
            _presenceMap.value = emptyMap()
            queriedContactVersions.clear()
        }
    }
}

/**
 * Maps Smack's [Presence] stanza mode/type to application domain [UserPresence].
 */
fun Presence.toUserPresence(): UserPresence {
    if (!isAvailable || type == Presence.Type.unavailable) {
        return UserPresence.OFFLINE
    }
    return when (mode) {
        Presence.Mode.chat -> UserPresence.CHAT
        Presence.Mode.available -> UserPresence.ONLINE
        Presence.Mode.away -> UserPresence.AWAY
        Presence.Mode.xa -> UserPresence.XA
        Presence.Mode.dnd -> UserPresence.DND
        null -> UserPresence.ONLINE
    }
}
