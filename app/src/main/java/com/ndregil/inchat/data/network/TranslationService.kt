package com.ndregil.inchat.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

data class TranslationLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String
)

class TranslationService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val TAG = "TranslationService"
        private const val GOOGLE_CHUNK_MAX_CHARS = 1800
        private const val MYMEMORY_CHUNK_MAX_CHARS = 420

        val SUPPORTED_LANGUAGES = listOf(
            TranslationLanguage("id", "Bahasa Indonesia", "Indonesia"),
            TranslationLanguage("en", "Inggris", "English"),
            TranslationLanguage("es", "Spanyol", "Español"),
            TranslationLanguage("fr", "Prancis", "Français"),
            TranslationLanguage("de", "Jerman", "Deutsch"),
            TranslationLanguage("ar", "Arab", "العربية"),
            TranslationLanguage("ja", "Jepang", "日本語"),
            TranslationLanguage("ko", "Korea", "한국어"),
            TranslationLanguage("zh", "China (Mandarin)", "中文"),
            TranslationLanguage("ru", "Rusia", "Русский"),
            TranslationLanguage("jv", "Jawa", "Basa Jawa"),
            TranslationLanguage("su", "Sunda", "Basa Sunda"),
            TranslationLanguage("ms", "Melayu", "Bahasa Melayu"),
            TranslationLanguage("pt", "Portugis", "Português"),
            TranslationLanguage("it", "Italia", "Italiano"),
            TranslationLanguage("nl", "Belanda", "Nederlands"),
            TranslationLanguage("tr", "Turki", "Türkçe"),
            TranslationLanguage("vi", "Vietnam", "Tiếng Việt"),
            TranslationLanguage("th", "Thailand", "ไทย"),
            TranslationLanguage("hi", "Hindi", "हिन्दी"),
            TranslationLanguage("bn", "Bengali", "বাংলা"),
            TranslationLanguage("ur", "Urdu", "اردو"),
            TranslationLanguage("fa", "Persia", "فارسی"),
            TranslationLanguage("pl", "Polandia", "Polski"),
            TranslationLanguage("uk", "Ukraina", "Українська"),
            TranslationLanguage("tl", "Filipino (Tagalog)", "Tagalog"),
            TranslationLanguage("ta", "Tamil", "தமிழ்"),
            TranslationLanguage("te", "Telugu", "తెలుగు"),
            TranslationLanguage("sv", "Swedia", "Svenska"),
            TranslationLanguage("no", "Norwegia", "Norsk"),
            TranslationLanguage("da", "Denmark", "Dansk"),
            TranslationLanguage("fi", "Finlandia", "Suomi"),
            TranslationLanguage("el", "Yunani", "Ελληνικά"),
            TranslationLanguage("cs", "Ceko", "Čeština"),
            TranslationLanguage("ro", "Rumania", "Română"),
            TranslationLanguage("hu", "Hongaria", "Magyar"),
            TranslationLanguage("he", "Ibrani", "עברית"),
            TranslationLanguage("my", "Myanmar (Burma)", "မြန်မာ"),
            TranslationLanguage("km", "Khmer", "ខ្មែរ"),
            TranslationLanguage("lo", "Lao", "ລາວ"),
            TranslationLanguage("ne", "Nepal", "नेपाली"),
            TranslationLanguage("si", "Sinhala", "සිංහල"),
            TranslationLanguage("sw", "Swahili", "Kiswahili"),
            TranslationLanguage("af", "Afrikaans", "Afrikaans"),
            TranslationLanguage("ca", "Katalan", "Català"),
            TranslationLanguage("hr", "Kroasia", "Hrvatski"),
            TranslationLanguage("sr", "Serbia", "Српски"),
            TranslationLanguage("sk", "Slovakia", "Slovenčina"),
            TranslationLanguage("bg", "Bulgaria", "Български"),
            TranslationLanguage("zh-TW", "China (Tradisional)", "繁體中文")
        )

        fun getDefaultSystemLanguageCode(): String {
            val lang = Locale.getDefault().language.lowercase()
            return if (SUPPORTED_LANGUAGES.any { it.code.equals(lang, ignoreCase = true) }) lang else "id"
        }

        fun getLanguageName(code: String): String {
            val clean = code.trim()
            return SUPPORTED_LANGUAGES.find { it.code.equals(clean, ignoreCase = true) }?.displayName
                ?: SUPPORTED_LANGUAGES.find { normalizeBaseLangCode(it.code) == normalizeBaseLangCode(clean) }?.displayName
                ?: clean.uppercase(Locale.ROOT)
        }

        internal fun normalizeBaseLangCode(code: String): String {
            return code.trim().lowercase(Locale.ROOT).substringBefore("-").substringBefore("_")
        }

        internal fun isStandaloneNonTranslatableLink(text: String): Boolean {
            val clean = text.trim()
            if (clean.isEmpty()) return true
            if (clean.any { it.isWhitespace() }) return false
            return clean.startsWith("http://", ignoreCase = true) ||
                clean.startsWith("https://", ignoreCase = true) ||
                clean.startsWith("geo:", ignoreCase = true)
        }

        /**
         * Kept for backward compatibility; only matches explicit fenced code blocks (```...```)
         * so normal conversational text with punctuation or technical words is never blocked.
         */
        fun isCodeMessage(text: String): Boolean {
            val clean = text.trim()
            if (clean.isEmpty()) return false
            return clean.startsWith("```") && clean.endsWith("```") && clean.length > 6
        }

        internal fun splitTextIntoChunks(text: String, maxChunkLength: Int): List<String> {
            if (text.length <= maxChunkLength || maxChunkLength <= 0) return listOf(text)

            val chunks = mutableListOf<String>()
            var start = 0
            val length = text.length

            while (start < length) {
                val endLimit = (start + maxChunkLength).coerceAtMost(length)
                if (endLimit == length) {
                    chunks.add(text.substring(start, length))
                    break
                }

                // Prefer splitting cleanly at paragraph/line break, then sentence end, then whitespace
                var splitAt = -1
                val minAcceptableSplit = start + (maxChunkLength / 3)

                val lastNewline = text.lastIndexOf('\n', endLimit - 1)
                if (lastNewline >= minAcceptableSplit) {
                    splitAt = lastNewline + 1
                }

                if (splitAt == -1) {
                    for (i in (endLimit - 1) downTo minAcceptableSplit) {
                        val ch = text[i]
                        if ((ch == '.' || ch == '!' || ch == '?' || ch == '。' || ch == '！' || ch == '？') &&
                            (i + 1 >= length || text[i + 1].isWhitespace())
                        ) {
                            splitAt = i + 1
                            break
                        }
                    }
                }

                if (splitAt == -1) {
                    for (i in (endLimit - 1) downTo minAcceptableSplit) {
                        if (text[i].isWhitespace()) {
                            splitAt = i + 1
                            break
                        }
                    }
                }

                if (splitAt <= start) {
                    splitAt = endLimit
                }

                chunks.add(text.substring(start, splitAt))
                start = splitAt
            }

            return chunks.filter { it.isNotEmpty() }
        }

        internal fun parseGoogleGtxResponse(bodyStr: String): Pair<String?, String?> {
            if (bodyStr.isBlank() || bodyStr.trimStart().startsWith("<")) return null to null
            val root = JSONArray(bodyStr)
            val sentences = root.optJSONArray(0) ?: return null to null

            val sb = StringBuilder()
            for (i in 0 until sentences.length()) {
                val segment = sentences.optJSONArray(i) ?: continue
                val part = segment.optString(0, "")
                if (part.isNotEmpty() && !part.equals("null", ignoreCase = true)) {
                    sb.append(part)
                }
            }
            val translated = sb.toString().trim()
            val detectedLang = root.optString(2, "").trim().lowercase(Locale.ROOT)
            return (translated.ifBlank { null }) to (detectedLang.ifBlank { null })
        }

        internal fun parseGoogleDictResponse(bodyStr: String): Pair<String?, String?> {
            if (bodyStr.isBlank() || bodyStr.trimStart().startsWith("<")) return null to null
            val jsonArray = JSONArray(bodyStr)
            if (jsonArray.length() == 0) return null to null

            val sb = StringBuilder()
            var detectedLang: String? = null

            for (i in 0 until jsonArray.length()) {
                val itemArray = jsonArray.optJSONArray(i)
                if (itemArray != null && itemArray.length() > 0) {
                    val part = itemArray.optString(0, "")
                    if (part.isNotEmpty() && !part.equals("null", ignoreCase = true)) {
                        if (sb.isNotEmpty() && !sb.endsWith(" ") && !sb.endsWith("\n") && !part.startsWith(" ")) {
                            sb.append(' ')
                        }
                        sb.append(part)
                    }
                    if (detectedLang.isNullOrBlank() && itemArray.length() > 1) {
                        val langCandidate = itemArray.optString(1, "").trim().lowercase(Locale.ROOT)
                        if (langCandidate.isNotBlank() && langCandidate.length <= 10) {
                            detectedLang = langCandidate
                        }
                    }
                } else {
                    val strItem = jsonArray.optString(i, "")
                    if (strItem.isNotBlank() && !strItem.equals("null", ignoreCase = true)) {
                        if (sb.isNotEmpty() && !sb.endsWith(" ") && !sb.endsWith("\n")) {
                            sb.append(' ')
                        }
                        sb.append(strItem)
                    }
                }
            }

            val translated = sb.toString().trim()
            return (translated.ifBlank { null }) to detectedLang
        }

        internal fun decodeHtmlEntities(input: String): String {
            return input
                .replace("&quot;", "\"")
                .replace("&#34;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
        }

        internal fun parseMyMemoryResponse(bodyStr: String): String? {
            if (bodyStr.isBlank() || bodyStr.trimStart().startsWith("<")) return null
            val jsonObj = JSONObject(bodyStr)
            val responseData = jsonObj.optJSONObject("responseData") ?: return null
            val rawText = responseData.optString("translatedText", "")
            if (rawText.isBlank()) return null

            val decoded = decodeHtmlEntities(rawText).trim()
            val upper = decoded.uppercase(Locale.ROOT)
            if (upper.contains("MYMEMORY WARNING") ||
                upper.contains("QUERY LENGTH LIMIT") ||
                upper.contains("INVALID SOURCE") ||
                upper.contains("DISTINCT LANGUAGES") ||
                upper.contains("PLEASE SELECT")
            ) {
                return null
            }
            return decoded
        }

        private fun isInvalidTranslationText(translated: String): Boolean {
            val upper = translated.uppercase(Locale.ROOT)
            return upper.contains("DISTINCT LANGUAGES") ||
                upper.contains("PLEASE SELECT") ||
                upper.contains("MYMEMORY WARNING") ||
                upper.contains("QUERY LENGTH LIMIT")
        }
    }

    suspend fun translateText(
        text: String,
        targetLangCode: String = getDefaultSystemLanguageCode(),
        skipHeuristics: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return@withContext null

        if (!skipHeuristics && isStandaloneNonTranslatableLink(cleanText)) {
            return@withContext null
        }

        val cleanTarget = targetLangCode.trim().ifBlank { getDefaultSystemLanguageCode() }

        // Translate in chunks if very long so there is no character/URL length cap
        val googleChunks = splitTextIntoChunks(cleanText, GOOGLE_CHUNK_MAX_CHARS)
        val translatedGoogleParts = ArrayList<String>(googleChunks.size)
        var allChunksSucceeded = true
        var allDetectedSameLanguage = true

        for (chunk in googleChunks) {
            val result = translateChunkWithGoogle(chunk, cleanTarget)
            if (result == null) {
                allChunksSucceeded = false
                break
            }
            val (partTranslated, detectedLang) = result
            val sameBaseLang = detectedLang != null &&
                normalizeBaseLangCode(detectedLang) == normalizeBaseLangCode(cleanTarget)
            if (!sameBaseLang) {
                allDetectedSameLanguage = false
            }
            val trailingSep = when {
                chunk.endsWith("\n") -> "\n"
                chunk.lastOrNull()?.isWhitespace() == true -> " "
                else -> ""
            }
            translatedGoogleParts.add(partTranslated + trailingSep)
        }

        if (allChunksSucceeded && translatedGoogleParts.isNotEmpty()) {
            val combined = translatedGoogleParts.joinToString("") { it }.trim()
            if (combined.isNotBlank() && !isInvalidTranslationText(combined)) {
                if (!skipHeuristics && allDetectedSameLanguage && combined.equals(cleanText, ignoreCase = true)) {
                    return@withContext null
                }
                if (skipHeuristics || !combined.equals(cleanText, ignoreCase = true)) {
                    return@withContext combined
                }
                return@withContext null
            }
        }

        // Fallback to MyMemory (chunked at 420 chars per call, works for any target language including "en")
        val myMemorySource = if (normalizeBaseLangCode(cleanTarget) == "en") "id" else "en"
        val mmChunks = splitTextIntoChunks(cleanText, MYMEMORY_CHUNK_MAX_CHARS)
        val mmParts = ArrayList<String>(mmChunks.size)
        var mmAllSucceeded = true

        for (chunk in mmChunks) {
            val part = translateMyMemoryChunk(chunk, cleanTarget, myMemorySource)
            if (part.isNullOrBlank()) {
                mmAllSucceeded = false
                break
            }
            val trailingSep = when {
                chunk.endsWith("\n") -> "\n"
                chunk.lastOrNull()?.isWhitespace() == true -> " "
                else -> ""
            }
            mmParts.add(part + trailingSep)
        }

        if (mmAllSucceeded && mmParts.isNotEmpty()) {
            val combined = mmParts.joinToString("") { it }.trim()
            if (combined.isNotBlank() &&
                !isInvalidTranslationText(combined) &&
                (skipHeuristics || !combined.equals(cleanText, ignoreCase = true))
            ) {
                return@withContext combined
            }
        }

        return@withContext null
    }

    private fun translateChunkWithGoogle(chunk: String, targetLang: String): Pair<String, String?>? {
        // 1. Primary: Google Translate GTX endpoint via POST (supports full multi-sentence & long text without URL cap)
        val gtx = translateGoogleGtx(chunk, targetLang)
        if (gtx != null && !gtx.first.isNullOrBlank()) {
            return gtx.first!! to gtx.second
        }

        // 2. Secondary: Google Dict endpoint via POST
        val dict = translateGoogleDict(chunk, targetLang)
        if (dict != null && !dict.first.isNullOrBlank()) {
            return dict.first!! to dict.second
        }

        return null
    }

    private fun translateGoogleGtx(text: String, targetLang: String): Pair<String?, String?>? {
        return try {
            val encodedTarget = URLEncoder.encode(targetLang, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$encodedTarget&dt=t"
            val formBody = FormBody.Builder()
                .add("q", text)
                .build()
            val request = Request.Builder()
                .url(url)
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                parseGoogleGtxResponse(bodyStr)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google GTX translation error: ${e.message}")
            null
        }
    }

    private fun translateGoogleDict(text: String, targetLang: String): Pair<String?, String?>? {
        return try {
            val encodedTarget = URLEncoder.encode(targetLang, "UTF-8")
            val url = "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=auto&tl=$encodedTarget"
            val formBody = FormBody.Builder()
                .add("q", text)
                .build()
            val request = Request.Builder()
                .url(url)
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                parseGoogleDictResponse(bodyStr)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google Dict translation error: ${e.message}")
            null
        }
    }

    private fun translateMyMemoryChunk(text: String, targetLang: String, sourceLang: String): String? {
        return try {
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val encodedPair = URLEncoder.encode("$sourceLang|$targetLang", "UTF-8")
            val url = "https://api.mymemory.translated.net/get?q=$encodedText&langpair=$encodedPair"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; InChat)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                parseMyMemoryResponse(bodyStr)
            }
        } catch (e: Exception) {
            Log.w(TAG, "MyMemory translation error: ${e.message}")
            null
        }
    }
}
