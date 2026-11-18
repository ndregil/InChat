package com.ndregil.inchat.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.ndregil.inchat.util.MediaUrlUtils
import java.io.File
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

data class PendingAttachment(
    val uri: Uri,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val targetJid: String? = null
)

fun resolvePendingAttachment(
    context: Context,
    uri: Uri,
    fallbackName: String? = null,
    fallbackSize: Long = 0L,
    fallbackMime: String? = null,
    targetJid: String? = null
): PendingAttachment {
    var displayName: String? = null
    var queriedSize: Long = -1L

    try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                    displayName = cursor.getString(nameIdx)
                }
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    queriedSize = cursor.getLong(sizeIdx)
                }
            }
        }
    } catch (_: Exception) {
    }

    if (queriedSize <= 0L) {
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                if (afd.length > 0L) queriedSize = afd.length
            }
        } catch (_: Exception) {
        }
    }

    if (queriedSize <= 0L && uri.scheme == "file") {
        uri.path?.let { path ->
            val f = File(path)
            if (f.exists() && f.length() > 0L) queriedSize = f.length()
        }
    }

    val rawName = displayName?.takeIf { it.isNotBlank() }
        ?: fallbackName?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: "berkas_${System.currentTimeMillis()}"

    val resolverMime = try {
        context.contentResolver.getType(uri)?.takeIf { it.isNotBlank() && it != "*/*" }
    } catch (_: Exception) {
        null
    }

    val extFromName = rawName.substringAfterLast('.', "").takeIf {
        rawName.contains('.') && it.isNotBlank() && it.length <= 15
    }?.lowercase(Locale.ROOT)

    val extMime = extFromName?.let {
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(it)
    }

    val resolvedMime = fallbackMime?.takeIf { it.isNotBlank() && it != "*/*" }
        ?: resolverMime
        ?: extMime
        ?: "application/octet-stream"

    val finalName = if (extFromName == null && resolvedMime != "application/octet-stream") {
        val extFromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(resolvedMime)
        if (!extFromMime.isNullOrBlank()) "$rawName.$extFromMime" else rawName
    } else {
        rawName
    }

    val finalSize = when {
        queriedSize > 0L -> queriedSize
        fallbackSize > 0L -> fallbackSize
        else -> 0L
    }

    return PendingAttachment(
        uri = uri,
        fileName = finalName,
        fileSize = finalSize,
        mimeType = resolvedMime,
        targetJid = targetJid
    )
}

@Composable
fun AttachmentSendDialog(
    attachment: PendingAttachment,
    initialDescription: String = "",
    onDismiss: () -> Unit,
    onConfirm: (description: String) -> Unit
) {
    var description by remember(attachment.uri) { mutableStateOf(initialDescription) }

    val isImage = remember(attachment) {
        attachment.mimeType.startsWith("image/", ignoreCase = true) ||
            MediaUrlUtils.isKnownImageUrl(attachment.fileName, attachment.mimeType)
    }
    val isVideo = attachment.mimeType.startsWith("video/", ignoreCase = true)
    val extBadge = remember(attachment.fileName) {
        val ext = attachment.fileName.substringAfterLast('.', "")
        if (attachment.fileName.contains('.') && ext.isNotBlank() && ext.length <= 8) {
            ext.uppercase(Locale.ROOT)
        } else {
            "FILE"
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("attachment_send_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isImage) Icons.Default.Image else Icons.Default.AttachFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isImage) "Kirim Gambar" else "Kirim Berkas",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isImage) {
                    AsyncImage(
                        model = attachment.uri,
                        contentDescription = attachment.fileName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 220.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isImage || isVideo) {
                                Icon(
                                    imageVector = if (isImage) Icons.Default.Image else Icons.Default.PlayCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = extBadge,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = attachment.fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val sizeLabel = if (attachment.fileSize > 0L) {
                                formatAttachmentSize(attachment.fileSize)
                            } else {
                                "Ukuran dihitung saat unggah"
                            }
                            Text(
                                text = "$sizeLabel • ${attachment.mimeType}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Deskripsi pesan (opsional)") },
                    placeholder = { Text("Tulis deskripsi atau keterangan berkas...") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("attachment_description_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("btn_cancel_attachment")
                    ) {
                        Text("Batal")
                    }
                    Button(
                        onClick = { onConfirm(description.trim()) },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("btn_send_attachment")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kirim")
                    }
                }
            }
        }
    }
}

private fun formatAttachmentSize(size: Long): String {
    if (size <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
    return String.format(Locale.getDefault(), "%.1f %s", size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}
