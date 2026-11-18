package com.ndregil.inchat.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndregil.inchat.data.model.MessageType
import com.ndregil.inchat.data.model.RoomOccupant
import com.ndregil.inchat.data.model.XmppMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Floating translucent air-bubble ("Gelembung Udara") overlay for Room private whispers.
 * Collapses into a draggable iridescent air bubble so users can keep chatting in the main room,
 * and expands into a floating glass mini-chat card when tapped.
 */
@Composable
fun WhisperFloatingBubbleOverlay(
    targetOccupant: RoomOccupant?,
    allWhisperMessages: List<XmppMessage>,
    availableOccupants: List<RoomOccupant>,
    unreadCount: Int,
    isExpanded: Boolean,
    onExpand: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    onSelectPartner: (RoomOccupant) -> Unit,
    onSendWhisper: (targetNickname: String, text: String) -> Unit,
    onTranslateClick: ((XmppMessage) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (targetOccupant == null) return

    BackHandler(enabled = isExpanded) {
        onMinimize()
    }

    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current

    // Gather all whisper partners present in this room + active target
    val whisperPartners = remember(allWhisperMessages, targetOccupant, availableOccupants) {
        val partnerNames = linkedSetOf<String>()
        partnerNames.add(targetOccupant.nickname)
        allWhisperMessages.forEach { msg ->
            if (msg.messageType == MessageType.WHISPER) {
                val peer = if (msg.isMine) msg.recipientNickname else msg.senderNickname
                if (!peer.isNullOrBlank()) partnerNames.add(peer)
            }
        }
        partnerNames.map { nick ->
            if (nick == targetOccupant.nickname) targetOccupant
            else availableOccupants.find { it.nickname == nick } ?: RoomOccupant(nickname = nick)
        }
    }

    val partnerMessages = remember(allWhisperMessages, targetOccupant.nickname) {
        allWhisperMessages.filter { msg ->
            msg.messageType == MessageType.WHISPER && (
                (!msg.isMine && msg.senderNickname.equals(targetOccupant.nickname, ignoreCase = true)) ||
                    (msg.isMine && msg.recipientNickname.equals(targetOccupant.nickname, ignoreCase = true))
                )
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val bubbleSizeDp = 64.dp
        val bubbleSizePx = with(density) { bubbleSizeDp.toPx() }
        val edgeMarginPx = with(density) { 12.dp.toPx() }

        var offsetX by remember(maxWidth) {
            mutableFloatStateOf((containerWidthPx - bubbleSizePx - edgeMarginPx).coerceAtLeast(edgeMarginPx))
        }
        var offsetY by remember(maxHeight) {
            mutableFloatStateOf(with(density) { 84.dp.toPx() }.coerceIn(edgeMarginPx, (containerHeightPx - bubbleSizePx - edgeMarginPx).coerceAtLeast(edgeMarginPx)))
        }

        // Expanded Floating Glass Whisper Card
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.88f, animationSpec = tween(220)),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.88f, animationSpec = tween(180)),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            WhisperExpandedCard(
                activePartner = targetOccupant,
                partners = whisperPartners,
                messages = partnerMessages,
                isDark = isDark,
                onSelectPartner = onSelectPartner,
                onMinimize = onMinimize,
                onClose = onClose,
                onSendWhisper = { text -> onSendWhisper(targetOccupant.nickname, text) },
                onTranslateClick = onTranslateClick
            )
        }

        // Collapsed Draggable Air Bubble ("Gelembung Udara")
        AnimatedVisibility(
            visible = !isExpanded,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.7f),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.7f),
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .pointerInput(containerWidthPx, containerHeightPx) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val maxX = (containerWidthPx - bubbleSizePx - edgeMarginPx).coerceAtLeast(edgeMarginPx)
                        val maxY = (containerHeightPx - bubbleSizePx - edgeMarginPx).coerceAtLeast(edgeMarginPx)
                        offsetX = (offsetX + dragAmount.x).coerceIn(edgeMarginPx, maxX)
                        offsetY = (offsetY + dragAmount.y).coerceIn(edgeMarginPx, maxY)
                    }
                }
        ) {
            AirBubbleChatHead(
                nickname = targetOccupant.nickname,
                unreadCount = unreadCount,
                onClick = onExpand,
                onClose = onClose
            )
        }
    }
}

