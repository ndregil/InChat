package com.ndregil.inchat.data.network

import android.util.Log
import com.ndregil.inchat.data.model.DeliveryStatus
import com.ndregil.inchat.data.model.MessageType
import com.ndregil.inchat.data.model.StanzaDirection
import com.ndregil.inchat.data.model.XmppMessage
import org.jivesoftware.smack.MessageListener
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smackx.delay.DelayInformationManager

import java.util.UUID

/**
 * High-level callback interface for listening to chat messages and MUC room events.
 */
interface XmppChatMessageCallback {
    /**
     * Invoked when a valid groupchat or whisper message is processed.
     */
    fun onMessageReceived(message: XmppMessage)

    /**
     * Invoked when a room subject / topic update stanza is detected.
     */
    fun onTopicChanged(roomJid: String, newTopic: String) {}

    /**
     * Invoked when raw stanza XML is received for debugging / stanza console.
     */
    fun onRawStanza(direction: StanzaDirection, rawXml: String) {}
}

/**
 * An implementation of Smack's [MessageListener] interface that hooks into
 * [MultiUserChatManager] (or individual [org.jivesoftware.smackx.muc.MultiUserChat] rooms)
 * to process incoming chat messages, extract timestamps, sender info, handle whispers,
 * and dispatch clean [XmppMessage] objects to update the UI state.
 *
 * Capabilities:
 * 1. Implements [org.jivesoftware.smack.MessageListener] via [processMessage].
 * 2. Handles XEP-0203 / XEP-0091 delayed delivery timestamps for historical messages replayed upon room join.
 * 3. Identifies whether messages are public room messages or private whispers (XEP-0045 7.5).
 * 4. Resolves the sender's occupant nickname from the MUC room JID resource part.
 * 5. Identifies whether the message was sent by the local user or another occupant.
 * 6. Processes MUC subject/topic broadcast stanzas.
 * 7. Dispatches the resulting [XmppMessage] to coroutine flows and UI state listeners.
 */
class MucMessageListener(
    val roomJid: String,
    private val myNicknameProvider: () -> String,
    private val onMessageProcessed: (XmppMessage) -> Unit,
    private val onTopicUpdated: ((roomJid: String, newTopic: String) -> Unit)? = null,
    private val onRawStanzaReceived: ((direction: StanzaDirection, rawXml: String) -> Unit)? = null
) : MessageListener {

    /**
     * Secondary constructor taking a fixed nickname string.
     */
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

    /**
     * Secondary constructor accepting an [XmppChatMessageCallback].
     */
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
            // Forward raw XML stanza if callback is registered
            try {
                val rawXml = message.toXML().toString()
                onRawStanzaReceived?.invoke(StanzaDirection.INCOMING, rawXml)
            } catch (e: Exception) {
                // Ignore XML rendering exceptions
            }

            // Check if this stanza represents a MUC topic/subject update
            val subject = message.subject
            if (!subject.isNullOrBlank() && message.body.isNullOrBlank()) {
                Log.i(TAG, "MUC [$roomJid] Subject change detected via MessageListener: '$subject'")
                onTopicUpdated?.invoke(roomJid, subject)
                return
            }

            // Check for encrypted message extensions (OMEMO XEP-0384, OpenPGP XEP-0373)
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

            // In MUC, the 'from' JID is typically 'room@conference.example.org/senderNickname'
            val fromJid = message.from?.toString() ?: roomJid
            val senderNickname = message.from?.resourceOrNull?.toString()
                ?: fromJid.substringAfterLast("/").ifBlank { fromJid.substringBefore("@") }

            // Determine if the message was sent by the local user
            val myNickname = myNicknameProvider()
            val isMine = senderNickname.equals(myNickname, ignoreCase = true)

            // Differentiate between private whispers (type="chat") and group messages (type="groupchat")
            // Differentiate between private whispers, voice notes, and group messages
            val isWhisper = message.type == Message.Type.chat
            
            // Detect image/file URLs in body (common in XEP-0363 HTTP File Upload)
            val bodyClean = body.trim()
            val isBodyImageUrl = (bodyClean.startsWith("http") && (
                    bodyClean.contains(".jpg", ignoreCase = true) ||
                    bodyClean.contains(".jpeg", ignoreCase = true) ||
                    bodyClean.contains(".png", ignoreCase = true) ||
                    bodyClean.contains(".gif", ignoreCase = true) ||
                    bodyClean.contains(".webp", ignoreCase = true) ||
                    bodyClean.contains("/preview/", ignoreCase = true)
            ))

            // XEP-0066 OOB Detection
            val xml = message.toXML().toString()
            val oobRegex = Regex("""<x[^>]*xmlns=['"]jabber:x:oob['"][^>]*>\s*<url>([^<]+)</url>""", RegexOption.IGNORE_CASE)
            val oobUrl = oobRegex.find(xml)?.groupValues?.get(1)?.trim()
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
                val durationMatch = Regex("\\((\\d+)d\\)").find(body)
                durationMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 5
            } else null

            // Extract true timestamp, handling delayed delivery (XEP-0203 / XEP-0091) for room history
            val delayInfo = DelayInformationManager.getDelayInformation(message)
            val timestamp = delayInfo?.stamp?.time ?: System.currentTimeMillis()

            // Unique stanza identifier
            val stanzaId = message.stanzaId?.ifBlank { null } ?: UUID.randomUUID().toString()

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

            Log.d(
                TAG,
                "Processed MUC Message in [$roomJid] from '$senderNickname' (isMine=$isMine, whisper=$isWhisper, time=$timestamp): $body"
            )

            // Dispatch to UI and repository listeners
            onMessageProcessed(xmppMessage)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing incoming MUC message stanza in room $roomJid: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "MucMessageListener"
    }
}
