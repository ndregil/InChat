package com.ndregil.inchat.data.network

import android.content.Context
import android.util.Log
import com.ndregil.inchat.R
import com.ndregil.inchat.data.model.ClientCaps
import com.ndregil.inchat.data.model.ClientDefinition
import com.ndregil.inchat.data.model.ClientPlatform
import org.jivesoftware.smack.packet.Presence
import org.jivesoftware.smack.packet.StandardExtensionElement
import org.jivesoftware.smack.packet.XmlEnvironment
import org.jivesoftware.smackx.caps.packet.CapsExtension
import java.util.concurrent.ConcurrentHashMap

/**
 * Identifikasi klien HANYA berdasarkan node XEP-0115 (atau hasil XEP-0092 live).
 *
 * Aturan stabilitas:
 * - Nick / resource / nama tampilan TIDAK PERNAH dipakai untuk menebak klien.
 * - Cache dikunci per JID persis (occupant JID lengkap di MUC), tanpa fallback ke bare JID.
 * - Klien tidak dikenal => null (UI menampilkan icon platform netral), bukan icon bot.
 *
 * CATATAN: kode ini mengasumsikan ClientDefinition.iconResId bertipe Int?
 * (ubah di data class jika sekarang Int non-null).
 */
object ClientRegistryManager {
    private const val TAG = "ClientRegistryManager"
    private const val CAPS_NS = "http://jabber.org/protocol/caps"

    // ---------------------------------------------------------------------
    // Cache: kunci = JID persis (bare lowercase + resource case-sensitive)
    // ---------------------------------------------------------------------

    private val globalCapsCache = ConcurrentHashMap<String, ClientCaps>()
    private val queriedVersions = ConcurrentHashMap.newKeySet<String>()

    private fun cacheKey(jid: String): String {
        val t = jid.trim()
        val i = t.indexOf('/')
        return if (i < 0) t.lowercase() else t.substring(0, i).lowercase() + t.substring(i)
    }

    fun getCachedCaps(jid: String): ClientCaps? {
        if (jid.isBlank()) return null
        val caps = globalCapsCache[cacheKey(jid)]
        return if (caps?.iconResId != null) caps else null
    }

    fun putCachedCaps(jid: String, caps: ClientCaps) {
        if (jid.isBlank() || caps.iconResId == null) return
        globalCapsCache[cacheKey(jid)] = caps
    }

    /** Panggil saat presence `unavailable` atau nick berganti. */
    fun removeCachedCaps(jid: String) {
        if (jid.isBlank()) return
        globalCapsCache.remove(cacheKey(jid))
        queriedVersions.remove(cacheKey(jid))
    }

    /** Panggil saat keluar dari room: hapus semua occupant room tersebut. */
    fun clearRoomCaps(roomBareJid: String) {
        val prefix = roomBareJid.trim().lowercase() + "/"
        globalCapsCache.keys.removeAll { it.startsWith(prefix) }
        queriedVersions.removeAll { it.startsWith(prefix) }
    }

    fun clearAllCaps() {
        globalCapsCache.clear()
        queriedVersions.clear()
    }

    fun markAsQueried(key: String): Boolean = queriedVersions.add(cacheKey(key))

    // ---------------------------------------------------------------------
    // Registry statis (fallback bila clients.txt gagal dimuat)
    // ---------------------------------------------------------------------

    private fun def(
        name: String,
        canonical: String,
        platform: ClientPlatform,
        icon: Int?,
        version: String,
        os: String,
        vararg patterns: String
    ) = ClientDefinition(
        patterns = patterns.toList(),
        clientName = name,
        canonicalNodeUri = canonical,
        platform = platform,
        iconResId = icon,
        defaultVersion = version,
        defaultOs = os
    )

