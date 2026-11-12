package com.ndregil.inchat.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

class TranslationService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    companion object {
        val SUPPORTED_LANGUAGES = listOf(
            TranslationLanguage("id", "Bahasa Indonesia", "Indonesia"),
            TranslationLanguage("en", "Inggris", "English"),
            TranslationLanguage("es", "Spanyol", "Español"),
            TranslationLanguage("fr", "Prancis", "Français"),
            TranslationLanguage("de", "Jerman", "Deutsch"),
            TranslationLanguage("ar", "Arab", "العربية"),
            TranslationLanguage("ja", "Jepang", "日本語"),
            TranslationLanguage("ko", "Korea", "한국어"),
            TranslationLanguage("zh", "China", "中文"),
            TranslationLanguage("ru", "Rusia", "Русский"),
            TranslationLanguage("jv", "Jawa", "Basa Jawa"),
            TranslationLanguage("su", "Sunda", "Basa Sunda")
        )

        fun getDefaultSystemLanguageCode(): String {
            val lang = Locale.getDefault().language.lowercase()
            return if (SUPPORTED_LANGUAGES.any { it.code.equals(lang, ignoreCase = true) }) lang else "id"
        }

        fun getLanguageName(code: String): String {
            return SUPPORTED_LANGUAGES.find { it.code.equals(code, ignoreCase = true) }?.displayName ?: code.uppercase()
        }
    }

    suspend fun translateText(text: String, targetLangCode: String = getDefaultSystemLanguageCode()): String? = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isEmpty() || cleanText.startsWith("http://") || cleanText.startsWith("https://") || cleanText.startsWith("geo:")) {
            return@withContext null
        }

        // 1. Try Google Dict Client Endpoint (Fast, autodetect source language)
        // returns translated text and detected language
        val googleDictResult = translateGoogleDict(cleanText, targetLangCode)
        if (!googleDictResult.isNullOrBlank()) {
            return@withContext googleDictResult
        }

        // If Google Dict failed, it might be that source == target. 
        // We cannot easily know source for MyMemory without detection,
        // but let's avoid it if we know the target is already 'en'.
        if (targetLangCode.equals("en", ignoreCase = true)) {
            return@withContext null
        }

        // 2. Fallback to MyMemory API with English as assumed source
        val myMemoryResult = translateMyMemory(cleanText, targetLangCode, "en")
        if (!myMemoryResult.isNullOrBlank() && !myMemoryResult.contains("DISTINCT LANGUAGES", ignoreCase = true)) {
            return@withContext myMemoryResult
        }

        return@withContext null
    }

    private fun translateGoogleDict(text: String, targetLang: String): String? {
        return try {
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val url = "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=auto&tl=$targetLang&q=$encodedText"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                if (bodyStr.startsWith("<html>")) return null

                val jsonArray = JSONArray(bodyStr)
                if (jsonArray.length() == 0) return null

                val firstItem = jsonArray.optJSONArray(0)
                val translated = if (firstItem != null && firstItem.length() > 0) {
                    firstItem.optString(0, "")
                } else if (jsonArray.optString(0, "").isNotBlank()) {
                    jsonArray.optString(0, "")
                } else null

                val detectedSourceLang = if (firstItem != null && firstItem.length() > 1) {
                    firstItem.optString(1, "").lowercase()
                } else ""

                // If message is already in target language (e.g. English message on an English device), skip translation
                if (detectedSourceLang.isNotBlank() && detectedSourceLang.equals(targetLang.lowercase(), ignoreCase = true)) {
                    return null
                }

                if (!translated.isNullOrBlank() && !translated.equals(text, ignoreCase = true)) {
                    translated
                } else null
            }
        } catch (e: Exception) {
            Log.w("TranslationService", "Google Dict translation error: ${e.message}")
            null
        }
    }

    private fun translateMyMemory(text: String, targetLang: String, sourceLang: String = "en"): String? {
        return try {
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val url = "https://api.mymemory.translated.net/get?q=$encodedText&langpair=$sourceLang|$targetLang"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; InChat)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                val jsonObj = JSONObject(bodyStr)
                val responseData = jsonObj.optJSONObject("responseData") ?: return null
                val translatedText = responseData.optString("translatedText", "")
                if (translatedText.isNotBlank() &&
                    !translatedText.equals(text, ignoreCase = true) &&
                    !translatedText.contains("MYMEMORY WARNING") &&
                    !translatedText.contains("INVALID SOURCE LANGUAGE")
                ) {
                    translatedText
                } else null
            }
        } catch (e: Exception) {
            Log.w("TranslationService", "MyMemory translation error: ${e.message}")
            null
        }
    }
}
