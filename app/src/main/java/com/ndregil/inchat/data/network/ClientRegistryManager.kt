package com.ndregil.inchat.data.network

import android.content.Context
import android.util.Log
import com.ndregil.inchat.data.model.ClientCaps
import com.ndregil.inchat.data.model.ClientDefinition
import com.ndregil.inchat.data.model.ClientPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Manager to load and handle client identification exclusively from clients.txt
 * and persist client capabilities to Room database.
 */
object ClientRegistryManager {
    private const val TAG = "ClientRegistryManager"
    
    // Global cache for consistent icon mapping: jid -> ClientCaps
    private val globalCapsCache = java.util.concurrent.ConcurrentHashMap<String, com.ndregil.inchat.data.model.ClientCaps>()
    // Global cache for consistent icon mapping: verHash -> ClientCaps
    private val verCapsCache = java.util.concurrent.ConcurrentHashMap<String, com.ndregil.inchat.data.model.ClientCaps>()
    private val queriedVersions = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext

        try {
            val inputStream = context.assets.open("clients.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val loadedClients = mutableListOf<ClientDefinition>()

            reader.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine

                val parts = trimmed.split("\t")
                if (parts.size >= 2) {
                    val patternsStr = parts[0]
                    val clientName = parts[1]
                    val patterns = patternsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    
                    if (patterns.isNotEmpty()) {
                        val iconRes = resolveIconRes(clientName, context)
                        val firstPat = patterns.first()
                        val canonicalUri = when {
                            patterns.any { it.startsWith("http://") || it.startsWith("https://") } ->
                                patterns.first { it.startsWith("http://") || it.startsWith("https://") }
                            patterns.any { it.contains(".") } ->
                                "http://" + patterns.first { it.contains(".") }
                            firstPat.contains(".") ->
                                "http://$firstPat"
                            else ->
                                "http://$firstPat"
                        }

                        loadedClients.add(
                            ClientDefinition(
                                patterns = patterns,
                                clientName = clientName,
                                canonicalNodeUri = canonicalUri,
                                platform = determinePlatform(clientName),
                                iconResId = iconRes,
                                defaultVersion = "1.0",
                                defaultOs = determineDefaultOs(clientName)
                            )
                        )
                    }
                }
            }
            if (loadedClients.isNotEmpty()) {
                synchronized(dynamicRegistry) {
                    dynamicRegistry.clear()
                    dynamicRegistry.addAll(loadedClients)
                }
                Log.i(TAG, "Loaded ${loadedClients.size} clients from clients.txt")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load clients.txt: ${e.message}, using static definitions")
        }
    }

    private const val PLACEHOLDER_VER = "kFwX23eY765qL90mN12aBcDeFgH="

    private fun isBareMucRoomJid(jid: String): Boolean {
        val clean = jid.trim().lowercase()
        if (clean.isEmpty() || clean.contains("/")) return false
        return clean.contains("@conference.") || clean.contains("@muc.") ||
            clean.contains("@chat.") || clean.contains("@room.") || clean.contains("@group.")
    }

    fun getCachedCaps(jid: String, ver: String? = null): com.ndregil.inchat.data.model.ClientCaps? {
        val cleanJid = jid.trim().lowercase()
        if (cleanJid.isNotBlank() && !isBareMucRoomJid(cleanJid)) {
            globalCapsCache[cleanJid]?.let { return it }
        }
        val cleanVer = ver?.trim() ?: ""
        if (cleanVer.isNotBlank() && cleanVer != PLACEHOLDER_VER && cleanVer.length >= 8) {
            verCapsCache[cleanVer]?.let { return it }
        }
        return null
    }

    fun putCachedCaps(jid: String, caps: com.ndregil.inchat.data.model.ClientCaps) {
        if (!caps.isIdentified() || caps.rawNode.isBlank()) return
        val cleanJid = jid.trim().lowercase()
        if (cleanJid.isNotBlank() && !isBareMucRoomJid(cleanJid)) {
            globalCapsCache[cleanJid] = caps
        }
        val cleanVer = caps.ver.trim()
        if (cleanVer.isNotBlank() && cleanVer != PLACEHOLDER_VER && cleanVer.length >= 8) {
            verCapsCache[cleanVer] = caps
        }
    }

    fun markAsQueried(key: String): Boolean = queriedVersions.add(key)

