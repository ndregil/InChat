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
import org.jivesoftware.smack.packet.XmlEnvironment
import org.jivesoftware.smack.filter.StanzaIdFilter
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
            var clean = rawDomain.trim().lowercase(Locale.ROOT)
            if (clean.startsWith("xmpp:")) clean = clean.removePrefix("xmpp:")
            if (clean.startsWith("https://")) clean = clean.removePrefix("https://")
            if (clean.startsWith("http://")) clean = clean.removePrefix("http://")
            return clean.substringBefore("/").trim()
        }

        fun suggestDomainCorrection(rawDomain: String): String? {
            val clean = sanitizeDomain(rawDomain)
            return when {
                clean == "comversations.im" || clean == "comversation.im" ||
                    clean == "conversations.m" || clean == "conversations.i" ||
                    clean == "conversation.im" -> "conversations.im"
                clean == "trashserver.n" -> "trashserver.net"
                clean == "xmpp.j" -> "xmpp.jp"
                clean.contains("comversations.im") -> clean.replace("comversations.im", "conversations.im")
                else -> null
            }
        }

        fun sanitizeHost(rawHost: String, @Suppress("UNUSED_PARAMETER") cleanDomain: String = ""): String {
            val clean = rawHost.trim().lowercase(Locale.ROOT)
            if (clean.isBlank() || clean == "auto") return ""
            return clean.removePrefix("https://").removePrefix("http://").substringBefore("/").trim()
        }

        fun sanitizeJid(rawJid: String): String {
            val clean = rawJid.trim()
            if (!clean.contains("@")) return clean
            val user = clean.substringBefore("@").trim()
            val rest = clean.substringAfter("@")
            val res = if (rest.contains("/")) "/" + rest.substringAfter("/") else ""
            val dom = rest.substringBefore("/")
            val cleanDom = sanitizeDomain(dom)
            return "$user@$cleanDom$res"
        }

        fun fromAccount(account: XmppAccount, password: String = ""): XmppCredentials {
            val cleanDom = sanitizeDomain(account.domain)
            val cleanHost = sanitizeHost(account.host)
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
        configureSmackIdentityAndCaps()
    }

    private fun configureSmackIdentityAndCaps() {
        try {
            org.jivesoftware.smackx.disco.ServiceDiscoveryManager.setDefaultIdentity(
                org.jivesoftware.smackx.disco.packet.DiscoverInfo.Identity("client", "InChat", "phone")
            )
            org.jivesoftware.smackx.caps.EntityCapsManager.setDefaultEntityNode("http://ndregil.inchat")
        } catch (e: Exception) {
            Log.w(TAG, "Failed setting Smack default identity/caps node: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "XmppConnectionManager"
        private const val DEFAULT_CONNECT_TIMEOUT_MS = 30000

        fun sanitizeDomain(rawDomain: String): String = XmppCredentials.sanitizeDomain(rawDomain)
        fun suggestDomainCorrection(rawDomain: String): String? = XmppCredentials.suggestDomainCorrection(rawDomain)
        fun sanitizeHost(rawHost: String, cleanDomain: String = ""): String = XmppCredentials.sanitizeHost(rawHost, cleanDomain)
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
        val cleanHost = sanitizeHost(creds.host)

        try {
            _connectionStatus.value = ConnectionStatus.CONNECTING
            _lastError.value = null

            safeDisconnectInternal()

            Log.i(TAG, "Initializing XMPP connection to ${cleanHost.ifBlank { "SRV($cleanDomain)" }}:${creds.port} (domain=$cleanDomain)")

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

            if (cleanHost.isNotBlank() && !cleanHost.equals(cleanDomain, ignoreCase = true)) {
                configBuilder.setHost(cleanHost)
                try {
                    val address = InetAddress.getByName(cleanHost)
                    configBuilder.setHostAddress(address)
                } catch (e: Exception) {
                    Log.w(TAG, "DNS resolution warning: ${e.message}")
                }
            } else if (cleanHost.isNotBlank() && creds.port != 5222) {
                configBuilder.setHost(cleanHost)
            }

            if (username.isNotBlank()) {
                configBuilder.setUsernameAndPassword(username, creds.password)
            }

            configureSmackIdentityAndCaps()
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
                val sdm = org.jivesoftware.smackx.disco.ServiceDiscoveryManager.getInstanceFor(connection)
                sdm.identity = org.jivesoftware.smackx.disco.packet.DiscoverInfo.Identity("client", "InChat", "phone")
                val capsManager = org.jivesoftware.smackx.caps.EntityCapsManager.getInstanceFor(connection)
                if (!capsManager.entityCapsEnabled()) {
                    capsManager.enableEntityCaps()
                }
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
                isTlsCertificateException(e) ->
                    "Koneksi TLS gagal: Sertifikat SSL server $cleanDomain tidak dipercaya atau tidak valid (${extractDeepestMessage(e)})."
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

            configureSmackIdentityAndCaps()
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
                val capsVer = try {
                    org.jivesoftware.smackx.caps.EntityCapsManager.getInstanceFor(conn)?.capsVersionAndHash
                } catch (_: Exception) { null }
                builder.overrideExtension(
                    org.jivesoftware.smackx.caps.packet.CapsExtension(
                        "http://ndregil.inchat",
                        capsVer?.version ?: "kFwX23eY765qL90mN12aBcDeFgH=",
                        capsVer?.hash ?: "sha-1"
                    )
                )
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

    private fun isTlsCertificateException(throwable: Throwable?): Boolean {
        var curr: Throwable? = throwable
        while (curr != null) {
            if (curr is javax.net.ssl.SSLException ||
                curr is java.security.cert.CertificateException ||
                curr is java.security.cert.CertPathValidatorException
            ) {
                return true
            }
            val msg = curr.message?.lowercase(Locale.ROOT) ?: ""
            if (msg.contains("ssl") ||
                msg.contains("tls") ||
                msg.contains("certificate") ||
                msg.contains("certpath") ||
                msg.contains("trust anchor") ||
                msg.contains("handshake") ||
                msg.contains("pkix")
            ) {
                return true
            }
            curr = curr.cause
        }
        return false
    }

    private fun extractDeepestMessage(throwable: Throwable?): String {
        var curr = throwable
        var best = throwable?.localizedMessage ?: throwable?.message ?: "Unknown error"
        while (curr != null) {
            val m = curr.localizedMessage ?: curr.message
            if (!m.isNullOrBlank()) best = m
            curr = curr.cause
        }
        return best
    }

    private fun openRegistrationConnection(
        cleanDomain: String,
        explicitHost: String,
        port: Int,
        useTls: Boolean
    ): XMPPTCPConnection {
        val domainJid = JidCreate.domainBareFrom(cleanDomain)
        val securityMode = if (useTls) {
            ConnectionConfiguration.SecurityMode.required
        } else {
            ConnectionConfiguration.SecurityMode.disabled
        }

        fun buildConn(forceHost: String?): XMPPTCPConnection {
            val builder = XMPPTCPConnectionConfiguration.builder()
                .setXmppDomain(domainJid)
                .setPort(port)
                .setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MS)
                .setSecurityMode(securityMode)
            if (!forceHost.isNullOrBlank()) {
                builder.setHost(forceHost)
            }
            val conn = XMPPTCPConnection(builder.build())
            conn.replyTimeout = DEFAULT_CONNECT_TIMEOUT_MS.toLong()
            return conn
        }

        val effectiveHost = sanitizeHost(explicitHost)
        if (effectiveHost.isNotBlank()) {
            val conn = buildConn(effectiveHost)
            try {
                conn.connect()
                return conn
            } catch (e: Exception) {
                try { conn.disconnect() } catch (_: Exception) {}
                if (isTlsCertificateException(e)) {
                    throw RegistrationTlsException(
                        "Koneksi TLS ke $effectiveHost gagal karena sertifikat SSL server tidak dipercaya atau tidak cocok dengan domain $cleanDomain (${extractDeepestMessage(e)})."
                    )
                }
                throw e
            }
        }

        // No explicit host -> let Smack perform DNS SRV (_xmpp-client._tcp.<domain>) lookup first
        val srvConn = buildConn(null)
        return try {
            Log.d(TAG, "Connecting to $cleanDomain using DNS SRV (port=$port, tls=$useTls)")
            srvConn.connect()
            srvConn
        } catch (e: Exception) {
            try { srvConn.disconnect() } catch (_: Exception) {}
            if (isTlsCertificateException(e)) {
                throw RegistrationTlsException(
                    "Koneksi TLS ke $cleanDomain gagal karena sertifikat SSL server tidak dipercaya atau tidak valid (${extractDeepestMessage(e)})."
                )
            }
            // Fallback to direct domain host if SRV failed due to Android DNS/host resolution
            Log.w(TAG, "SRV connect to $cleanDomain failed (${e.message}), trying direct host fallback")
            val directConn = buildConn(cleanDomain)
            try {
                directConn.connect()
                directConn
            } catch (e2: Exception) {
                try { directConn.disconnect() } catch (_: Exception) {}
                if (isTlsCertificateException(e2)) {
                    throw RegistrationTlsException(
                        "Koneksi TLS ke $cleanDomain gagal karena sertifikat SSL server tidak dipercaya atau tidak valid (${extractDeepestMessage(e2)})."
                    )
                }
                throw e2
            }
        }
    }

    private fun extractOobUrl(xml: String): String? {
        if (xml.isBlank()) return null
        val oobMatch = Regex(
            """<x[^>]*xmlns=['"]jabber:x:oob['"][^>]*>.*?<url[^>]*>(https?://[^<\s]+)</url>.*?</x>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(xml)
        return oobMatch?.groupValues?.get(1)?.trim()
    }

    private fun extractWebUrl(xml: String, fallbackText: String? = null): String? {
        // 1. Formal jabber:x:oob
        val oob = extractOobUrl(xml)
        if (!oob.isNullOrBlank()) return oob

        // 2. Any explicit <url> tag in XML
        val urlTag = Regex(
            """<url[^>]*>(https?://[^<\s]+)</url>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(xml)?.groupValues?.get(1)?.trim()
        if (!urlTag.isNullOrBlank()) {
            return urlTag.trimEnd('.', ',', ';', ':', ')', ']', '>')
        }

        // 3. Any http/https link in fallback descriptive text / instructions / error message
        val urlRegex = Regex("""https?://[^\s<>"')]+""", RegexOption.IGNORE_CASE)
        val textUrl = fallbackText?.let { urlRegex.find(it)?.value }
        if (!textUrl.isNullOrBlank()) {
            return textUrl.trim().trimEnd('.', ',', ';', ':', ')', ']', '>')
        }

        // 4. Any http/https link anywhere in the stanza XML
        val xmlUrl = urlRegex.find(xml)?.value
        if (!xmlUrl.isNullOrBlank()) {
            return xmlUrl.trim().trimEnd('.', ',', ';', ':', ')', ']', '>')
        }

        return null
    }

    private fun extractCaptchaMediaUrl(xml: String): String? {
        if (xml.isBlank()) return null
        val mediaBlocks = Regex(
            """<media[^>]*xmlns=['"]urn:xmpp:media-elements['"][^>]*>(.*?)</media>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(xml)
        for (block in mediaBlocks) {
            val inner = block.groupValues[1]
            val uriMatch = Regex(
                """<uri[^>]*>(https?://[^<\s]+)</uri>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).find(inner)
            if (uriMatch != null) {
                return uriMatch.groupValues[1].trim()
            }
        }
        return null
    }

    private fun extractFormFieldOptions(fieldXml: String): List<Pair<String, String>> {
        val options = mutableListOf<Pair<String, String>>()
        val optionRegex = Regex(
            """<option(?:\s+label=['"]([^'"]*)['"])?[^>]*>\s*<value[^>]*>([^<]*)</value>\s*</option>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        for (match in optionRegex.findAll(fieldXml)) {
            val label = match.groupValues[1].trim()
            val value = match.groupValues[2].trim()
            if (value.isNotBlank()) {
                options.add((label.ifBlank { value }) to value)
            }
        }
        return options
    }

    private fun extractInstructionsFromXml(xml: String): String? {
        val m = Regex(
            """<instructions[^>]*>(.*?)</instructions>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(xml) ?: return null
        return m.groupValues[1].trim().takeIf { it.isNotBlank() }
    }

    private fun mapRegistrationError(
        e: XMPPException.XMPPErrorException,
        cleanDomain: String,
        cleanUser: String
    ): Exception {
        val stanzaErr = e.stanzaError
        val errXml = stanzaErr?.toXML()?.toString() ?: ""
        val desc = stanzaErr?.descriptiveText?.trim()?.takeIf { it.isNotBlank() }
            ?: extractInstructionsFromXml(errXml)
            ?: e.message?.trim()?.takeIf { it.isNotBlank() }
        val webUrl = extractWebUrl(errXml, desc)
        val condition = stanzaErr?.condition

        // If username is taken, always report conflict directly
        if (condition == StanzaError.Condition.conflict) {
            return Exception("Username '$cleanUser' sudah digunakan di $cleanDomain. Silakan pilih username lain.", e)
        }

        // If server provided a web registration URL or redirected registration to web
        if (!webUrl.isNullOrBlank()) {
            return WebRegistrationRequiredException(
                domain = cleanDomain,
                registrationUrl = webUrl,
                instructions = desc ?: "Server $cleanDomain mengalihkan pendaftaran ke halaman web."
            )
        }

        val suffix = if (!desc.isNullOrBlank()) " ($desc)" else ""
        return when (condition) {
            StanzaError.Condition.not_allowed,
            StanzaError.Condition.service_unavailable,
            StanzaError.Condition.feature_not_implemented ->
                RegistrationNotAllowedException(
                    "Server $cleanDomain menolak pendaftaran langsung (In-Band Registration tidak tersedia)$suffix."
                )
            StanzaError.Condition.bad_request ->
                Exception("Server $cleanDomain menolak format form pendaftaran (bad-request)$suffix.", e)
            StanzaError.Condition.not_acceptable ->
                Exception("Data atau pendaftaran ditolak oleh server $cleanDomain (not-acceptable)$suffix.", e)
            StanzaError.Condition.resource_constraint ->
                Exception("Terlalu banyak percobaan pendaftaran ke $cleanDomain. Silakan tunggu sebentar lalu coba lagi.", e)
            StanzaError.Condition.forbidden,
            StanzaError.Condition.policy_violation ->
                Exception("Pendaftaran ditolak oleh kebijakan server $cleanDomain$suffix.", e)
            else ->
                Exception("Gagal mendaftar di $cleanDomain$suffix: ${stanzaErr?.condition ?: e.localizedMessage}", e)
        }
    }

    private fun buildChallengeFromDataForm(
        dataForm: DataForm,
        responseXml: String,
        instructions: String?,
        oobUrl: String?,
        domain: String,
        username: String,
        password: String,
        host: String,
        port: Int,
        nickname: String,
        email: String,
        useTls: Boolean
    ): Triple<CaptchaChallengeData, Boolean, Boolean> {
        val formType = dataForm.formType?.toString()?.trim()?.takeIf { it.isNotBlank() }
            ?: "jabber:iq:register"
        val hiddenFields = linkedMapOf<String, String>()
        val dynamicFields = mutableListOf<RegistrationFormField>()
        var captchaVar = ""
        var hasCaptcha = false
        var hasAccountCoreFields = false
        var needsPrompt = false

        val bobBytes = XmppBobManager.extractBytesFromStanza(responseXml)
        val mediaImageUrl = extractCaptchaMediaUrl(responseXml)

        val fixedTexts = mutableListOf<String>()
        for (field in dataForm.fields) {
            val rawVar = field.variable?.trim().orEmpty()
            val vLower = rawVar.lowercase(Locale.ROOT)
            val typeStr = field.type?.toString()?.lowercase(Locale.ROOT).orEmpty()
            val fieldXml = try { field.toXML(XmlEnvironment.EMPTY).toString() } catch (_: Exception) { "" }
            val fieldHasMedia = fieldXml.contains("urn:xmpp:media-elements", ignoreCase = true) ||
                fieldXml.contains("cid:", ignoreCase = true)
            val defaultVal = field.firstValue?.toString()?.trim().orEmpty()

            if (rawVar == "FORM_TYPE") continue

            if (typeStr == "fixed") {
                if (defaultVal.isNotBlank()) fixedTexts.add(defaultVal)
                continue
            }

            if (typeStr == "hidden") {
                if (rawVar.isNotBlank()) {
                    hiddenFields[rawVar] = defaultVal
                }
                continue
            }

            if (vLower == "username" || vLower == "password") {
                hasAccountCoreFields = true
            }

            val isCaptchaField = fieldHasMedia ||
                vLower == "ocr" ||
                vLower == "captcha" ||
                vLower == "captcha_reply" ||
                vLower == "audio_recog" ||
                (vLower == "challenge" && (formType.equals("urn:xmpp:captcha", ignoreCase = true) || bobBytes != null || !mediaImageUrl.isNullOrBlank()))

            if (isCaptchaField && rawVar.isNotBlank()) {
                captchaVar = rawVar
                hasCaptcha = true
                needsPrompt = true
                continue
            }

            val prefilled = when (vLower) {
                "username" -> username
                "password" -> password
                "email" -> email.ifBlank { defaultVal }
                "name", "nick", "nickname" -> nickname.ifBlank { defaultVal }
                else -> defaultVal
            }

            // Determine whether user input is still needed
            val isAutoFilled = when (vLower) {
                "username" -> username.isNotBlank()
                "password" -> password.isNotBlank()
                "email" -> email.isNotBlank() || !field.isRequired
                "name", "nick", "nickname" -> nickname.isNotBlank() || !field.isRequired
                else -> false
            }

            val fieldType = when (typeStr) {
                "text-private" -> RegistrationFieldType.PASSWORD
                "boolean" -> RegistrationFieldType.BOOLEAN
                "list-single" -> RegistrationFieldType.LIST_SINGLE
                else -> RegistrationFieldType.TEXT
            }

            val options = if (fieldType == RegistrationFieldType.LIST_SINGLE) {
                extractFormFieldOptions(fieldXml)
            } else emptyList()

            if (rawVar.isNotBlank() && vLower != "username" && vLower != "password") {
                val label = field.label?.trim()?.takeIf { it.isNotBlank() } ?: rawVar
                dynamicFields.add(
                    RegistrationFormField(
                        variable = rawVar,
                        label = label,
                        type = fieldType,
                        isRequired = field.isRequired,
                        defaultValue = prefilled,
                        options = options
                    )
                )
                if (!isAutoFilled || field.isRequired && prefilled.isBlank() || fieldType == RegistrationFieldType.BOOLEAN) {
                    needsPrompt = true
                }
            }
        }

        if (bobBytes != null || !mediaImageUrl.isNullOrBlank() || formType.equals("urn:xmpp:captcha", ignoreCase = true)) {
            hasCaptcha = true
            if (captchaVar.isBlank()) captchaVar = "ocr"
            needsPrompt = true
        }

        val combinedInstructions = listOfNotNull(
            instructions?.takeIf { it.isNotBlank() },
            fixedTexts.joinToString("\n").takeIf { it.isNotBlank() }
        ).joinToString("\n\n").takeIf { it.isNotBlank() }

        val challenge = CaptchaChallengeData(
            domain = domain,
            username = username,
            password = password,
            host = host,
            port = port,
            email = email,
            nickname = nickname,
            useTls = useTls,
            challengeVar = captchaVar,
            captchaImageBytes = bobBytes,
            captchaImageUrl = mediaImageUrl,
            formType = formType,
            instructions = combinedInstructions,
            hiddenFields = hiddenFields,
            formFields = dynamicFields,
            usesDataForm = true,
            oobUrl = oobUrl
        )
        return Triple(challenge, needsPrompt, hasAccountCoreFields || hasCaptcha || dynamicFields.isNotEmpty())
    }

    private fun buildDataFormSubmitIq(
        domain: String,
        formType: String,
        hiddenFields: Map<String, String>,
        values: Map<String, String>
    ): Registration {
        val submitReg = Registration()
        submitReg.type = IQ.Type.set
        submitReg.to = JidCreate.domainBareFrom(domain)

        val formBuilder = DataForm.builder(DataForm.Type.submit)
        val addedKeys = HashSet<String>()

        formBuilder.addField(
            org.jivesoftware.smackx.xdata.FormField.builder("FORM_TYPE")
                .setValue(formType.ifBlank { "jabber:iq:register" })
                .build()
        )
        addedKeys.add("FORM_TYPE")

        for ((k, v) in hiddenFields) {
            if (k.isNotBlank() && addedKeys.add(k)) {
                formBuilder.addField(
                    org.jivesoftware.smackx.xdata.FormField.builder(k).setValue(v).build()
                )
            }
        }

        for ((k, v) in values) {
            if (k.isNotBlank() && addedKeys.add(k)) {
                formBuilder.addField(
                    org.jivesoftware.smackx.xdata.FormField.builder(k).setValue(v).build()
                )
            }
        }

        submitReg.addExtension(formBuilder.build())
        return submitReg
    }

    private fun sendRegistrationIq(
        connection: XMPPTCPConnection,
        iq: IQ
    ): org.jivesoftware.smack.packet.Stanza {
        if (!iq.hasStanzaIdSet()) {
            iq.stanzaId = java.util.UUID.randomUUID().toString()
        }
        val collector = connection.createStanzaCollectorAndSend(
            StanzaIdFilter(iq.stanzaId),
            iq
        )
        return try {
            collector.nextResultOrThrow<org.jivesoftware.smack.packet.Stanza>()
        } finally {
            collector.cancel()
        }
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
        val cleanDomain = sanitizeDomain(domain)
        val cleanUser = username.trim()
        val explicitHost = sanitizeHost(host)

        try {
            tempConnection = openRegistrationConnection(cleanDomain, explicitHost, port, useTls)

            // Query XEP-0077 Registration GET directly so we preserve DataForm, OOB URL, and BoB CAPTCHA
            val registrationIQ = Registration().apply {
                type = IQ.Type.get
                to = JidCreate.domainBareFrom(cleanDomain)
            }

            val response = try {
                sendRegistrationIq(tempConnection, registrationIQ)
            } catch (e: XMPPException.XMPPErrorException) {
                throw mapRegistrationError(e, cleanDomain, cleanUser)
            }

            val responseXml = response.toXML().toString()
            Log.d(TAG, "Registration GET response stanza XML: $responseXml")

            val regResponse = response as? Registration
            val instructions = regResponse?.instructions?.trim()?.takeIf { it.isNotBlank() }
                ?: extractInstructionsFromXml(responseXml)
            val oobUrl = extractWebUrl(responseXml, instructions)
            val dataForm = DataForm.from(response)

            if (dataForm != null) {
                val (challenge, needsPrompt, hasInBandFields) = buildChallengeFromDataForm(
                    dataForm = dataForm,
                    responseXml = responseXml,
                    instructions = instructions,
                    oobUrl = oobUrl,
                    domain = cleanDomain,
                    username = cleanUser,
                    password = password,
                    host = explicitHost,
                    port = port,
                    nickname = nickname.trim(),
                    email = email.trim(),
                    useTls = useTls
                )

                if (!hasInBandFields && !oobUrl.isNullOrBlank()) {
                    throw WebRegistrationRequiredException(cleanDomain, oobUrl, instructions)
                }

                if (needsPrompt) {
                    throw CaptchaRequiredException(challenge)
                }

                // Server uses DataForm with no missing required fields & no CAPTCHA -> submit pure DataForm
                val submitValues = linkedMapOf<String, String>()
                val formFieldVars = dataForm.fields.mapNotNull { it.variable?.trim() }.toSet()
                if (formFieldVars.any { it.equals("username", ignoreCase = true) }) {
                    val key = formFieldVars.first { it.equals("username", ignoreCase = true) }
                    submitValues[key] = cleanUser
                } else {
                    submitValues["username"] = cleanUser
                }
                if (formFieldVars.any { it.equals("password", ignoreCase = true) }) {
                    val key = formFieldVars.first { it.equals("password", ignoreCase = true) }
                    submitValues[key] = password
                } else {
                    submitValues["password"] = password
                }
                if (email.isNotBlank()) {
                    formFieldVars.firstOrNull { it.equals("email", ignoreCase = true) }?.let {
                        submitValues[it] = email.trim()
                    }
                }
                if (nickname.isNotBlank()) {
                    formFieldVars.firstOrNull {
                        it.equals("name", ignoreCase = true) ||
                            it.equals("nick", ignoreCase = true) ||
                            it.equals("nickname", ignoreCase = true)
                    }?.let {
                        submitValues[it] = nickname.trim()
                    }
                }

                val submitIq = buildDataFormSubmitIq(
                    domain = cleanDomain,
                    formType = challenge.formType,
                    hiddenFields = challenge.hiddenFields,
                    values = submitValues
                )
                try {
                    sendRegistrationIq(tempConnection, submitIq)
                } catch (e: XMPPException.XMPPErrorException) {
                    val errXml = e.stanzaError?.toXML()?.toString() ?: ""
                    val errForm = e.stanzaError?.getExtension<org.jivesoftware.smack.packet.ExtensionElement>("x", "jabber:x:data") as? DataForm
                    val errBob = XmppBobManager.extractBytesFromStanza(errXml)
                    val errDesc = extractInstructionsFromXml(errXml) ?: e.stanzaError?.descriptiveText
                    val errOob = extractWebUrl(errXml, errDesc)
                    if (errForm != null) {
                        val (errChallenge, errPrompt, _) = buildChallengeFromDataForm(
                            dataForm = errForm,
                            responseXml = errXml,
                            instructions = errDesc,
                            oobUrl = errOob,
                            domain = cleanDomain,
                            username = cleanUser,
                            password = password,
                            host = explicitHost,
                            port = port,
                            nickname = nickname.trim(),
                            email = email.trim(),
                            useTls = useTls
                        )
                        if (errPrompt || errBob != null) {
                            throw CaptchaRequiredException(errChallenge)
                        }
                    }
                    throw mapRegistrationError(e, cleanDomain, cleanUser)
                }
            } else {
                // Legacy XEP-0077 attribute-based registration (no DataForm)
                val requestedAttrs = regResponse?.attributes?.keys ?: emptySet()
                val bobBytes = XmppBobManager.extractBytesFromStanza(responseXml)

                if (requestedAttrs.isEmpty() && !oobUrl.isNullOrBlank() && bobBytes == null) {
                    throw WebRegistrationRequiredException(cleanDomain, oobUrl, instructions)
                }

                if (bobBytes != null) {
                    val challenge = CaptchaChallengeData(
                        domain = cleanDomain,
                        username = cleanUser,
                        password = password,
                        host = explicitHost,
                        port = port,
                        email = email.trim(),
                        nickname = nickname.trim(),
                        useTls = useTls,
                        challengeVar = "ocr",
                        captchaImageBytes = bobBytes,
                        instructions = instructions,
                        usesDataForm = false,
                        oobUrl = oobUrl
                    )
                    throw CaptchaRequiredException(challenge)
                }

                val attributes = linkedMapOf<String, String>()
                attributes["username"] = cleanUser
                attributes["password"] = password
                if (email.isNotBlank() && (requestedAttrs.isEmpty() || requestedAttrs.any { it.equals("email", ignoreCase = true) })) {
                    attributes["email"] = email.trim()
                }
                if (nickname.isNotBlank() && requestedAttrs.any { it.equals("name", ignoreCase = true) }) {
                    attributes["name"] = nickname.trim()
                }

                val submitLegacy = Registration(attributes).apply {
                    type = IQ.Type.set
                    to = JidCreate.domainBareFrom(cleanDomain)
                }

                try {
                    sendRegistrationIq(tempConnection, submitLegacy)
                } catch (e: XMPPException.XMPPErrorException) {
                    val errXml = e.stanzaError?.toXML()?.toString() ?: ""
                    val errBobBytes = XmppBobManager.extractBytesFromStanza(errXml)
                    val errForm = e.stanzaError?.getExtension<org.jivesoftware.smack.packet.ExtensionElement>("x", "jabber:x:data") as? DataForm
                    val errDesc = extractInstructionsFromXml(errXml) ?: e.stanzaError?.descriptiveText
                    val errOob = extractWebUrl(errXml, errDesc)

                    if (errForm != null) {
                        val (errChallenge, _, _) = buildChallengeFromDataForm(
                            dataForm = errForm,
                            responseXml = errXml,
                            instructions = errDesc,
                            oobUrl = errOob,
                            domain = cleanDomain,
                            username = cleanUser,
                            password = password,
                            host = explicitHost,
                            port = port,
                            nickname = nickname.trim(),
                            email = email.trim(),
                            useTls = useTls
                        )
                        throw CaptchaRequiredException(errChallenge)
                    }

                    if (errBobBytes != null) {
                        throw CaptchaRequiredException(
                            CaptchaChallengeData(
                                domain = cleanDomain,
                                username = cleanUser,
                                password = password,
                                host = explicitHost,
                                port = port,
                                nickname = nickname.trim(),
                                email = email.trim(),
                                useTls = useTls,
                                challengeVar = "ocr",
                                captchaImageBytes = errBobBytes,
                                instructions = e.stanzaError?.descriptiveText,
                                usesDataForm = false
                            )
                        )
                    }

                    // Fallback: if server rejected missing email attribute when email was listed, prompt user
                    val condition = e.stanzaError?.condition
                    if ((condition == StanzaError.Condition.bad_request || condition == StanzaError.Condition.not_acceptable) &&
                        requestedAttrs.any { it.equals("email", ignoreCase = true) } && email.isBlank()
                    ) {
                        throw CaptchaRequiredException(
                            CaptchaChallengeData(
                                domain = cleanDomain,
                                username = cleanUser,
                                password = password,
                                host = explicitHost,
                                port = port,
                                nickname = nickname.trim(),
                                email = "",
                                useTls = useTls,
                                challengeVar = "",
                                instructions = instructions ?: "Server $cleanDomain mewajibkan alamat email untuk pendaftaran.",
                                formFields = listOf(
                                    RegistrationFormField(
                                        variable = "email",
                                        label = "Email",
                                        type = RegistrationFieldType.TEXT,
                                        isRequired = true
                                    )
                                ),
                                usesDataForm = false,
                                oobUrl = oobUrl
                            )
                        )
                    }

                    throw mapRegistrationError(e, cleanDomain, cleanUser)
                }
            }

            try { tempConnection.disconnect() } catch (_: Exception) {}
            Result.success("$cleanUser@$cleanDomain")
        } catch (e: Exception) {
            if (e is CaptchaRequiredException ||
                e is RegistrationNotAllowedException ||
                e is WebRegistrationRequiredException ||
                e is RegistrationTlsException
            ) {
                Log.d(TAG, "Registration flow status: ${e.javaClass.simpleName} - ${e.message}")
            } else {
                Log.e(TAG, "Registration final failure: ${e.message}", e)
            }
            try { tempConnection?.disconnect() } catch (_: Exception) {}
            if (isTlsCertificateException(e) && e !is RegistrationTlsException) {
                Result.failure(
                    RegistrationTlsException(
                        "Koneksi TLS ke $cleanDomain gagal: sertifikat SSL server tidak dipercaya (${extractDeepestMessage(e)})."
                    )
                )
            } else {
                Result.failure(e)
            }
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
            
            val vName = version.name?.trim().takeIf { !it.isNullOrBlank() } ?: "Unknown"
            val vVersion = version.version?.trim().takeIf { !it.isNullOrBlank() } ?: "Tidak diketahui"
            val vOs = version.os?.trim().takeIf { !it.isNullOrBlank() } ?: "Tidak diketahui"

            val definition = ClientRegistryManager.findDefByName(vName) ?: ClientRegistryManager.resolve(vName, jid.toString(), "")
            val icon = definition?.iconResId
            val platform = definition?.platform ?: com.ndregil.inchat.data.model.ClientPlatform.UNKNOWN

            val caps = com.ndregil.inchat.data.model.ClientCaps(
                clientName = vName,
                version = vVersion,
                os = vOs,
                platform = platform,
                nodeUri = definition?.canonicalNodeUri ?: "",
                iconResId = icon,
                rawNode = vName
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
        captchaAnswer: String,
        extraFieldValues: Map<String, String> = emptyMap()
    ): Result<String> = withContext(Dispatchers.IO) {
        var tempConnection: XMPPTCPConnection? = null
        val cleanDomain = sanitizeDomain(challenge.domain)
        try {
            tempConnection = openRegistrationConnection(
                cleanDomain = cleanDomain,
                explicitHost = challenge.host,
                port = challenge.port,
                useTls = challenge.useTls
            )

            if (challenge.usesDataForm) {
                // XEP-0077: Submit ONLY DataForm (never mix legacy attributes and DataForm in one IQ)
                val formValues = linkedMapOf<String, String>()
                formValues["username"] = challenge.username
                formValues["password"] = challenge.password

                for ((k, v) in extraFieldValues) {
                    if (k.isNotBlank() && v.isNotBlank()) {
                        formValues[k] = v.trim()
                    }
                }

                if (challenge.email.isNotBlank() && !formValues.containsKey("email")) {
                    formValues["email"] = challenge.email.trim()
                }
                if (challenge.nickname.isNotBlank() && !formValues.containsKey("name")) {
                    val nickVar = challenge.formFields.firstOrNull {
                        it.variable.equals("name", true) || it.variable.equals("nick", true) || it.variable.equals("nickname", true)
                    }?.variable
                    if (nickVar != null) {
                        formValues.putIfAbsent(nickVar, challenge.nickname.trim())
                    }
                }

                if (challenge.challengeVar.isNotBlank() && captchaAnswer.isNotBlank()) {
                    formValues[challenge.challengeVar] = captchaAnswer.trim()
                }

                val submitReg = buildDataFormSubmitIq(
                    domain = cleanDomain,
                    formType = challenge.formType,
                    hiddenFields = challenge.hiddenFields,
                    values = formValues
                )

                try {
                    sendRegistrationIq(tempConnection, submitReg)
                } catch (e: XMPPException.XMPPErrorException) {
                    throw mapRegistrationError(e, cleanDomain, challenge.username)
                }
            } else {
                // Pure legacy XEP-0077 attributes (no DataForm)
                val attributes = linkedMapOf<String, String>()
                attributes["username"] = challenge.username
                attributes["password"] = challenge.password
                if (challenge.challengeVar.isNotBlank() && captchaAnswer.isNotBlank()) {
                    attributes[challenge.challengeVar] = captchaAnswer.trim()
                }
                if (challenge.email.isNotBlank()) attributes["email"] = challenge.email.trim()
                if (challenge.nickname.isNotBlank()) attributes["name"] = challenge.nickname.trim()
                for ((k, v) in extraFieldValues) {
                    if (k.isNotBlank() && v.isNotBlank()) {
                        attributes[k] = v.trim()
                    }
                }

                val submitReg = Registration(attributes).apply {
                    type = IQ.Type.set
                    to = JidCreate.domainBareFrom(cleanDomain)
                }
                try {
                    sendRegistrationIq(tempConnection, submitReg)
                } catch (e: XMPPException.XMPPErrorException) {
                    throw mapRegistrationError(e, cleanDomain, challenge.username)
                }
            }

            try { tempConnection.disconnect() } catch (_: Exception) {}
            Result.success("${challenge.username}@$cleanDomain")
        } catch (e: Exception) {
            if (e is RegistrationNotAllowedException ||
                e is WebRegistrationRequiredException ||
                e is RegistrationTlsException
            ) {
                Log.d(TAG, "Registration form submit status: ${e.message}")
            } else {
                Log.e(TAG, "Registration form submission failed: ${e.message}", e)
            }
            try { tempConnection?.disconnect() } catch (_: Exception) {}
            if (isTlsCertificateException(e) && e !is RegistrationTlsException) {
                Result.failure(
                    RegistrationTlsException(
                        "Koneksi TLS ke $cleanDomain gagal: sertifikat SSL server tidak dipercaya (${extractDeepestMessage(e)})."
                    )
                )
            } else {
                Result.failure(e)
            }
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

enum class RegistrationFieldType {
    TEXT,
    PASSWORD,
    BOOLEAN,
    LIST_SINGLE
}

data class RegistrationFormField(
    val variable: String,
    val label: String,
    val type: RegistrationFieldType = RegistrationFieldType.TEXT,
    val isRequired: Boolean = false,
    val defaultValue: String = "",
    val options: List<Pair<String, String>> = emptyList()
)

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
    val captchaImageUrl: String? = null,
    val formType: String = "jabber:iq:register",
    val instructions: String? = null,
    val hiddenFields: Map<String, String> = emptyMap(),
    val formFields: List<RegistrationFormField> = emptyList(),
    val usesDataForm: Boolean = true,
    val oobUrl: String? = null
)

class CaptchaRequiredException(val challenge: CaptchaChallengeData) : Exception("Captcha or registration form required")
class WebRegistrationRequiredException(
    val domain: String,
    val registrationUrl: String,
    val instructions: String? = null
) : Exception("Web registration required at $registrationUrl")
class RegistrationTlsException(override val message: String) : Exception(message)
class RegistrationNotAllowedException(override val message: String) : Exception(message)
