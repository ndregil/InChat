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
    if (iconRes != null && iconRes != 0) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = iconRes),
            contentDescription = contentDescription ?: clientCaps.clientName,
            modifier = modifier
        )
    } else {
        val effectivePlatform = if (platform != ClientPlatform.UNKNOWN) platform else (clientCaps?.platform ?: ClientPlatform.UNKNOWN)
        val icon = getClientPlatformIcon(effectivePlatform)
        val color = tint ?: getClientPlatformColor(effectivePlatform)
        Icon(
            imageVector = icon,
            contentDescription = contentDescription ?: "Client Caps Icon",
            tint = color,
            modifier = modifier
        )
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

            if (showLabel) {
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClientCapsDialog(
    clientCaps: ClientCaps,
    userName: String,
    targetJid: String = "",
    onQuerySoftwareVersion: ((targetJid: String, onResult: (Result<ClientCaps>) -> Unit) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var activeCaps by remember(clientCaps) { mutableStateOf(clientCaps) }
    var isQuerying by remember { mutableStateOf(false) }
    var queryStatusMessage by remember { mutableStateOf<String?>(null) }
    var isLiveVerified by remember { mutableStateOf(false) }

    val platformColor = getClientPlatformColor(activeCaps.platform)
    val platformLabel = when (activeCaps.platform) {
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
                            platform = activeCaps.platform,
                            clientCaps = activeCaps,
                            modifier = Modifier.size(26.dp),
                            tint = platformColor
                        )
                    }

                    Column {
                        Text(
                            text = "Info Klien & Versi User",
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Live Query Button (XEP-0092 Software Version Query)
                Button(
                    onClick = {
                        val queryJid = targetJid.ifBlank { userName }
                        if (queryJid.isNotBlank() && onQuerySoftwareVersion != null) {
                            isQuerying = true
                            queryStatusMessage = "Mengirim XEP-0092 IQ Request ke $queryJid..."
                            onQuerySoftwareVersion(queryJid) { result ->
                                isQuerying = false
                                result.onSuccess { liveCaps ->
                                    activeCaps = liveCaps
                                    isLiveVerified = true
                                    queryStatusMessage = "Versi klien terverifikasi via XEP-0092 IQ Version!"
                                }.onFailure {
                                    queryStatusMessage = "Klien user tidak merespons query XEP-0092. Menampilkan estimasi Presence Caps (XEP-0115)."
                                }
                            }
                        } else {
                            queryStatusMessage = "Memerlukan koneksi aktif ke server XMPP."
                        }
                    },
                    enabled = !isQuerying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_query_client_version")
                ) {
                    if (isQuerying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Meminta versi XEP-0092...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cek Versi Klien Langsung (XEP-0092 IQ)")
                    }
                }

                if (queryStatusMessage != null) {
                    Text(
                        text = queryStatusMessage!!,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isLiveVerified) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Client Identity Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ClientCapsIcon(
                                    platform = activeCaps.platform,
                                    clientCaps = activeCaps,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = activeCaps.clientName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = platformColor.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = platformLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = platformColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = "Nama Klien: ${activeCaps.clientName}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Versi Klien: ${activeCaps.version}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Sistem Operasi / Platform: ${activeCaps.os}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // XEP-0115 Entity Capabilities Info
                Text(
                    text = "XEP-0115: Entity Capabilities",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                val displayNodeUri = activeCaps.nodeUri.ifBlank {
                    if (activeCaps.rawNode.isNotBlank()) {
                        if (activeCaps.rawNode.startsWith("http")) activeCaps.rawNode else "http://${activeCaps.rawNode}"
                    } else "urn:xmpp:caps"
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Node URI:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = displayNodeUri,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (activeCaps.rawNode.isNotBlank() && activeCaps.rawNode != displayNodeUri) {
                            Text(
                                text = "Detected Node: ${activeCaps.rawNode}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Caps Hash (${activeCaps.hash}):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = activeCaps.ver,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Supported XMPP Features / Extensions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Fitur & Ekstensi XMPP Terdeteksi (${activeCaps.features.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    activeCaps.readableFeatures.forEach { (title, _) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                // Raw Caps XML preview snippet
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Stanza XML Presence Caps / IQ Version:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "<c xmlns='http://jabber.org/protocol/caps'\n   hash='${activeCaps.hash}'\n   node='$displayNodeUri'\n   ver='${activeCaps.ver}'/>",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
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