    // Default static entries directly sourced from clients.txt
    private val staticRegistry: List<ClientDefinition> = listOf(
        ClientDefinition(
            patterns = listOf("slixmpp.com", "slixmpp"),
            clientName = "Slixmpp",
            canonicalNodeUri = "slixmpp.com",
            platform = ClientPlatform.BOT,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_slixmpp,
            defaultVersion = "1.0",
            defaultOs = "Python"
        ),
        ClientDefinition(
            patterns = listOf("jtalk.ustyugov.net/caps", "jtalk", "jtalk.ustyugov.net"),
            clientName = "jTalk",
            canonicalNodeUri = "http://jtalk.ustyugov.net/caps",
            platform = ClientPlatform.ANDROID,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_jtalk,
            defaultVersion = "1.0",
            defaultOs = "Mobile"
        ),
        ClientDefinition(
            patterns = listOf("dev.narayana.im", "anotherim"),
            clientName = "Another IM",
            canonicalNodeUri = "http://dev.narayana.im",
            platform = ClientPlatform.DESKTOP,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_anotherim,
            defaultVersion = "1.0",
            defaultOs = "Desktop"
        ),
        ClientDefinition(
            patterns = listOf("ndregil.smart-ng", "smartng", "Smart Next-Gen XMPP Bot", "Smart Next-Gen"),
            clientName = "SmartNG",
            canonicalNodeUri = "http://ndregil.smart-ng",
            platform = ClientPlatform.ANDROID,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_smartng,
            defaultVersion = "1.0",
            defaultOs = "Android"
        ),
        ClientDefinition(
            patterns = listOf("http://ndregil.inchat", "ndregil.inchat", "inchat", "inchat-android"),
            clientName = "InChat",
            canonicalNodeUri = "http://ndregil.inchat",
            platform = ClientPlatform.ANDROID,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_inchat,
            defaultVersion = "1.0.0",
            defaultOs = "Android"
        ),
        ClientDefinition(
            patterns = listOf("bombusmod.net.ru", "bombusmod"),
            clientName = "BombusMod",
            canonicalNodeUri = "http://bombusmod.net.ru/",
            platform = ClientPlatform.ANDROID,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_bombusmod,
            defaultVersion = "Mod",
            defaultOs = "Mobile"
        ),
        ClientDefinition(
            patterns = listOf("bombus-im.org", "voffk.org.ru/bombus"),
            clientName = "Bombus",
            canonicalNodeUri = "http://bombus-im.org/",
            platform = ClientPlatform.ANDROID,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_bombus,
            defaultVersion = "Original",
            defaultOs = "Mobile"
        ),
        ClientDefinition(
            patterns = listOf("bombusmod-qd"),
            clientName = "Bombus-QD",
            canonicalNodeUri = "http://bombusmod-qd/",
            platform = ClientPlatform.ANDROID,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_bombus_qd,
            defaultVersion = "QD",
            defaultOs = "Mobile"
        ),
        ClientDefinition(
            patterns = listOf("gajim.org", "gajim", "http://gajim.org"),
            clientName = "Gajim",
            canonicalNodeUri = "http://gajim.org",
            platform = ClientPlatform.DESKTOP,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_gajim,
            defaultVersion = "Desktop",
            defaultOs = "Linux/Windows"
        ),
        ClientDefinition(
            patterns = listOf("google.com", "gmail"),
            clientName = "Google",
            canonicalNodeUri = "http://google.com/talk",
            platform = ClientPlatform.WEB,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_google,
            defaultVersion = "Web",
            defaultOs = "Cloud"
        ),
        ClientDefinition(
            patterns = listOf("http://jabbrik.ru/caps", "http://jabrvista.net.ru", "bot", "jabber-bot", "freq"),
            clientName = "Jabber Bot",
            canonicalNodeUri = "http://jabbrik.ru/caps",
            platform = ClientPlatform.BOT,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_jabber_bot,
            defaultVersion = "Bot",
            defaultOs = "Server"
        ),
        ClientDefinition(
            patterns = listOf("jimm.net.ru"),
            clientName = "Jimm aspro",
            canonicalNodeUri = "http://jimm.net.ru/",
            platform = ClientPlatform.ANDROID,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_jimm,
            defaultVersion = "Aspro",
            defaultOs = "Mobile"
        ),
        ClientDefinition(
            patterns = listOf("kopete"),
            clientName = "Kopete",
            canonicalNodeUri = "http://kopete.kde.org/",
            platform = ClientPlatform.DESKTOP,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_kopete,
            defaultVersion = "Desktop",
            defaultOs = "Linux"
        ),
        ClientDefinition(
            patterns = listOf("miranda-im"),
            clientName = "Miranda",
            canonicalNodeUri = "http://miranda-im.org/caps",
            platform = ClientPlatform.DESKTOP,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_miranda,
            defaultVersion = "Desktop",
            defaultOs = "Windows"
        ),
        ClientDefinition(
            patterns = listOf("pidgin", "gaim."),
            clientName = "Pidgin",
            canonicalNodeUri = "http://pidgin.im/caps",
            platform = ClientPlatform.DESKTOP,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_pidgin,
            defaultVersion = "Desktop",
            defaultOs = "Linux/Windows"
        ),
        ClientDefinition(
            patterns = listOf("psi-im.org"),
            clientName = "Psi",
            canonicalNodeUri = "http://psi-im.org/caps",
            platform = ClientPlatform.DESKTOP,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_psi,
            defaultVersion = "Desktop",
            defaultOs = "Desktop"
        ),
        ClientDefinition(
            patterns = listOf("psi-plus", "psi+", "psi-plus.com", "psi-dev.googlecode.com"),
            clientName = "Psi+",
            canonicalNodeUri = "http://psi-plus.com/caps",
            platform = ClientPlatform.DESKTOP,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_psi_plus,
            defaultVersion = "Desktop",
            defaultOs = "Desktop"
        ),
        ClientDefinition(
            patterns = listOf("pyicqt.googlecode.com"),
            clientName = "PyICQ-t",
            canonicalNodeUri = "http://pyicqt.googlecode.com/",
            platform = ClientPlatform.BOT,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_pyicq,
            defaultVersion = "Transport",
            defaultOs = "Server"
        ),
        ClientDefinition(
            patterns = listOf("qip"),
            clientName = "Qip",
            canonicalNodeUri = "http://qip.ru/caps",
            platform = ClientPlatform.DESKTOP,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_qip,
            defaultVersion = "Desktop",
            defaultOs = "Windows"
        ),
        ClientDefinition(
            patterns = listOf("tkabber.jabber.ru"),
            clientName = "Tkabber",
            canonicalNodeUri = "http://tkabber.jabber.ru/",
            platform = ClientPlatform.DESKTOP,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_tkabber,
            defaultVersion = "Desktop",
            defaultOs = "Tcl/Tk"
        ),
        ClientDefinition(
            patterns = listOf("ajabb", "yandex"),
            clientName = "Ya.Online",
            canonicalNodeUri = "http://online.yandex.ru/",
            platform = ClientPlatform.WEB,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_ya_online,
            defaultVersion = "Web",
            defaultOs = "Cloud"
        ),
        ClientDefinition(
            patterns = listOf("miranda-ng.org"),
            clientName = "Miranda NG",
            canonicalNodeUri = "http://miranda-ng.org/caps",
            platform = ClientPlatform.DESKTOP,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_miranda_ng,
            defaultVersion = "Desktop",
            defaultOs = "Windows"
        ),
        ClientDefinition(
            patterns = listOf("mcabber"),
            clientName = "Mcabber",
            canonicalNodeUri = "http://mcabber.com/caps",
            platform = ClientPlatform.DESKTOP,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_mcabber,
            defaultVersion = "Console",
            defaultOs = "Linux"
        ),
        ClientDefinition(
            patterns = listOf("twitter"),
            clientName = "Twitter bot",
            canonicalNodeUri = "http://twitter.com/bot",
            platform = ClientPlatform.BOT,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_twitter,
            defaultVersion = "Bot",
            defaultOs = "Server"
        ),
        ClientDefinition(
            patterns = listOf("http://isida-bot.com"),
            clientName = "Isida bot",
            canonicalNodeUri = "http://isida-bot.com/",
            platform = ClientPlatform.BOT,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_isida_bot,
            defaultVersion = "Bot",
            defaultOs = "Server"
        ),
        ClientDefinition(
            patterns = listOf("jabrss"),
            clientName = "RSS Bot",
            canonicalNodeUri = "http://jabrss.ru/caps",
            platform = ClientPlatform.BOT,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_rss_bot,
            defaultVersion = "Bot",
            defaultOs = "Server"
        ),
        ClientDefinition(
            patterns = listOf("http://conversations.im", "conversations"),
            clientName = "Conversations",
            canonicalNodeUri = "http://conversations.im",
            platform = ClientPlatform.ANDROID,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_conversations,
            defaultVersion = "Android",
            defaultOs = "Android"
        ),
        ClientDefinition(
            patterns = listOf("blabber.im"),
            clientName = "Blabber",
            canonicalNodeUri = "http://blabber.im",
            platform = ClientPlatform.ANDROID,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_blabber,
            defaultVersion = "Android",
            defaultOs = "Android"
        ),
        ClientDefinition(
            patterns = listOf("monocles", "http://monocles.chat"),
            clientName = "Monocles",
            canonicalNodeUri = "http://monocles.chat",
            platform = ClientPlatform.ANDROID,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_monocles,
            defaultVersion = "Android",
            defaultOs = "Android"
        ),
        ClientDefinition(
            patterns = listOf("cheogram", "https://cheogram.com/caps"),
            clientName = "Cheogram",
            canonicalNodeUri = "https://cheogram.com/caps",
            platform = ClientPlatform.ANDROID,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_cheogram,
            defaultVersion = "Android",
            defaultOs = "Android"
        ),
        ClientDefinition(
            patterns = listOf("movim.eu", "http://movim.eu"),
            clientName = "Movim",
            canonicalNodeUri = "http://movim.eu",
            platform = ClientPlatform.WEB,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_movim,
            defaultVersion = "Web",
            defaultOs = "Cloud"
        ),
        ClientDefinition(
            patterns = listOf("http://monal.im", "monal"),
            clientName = "Monal IM",
            canonicalNodeUri = "http://monal.im",
            platform = ClientPlatform.IOS,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_monal,
            defaultVersion = "iOS",
            defaultOs = "iOS"
        ),
        ClientDefinition(
            patterns = listOf("poezio", "http://poez.io", "poez.io"),
            clientName = "Poezio",
            canonicalNodeUri = "http://poez.io",
            platform = ClientPlatform.DESKTOP,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_poezio,
            defaultVersion = "Console",
            defaultOs = "Linux"
        ),
        ClientDefinition(
            patterns = listOf("c0nnect", "c0nnect.org"),
            clientName = "c0nnectPRO",
            canonicalNodeUri = "http://c0nnect.org/",
            platform = ClientPlatform.DESKTOP,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_c0nnect,
            defaultVersion = "Desktop",
            defaultOs = "Desktop"
        ),
        ClientDefinition(
            patterns = listOf("yaxim", "yaxim.org", "http://yaxim.org"),
            clientName = "Yaxim",
            canonicalNodeUri = "http://yaxim.org",
            platform = ClientPlatform.ANDROID,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_yaxim,
            defaultVersion = "Android",
            defaultOs = "Android"
        ),
        ClientDefinition(
            patterns = listOf("archon", "archon-bifrost", "archon.im"),
            clientName = "Archon",
            canonicalNodeUri = "http://archon.im/caps",
            platform = ClientPlatform.DESKTOP,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_archon,
            defaultVersion = "Desktop",
            defaultOs = "Desktop"
        ),
        ClientDefinition(
            patterns = listOf("dino.im", "http://dino.im"),
            clientName = "Dino",
            canonicalNodeUri = "http://dino.im",
            platform = ClientPlatform.DESKTOP,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_dino,
            defaultVersion = "Desktop",
            defaultOs = "Linux"
        ),
        ClientDefinition(
            patterns = listOf("profanity.im", "http://profanity.im"),
            clientName = "Profanity",
            canonicalNodeUri = "http://profanity.im",
            platform = ClientPlatform.DESKTOP,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_profanity,
            defaultVersion = "Console",
            defaultOs = "Linux"
        ),
        ClientDefinition(
            patterns = listOf("beagleim.org", "http://beagleim.org"),
            clientName = "BeagleIM",
            canonicalNodeUri = "http://beagleim.org",
            platform = ClientPlatform.DESKTOP,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_beagleim,
            defaultVersion = "Desktop",
            defaultOs = "macOS"
        )
    )

