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
            canonicalNodeUri = "http://gajim.org/",
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
            canonicalNodeUri = "http://movim.eu/",
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
                        loadedClients.add(
                            ClientDefinition(
                                patterns = patterns,
                                clientName = clientName,
                                canonicalNodeUri = patterns.firstOrNull { it.startsWith("http") } ?: "http://$clientName",
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
     * Resolves client definition strictly from clients.txt definitions and node patterns.
     * Note: Server domain in JID is NEVER used to infer or resolve the client.
     */
    fun resolve(name: String, jid: String, node: String): ClientDefinition? {
        val cleanNode = node.trim().lowercase().removePrefix("http://").removePrefix("https://").removeSuffix("/")
        val cleanName = name.trim().lowercase()

        // 1. Direct and Highest Precision: Match node from XEP-0115 against patterns in clients.txt
        if (cleanNode.isNotBlank()) {
            dynamicRegistry.firstOrNull { def -> 
                def.patterns.any { pattern ->
                    val cleanPattern = pattern.trim().lowercase().removePrefix("http://").removePrefix("https://").removeSuffix("/")
                    cleanNode == cleanPattern || cleanNode.startsWith("$cleanPattern/") || cleanNode.startsWith("$cleanPattern#")
                } 
            }?.let { return it }

            // Partial contains match on node pattern (minimum 4 characters)
            dynamicRegistry.firstOrNull { def ->
                def.patterns.any { pattern ->
                    val cleanPattern = pattern.trim().lowercase().removePrefix("http://").removePrefix("https://").removeSuffix("/")
                    cleanPattern.length >= 4 && cleanNode.contains(cleanPattern)
                }
            }?.let { return it }
        }

        // 2. Exact Client Name match (e.g. from XEP-0092 Software Version Query)
        if (cleanName.isNotBlank()) {
            dynamicRegistry.firstOrNull { def ->
                def.clientName.lowercase() == cleanName || 
                def.patterns.any { p -> p.lowercase() == cleanName }
            }?.let { return it }
        }

        // 3. Special case: If node is absent but name is explicitly our app
        if (cleanName == "inchat") {
            return dynamicRegistry.firstOrNull { it.clientName.equals("InChat", ignoreCase = true) }
        }

        // Do NOT guess from server domain in JID!
        return null
    }

    fun getRegistry(): List<ClientDefinition> = dynamicRegistry
}

