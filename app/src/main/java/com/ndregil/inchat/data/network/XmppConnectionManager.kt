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
import org.jivesoftware.smack.XMPPException
import org.jxmpp.jid.parts.Localpart
import java.net.InetAddress
import android.os.Build

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
    val resource: String = "InChat-Android",
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
        fun fromAccount(account: XmppAccount, password: String = ""): XmppCredentials {
            return XmppCredentials(
                jid = account.jid,
                username = account.username,
                domain = account.domain,
                password = password,
                host = account.host,
                port = account.port,
                resource = "InChat-Android",
                nickname = account.nickname,
                useTls = account.useTls
            )
        }
    }
}

/**
 * Manager class responsible for initializing, establishing, and managing TCP socket connections
 * and authentication with an XMPP server using the Smack library.
 */
class XmppConnectionManager(
    initialCredentials: XmppCredentials = XmppCredentials()
) {
    companion object {
        private const val TAG = "XmppConnectionManager"
        private const val DEFAULT_CONNECT_TIMEOUT_MS = 15000
    }

    // State flows for connection status and current credentials
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _credentials = MutableStateFlow(initialCredentials)
    val credentials: StateFlow<XmppCredentials> = _credentials.asStateFlow()
    val currentCredentials: XmppCredentials get() = _credentials.value

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // Underlying Smack XMPP TCP connection
    @Volatile
    private var tcpConnection: XMPPTCPConnection? = null

    // Multi-User Chat manager associated with current connection
    @Volatile
    private var mucManager: MultiUserChatManager? = null

    // Chat manager for 1-on-1 chats
    @Volatile
    private var chatManager: org.jivesoftware.smack.chat2.ChatManager? = null

    // Connection listener for Smack callbacks
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

    /**
     * Updates and saves login credentials in memory.
     */
    fun setCredentials(newCredentials: XmppCredentials) {
        _credentials.value = newCredentials
    }

    /**
     * Updates credentials from an [XmppAccount] object and optional password.
     */
    fun updateCredentialsFromAccount(account: XmppAccount, password: String = "") {
        _credentials.value = XmppCredentials.fromAccount(account, password)
    }

    /**
     * Clears credentials back to default guest values.
     */
    fun clearCredentials() {
        _credentials.value = XmppCredentials()
    }

    /**
     * Returns true if the TCP connection is active and connected.
     */
    fun isConnected(): Boolean = tcpConnection?.isConnected == true

    /**
     * Returns true if connection is active and user is successfully authenticated.
     */
    fun isAuthenticated(): Boolean = tcpConnection?.isAuthenticated == true

    /**
     * Access the active [XMPPTCPConnection] instance, or null if uninitialized.
     */
    fun getRawConnection(): XMPPTCPConnection? = tcpConnection

    /**
     * Access the [MultiUserChatManager] instance for MUC operations.
     */
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

    /**
     * Access the [org.jivesoftware.smack.chat2.ChatManager] instance for 1-on-1 chats.
     */
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
     * Runs asynchronously on [Dispatchers.IO].
     */
    suspend fun connectAndLogin(
        overrideCredentials: XmppCredentials? = null
    ): Result<XMPPTCPConnection> = withContext(Dispatchers.IO) {
        if (overrideCredentials != null) {
            _credentials.value = overrideCredentials
        }
        val creds = _credentials.value

        // Check if already connected and authenticated with matching credentials
        val existingConn = tcpConnection
        if (existingConn != null && existingConn.isConnected && existingConn.isAuthenticated) {
            val sameUser = creds.username.isNotBlank() &&
                    creds.username.equals(currentCredentials.username, ignoreCase = true)
            val sameDomain = creds.domain.isNotBlank() &&
                    creds.domain.equals(currentCredentials.domain, ignoreCase = true)
            if (sameUser && sameDomain) {
                Log.i(TAG, "Already connected and authenticated as ${existingConn.user}. Reusing active connection.")
                _connectionStatus.value = ConnectionStatus.CONNECTED
                return@withContext Result.success(existingConn)
            }
        }

        try {
            _connectionStatus.value = ConnectionStatus.CONNECTING
            _lastError.value = null

            // Clean up any stale existing connection
            safeDisconnectInternal()

            Log.i(TAG, "Initializing Smack TCP connection to ${creds.host}:${creds.port} (domain=${creds.domain})")

            val domainJid = JidCreate.domainBareFrom(creds.domain.trim())
            val username = creds.username.trim()
            val resource = creds.resource.ifBlank { "InChat-Android" }

            val configBuilder = XMPPTCPConnectionConfiguration.builder()
                .setXmppDomain(domainJid)
                .setPort(creds.port)
                .setResource(Resourcepart.from(resource))
                .setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MS)
                .setSecurityMode(
                    if (creds.useTls) ConnectionConfiguration.SecurityMode.required
                    else ConnectionConfiguration.SecurityMode.disabled
                )

            if (creds.host.isNotBlank()) {
                configBuilder.setHost(creds.host.trim())
                // Resolve host IP address if hostname is provided
                try {
                    val address = InetAddress.getByName(creds.host.trim())
                    configBuilder.setHostAddress(address)
                } catch (e: Exception) {
                    Log.w(TAG, "DNS resolution warning for ${creds.host}: ${e.message}")
                }
            }

            // Set username and password if provided
            if (username.isNotBlank()) {
                configBuilder.setUsernameAndPassword(username, creds.password)
            }

            val configuration = configBuilder.build()
            val connection = XMPPTCPConnection(configuration)

            // Attach connection listener
            connection.addConnectionListener(smackConnectionListener)

            // Configure automatic reconnection
            val reconnectionManager = ReconnectionManager.getInstanceFor(connection)
            reconnectionManager.enableAutomaticReconnection()

            // Establish TCP connection
            connection.connect()
            Log.i(TAG, "TCP connection established successfully.")

            // Increase default reply timeout (default is 5s, which often causes NoResponseException)
            connection.replyTimeout = 15000 

            // Authenticate if credentials are provided
            if (username.isNotBlank() && creds.password.isNotBlank()) {
                _connectionStatus.value = ConnectionStatus.AUTHENTICATING
                val resourceWithSuffix = Resourcepart.from(resource + "-" + (1000..9999).random())
                connection.login(username, creds.password, resourceWithSuffix)
                Log.i(TAG, "User '$username' authenticated successfully with resource $resourceWithSuffix")
            } else if (username.isNotBlank()) {
                // Anonymous or passwordless attempt
                _connectionStatus.value = ConnectionStatus.AUTHENTICATING
                connection.login()
                Log.i(TAG, "Logged in anonymously or with preconfigured credentials.")
            }

            tcpConnection = connection
            mucManager = MultiUserChatManager.getInstanceFor(connection)
            chatManager = org.jivesoftware.smack.chat2.ChatManager.getInstanceFor(connection)
            
            // Enable XEP-0280: Message Carbons
            try {
                val carbonManager = org.jivesoftware.smackx.carbons.CarbonManager.getInstanceFor(connection)
                if (carbonManager.isSupportedByServer) {
                    carbonManager.enableCarbons()
                    Log.i(TAG, "Message Carbons (XEP-0280) enabled.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to enable Message Carbons: ${e.message}")
            }
            
            // Enable XEP-0199: XMPP Ping to keep connection alive
            val pingManager = org.jivesoftware.smackx.ping.PingManager.getInstanceFor(connection)
            pingManager.pingInterval = 30 // Send ping every 30 seconds
            
            // Enable XEP-0030 & XEP-0115: Service Discovery and Entity Capabilities
            org.jivesoftware.smackx.caps.EntityCapsManager.setDefaultEntityNode("http://ndregil.inchat")
            
            // Enable XEP-0092: Software Version
            val versionManager = VersionManager.getInstanceFor(connection)
            versionManager.setVersion("InChat", "1.0.0", "Android ${Build.VERSION.RELEASE}")

            // Enable XEP-0184: Message Delivery Receipts
            val deliveryReceiptManager = org.jivesoftware.smackx.receipts.DeliveryReceiptManager.getInstanceFor(connection)
            deliveryReceiptManager.autoReceiptMode = org.jivesoftware.smackx.receipts.DeliveryReceiptManager.AutoReceiptMode.always
            deliveryReceiptManager.autoAddDeliveryReceiptRequests()
            
            _connectionStatus.value = ConnectionStatus.CONNECTED

            Result.success(connection)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect and login with Smack: ${e.message}", e)
            _connectionStatus.value = ConnectionStatus.ERROR
            _lastError.value = e.localizedMessage ?: "XMPP connection failure"
            safeDisconnectInternal()
            Result.failure(e)
        }
    }

    /**
     * Connects socket without immediate authentication.
     */
    suspend fun connect(): Result<XMPPTCPConnection> = withContext(Dispatchers.IO) {
        val creds = _credentials.value
        try {
            _connectionStatus.value = ConnectionStatus.CONNECTING
            _lastError.value = null

            safeDisconnectInternal()

            val domainJid = JidCreate.domainBareFrom(creds.domain.trim())
            val resource = creds.resource.ifBlank { "Android-MUC" }

            val configBuilder = XMPPTCPConnectionConfiguration.builder()
                .setXmppDomain(domainJid)
                .setPort(creds.port)
                .setResource(Resourcepart.from(resource))
                .setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MS)
                .setSecurityMode(
                    if (creds.useTls) ConnectionConfiguration.SecurityMode.required
                    else ConnectionConfiguration.SecurityMode.disabled
                )

            if (creds.host.isNotBlank()) {
                configBuilder.setHost(creds.host.trim())
            }

            val connection = XMPPTCPConnection(configBuilder.build())
            connection.addConnectionListener(smackConnectionListener)

            connection.connect()
            tcpConnection = connection
            _connectionStatus.value = ConnectionStatus.CONNECTING

            Result.success(connection)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect: ${e.message}", e)
            _connectionStatus.value = ConnectionStatus.ERROR
            _lastError.value = e.localizedMessage ?: "Connection error"
            safeDisconnectInternal()
            Result.failure(e)
        }
    }

    /**
     * Authenticates an already-connected Smack session.
     */
    suspend fun login(passwordOverride: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        val conn = tcpConnection ?: return@withContext Result.failure(IllegalStateException("Connection is not established"))
        val creds = _credentials.value
        val pass = passwordOverride ?: creds.password
        val user = creds.username.trim()
        val resource = Resourcepart.from(creds.resource.ifBlank { "Android-MUC" })

        try {
            _connectionStatus.value = ConnectionStatus.AUTHENTICATING
            if (user.isNotBlank() && pass.isNotBlank()) {
                conn.login(user, pass, resource)
            } else {
                conn.login()
            }
            mucManager = MultiUserChatManager.getInstanceFor(conn)
            chatManager = org.jivesoftware.smack.chat2.ChatManager.getInstanceFor(conn)

            // Enable XEP-0280: Message Carbons
            try {
                val carbonManager = org.jivesoftware.smackx.carbons.CarbonManager.getInstanceFor(conn)
                if (carbonManager.isSupportedByServer) {
                    carbonManager.enableCarbons()
                    Log.i(TAG, "Message Carbons (XEP-0280) enabled.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to enable Message Carbons: ${e.message}")
            }
            
            // Enable XEP-0199: XMPP Ping
            val pingManager = org.jivesoftware.smackx.ping.PingManager.getInstanceFor(conn)
            pingManager.pingInterval = 30
            
            // Enable XEP-0030 & XEP-0115: Service Discovery and Entity Capabilities
            org.jivesoftware.smackx.caps.EntityCapsManager.setDefaultEntityNode("http://ndregil.inchat")
            
            // Enable XEP-0092: Software Version
            val versionManager = VersionManager.getInstanceFor(conn)
            versionManager.setVersion("InChat", "1.0.0", "Android ${Build.VERSION.RELEASE}")

            // Enable XEP-0184: Message Delivery Receipts
            val deliveryReceiptManager = org.jivesoftware.smackx.receipts.DeliveryReceiptManager.getInstanceFor(conn)
            deliveryReceiptManager.autoReceiptMode = org.jivesoftware.smackx.receipts.DeliveryReceiptManager.AutoReceiptMode.always
            deliveryReceiptManager.autoAddDeliveryReceiptRequests()
            
            _connectionStatus.value = ConnectionStatus.CONNECTED
            _lastError.value = null
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Login failed: ${e.message}", e)
            _connectionStatus.value = ConnectionStatus.ERROR
            _lastError.value = e.localizedMessage ?: "Authentication failed"
            Result.failure(e)
        }
    }

    /**
     * Broadcasts updated user presence to the server.
     */
    suspend fun updatePresence(
        userPresence: UserPresence,
        statusMessage: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val conn = tcpConnection
        if (conn == null || !conn.isAuthenticated) {
            return@withContext Result.failure(IllegalStateException("Not connected or authenticated"))
        }

        try {
            val presenceMode = when (userPresence) {
                UserPresence.ONLINE -> Presence.Mode.available
                UserPresence.CHAT -> Presence.Mode.chat
                UserPresence.AWAY -> Presence.Mode.away
                UserPresence.XA -> Presence.Mode.xa
                UserPresence.DND -> Presence.Mode.dnd
                UserPresence.OFFLINE -> null
            }

            val presence = if (userPresence == UserPresence.OFFLINE) {
                PresenceBuilder.buildPresence()
                    .ofType(Presence.Type.unavailable)
                    .setStatus(statusMessage ?: "Offline")
                    .build()
            } else {
                val builder = PresenceBuilder.buildPresence()
                    .ofType(Presence.Type.available)
                    .setStatus(statusMessage ?: "Active on InChat")
                if (presenceMode != null) {
                    builder.setMode(presenceMode)
                }
                builder.build()
            }

            conn.sendStanza(presence)
            Log.d(TAG, "Sent presence update: $userPresence, status: $statusMessage")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update presence: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Disconnects the active connection gracefully.
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        safeDisconnectInternal()
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    /**
     * Registers a new XMPP JID user account on a remote XMPP server using XEP-0077 (In-Band Registration).
     */
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
            val rawDomain = domain.trim().lowercase(Locale.getDefault())
            val cleanDomain = when (rawDomain) {
                "conversations.m", "conversation.im", "conversations.i" -> "conversations.im"
                "trashserver.n", "trashserver.de" -> "trashserver.net"
                "xmpp.j" -> "xmpp.jp"
                else -> rawDomain
            }
            val cleanUser = username.trim()
            val rawHost = host.trim().ifBlank { cleanDomain }
            val cleanHost = when (rawHost.lowercase(Locale.getDefault())) {
                "conversations.m", "conversation.im", "conversations.i" -> "conversations.im"
                "trashserver.n", "trashserver.de" -> "trashserver.net"
                "xmpp.j" -> "xmpp.jp"
                else -> rawHost
            }

            Log.i(TAG, "Starting In-Band Registration (XEP-0077) for user '$cleanUser' at $cleanDomain ($cleanHost:$port)")

            val domainJid = JidCreate.domainBareFrom(cleanDomain)
            val configBuilder = XMPPTCPConnectionConfiguration.builder()
                .setXmppDomain(domainJid)
                .setPort(port)
                .setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MS)
                .setSecurityMode(
                    if (useTls) ConnectionConfiguration.SecurityMode.required
                    else ConnectionConfiguration.SecurityMode.disabled
                )

            if (cleanHost.isNotBlank()) {
                configBuilder.setHost(cleanHost)
                try {
                    val address = InetAddress.getByName(cleanHost)
                    configBuilder.setHostAddress(address)
                } catch (e: Exception) {
                    Log.w(TAG, "DNS resolution warning for registration host $cleanHost: ${e.message}")
                }
            }

            try {
                val config = configBuilder.build()
                tempConnection = XMPPTCPConnection(config)
                tempConnection.connect()
            } catch (connErr: Exception) {
                Log.w(TAG, "Primary registration connection to $cleanHost:$port failed (${connErr.message}), trying fallback direct host connection...")
                try {
                    val fallbackBuilder = XMPPTCPConnectionConfiguration.builder()
                        .setXmppDomain(domainJid)
                        .setHost(cleanDomain)
                        .setPort(port)
                        .setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MS)
                        .setSecurityMode(
                            if (useTls) ConnectionConfiguration.SecurityMode.required
                            else ConnectionConfiguration.SecurityMode.disabled
                        )
                    val fallbackConfig = fallbackBuilder.build()
                    tempConnection = XMPPTCPConnection(fallbackConfig)
                    tempConnection.connect()
                } catch (fallbackErr: Exception) {
                    throw connErr
                }
            }

            val accountManager = AccountManager.getInstance(tempConnection)
            accountManager.sensitiveOperationOverInsecureConnection(true)

            val supportsCreation = try {
                accountManager.supportsAccountCreation()
            } catch (e: Exception) {
                Log.w(TAG, "Error checking account creation support: ${e.message}")
                true
            }
            val instructions = try {
                accountManager.getAccountInstructions()
            } catch (_: Exception) {
                null
            }
            Log.i(TAG, "Server $cleanDomain account creation support: $supportsCreation, instructions: $instructions")

            val localpart = Localpart.from(cleanUser)
            var registrationSuccess = false

            // Step 1: Query server for registration fields/form (XEP-0077)
            val queryReg = org.jivesoftware.smackx.iqregister.packet.Registration()
            queryReg.type = org.jivesoftware.smack.packet.IQ.Type.get
            queryReg.to = JidCreate.from(cleanDomain)

            var serverDataForm: org.jivesoftware.smackx.xdata.packet.DataForm? = null
            var oobRedirectUrl: String? = null
            var iqResponseXml = ""

            try {
                val collector = tempConnection.createStanzaCollectorAndSend(queryReg)
                val responseIq = collector.nextResultOrThrow<org.jivesoftware.smack.packet.Stanza>()
                iqResponseXml = responseIq.toXML().toString()

                // Check for Out-Of-Band Data redirection (XEP-0077 <x xmlns='jabber:x:oob'>)
                val oobRegex = Regex("""<x[^>]*xmlns=['"]jabber:x:oob['"][^>]*>\s*<url>([^<]+)</url>""", RegexOption.IGNORE_CASE)
                oobRedirectUrl = oobRegex.find(iqResponseXml)?.groupValues?.get(1)?.trim()

                if (responseIq is org.jivesoftware.smackx.iqregister.packet.Registration) {
                    serverDataForm = responseIq.getExtension(org.jivesoftware.smackx.xdata.packet.DataForm::class.java)
                        ?: org.jivesoftware.smackx.xdata.packet.DataForm.from(responseIq)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Query registration IQ form note: ${e.message}")
            }

            if (!oobRedirectUrl.isNullOrBlank()) {
                Log.i(TAG, "Server $cleanDomain requested OOB web registration redirect: $oobRedirectUrl")
                try { tempConnection.disconnect() } catch (_: Exception) {}
                return@withContext Result.failure(Exception("Server $cleanDomain mengarahkan registrasi ke Portal Web resmi (XEP-0077 OOB): $oobRedirectUrl"))
            }

            Log.d(TAG, "Registration IQ Response XML for $cleanDomain: $iqResponseXml")

            // Step 1B: Check for CAPTCHA challenge (XEP-0158) & Bits of Binary (XEP-0231 urn:xmpp:bob)
            val captchaImageBytes = XmppBobManager.extractBytesFromStanza(iqResponseXml)
            if (captchaImageBytes != null) {
                Log.i(TAG, "Extracted Bits of Binary (XEP-0231) CAPTCHA image bytes: ${captchaImageBytes.size} bytes")
            }

            var captchaImageUrl: String? = null


            if (captchaImageBytes == null) {
                val mediaUrlRegex = Regex("""https?://[^\s<"]+(?:captcha|ocr|challenge|bob)[^\s<"]*""", RegexOption.IGNORE_CASE)
                captchaImageUrl = mediaUrlRegex.find(iqResponseXml)?.value?.trim()
            }

            val challengeVar = if (iqResponseXml.contains("var='challenge'") || iqResponseXml.contains("var=\"challenge\"")) "challenge" else "ocr"

            if (captchaImageBytes != null || !captchaImageUrl.isNullOrBlank()) {
                val challenge = CaptchaChallengeData(
                    domain = cleanDomain,
                    username = cleanUser,
                    password = password,
                    host = cleanHost,
                    port = port,
                    email = email,
                    nickname = nickname,
                    useTls = useTls,
                    challengeVar = challengeVar,
                    captchaImageBytes = captchaImageBytes,
                    captchaImageUrl = captchaImageUrl
                )
                Log.i(TAG, "CAPTCHA challenge detected for domain $cleanDomain (Bits of Binary / XEP-0158). Interrupting flow for user verification.")
                try { tempConnection.disconnect() } catch (_: Exception) {}
                return@withContext Result.failure(CaptchaRequiredException(challenge))
            }

            // Step 2: If server returned DataForm (XEP-0077 DataForms / Conversations style), submit DataForm dynamically
            if (serverDataForm != null) {
                try {
                    Log.i(TAG, "Server $cleanDomain returned DataForm for registration. Dynamically building XEP-0077 DataForm...")
                    val submitReg = org.jivesoftware.smackx.iqregister.packet.Registration()
                    submitReg.type = org.jivesoftware.smack.packet.IQ.Type.set
                    submitReg.to = JidCreate.from(cleanDomain)

                    val formBuilder = org.jivesoftware.smackx.xdata.packet.DataForm.builder(org.jivesoftware.smackx.xdata.packet.DataForm.Type.submit)

                    // Map all fields from the server form dynamically
                    for (field in serverDataForm.fields) {
                        val varName = field.variable ?: continue
                        val fB = org.jivesoftware.smackx.xdata.FormField.builder(varName)
                        when (varName.lowercase()) {
                            "username" -> fB.setValue(cleanUser)
                            "password" -> fB.setValue(password)
                            "email" -> if (email.isNotBlank()) fB.setValue(email.trim()) else { field.values.firstOrNull()?.let { fB.setValue(it) } }
                            "name", "given", "nick", "nickname" -> if (nickname.isNotBlank()) fB.setValue(nickname.trim()) else { field.values.firstOrNull()?.let { fB.setValue(it) } }
                            "form_type" -> fB.setValue("jabber:iq:register")
                            else -> {
                                for (valItem in field.values) {
                                    fB.addValue(valItem)
                                }
                            }
                        }
                        formBuilder.addField(fB.build())
                    }

                    // Guarantee core fields if missing from server field list
                    if (serverDataForm.getField("username") == null) {
                        formBuilder.addField(org.jivesoftware.smackx.xdata.FormField.builder("username").setValue(cleanUser).build())
                    }
                    if (serverDataForm.getField("password") == null) {
                        formBuilder.addField(org.jivesoftware.smackx.xdata.FormField.builder("password").setValue(password).build())
                    }

                    val submitForm = formBuilder.build()
                    submitReg.addExtension(submitForm)

                    val submitCollector = tempConnection.createStanzaCollectorAndSend(submitReg)
                    submitCollector.nextResultOrThrow<org.jivesoftware.smack.packet.Stanza>()
                    registrationSuccess = true
                    Log.i(TAG, "Registrasi via DataForm (XEP-0077) berhasil di server $cleanDomain")
                } catch (dfErr: Exception) {
                    Log.w(TAG, "DataForm registration attempt failed: ${dfErr.message}, falling back to AccountManager...")
                }
            }

            // Step 3: Fallback to AccountManager (Try username & password first)
            if (!registrationSuccess) {
                val attributes = mutableMapOf<String, String>()
                if (nickname.isNotBlank()) attributes["name"] = nickname.trim()
                if (email.isNotBlank()) attributes["email"] = email.trim()

                try {
                    // Primary attempt: Username & Password strictly (most compatible with all XMPP servers)
                    accountManager.createAccount(localpart, password)
                    registrationSuccess = true
                    Log.i(TAG, "Registrasi standar (username & password) berhasil di server $cleanDomain")
                } catch (e: XMPPException.XMPPErrorException) {
                    val condition = e.stanzaError?.condition?.toString()
                    Log.w(TAG, "Standard registration with username/password returned condition: $condition")
                    if (attributes.isNotEmpty()) {
                        try {
                            Log.i(TAG, "Retrying registration with extra attributes...")
                            accountManager.createAccount(localpart, password, attributes)
                            registrationSuccess = true
                        } catch (_: Exception) {
                            throw e
                        }
                    } else {
                        throw e
                    }
                }
            }

            try {
                tempConnection.disconnect()
            } catch (_: Exception) {}

            val registeredJid = "$cleanUser@$cleanDomain"
            Log.i(TAG, "Registrasi JID nyata berhasil dibuat di server: $registeredJid")
            Result.success(registeredJid)
        } catch (e: XMPPException.XMPPErrorException) {
            val stanzaXml = e.stanzaError?.toXML()?.toString() ?: ""
            val oobRegex = Regex("""<x[^>]*xmlns=['"]jabber:x:oob['"][^>]*>\s*<url>([^<]+)</url>""", RegexOption.IGNORE_CASE)
            val oobUrl = oobRegex.find(stanzaXml)?.groupValues?.get(1)?.trim()
                ?: Regex("""https?://[^\s<"]+(?:register|account|signup|verify|captcha)[^\s<"]*""", RegexOption.IGNORE_CASE).find(stanzaXml)?.value?.trim()
            
            val condition = e.stanzaError?.condition?.toString()
            val descriptiveText = e.stanzaError?.descriptiveText

            val redirectUrl = when {
                !oobUrl.isNullOrBlank() -> oobUrl
                condition == "not-allowed" || condition == "forbidden" || condition == "not-acceptable" -> {
                    when {
                        domain.contains("xmpp.jp", ignoreCase = true) -> "https://xmpp.jp/signup"
                        domain.contains("conversations.im", ignoreCase = true) -> "https://conversations.im/account/"
                        domain.contains("trashserver.net", ignoreCase = true) -> "https://trashserver.net/register/"
                        domain.contains("jabber.at", ignoreCase = true) -> "https://jabber.at/account/register"
                        else -> "https://$domain"
                    }
                }
                else -> null
            }

            val errorMsg = when {
                !oobUrl.isNullOrBlank() ->
                    "Server $domain mengarahkan registrasi ke Portal Web resmi (XEP-0077 OOB): $oobUrl"
                condition == "conflict" ->
                    "Username '$username' sudah digunakan di server $domain. Silakan pilih username lain."
                condition == "not-allowed" || condition == "forbidden" ->
                    "Server $domain membatasi pendaftaran langsung via TCP (kebijakan anti-spam server). Mengalihkan ke Portal Web..."
                condition == "not-acceptable" ->
                    "Server $domain membutuhkan penyelesaian Captcha atau verifikasi tambahan. Mengalihkan ke Portal Web..."
                condition == "bad-request" ->
                    "Server $domain menolak format pendaftaran (membutuhkan verifikasi Captcha/Web atau format password tidak sesuai)."
                condition == "resource-constraint" ->
                    "Server $domain membatasi pendaftaran baru (rate limit IP). Coba beberapa saat lagi atau gunakan Portal Web."
                else ->
                    if (!descriptiveText.isNullOrBlank()) descriptiveText else "Server $domain menolak pendaftaran (Stanza error: ${condition ?: "unknown"})."
            }
            Log.e(TAG, "Registration failed with stanza error: $errorMsg", e)
            try { tempConnection?.disconnect() } catch (_: Exception) {}

            if (redirectUrl != null) {
                Result.failure(RegistrationRedirectException(redirectUrl, errorMsg))
            } else {
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register JID account: ${e.message}", e)
            try { tempConnection?.disconnect() } catch (_: Exception) {}
            val errText = e.message ?: ""
            val fallbackMsg = when {
                errText.contains("NX_DOMAIN", ignoreCase = true) || errText.contains("UnknownHost", ignoreCase = true) || errText.contains("ResolutionUnsuccessfulException", ignoreCase = true) ->
                    "Domain '$domain' tidak ditemukan di internet. Mohon periksa ejaan domain Anda (contoh: conversations.im, xmpp.jp, trashserver.net)."
                errText.contains("EndpointConnectionException", ignoreCase = true) || errText.contains("Connection refused", ignoreCase = true) ->
                    "Gagal terhubung ke $domain:$port. Server mungkin sedang offline atau port diblokir."
                else ->
                    e.localizedMessage?.ifBlank { null } ?: "Gagal terhubung ke server $domain untuk registrasi. Pastikan domain aktif dan coba server lain."
            }
            Result.failure(Exception(fallbackMsg))
        }
    }

    /**
     * Queries software version (XEP-0092) and disco info (XEP-0030) from a target JID.
     */
    suspend fun querySoftwareVersion(targetJid: String): Result<com.ndregil.inchat.data.model.ClientCaps> = withContext(Dispatchers.IO) {
        val conn = tcpConnection
        if (conn == null || !conn.isAuthenticated) {
            return@withContext Result.failure(IllegalStateException("Koneksi XMPP belum terhubung."))
        }

        try {
            val jid = JidCreate.from(targetJid.trim())
            val versionManager = VersionManager.getInstanceFor(conn)
            val verPacket = versionManager.getVersion(jid)

            val softwareName = verPacket?.name?.ifBlank { null } ?: "Unknown Client"
            val versionStr = verPacket?.version?.ifBlank { null } ?: "1.0"
            val osStr = verPacket?.os?.ifBlank { null } ?: "Unknown OS"

            val platform = when {
                softwareName.contains("Android", ignoreCase = true) || osStr.contains("Android", ignoreCase = true) || softwareName.contains("Conversations", ignoreCase = true) -> com.ndregil.inchat.data.model.ClientPlatform.ANDROID
                softwareName.contains("iOS", ignoreCase = true) || osStr.contains("iOS", ignoreCase = true) || softwareName.contains("Monal", ignoreCase = true) -> com.ndregil.inchat.data.model.ClientPlatform.IOS
                softwareName.contains("Web", ignoreCase = true) || softwareName.contains("Movim", ignoreCase = true) -> com.ndregil.inchat.data.model.ClientPlatform.WEB
                softwareName.contains("Bot", ignoreCase = true) -> com.ndregil.inchat.data.model.ClientPlatform.BOT
                else -> com.ndregil.inchat.data.model.ClientPlatform.DESKTOP
            }

            val featuresList = mutableListOf<String>()
            try {
                val discoManager = org.jivesoftware.smackx.disco.ServiceDiscoveryManager.getInstanceFor(conn)
                val info = discoManager.discoverInfo(jid)
                info.features.forEach { f ->
                    featuresList.add(f.`var`)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Disco info query failed for $targetJid: ${e.message}")
            }

            val base = com.ndregil.inchat.data.model.ClientCaps.resolveFor(
                name = softwareName,
                jid = targetJid,
                node = verPacket?.name ?: softwareName
            )

            val caps = base.copy(
                clientName = softwareName,
                version = versionStr,
                os = osStr,
                platform = if (base.platform != com.ndregil.inchat.data.model.ClientPlatform.UNKNOWN) base.platform else platform,
                features = featuresList.ifEmpty { base.features }
            )

            Log.i(TAG, "Successfully queried software version for $targetJid: ${caps.clientName} v${caps.version} on ${caps.os}")
            Result.success(caps)
        } catch (e: Exception) {
            Log.w(TAG, "Version query failed for $targetJid: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Submits a solved CAPTCHA challenge (XEP-0158) back to the XMPP server via DataForm.
     */
    suspend fun submitCaptchaAnswer(
        challenge: CaptchaChallengeData,
        captchaAnswer: String
    ): Result<String> = withContext(Dispatchers.IO) {
        var tempConnection: XMPPTCPConnection? = null
        try {
            val configBuilder = XMPPTCPConnectionConfiguration.builder()
                .setXmppDomain(JidCreate.domainBareFrom(challenge.domain))
                .setHost(challenge.host.ifBlank { challenge.domain })
                .setPort(challenge.port)
                .setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MS)
                .setSecurityMode(
                    if (challenge.useTls) ConnectionConfiguration.SecurityMode.required
                    else ConnectionConfiguration.SecurityMode.disabled
                )

            val config = configBuilder.build()
            tempConnection = XMPPTCPConnection(config)
            tempConnection.connect()

            val submitReg = org.jivesoftware.smackx.iqregister.packet.Registration()
            submitReg.type = org.jivesoftware.smack.packet.IQ.Type.set
            submitReg.to = JidCreate.from(challenge.domain)

            val formBuilder = org.jivesoftware.smackx.xdata.packet.DataForm.builder(org.jivesoftware.smackx.xdata.packet.DataForm.Type.submit)
            formBuilder.addField(org.jivesoftware.smackx.xdata.FormField.builder("FORM_TYPE").setValue("jabber:iq:register").build())
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

            val collector = tempConnection.createStanzaCollectorAndSend(submitReg)
            collector.nextResultOrThrow<org.jivesoftware.smack.packet.Stanza>()

            try { tempConnection.disconnect() } catch (_: Exception) {}

            val registeredJid = "${challenge.username}@${challenge.domain}"
            Log.i(TAG, "Registrasi dengan CAPTCHA (XEP-0158) berhasil: $registeredJid")
            Result.success(registeredJid)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengirim jawaban CAPTCHA: ${e.message}", e)
            try { tempConnection?.disconnect() } catch (_: Exception) {}
            Result.failure(Exception("Jawaban CAPTCHA salah atau ditolak server: ${e.localizedMessage ?: "Unknown error"}"))
        }
    }

    /**
     * Internal safe cleanup of TCP socket and listeners without throwing exceptions.
     */
    private fun safeDisconnectInternal() {
        try {
            tcpConnection?.let { conn ->
                conn.removeConnectionListener(smackConnectionListener)
                if (conn.isConnected) {
                    conn.disconnect()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception during Smack disconnect: ${e.message}")
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

class CaptchaRequiredException(val challenge: CaptchaChallengeData) : Exception("Server ${challenge.domain} memerlukan verifikasi CAPTCHA (XEP-0158).")

class RegistrationRedirectException(val url: String, override val message: String) : Exception(message)