    private val dynamicRegistry = mutableListOf<ClientDefinition>().apply {
        addAll(staticRegistry)
    }

    private fun resolveIconRes(clientName: String, context: Context?): Int {
        val clean = clientName.lowercase().trim()
        return when {
            isInChatExact(clean) -> com.ndregil.inchat.R.drawable.ic_client_inchat
            clean.contains("slixmpp") -> com.ndregil.inchat.R.drawable.ic_client_slixmpp
            clean.contains("jtalk") -> com.ndregil.inchat.R.drawable.ic_client_jtalk
            clean.contains("bombusmod-qd") || clean.contains("bombus-qd") -> com.ndregil.inchat.R.drawable.ic_client_bombus_qd
            clean.contains("bombusmod") -> com.ndregil.inchat.R.drawable.ic_client_bombusmod
            clean.contains("bombus") -> com.ndregil.inchat.R.drawable.ic_client_bombus
            clean.contains("miranda ng") || clean == "miranda-ng" -> com.ndregil.inchat.R.drawable.ic_client_miranda_ng
            clean.contains("miranda") -> com.ndregil.inchat.R.drawable.ic_client_miranda
            clean.contains("conversations") -> com.ndregil.inchat.R.drawable.ic_client_conversations
            clean.contains("psi+") || clean.contains("psi-plus") || clean.contains("psi plus") -> com.ndregil.inchat.R.drawable.ic_client_psi_plus
            clean.contains("psi") -> com.ndregil.inchat.R.drawable.ic_client_psi
            clean.contains("gajim") -> com.ndregil.inchat.R.drawable.ic_client_gajim
            clean.contains("pidgin") || clean.contains("gaim") -> com.ndregil.inchat.R.drawable.ic_client_pidgin
            clean.contains("qip") -> com.ndregil.inchat.R.drawable.ic_client_qip
            clean.contains("tkabber") -> com.ndregil.inchat.R.drawable.ic_client_tkabber
            clean.contains("kopete") -> com.ndregil.inchat.R.drawable.ic_client_kopete
            clean.contains("mcabber") -> com.ndregil.inchat.R.drawable.ic_client_mcabber
            clean.contains("yaxim") -> com.ndregil.inchat.R.drawable.ic_client_yaxim
            clean.contains("monal") -> com.ndregil.inchat.R.drawable.ic_client_monal
            clean.contains("monocles") -> com.ndregil.inchat.R.drawable.ic_client_monocles
            clean.contains("blabber") -> com.ndregil.inchat.R.drawable.ic_client_blabber
            clean.contains("cheogram") -> com.ndregil.inchat.R.drawable.ic_client_cheogram
            clean.contains("movim") -> com.ndregil.inchat.R.drawable.ic_client_movim
            clean.contains("poezio") -> com.ndregil.inchat.R.drawable.ic_client_poezio
            clean.contains("c0nnect") -> com.ndregil.inchat.R.drawable.ic_client_c0nnect
            clean.contains("another im") || clean == "anotherim" -> com.ndregil.inchat.R.drawable.ic_client_anotherim
            clean.contains("smartng") || clean == "smart-ng" -> com.ndregil.inchat.R.drawable.ic_client_smartng
            clean.contains("archon") -> com.ndregil.inchat.R.drawable.ic_client_archon
            clean.contains("jimm") -> com.ndregil.inchat.R.drawable.ic_client_jimm
            clean.contains("pyicq") -> com.ndregil.inchat.R.drawable.ic_client_pyicq
            clean.contains("ya.online") || clean.contains("yandex") || clean.contains("ajabb") -> com.ndregil.inchat.R.drawable.ic_client_ya_online
            clean.contains("twitter") -> com.ndregil.inchat.R.drawable.ic_client_twitter
            clean.contains("isida") -> com.ndregil.inchat.R.drawable.ic_client_isida_bot
            clean.contains("rss") || clean.contains("jabrss") -> com.ndregil.inchat.R.drawable.ic_client_rss_bot
            clean.contains("google") || clean.contains("gmail") -> com.ndregil.inchat.R.drawable.ic_client_google
            clean.contains("dino") -> com.ndregil.inchat.R.drawable.ic_client_dino
            clean.contains("profanity") -> com.ndregil.inchat.R.drawable.ic_client_profanity
            clean.contains("beagle") -> com.ndregil.inchat.R.drawable.ic_client_beagleim
            clean.contains("bot") || clean.contains("jabber bot") -> com.ndregil.inchat.R.drawable.ic_client_jabber_bot
            else -> {
                if (context != null) {
                    val iconName = "ic_client_" + clean.replace(" ", "_").replace("+", "_plus").replace("-", "_").replace(".", "_")
                    val resId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
                    if (resId != 0) resId else com.ndregil.inchat.R.drawable.ic_client_jabber_bot
                } else {
                    com.ndregil.inchat.R.drawable.ic_client_jabber_bot
                }
            }
        }
    }