    private val staticRegistry: List<ClientDefinition> = listOf(
        def("Slixmpp", "slixmpp.com", ClientPlatform.BOT, R.drawable.ic_client_slixmpp, "1.0", "Python",
            "slixmpp.com", "slixmpp"),
        def("jTalk", "http://jtalk.ustyugov.net/caps", ClientPlatform.ANDROID, R.drawable.ic_client_jtalk, "1.0", "Mobile",
            "jtalk.ustyugov.net/caps", "jtalk.ustyugov.net", "jtalk"),
        def("Another IM", "http://dev.narayana.im", ClientPlatform.DESKTOP, R.drawable.ic_client_anotherim, "1.0", "Desktop",
            "dev.narayana.im", "anotherim"),
        def("SmartNG", "http://ndregil.smart-ng", ClientPlatform.ANDROID, R.drawable.ic_client_smartng, "1.0", "Android",
            "ndregil.smart-ng", "smartng"),
        def("InChat", "http://ndregil.inchat", ClientPlatform.ANDROID, R.drawable.ic_client_inchat, "1.0.0", "Android",
            "http://ndregil.inchat", "ndregil.inchat", "inchat"),
        def("BombusMod", "http://bombusmod.net.ru/", ClientPlatform.ANDROID, R.drawable.ic_client_bombusmod, "Mod", "Mobile",
            "bombusmod.net.ru", "bombusmod"),
        def("Bombus", "http://bombus-im.org/", ClientPlatform.ANDROID, R.drawable.ic_client_bombus, "Original", "Mobile",
            "bombus-im.org", "voffk.org.ru/bombus"),
        def("Bombus-QD", "http://bombusmod-qd/", ClientPlatform.ANDROID, R.drawable.ic_client_bombus_qd, "QD", "Mobile",
            "bombusmod-qd"),
        def("Gajim", "http://gajim.org", ClientPlatform.DESKTOP, R.drawable.ic_client_gajim, "Desktop", "Linux/Windows",
            "gajim.org", "gajim"),
        def("Google", "http://google.com/talk", ClientPlatform.WEB, R.drawable.ic_client_google, "Web", "Cloud",
            "google.com"),
        def("Jabber Bot", "http://jabbrik.ru/caps", ClientPlatform.BOT, R.drawable.ic_client_jabber_bot, "Bot", "Server",
            "jabbrik.ru/caps", "jabrvista.net.ru", "jabber-bot"),
        def("Jimm aspro", "http://jimm.net.ru/", ClientPlatform.ANDROID, R.drawable.ic_client_jimm, "Aspro", "Mobile",
            "jimm.net.ru"),
        def("Kopete", "http://kopete.kde.org/", ClientPlatform.DESKTOP, R.drawable.ic_client_kopete, "Desktop", "Linux",
            "kopete.kde.org", "kopete"),
        def("Miranda", "http://miranda-im.org/caps", ClientPlatform.DESKTOP, R.drawable.ic_client_miranda, "Desktop", "Windows",
            "miranda-im.org", "miranda-im"),
        def("Pidgin", "http://pidgin.im/caps", ClientPlatform.DESKTOP, R.drawable.ic_client_pidgin, "Desktop", "Linux/Windows",
            "pidgin.im", "pidgin", "gaim"),
        def("Psi", "http://psi-im.org/caps", ClientPlatform.DESKTOP, R.drawable.ic_client_psi, "Desktop", "Desktop",
            "psi-im.org"),
        def("Psi+", "http://psi-plus.com/caps", ClientPlatform.DESKTOP, R.drawable.ic_client_psi_plus, "Desktop", "Desktop",
            "psi-plus.com", "psi-plus", "psi-dev.googlecode.com"),
        def("PyICQ-t", "http://pyicqt.googlecode.com/", ClientPlatform.BOT, R.drawable.ic_client_pyicq, "Transport", "Server",
            "pyicqt.googlecode.com"),
        def("Qip", "http://qip.ru/caps", ClientPlatform.DESKTOP, R.drawable.ic_client_qip, "Desktop", "Windows",
            "qip.ru"),
        def("Tkabber", "http://tkabber.jabber.ru/", ClientPlatform.DESKTOP, R.drawable.ic_client_tkabber, "Desktop", "Tcl/Tk",
            "tkabber.jabber.ru"),
        def("Ya.Online", "http://online.yandex.ru/", ClientPlatform.WEB, R.drawable.ic_client_ya_online, "Web", "Cloud",
            "online.yandex.ru", "ajabb"),
        def("Miranda NG", "http://miranda-ng.org/caps", ClientPlatform.DESKTOP, R.drawable.ic_client_miranda_ng, "Desktop", "Windows",
            "miranda-ng.org"),
        def("Mcabber", "http://mcabber.com/caps", ClientPlatform.DESKTOP, R.drawable.ic_client_mcabber, "Console", "Linux",
            "mcabber.com", "mcabber"),
        def("Twitter bot", "http://twitter.com/bot", ClientPlatform.BOT, R.drawable.ic_client_twitter, "Bot", "Server",
            "twitter.com/bot"),
        def("Isida bot", "http://isida-bot.com/", ClientPlatform.BOT, R.drawable.ic_client_isida_bot, "Bot", "Server",
            "isida-bot.com"),
        def("RSS Bot", "http://jabrss.ru/caps", ClientPlatform.BOT, R.drawable.ic_client_rss_bot, "Bot", "Server",
            "jabrss.ru", "jabrss"),
        def("Conversations", "http://conversations.im", ClientPlatform.ANDROID, R.drawable.ic_client_conversations, "Android", "Android",
            "conversations.im"),
        def("Blabber", "http://blabber.im", ClientPlatform.ANDROID, R.drawable.ic_client_blabber, "Android", "Android",
            "blabber.im"),
        def("Monocles", "http://monocles.chat", ClientPlatform.ANDROID, R.drawable.ic_client_monocles, "Android", "Android",
            "monocles.chat", "monocles"),
        def("Cheogram", "https://cheogram.com/caps", ClientPlatform.ANDROID, R.drawable.ic_client_cheogram, "Android", "Android",
            "cheogram.com/caps", "cheogram.com"),
        def("Movim", "http://movim.eu", ClientPlatform.WEB, R.drawable.ic_client_movim, "Web", "Cloud",
            "movim.eu"),
        def("Monal IM", "http://monal.im", ClientPlatform.IOS, R.drawable.ic_client_monal, "iOS", "iOS",
            "monal.im", "monal-im.org"),
        def("Poezio", "http://poez.io", ClientPlatform.DESKTOP, R.drawable.ic_client_poezio, "Console", "Linux",
            "poez.io", "poezio.eu"),
        def("c0nnectPRO", "http://c0nnect.org/", ClientPlatform.DESKTOP, R.drawable.ic_client_c0nnect, "Desktop", "Desktop",
            "c0nnect.org"),
        def("Yaxim", "http://yaxim.org", ClientPlatform.ANDROID, R.drawable.ic_client_yaxim, "Android", "Android",
            "yaxim.org"),
        def("Archon", "http://archon.im/caps", ClientPlatform.DESKTOP, R.drawable.ic_client_archon, "Desktop", "Desktop",
            "archon.im"),
        def("Dino", "http://dino.im", ClientPlatform.DESKTOP, R.drawable.ic_client_dino, "Desktop", "Linux",
            "dino.im"),
        def("Profanity", "http://profanity.im", ClientPlatform.DESKTOP, R.drawable.ic_client_profanity, "Console", "Linux",
            "profanity.im"),
        def("BeagleIM", "http://beagleim.org", ClientPlatform.DESKTOP, R.drawable.ic_client_beagleim, "Desktop", "macOS",
            "beagleim.org")
    )

