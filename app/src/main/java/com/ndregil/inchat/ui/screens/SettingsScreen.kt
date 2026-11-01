package com.ndregil.inchat.ui.screens

import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndregil.inchat.data.model.ConnectionStatus
import com.ndregil.inchat.data.model.XmppAccount
import com.ndregil.inchat.ui.RegistrationState
import com.ndregil.inchat.data.network.fcm.XmppForegroundService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentAccount: XmppAccount,
    connectionStatus: ConnectionStatus,
    registrationState: RegistrationState,
    onConnect: (XmppAccount, String) -> Unit,
    onDisconnect: () -> Unit,
    onRegisterAccount: (String, String, String, String, Int, String, String, Boolean) -> Unit,
    onCreateInstantAccount: (String, String, String) -> Unit,
    onResetRegistrationState: () -> Unit,
    backupStatus: String?,
    isBackupLoading: Boolean,
    onExportBackup: (Uri?, String) -> Unit,
    onImportBackup: (Uri, String) -> Unit,
    onClearBackupStatus: () -> Unit,
    onUpdateAvatar: (Uri) -> Unit,
    myAvatar: ByteArray?,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onSwitchToAccountTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showXepInfoDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan & Konfigurasi", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Section 1: Account & Server Connection Status
            item {
                SettingsCategoryHeader(title = "Akun & Koneksi XMPP", icon = Icons.Default.AccountCircle)
                Spacer(modifier = Modifier.height(8.dp))
                AccountConnectionCard(
                    currentAccount = currentAccount,
                    connectionStatus = connectionStatus,
                    myAvatar = myAvatar,
                    onManageAccountClick = onSwitchToAccountTab
                )
            }

            // Section 2: Theme & Appearance
            item {
                SettingsCategoryHeader(title = "Tampilan & Tema", icon = Icons.Default.Palette)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = if (isDarkMode) Icons.Default.NightsStay else Icons.Default.WbSunny,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Mode Gelap (Dark Mode)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    Text(
                                        if (isDarkMode) "Tema gelap aktif untuk kenyamanan mata" else "Tema terang aktif",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = isDarkMode,
                                onCheckedChange = { onToggleDarkMode() },
                                modifier = Modifier.testTag("switch_dark_mode")
                            )
                        }
                    }
                }
            }

            // Section 3: Foreground Service & Anti-Kill System
            item {
                SettingsCategoryHeader(title = "Latar Belakang & Anti-Kill", icon = Icons.Default.Memory)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Layanan Foreground & Wakelock", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(
                                    "Koneksi XMPP dan push pesan tetap aktif di latar belakang (Anti-Kill OS).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { XmppForegroundService.startService(context) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Aktifkan Service", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { XmppForegroundService.stopService(context) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Hentikan", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Section 4: Standards & XEP Information
            item {
                SettingsCategoryHeader(title = "Standar & Protokol XEP", icon = Icons.Default.Code)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showXepInfoDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Informasi Standar XEP", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                "XEP-0048, XEP-0402, XEP-0166 Jingle, XEP-0077 IBR, XEP-0153 Avatars",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Section 5: About App
            item {
                SettingsCategoryHeader(title = "Tentang InChat", icon = Icons.Default.Info)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("InChat Multi-Room XMPP", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Versi 1.0.0 (Production)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Aplikasi chat berbasis protokol terbuka XMPP dengan dukungan MUC, Bookmarks, Jingle Voice/Video Calls, dan enkripsi lokal.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showXepInfoDialog) {
        AlertDialog(
            onDismissRequest = { showXepInfoDialog = false },
            title = { Text("Spesifikasi & Standar XEP") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• XEP-0048 & XEP-0402: Bookmarks MUC & PEP Native Bookmarks untuk autojoin ruang favorit.")
                    Text("• XEP-0166 & XEP-0167: Jingle Audio & Video Calling antar pengguna.")
                    Text("• XEP-0077: In-Band Registration (pendaftaran akun langsung ke server).")
                    Text("• XEP-0153 & XEP-0084: vCard-temp & PEP Avatar synchronization.")
                    Text("• XEP-0045: Multi-User Chat (MUC) lengkap dengan moderasi dan konfigurasi ruang.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showXepInfoDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }
}

@Composable
fun SettingsCategoryHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun AccountConnectionCard(
    currentAccount: XmppAccount,
    connectionStatus: ConnectionStatus,
    myAvatar: ByteArray?,
    onManageAccountClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onManageAccountClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                val decodedBitmap = remember(myAvatar) {
                    if (myAvatar != null && myAvatar.isNotEmpty()) {
                        try {
                            BitmapFactory.decodeByteArray(myAvatar, 0, myAvatar.size)
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                }

                if (decodedBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = decodedBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (currentAccount.jid.isNotBlank()) currentAccount.jid else "Belum ada akun aktif",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when (connectionStatus) {
                                    ConnectionStatus.CONNECTED -> com.ndregil.inchat.ui.theme.OnlineGreen
                                    ConnectionStatus.CONNECTING, ConnectionStatus.AUTHENTICATING -> com.ndregil.inchat.ui.theme.AwayAmber
                                    ConnectionStatus.ERROR -> com.ndregil.inchat.ui.theme.DndRed
                                    ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (connectionStatus) {
                            ConnectionStatus.CONNECTED -> "Terhubung"
                            ConnectionStatus.CONNECTING -> "Menghubungkan..."
                            ConnectionStatus.AUTHENTICATING -> "Autentikasi..."
                            ConnectionStatus.ERROR -> "Koneksi Error"
                            ConnectionStatus.DISCONNECTED -> "Terputus"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
