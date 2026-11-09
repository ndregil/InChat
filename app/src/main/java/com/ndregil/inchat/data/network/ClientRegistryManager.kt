package com.ndregil.inchat.data.network

import android.content.Context
import android.util.Log
import com.ndregil.inchat.data.model.ClientCaps
import com.ndregil.inchat.data.model.ClientDefinition
import com.ndregil.inchat.data.model.ClientPlatform
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Manager to load and handle client identification exclusively from clients.txt.
 * Adheres strictly to clients.txt patterns and node URIs without guessing client from server domains.
 */
object ClientRegistryManager {
    private const val TAG = "ClientRegistryManager"
    
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
            canonicalNodeUri = "jtalk.ustyugov.net/caps",
            platform = ClientPlatform.ANDROID,
            iconResId = com.ndregil.inchat.R.drawable.ic_client_jtalk,
            defaultVersion = "1.0",
            defaultOs = "Mobile"
        ),
        ClientDefinition(
            patterns = listOf("http://ndregil.inchat", "ndregil.inchat", "inchat"),
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

    fun init(context: Context) {
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

    private fun resolveIconRes(clientName: String, context: Context?): Int {
        val clean = clientName.lowercase().trim()
        return when {
            clean.contains("inchat") -> com.ndregil.inchat.R.drawable.ic_client_inchat
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
            clean.contains("android") || clean == "conversations" || clean == "inchat" || clean == "blabber" || clean == "monocles" || clean == "yaxim" || clean == "cheogram" || clean == "bombusmod" || clean == "bombus" -> "Android"
            clean.contains("ios") || clean.contains("monal") -> "iOS"
            clean.contains("bot") || clean.contains("rss") || clean.contains("pyicq") -> "Server"
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
            n.contains("android") || n == "conversations" || n == "inchat" || n == "blabber" || n == "monocles" || n == "yaxim" || n == "cheogram" || n.contains("bombus") || n.contains("jimm") -> ClientPlatform.ANDROID
            n.contains("ios") || n.contains("monal") -> ClientPlatform.IOS
            n.contains("bot") || n.contains("pyicq") || n.contains("rss") || n.contains("isida") || n.contains("twitter") -> ClientPlatform.BOT
            n.contains("web") || n == "movim" || n == "google" || n.contains("ya.online") -> ClientPlatform.WEB
            else -> ClientPlatform.DESKTOP
        }
    }

    /**
     * Robustly extracts node and ver attributes from any XEP-0115 presence packet.
     * Handles CapsExtension, DefaultExtensionElement, StandardExtensionElement, and XML fallbacks.
     */
    fun extractCapsFromPresence(presence: org.jivesoftware.smack.packet.Presence?): Pair<String, String>? {
        if (presence == null) return null

        try {
            // 1. Try standard Smack CapsExtension
            val capsExt = org.jivesoftware.smackx.caps.packet.CapsExtension.from(presence)
                ?: (presence.getExtension("c", "http://jabber.org/protocol/caps") as? org.jivesoftware.smackx.caps.packet.CapsExtension)
            if (capsExt != null && capsExt.node.isNotBlank()) {
                return Pair(capsExt.node, capsExt.ver ?: "")
            }
        } catch (_: Exception) {}

        try {
            // 2. Try raw extension extraction
            val ext = presence.getExtension("c", "http://jabber.org/protocol/caps")
                ?: presence.getExtension("http://jabber.org/protocol/caps")
                ?: presence.extensions.firstOrNull { it.elementName == "c" && it.namespace == "http://jabber.org/protocol/caps" }

            if (ext != null) {
                if (ext is org.jivesoftware.smackx.caps.packet.CapsExtension) {
                    return Pair(ext.node, ext.ver ?: "")
                }
                if (ext is org.jivesoftware.smack.packet.StandardExtensionElement) {
                    val nodeAttr = ext.getAttributeValue("node")
                    val verAttr = ext.getAttributeValue("ver")
                    if (!nodeAttr.isNullOrBlank()) {
                        return Pair(nodeAttr, verAttr ?: "")
                    }
                }
                // 3. XML regex fallback for malformed or generic extensions
                val xml = ext.toXML("").toString()
                val nodeMatch = Regex("""node=["']([^"']+)["']""").find(xml)?.groupValues?.get(1)
                val verMatch = Regex("""ver=["']([^"']+)["']""").find(xml)?.groupValues?.get(1)
                if (!nodeMatch.isNullOrBlank()) {
                    return Pair(nodeMatch, verMatch ?: "")
                }
            }
        } catch (_: Exception) {}

        // 4. Resource-based fallback for known clients that put node in resource
        val fromStr = presence.from?.toString() ?: ""
        if (fromStr.contains("/")) {
            val resource = fromStr.substringAfterLast("/")
            if (resource.contains("InChat", ignoreCase = true)) {
                return Pair("http://ndregil.inchat", "")
            }
            if (resource.contains("Bombus", ignoreCase = true)) {
                return Pair("http://bombus-im.org", "")
            }
        }

        return null
    }

    fun findDefByName(targetName: String): ClientDefinition? {
        val cleanTarget = targetName.trim().lowercase()
        return synchronized(dynamicRegistry) {
            dynamicRegistry.firstOrNull { it.clientName.lowercase() == cleanTarget }
        } ?: staticRegistry.firstOrNull { it.clientName.lowercase() == cleanTarget }
    }

    /**
     * Sensitively matches a node URL against clients.txt patterns and client capabilities.
     * Handles exact URLs, subdomains, token boundaries, and keyword identifiers (e.g. "http://xmpp.bot.com" -> Jabber Bot).
     */
    fun resolveByNode(node: String): ClientDefinition? {
        val rawClean = node.trim().lowercase()
        if (rawClean.isBlank()) return null
        val cleanNode = rawClean.removePrefix("http://").removePrefix("https://").removeSuffix("/")
        if (cleanNode.isBlank()) return null

        val host = cleanNode.substringBefore("/").substringBefore("#").substringBefore("?").trim()
        val path = cleanNode.substringAfter("/", "").substringBefore("#").substringBefore("?").trim()

        val registry = synchronized(dynamicRegistry) { dynamicRegistry.toList() }

        // 1. Direct or prefix match against registered patterns
        val directMatch = registry.firstOrNull { def -> 
            def.patterns.any { pattern ->
                val cleanPattern = pattern.trim().lowercase().removePrefix("http://").removePrefix("https://").removeSuffix("/")
                if (cleanPattern.isBlank()) return@any false

                cleanNode == cleanPattern || 
                cleanNode.startsWith("$cleanPattern/") || 
                cleanNode.startsWith("$cleanPattern#") || 
                cleanNode.startsWith("$cleanPattern?") ||
                cleanNode.startsWith("$cleanPattern.") ||
                cleanNode.startsWith(cleanPattern) ||
                cleanPattern.startsWith(cleanNode) ||
                cleanNode.substringBefore("/") == cleanPattern.substringBefore("/") ||
                cleanNode.substringBefore("#") == cleanPattern.substringBefore("#") ||
                (cleanNode.contains(".") && cleanPattern.contains(".") && cleanNode.substringBefore(".") == cleanPattern.substringBefore("."))
            } 
        }
        if (directMatch != null) return directMatch

        // 2. Domain / Subdomain matching (e.g. "xmpp.conversations.im" or "conversations.im")
        if (host.isNotBlank() && host.contains(".")) {
            val domainMatch = registry.firstOrNull { def ->
                def.patterns.any { pattern ->
                    val cleanPattern = pattern.trim().lowercase().removePrefix("http://").removePrefix("https://").removeSuffix("/")
                    val patternHost = cleanPattern.substringBefore("/").substringBefore("#").substringBefore("?").trim()
                    patternHost.contains(".") && (host == patternHost || host.endsWith(".$patternHost") || patternHost.endsWith(".$host"))
                }
            }
            if (domainMatch != null) return domainMatch
        }

        // 3. Priority specialized clients (to avoid shorter/generic tokens shadowing specific client variants)
        // Bombus variants
        if (cleanNode.contains("bombusmod-qd") || cleanNode.contains("bombus-qd")) {
            findDefByName("Bombus-QD")?.let { return it }
        }
        if (cleanNode.contains("bombusmod")) {
            findDefByName("BombusMod")?.let { return it }
        }
        if (cleanNode.contains("bombus")) {
            findDefByName("Bombus")?.let { return it }
        }

        // Psi variants
        if (cleanNode.contains("psi-plus") || cleanNode.contains("psi+") || cleanNode.contains("psi_plus")) {
            findDefByName("Psi+")?.let { return it }
        }
        if (cleanNode.contains("psi-im") || cleanNode.contains("psi")) {
            findDefByName("Psi")?.let { return it }
        }

        // Miranda variants
        if (cleanNode.contains("miranda-ng") || cleanNode.contains("miranda_ng")) {
            findDefByName("Miranda NG")?.let { return it }
        }
        if (cleanNode.contains("miranda")) {
            findDefByName("Miranda")?.let { return it }
        }

        // Specialized Bots / Transports
        if (cleanNode.contains("isida-bot") || cleanNode.contains("isida")) {
            findDefByName("Isida bot")?.let { return it }
        }
        if (cleanNode.contains("twitter")) {
            findDefByName("Twitter bot")?.let { return it }
        }
        if (cleanNode.contains("jabrss") || cleanNode.contains("rss")) {
            findDefByName("RSS Bot")?.let { return it }
        }
        if (cleanNode.contains("pyicq")) {
            findDefByName("PyICQ-t")?.let { return it }
        }

        // 4. Token & Segment analysis (sensitive matching for host and path components)
        val tokens = cleanNode.split('.', '/', '#', '?', '&', '-', '_', '+', ':', '@')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        // Jabber Bot sensitivity:
        // E.g. "http://xmpp.bot.com", "http://bot.xmpp.today/caps", "http://mybot.com", "http://service.net/bot"
        if (tokens.contains("bot") || host.contains("bot") || path.contains("bot") || cleanNode.contains("bot") || cleanNode.contains("freq")) {
            findDefByName("Jabber Bot")?.let { return it }
        }

        // Sensitive check for other known clients
        if (tokens.contains("conversations") || cleanNode.contains("conversations")) findDefByName("Conversations")?.let { return it }
        if (tokens.contains("gajim") || cleanNode.contains("gajim")) findDefByName("Gajim")?.let { return it }
        if (tokens.contains("slixmpp") || cleanNode.contains("slixmpp")) findDefByName("Slixmpp")?.let { return it }
        if (tokens.contains("yaxim") || cleanNode.contains("yaxim")) findDefByName("Yaxim")?.let { return it }
        if (tokens.contains("monal") || cleanNode.contains("monal")) findDefByName("Monal IM")?.let { return it }
        if (tokens.contains("monocles") || cleanNode.contains("monocles")) findDefByName("Monocles")?.let { return it }
        if (tokens.contains("blabber") || cleanNode.contains("blabber")) findDefByName("Blabber")?.let { return it }
        if (tokens.contains("cheogram") || cleanNode.contains("cheogram")) findDefByName("Cheogram")?.let { return it }
        if (tokens.contains("dino") || cleanNode.contains("dino")) findDefByName("Dino")?.let { return it }
        if (tokens.contains("profanity") || cleanNode.contains("profanity")) findDefByName("Profanity")?.let { return it }
        if (tokens.contains("mcabber") || cleanNode.contains("mcabber")) findDefByName("Mcabber")?.let { return it }
        if (tokens.contains("pidgin") || tokens.contains("gaim") || cleanNode.contains("pidgin") || cleanNode.contains("gaim")) findDefByName("Pidgin")?.let { return it }
        if (tokens.contains("tkabber") || cleanNode.contains("tkabber")) findDefByName("Tkabber")?.let { return it }
        if (tokens.contains("inchat") || cleanNode.contains("inchat") || cleanNode.contains("ndregil")) findDefByName("InChat")?.let { return it }
        if (tokens.contains("jtalk") || cleanNode.contains("jtalk")) findDefByName("jTalk")?.let { return it }
        if (tokens.contains("jimm") || cleanNode.contains("jimm")) findDefByName("Jimm aspro")?.let { return it }
        if (tokens.contains("kopete") || cleanNode.contains("kopete")) findDefByName("Kopete")?.let { return it }
        if (tokens.contains("qip") || cleanNode.contains("qip")) findDefByName("Qip")?.let { return it }
        if (tokens.contains("movim") || cleanNode.contains("movim")) findDefByName("Movim")?.let { return it }
        if (tokens.contains("poezio") || cleanNode.contains("poezio") || cleanNode.contains("poez.io")) findDefByName("Poezio")?.let { return it }
        if (tokens.contains("beagle") || cleanNode.contains("beagleim") || cleanNode.contains("beagle")) findDefByName("BeagleIM")?.let { return it }
        if (tokens.contains("c0nnect") || cleanNode.contains("c0nnect")) findDefByName("c0nnectPRO")?.let { return it }
        if (tokens.contains("archon") || cleanNode.contains("archon")) findDefByName("Archon")?.let { return it }
        if (tokens.contains("google") || tokens.contains("gmail") || cleanNode.contains("google") || cleanNode.contains("gmail")) findDefByName("Google")?.let { return it }
        if (tokens.contains("yandex") || cleanNode.contains("ya.online") || cleanNode.contains("ajabb")) findDefByName("Ya.Online")?.let { return it }

        // 5. Fallback token match across all patterns
        return registry.firstOrNull { def ->
            def.patterns.any { p ->
                val cp = p.trim().lowercase().removePrefix("http://").removePrefix("https://").removeSuffix("/")
                cp.length >= 3 && (tokens.contains(cp) || cleanNode.contains(cp))
            }
        }
    }

    /**
     * Sensitively matches client name against clients.txt patterns and names.
     */
    fun resolveByName(name: String): ClientDefinition? {
        val cleanName = name.trim().lowercase()
        if (cleanName.isBlank() || cleanName == "unknown" || cleanName.contains("@")) return null

        val registry = synchronized(dynamicRegistry) { dynamicRegistry.toList() }

        // 1. Direct or pattern exact match
        registry.firstOrNull { def ->
            def.clientName.lowercase() == cleanName || 
            def.patterns.any { p ->
                val cp = p.trim().lowercase().removePrefix("http://").removePrefix("https://").removeSuffix("/")
                val cpBase = cp.substringBefore("/")
                cpBase.isNotBlank() && cleanName == cpBase
            }
        }?.let { return it }

        // 2. Sensitive token or keyword match
        val tokens = cleanName.split(' ', '-', '_', '.', '+', '@').filter { it.isNotBlank() }

        // Priority bots & specialized clients first
        if (tokens.contains("isida") || cleanName.contains("isida")) return findDefByName("Isida bot")
        if (tokens.contains("twitter") || cleanName.contains("twitter")) return findDefByName("Twitter bot")
        if (tokens.contains("rss") || cleanName.contains("rss") || cleanName.contains("jabrss")) return findDefByName("RSS Bot")
        if (tokens.contains("bombusmod-qd") || cleanName.contains("bombus-qd")) return findDefByName("Bombus-QD")
        if (tokens.contains("bombusmod") || cleanName.contains("bombusmod")) return findDefByName("BombusMod")
        if (tokens.contains("bombus") || cleanName.contains("bombus")) return findDefByName("Bombus")
        if (tokens.contains("psi-plus") || cleanName.contains("psi+") || cleanName.contains("psi-plus")) return findDefByName("Psi+")
        if (tokens.contains("psi") || cleanName.contains("psi")) return findDefByName("Psi")
        if (tokens.contains("miranda-ng") || cleanName.contains("miranda-ng")) return findDefByName("Miranda NG")
        if (tokens.contains("miranda") || cleanName.contains("miranda")) return findDefByName("Miranda")

        // Generic bot match:
        if (tokens.contains("bot") || cleanName.contains("bot")) return findDefByName("Jabber Bot")

        // Priority InChat & common clients
        if (tokens.contains("inchat") || cleanName.contains("inchat") || cleanName.contains("ndregil")) return findDefByName("InChat")
        if (tokens.contains("conversations") || cleanName.contains("conversations")) return findDefByName("Conversations")
        if (tokens.contains("gajim") || cleanName.contains("gajim")) return findDefByName("Gajim")
        if (tokens.contains("pidgin") || cleanName.contains("pidgin") || cleanName.contains("gaim")) return findDefByName("Pidgin")
        if (tokens.contains("monal") || cleanName.contains("monal")) return findDefByName("Monal IM")
        if (tokens.contains("yaxim") || cleanName.contains("yaxim")) return findDefByName("Yaxim")
        if (tokens.contains("dino") || cleanName.contains("dino")) return findDefByName("Dino")
        if (tokens.contains("profanity") || cleanName.contains("profanity")) return findDefByName("Profanity")
        if (tokens.contains("slixmpp") || cleanName.contains("slixmpp")) return findDefByName("Slixmpp")
        if (tokens.contains("inchat") || cleanName.contains("inchat")) return findDefByName("InChat")
        if (tokens.contains("jtalk") || cleanName.contains("jtalk")) return findDefByName("jTalk")
        if (tokens.contains("blabber") || cleanName.contains("blabber")) return findDefByName("Blabber")
        if (tokens.contains("monocles") || cleanName.contains("monocles")) return findDefByName("Monocles")
        if (tokens.contains("cheogram") || cleanName.contains("cheogram")) return findDefByName("Cheogram")
        if (tokens.contains("movim") || cleanName.contains("movim")) return findDefByName("Movim")
        if (tokens.contains("poezio") || cleanName.contains("poezio")) return findDefByName("Poezio")
        if (tokens.contains("tkabber") || cleanName.contains("tkabber")) return findDefByName("Tkabber")
        if (tokens.contains("mcabber") || cleanName.contains("mcabber")) return findDefByName("Mcabber")
        if (tokens.contains("qip") || cleanName.contains("qip")) return findDefByName("Qip")
        if (tokens.contains("kopete") || cleanName.contains("kopete")) return findDefByName("Kopete")
        if (tokens.contains("jimm") || cleanName.contains("jimm")) return findDefByName("Jimm aspro")
        if (tokens.contains("beagle") || cleanName.contains("beagle")) return findDefByName("BeagleIM")
        if (tokens.contains("c0nnect") || cleanName.contains("c0nnect")) return findDefByName("c0nnectPRO")
        if (tokens.contains("archon") || cleanName.contains("archon")) return findDefByName("Archon")
        if (tokens.contains("google") || cleanName.contains("google") || cleanName.contains("gmail")) return findDefByName("Google")
        if (tokens.contains("yandex") || cleanName.contains("ya.online") || cleanName.contains("ajabb")) return findDefByName("Ya.Online")

        return null
    }

    /**
     * Resolves client definition strictly from clients.txt definitions and node patterns.
     * Note: Server domain in JID is NEVER used to infer or resolve the client.
     */
    fun resolve(name: String, jid: String, node: String): ClientDefinition? {
        val cleanNode = node.trim()
        if (cleanNode.isNotBlank()) {
            resolveByNode(cleanNode)?.let { return it }
        }

        val cleanName = name.trim()
        if (cleanName.isNotBlank()) {
            resolveByName(cleanName)?.let { return it }
        }

        if (jid.isNotBlank()) {
            resolveByJid(jid)?.let { return it }
        }

        return null
    }

    /**
     * Attempts to resolve client by examining the resource part of a JID or the JID itself.
     */
    fun resolveByJid(jid: String): ClientDefinition? {
        if (jid.isBlank()) return null
        val resource = if (jid.contains("/")) jid.substringAfterLast("/") else ""
        if (resource.isNotBlank()) {
            resolveByName(resource)?.let { return it }
        }
        
        val lower = jid.lowercase()
        if (lower.contains("inchat") || lower.contains("ndregil")) return findDefByName("InChat")
        if (lower.contains("conversations")) return findDefByName("Conversations")
        
        return null
    }

    fun getRegistry(): List<ClientDefinition> = synchronized(dynamicRegistry) { dynamicRegistry.toList() }
}


