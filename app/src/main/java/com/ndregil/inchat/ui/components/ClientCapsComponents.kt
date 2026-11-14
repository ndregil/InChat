package com.ndregil.inchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndregil.inchat.data.model.ClientCaps
import com.ndregil.inchat.data.model.ClientPlatform

fun getClientPlatformIcon(platform: ClientPlatform): ImageVector {
    return when (platform) {
        ClientPlatform.ANDROID -> Icons.Default.PhoneAndroid
        ClientPlatform.IOS -> Icons.Default.PhoneIphone
        ClientPlatform.DESKTOP -> Icons.Default.Computer
        ClientPlatform.WEB -> Icons.Default.Language
        ClientPlatform.BOT -> Icons.Default.SmartToy
        ClientPlatform.UNKNOWN -> Icons.Default.Devices
    }
}

fun getClientPlatformColor(platform: ClientPlatform): Color {
    return when (platform) {
        ClientPlatform.ANDROID -> Color(0xFF16A34A) // Green
        ClientPlatform.IOS -> Color(0xFF64748B)     // Slate
        ClientPlatform.DESKTOP -> Color(0xFF4F46E5) // Indigo
        ClientPlatform.WEB -> Color(0xFF0D9488)     // Teal
        ClientPlatform.BOT -> Color(0xFF9333EA)     // Purple
        ClientPlatform.UNKNOWN -> Color(0xFF6B7280) // Gray
    }
}

@Composable
fun ClientCapsIcon(
    platform: ClientPlatform = ClientPlatform.UNKNOWN,
    clientCaps: ClientCaps? = null,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    contentDescription: String? = null
) {
    val iconRes = clientCaps?.iconResId
    val effectivePlatform = if (platform != ClientPlatform.UNKNOWN) platform else (clientCaps?.platform ?: ClientPlatform.UNKNOWN)

    if (iconRes != null && iconRes != 0) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = iconRes),
            contentDescription = contentDescription ?: clientCaps?.clientName,
            modifier = modifier
        )
    } else if (effectivePlatform != ClientPlatform.UNKNOWN) {
        val icon = getClientPlatformIcon(effectivePlatform)
        val color = tint ?: getClientPlatformColor(effectivePlatform)
        Icon(
            imageVector = icon,
            contentDescription = contentDescription ?: "Client Caps Icon",
            tint = color,
            modifier = modifier
        )
    } else {
        return
    }
}

/**
 * Clickable Badge/Chip showing the client icon and name, tapping it opens client caps details.
 */
@Composable
fun ClientCapsBadge(
    clientCaps: ClientCaps,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    // If no identification info, don't show the badge at all
    if (clientCaps.clientName.isBlank() && clientCaps.platform == ClientPlatform.UNKNOWN && clientCaps.iconResId == null) {
        return
    }

    val platformColor = getClientPlatformColor(clientCaps.platform)
    val clickableModifier = if (onClick != null) {
        Modifier.clickable { onClick() }
    } else Modifier

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = platformColor.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, platformColor.copy(alpha = 0.35f)),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .then(clickableModifier)
            .testTag("client_caps_badge_${clientCaps.clientName}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            ClientCapsIcon(
                platform = clientCaps.platform,
                clientCaps = clientCaps,
                modifier = Modifier.size(15.dp),
                tint = platformColor
            )

            if (showLabel && clientCaps.clientName.isNotBlank()) {
                Text(
                    text = clientCaps.clientName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = platformColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Full XEP-0115 Entity Capabilities & Live XEP-0092 Software Version Dialog
 */
/**
 * Full XEP-0115 Entity Capabilities & Live XEP-0092 Software Version Dialog
 * Strictly decoupled: Client Capability Icon (XEP-0115) and Software Version (XEP-0092) operate independently.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClientCapsDialog(
    clientCaps: ClientCaps,
    userName: String,
    targetJid: String = "",
    onQuerySoftwareVersion: ((targetJid: String, onResult: (Result<ClientCaps>) -> Unit) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var liveVersionResult by remember { mutableStateOf<ClientCaps?>(null) }
    var isQuerying by remember { mutableStateOf(false) }
    var queryStatusMessage by remember { mutableStateOf<String?>(null) }
    var isLiveVerified by remember { mutableStateOf(false) }

    val platformColor = getClientPlatformColor(clientCaps.platform)
    val platformLabel = when (clientCaps.platform) {
        ClientPlatform.ANDROID -> "Android Mobile"
        ClientPlatform.IOS -> "Apple iOS Mobile"
        ClientPlatform.DESKTOP -> "Desktop Computer"
        ClientPlatform.WEB -> "Web Browser Client"
        ClientPlatform.BOT -> "Automated Bot / Service"
        ClientPlatform.UNKNOWN -> "Unknown Client"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(platformColor.copy(alpha = 0.15f))
                            .border(1.dp, platformColor.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        ClientCapsIcon(
                            platform = clientCaps.platform,
                            clientCaps = clientCaps,
                            modifier = Modifier.size(24.dp),
                            tint = platformColor
                        )
                    }

                    Column {
                        Text(
                            text = "Info Klien & Kemampuan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = userName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup", modifier = Modifier.size(18.dp))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // LaunchedEffect to automatically trigger software version query on open
                androidx.compose.runtime.LaunchedEffect(targetJid) {
                    val queryJid = targetJid.ifBlank { userName }
                    if (queryJid.isNotBlank() && onQuerySoftwareVersion != null && !isLiveVerified && !isQuerying) {
                        isQuerying = true
                        queryStatusMessage = "Memeriksa versi klien..."
                        onQuerySoftwareVersion(queryJid) { result ->
                            isQuerying = false
                            result.onSuccess { liveInfo ->
                                liveVersionResult = liveInfo
                                isLiveVerified = true
                                queryStatusMessage = null
                            }.onFailure {
                                liveVersionResult = null
                                isLiveVerified = false
                                queryStatusMessage = "Gagal mendapatkan versi real (offline/tidak merespons)."
                            }
                        }
                    }
                }

                if (isQuerying) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Memeriksa versi klien...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (queryStatusMessage != null && !isLiveVerified) {
                    Text(
                        text = queryStatusMessage!!,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Live Version Response Card
                if (isLiveVerified && liveVersionResult != null) {
                    val ver = liveVersionResult!!
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, platformColor.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Nama software: ${ver.clientName.ifBlank { "Tidak diketahui" }}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = platformColor
                            )
                            Text(
                                text = "Versi software: ${ver.version.ifBlank { "Tidak diketahui" }}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF16A34A)
                            )
                            Text(
                                text = "Operasi sistem: ${ver.os.ifBlank { "Tidak diketahui" }}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_close_caps_dialog")
            ) {
                Text("Tutup")
            }
        }
    )
}
