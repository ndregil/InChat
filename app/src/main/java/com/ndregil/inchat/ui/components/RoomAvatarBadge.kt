package com.ndregil.inchat.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.util.Locale
import kotlin.math.abs

private val ROOM_AVATAR_PALETTE = listOf(
    Color(0xFF0284C7),
    Color(0xFF0D9488),
    Color(0xFF4F46E5),
    Color(0xFF7C3AED),
    Color(0xFFDB2777),
    Color(0xFFEA580C),
    Color(0xFF059669),
    Color(0xFF2563EB)
)

@Composable
fun RoomAvatarBadge(
    roomJid: String,
    roomName: String,
    avatarBytes: ByteArray?,
    avatarUrl: String? = null,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accentColor = remember(roomJid) {
        val idx = abs(roomJid.lowercase(Locale.ROOT).hashCode()) % ROOM_AVATAR_PALETTE.size
        ROOM_AVATAR_PALETTE[idx]
    }

    val initials = remember(roomName, roomJid) {
        val cleanName = roomName.trim()
        val alnum = cleanName.filter { it.isLetterOrDigit() }
        when {
            alnum.length >= 2 -> alnum.take(2).uppercase(Locale.getDefault())
            alnum.isNotEmpty() -> alnum.uppercase(Locale.getDefault())
            cleanName.isNotEmpty() -> cleanName.trimStart('#', ' ').take(2).uppercase(Locale.getDefault())
            else -> roomJid.substringBefore("@").filter { it.isLetterOrDigit() }.take(2).uppercase(Locale.getDefault()).ifEmpty { "RM" }
        }
    }

    val bitmap = remember(avatarBytes) {
        if (avatarBytes != null && avatarBytes.isNotEmpty()) {
            try {
                BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.size)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(accentColor.copy(alpha = 0.16f))
            .border(1.5.dp, accentColor.copy(alpha = 0.75f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.34f).sp,
            color = accentColor
        )

        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Avatar Room $roomName",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else if (avatarBytes != null && avatarBytes.isNotEmpty()) {
            val bytesRequest = remember(avatarBytes) {
                ImageRequest.Builder(context)
                    .data(avatarBytes)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = bytesRequest,
                contentDescription = "Avatar Room $roomName",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else if (!avatarUrl.isNullOrBlank()) {
            val urlRequest = remember(avatarUrl) {
                ImageRequest.Builder(context)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = urlRequest,
                contentDescription = "Avatar Room $roomName",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
    }
}