    private fun determineDefaultOs(clientName: String): String {
        val clean = clientName.lowercase()
        return when {
            clean.contains("android") || clean == "conversations" || clean == "inchat" || clean == "blabber" || clean == "monocles" || clean == "yaxim" || clean == "cheogram" || clean == "bombusmod" || clean == "bombus" || clean == "smartng" -> "Android"
            clean.contains("ios") || clean.contains("monal") -> "iOS"
            clean.contains("bot") || clean.contains("rss") || clean.contains("pyicq") || clean.contains("slixmpp") -> "Server"
            clean.contains("movim") || clean.contains("web") || clean.contains("google") -> "Web"
            clean.contains("windows") || clean.contains("miranda") || clean.contains("qip") -> "Windows"
            clean.contains("mcabber") || clean.contains("poezio") || clean.contains("profanity") -> "Linux CLI"
            clean.contains("dino") || clean.contains("gajim") -> "Linux/Desktop"
            clean.contains("beagle") -> "macOS"
            else -> "Desktop"
        }
    }

    private fun determinePlatform(name: String): ClientPlatform {
        val n = name.lowercase()
        return when {
            n.contains("android") || n == "conversations" || n == "inchat" || n == "blabber" || n == "monocles" || n == "yaxim" || n == "cheogram" || n.contains("bombus") || n.contains("jimm") || n == "smartng" -> ClientPlatform.ANDROID
            n.contains("ios") || n.contains("monal") -> ClientPlatform.IOS
            n.contains("bot") || n.contains("pyicq") || n.contains("rss") || n.contains("isida") || n.contains("twitter") || n.contains("slixmpp") -> ClientPlatform.BOT
            n.contains("web") || n == "movim" || n == "google" || n.contains("ya.online") -> ClientPlatform.WEB
            else -> ClientPlatform.DESKTOP
        }
    }

