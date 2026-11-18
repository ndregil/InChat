package com.ndregil.inchat.data.network

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TranslationServiceTest {

    @Test
    fun `parseGoogleGtxResponse joins all sentence segments`() {
        val json = """
            [
              [
                ["Hello world. ", "Halo dunia. ", null, null, 1],
                ["How are you today?", "Apa kabar hari ini?", null, null, 1]
              ],
              null,
              "id"
            ]
        """.trimIndent()

        val (translated, detectedLang) = TranslationService.parseGoogleGtxResponse(json)
        assertEquals("Hello world. How are you today?", translated)
        assertEquals("id", detectedLang)
    }

    @Test
    fun `parseGoogleDictResponse joins multiple segments`() {
        val json = """
            [
              ["Selamat pagi.", "id"],
              ["Semoga harimu menyenangkan.", "id"]
            ]
        """.trimIndent()

        val (translated, detectedLang) = TranslationService.parseGoogleDictResponse(json)
        assertEquals("Selamat pagi. Semoga harimu menyenangkan.", translated)
        assertEquals("id", detectedLang)
    }

    @Test
    fun `parseMyMemoryResponse decodes html entities and rejects warnings`() {
        val validJson = """
            {"responseData":{"translatedText":"It&#39;s &quot;good&quot; &amp; fast"}}
        """.trimIndent()
        assertEquals("It's \"good\" & fast", TranslationService.parseMyMemoryResponse(validJson))

        val warningJson = """
            {"responseData":{"translatedText":"MYMEMORY WARNING: YOU USED ALL AVAILABLE FREE TRANSLATIONS"}}
        """.trimIndent()
        assertNull(TranslationService.parseMyMemoryResponse(warningJson))
    }

    @Test
    fun `splitTextIntoChunks handles very long text without losing characters`() {
        val paragraph = "Ini adalah kalimat panjang untuk menguji penerjemahan pesan tanpa batasan panjang karakter. "
        val longText = buildString {
            repeat(80) { idx ->
                append(paragraph)
                if (idx % 10 == 9) append("\n")
            }
        }.trim()

        assertTrue(longText.length > 5000)
        val chunks = TranslationService.splitTextIntoChunks(longText, 1800)
        assertTrue(chunks.size >= 3)
        assertTrue(chunks.all { it.length <= 1800 })
        assertEquals(longText, chunks.joinToString(""))
    }

    @Test
    fun `translateText handles very long message via chunked POST without truncation`() = runBlocking {
        val capturedBodies = mutableListOf<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val buf = Buffer()
                req.body?.writeTo(buf)
                capturedBodies.add(buf.readUtf8())
                val json = """[[["Translated chunk", "orig", null, null, 1]], null, "id"]"""
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(json.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val service = TranslationService(client)
        val longMessage = (1..300).joinToString(" ") { "word$it" }
        assertTrue(longMessage.length > 2000)

        val result = service.translateText(longMessage, "en")
        assertNotNull(result)
        assertTrue(capturedBodies.size >= 2)
        assertTrue(result!!.contains("Translated chunk"))
    }

    @Test
    fun `translateText allows English target fallback to MyMemory when Google endpoints fail`() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                requestedUrls.add(url)
                if (url.contains("mymemory.translated.net")) {
                    val body = """{"responseData":{"translatedText":"Good morning everyone"}}"""
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(503)
                        .message("Service Unavailable")
                        .body("error".toResponseBody("text/plain".toMediaType()))
                        .build()
                }
            })
            .build()

        val service = TranslationService(client)
        val translated = service.translateText("Selamat pagi semua", "en")

        assertEquals("Good morning everyone", translated)
        assertTrue(requestedUrls.any { it.contains("mymemory.translated.net") })
    }

    @Test
    fun `translateText does not block punctuation, multi-line text, or URLs with text`() = runBlocking {
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val json = """[[["Ok translated", "orig", null, null, 1]], null, "en"]"""
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(json.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val service = TranslationService(client)

        // Previously falsely rejected by isCodeMessage or startsWith("http")
        val trickyMessages = listOf(
            "Let us go!\n-> hurry up!! (now)",
            "https://example.com check out this article!",
            "Select option (1) & confirm == true\nPlease reply!!"
        )
        for (msg in trickyMessages) {
            assertEquals("Ok translated", service.translateText(msg, "id", skipHeuristics = false))
        }

        // Pure standalone URL is skipped in auto mode, but not blocked when skipHeuristics=true
        assertNull(service.translateText("https://example.com/image.png", "id", skipHeuristics = false))
        assertEquals("Ok translated", service.translateText("https://example.com/image.png", "id", skipHeuristics = true))
    }

    @Test
    fun `supported languages is expanded and supports custom ISO lookup`() {
        assertTrue(TranslationService.SUPPORTED_LANGUAGES.size >= 40)
        assertFalse(TranslationService.getLanguageName("pt").equals("pt", ignoreCase = true))
        assertEquals("XY", TranslationService.getLanguageName("xy"))
    }
}
