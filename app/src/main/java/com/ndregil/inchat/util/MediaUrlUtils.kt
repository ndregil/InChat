package com.ndregil.inchat.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.UriHandler
import java.util.Locale

data class UrlMatch(
    val range: IntRange,
    val displayUrl: String,
    val normalizedUrl: String
)

object MediaUrlUtils {

    private val EXPLICIT_URL_REGEX = Regex(
        """(?:https?://|www\.)[^\s<>"{}|\\^`\[\]]+""",
        RegexOption.IGNORE_CASE
    )

    private val BARE_DOMAIN_REGEX = Regex(
        """(?<![@\w./-])(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+(?:com|org|net|id|im|io|co|me|dev|app|ai|gg|tv|xyz|info|biz|edu|gov|ru|de|uk|jp|cn|nl|fr|es|it|br|in|cc|ly|to|sh|fm|tech|online|site|store|blog|cloud|live)(?::\d{1,5})?(?:/[^\s<>"{}|\\^`\[\]]*)?""",
        RegexOption.IGNORE_CASE
    )

    private val KNOWN_IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "jpe", "jfif", "pjpeg", "pjp",
        "png", "apng",
        "gif",
        "webp",
        "bmp", "dib",
        "svg", "svgz",
        "heic", "heif",
        "avif", "avifs",
        "tif", "tiff",
        "ico", "cur",
        "jxl",
        "tga", "pcx", "psd", "wbmp", "xbm", "xpm",
        "raw", "cr2", "nef", "arw", "dng"
    )

    private val KNOWN_AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "flac", "wma", "amr", "mid", "midi"
    )

    private val KNOWN_NON_IMAGE_EXTENSIONS = KNOWN_AUDIO_EXTENSIONS + setOf(
        "mp4", "m4v", "mkv", "webm", "avi", "mov", "wmv", "flv", "3gp",
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf", "epub",
        "txt", "csv", "tsv", "json", "xml", "html", "htm", "md", "log", "ini", "cfg", "conf", "yaml", "yml", "toml",
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "zst",
        "apk", "aab", "exe", "msi", "dmg", "iso", "deb", "rpm", "appimage",
        "bin", "dat", "db", "sqlite", "sql", "jar", "dex", "so",
        "py", "kt", "java", "js", "ts", "c", "cpp", "h", "sh", "bat", "ps1",
        "vcf", "ics", "torrent"
    )

    private val IMAGE_FORMAT_QUERY_REGEX = Regex(
        """(?:^|[?&])(format|fm|ext|type|img|image)=?(png|jpe?g|webp|gif|bmp|svg|heic|heif|avif|tiff?|ico|jfif|apng|jxl|image)(?:&|$)""",
        RegexOption.IGNORE_CASE
    )

    fun extractUrlMatches(text: String): List<UrlMatch> {
        if (text.isBlank()) return emptyList()

        val matches = mutableListOf<UrlMatch>()
        val occupiedRanges = mutableListOf<IntRange>()

        for (rawMatch in EXPLICIT_URL_REGEX.findAll(text)) {
            val cleaned = trimTrailingUrlPunctuation(rawMatch.value)
            if (cleaned.isBlank()) continue
            val endInclusive = rawMatch.range.first + cleaned.length - 1
            val range = rawMatch.range.first..endInclusive
            occupiedRanges.add(range)
            matches.add(
                UrlMatch(
                    range = range,
                    displayUrl = cleaned,
                    normalizedUrl = normalizeUrl(cleaned)
                )
            )
        }

        for (rawMatch in BARE_DOMAIN_REGEX.findAll(text)) {
            val start = rawMatch.range.first
            val overlapped = occupiedRanges.any { start in it || rawMatch.range.last in it }
            if (overlapped) continue

            // Never match domains that belong to an email/XMPP JID like user@example.com
            if (isPrecededByAtInToken(text, start)) continue

            val cleaned = trimTrailingUrlPunctuation(rawMatch.value)
            if (cleaned.isBlank() || !cleaned.contains('.')) continue
            val endInclusive = start + cleaned.length - 1
            val range = start..endInclusive
            occupiedRanges.add(range)
            matches.add(
                UrlMatch(
                    range = range,
                    displayUrl = cleaned,
                    normalizedUrl = normalizeUrl(cleaned)
                )
            )
        }

        return matches.sortedBy { it.range.first }
    }

    fun extractUrls(text: String): List<String> {
        return extractUrlMatches(text)
            .map { it.normalizedUrl }
            .distinct()
    }

    fun normalizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        return when {
            trimmed.startsWith("https://", ignoreCase = true) ->
                "https://" + trimmed.substring(8)
            trimmed.startsWith("http://", ignoreCase = true) ->
                "http://" + trimmed.substring(7)
            trimmed.startsWith("content://", ignoreCase = true) ||
                trimmed.startsWith("file://", ignoreCase = true) -> trimmed
            else -> "https://$trimmed"
        }
    }

    fun extractFileExtension(url: String): String {
        val clean = url.trim().substringBefore('#').substringBefore('?')
        val lastSegment = clean.substringAfterLast('/', "")
        val ext = lastSegment.substringAfterLast('.', "")
        return if (ext.isNotBlank() && ext.length <= 15 && lastSegment.contains('.')) {
            ext.lowercase(Locale.ROOT)
        } else {
            ""
        }
    }

    fun isKnownImageUrl(url: String?, mimeType: String? = null): Boolean {
        if (!mimeType.isNullOrBlank() && mimeType.startsWith("image/", ignoreCase = true)) {
            return true
        }
        val target = url?.trim() ?: return false
        if (target.isBlank()) return false

        val ext = extractFileExtension(target)
        if (ext in KNOWN_IMAGE_EXTENSIONS) return true

        if (IMAGE_FORMAT_QUERY_REGEX.containsMatchIn(target)) return true

        val lower = target.lowercase(Locale.ROOT)
        if (lower.contains("/preview/") || lower.contains("/thumb/") || lower.contains("/thumbnail/")) {
            return true
        }
        return false
    }

    fun isVoiceOrAudioUrl(url: String?): Boolean {
        val target = url?.trim() ?: return false
        if (target.isBlank()) return false
        val ext = extractFileExtension(target)
        return ext in KNOWN_AUDIO_EXTENSIONS || target.contains("voice_", ignoreCase = true)
    }

    fun isDefinitelyNonImageUrl(url: String?, mimeType: String? = null): Boolean {
        if (!mimeType.isNullOrBlank()) {
            val lowerMime = mimeType.lowercase(Locale.ROOT)
            if (lowerMime.startsWith("image/")) return false
            if (lowerMime.startsWith("audio/") ||
                lowerMime.startsWith("video/") ||
                lowerMime.startsWith("text/") ||
                lowerMime == "application/pdf" ||
                lowerMime == "application/zip" ||
                lowerMime == "application/x-rar-compressed" ||
                lowerMime == "application/vnd.android.package-archive"
            ) {
                return true
            }
        }
        val target = url?.trim() ?: return true
        if (target.isBlank()) return true
        if (IMAGE_FORMAT_QUERY_REGEX.containsMatchIn(target)) return false
        val ext = extractFileExtension(target)
        return ext in KNOWN_NON_IMAGE_EXTENSIONS
    }

    fun shouldAttemptImagePreview(url: String?, mimeType: String? = null): Boolean {
        val target = url?.trim() ?: return false
        if (target.isBlank()) return false
        val lower = target.lowercase(Locale.ROOT)
        val isSupportedScheme = lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("content://") ||
            lower.startsWith("file://")
        if (!isSupportedScheme) return false
        return isKnownImageUrl(target, mimeType) || !isDefinitelyNonImageUrl(target, mimeType)
    }

    fun inferImageMimeType(url: String): String {
        return when (extractFileExtension(url)) {
            "png", "apng" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg", "svgz" -> "image/svg+xml"
            "bmp", "dib" -> "image/bmp"
            "avif", "avifs" -> "image/avif"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "tif", "tiff" -> "image/tiff"
            "ico", "cur" -> "image/x-icon"
            else -> "image/*"
        }
    }

    fun inferFileMimeType(url: String): String {
        val ext = extractFileExtension(url)
        if (ext.isNotBlank()) {
            try {
                val mapped = android.webkit.MimeTypeMap.getSingleton()?.getMimeTypeFromExtension(ext)
                if (!mapped.isNullOrBlank()) return mapped
            } catch (_: Throwable) {
            }
        }
        return "application/octet-stream"
    }

    fun openExternalUrl(
        context: Context,
        uriHandler: UriHandler?,
        rawUrl: String,
        errorToastPrefix: String? = null
    ): Boolean {
        val normalized = normalizeUrl(rawUrl)
        val parsedUri = try {
            Uri.parse(normalized)
        } catch (_: Exception) {
            null
        } ?: return false

        val intent = Intent(Intent.ACTION_VIEW, parsedUri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            if (findActivity(context) == null) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        return try {
            context.startActivity(intent)
            true
        } catch (firstErr: Exception) {
            try {
                if (uriHandler != null) {
                    uriHandler.openUri(normalized)
                    true
                } else {
                    val newTaskIntent = Intent(Intent.ACTION_VIEW, parsedUri).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(newTaskIntent)
                    true
                }
            } catch (secondErr: Exception) {
                val msg = secondErr.localizedMessage ?: firstErr.localizedMessage ?: normalized
                val toastText = if (!errorToastPrefix.isNullOrBlank()) "$errorToastPrefix $msg" else msg
                Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
                false
            }
        }
    }

    private fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    private fun isPrecededByAtInToken(text: String, startIndex: Int): Boolean {
        var idx = startIndex - 1
        while (idx >= 0) {
            val ch = text[idx]
            if (ch.isWhitespace()) return false
            if (ch == '@') return true
            idx--
        }
        return false
    }

    internal fun trimTrailingUrlPunctuation(raw: String): String {
        var result = raw
        while (result.isNotEmpty()) {
            val last = result.last()
            val shouldTrim = when (last) {
                ',', '.', '!', '?', ';', ':', '\'', '"', '>' -> true
                ')' -> result.count { it == ')' } > result.count { it == '(' }
                ']' -> result.count { it == ']' } > result.count { it == '[' }
                else -> false
            }
            if (shouldTrim) {
                result = result.dropLast(1)
            } else {
                break
            }
        }
        return result
    }
}
