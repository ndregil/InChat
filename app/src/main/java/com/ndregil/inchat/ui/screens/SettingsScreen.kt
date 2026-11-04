package com.ndregil.inchat.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndregil.inchat.data.model.ConnectionStatus
import com.ndregil.inchat.data.model.XmppAccount
import com.ndregil.inchat.data.network.fcm.FcmPushManager
import com.ndregil.inchat.data.network.fcm.PushMessagePayload
import com.ndregil.inchat.data.network.fcm.XmppForegroundService
import com.ndregil.inchat.ui.RegistrationState
import com.ndregil.inchat.ui.theme.AwayAmber
import com.ndregil.inchat.ui.theme.DndRed
import com.ndregil.inchat.ui.theme.OnlineGreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.graphics.drawable.toBitmap

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

enum class SubSettingCategory(val title: String, val subtitle: String, val icon: ImageVector) {
    ACCOUNT("Pengaturan Akun", "Profil avatar, nama pengguna, status & akun XMPP", Icons.Default.AccountCircle),
    DISPLAY("Pengaturan Tampilan", "Mode gelap, tema chat bubble, wallpaper & favorit", Icons.Default.Palette),
    CONNECTION("Pengaturan Koneksi", "Server host, port, TLS, status koneksi & protokol XEP", Icons.Default.Router),
    NOTIFICATION("Pengaturan Notifikasi", "Push notification, stabilitas background & anti-kill", Icons.Default.Notifications)
}

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
    onNavigateToBookmarks: () -> Unit,
    onSwitchToAccountTab: () -> Unit,
    autoApproveSubscription: Boolean,
    onToggleAutoApproveSubscription: (Boolean) -> Unit,
    onSignOut: () -> Unit = {},
    onExitApp: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf<SubSettingCategory?>(null) }

    // Intercept back button when inside a sub-setting category
    if (selectedCategory != null) {
        BackHandler {
            selectedCategory = null
        }
    }

    AnimatedContent(
        targetState = selectedCategory,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "SettingsSubNavigation"
    ) { category ->
        if (category == null) {
            MainSubSettingsMenu(
                currentAccount = currentAccount,
                connectionStatus = connectionStatus,
                myAvatar = myAvatar,
                onSelectCategory = { selectedCategory = it },
                onUpdateAvatar = onUpdateAvatar,
                onSwitchToAccountTab = onSwitchToAccountTab,
                onSignOut = onSignOut,
                onExitApp = onExitApp,
                modifier = modifier
            )
        } else {
            SubSettingDetailScreen(
                category = category,
                currentAccount = currentAccount,
                connectionStatus = connectionStatus,
                myAvatar = myAvatar,
                isDarkMode = isDarkMode,
                onToggleDarkMode = onToggleDarkMode,
                onNavigateToBookmarks = onNavigateToBookmarks,
                onSwitchToAccountTab = onSwitchToAccountTab,
                onUpdateAvatar = onUpdateAvatar,
                onConnect = onConnect,
                onDisconnect = onDisconnect,
                autoApproveSubscription = autoApproveSubscription,
                onToggleAutoApproveSubscription = onToggleAutoApproveSubscription,
                onSignOut = onSignOut,
                onExitApp = onExitApp,
                onBack = { selectedCategory = null },
                modifier = modifier
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSubSettingsMenu(
    currentAccount: XmppAccount,
    connectionStatus: ConnectionStatus,
    myAvatar: ByteArray?,
    onSelectCategory: (SubSettingCategory) -> Unit,
    onUpdateAvatar: (Uri) -> Unit,
    onSwitchToAccountTab: () -> Unit,
    onSignOut: () -> Unit = {},
    onExitApp: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onUpdateAvatar(it) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan", fontWeight = FontWeight.Bold) },
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
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            // Profile & Connection Quick Header
            item {
                AccountConnectionCard(
                    currentAccount = currentAccount,
                    connectionStatus = connectionStatus,
                    myAvatar = myAvatar,
                    onManageAccountClick = onSwitchToAccountTab,
                    onUpdateAvatarClick = { avatarPicker.launch("image/*") }
                )
            }

            item {
                Text(
                    text = "Sub Pengaturan",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }

            // Sub Settings List Tiles
            items(SubSettingCategory.values().size) { index ->
                val category = SubSettingCategory.values()[index]
                SubSettingTileCard(
                    category = category,
                    onClick = { onSelectCategory(category) }
                )
            }

            // Session & Application Management
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Sesi & Aplikasi",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Sign Out Card
                        Surface(
                            onClick = { showSignOutDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                            modifier = Modifier.testTag("setting_action_signout")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(42.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = AwayAmber.copy(alpha = 0.15f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Logout,
                                            contentDescription = "Sign Out",
                                            tint = AwayAmber,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Keluar Akun (Sign Out)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Keluar dari akun XMPP, hapus sesi login di perangkat, dan hentikan aplikasi.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Exit App Card
                        Surface(
                            onClick = { showExitDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            color = DndRed.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, DndRed.copy(alpha = 0.25f)),
                            modifier = Modifier.testTag("setting_action_exit")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(42.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = DndRed.copy(alpha = 0.2f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.PowerSettingsNew,
                                            contentDescription = "Exit App",
                                            tint = DndRed,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Keluar Aplikasi (Exit)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = DndRed
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Tutup aplikasi dan hentikan semua proses latar belakang (foreground service & koneksi).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = DndRed)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = AwayAmber, modifier = Modifier.size(32.dp)) },
            title = { Text("Konfirmasi Keluar Akun", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Apakah Anda yakin ingin keluar dari akun (${currentAccount.jid.ifBlank { "Akun Aktif" }})?\n\nSesi kredensial pada perangkat akan dihapus, koneksi ditutup, dan aplikasi akan berhenti berjalan.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        onSignOut()
                        activity?.finishAffinity()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AwayAmber, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Keluar Akun", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showSignOutDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Batal")
                }
            }
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            icon = { Icon(Icons.Default.PowerSettingsNew, null, tint = DndRed, modifier = Modifier.size(32.dp)) },
            title = { Text("Hentikan & Keluar Aplikasi?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Aplikasi akan ditutup dan seluruh proses latar belakang (termasuk foreground service anti-kill, koneksi XMPP, dan penjadwalan) akan dihentikan sepenuhnya.\n\nAkun Anda tetap tersimpan untuk login otomatis saat aplikasi dibuka kembali.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        onExitApp()
                        activity?.finishAffinity()
                        android.os.Process.killProcess(android.os.Process.myPid())
                        kotlin.system.exitProcess(0)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DndRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Hentikan & Keluar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showExitDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun SubSettingTileCard(
    category: SubSettingCategory,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("setting_tile_${category.name.lowercase()}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = category.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubSettingDetailScreen(
    category: SubSettingCategory,
    currentAccount: XmppAccount,
    connectionStatus: ConnectionStatus,
    myAvatar: ByteArray?,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onNavigateToBookmarks: () -> Unit,
    onSwitchToAccountTab: () -> Unit,
    onUpdateAvatar: (Uri) -> Unit,
    onConnect: (XmppAccount, String) -> Unit,
    onDisconnect: () -> Unit,
    autoApproveSubscription: Boolean,
    onToggleAutoApproveSubscription: (Boolean) -> Unit,
    onSignOut: () -> Unit = {},
    onExitApp: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(category.title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali ke Pengaturan"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (category) {
                SubSettingCategory.ACCOUNT -> AccountSubSettings(
                    currentAccount = currentAccount,
                    connectionStatus = connectionStatus,
                    myAvatar = myAvatar,
                    onUpdateAvatar = onUpdateAvatar,
                    onSwitchToAccountTab = onSwitchToAccountTab,
                    onConnect = onConnect,
                    onDisconnect = onDisconnect,
                    onSignOut = onSignOut,
                    onExitApp = onExitApp
                )
                SubSettingCategory.DISPLAY -> DisplaySubSettings(
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = onToggleDarkMode,
                    onNavigateToBookmarks = onNavigateToBookmarks
                )
                SubSettingCategory.CONNECTION -> ConnectionSubSettings(
                    currentAccount = currentAccount,
                    connectionStatus = connectionStatus,
                    autoApproveSubscription = autoApproveSubscription,
                    onToggleAutoApprove = onToggleAutoApproveSubscription,
                    onConnect = onConnect,
                    onDisconnect = onDisconnect
                )
                SubSettingCategory.NOTIFICATION -> NotificationSubSettings()
            }
        }
    }
}

// ==========================================
// 1. PENGATURAN AKUN SUB-SETTING
// ==========================================
@Composable
fun AccountSubSettings(
    currentAccount: XmppAccount,
    connectionStatus: ConnectionStatus,
    myAvatar: ByteArray?,
    onUpdateAvatar: (Uri) -> Unit,
    onSwitchToAccountTab: () -> Unit,
    onConnect: (XmppAccount, String) -> Unit,
    onDisconnect: () -> Unit,
    onSignOut: () -> Unit = {},
    onExitApp: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onUpdateAvatar(it) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AccountConnectionCard(
                currentAccount = currentAccount,
                connectionStatus = connectionStatus,
                myAvatar = myAvatar,
                onManageAccountClick = onSwitchToAccountTab,
                onUpdateAvatarClick = { avatarPicker.launch("image/*") }
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Informasi Detail Akun", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Bare JID", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (currentAccount.jid.isNotBlank()) currentAccount.jid else "-", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Nickname", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (currentAccount.nickname.isNotBlank()) currentAccount.nickname else "-", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Domain Server", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (currentAccount.domain.isNotBlank()) currentAccount.domain else "-", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onSwitchToAccountTab,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.SwitchAccount, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ganti Akun")
                        }

                        if (connectionStatus == ConnectionStatus.CONNECTED) {
                            Button(
                                onClick = onDisconnect,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Putuskan")
                            }
                        } else {
                            Button(
                                onClick = { onConnect(currentAccount, "") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Hubungkan")
                            }
                        }
                    }
                }
            }
        }

        // Dedicated Sign Out & Exit Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Tindakan Sesi & Aplikasi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showSignOutDialog = true },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AwayAmber)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Keluar Akun", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { showExitDialog = true },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DndRed)
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Keluar Aplikasi", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = AwayAmber, modifier = Modifier.size(32.dp)) },
            title = { Text("Konfirmasi Keluar Akun", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Apakah Anda yakin ingin keluar dari akun (${currentAccount.jid.ifBlank { "Akun Aktif" }})?\n\nSesi kredensial pada perangkat akan dihapus, koneksi ditutup, dan aplikasi akan berhenti berjalan.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        onSignOut()
                        activity?.finishAffinity()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AwayAmber, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Keluar Akun", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showSignOutDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Batal")
                }
            }
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            icon = { Icon(Icons.Default.PowerSettingsNew, null, tint = DndRed, modifier = Modifier.size(32.dp)) },
            title = { Text("Hentikan & Keluar Aplikasi?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Aplikasi akan ditutup dan seluruh proses latar belakang (termasuk foreground service anti-kill, koneksi XMPP, dan penjadwalan) akan dihentikan sepenuhnya.\n\nAkun Anda tetap tersimpan untuk login otomatis saat aplikasi dibuka kembali.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        onExitApp()
                        activity?.finishAffinity()
                        android.os.Process.killProcess(android.os.Process.myPid())
                        kotlin.system.exitProcess(0)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DndRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Hentikan & Keluar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showExitDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Batal")
                }
            }
        )
    }
}

// ==========================================
// 2. PENGATURAN TAMPILAN SUB-SETTING
// ==========================================
@Composable
fun DisplaySubSettings(
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onNavigateToBookmarks: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
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

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onNavigateToBookmarks() },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Kelola Ruangan Favorit & Bookmarks", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text("Atur auto-join dan daftar favorit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. PENGATURAN KONEKSI SUB-SETTING
// ==========================================
@Composable
fun ConnectionSubSettings(
    currentAccount: XmppAccount,
    connectionStatus: ConnectionStatus,
    autoApproveSubscription: Boolean,
    onToggleAutoApprove: (Boolean) -> Unit,
    onConnect: (XmppAccount, String) -> Unit,
    onDisconnect: () -> Unit
) {
    var showXepInfoDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Konfigurasi Server & TLS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Host Server", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (currentAccount.host.isNotBlank()) currentAccount.host else currentAccount.domain, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Port XMPP", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${currentAccount.port}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Enkripsi TLS", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (currentAccount.useTls) "STARTTLS Direct" else "Disabled", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (currentAccount.useTls) OnlineGreen else AwayAmber)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Status Koneksi", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(connectionStatus.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = when(connectionStatus) {
                            ConnectionStatus.CONNECTED -> OnlineGreen
                            ConnectionStatus.CONNECTING, ConnectionStatus.AUTHENTICATING -> AwayAmber
                            else -> DndRed
                        })
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Approve Subscription Requests", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("Otomatis menyetujui permintaan kontak masuk (privacy control)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = autoApproveSubscription,
                        onCheckedChange = onToggleAutoApprove,
                        modifier = Modifier.testTag("switch_auto_approve_subscription")
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showXepInfoDialog = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Informasi Standar & Protokol XEP", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("XEP-0048, XEP-0402, XEP-0166, XEP-0077, XEP-0153, XEP-0084, XEP-0184", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("• XEP-0048 & XEP-0402: Bookmarks MUC & PEP Native Bookmarks.")
                    Text("• XEP-0184: Message Delivery Receipts (tanda terima pesan).")
                    Text("• XEP-0166 & XEP-0167: Jingle Audio & Video Calling.")
                    Text("• XEP-0077: In-Band Registration (pendaftaran akun).")
                    Text("• XEP-0153 & XEP-0084: vCard & PEP User Avatar sync.")
                    Text("• XEP-0045: Multi-User Chat moderasi dan hak akses.")
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

// ==========================================
// 4. PENGATURAN NOTIFIKASI SUB-SETTING
// ==========================================
@Composable
fun NotificationSubSettings() {
    val context = LocalContext.current
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    val isAllMessagesPushEnabled by FcmPushManager.isPushEnabled.collectAsStateWithLifecycle()

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    // Notification Sound & Vibration States
    var privateSound by remember { mutableStateOf("Default (Chime)") }
    var mucSound by remember { mutableStateOf("Default (Group Beep)") }
    var vibrationEnabled by remember { mutableStateOf(true) }
    var soundPreviewToast by remember { mutableStateOf<String?>(null) }

    val soundOptions = listOf("Default", "Whistle", "Pop", "Ping", "Chime", "Silent")
    val mucSoundOptions = listOf("Default", "Group Beep", "Chime", "Bubble", "Silent")

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Master Notification Toggle Card (All Messages)
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("card_notification_all_messages"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Notifikasi Semua Pesan",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (isAllMessagesPushEnabled) "Aktif untuk seluruh pesan masuk" else "Notifikasi dinonaktifkan",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isAllMessagesPushEnabled) OnlineGreen else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Switch(
                            checked = isAllMessagesPushEnabled,
                            onCheckedChange = { FcmPushManager.setPushEnabled(context, it) },
                            modifier = Modifier.testTag("switch_all_messages_notification")
                        )
                    }

                    Text(
                        "Tampilkan notifikasi bilah status & heads-up untuk seluruh pesan masuk: Room MUC, Obrolan Langsung (1-on-1), dan Bisikan Rahasia.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // MUC Mention Only Toggle
                    val isMucMentionOnly by FcmPushManager.isMucMentionOnly.collectAsStateWithLifecycle()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Notifikasi Grup MUC", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(if (isMucMentionOnly) "Hanya saat disebut (mention)" else "Semua pesan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Switch(
                            checked = isMucMentionOnly,
                            onCheckedChange = { FcmPushManager.setMucNotificationMode(context, it) },
                            modifier = Modifier.testTag("switch_muc_mention_only")
                        )
                    }

                    // Android 13+ Permission Warning if not granted
                    if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Izin Notifikasi Belum Diberikan",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        "Android 13+ memerlukan izin sistem agar pesan dapat muncul di bilah notifikasi.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    },
                                    modifier = Modifier.testTag("btn_grant_notification_permission"),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Izinkan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Test Notification Quick Actions
                    Text(
                        "Uji Coba Notifikasi Sistem Langsung",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val testPayload = PushMessagePayload(
                                    roomJid = "general@conference.xmpp.today",
                                    senderNickname = "Budi (MUC)",
                                    body = "Halo rekan-rekan! Selamat datang di room diskusi.",
                                    isWhisper = false,
                                    isDirect = false,
                                    roomTitle = "Ruang Umum MUC"
                                )
                                FcmPushManager.showSystemNotification(context, testPayload)
                                soundPreviewToast = "Notifikasi MUC dikirim ke bilah status"
                            },
                            modifier = Modifier.weight(1f).testTag("btn_test_notification_muc"),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Room MUC", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = {
                                val testPayload = PushMessagePayload(
                                    roomJid = "alice@xmpp.today",
                                    senderNickname = "Alice W.",
                                    body = "Hai! Ini uji notifikasi pesan obrolan pribadi 1-on-1.",
                                    isWhisper = false,
                                    isDirect = true,
                                    roomTitle = "Alice Wonderland"
                                )
                                FcmPushManager.showSystemNotification(context, testPayload)
                                soundPreviewToast = "Notifikasi 1-on-1 dikirim ke bilah status"
                            },
                            modifier = Modifier.weight(1f).testTag("btn_test_notification_direct"),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("1-on-1 Direct", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = {
                                val testPayload = PushMessagePayload(
                                    roomJid = "general@conference.xmpp.today",
                                    senderNickname = "Charlie",
                                    body = "Psst.. ini notifikasi bisikan rahasia khusus kamu.",
                                    isWhisper = true,
                                    isDirect = false,
                                    roomTitle = "Ruang Umum MUC"
                                )
                                FcmPushManager.showSystemNotification(context, testPayload)
                                soundPreviewToast = "Notifikasi bisikan dikirim ke bilah status"
                            },
                            modifier = Modifier.weight(1f).testTag("btn_test_notification_whisper"),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Bisikan", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Notification Sound Settings Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Suara & Getar Notifikasi Pesan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        "Pilih nada dering dan suara khusus untuk pesan pribadi (1-on-1) dan pesan grup MUC.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                    )

                    // Vibration Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Getar Saat Pesan Masuk", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Getar perangkat ketika notifikasi diterima", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = vibrationEnabled,
                            onCheckedChange = { vibrationEnabled = it },
                            modifier = Modifier.testTag("switch_notification_vibration")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Private Message Sound Selector
                    Text("Suara Pesan Pribadi (1-on-1)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(6.dp))

                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().testTag("private_sound_selector")
                    ) {
                        items(soundOptions.size) { idx ->
                            val sound = soundOptions[idx]
                            val isSelected = privateSound == sound
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    privateSound = sound
                                    soundPreviewToast = "Suara pesan pribadi diatur ke: $sound"
                                },
                                label = { Text(sound, fontSize = 11.sp) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                modifier = Modifier.testTag("private_sound_${sound.lowercase().replace(" ", "_")}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // MUC Message Sound Selector
                    Text("Suara Pesan Grup MUC", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(6.dp))

                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().testTag("muc_sound_selector")
                    ) {
                        items(mucSoundOptions.size) { idx ->
                            val sound = mucSoundOptions[idx]
                            val isSelected = mucSound == sound
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    mucSound = sound
                                    soundPreviewToast = "Suara pesan MUC diatur ke: $sound"
                                },
                                label = { Text(sound, fontSize = 11.sp) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                modifier = Modifier.testTag("muc_sound_${sound.lowercase().replace(" ", "_")}")
                            )
                        }
                    }

                    soundPreviewToast?.let { toast ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(toast, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }

        // Battery Optimization & Anti-Kill Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = if (!isIgnoringBatteryOptimizations) BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)) else null
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Stabilitas Latar Belakang & Anti-Kill",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        "Menjamin aplikasi tidak dimatikan oleh sistem saat layar mati agar pesan tetap masuk.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    // Battery Optimization Status & Action
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isIgnoringBatteryOptimizations)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        border = if (!isIgnoringBatteryOptimizations)
                            BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                        else null
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isIgnoringBatteryOptimizations) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isIgnoringBatteryOptimizations) OnlineGreen else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (isIgnoringBatteryOptimizations) "Optimasi Baterai: Aman" else "Optimasi Baterai: Terbatas",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isIgnoringBatteryOptimizations) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                                )
                                Text(
                                    if (isIgnoringBatteryOptimizations) "Aplikasi diizinkan berjalan tanpa batas." else "Sistem mungkin menghentikan aplikasi secara paksa.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (!isIgnoringBatteryOptimizations) {
                                FilledTonalButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            try {
                                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                                context.startActivity(intent)
                                            } catch (ex: Exception) {}
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                ) {
                                    Text("Perbaiki", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Metode Anti-Kill (Manual)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))

                    AntiKillMethodRow(
                        title = "Foreground Service",
                        description = "Ikon aktif di notifikasi (XEP-Standard)",
                        onStart = { XmppForegroundService.startService(context) },
                        onStop = { XmppForegroundService.stopService(context) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    AntiKillMethodRow(
                        title = "WorkManager (Pesan)",
                        description = "Penjadwalan sistem modern (Background)",
                        onStart = { com.ndregil.inchat.data.network.fcm.XmppKeepAliveWorker.enqueue(context) },
                        onStop = { com.ndregil.inchat.data.network.fcm.XmppKeepAliveWorker.stop(context) },
                        primaryColor = MaterialTheme.colorScheme.secondaryContainer,
                        onPrimaryColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}



// ==========================================
// COMMON SHARED COMPONENTS
// ==========================================
@Composable
fun AntiKillMethodRow(
    title: String,
    description: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    primaryColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer,
    onPrimaryColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onStart,
                    modifier = Modifier.size(36.dp).background(primaryColor, CircleShape)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = onPrimaryColor, modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick = onStop,
                    modifier = Modifier.size(36.dp).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun AccountConnectionCard(
    currentAccount: XmppAccount,
    connectionStatus: ConnectionStatus,
    myAvatar: ByteArray?,
    onManageAccountClick: () -> Unit,
    onUpdateAvatarClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onManageAccountClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with edit badge
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clickable { onUpdateAvatarClick() }
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

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (decodedBitmap != null) {
                        Image(
                            bitmap = decodedBitmap.asImageBitmap(),
                            contentDescription = "Foto Profil",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Ganti Foto",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (currentAccount.jid.isNotBlank()) currentAccount.jid else "Belum ada akun aktif",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(10.dp),
                        shape = CircleShape,
                        color = when (connectionStatus) {
                            ConnectionStatus.CONNECTED -> OnlineGreen
                            ConnectionStatus.CONNECTING, ConnectionStatus.AUTHENTICATING -> AwayAmber
                            ConnectionStatus.ERROR -> DndRed
                            ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    ) {}
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (connectionStatus) {
                            ConnectionStatus.CONNECTED -> "Terhubung"
                            ConnectionStatus.CONNECTING -> "Menghubungkan..."
                            ConnectionStatus.AUTHENTICATING -> "Autentikasi..."
                            ConnectionStatus.ERROR -> "Koneksi Error"
                            ConnectionStatus.DISCONNECTED -> "Terputus"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
