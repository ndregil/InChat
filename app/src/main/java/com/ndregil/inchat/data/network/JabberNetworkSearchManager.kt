package com.ndregil.inchat.data.network

import android.util.Log
import com.ndregil.inchat.data.model.DiscoveredRoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Manager to fetch and search public MUC rooms from http://search.jabber.network
 * Supports live channel discovery (/channels/1) and keyword search (/search?q=...),
 * with resilient HTML parsing and curated fallback directory.
 */
class JabberNetworkSearchManager {
    companion object {
        private const val TAG = "JabberNetworkSearch"
        private const val BASE_URL = "https://search.jabber.network"

        // Curated popular global XMPP public rooms for instant offline/network fallback
        private val CURATED_PUBLIC_ROOMS = listOf(
            DiscoveredRoom(
                jid = "indonesia@conference.conversations.im",
                name = "🇮🇩 INDONESIA 🇮🇩 Conversations",
                serviceJid = "conference.conversations.im",
                description = "Komunitas XMPP & Jabber Indonesia: Diskusi Linux, Android, Coding, dan Obrolan santai nusantara.",
                occupantsCount = 28,
                subject = "Selamat datang di ruang publik Indonesia",
                isPasswordProtected = false,
                isMembersOnly = false,
                isPersistent = true
            ),
            DiscoveredRoom(
                jid = "indochat@conference.jabber.ru",
                name = "🇮🇩 💬 Indochat ✉️📲",
                serviceJid = "conference.jabber.ru",
                description = "Komunitas Indochat - Pengguna XMPP & Jabber se-Indonesia dari Sabang sampai Merauke.",
                occupantsCount = 14,
                subject = "Indochat Indonesia",
                isPasswordProtected = false,
                isMembersOnly = false,
                isPersistent = true
            ),
            DiscoveredRoom(
                jid = "conversations@conference.siacs.eu",
                name = "Conversations",
                serviceJid = "conference.siacs.eu",
                description = "Official discussion room for Conversations XMPP client on Android.",
                occupantsCount = 570,
                subject = "Conversations Android Client Support",
                isPasswordProtected = false,
                isMembersOnly = false,
                isPersistent = true
            ),
            DiscoveredRoom(
                jid = "xsf@muc.xmpp.org",
                name = "XSF Discussion",
                serviceJid = "muc.xmpp.org",
                description = "Discussion room of the XMPP Standards Foundation (XSF).",
                occupantsCount = 191,
                subject = "XMPP Standards & Extensions",
                isPasswordProtected = false,
                isMembersOnly = false,
                isPersistent = true
            ),
            DiscoveredRoom(
                jid = "jdev@muc.xmpp.org",
                name = "jdev",
                serviceJid = "muc.xmpp.org",
                description = "Jabber/XMPP Protocol & Software Developers channel.",
                occupantsCount = 152,
                subject = "XMPP development and interoperability",
                isPasswordProtected = false,
                isMembersOnly = false,
                isPersistent = true
            ),
            DiscoveredRoom(
                jid = "gajim@conference.gajim.org",
                name = "Gajim",
                serviceJid = "conference.gajim.org",
                description = "Gajim XMPP Desktop client official support channel.",
                occupantsCount = 490,
                subject = "Gajim Client",
                isPasswordProtected = false,
                isMembersOnly = false,
                isPersistent = true
            ),
            DiscoveredRoom(
                jid = "disroot@chat.disroot.org",
                name = "Disroot Community Hangout",
                serviceJid = "chat.disroot.org",
                description = "Disroot.org community hangout and general discussions.",
                occupantsCount = 464,
                subject = "Disroot Community",
                isPasswordProtected = false,
                isMembersOnly = false,
                isPersistent = true
            ),
            DiscoveredRoom(
                jid = "monal@chat.yax.im",
                name = "Monal IM",
                serviceJid = "chat.yax.im",
                description = "XMPP client for iOS and macOS discussion.",
                occupantsCount = 188,
                subject = "Monal IM Support",
                isPasswordProtected = false,
                isMembersOnly = false,
                isPersistent = true
            ),
            DiscoveredRoom(
                jid = "discuss@conference.soprani.ca",
                name = "Soprani.ca Project Discussion",
                serviceJid = "conference.soprani.ca",
                description = "Soprani.ca decentralized telephony and communication project.",
                occupantsCount = 825,
                subject = "Soprani.ca Project",
                isPasswordProtected = false,
                isMembersOnly = false,
                isPersistent = true
            ),
            DiscoveredRoom(
                jid = "linux@conference.jabber.ru",
                name = "Linux",
                serviceJid = "conference.jabber.ru",
                description = "GNU/Linux operating system, open-source software, and system administration.",
                occupantsCount = 120,
                subject = "Linux & Open Source",
                isPasswordProtected = false,
                isMembersOnly = false,
                isPersistent = true
            )
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun searchRooms(query: String = ""): Result<List<DiscoveredRoom>> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        val url = if (trimmedQuery.isNotBlank()) {
            "$BASE_URL/search?q=${java.net.URLEncoder.encode(trimmedQuery, "UTF-8")}"
        } else {
            "$BASE_URL/channels/1"
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "InChat/1.0 (Android; XMPP Public Search)")
                .header("Accept", "text/html,application/xhtml+xml")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP error ${response.code} from search.jabber.network, using fallback")
                    return@withContext Result.success(getFallbackRooms(trimmedQuery))
                }

                val html = response.body?.string() ?: ""
                val parsedRooms = parseHtmlRoomCards(html)

                if (parsedRooms.isEmpty()) {
                    Log.d(TAG, "No HTML roomcards matched, using fallback filtered rooms")
                    return@withContext Result.success(getFallbackRooms(trimmedQuery))
                }

                Log.i(TAG, "Successfully parsed ${parsedRooms.size} rooms from $url")
                Result.success(parsedRooms)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching from search.jabber.network: ${e.message}, falling back to curated list", e)
            Result.success(getFallbackRooms(trimmedQuery))
        }
    }

    private fun parseHtmlRoomCards(html: String): List<DiscoveredRoom> {
        val rooms = mutableListOf<DiscoveredRoom>()
        val cardStartRegex = Regex("<li\\b[^>]*class=['\"][^'\"]*\\broomcard\\b[^'\"]*['\"][^>]*>", RegexOption.IGNORE_CASE)
        val starts = cardStartRegex.findAll(html).toList()
        if (starts.isEmpty()) return emptyList()

        val jidPattern = Pattern.compile("href=['\"]xmpp:([^?\"#\\s]+)\\?join['\"]", Pattern.CASE_INSENSITIVE)
        val altJidPattern = Pattern.compile("class=['\"][^'\"]*\\baddress\\b[^'\"]*['\"][^>]*>([^<]+)<", Pattern.CASE_INSENSITIVE)
        val titlePattern = Pattern.compile("<h3[^>]*class=['\"][^'\"]*\\btitle\\b[^'\"]*['\"][^>]*>(.*?)</h3>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        val usersPattern = Pattern.compile("data-content=['\"](\\d+)['\"]", Pattern.CASE_INSENSITIVE)
        val descrPattern = Pattern.compile("<p[^>]*class=['\"][^'\"]*\\bdescr\\b[^'\"]*['\"][^>]*>(.*?)</p>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        val imgPattern = Pattern.compile("<img[^>]+(?:src|data-src)=['\"]?([^'\"\\s>]+)['\"]?", Pattern.CASE_INSENSITIVE)

        for (i in starts.indices) {
            val startIdx = starts[i].range.last + 1
            val endIdx = if (i + 1 < starts.size) starts[i + 1].range.first else html.length
            if (startIdx >= endIdx) continue
            val cardHtml = html.substring(startIdx, endIdx)

            // 1. Extract JID
            var jid = ""
            val jidMatcher = jidPattern.matcher(cardHtml)
            if (jidMatcher.find()) {
                jid = jidMatcher.group(1)?.trim() ?: ""
            } else {
                val altJidMatcher = altJidPattern.matcher(cardHtml)
                if (altJidMatcher.find()) {
                    jid = altJidMatcher.group(1)?.trim() ?: ""
                }
            }

            if (jid.isBlank() || !jid.contains("@")) continue

            // 2. Extract Room Title / Name
            var name = jid.substringBefore("@")
            val titleMatcher = titlePattern.matcher(cardHtml)
            if (titleMatcher.find()) {
                val rawTitle = titleMatcher.group(1) ?: ""
                val cleanTitle = cleanHtml(rawTitle)
                if (cleanTitle.isNotBlank()) {
                    name = cleanTitle
                }
            }

            // 3. Extract Online Users Count
            var occupantsCount = 0
            val usersMatcher = usersPattern.matcher(cardHtml)
            if (usersMatcher.find()) {
                occupantsCount = usersMatcher.group(1)?.toIntOrNull() ?: 0
            }

            // 4. Extract Description
            var description = ""
            val descrMatcher = descrPattern.matcher(cardHtml)
            if (descrMatcher.find()) {
                val rawDesc = descrMatcher.group(1) ?: ""
                description = cleanHtml(rawDesc)
            }

            var avatarUrl: String? = null
            val imgMatcher = imgPattern.matcher(cardHtml)
            while (imgMatcher.find()) {
                val rawSrc = imgMatcher.group(1)?.trim() ?: ""
                if (rawSrc.isNotBlank() && !rawSrc.startsWith("data:", ignoreCase = true)) {
                    avatarUrl = when {
                        rawSrc.startsWith("http://", ignoreCase = true) || rawSrc.startsWith("https://", ignoreCase = true) -> rawSrc
                        rawSrc.startsWith("//") -> "https:$rawSrc"
                        rawSrc.startsWith("/") -> "$BASE_URL$rawSrc"
                        else -> "$BASE_URL/$rawSrc"
                    }
                    break
                }
            }

            val serviceJid = jid.substringAfter("@")

            rooms.add(
                DiscoveredRoom(
                    jid = jid,
                    name = name,
                    serviceJid = serviceJid,
                    description = description,
                    occupantsCount = occupantsCount,
                    subject = description.take(60),
                    isPasswordProtected = cardHtml.contains("icon-lock") || cardHtml.contains("password"),
                    isMembersOnly = cardHtml.contains("members_only") || cardHtml.contains("members-only"),
                    isPersistent = true,
                    avatarUrl = avatarUrl
                )
            )
        }

        return rooms
    }

    private fun cleanHtml(html: String): String {
        return html
            .replace(Regex("<[^>]+>"), "") // strip HTML tags
            .replace("&#x2f;", "/")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .trim()
    }

    private fun getFallbackRooms(query: String): List<DiscoveredRoom> {
        if (query.isBlank()) return CURATED_PUBLIC_ROOMS
        val q = query.lowercase()
        return CURATED_PUBLIC_ROOMS.filter {
            it.name.lowercase().contains(q) ||
            it.jid.lowercase().contains(q) ||
            it.description.lowercase().contains(q) ||
            it.subject.lowercase().contains(q)
        }
    }
}
