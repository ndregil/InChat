package com.ndregil.inchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndregil.inchat.data.model.XmppMessage
import com.ndregil.inchat.ui.theme.LocalThemePreferences
import com.ndregil.inchat.util.MediaUrlUtils
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun FileMessageBubbleContent(
    message: XmppMessage,
    textColor: Color
) {
    val strings = com.ndregil.inchat.ui.i18n.LocalAppStrings.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val fontSizeSp = LocalThemePreferences.current.messageFontSizeSp

    val extractedUrls = remember(message.body) { MediaUrlUtils.extractUrls(message.body) }
    val primaryUrl = remember(message.fileUrl, extractedUrls) {
        message.fileUrl?.takeIf { it.isNotBlank() }?.let(MediaUrlUtils::normalizeUrl)
            ?: extractedUrls.firstOrNull()
    }
    val isImage = remember(primaryUrl, message.fileMimeType) {
        MediaUrlUtils.isKnownImageUrl(primaryUrl, message.fileMimeType)
    }
    val isVideo = message.fileMimeType?.startsWith("video/") == true

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        if (message.body.isNotBlank()) {
            LinkifiedText(
                text = message.body,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.4f).sp
                ),
                color = textColor,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        InlineUrlImagePreviews(
            text = message.body,
            extraUrls = listOfNotNull(primaryUrl),
            mimeTypeHint = message.fileMimeType,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        if (!primaryUrl.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(textColor.copy(alpha = 0.1f))
                    .clickable {
                        MediaUrlUtils.openExternalUrl(context, uriHandler, primaryUrl, strings.cannotOpenUrlToast)
                    }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        isImage -> Icons.Default.Image
                        isVideo -> Icons.Default.PlayCircle
                        else -> Icons.Default.Description
                    },
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val displayLabel = message.fileName?.takeIf { it.isNotBlank() && it != "Lampiran" }
                        ?: primaryUrl.substringAfterLast('/').substringBefore('?').ifBlank { message.fileName ?: strings.defaultFileLabel }
                    Text(
                        text = displayLabel,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if ((message.fileSize ?: 0L) > 0L) formatFileSize(message.fileSize!!) else primaryUrl,
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = {
                    MediaUrlUtils.openExternalUrl(context, uriHandler, primaryUrl, strings.cannotOpenUrlToast)
                }) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = strings.downloadDesc,
                        tint = textColor
                    )
                }
            }
        }
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
    return String.format(Locale.getDefault(), "%.1f %s", size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}
