package com.ndregil.inchat.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndregil.inchat.data.model.DeliveryStatus
import com.ndregil.inchat.data.model.MessageType
import com.ndregil.inchat.data.model.XmppMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BubbleColorPalette(
    val mineBg: Color,
    val mineText: Color,
    val otherBg: Color,
    val otherText: Color,
    val accentColor: Color
)

fun getBubblePalette(theme: String, isDark: Boolean): BubbleColorPalette {
    return when (theme.lowercase()) {
        "purple" -> BubbleColorPalette(
            mineBg = if (isDark) Color(0xFF7E22CE) else Color(0xFFF3E8FF),
            mineText = if (isDark) Color(0xFFFAF5FF) else Color(0xFF581C87),
            otherBg = if (isDark) Color(0xFF3B0764) else Color(0xFFFDF4FF),
            otherText = if (isDark) Color(0xFFF3E8FF) else Color(0xFF701A75),
            accentColor = Color(0xFFC084FC)
        )
        "green" -> BubbleColorPalette(
            mineBg = if (isDark) Color(0xFF059669) else Color(0xFFD1FAE5),
            mineText = if (isDark) Color(0xFFECFDF5) else Color(0xFF064E3B),
            otherBg = if (isDark) Color(0xFF064E3B) else Color(0xFFF0FDF4),
            otherText = if (isDark) Color(0xFFD1FAE5) else Color(0xFF14532D),
            accentColor = Color(0xFF34D399)
        )
        "orange" -> BubbleColorPalette(
            mineBg = if (isDark) Color(0xFFEA580C) else Color(0xFFFFEDD5),
            mineText = if (isDark) Color(0xFFFFF7ED) else Color(0xFF7C2D12),
            otherBg = if (isDark) Color(0xFF7C2D12) else Color(0xFFFFF7ED),
            otherText = if (isDark) Color(0xFFFFEDD5) else Color(0xFF9A3412),
            accentColor = Color(0xFFFB923C)
        )
        "pink" -> BubbleColorPalette(
            mineBg = if (isDark) Color(0xFFDB2777) else Color(0xFFFCE7F3),
            mineText = if (isDark) Color(0xFFFDF2F8) else Color(0xFF831843),
            otherBg = if (isDark) Color(0xFF831843) else Color(0xFFFDF2F8),
            otherText = if (isDark) Color(0xFFFCE7F3) else Color(0xFF9D174D),
            accentColor = Color(0xFFF472B6)
        )
        "blue" -> BubbleColorPalette(
            mineBg = if (isDark) Color(0xFF0284C7) else Color(0xFFE0F2FE),
            mineText = if (isDark) Color(0xFFF0F9FF) else Color(0xFF0C4A6E),
            otherBg = if (isDark) Color(0xFF0C4A6E) else Color(0xFFF0F9FF),
            otherText = if (isDark) Color(0xFFE0F2FE) else Color(0xFF075985),
            accentColor = Color(0xFF38BDF8)
        )
        "dregil-theme" -> BubbleColorPalette(
            mineBg = if (isDark) Color(0xFF4F46E5) else Color(0xFFE0E7FF),
            mineText = if (isDark) Color(0xFFEEF2FF) else Color(0xFF1E1B4B),
            otherBg = if (isDark) Color(0xFF1E1B4B) else Color(0xFFEEF2FF),
            otherText = if (isDark) Color(0xFFE0E7FF) else Color(0xFF312E81),
            accentColor = Color(0xFF818CF8)
        )
        else -> BubbleColorPalette(
            mineBg = if (isDark) Color(0xFF4F46E5) else Color(0xFFE0E7FF),
            mineText = if (isDark) Color(0xFFEEF2FF) else Color(0xFF1E1B4B),
            otherBg = if (isDark) Color(0xFF1E1B4B) else Color(0xFFEEF2FF),
            otherText = if (isDark) Color(0xFFE0E7FF) else Color(0xFF312E81),
            accentColor = Color(0xFF818CF8)
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: XmppMessage,
    myNickname: String = "",
    onSenderClick: (String) -> Unit = {},
    onReplyClick: (XmppMessage) -> Unit = {},
    onQuoteClick: (XmppMessage) -> Unit = {},
    onMentionClick: (String) -> Unit = {},
    onQuerySoftwareVersion: ((targetJid: String, onResult: (Result<com.ndregil.inchat.data.model.ClientCaps>) -> Unit) -> Unit)? = null,
    bubbleTheme: String = "dregil-theme",
    isDark: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    modifier: Modifier = Modifier
) {
    var showCapsDialog by remember { mutableStateOf(false) }
    var showActionMenu by remember { mutableStateOf(false) }

    if (message.messageType == MessageType.SYSTEM_JOIN ||
        message.messageType == MessageType.SYSTEM_LEAVE ||
        message.messageType == MessageType.SYSTEM_STATUS ||
        message.messageType == MessageType.SYSTEM_TOPIC
    ) {
        SystemMessagePill(message = message, onQuoteClick = onQuoteClick, modifier = modifier)
        return
    }

    val isMine = message.isMine
    val isWhisper = message.messageType == MessageType.WHISPER
    val senderColor = getSenderColor(message.senderNickname)
    val palette = remember(bubbleTheme, isDark) { getBubblePalette(bubbleTheme, isDark) }

    val bubbleBg = when {
        isWhisper && isMine -> Color(0xFF4C1D95)
        isWhisper && !isMine -> Color(0xFF581C87)
        isMine -> palette.mineBg
        else -> palette.otherBg
    }

    val textColor = when {
        isWhisper -> Color(0xFFF3E8FF)
        isMine -> palette.mineText
        else -> palette.otherText
    }

    val bubbleShape = if (isMine) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("message_bubble_${message.id}"),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // Incoming Avatar
        if (!isMine) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(senderColor.copy(alpha = 0.2f))
                    .border(1.dp, senderColor, CircleShape)
                    .combinedClickable(
                        onClick = { onReplyClick(message) },
                        onLongClick = { showCapsDialog = true }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (message.senderAvatar != null) {
                    coil.compose.AsyncImage(
                        model = message.senderAvatar,
                        contentDescription = "Avatar ${message.senderNickname}",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Text(
                        text = message.senderNickname.take(1).uppercase(Locale.getDefault()),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = senderColor
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            // Sender name & Client Caps
            if (!isMine || isWhisper) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 2.dp, start = if (!isMine) 4.dp else 0.dp)
                ) {
                    if (isWhisper) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Private Whisper",
                            tint = Color(0xFFC084FC),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (isMine) "Bisikan ke @${message.recipientNickname ?: "user"}" else "Bisikan dari ${message.senderNickname}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFC084FC)
                        )
                    } else {
                        Text(
                            text = message.senderNickname,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = senderColor,
                            modifier = Modifier.clickable {
                                onSenderClick(message.senderNickname)
                                onMentionClick(message.senderNickname)
                            }
                        )
                    }

                    ClientCapsIcon(
                        platform = message.senderClientCaps.platform,
                        clientCaps = message.senderClientCaps,
                        modifier = Modifier
                            .size(15.dp)
                            .clip(CircleShape)
                            .clickable { showCapsDialog = true }
                            .testTag("caps_icon_msg_${message.id}"),
                        contentDescription = "Caps: ${message.senderClientCaps.clientName}"
                    )
                }
            }

            // Message Bubble
            Box(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(bubbleBg)
                    .combinedClickable(
                        onClick = { onReplyClick(message) },
                        onLongClick = { onQuoteClick(message) }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column {
                    // Render Quoted/Reply Preview Header if present
                    if (!message.replyToSender.isNullOrBlank() || !message.replyToText.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(textColor.copy(alpha = 0.12f))
                                .border(
                                    BorderStroke(1.dp, palette.accentColor.copy(alpha = 0.4f)),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(28.dp)
                                        .background(palette.accentColor, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Membalas @${message.replyToSender ?: "user"}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.accentColor
                                    )
                                    Text(
                                        text = message.replyToText ?: "",
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = textColor.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }

                    // Main Content
                    Box(modifier = Modifier.padding(bottom = 2.dp)) {
                        when (message.messageType) {
                            MessageType.FILE -> {
                                FileMessageBubbleContent(message = message, textColor = textColor)
                            }
                            MessageType.VOICE -> {
                                VoiceMessageBubbleContent(message = message, textColor = textColor, isMine = isMine)
                            }
                            MessageType.CALL_SIGNALLING -> {
                                CallSignallingBubble(message = message, textColor = textColor)
                            }
                            else -> {
                                FormattedMessageText(
                                    text = message.body,
                                    myNickname = myNickname,
                                    textColor = textColor,
                                    accentColor = palette.accentColor
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Timestamp and Status
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(message.timestamp),
                            fontSize = 10.sp,
                            color = textColor.copy(alpha = 0.7f)
                        )
                        if (isMine) {
                            Spacer(modifier = Modifier.width(4.dp))
                            when (message.status) {
                                DeliveryStatus.SENDING -> Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Mengirim",
                                    tint = textColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(11.dp)
                                )
                                DeliveryStatus.SENT -> Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Terkirim",
                                    tint = textColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(11.dp)
                                )
                                DeliveryStatus.DELIVERED -> Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Sampai",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(12.dp)
                                )
                                DeliveryStatus.FAILED -> Text(
                                    text = "!",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Outgoing Avatar
        if (isMine) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(senderColor.copy(alpha = 0.2f))
                    .border(1.dp, senderColor, CircleShape)
                    .combinedClickable(
                        onClick = { onReplyClick(message) },
                        onLongClick = { showCapsDialog = true }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (message.senderAvatar != null) {
                    coil.compose.AsyncImage(
                        model = message.senderAvatar,
                        contentDescription = "Avatar ${message.senderNickname}",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Text(
                        text = message.senderNickname.take(1).uppercase(Locale.getDefault()),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = senderColor
                    )
                }
            }
        }
    }

    // Context Menu for Reply, Quote, Mention
    DropdownMenu(
        expanded = showActionMenu,
        onDismissRequest = { showActionMenu = false }
    ) {
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Reply, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Balas Pesan", fontSize = 13.sp)
                }
            },
            onClick = {
                showActionMenu = false
                onReplyClick(message)
            }
        )
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FormatQuote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Kutip Pesan", fontSize = 13.sp)
                }
            },
            onClick = {
                showActionMenu = false
                onQuoteClick(message)
            }
        )
        if (!isMine) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AlternateEmail, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mention @${message.senderNickname}", fontSize = 13.sp)
                    }
                },
                onClick = {
                    showActionMenu = false
                    onMentionClick(message.senderNickname)
                }
            )
        }
    }

    if (showCapsDialog) {
        ClientCapsDialog(
            clientCaps = message.senderClientCaps,
            userName = "${message.senderNickname} (${if (message.isMine) "Kamu" else message.senderJid.ifBlank { "User" }})",
            targetJid = message.senderJid,
            onQuerySoftwareVersion = onQuerySoftwareVersion,
            onDismiss = { showCapsDialog = false }
        )
    }
}

