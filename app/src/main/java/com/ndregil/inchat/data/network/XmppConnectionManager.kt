package com.ndregil.inchat.data.network

import android.util.Log
import java.util.Locale
import com.ndregil.inchat.data.model.ConnectionStatus
import com.ndregil.inchat.data.model.UserPresence
import com.ndregil.inchat.data.model.XmppAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jivesoftware.smack.ConnectionConfiguration
import org.jivesoftware.smack.ConnectionListener
import org.jivesoftware.smack.ReconnectionManager
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.packet.Presence
import org.jivesoftware.smack.packet.PresenceBuilder
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.jivesoftware.smackx.muc.MultiUserChatManager
import org.jivesoftware.smackx.iqversion.VersionManager
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.jid.parts.Resourcepart
import org.jivesoftware.smackx.iqregister.AccountManager
import org.jivesoftware.smackx.xdata.packet.DataForm
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smack.packet.StanzaError
import org.jivesoftware.smackx.iqregister.packet.Registration
import org.jivesoftware.smack.packet.IQ
import org.jxmpp.jid.parts.Localpart
import java.net.InetAddress
import android.os.Build
import org.jivesoftware.smack.sasl.SASLErrorException

import org.jivesoftware.smack.SmackConfiguration

/**
 * Credentials model for XMPP authentication and server connection.
 */
data class XmppCredentials(
    val jid: String = "",
    val username: String = "",
    val domain: String = "",
    val password: String = "",
    val host: String = "",
    val port: Int = 5222,
    val resource: String = "InChat",
    val nickname: String = "InChatUser",
    val useTls: Boolean = true
) {
    fun toAccount(isDemoMode: Boolean = false): XmppAccount {
        return XmppAccount(
            jid = if (jid.contains("@")) jid else "$username@$domain",
            username = username,
            domain = domain,
            nickname = nickname,
            host = host,
            port = port,
            useTls = useTls,
            isDemoMode = isDemoMode
        )
    }

    companion object {
        fun sanitizeDomain(rawDomain: String): String {
            val clean = rawDomain.trim().lowercase(Locale.getDefault())
            return when {
                clean == "comversations.im" || clean == "comversation.im" || 
                clean == "conversations.m" || clean == "conversations.i" || 
                clean == "comversations.net" || clean == "comversation.net" ||
                clean == "conversations.net" || clean == "conversation.im" -> "conversations.im"
                clean == "trashserver.n" || clean == "trashserver.de" -> "trashserver.net"
                clean == "xmpp.j" -> "xmpp.jp"
                clean.contains("comversations.im") -> clean.replace("comversations.im", "conversations.im")
                else -> clean
            }
        }

        fun sanitizeHost(rawHost: String, cleanDomain: String): String {
            val clean = rawHost.trim().lowercase(Locale.getDefault())
            if (clean.isBlank() || clean == "auto") return cleanDomain
            return when {
                clean == "comversations.im" || clean == "comversation.im" || 
                clean == "conversations.m" || clean == "conversations.i" || 
                clean == "comversations.net" || clean == "comversation.net" ||
                clean == "conversations.net" || clean == "conversation.im" -> "conversations.im"
                clean == "trashserver.n" || clean == "trashserver.de" -> "trashserver.net"
                clean == "xmpp.j" -> "xmpp.jp"
                clean.contains("comversations.im") -> clean.replace("comversations.im", "conversations.im")
                else -> rawHost.trim()
            }
        }

        fun sanitizeJid(rawJid: String): String {
            val clean = rawJid.trim()
            if (!clean.contains("@")) return clean
            val user = clean.substringBefore("@")
            val rest = clean.substringAfter("@")
            val res = if (rest.contains("/")) "/" + rest.substringAfter("/") else ""
            val dom = rest.substringBefore("/")
            val cleanDom = sanitizeDomain(dom)
            return "$user@$cleanDom$res"
        }

        fun fromAccount(account: XmppAccount, password: String = ""): XmppCredentials {
            val cleanDom = sanitizeDomain(account.domain)
            val cleanHost = sanitizeHost(account.host, cleanDom)
            val cleanJid = sanitizeJid(account.jid)
            return XmppCredentials(
                jid = cleanJid,
                username = account.username,
                domain = cleanDom,
                password = password,
                host = cleanHost,
                port = account.port,
                resource = "InChat-Android",
                nickname = account.nickname,
                useTls = account.useTls
            )
        }
    }
}