    /**
     * Robustly extracts node and ver attributes from any XEP-0115 presence packet.
     * Collects all CapsExtension / <c> elements and prefers known client nodes (e.g., http://ndregil.inchat).
     */
    fun extractCapsFromPresence(presence: org.jivesoftware.smack.packet.Presence?): Pair<String, String>? {
        if (presence == null) return null

        val candidates = mutableListOf<Pair<String, String>>()

        try {
            // 1. Collect across all extensions for 'c' element with caps namespace
            presence.extensions.forEach { ext ->
                if ((ext.elementName == "c" && ext.namespace == "http://jabber.org/protocol/caps") ||
                    ext.namespace == "http://jabber.org/protocol/caps") {

                    if (ext is org.jivesoftware.smackx.caps.packet.CapsExtension) {
                        if (ext.node.isNotBlank()) {
                            candidates.add(Pair(ext.node, ext.ver ?: ""))
                        }
                    } else if (ext is org.jivesoftware.smack.packet.StandardExtensionElement) {
                        val nodeAttr = ext.getAttributeValue("node")
                        val verAttr = ext.getAttributeValue("ver")
                        if (!nodeAttr.isNullOrBlank()) {
                            candidates.add(Pair(nodeAttr, verAttr ?: ""))
                        }
                    } else {
                        val xml = ext.toXML(null as org.jivesoftware.smack.packet.XmlEnvironment?).toString()
                        val nodeMatch = Regex("""\bnode=["']([^"']+)["']""").find(xml)?.groupValues?.get(1)
                        val verMatch = Regex("""\bver=["']([^"']+)["']""").find(xml)?.groupValues?.get(1)
                        if (!nodeMatch.isNullOrBlank()) {
                            candidates.add(Pair(nodeMatch, verMatch ?: ""))
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            // 2. Try standard Smack CapsExtension helper if none found yet
            if (candidates.isEmpty()) {
                val capsExt = org.jivesoftware.smackx.caps.packet.CapsExtension.from(presence)
                    ?: (presence.getExtension("c", "http://jabber.org/protocol/caps") as? org.jivesoftware.smackx.caps.packet.CapsExtension)
                if (capsExt != null && capsExt.node.isNotBlank()) {
                    candidates.add(Pair(capsExt.node, capsExt.ver ?: ""))
                }
            }
        } catch (_: Exception) {}

        // 3. Stanza XML fallback specifically within <c ... http://jabber.org/protocol/caps ...>
        if (candidates.isEmpty()) {
            try {
                val xml = presence.toXML(null as org.jivesoftware.smack.packet.XmlEnvironment?).toString()
                val cTagRegex = Regex("""<c\b[^>]*xmlns=["']http://jabber\.org/protocol/caps["'][^>]*>""", RegexOption.IGNORE_CASE)
                cTagRegex.findAll(xml).forEach { match ->
                    val tag = match.value
                    val nodeMatch = Regex("""\bnode=["']([^"']+)["']""").find(tag)?.groupValues?.get(1)
                    val verMatch = Regex("""\bver=["']([^"']+)["']""").find(tag)?.groupValues?.get(1)
                    if (!nodeMatch.isNullOrBlank()) {
                        candidates.add(Pair(nodeMatch, verMatch ?: ""))
                    }
                }
            } catch (_: Exception) {}
        }

        if (candidates.isEmpty()) return null
        // Prefer an InChat or known registry node if multiple <c> extensions were attached
        return candidates.firstOrNull { isInChatNode(it.first) }
            ?: candidates.firstOrNull { resolveByNode(it.first) != null }
            ?: candidates.first()
    }

    /**
     * Strict check for InChat name/resource/bare node:
     * http://ndregil.inchat, https://ndregil.inchat, ndregil.inchat, inchat, inchat-android
     * Jika bukan persis di atas tadi berarti BUKAN InChat.
     */
    fun isInChatExact(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return false
        val clean = trimmed.lowercase().removeSuffix("/")
        return clean == "http://ndregil.inchat" ||
               clean == "https://ndregil.inchat" ||
               clean == "ndregil.inchat" ||
               clean == "inchat" ||
               clean == "inchat-android"
    }

    /**
     * Strict check for InChat XEP-0115 node URI (supports #ver fragment and /caps path):
     * e.g. http://ndregil.inchat, https://ndregil.inchat, http://ndregil.inchat/caps,
     * http://ndregil.inchat#ver, ndregil.inchat, ndregil.inchat/caps, inchat.
     * Rejects unrelated domains like http://fake.inchat.org or http://ndregil.inchat.bot.com.
     */
    fun isInChatNode(node: String): Boolean {
        val trimmed = node.trim()
        if (trimmed.isEmpty()) return false
        val base = trimmed.substringBefore("#").trim().lowercase().removeSuffix("/")
        return base == "http://ndregil.inchat" ||
               base == "https://ndregil.inchat" ||
               base == "http://ndregil.inchat/caps" ||
               base == "https://ndregil.inchat/caps" ||
               base == "ndregil.inchat" ||
               base == "ndregil.inchat/caps" ||
               base == "inchat" ||
               base == "inchat-android"
    }

    fun isMucJid(jid: String): Boolean {
        val bare = jid.trim().substringBefore("/").lowercase()
        return bare.contains("@conference.") || bare.contains("@muc.") ||
            bare.contains("@chat.") || bare.contains("@room.") || bare.contains("@group.")
    }

    fun isDelimitedOrCamelBot(value: String): Boolean {
        val clean = value.trim()
        if (clean.isEmpty()) return false
        val lower = clean.lowercase()
        if (lower == "bot" ||
            lower.startsWith("bot-") || lower.startsWith("bot_") || lower.startsWith("bot.") || lower.startsWith("bot ") ||
            lower.endsWith("-bot") || lower.endsWith("_bot") || lower.endsWith(".bot") || lower.endsWith(" bot") ||
            lower.contains("-bot-") || lower.contains("_bot_") || lower.contains(" bot ") || lower.contains(".bot.")
        ) {
            return true
        }
        // CamelCase suffix like BudiBot, SmartBot (does not falsely match "robot" or "abbott")
        return Regex("""[a-z0-9]Bot(?:$|[^a-zA-Z0-9])""").containsMatchIn(clean)
    }

    fun findDefByName(targetName: String): ClientDefinition? {
        val cleanTarget = targetName.trim().lowercase()
        return synchronized(dynamicRegistry) {
            dynamicRegistry.firstOrNull { it.clientName.lowercase() == cleanTarget }
        } ?: staticRegistry.firstOrNull { it.clientName.lowercase() == cleanTarget }
    }

    /**
     * Flexible matching of a node URL against clients.txt patterns.
     * InChat is strictly matched via isInChatNode BEFORE the dot/protocol check.
     */
    fun resolveByNode(node: String): ClientDefinition? {
        val rawClean = node.trim()
        if (rawClean.isBlank()) return null

        // Check strict InChat node forms first (including "inchat", "http://ndregil.inchat/caps", "...#ver")
        if (isInChatNode(rawClean)) {
            return findDefByName("InChat") ?: staticRegistry.find { it.clientName.equals("InChat", ignoreCase = true) }
        }

        // Ensure remaining node strings look like a valid node URL or domain (has dots or protocol)
        val isLikelyNode = rawClean.contains(".") || rawClean.contains("://") || rawClean.startsWith("http")
        if (!isLikelyNode) return null

        // Remove protocol and fragment for keyword matching
        val cleanNode = rawClean.substringBefore("#").trim().lowercase()
            .removePrefix("http://").removePrefix("https://").removeSuffix("/")

        val registry = synchronized(dynamicRegistry) { dynamicRegistry.toList() }

        // Pass 1: Match specific client patterns first (excluding InChat and generic "bot" keyword)
        registry.firstOrNull { def ->
            if (def.clientName.equals("InChat", ignoreCase = true)) return@firstOrNull false
            def.patterns.any { pattern ->
                val keyword = pattern.trim().lowercase().removePrefix("http://").removePrefix("https://").removeSuffix("/")
                if (keyword.isBlank() || keyword == "bot") return@any false
                cleanNode.contains(keyword)
            }
        }?.let { return it }

        // Pass 2: Fallback for generic bot node keywords (e.g., xmpp.bot.com, bot.xmpp.today/caps)
        return registry.firstOrNull { def ->
            if (def.clientName.equals("InChat", ignoreCase = true)) return@firstOrNull false
            def.patterns.any { pattern ->
                val keyword = pattern.trim().lowercase().removePrefix("http://").removePrefix("https://").removeSuffix("/")
                keyword == "bot" && cleanNode.contains(keyword)
            }
        }
    }

    /**
     * Flexible matching of client name/resource against registry patterns.
     * InChat is strictly matched via isInChatExact and never guessed via substring.
     */
    fun resolveByName(name: String): ClientDefinition? {
        val cleanName = name.trim().lowercase()
        if (cleanName.isBlank() || cleanName == "unknown" || cleanName.contains("@")) return null

        // Aturan KETAT untuk InChat:
        if (isInChatExact(cleanName)) {
            return findDefByName("InChat") ?: staticRegistry.find { it.clientName.equals("InChat", ignoreCase = true) }
        }

        val registry = synchronized(dynamicRegistry) { dynamicRegistry.toList() }

        return registry.firstOrNull { def ->
            if (def.clientName.equals("InChat", ignoreCase = true)) {
                return@firstOrNull false
            }

            // 1. Direct name match (STRICT)
            if (def.clientName.lowercase() == cleanName) return@firstOrNull true

            // 2. Pattern match (STRICT for names)
            def.patterns.any { p ->
                val keyword = p.trim().lowercase().removePrefix("http://").removePrefix("https://").removeSuffix("/")
                if (keyword.isBlank()) return@any false

                // Only allow exact match for name-based resolution to avoid "inchatuser" matching "inchat"
                cleanName == keyword
            }
        }
    }

    /**
     * Resolves client definition strictly from clients.txt definitions and node patterns.
     */
    fun resolve(name: String, jid: String, node: String): ClientDefinition? {
        val cleanNode = node.trim()
        val cleanName = name.trim()
        val resource = if (jid.contains("/")) jid.substringAfterLast("/").trim() else ""
        val mucJid = isMucJid(jid)

        // 1. Resolusi berdasarkan Node XEP-0115 terlebih dahulu (paling otoritatif):
        // Termasuk http://ndregil.inchat, .../caps, ...#ver, ndregil.inchat, inchat, maupun bot node.
        // Tidak boleh diblokir oleh nick/room JID yang mengandung "bot" (seperti "robot", "abbott", atau room "*bot*").
        if (cleanNode.isNotBlank()) {
            resolveByNode(cleanNode)?.let { return it }
        }

        // 2. Resolusi berdasarkan Nama Software / Klien (jika spesifik dan valid):
        if (cleanName.isNotBlank() && !cleanName.contains(".") && !cleanName.contains("/")) {
            resolveByName(cleanName)?.let { return it }
        }

        // 3. Resolusi berdasarkan Resource JID HANYA untuk chat 1:1 (bukan MUC room, karena di MUC resource = nickname):
        if (!mucJid && resource.isNotBlank()) {
            resolveByName(resource)?.let { return it }
            // Support resource variants like "InChat.abc123" or "InChat-Android" in 1:1 chat
            val resBase = resource.substringBefore(".").trim()
            if (isInChatExact(resBase)) {
                return findDefByName("InChat") ?: staticRegistry.find { it.clientName.equals("InChat", ignoreCase = true) }
            }
        }

        // 4. Fallback Bot (token terpisah / CamelCase, TIDAK menyamakan "robot", "abbott", atau nama room MUC):
        val localPart = jid.substringBefore("@").trim()
        if (isDelimitedOrCamelBot(cleanName) ||
            isDelimitedOrCamelBot(resource) ||
            (!mucJid && isDelimitedOrCamelBot(localPart))
        ) {
            findDefByName("Jabber Bot")?.let { return it }
        }

        return null
    }

    /**
     * Attempts to resolve client by examining the resource part of a JID.
     */
    fun resolveByJid(jid: String): ClientDefinition? {
        if (jid.isBlank()) return null
        val resource = if (jid.contains("/")) jid.substringAfterLast("/") else ""
        if (resource.isNotBlank()) {
            return resolveByName(resource)
        }
        return null
    }

    fun getRegistry(): List<ClientDefinition> = synchronized(dynamicRegistry) { dynamicRegistry.toList() }
}