@Composable
fun FormattedMessageText(
    text: String,
    myNickname: String,
    textColor: Color,
    accentColor: Color
) {
    val annotatedString = buildAnnotatedString {
        val words = text.split(" ")
        words.forEachIndexed { index, word ->
            if (word.startsWith("@") && word.length > 1) {
                val cleanWord = word.trimEnd(',', '.', '!', '?')
                val isMe = myNickname.isNotBlank() && cleanWord.equals("@$myNickname", ignoreCase = true)
                val mentionColor = if (isMe) {
                    if (textColor.luminance() > 0.5f) Color(0xFFFACC15) else Color(0xFFCA8A04)
                } else {
                    accentColor
                }
                pushStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = mentionColor,
                        background = if (isMe) mentionColor.copy(alpha = 0.15f) else Color.Unspecified
                    )
                )
                append(word)
                pop()
            } else if (word.startsWith(">")) {
                pushStyle(
                    SpanStyle(
                        fontStyle = FontStyle.Italic,
                        color = textColor.copy(alpha = 0.8f)
                    )
                )
                append(word)
                pop()
            } else {
                append(word)
            }
            if (index < words.size - 1) append(" ")
        }
    }

    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodyMedium,
        color = textColor
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SystemMessagePill(
    message: XmppMessage,
    onQuoteClick: (XmppMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                .combinedClickable(
                    onClick = {},
                    onLongClick = { onQuoteClick(message) }
                )
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = message.body,
                style = MaterialTheme.typography.labelSmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CallSignallingBubble(message: XmppMessage, textColor: Color) {
    val isVideo = message.body.lowercase().contains("video")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(textColor.copy(alpha = 0.1f))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isVideo) Icons.Default.VideoCall else Icons.Default.Call,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = message.body,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = { /* Answer call */ }) {
            Text("Answer", color = textColor)
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun getSenderColor(nickname: String): Color {
    val palette = listOf(
        Color(0xFF0284C7),
        Color(0xFF0D9488),
        Color(0xFF8B5CF6),
        Color(0xFFD97706),
        Color(0xFFE11D48),
        Color(0xFF059669),
        Color(0xFF7C3AED),
        Color(0xFF2563EB)
    )
    val hash = kotlin.math.abs(nickname.hashCode())
    return palette[hash % palette.size]
}