/**
 * Manager class responsible for initializing, establishing, and managing XMPP connections
 * and authentication using the Smack library.
 */
class XmppConnectionManager(
    initialCredentials: XmppCredentials = XmppCredentials()
) {
    init {
        try {
            SmackConfiguration.DEBUG = true
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "XmppConnectionManager"
        private const val DEFAULT_CONNECT_TIMEOUT_MS = 30000

        fun sanitizeDomain(rawDomain: String): String = XmppCredentials.sanitizeDomain(rawDomain)
        fun sanitizeHost(rawHost: String, cleanDomain: String): String = XmppCredentials.sanitizeHost(rawHost, cleanDomain)
        fun sanitizeJid(rawJid: String): String = XmppCredentials.sanitizeJid(rawJid)
    }

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _credentials = MutableStateFlow(initialCredentials)
    val credentials: StateFlow<XmppCredentials> = _credentials.asStateFlow()
    val currentCredentials: XmppCredentials get() = _credentials.value

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    @Volatile
    private var tcpConnection: XMPPTCPConnection? = null

    @Volatile
    private var mucManager: MultiUserChatManager? = null

    @Volatile
    private var chatManager: org.jivesoftware.smack.chat2.ChatManager? = null

    private val smackConnectionListener = object : ConnectionListener {
        override fun connected(connection: XMPPConnection) {
            Log.d(TAG, "Smack: Connected to ${connection.host}:${connection.port}")
            _connectionStatus.value = ConnectionStatus.AUTHENTICATING
        }

        override fun authenticated(connection: XMPPConnection, resumed: Boolean) {
            Log.d(TAG, "Smack: Authenticated as ${connection.user} (resumed=$resumed)")
            _connectionStatus.value = ConnectionStatus.CONNECTED
            _lastError.value = null
        }

        override fun connectionClosed() {
            Log.d(TAG, "Smack: Connection closed normally")
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
        }

        override fun connectionClosedOnError(e: Exception) {
            Log.e(TAG, "Smack: Connection closed on error: ${e.message}", e)
            _connectionStatus.value = ConnectionStatus.ERROR
            _lastError.value = e.localizedMessage ?: "Connection closed with error"
        }
    }

    fun setCredentials(newCredentials: XmppCredentials) {
        _credentials.value = newCredentials
    }

    fun updateCredentialsFromAccount(account: XmppAccount, password: String = "") {
        _credentials.value = XmppCredentials.fromAccount(account, password)
    }

    fun clearCredentials() {
        _credentials.value = XmppCredentials()
    }

    fun isConnected(): Boolean = tcpConnection?.isConnected == true
    fun isAuthenticated(): Boolean = tcpConnection?.isAuthenticated == true
    fun getRawConnection(): XMPPTCPConnection? = tcpConnection

    fun getMultiUserChatManager(): MultiUserChatManager? {
        val conn = tcpConnection
        if (conn != null && conn.isAuthenticated) {
            if (mucManager == null) {
                mucManager = MultiUserChatManager.getInstanceFor(conn)
            }
            return mucManager
        }
        return null
    }

    fun getChatManager(): org.jivesoftware.smack.chat2.ChatManager? {
        val conn = tcpConnection
        if (conn != null && conn.isAuthenticated) {
            if (chatManager == null) {
                chatManager = org.jivesoftware.smack.chat2.ChatManager.getInstanceFor(conn)
            }
            return chatManager
        }
        return null
    }

    /**
     * Initializes Smack configuration and establishes TCP connection + login.
     * Implements XEP-0199 (XMPP Ping) and XEP-0184 (Message Delivery Receipts).
     */
    suspend fun connectAndLogin(
        overrideCredentials: XmppCredentials? = null
    ): Result<XMPPTCPConnection> = withContext(Dispatchers.IO) {
        if (overrideCredentials != null) {
            _credentials.value = overrideCredentials
        }
        val creds = _credentials.value

        val existingConn = tcpConnection
        if (existingConn != null && existingConn.isConnected && existingConn.isAuthenticated) {
            val sameUser = creds.username.isNotBlank() &&
                    creds.username.equals(currentCredentials.username, ignoreCase = true)
            val sameDomain = creds.domain.isNotBlank() &&
                    creds.domain.equals(currentCredentials.domain, ignoreCase = true)
            if (sameUser && sameDomain) {
                Log.i(TAG, "Already connected and authenticated. Reusing active connection.")
                _connectionStatus.value = ConnectionStatus.CONNECTED
                return@withContext Result.success(existingConn)
            }
        }

        val rawDomain = creds.domain.ifBlank { creds.jid.substringAfter("@", "").substringBefore("/") }
        val cleanDomain = sanitizeDomain(rawDomain)
        val cleanHost = sanitizeHost(creds.host, cleanDomain)

        try {
            _connectionStatus.value = ConnectionStatus.CONNECTING
            _lastError.value = null

            safeDisconnectInternal()

            Log.i(TAG, "Initializing XMPP connection to $cleanHost:${creds.port} (domain=$cleanDomain)")

            val domainJid = JidCreate.domainBareFrom(cleanDomain)
            val username = creds.username.trim()
            val resource = creds.resource.ifBlank { "InChat" }

            val configBuilder = XMPPTCPConnectionConfiguration.builder()
                .setXmppDomain(domainJid)
                .setPort(creds.port)
                .setResource(Resourcepart.from(resource))
                .setConnectTimeout(30000) // 30 seconds
                .setSecurityMode(if (creds.useTls) ConnectionConfiguration.SecurityMode.required else ConnectionConfiguration.SecurityMode.ifpossible)
                .setSendPresence(true)
                .setCompressionEnabled(false)

            if (cleanHost.isNotBlank()) {
                configBuilder.setHost(cleanHost)
                try {
                    val address = InetAddress.getByName(cleanHost)
                    configBuilder.setHostAddress(address)
                } catch (e: Exception) {
                    Log.w(TAG, "DNS resolution warning: ${e.message}")
                }
            }

            if (username.isNotBlank()) {
                configBuilder.setUsernameAndPassword(username, creds.password)
            }

            val configuration = configBuilder.build()
            val connection = XMPPTCPConnection(configuration)
            
            // Enable Stream Management (XEP-0198)
            connection.setUseStreamManagement(false)
            connection.setUseStreamManagementResumption(false)



            connection.addConnectionListener(smackConnectionListener)
            org.jivesoftware.smack.SmackConfiguration.setDefaultReplyTimeout(DEFAULT_CONNECT_TIMEOUT_MS)
            connection.replyTimeout = DEFAULT_CONNECT_TIMEOUT_MS.toLong()

            // Setup Automatic Reconnection (XEP-0198 Stream Management compatible)
            val reconnectionManager = ReconnectionManager.getInstanceFor(connection)
            reconnectionManager.setReconnectionPolicy(ReconnectionManager.ReconnectionPolicy.FIXED_DELAY)
            reconnectionManager.setFixedDelay(5) // Reconnect every 5 seconds
            reconnectionManager.enableAutomaticReconnection()


            // Setup Entity Capabilities (XEP-0115) and Software Version (XEP-0092) BEFORE connecting
            try {
                org.jivesoftware.smackx.caps.EntityCapsManager.setDefaultEntityNode("http://ndregil.inchat")
                val capsManager = org.jivesoftware.smackx.caps.EntityCapsManager.getInstanceFor(connection)
                capsManager.enableEntityCaps()
            } catch (e: Exception) {
                Log.w(TAG, "EntityCapsManager setup failed: ${e.message}")
            }

            try {
                val versionManager = VersionManager.getInstanceFor(connection)
                versionManager.setVersion("InChat", "1.0.0", "Android ${Build.VERSION.RELEASE}")
            } catch (e: Exception) {
                Log.w(TAG, "VersionManager setup failed: ${e.message}")
            }

            connection.connect()
            Log.i(TAG, "TCP connection established.")

            if (username.isNotBlank() && creds.password.isNotBlank()) {
                _connectionStatus.value = ConnectionStatus.AUTHENTICATING
                val resourcePart = Resourcepart.from(resource)
                connection.login(username, creds.password, resourcePart)
            } else if (username.isNotBlank()) {
                _connectionStatus.value = ConnectionStatus.AUTHENTICATING
                connection.login()
            }

            tcpConnection = connection
            mucManager = MultiUserChatManager.getInstanceFor(connection)
            chatManager = org.jivesoftware.smack.chat2.ChatManager.getInstanceFor(connection)
            
            // Enable Message Carbons (XEP-0280)
            try {
                val carbonManager = org.jivesoftware.smackx.carbons.CarbonManager.getInstanceFor(connection)
                if (carbonManager.isSupportedByServer) {
                    carbonManager.enableCarbons()
                }
            } catch (e: Exception) {}
            
            // Enable XMPP Ping (XEP-0199) to keep-alive
            val pingManager = org.jivesoftware.smackx.ping.PingManager.getInstanceFor(connection)
            pingManager.pingInterval = 18 // Reduced to 18 seconds to stay within NAT windows
            

            // Also try to enable standard TCP Keep-Alive
            try {
                // connection.socket.keepAlive = true // Not directly accessible in all Smack versions
                // But XMPPTCPConnection handles this if configured in builder (which we will add)
            } catch (e: Exception) {}



            // Delivery Receipts (XEP-0184)
            val deliveryReceiptManager = org.jivesoftware.smackx.receipts.DeliveryReceiptManager.getInstanceFor(connection)
            deliveryReceiptManager.autoReceiptMode = org.jivesoftware.smackx.receipts.DeliveryReceiptManager.AutoReceiptMode.always
            deliveryReceiptManager.autoAddDeliveryReceiptRequests()
            
            _connectionStatus.value = ConnectionStatus.CONNECTED
            Result.success(connection)
        } catch (e: Exception) {
            Log.e(TAG, "Connect/Login failed: ${e.message}")
            _connectionStatus.value = ConnectionStatus.ERROR
            val rawMsg = e.localizedMessage ?: "XMPP failure"
            val friendlyMsg = when {
                e is SASLErrorException || rawMsg.contains("not-authorized", ignoreCase = true) ->
                    "Autentikasi gagal: Username atau password salah."
                rawMsg.contains("connection refused", ignoreCase = true) ->
                    "Koneksi ditolak oleh server."
                rawMsg.contains("timeout", ignoreCase = true) ->
                    "Batas waktu koneksi habis."
                else -> rawMsg
            }
            _lastError.value = friendlyMsg
            safeDisconnectInternal()
            Result.failure(e)
        }
    }

    suspend fun connect(): Result<XMPPTCPConnection> = withContext(Dispatchers.IO) {
        val creds = _credentials.value
        try {
            _connectionStatus.value = ConnectionStatus.CONNECTING
            _lastError.value = null
            safeDisconnectInternal()

            val domainJid = JidCreate.domainBareFrom(creds.domain.trim())
            val configBuilder = XMPPTCPConnectionConfiguration.builder()
                .setXmppDomain(domainJid)
                .setPort(creds.port)
                .setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MS)
                .setSecurityMode(if (creds.useTls) ConnectionConfiguration.SecurityMode.required else ConnectionConfiguration.SecurityMode.disabled)

            if (creds.host.isNotBlank()) configBuilder.setHost(creds.host.trim())

            val connection = XMPPTCPConnection(configBuilder.build())
            connection.addConnectionListener(smackConnectionListener)
            connection.connect()
            tcpConnection = connection
            Result.success(connection)
        } catch (e: Exception) {
            _connectionStatus.value = ConnectionStatus.ERROR
            _lastError.value = e.localizedMessage
            safeDisconnectInternal()
            Result.failure(e)
        }
    }

    suspend fun login(passwordOverride: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        val conn = tcpConnection ?: return@withContext Result.failure(IllegalStateException("No connection"))
        val creds = _credentials.value
        val pass = passwordOverride ?: creds.password
        val user = creds.username.trim()
        try {
            _connectionStatus.value = ConnectionStatus.AUTHENTICATING
            if (user.isNotBlank() && pass.isNotBlank()) {
                conn.login(user, pass)
            } else {
                conn.login()
            }
            _connectionStatus.value = ConnectionStatus.CONNECTED
            Result.success(Unit)
        } catch (e: Exception) {
            _connectionStatus.value = ConnectionStatus.ERROR
            _lastError.value = e.localizedMessage
            Result.failure(e)
        }
    }

    suspend fun updatePresence(
        userPresence: UserPresence,
        statusMessage: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val conn = tcpConnection
        if (conn == null || !conn.isAuthenticated) return@withContext Result.failure(IllegalStateException("Unauthenticated"))

        try {
            val mode = when (userPresence) {
                UserPresence.ONLINE -> Presence.Mode.available
                UserPresence.CHAT -> Presence.Mode.chat
                UserPresence.AWAY -> Presence.Mode.away
                UserPresence.XA -> Presence.Mode.xa
                UserPresence.DND -> Presence.Mode.dnd
                UserPresence.OFFLINE -> null
            }

            val presence = if (userPresence == UserPresence.OFFLINE) {
                PresenceBuilder.buildPresence().ofType(Presence.Type.unavailable).setStatus(statusMessage ?: "Offline").build()
            } else {
                val builder = PresenceBuilder.buildPresence().ofType(Presence.Type.available).setStatus(statusMessage ?: "Active")
                if (mode != null) builder.setMode(mode)
                builder.addExtension(org.jivesoftware.smackx.caps.packet.CapsExtension("http://ndregil.inchat", "kFwX23eY765qL90mN12aBcDeFgH=", "sha-1"))
                builder.build()
            }

            conn.sendStanza(presence)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        safeDisconnectInternal()
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
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
    ): Result<String> = withContext(Dispatchers.IO) {
        var tempConnection: XMPPTCPConnection? = null
        try {
            val cleanDomain = sanitizeDomain(domain)
            val cleanUser = username.trim()
            val cleanHost = host.trim().ifBlank { cleanDomain }

            val domainJid = JidCreate.domainBareFrom(cleanDomain)
            val config = XMPPTCPConnectionConfiguration.builder()
                .setXmppDomain(domainJid)
                .setHost(cleanHost)
                .setPort(port)
                .setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MS)
                .setSecurityMode(if (useTls) ConnectionConfiguration.SecurityMode.required else ConnectionConfiguration.SecurityMode.disabled)
                .build()

            tempConnection = XMPPTCPConnection(config)
            tempConnection.connect()

            val accountManager = AccountManager.getInstance(tempConnection)
            accountManager.sensitiveOperationOverInsecureConnection(true)
            
            if (!accountManager.supportsAccountCreation()) {
                Log.e(TAG, "Server does not support In-Band Registration")
                throw RegistrationNotAllowedException("Server $cleanDomain menolak pendaftaran langsung. Silakan pilih server lain seperti xmpp.jp, trashserver.net, atau dismail.de.")
            }

            // Check for CAPTCHA requirements manually using IQ get packet
            val registrationIQ = Registration()
            registrationIQ.type = IQ.Type.get
            registrationIQ.to = domainJid

            val getCollector = tempConnection.createStanzaCollector(
                org.jivesoftware.smack.filter.StanzaIdFilter(registrationIQ.stanzaId)
            )
            val response = try {
                tempConnection.sendStanza(registrationIQ)
                getCollector.nextResultOrThrow<org.jivesoftware.smack.packet.Stanza>()
            } finally {
                getCollector.cancel()
            }

            val responseXml = response.toXML().toString()
            Log.d(TAG, "Registration GET response stanza XML: $responseXml")

            val urlRegex = Regex("""(https?://[^\s<"]+)""", RegexOption.IGNORE_CASE)
            val genericUrlMatch = urlRegex.find(responseXml)?.value

            val dataForm = DataForm.from(response)
            var captchaVar = "ocr"
            var isCaptchaRequired = false

            if (dataForm != null) {
                for (field in dataForm.fields) {
                    val v = field.variable?.lowercase(Locale.getDefault()) ?: ""
                    if (v == "ocr" || v == "captcha" || v == "challenge" || v == "url-data") {
                        captchaVar = field.variable
                        isCaptchaRequired = true
                    }
                }
            }

            val bobImageBytes = XmppBobManager.extractBytesFromStanza(responseXml)
            val captchaImageUrl = if (genericUrlMatch != null && (genericUrlMatch.endsWith(".png") || genericUrlMatch.endsWith(".jpg") || genericUrlMatch.contains("captcha", ignoreCase = true))) {
                genericUrlMatch
            } else null

            if (isCaptchaRequired || bobImageBytes != null || !captchaImageUrl.isNullOrBlank() || responseXml.contains("urn:xmpp:captcha", ignoreCase = true)) {
                val captchaData = CaptchaChallengeData(
                    domain = cleanDomain,
                    username = cleanUser,
                    password = password,
                    host = cleanHost,
                    port = port,
                    nickname = nickname,
                    email = email,
                    useTls = useTls,
                    challengeVar = captchaVar,
                    captchaImageBytes = bobImageBytes,
                    captchaImageUrl = captchaImageUrl
                )
                throw CaptchaRequiredException(captchaData)
            }

            val localpart = Localpart.from(cleanUser)
            val attributes = mutableMapOf<String, String>()
            if (email.isNotBlank()) attributes["email"] = email
            if (nickname.isNotBlank()) attributes["name"] = nickname

            try {
                // Attempt 1: Minimal registration
                Log.d(TAG, "Attempting minimal registration for $localpart")
                accountManager.createAccount(localpart, password, mutableMapOf())
            } catch (e: org.jivesoftware.smack.XMPPException.XMPPErrorException) {
                val errXml = e.stanzaError?.toXML()?.toString() ?: ""
                Log.w(TAG, "Minimal registration failed. Error stanza XML: $errXml", e)
                
                if (e.stanzaError.condition == StanzaError.Condition.not_allowed) {
                    throw RegistrationNotAllowedException("Server $cleanDomain menolak pendaftaran langsung (In-Band Registration ditutup). Silakan coba server lain seperti xmpp.jp atau trashserver.net.")
                }

                // Check if error stanza contains CAPTCHA challenge
                val errBobBytes = XmppBobManager.extractBytesFromStanza(errXml)
                if (errBobBytes != null || errXml.contains("captcha", ignoreCase = true) || errXml.contains("ocr", ignoreCase = true)) {
                    val captchaData = CaptchaChallengeData(
                        domain = cleanDomain,
                        username = cleanUser,
                        password = password,
                        host = cleanHost,
                        port = port,
                        nickname = nickname,
                        email = email,
                        useTls = useTls,
                        challengeVar = captchaVar,
                        captchaImageBytes = errBobBytes
                    )
                    throw CaptchaRequiredException(captchaData)
                }

                // Attempt 2: Registration with attributes
                try {
                    accountManager.createAccount(localpart, password, attributes)
                } catch (e2: Exception) {
                    Log.w(TAG, "Attribute registration failed: ${e2.message}", e2)
                    throw e2
                }
            }

            try { tempConnection.disconnect() } catch (_: Exception) {}
            Result.success("$cleanUser@$cleanDomain")
        } catch (e: Exception) {
            if (e is CaptchaRequiredException || e is RegistrationNotAllowedException) {
                Log.d(TAG, "Registration expected flow: ${e.javaClass.simpleName} - ${e.message}")
            } else {
                Log.e(TAG, "Registration final failure: ${e.message}", e)
            }
            try { tempConnection?.disconnect() } catch (_: Exception) {}
            Result.failure(e)
        }
    }

    suspend fun querySoftwareVersion(targetJid: String): Result<com.ndregil.inchat.data.model.ClientCaps> = withContext(Dispatchers.IO) {
        val conn = tcpConnection ?: return@withContext Result.failure(Exception("Disconnected"))
        try {
            val jidStr = targetJid.trim()
            var jid = JidCreate.from(jidStr)
            
            // If bare JID, try to find a full JID from Roster if available
            if (!jid.hasResource()) {
                try {
                    val roster = org.jivesoftware.smack.roster.Roster.getInstanceFor(conn)
                    val presence = roster.getPresence(jid.asBareJid())
                    if (presence.isAvailable && presence.from != null) {
                        jid = presence.from
                        Log.d(TAG, "Resolved bare JID $targetJid to full JID $jid for version query")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to resolve full JID from roster for $targetJid: ${e.message}")
                }
            }

            val versionManager = VersionManager.getInstanceFor(conn)
            val version = versionManager.getVersion(jid)
            
            if (version == null) return@withContext Result.failure(Exception("Tidak ada info versi dari user"))
            
            val vName = version.name ?: "Unknown"
            val isTargetInChat = vName.equals("InChat", ignoreCase = true)
            val caps = com.ndregil.inchat.data.model.ClientCaps.resolveFor(
                name = vName,
                jid = jid.toString(),
                node = if (isTargetInChat) "http://ndregil.inchat" else "",
                version = version.version ?: "1.0.0",
                os = version.os ?: "Android"
            )
            Result.success(caps)
        } catch (e: Exception) {
            Log.d(TAG, "querySoftwareVersion info for $targetJid: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun loadVCard(targetJid: String): org.jivesoftware.smackx.vcardtemp.packet.VCard? = withContext(Dispatchers.IO) {
        val conn = tcpConnection ?: return@withContext null
        try {
            val cleanJid = targetJid.trim()
            val jidObj = JidCreate.from(cleanJid)

            // 1. If it has resource or is a MUC occupant JID, query via raw VCard IQ directly to the full JID
            if (jidObj.hasResource()) {
                try {
                    val vcardIq = org.jivesoftware.smackx.vcardtemp.packet.VCard()
                    vcardIq.to = jidObj
                    vcardIq.type = org.jivesoftware.smack.packet.IQ.Type.get
                    val res = conn.sendIqRequestAndWaitForResponse<org.jivesoftware.smackx.vcardtemp.packet.VCard>(vcardIq)
                    if (res != null) return@withContext res
                } catch (e: Exception) {
                    Log.d(TAG, "Direct vCard IQ to $cleanJid failed: ${e.message}")
                }
            }

            // 2. Query via VCardManager using bare JID
            val bare = jidObj.asEntityBareJidIfPossible()
            if (bare != null) {
                try {
                    val vCardManager = org.jivesoftware.smackx.vcardtemp.VCardManager.getInstanceFor(conn)
                    val vCard = vCardManager.loadVCard(bare)
                    if (vCard != null) return@withContext vCard
                } catch (e: Exception) {
                    Log.d(TAG, "vCardManager for $bare failed: ${e.message}")
                }
            }

            // 3. Fallback: try raw VCard IQ to bare JID
            try {
                val vcardIq = org.jivesoftware.smackx.vcardtemp.packet.VCard()
                vcardIq.to = jidObj.asBareJid()
                vcardIq.type = org.jivesoftware.smack.packet.IQ.Type.get
                val res = conn.sendIqRequestAndWaitForResponse<org.jivesoftware.smackx.vcardtemp.packet.VCard>(vcardIq)
                if (res != null) return@withContext res
            } catch (e: Exception) {
                Log.d(TAG, "Fallback vCard IQ to bare failed: ${e.message}")
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load vCard for $targetJid: ${e.message}")
            null
        }
    }


    suspend fun submitCaptchaAnswer(
        challenge: CaptchaChallengeData,
        captchaAnswer: String
    ): Result<String> = withContext(Dispatchers.IO) {
        var tempConnection: XMPPTCPConnection? = null
        try {
            val config = XMPPTCPConnectionConfiguration.builder()
                .setXmppDomain(JidCreate.domainBareFrom(challenge.domain))
                .setHost(challenge.host.ifBlank { challenge.domain })
                .setPort(challenge.port)
                .setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MS)
                .setSecurityMode(if (challenge.useTls) ConnectionConfiguration.SecurityMode.required else ConnectionConfiguration.SecurityMode.disabled)
                .build()

            tempConnection = XMPPTCPConnection(config)
            tempConnection.connect()

            val attributes = mutableMapOf<String, String>()
            attributes["username"] = challenge.username
            attributes["password"] = challenge.password
            attributes[challenge.challengeVar] = captchaAnswer.trim()
            if (challenge.email.isNotBlank()) attributes["email"] = challenge.email.trim()
            if (challenge.nickname.isNotBlank()) attributes["name"] = challenge.nickname.trim()

            val submitReg = org.jivesoftware.smackx.iqregister.packet.Registration(attributes)
            submitReg.type = org.jivesoftware.smack.packet.IQ.Type.set
            submitReg.to = JidCreate.from(challenge.domain)

            val formBuilder = org.jivesoftware.smackx.xdata.packet.DataForm.builder(org.jivesoftware.smackx.xdata.packet.DataForm.Type.submit)
            formBuilder.addField(org.jivesoftware.smackx.xdata.FormField.builder("FORM_TYPE").setValue("urn:xmpp:captcha").build())
            formBuilder.addField(org.jivesoftware.smackx.xdata.FormField.builder("username").setValue(challenge.username).build())
            formBuilder.addField(org.jivesoftware.smackx.xdata.FormField.builder("password").setValue(challenge.password).build())
            formBuilder.addField(org.jivesoftware.smackx.xdata.FormField.builder(challenge.challengeVar).setValue(captchaAnswer.trim()).build())
            if (challenge.email.isNotBlank()) {
                formBuilder.addField(org.jivesoftware.smackx.xdata.FormField.builder("email").setValue(challenge.email.trim()).build())
            }
            if (challenge.nickname.isNotBlank()) {
                formBuilder.addField(org.jivesoftware.smackx.xdata.FormField.builder("name").setValue(challenge.nickname.trim()).build())
            }

            submitReg.addExtension(formBuilder.build())

            val collector = tempConnection.createStanzaCollector(
                org.jivesoftware.smack.filter.StanzaIdFilter(submitReg.stanzaId)
            )
            try {
                tempConnection.sendStanza(submitReg)
                collector.nextResultOrThrow<org.jivesoftware.smack.packet.Stanza>()
            } catch (e: Exception) {
                Log.w(TAG, "IQ DataForm submit failed (${e.message}), trying AccountManager with attributes", e)
                val accountManager = AccountManager.getInstance(tempConnection)
                accountManager.sensitiveOperationOverInsecureConnection(true)
                try {
                    accountManager.createAccount(Localpart.from(challenge.username), challenge.password, attributes)
                } catch (e2: org.jivesoftware.smack.XMPPException.XMPPErrorException) {
                    if (e2.stanzaError.condition == StanzaError.Condition.not_allowed) {
                        throw RegistrationNotAllowedException("Server ${challenge.domain} menolak pendaftaran langsung. Silakan coba server lain seperti xmpp.jp atau trashserver.net.")
                    }
                    throw e2
                }
            } finally {
                collector.cancel()
            }

            try { tempConnection.disconnect() } catch (_: Exception) {}
            Result.success("${challenge.username}@${challenge.domain}")
        } catch (e: Exception) {
            if (e is RegistrationNotAllowedException) {
                Log.d(TAG, "Captcha submit expected error: ${e.message}")
            } else {
                Log.e(TAG, "Captcha answer submission failed: ${e.message}", e)
            }
            try { tempConnection?.disconnect() } catch (_: Exception) {}
            Result.failure(e)
        }
    }

    private fun safeDisconnectInternal() {
        try {
            tcpConnection?.let { conn ->
                conn.removeConnectionListener(smackConnectionListener)
                if (conn.isConnected) conn.disconnect()
            }
        } catch (e: Exception) {
        } finally {
            tcpConnection = null
            mucManager = null
            chatManager = null
        }
    }
}

data class CaptchaChallengeData(
    val domain: String,
    val username: String,
    val password: String,
    val host: String = "",
    val port: Int = 5222,
    val email: String = "",
    val nickname: String = "",
    val useTls: Boolean = true,
    val challengeVar: String = "ocr",
    val captchaImageBytes: ByteArray? = null,
    val captchaImageUrl: String? = null
)

class CaptchaRequiredException(val challenge: CaptchaChallengeData) : Exception("Captcha required")
class RegistrationNotAllowedException(override val message: String) : Exception(message)