@Composable
private fun AirBubbleChatHead(
    nickname: String,
    unreadCount: Int,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    val strings = com.ndregil.inchat.ui.i18n.LocalAppStrings.current
    val infiniteTransition = rememberInfiniteTransition(label = "air_bubble_float")
    val floatOffsetDp by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_y"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_alpha"
    )

    Box(
        modifier = Modifier
            .offset(y = floatOffsetDp.dp)
            .size(72.dp),
        contentAlignment = Alignment.Center
    ) {
        // Iridescent spherical air bubble body
        Box(
            modifier = Modifier
                .size(62.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = CircleShape,
                    ambientColor = Color(0xFF38BDF8),
                    spotColor = Color(0xFFA855F7)
                )
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .testTag("whisper_floating_bubble"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                // Translucent iridescent air-bubble sphere
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFECFEFF).copy(alpha = 0.42f),
                            Color(0xFF38BDF8).copy(alpha = 0.48f),
                            Color(0xFF8B5CF6).copy(alpha = 0.72f),
                            Color(0xFF6D28D9).copy(alpha = 0.88f)
                        ),
                        center = Offset(size.width * 0.32f, size.height * 0.28f),
                        radius = radius * 1.35f
                    ),
                    radius = radius,
                    center = center
                )

                // Outer soap/air bubble shimmer rim
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.92f),
                            Color(0xFF67E8F9).copy(alpha = if (unreadCount > 0) glowAlpha else 0.7f),
                            Color(0xFFC084FC).copy(alpha = 0.85f)
                        )
                    ),
                    radius = radius - 1.5.dp.toPx(),
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Glossy top-left specular air-bubble highlight
                drawCircle(
                    color = Color.White.copy(alpha = 0.72f),
                    radius = radius * 0.19f,
                    center = Offset(size.width * 0.30f, size.height * 0.26f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.45f),
                    radius = radius * 0.08f,
                    center = Offset(size.width * 0.22f, size.height * 0.42f)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = nickname.take(2).uppercase(Locale.getDefault()),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = strings.whisper,
                        tint = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.size(9.dp)
                    )
                    Text(
                        text = strings.whisper,
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Dismiss "x" mini pill at top-left
        Surface(
            shape = CircleShape,
            color = Color(0xFF1E293B).copy(alpha = 0.88f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(22.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose)
                .testTag("whisper_bubble_dismiss_mini")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = strings.closeWhisperBubbleDesc,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        // Unread count badge at top-right
        if (unreadCount > 0) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFEC4899),
                border = BorderStroke(1.5.dp, Color.White),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun WhisperExpandedCard(
    activePartner: RoomOccupant,
    partners: List<RoomOccupant>,
    messages: List<XmppMessage>,
    isDark: Boolean,
    onSelectPartner: (RoomOccupant) -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    onSendWhisper: (String) -> Unit,
    onTranslateClick: ((XmppMessage) -> Unit)? = null
) {
    val strings = com.ndregil.inchat.ui.i18n.LocalAppStrings.current
    var draft by remember(activePartner.nickname) { mutableStateOf("") }
    val listState = rememberLazyListState()
    val lastWhisperId = messages.lastOrNull()?.id

    LaunchedEffect(activePartner.nickname, lastWhisperId, messages.size) {
        if (messages.isNotEmpty()) {
            val target = messages.size - 1
            try {
                listState.animateScrollToItem(target)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                listState.scrollToItem(target)
            }
        }
    }

    val cardBg = if (isDark) {
        Color(0xFF13152A).copy(alpha = 0.95f)
    } else {
        Color(0xFFF8FAFF).copy(alpha = 0.96f)
    }
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF38BDF8).copy(alpha = 0.7f),
            Color(0xFFA855F7).copy(alpha = 0.7f)
        )
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = cardBg,
        shadowElevation = 14.dp,
        border = BorderStroke(1.5.dp, borderBrush),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 380.dp)
            .heightIn(min = 270.dp, max = 420.dp)
            .testTag("whisper_expanded_card")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF0EA5E9).copy(alpha = if (isDark) 0.25f else 0.14f),
                                Color(0xFF8B5CF6).copy(alpha = if (isDark) 0.30f else 0.16f)
                            )
                        )
                    )
                    .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0xFF38BDF8), Color(0xFF7C3AED))
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.75f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = activePartner.nickname.take(1).uppercase(Locale.getDefault()),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (isDark) Color(0xFFC4B5FD) else Color(0xFF6D28D9),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "@${activePartner.nickname}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = strings.whisperCardTitle,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Minimize to Floating Air Bubble
                    IconButton(
                        onClick = onMinimize,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("btn_minimize_whisper_bubble")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = strings.minimizeToAirBubbleDesc,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Explicit 'Akhiri' Button Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.16f),
                        border = BorderStroke(0.8.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onClose)
                            .testTag("btn_close_whisper_bubble")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = strings.endWhisper,
                                tint = Color(0xFFF87171),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = strings.endWhisper,
                                color = Color(0xFFF87171),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Partner Switcher Pills (when multiple peers whispered in the room)
            if (partners.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    partners.forEach { partner ->
                        val selected = partner.nickname == activePartner.nickname
                        Surface(
                            onClick = { onSelectPartner(partner) },
                            shape = RoundedCornerShape(50),
                            color = if (selected) Color(0xFF7C3AED) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(
                                1.dp,
                                if (selected) Color(0xFF38BDF8) else Color.Transparent
                            )
                        ) {
                            Text(
                                text = "@${partner.nickname}",
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            // Whisper Messages Stream
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BubbleChart,
                            contentDescription = null,
                            tint = Color(0xFF8B5CF6).copy(alpha = 0.55f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${strings.whisperHint} @${activePartner.nickname}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = strings.whisperOnlyTwoOfYou,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            WhisperMiniAirBubble(
                                msg = msg,
                                isDark = isDark,
                                onTranslateClick = onTranslateClick
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            // Dedicated Whisper Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.08f)
                            else Color(0xFFEEF2FF)
                        )
                        .border(
                            1.dp,
                            Color(0xFF8B5CF6).copy(alpha = 0.35f),
                            RoundedCornerShape(22.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (draft.isEmpty()) {
                        Text(
                            text = "${strings.whisperTargetTo} @${activePartner.nickname}...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        ),
                        cursorBrush = SolidColor(Color(0xFF8B5CF6)),
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                val trimmed = draft.trim()
                                if (trimmed.isNotEmpty()) {
                                    onSendWhisper(trimmed)
                                    draft = ""
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("whisper_bubble_input")
                    )
                }

                IconButton(
                    onClick = {
                        val trimmed = draft.trim()
                        if (trimmed.isNotEmpty()) {
                            onSendWhisper(trimmed)
                            draft = ""
                        }
                    },
                    enabled = draft.isNotBlank(),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (draft.isNotBlank()) {
                                Brush.linearGradient(listOf(Color(0xFF0EA5E9), Color(0xFF7C3AED)))
                            } else {
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            }
                        )
                        .testTag("btn_send_whisper_bubble")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = strings.sendWhisperDesc,
                        tint = if (draft.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WhisperMiniAirBubble(
    msg: XmppMessage,
    isDark: Boolean,
    onTranslateClick: ((XmppMessage) -> Unit)? = null
) {
    val strings = com.ndregil.inchat.ui.i18n.LocalAppStrings.current
    var showOriginal by remember(msg.id) { mutableStateOf(false) }
    val isTranslated = !msg.translatedBody.isNullOrBlank()

    val timeText = remember(msg.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
    }
    val isMine = msg.isMine
    val bubbleBrush = if (isMine) {
        Brush.linearGradient(
            listOf(
                Color(0xFF7C3AED).copy(alpha = if (isDark) 0.75f else 0.88f),
                Color(0xFF0EA5E9).copy(alpha = if (isDark) 0.55f else 0.72f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                if (isDark) Color(0xFF312E81).copy(alpha = 0.72f) else Color(0xFFE0F2FE).copy(alpha = 0.92f),
                if (isDark) Color(0xFF4C1D95).copy(alpha = 0.65f) else Color(0xFFEDE9FE).copy(alpha = 0.92f)
            )
        )
    }
    val textColor = when {
        isMine -> Color.White
        isDark -> Color(0xFFF3E8FF)
        else -> Color(0xFF2E1065)
    }
    val shape = RoundedCornerShape(
        topStart = if (isMine) 18.dp else 6.dp,
        topEnd = if (isMine) 6.dp else 18.dp,
        bottomStart = 18.dp,
        bottomEnd = 18.dp
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clip(shape)
                .background(bubbleBrush)
                .border(
                    width = 1.dp,
                    color = if (isMine) Color.White.copy(alpha = 0.35f) else Color(0xFF38BDF8).copy(alpha = 0.45f),
                    shape = shape
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                if (isTranslated) {
                    val langName = com.ndregil.inchat.data.network.TranslationService.getLanguageName(
                        msg.translationLanguage ?: "id"
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = "${strings.translatedToPrefix} $langName",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (showOriginal) strings.showTranslationLabel else strings.viewOriginalLabel,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMine || isDark) Color(0xFF93C5FD) else Color(0xFF1D4ED8),
                            modifier = Modifier.clickable { showOriginal = !showOriginal }
                        )
                    }
                }
                LinkifiedText(
                    text = if (isTranslated && !showOriginal) msg.translatedBody!! else msg.body,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    color = textColor,
                    linkColor = if (isMine || isDark) Color(0xFF93C5FD) else Color(0xFF1D4ED8)
                )
                InlineUrlImagePreviews(
                    text = msg.body,
                    extraUrls = listOfNotNull(msg.fileUrl),
                    mimeTypeHint = msg.fileMimeType,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    if (onTranslateClick != null && msg.body.isNotBlank()) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = strings.translateMessageAction,
                            tint = textColor.copy(alpha = 0.78f),
                            modifier = Modifier
                                .size(13.dp)
                                .clickable { onTranslateClick(msg) }
                                .testTag("btn_translate_whisper_${msg.id}")
                        )
                    }
                    Text(
                        text = timeText,
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