    private val staticByName: Map<String, ClientDefinition> =
        staticRegistry.associateBy { it.clientName.lowercase() }

    @Volatile
    private var registry: List<ClientDefinition> = staticRegistry

    // ---------------------------------------------------------------------
    // Loader clients.txt
    //
    // Format (dipisah TAB, baris # diabaikan):
    //   pola1,pola2,...  <TAB> NamaKlien  [<TAB> PLATFORM] [<TAB> nama_icon] [<TAB> OS]
    // Kolom 3-5 opsional. Jika kosong, diambil dari registry statis
    // (nama persis sama), selain itu UNKNOWN / tanpa icon.
    // ---------------------------------------------------------------------

    fun init(context: Context) {
        try {
            val loaded = mutableListOf<ClientDefinition>()

            context.assets.open("clients.txt").bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine

                    val parts = trimmed.split("\t").map { it.trim() }
                    if (parts.size < 2) return@forEachLine

                    val patterns = parts[0].split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val clientName = parts[1]
                    if (patterns.isEmpty() || clientName.isBlank()) return@forEachLine

                    val known = staticByName[clientName.lowercase()]

                    val platform = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }?.let { parsePlatform(it) }
                        ?: known?.platform
                        ?: ClientPlatform.UNKNOWN

                    val icon = resolveIconRes(
                        clientName = clientName,
                        explicitIconName = parts.getOrNull(3),
                        known = known,
                        context = context
                    )

                    val os = parts.getOrNull(4)?.takeIf { it.isNotEmpty() }
                        ?: known?.defaultOs
                        ?: "-"

                    val canonical = known?.canonicalNodeUri ?: canonicalFromPatterns(patterns)

                    loaded.add(
                        ClientDefinition(
                            patterns = patterns,
                            clientName = clientName,
                            canonicalNodeUri = canonical,
                            platform = platform,
                            iconResId = icon,
                            defaultVersion = known?.defaultVersion ?: "-",
                            defaultOs = os
                        )
                    )
                }
            }

            if (loaded.isNotEmpty()) {
                registry = loaded.toList()
                Log.i(TAG, "Loaded ${loaded.size} clients from clients.txt")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load clients.txt: ${e.message}, using static definitions")
        }
    }

    private fun parsePlatform(raw: String): ClientPlatform? =
        runCatching { ClientPlatform.valueOf(raw.trim().uppercase()) }.getOrNull()

    private fun canonicalFromPatterns(patterns: List<String>): String {
        patterns.firstOrNull { it.startsWith("http://") || it.startsWith("https://") }?.let { return it }
        val p = patterns.firstOrNull { it.contains(".") } ?: patterns.first()
        return "http://$p"
    }

    /**
     * Icon: (1) nama icon eksplisit dari clients.txt, (2) registry statis dengan nama PERSIS sama,
     * (3) drawable ic_client_<nama> bila ada. Selain itu null (tanpa tebakan substring).
     */
    private fun resolveIconRes(
        clientName: String,
        explicitIconName: String?,
        known: ClientDefinition?,
        context: Context
    ): Int? {
        fun lookup(name: String): Int? {
            val id = context.resources.getIdentifier(name, "drawable", context.packageName)
            return if (id != 0) id else null
        }

        explicitIconName?.takeIf { it.isNotEmpty() }?.let { lookup(it)?.let { id -> return id } }
        known?.iconResId?.let { return it }

        val sanitized = clientName.lowercase().trim()
            .replace("+", "_plus")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        return if (sanitized.isEmpty()) null else lookup("ic_client_$sanitized")
    }

    // ---------------------------------------------------------------------
    // Ekstraksi caps dari presence (HANYA dari elemen <c/>, tanpa fallback resource)
    // ---------------------------------------------------------------------

    fun extractCapsFromPresence(presence: Presence?): Pair<String, String>? {
        if (presence == null) return null

        // 1. Smack CapsExtension standar
        try {
            val capsExt = CapsExtension.from(presence)
                ?: (presence.getExtension("c", CAPS_NS) as? CapsExtension)
            if (capsExt != null && capsExt.node.isNotBlank()) {
                return Pair(capsExt.node, capsExt.ver ?: "")
            }
        } catch (_: Exception) {
        }

        // 2. Cari elemen caps di semua extension
        try {
            for (ext in presence.extensions) {
                if (ext.namespace != CAPS_NS) continue

                if (ext is CapsExtension && ext.node.isNotBlank()) {
                    return Pair(ext.node, ext.ver ?: "")
                }

                if (ext is StandardExtensionElement) {
                    val node = ext.getAttributeValue("node")
                    val ver = ext.getAttributeValue("ver")
                    if (!node.isNullOrBlank()) return Pair(node, ver ?: "")
                }

                // 3. Fallback regex untuk tipe extension yang tidak dikenal
                val xml = ext.toXML(null as XmlEnvironment?).toString()
                val node = Regex("""node=["']([^"']+)["']""").find(xml)?.groupValues?.get(1)
                val ver = Regex("""ver=["']([^"']+)["']""").find(xml)?.groupValues?.get(1)
                if (!node.isNullOrBlank()) return Pair(node, ver ?: "")
            }
        } catch (_: Exception) {
        }

        // Sengaja TIDAK ada fallback berdasarkan resource/nick.
        return null
    }

    // ---------------------------------------------------------------------
    // Pencocokan
    // ---------------------------------------------------------------------

    /**
     * Strict check untuk InChat: http(s)://ndregil.inchat, ndregil.inchat, inchat.
     * Selain itu BUKAN InChat.
     */
    fun isInChatExact(value: String): Boolean {
        val clean = value.trim().lowercase().removeSuffix("/")
        if (clean.isEmpty()) return false
        return clean == "http://ndregil.inchat" ||
                clean == "https://ndregil.inchat" ||
                clean == "ndregil.inchat" ||
                clean == "inchat"
    }

    /** Normalisasi node/pola: tanpa skema, tanpa #ver / ?query, tanpa slash akhir, lowercase. */
    private fun normalize(s: String): String = s.trim().lowercase()
        .removePrefix("http://").removePrefix("https://")
        .substringBefore('#').substringBefore('?')
        .trimEnd('/')

    /**
     * Skor kecocokan node vs pola (0 = tidak cocok). Semua berbasis batas yang jelas, bukan contains():
     *  - sama persis                         -> 3
     *  - node diawali "pola/"                -> 2
     *  - host node == pola, atau host berakhir ".pola"      -> 2
     *  - pola tanpa titik/slash cocok dengan satu label host -> 1
     * Skor akhir ditambah panjang pola agar pola lebih spesifik menang, tidak bergantung urutan list.
     */
    private fun matchScore(node: String, pattern: String): Int {
        val k = normalize(pattern)
        if (k.isBlank()) return 0
        val host = node.substringBefore('/')

        val tier = when {
            node == k -> 3
            node.startsWith("$k/") -> 2
            host == k || host.endsWith(".$k") -> 2
            !k.contains('.') && !k.contains('/') && host.split('.').contains(k) -> 1
            else -> 0
        }
        return if (tier == 0) 0 else tier * 1000 + k.length
    }

    fun findDefByName(targetName: String): ClientDefinition? {
        val target = targetName.trim().lowercase()
        if (target.isEmpty()) return null
        return registry.firstOrNull { it.clientName.lowercase() == target } ?: staticByName[target]
    }

    /** Pencocokan node XEP-0115 terhadap pola clients.txt. InChat hanya lewat isInChatExact. */
    fun resolveByNode(node: String): ClientDefinition? {
        val raw = node.trim()
        if (raw.isBlank()) return null

        if (isInChatExact(raw.substringBefore('#'))) return staticByName["inchat"]

        val n = normalize(raw)
        if (n.isBlank()) return null

        var best: ClientDefinition? = null
        var bestScore = 0
        for (def in registry) {
            if (def.clientName.equals("InChat", ignoreCase = true)) continue
            val score = def.patterns.maxOfOrNull { matchScore(n, it) } ?: 0
            if (score > bestScore) {
                bestScore = score
                best = def
            }
        }
        return best
    }

    /**
     * Hanya untuk NAMA KLIEN yang dilaporkan XEP-0092 (bukan nick/resource).
     * Cocok persis dengan clientName.
     */
    fun resolveByName(name: String): ClientDefinition? {
        val clean = name.trim().lowercase()
        if (clean.isBlank() || clean == "unknown" || clean.contains("@")) return null
        if (isInChatExact(clean)) return staticByName["inchat"]
        return registry.firstOrNull {
            !it.clientName.equals("InChat", ignoreCase = true) && it.clientName.lowercase() == clean
        }
    }

    /**
     * Resolusi utama: HANYA dari node. Parameter name dan jid dipertahankan demi kompatibilitas
     * pemanggil lama tetapi diabaikan (nick/resource tidak boleh menentukan klien).
     */
    @Suppress("UNUSED_PARAMETER")
    fun resolve(name: String, jid: String, node: String): ClientDefinition? = resolveByNode(node)

    @Deprecated("Resource/nick bukan sumber identitas klien yang valid.", ReplaceWith("null"))
    @Suppress("UNUSED_PARAMETER")
    fun resolveByJid(jid: String): ClientDefinition? = null

    fun getRegistry(): List<ClientDefinition> = registry
}
