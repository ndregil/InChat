package com.ndregil.inchat.ui.components

import android.annotation.SuppressLint
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndregil.inchat.data.model.DeliveryStatus
import com.ndregil.inchat.data.model.MessageType
import com.ndregil.inchat.data.model.XmppMessage
import com.ndregil.inchat.data.model.ThemePreferences
import com.ndregil.inchat.ui.theme.AwayAmber
import com.ndregil.inchat.ui.theme.DndRed
import com.ndregil.inchat.ui.theme.OfflineGray
import com.ndregil.inchat.ui.theme.OnlineGreen
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
    onWhisperClick: (XmppMessage) -> Unit = {},
    onMentionClick: (String) -> Unit = {},
    onViewProfile: ((XmppMessage) -> Unit)? = null,
    onQuerySoftwareVersion: ((targetJid: String, onResult: (Result<com.ndregil.inchat.data.model.ClientCaps>) -> Unit) -> Unit)? = null,
    bubbleTheme: String = "dregil-theme",
    isDark: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    themePrefs: ThemePreferences = ThemePreferences(),
    modifier: Modifier = Modifier
) {
    var showCapsDialog by remember { mutableStateOf(false) }
    var showAvatarMenu by remember { mutableStateOf(false) }
    var showVCardDialog by remember { mutableStateOf(false) }
    var showActionMenu by remember { mutableStateOf(false) }

    if (message.messageType == MessageType.SYSTEM_JOIN ||
        message.messageType == MessageType.SYSTEM_LEAVE ||
        message.messageType == MessageType.SYSTEM_STATUS ||
        message.messageType == MessageType.SYSTEM_TOPIC ||
        message.messageType == MessageType.MODERATION
    ) {
        SystemMessagePill(message = message, onQuoteClick = onQuoteClick, modifier = modifier)
        return
    }

    val isMine = message.isMine
    val isWhisper = message.messageType == MessageType.WHISPER
    val palette = remember(bubbleTheme, isDark) { getBubblePalette(bubbleTheme, isDark) }

    val senderColor = if (themePrefs.isCustomThemeEnabled) {
        try { Color(android.graphics.Color.parseColor(themePrefs.nicknameColorHex)) } catch (e: Exception) { getSenderColor(message.senderNickname) }
    } else {
        getSenderColor(message.senderNickname)
    }

    val bubbleBg = if (themePrefs.isCustomThemeEnabled) {
        try {
            when {
                isWhisper && isMine -> Color(0xFF4C1D95)
                isWhisper && !isMine -> Color(0xFF581C87)
                isMine -> Color(android.graphics.Color.parseColor(themePrefs.outgoingBubbleColorHex))
                else -> Color(android.graphics.Color.parseColor(themePrefs.incomingBubbleColorHex))
            }
        } catch (e: Exception) {
            when {
                isWhisper && isMine -> Color(0xFF4C1D95)
                isWhisper && !isMine -> Color(0xFF581C87)
                isMine -> palette.mineBg
                else -> palette.otherBg
            }
        }
    } else {
        when {
            isWhisper && isMine -> Color(0xFF4C1D95)
            isWhisper && !isMine -> Color(0xFF581C87)
            isMine -> palette.mineBg
            else -> palette.otherBg
        }
    }

    val textColor = if (themePrefs.isCustomThemeEnabled && !isWhisper) {
        try {
            if (isMine) Color(android.graphics.Color.parseColor(themePrefs.outgoingTextColorHex))
            else Color(android.graphics.Color.parseColor(themePrefs.incomingTextColorHex))
        } catch (e: Exception) {
            if (bubbleBg.luminance() > 0.5f) Color(0xFF1E293B) else Color.White
        }
    } else {
        when {
            isWhisper -> Color(0xFFF3E8FF)
            isMine -> palette.mineText
            else -> palette.otherText
        }
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
        if (!isMine) {
            IncomingAvatar(message, senderColor, { showAvatarMenu = true }, { showAvatarMenu = true })
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = if (isWhisper) 310.dp else 290.dp)
        ) {
            // Sender name & Client Caps
            if (!isMine || isWhisper) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 3.dp, start = if (!isMine) 4.dp else 0.dp, end = if (isMine) 4.dp else 0.dp)
                ) {
                    if (isWhisper) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF7C3AED).copy(alpha = 0.15f),
                            border = BorderStroke(0.8.dp, Color(0xFFA78BFA).copy(alpha = 0.4f)),
                            modifier = Modifier.padding(end = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Pesan Bisikan",
                                    tint = Color(0xFFA78BFA),
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = if (isMine) "Bisikan ke @${message.recipientNickname ?: "user"}" else "Bisikan dari ${message.senderNickname}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFA78BFA)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = message.senderNickname,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = senderColor,
                            modifier = Modifier.clickable {
                                showAvatarMenu = true
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

                    Spacer(modifier = Modifier.width(2.dp))

                    // Reply Button (Icon only)
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable { onReplyClick(message) }
                            .testTag("btn_reply_msg_${message.id}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Reply,
                                contentDescription = "Balas Pesan",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Quote Button (Icon only)
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable { onQuoteClick(message) }
                            .testTag("btn_quote_msg_${message.id}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.FormatQuote,
                                contentDescription = "Kutip Pesan",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    // Private Message / Whisper Button (Icon only, Chat icon instead of Lock)
                    if (!isMine && !isWhisper) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF8B5CF6).copy(alpha = 0.18f),
                            border = BorderStroke(0.6.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f)),
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .clickable { onWhisperClick(message) }
                                .testTag("btn_whisper_msg_${message.id}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Chat,
                                    contentDescription = "Pesan Pribadi",
                                    modifier = Modifier.size(11.dp),
                                    tint = Color(0xFFA855F7)
                                )
                            }
                        }
                    }
                }
            } else {
                // For own standard messages, show quick quote icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 2.dp, end = 4.dp)
                ) {
                    ClientCapsIcon(
                        platform = message.senderClientCaps.platform,
                        clientCaps = message.senderClientCaps,
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .clickable { showCapsDialog = true },
                        contentDescription = "Caps Saya"
                    )

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable { onQuoteClick(message) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.FormatQuote,
                                contentDescription = "Kutip Pesan Sendiri",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            // Message Bubble
            Box(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(bubbleBg)
                    .then(
                        if (isWhisper) {
                            Modifier.border(
                                BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.5f)),
                                bubbleShape
                            )
                        } else Modifier
                    )
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
                        SelectionContainer {
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
                                    if (message.body.contains("geo:") || message.body.contains("Berbagi Lokasi")) {
                                        LocationMessageBubbleContent(message = message, textColor = textColor)
                                    } else {
                                        FormattedMessageText(
                                            text = message.body,
                                            myNickname = myNickname,
                                            textColor = textColor,
                                            accentColor = palette.accentColor,
                                            onMentionClick = onMentionClick,
                                            onQuoteClick = { onReplyClick(message) }
                                        )
                                    }
                                }
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
                                    tint = textColor.copy(alpha = 0.5f),
                                    modifier = Modifier.size(13.dp)
                                )
                                DeliveryStatus.SENT -> Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Terkirim ke Server",
                                    tint = textColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(13.dp)
                                )
                                DeliveryStatus.DELIVERED -> Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Sampai ke Penerima",
                                    tint = textColor.copy(alpha = 0.85f),
                                    modifier = Modifier.size(14.dp)
                                )
                                DeliveryStatus.READ -> Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Telah Dibaca",
                                    tint = Color(0xFF3B82F6), // Blue double check
                                    modifier = Modifier.size(14.dp)
                                )
                                DeliveryStatus.FAILED -> Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Gagal",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isMine) {
            Spacer(modifier = Modifier.width(8.dp))
            OutgoingAvatar(message, senderColor, { showAvatarMenu = true }, { showAvatarMenu = true })
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

    if (showAvatarMenu) {
        AlertDialog(
            onDismissRequest = { showAvatarMenu = false },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = senderColor.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, senderColor),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
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
                                fontSize = 20.sp,
                                color = senderColor
                            )
                        }
                    }
                }
            },
            title = {
                Text(
                    text = message.senderNickname,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    if (!isMine) {
                        DropdownMenuItem(
                            text = { Text("Mention (@${message.senderNickname})") },
                            leadingIcon = { Icon(Icons.Default.AlternateEmail, null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                showAvatarMenu = false
                                onMentionClick(message.senderNickname)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Kirim Pesan Pribadi (Bisikan)") },
                            leadingIcon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                showAvatarMenu = false
                                onSenderClick(message.senderNickname)
                                onWhisperClick(message)
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Cek Versi Klien (XEP-0092)") },
                        leadingIcon = { Icon(Icons.Default.Code, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            showAvatarMenu = false
                            showCapsDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Info Profil Anggota") },
                        leadingIcon = { Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            showAvatarMenu = false
                            if (onViewProfile != null) {
                                onViewProfile(message)
                            } else {
                                showVCardDialog = true
                            }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAvatarMenu = false }) {
                    Text("Tutup")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showVCardDialog) {
        VCardProfileDialog(
            message = message,
            onDismiss = { showVCardDialog = false }
        )
    }
}

@Composable
fun FormattedMessageText(
    text: String,
    myNickname: String,
    textColor: Color,
    accentColor: Color,
    onMentionClick: ((String) -> Unit)? = null,
    onQuoteClick: (() -> Unit)? = null
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val linkColor = if (textColor.luminance() > 0.5f) Color(0xFF60A5FA) else Color(0xFF2563EB)

    val urlRegex = Regex("""(https?://[^\s]+|www\.[^\s]+)""", RegexOption.IGNORE_CASE)

    val annotatedString = buildAnnotatedString {
        val words = text.split(" ")
        words.forEachIndexed { index, word ->
            val match = urlRegex.matchEntire(word)
            if (match != null) {
                val rawUrl = match.value
                val cleanUrl = rawUrl.trimEnd(',', '.', '!', '?', ';', ':', ')', ']')
                val trailingPunct = rawUrl.substring(cleanUrl.length)
                val fullUrl = if (cleanUrl.startsWith("http://", ignoreCase = true) || cleanUrl.startsWith("https://", ignoreCase = true)) {
                    cleanUrl
                } else {
                    "https://$cleanUrl"
                }

                pushStringAnnotation(tag = "URL", annotation = fullUrl)
                pushStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Medium
                    )
                )
                append(cleanUrl)
                pop()
                pop()

                if (trailingPunct.isNotEmpty()) {
                    append(trailingPunct)
                }
            } else if (word.startsWith("@") && word.length > 1) {
                val cleanWord = word.trimEnd(',', '.', '!', '?', ';', ':', ')', ']')
                val trailingPunct = word.substring(cleanWord.length)
                val isMe = myNickname.isNotBlank() && cleanWord.equals("@$myNickname", ignoreCase = true)
                val mentionColor = if (isMe) {
                    if (textColor.luminance() > 0.5f) Color(0xFFFACC15) else Color(0xFFCA8A04)
                } else {
                    accentColor
                }

                pushStringAnnotation(tag = "MENTION", annotation = cleanWord.removePrefix("@"))
                pushStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = mentionColor,
                        background = if (isMe) mentionColor.copy(alpha = 0.15f) else Color.Unspecified
                    )
                )
                append(cleanWord)
                pop()
                pop()

                if (trailingPunct.isNotEmpty()) {
                    append(trailingPunct)
                }
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

    ClickableText(
        text = annotatedString,
        style = MaterialTheme.typography.bodyMedium.copy(color = textColor),
        onClick = { offset ->
            val annotations = annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
            if (annotations.isNotEmpty()) {
                val annotation = annotations.first()
                try {
                    uriHandler.openUri(annotation.item)
                } catch (e: Exception) {
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(annotation.item)).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (err: Exception) {
                        Toast.makeText(context, "Tidak dapat membuka URL: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                val mentionAnnotations = annotatedString.getStringAnnotations(tag = "MENTION", start = offset, end = offset)
                if (mentionAnnotations.isNotEmpty()) {
                    onMentionClick?.invoke(mentionAnnotations.first().item)
                } else {
                    onQuoteClick?.invoke()
                }
            }
        }
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SystemMessagePill(
    message: XmppMessage,
    onQuoteClick: (XmppMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    val cleanBody = message.body
        .removePrefix("• ")
        .removePrefix("→ ")
        .removePrefix("← ")
        .trim()

    val formattedDateTime = formatFullDateTime(message.timestamp)
    val fullText = "$formattedDateTime $cleanBody"

    val textColor = when (message.messageType) {
        MessageType.SYSTEM_JOIN -> OnlineGreen
        MessageType.SYSTEM_LEAVE -> Color(0xFFEA580C)
        MessageType.SYSTEM_STATUS -> {
            when {
                cleanBody.contains("[Away]", ignoreCase = true) || cleanBody.contains("[Extended Away]", ignoreCase = true) -> AwayAmber
                cleanBody.contains("[Jangan Ganggu", ignoreCase = true) || cleanBody.contains("[DND]", ignoreCase = true) -> DndRed
                cleanBody.contains("[Bebas Mengobrol]", ignoreCase = true) || cleanBody.contains("[Online]", ignoreCase = true) -> OnlineGreen
                cleanBody.contains("Offline", ignoreCase = true) -> OfflineGray
                cleanBody.contains("mengganti nickname", ignoreCase = true) -> Color(0xFF06B6D4)
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
            }
        }
        MessageType.MODERATION -> Color(0xFF8B5CF6)
        MessageType.SYSTEM_TOPIC -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 12.dp)
            .combinedClickable(
                onClick = {},
                onLongClick = { onQuoteClick(message) }
            )
            .testTag("system_msg_${message.id}"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = fullText,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            color = textColor,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

private fun formatFullDateTime(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault())
    return "(${sdf.format(java.util.Date(timestamp))})"
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

@Composable
fun LocationMessageBubbleContent(message: XmppMessage, textColor: Color) {
    val context = LocalContext.current
    val body = message.body

    // Extract lat/lng coordinates
    val (lat, lng) = remember(body) {
        val coordRegex = "([-0-9.]+),([-0-9.]+)".toRegex()
        val match = coordRegex.find(body)
        if (match != null && match.groupValues.size >= 3) {
            val latitude = match.groupValues[1].toDoubleOrNull()
            val longitude = match.groupValues[2].toDoubleOrNull()
            if (latitude != null && longitude != null) {
                Pair(latitude, longitude)
            } else Pair(-6.200000, 106.816666)
        } else Pair(-6.200000, 106.816666)
    }

    val displayTitle = remember(body) {
        val clean = body.substringBefore("http").substringBefore("geo:").replace("📍", "").trim()
        clean.ifBlank { "Lokasi Terkini" }
    }

    val openInMaps = {
        val labelEncoded = java.net.URLEncoder.encode(displayTitle, "UTF-8")
        val geoUrl = "geo:$lat,$lng?q=$lat,$lng($labelEncoded)"
        val webMapsUrl = "https://www.google.com/maps/search/?api=1&query=$lat,$lng"
        
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(geoUrl))
        val chooser = android.content.Intent.createChooser(intent, "Pilih Aplikasi Peta")
        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        
        try {
            context.startActivity(chooser)
        } catch (e: Exception) {
            try {
                val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(webMapsUrl))
                webIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(webIntent)
            } catch (e2: Exception) {
                Toast.makeText(context, "Tidak dapat membuka aplikasi peta", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { openInMaps() }
            .testTag("location_bubble_${message.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = "Lokasi",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = textColor
                )
                Text(
                    text = "Koordinat: %.5f, %.5f".format(Locale.getDefault(), lat, lng),
                    fontSize = 11.sp,
                    color = textColor.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Map Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.LightGray.copy(alpha = 0.3f))
        ) {
            StaticMapPreview(lat = lat, lng = lng)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFFEF4444).copy(alpha = 0.15f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { openInMaps() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Buka Peta (Google Maps)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun StaticMapPreview(lat: Double, lng: Double) {
    val htmlContent = remember(lat, lng) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" crossorigin="" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js" crossorigin=""></script>
            <style>
                html, body, #map { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background: #cbd5e1; }
                .leaflet-control-attribution { display: none !important; }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                try {
                    var map = L.map('map', {
                        center: [$lat, $lng],
                        zoom: 15,
                        zoomControl: false,
                        dragging: false,
                        touchZoom: false,
                        scrollWheelZoom: false,
                        doubleClickZoom: false,
                        boxZoom: false,
                        tap: false
                    });
                    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { crossOrigin: true }).addTo(map);
                    L.marker([$lat, $lng]).addTo(map);
                    setTimeout(function() { map.invalidateSize(); }, 300);
                } catch(e) { console.error(e); }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    var hasCrashed by remember { mutableStateOf(false) }

    if (hasCrashed) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Error, contentDescription = null, tint = Color.Gray)
        }
        return
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                // Use software layer for many small instances to reduce memory pressure
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                
                settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                
                webViewClient = object : WebViewClient() {
                    override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                        hasCrashed = true
                        return true
                    }
                }
                
                setOnTouchListener { _, _ -> true }
                loadDataWithBaseURL("https://www.openstreetmap.org", htmlContent, "text/html", "UTF-8", null)
            }
        },
        onRelease = { wv ->
            try {
                wv.stopLoading()
                wv.clearCache(true)
                wv.clearHistory()
                wv.destroy()
            } catch (_: Exception) {}
        },
        modifier = Modifier.fillMaxSize()
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun IncomingAvatar(
    message: XmppMessage,
    senderColor: Color,
    onClick: (XmppMessage) -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(senderColor.copy(alpha = 0.2f))
            .border(1.dp, senderColor, CircleShape)
            .combinedClickable(
                onClick = { onClick(message) },
                onLongClick = onLongClick
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OutgoingAvatar(
    message: XmppMessage,
    senderColor: Color,
    onClick: (XmppMessage) -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(senderColor.copy(alpha = 0.2f))
            .border(1.dp, senderColor, CircleShape)
            .combinedClickable(
                onClick = { onClick(message) },
                onLongClick = onLongClick
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

@Composable
fun VCardProfileDialog(
    message: XmppMessage,
    onDismiss: () -> Unit
) {
    val senderColor = getSenderColor(message.senderNickname)
    val avatarData = message.senderAvatar
    var showFullPhoto by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(senderColor.copy(alpha = 0.2f))
                        .border(2.dp, senderColor, CircleShape)
                        .clickable { if (avatarData != null) showFullPhoto = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarData != null) {
                        coil.compose.AsyncImage(
                            model = avatarData,
                            contentDescription = "Foto vCard ${message.senderNickname}",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = message.senderNickname.take(1).uppercase(Locale.getDefault()),
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            color = senderColor
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message.senderNickname,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Text(
                    text = "@${message.senderNickname}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("JID / Alamat XMPP:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(message.senderJid.ifBlank { "User" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )

    if (showFullPhoto && avatarData != null) {
        AlertDialog(
            onDismissRequest = { showFullPhoto = false },
            title = { Text("Foto vCard ${message.senderNickname}") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    coil.compose.AsyncImage(
                        model = avatarData,
                        contentDescription = "Foto vCard Full",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showFullPhoto = false }) {
                    Text("Tutup Foto")
                }
            }
        )
    }
}


