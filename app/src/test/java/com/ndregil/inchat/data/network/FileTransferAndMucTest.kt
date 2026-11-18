package com.ndregil.inchat.data.network

import com.ndregil.inchat.data.model.MessageType
import com.ndregil.inchat.data.model.XmppMessage
import com.ndregil.inchat.util.MediaUrlUtils
import org.jivesoftware.smack.packet.MessageBuilder
import org.jivesoftware.smack.packet.StandardExtensionElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.jxmpp.jid.impl.JidCreate

class FileTransferAndMucTest {

    @Test
    fun sanitizeFileName_preservesArbitraryExtensionsAndCleansUnsafeChars() {
        assertEquals("laporan_akhir.pdf", FileTransferManager.sanitizeFileName("folder/laporan_akhir.pdf"))
        assertEquals("firmware_v2.bin", FileTransferManager.sanitizeFileName("firmware_v2.bin"))
        assertEquals("archive.tar.gz", FileTransferManager.sanitizeFileName("archive.tar.gz"))
        assertEquals("custom_data.xyz123", FileTransferManager.sanitizeFileName("custom_data.xyz123"))
        assertEquals("foto__1_.jpg", FileTransferManager.sanitizeFileName("foto?#1*.jpg"))
    }

    @Test
    fun normalizeMimeType_supportsAnyExtensionWithOctetStreamFallback() {
        assertEquals(
            "application/octet-stream",
            FileTransferManager.normalizeMimeType("unknown_file.xyz99", null)
        )
        assertEquals(
            "application/octet-stream",
            FileTransferManager.normalizeMimeType("rawfile", "*/*")
        )
        assertEquals(
            "image/png",
            FileTransferManager.normalizeMimeType("pic.png", "image/png")
        )
    }

    @Test
    fun mucListener_preservesStanzaIdDescriptionAndFileNameForReflectedImage() {
        var captured: XmppMessage? = null
        val listener = MucMessageListener(
            roomJid = "room@conference.example.com",
            myNicknameProvider = { "andi" },
            onMessageProcessed = { captured = it }
        )

        val stanza = MessageBuilder.buildMessage("file_test_id_123")
            .from(JidCreate.entityFullFrom("room@conference.example.com/andi"))
            .ofType(org.jivesoftware.smack.packet.Message.Type.groupchat)
            .setBody("Ini foto dokumentasi\nhttps://upload.example.com/abc/foto_kegiatan.jpg")
            .addExtension(
                StandardExtensionElement.builder("x", "jabber:x:oob")
                    .addElement("url", "https://upload.example.com/abc/foto_kegiatan.jpg")
                    .build()
            )
            .build()

        listener.processMessage(stanza)

        val msg = captured
        assertNotNull(msg)
        assertEquals("file_test_id_123", msg!!.id)
        assertTrue(msg.isMine)
        assertEquals(MessageType.FILE, msg.messageType)
        assertEquals("https://upload.example.com/abc/foto_kegiatan.jpg", msg.fileUrl)
        assertEquals("foto_kegiatan.jpg", msg.fileName)
        assertEquals("Ini foto dokumentasi\nhttps://upload.example.com/abc/foto_kegiatan.jpg", msg.body)
    }

    @Test
    fun mucListener_handlesOobOnlyFileWithCustomExtension() {
        var captured: XmppMessage? = null
        val listener = MucMessageListener(
            roomJid = "room@conference.example.com",
            myNicknameProvider = { "andi" },
            onMessageProcessed = { captured = it }
        )

        val stanza = MessageBuilder.buildMessage("oob_custom_1")
            .from(JidCreate.entityFullFrom("room@conference.example.com/budi"))
            .ofType(org.jivesoftware.smack.packet.Message.Type.groupchat)
            .addExtension(
                StandardExtensionElement.builder("x", "jabber:x:oob")
                    .addElement("url", "https://upload.example.com/files/backup_db.sqlite")
                    .build()
            )
            .build()

        listener.processMessage(stanza)

        val msg = captured
        assertNotNull(msg)
        assertFalse(msg!!.isMine)
        assertEquals(MessageType.FILE, msg.messageType)
        assertEquals("https://upload.example.com/files/backup_db.sqlite", msg.fileUrl)
        assertEquals("backup_db.sqlite", msg.fileName)
    }

    @Test
    fun mediaUrlUtils_extractsUrlFromCaptionBodyAndFiltersBinaryExtensionsFromImagePreview() {
        val body = "Tolong cek berkas konfigurasi ini\nhttps://files.example.org/app/config.yaml"
        val urls = MediaUrlUtils.extractUrls(body)
        assertEquals(listOf("https://files.example.org/app/config.yaml"), urls)
        assertFalse(MediaUrlUtils.shouldAttemptImagePreview("https://files.example.org/app/config.yaml"))
        assertTrue(MediaUrlUtils.shouldAttemptImagePreview("https://cdn.example.org/media/12345?format=png"))
    }
}
