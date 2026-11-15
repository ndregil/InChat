package com.ndregil.inchat.data.network

import android.util.Log
import com.ndregil.inchat.data.model.DeliveryStatus
import com.ndregil.inchat.data.model.MessageType
import com.ndregil.inchat.data.model.StanzaDirection
import com.ndregil.inchat.data.model.XmppMessage
import org.jivesoftware.smack.MessageListener
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smackx.delay.DelayInformationManager

/**
 * High-level callback interface for listening to chat messages and MUC room events.
 */
interface XmppChatMessageCallback {
    fun onMessageReceived(message: XmppMessage)
    fun onTopicChanged(roomJid: String, newTopic: String) {}
    fun onRawStanza(direction: StanzaDirection, rawXml: String) {}
}

class MucMessageListener(
    val roomJid: String,
    private val myNicknameProvider: () -> String,
    private val onMessageProcessed: (XmppMessage) -> Unit,
    private val onTopicUpdated: ((roomJid: String, newTopic: String) -> Unit)? = null,
    private val onRawStanzaReceived: ((direction: StanzaDirection, rawXml: String) -> Unit)? = null
) : MessageListener {

    constructor(
        roomJid: String,
        myNickname: String,
        onMessageProcessed: (XmppMessage) -> Unit
    ) : this(
        roomJid = roomJid,
        myNicknameProvider = { myNickname },
        onMessageProcessed = onMessageProcessed,
        onTopicUpdated = null,
        onRawStanzaReceived = null
    )

    constructor(
        roomJid: String,
        myNicknameProvider: () -> String,
        callback: XmppChatMessageCallback
    ) : this(
        roomJid = roomJid,
        myNicknameProvider = myNicknameProvider,
        onMessageProcessed = { callback.onMessageReceived(it) },
        onTopicUpdated = { jid, topic -> callback.onTopicChanged(jid, topic) },
        onRawStanzaReceived = { dir, xml -> callback.onRawStanza(dir, xml) }
    )

    override fun processMessage(message: Message) {
        try {
            if (onRawStanzaReceived != null) {
                try {
                    val rawXml = message.toXML().toString()
                    onRawStanzaReceived.invoke(StanzaDirection.INCOMING, rawXml)
                } catch (_: Exception) {}
            }

            val subject = message.subject
            if (!subject.isNullOrBlank() && message.body.isNullOrBlank()) {
                onTopicUpdated?.invoke(roomJid, subject)
                return
            }

            val hasOmemo = message.hasExtension("encrypted", "urn:xmpp:omemo:2") ||
                    message.hasExtension("encrypted", "eu.siacs.conversations.axolotl")
            val hasOpenPgp = message.hasExtension("openpgp", "urn:xmpp:openpgp:0")

            var body = message.body
            if (body.isNullOrBlank()) {
                if (hasOmemo) {
                    body = "🔒 [Pesan Terenkripsi OMEMO]"
                } else if (hasOpenPgp) {
                    body = "🔒 [Pesan Terenkripsi OpenPGP]"
                } else {
                    return
                }
            }

            val fromJid = message.from?.toString() ?: roomJid
            val senderNickname = message.from?.resourceOrNull?.toString()
                ?: fromJid.substringAfterLast("/").ifBlank { fromJid.substringBefore("@") }

            val myNickname = myNicknameProvider()
            val isMine = senderNickname.equals(myNickname, ignoreCase = true)
            val isWhisper = message.type == Message.Type.chat

            val bodyClean = body.trim()
            val isBodyImageUrl = (bodyClean.startsWith("http") && (
                    bodyClean.contains(".jpg", ignoreCase = true) ||
                    bodyClean.contains(".jpeg", ignoreCase = true) ||
                    bodyClean.contains(".png", ignoreCase = true) ||
                    bodyClean.contains(".gif", ignoreCase = true) ||
                    bodyClean.contains(".webp", ignoreCase = true) ||
                    bodyClean.contains("/preview/", ignoreCase = true)
            ))

            val xml = if (onRawStanzaReceived != null || message.hasExtension("x", "jabber:x:oob")) {
                try { message.toXML().toString() } catch (_: Exception) { "" }
            } else ""
            val oobUrl = if (xml.isNotEmpty()) OOB_REGEX.find(xml)?.groupValues?.get(1)?.trim() else null
            val isOobImage = (oobUrl != null && (
                    oobUrl.contains(".jpg", ignoreCase = true) ||
                    oobUrl.contains(".jpeg", ignoreCase = true) ||
                    oobUrl.contains(".png", ignoreCase = true) ||
                    oobUrl.contains(".gif", ignoreCase = true) ||
                    oobUrl.contains(".webp", ignoreCase = true)
            ))

            val isImage = isOobImage || isBodyImageUrl
            val isVoice = body.startsWith("🎤 Pesan Suara") || 
                (body.contains("http") && (body.contains(".m4a") || body.contains(".aac") || body.contains(".mp3") || body.contains(".ogg") || body.contains(".wav") || body.contains("voice_")))
            val isFile = isImage || oobUrl != null

            val messageType = when {
                isVoice -> MessageType.VOICE
                isFile -> MessageType.FILE
                isWhisper -> MessageType.WHISPER
                else -> MessageType.GROUPCHAT
            }

            val fileUrl = oobUrl ?: if (isVoice || isBodyImageUrl) {
                body.lines().firstOrNull { it.startsWith("http") || it.startsWith("file://") } 
                    ?: body.substringAfter("\n", "").takeIf { it.startsWith("http") || it.startsWith("file://") }
                    ?: body.trim().takeIf { it.startsWith("http") }
            } else null

            val voiceDuration = if (isVoice) {
                val durationMatch = VOICE_DURATION_REGEX.find(body)
                durationMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 5
            } else null

            val delayInfo = DelayInformationManager.getDelayInformation(message)
            val timestamp = delayInfo?.stamp?.time ?: System.currentTimeMillis()

            val stanzaId = message.stanzaId?.ifBlank { null } ?: "${roomJid}_${timestamp}_${senderNickname.hashCode()}_${body.hashCode()}"

            val xmppMessage = XmppMessage(
                id = stanzaId,
                roomJid = roomJid,
                senderJid = fromJid,
                senderNickname = senderNickname,
                body = body,
                timestamp = timestamp,
                isMine = isMine,
                messageType = messageType,
                status = DeliveryStatus.DELIVERED,
                recipientNickname = if (isWhisper) myNickname else null,
                fileUrl = fileUrl,
                fileMimeType = when {
                    isImage -> "image/jpeg"
                    isVoice -> "audio/mp4"
                    else -> null
                },
                fileName = if (isFile) "Lampiran" else null,
                voiceDurationSeconds = voiceDuration,
                isHistory = delayInfo != null
            )

            onMessageProcessed(xmppMessage)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing incoming MUC message stanza in room $roomJid: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "MucMessageListener"
        private val OOB_REGEX = Regex("""<x[^>]*xmlns=['"]jabber:x:oob['"][^>]*>\s*<url>([^<]+)</url>""", RegexOption.IGNORE_CASE)
        private val VOICE_DURATION_REGEX = Regex("\\((\\d+)d\\)")
    }
}
