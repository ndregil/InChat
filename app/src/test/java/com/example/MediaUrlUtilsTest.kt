package com.ndregil.inchat

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.ndregil.inchat.util.MediaUrlUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaUrlUtilsTest {

    @Test
    fun `extractUrls handles multiline parenthesized www uppercase and bare domains while skipping JIDs`() {
        val text = """
            Topik ruang baru:
            Info (https://example.com/rules), lihat juga https://en.wikipedia.org/wiki/Jabber_(protocol).
            Portal: www.narayana.im/chat atau HTTP://CDN.EXAMPLE.ORG/img.AVIF
            JID user@conference.jabber.org tidak boleh jadi URL, tapi dev.narayana.im/app boleh!
        """.trimIndent()

        val urls = MediaUrlUtils.extractUrls(text)
        assertTrue(urls.contains("https://example.com/rules"))
        assertTrue(urls.contains("https://en.wikipedia.org/wiki/Jabber_(protocol)"))
        assertTrue(urls.contains("https://www.narayana.im/chat"))
        assertTrue(urls.contains("http://CDN.EXAMPLE.ORG/img.AVIF"))
        assertTrue(urls.contains("https://dev.narayana.im/app"))
        assertFalse(urls.any { it.contains("conference.jabber.org") })
    }

    @Test
    fun `isKnownImageUrl and shouldAttemptImagePreview support all image extensions and unknown extensions`() {
        val imageUrls = listOf(
            "https://example.com/a.jpg",
            "https://example.com/a.jpeg",
            "https://example.com/a.PNG",
            "https://example.com/a.gif",
            "https://example.com/a.webp",
            "https://example.com/a.bmp",
            "https://example.com/a.svg",
            "https://example.com/a.svgz",
            "https://example.com/a.heic",
            "https://example.com/a.heif",
            "https://example.com/a.avif",
            "https://example.com/a.tiff",
            "https://example.com/a.tif",
            "https://example.com/a.ico",
            "https://example.com/a.jfif",
            "https://example.com/a.apng",
            "https://example.com/a.jxl",
            "https://pbs.twimg.com/media/abc123?format=webp&name=large",
            "https://example.com/preview/12345"
        )
        imageUrls.forEach { url ->
            assertTrue("Expected known image: $url", MediaUrlUtils.isKnownImageUrl(url))
            assertTrue("Expected attempt preview: $url", MediaUrlUtils.shouldAttemptImagePreview(url))
        }

        // Arbitrary/custom/extensionless image URLs must still be attempted for preview
        val unknownExtUrls = listOf(
            "https://picsum.photos/400/300",
            "https://example.com/image.php?id=42",
            "https://cdn.example.org/file.customext",
            "https://example.com/media/rawblob"
        )
        unknownExtUrls.forEach { url ->
            assertTrue("Expected shouldAttemptImagePreview=true for $url", MediaUrlUtils.shouldAttemptImagePreview(url))
        }

        // Explicit non-image media/docs must not attempt image preview
        val nonImageUrls = listOf(
            "https://example.com/song.mp3",
            "https://example.com/video.mp4",
            "https://example.com/doc.pdf",
            "https://example.com/archive.zip",
            "https://example.com/app.apk"
        )
        nonImageUrls.forEach { url ->
            assertTrue("Expected non-image: $url", MediaUrlUtils.isDefinitelyNonImageUrl(url))
            assertFalse("Expected shouldAttemptImagePreview=false for $url", MediaUrlUtils.shouldAttemptImagePreview(url))
        }
    }

    @Test
    fun `openExternalUrl launches ACTION_VIEW intent with normalized https url`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val opened = MediaUrlUtils.openExternalUrl(
            context = app,
            uriHandler = null,
            rawUrl = "www.example.com/topic",
            errorToastPrefix = "Failed:"
        )
        assertTrue(opened)
        val started = Shadows.shadowOf(app).nextStartedActivity
        assertNotNull(started)
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://www.example.com/topic", started.dataString)
        assertTrue(started.hasCategory(Intent.CATEGORY_BROWSABLE))
    }
}
