package com.ndregil.inchat.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.Home
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Edit
import com.ndregil.inchat.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AssistChip
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ndregil.inchat.data.model.ConnectionStatus
import com.ndregil.inchat.data.model.XmppAccount
import com.ndregil.inchat.data.network.fcm.FcmPushManager
import com.ndregil.inchat.ui.RegistrationState
import com.ndregil.inchat.ui.theme.AwayAmber
import com.ndregil.inchat.ui.theme.DndRed
import com.ndregil.inchat.ui.theme.OnlineGreen

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AccountConnectionScreen(
    currentAccount: XmppAccount,
    connectionStatus: ConnectionStatus,
    registrationState: RegistrationState = RegistrationState.Idle,
    backupStatus: String? = null,
    isBackupLoading: Boolean = false,
    onConnect: (XmppAccount, String) -> Unit,
    onDisconnect: () -> Unit,
    onRegisterAccount: (
        username: String,
        password: String,
        domain: String,
        host: String,
        port: Int,
        nickname: String,
        email: String,
        useTls: Boolean
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onCreateInstantAccount: (username: String, domain: String, nickname: String) -> Unit = { _, _, _ -> },
    onResetRegistrationState: () -> Unit = {},
    onExportBackup: (targetUri: android.net.Uri?, passphrase: String) -> Unit = { _, _ -> },
    onImportBackup: (sourceUri: android.net.Uri, passphrase: String) -> Unit = { _, _ -> },
    onClearBackupStatus: () -> Unit = {},
    onUpdateAvatar: (android.net.Uri) -> Unit = {},
    myAvatar: ByteArray? = null
) {
    // Mode tab: 0 = Welcome & Splash Landing, 1 = Login Existing Account, 2 = Register New JID Account
    var activeTabMode by remember(currentAccount, connectionStatus) {
        mutableIntStateOf(
            if (connectionStatus != ConnectionStatus.CONNECTED && currentAccount.jid.isBlank()) 0 else 1
        )
    }

    // Login Form Inputs
    var jidInput by remember(currentAccount) { mutableStateOf(currentAccount.jid) }
    var passwordInput by remember { mutableStateOf("") }
    var nicknameInput by remember(currentAccount) { mutableStateOf(currentAccount.nickname) }
    var hostInput by remember(currentAccount) { mutableStateOf(currentAccount.host) }
    var portInput by remember(currentAccount) { mutableStateOf(currentAccount.port.toString()) }
    var useTls by remember(currentAccount) { mutableStateOf(currentAccount.useTls) }
    var statusMsgInput by remember(currentAccount) { mutableStateOf(currentAccount.statusMessage) }

    // Registration Form Inputs
    var regUsername by remember { mutableStateOf("") }
    var regDomain by remember { mutableStateOf("trashserver.net") }
    var regPassword by remember { mutableStateOf("") }
    var regConfirmPassword by remember { mutableStateOf("") }
    var regNickname by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regHost by remember { mutableStateOf("") }
    var regPort by remember { mutableStateOf("5222") }
    var regUseTls by remember { mutableStateOf(true) }
    var regValidationError by remember { mutableStateOf<String?>(null) }

    // Backup & Restore Inputs
    var backupPassphrase by remember { mutableStateOf("") }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val fcmToken by FcmPushManager.fcmToken.collectAsStateWithLifecycle()
    val isPushEnabled by FcmPushManager.isPushEnabled.collectAsStateWithLifecycle()
    var testFeedback by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { onExportBackup(it, backupPassphrase) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onImportBackup(it, backupPassphrase) }
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            testFeedback = "Izin notifikasi diberikan!"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pengaturan Akun & Server XMPP")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            val avatarPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri ->
                uri?.let { onUpdateAvatar(it) }
            }

            // Profile Card (When connected)
            if (connectionStatus == ConnectionStatus.CONNECTED) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                .clickable { avatarPicker.launch("image/*") }
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (myAvatar != null) {
                                androidx.compose.foundation.Image(
                                    painter = coil.compose.rememberAsyncImagePainter(myAvatar),
                                    contentDescription = "Avatar Profil",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Ubah Avatar",
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            // Overlay edit icon
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp).padding(4.dp),
                                    tint = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = currentAccount.nickname.ifBlank { currentAccount.username },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = currentAccount.jid,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = { avatarPicker.launch("image/*") },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Perbarui Foto Profil (PEP)", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            // Status Connection Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (connectionStatus) {
                        ConnectionStatus.CONNECTED -> OnlineGreen.copy(alpha = 0.12f)
                        ConnectionStatus.CONNECTING, ConnectionStatus.AUTHENTICATING -> AwayAmber.copy(alpha = 0.12f)
                        ConnectionStatus.ERROR -> DndRed.copy(alpha = 0.12f)
                        ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    when (connectionStatus) {
                        ConnectionStatus.CONNECTED -> OnlineGreen
                        ConnectionStatus.CONNECTING, ConnectionStatus.AUTHENTICATING -> AwayAmber
                        ConnectionStatus.ERROR -> DndRed
                        ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (connectionStatus) {
                        ConnectionStatus.CONNECTED -> Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = OnlineGreen,
                            modifier = Modifier.size(32.dp)
                        )
                        ConnectionStatus.CONNECTING, ConnectionStatus.AUTHENTICATING -> CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = AwayAmber,
                            strokeWidth = 3.dp
                        )
                        ConnectionStatus.ERROR -> Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = DndRed,
                            modifier = Modifier.size(32.dp)
                        )
                        ConnectionStatus.DISCONNECTED -> Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (connectionStatus) {
                                ConnectionStatus.CONNECTED -> "Terhubung (Online)"
                                ConnectionStatus.CONNECTING -> "Menghubungkan ke Stream..."
                                ConnectionStatus.AUTHENTICATING -> "Mengotentikasi SASL..."
                                ConnectionStatus.ERROR -> "Koneksi Bermasalah"
                                ConnectionStatus.DISCONNECTED -> "Terputus (Offline)"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (connectionStatus == ConnectionStatus.CONNECTED)
                                "JID Aktif: ${currentAccount.jid} | Host: ${currentAccount.host}"
                            else
                                "Pilih 'Masuk' atau 'Registrasi JID Baru' untuk memulai obrolan",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mode Selection Tab (Welcome vs Login vs Register New JID)
            TabRow(
                selectedTabIndex = activeTabMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Tab(
                    selected = activeTabMode == 0,
                    onClick = { activeTabMode = 0 },
                    modifier = Modifier.testTag("tab_welcome_mode"),
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Welcome", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                )
                Tab(
                    selected = activeTabMode == 1,
                    onClick = {
                        activeTabMode = 1
                        onResetRegistrationState()
                    },
                    modifier = Modifier.testTag("tab_login_mode"),
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Masuk", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                )
                Tab(
                    selected = activeTabMode == 2,
                    onClick = {
                        activeTabMode = 2
                        onResetRegistrationState()
                    },
                    modifier = Modifier.testTag("tab_register_mode"),
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Daftar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (activeTabMode == 0) {
                // ==================== MODE 0: WELCOME & SPLASH LANDING ====================
                WelcomeSplashScreen(
                    onGoToLogin = { activeTabMode = 1 },
                    onGoToRegister = { activeTabMode = 2 },
                    onInstantGuestLogin = { onCreateInstantAccount("User", "xmpp.today", "User") }
                )
            } else if (activeTabMode == 1) {
                // ==================== MODE 1: LOGIN EXISTING ACCOUNT ====================
                SplashBrandHeader(
                    title = "Login Ke Akun XMPP",
                    subtitle = "Masukkan Jabber ID dan kata sandi akun Anda untuk terhubung"
                )

                Text(
                    text = "Preset Server XMPP Publik Cepat:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = jidInput.endsWith("@conversations.im"),
                        onClick = {
                            jidInput = "user@conversations.im"
                            hostInput = ""
                            portInput = "5222"
                        },
                        label = { Text("conversations.im", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = jidInput.endsWith("@jabber.at"),
                        onClick = {
                            jidInput = "user@jabber.at"
                            hostInput = ""
                            portInput = "5222"
                        },
                        label = { Text("jabber.at", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = jidInput.endsWith("@xmpp.today"),
                        onClick = {
                            jidInput = "user@xmpp.today"
                            hostInput = ""
                            portInput = "5222"
                        },
                        label = { Text("xmpp.today", fontSize = 11.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = jidInput,
                    onValueChange = { jidInput = it },
                    label = { Text("Jabber ID (JID)") },
                    placeholder = { Text("username@domain.org") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_account_jid"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Kata Sandi Akun") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_account_password"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = nicknameInput,
                    onValueChange = { nicknameInput = it },
                    label = { Text("Nama Panggilan MUC (Otomatis)") },
                    placeholder = { Text("Jika kosong, gunakan username JID") },
                    leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = { hostInput = it },
                        label = { Text("Server Host (Otomatis)") },
                        placeholder = { Text("Derivat dari JID") },
                        leadingIcon = { Icon(Icons.Default.Lan, contentDescription = null) },
                        modifier = Modifier.weight(2f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = portInput,
                        onValueChange = { portInput = it },
                        label = { Text("Port") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = statusMsgInput,
                    onValueChange = { statusMsgInput = it },
                    label = { Text("Pesan Status Presence") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Enkripsi TLS/SSL (StartTLS)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Amankan transmisi data ke port 5222 / 5223", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = useTls, onCheckedChange = { useTls = it })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (connectionStatus == ConnectionStatus.CONNECTED) {
                        OutlinedButton(
                            onClick = onDisconnect,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_disconnect"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(imageVector = Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Putuskan")
                        }
                    }

                    Button(
                        onClick = {
                            val parsedPort = portInput.toIntOrNull() ?: 5222
                            val domain = if (jidInput.contains("@")) jidInput.substringAfter("@") else hostInput
                            val username = if (jidInput.contains("@")) jidInput.substringBefore("@") else jidInput
                            val updatedAccount = XmppAccount(
                                jid = jidInput.trim(),
                                username = username.trim(),
                                domain = domain.trim(),
                                nickname = nicknameInput.trim().ifBlank { username },
                                host = hostInput.trim().ifBlank { domain },
                                port = parsedPort,
                                useTls = useTls,
                                isDemoMode = false,
                                statusMessage = statusMsgInput.trim()
                            )
                            onConnect(updatedAccount, passwordInput)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_connect"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (connectionStatus == ConnectionStatus.CONNECTED) "Perbarui" else "Sambungkan")
                    }
                }

            } else if (activeTabMode == 2) {
                // ==================== MODE 2: REGISTER NEW JID ACCOUNT ====================
                SplashBrandHeader(
                    title = "Daftar Akun XMPP Baru",
                    subtitle = "Buat akun Jabber/XMPP gratis di server publik pilihan Anda"
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.HowToReg,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pendaftaran JID Baru (XEP-0077 In-Band Registration)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Buat akun Jabber/XMPP gratis langsung di server pilihan Anda tanpa perlu verifikasi email rumit.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Pilih Server XMPP Publik Terbuka:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = regDomain == "trashserver.net",
                        onClick = {
                            regDomain = "trashserver.net"
                            regHost = ""
                        },
                        label = { Text("trashserver.net", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = regDomain == "jabber.de",
                        onClick = {
                            regDomain = "jabber.de"
                            regHost = ""
                        },
                        label = { Text("jabber.de", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = regDomain == "magicbroccoli.de",
                        onClick = {
                            regDomain = "magicbroccoli.de"
                            regHost = ""
                        },
                        label = { Text("magicbroccoli.de", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = regDomain == "jabber.at",
                        onClick = {
                            regDomain = "jabber.at"
                            regHost = ""
                        },
                        label = { Text("jabber.at", fontSize = 11.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = regUsername,
                        onValueChange = {
                            regUsername = it
                            regValidationError = null
                        },
                        label = { Text("Username Baru") },
                        placeholder = { Text("misal: ndregil") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("input_reg_username"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = regDomain,
                        onValueChange = {
                            regDomain = it
                            regValidationError = null
                        },
                        label = { Text("Domain Server") },
                        leadingIcon = { Icon(Icons.Default.Domain, contentDescription = null) },
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("input_reg_domain"),
                        singleLine = true
                    )
                }

                if (regUsername.isNotBlank() && regDomain.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "JID Lengkap Anda: ${regUsername.trim()}@${regDomain.trim()}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = regPassword,
                    onValueChange = {
                        regPassword = it
                        regValidationError = null
                    },
                    label = { Text("Kata Sandi") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_reg_password"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = regConfirmPassword,
                    onValueChange = {
                        regConfirmPassword = it
                        regValidationError = null
                    },
                    label = { Text("Konfirmasi Kata Sandi") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_reg_confirm_password"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = regNickname,
                        onValueChange = { regNickname = it },
                        label = { Text("Nama Panggilan (Opsional)") },
                        leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = regEmail,
                        onValueChange = { regEmail = it },
                        label = { Text("Email (Opsional)") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = regHost,
                        onValueChange = { regHost = it },
                        label = { Text("Custom Host (Opsional)") },
                        placeholder = { Text("Sama dgn domain") },
                        leadingIcon = { Icon(Icons.Default.Lan, contentDescription = null) },
                        modifier = Modifier.weight(2f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = regPort,
                        onValueChange = { regPort = it },
                        label = { Text("Port") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Inline validation error
                regValidationError?.let { err ->
                    Text(
                        text = err,
                        color = DndRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Registration Status Banner
                when (registrationState) {
                    is RegistrationState.Registering -> {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = AwayAmber.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, AwayAmber)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = AwayAmber,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Mengirimkan stanza registrasi JID ke server XMPP $regDomain...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    is RegistrationState.Success -> {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = OnlineGreen.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, OnlineGreen)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = OnlineGreen,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Registrasi JID Berhasil!",
                                        fontWeight = FontWeight.Bold,
                                        color = OnlineGreen,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Akun ${registrationState.jid} dibuat. Otomatis menghubungkan ke server...",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                    is RegistrationState.Error -> {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = DndRed.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, DndRed)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Error,
                                        contentDescription = null,
                                        tint = DndRed,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Server Menolak Pendaftaran Otomatis",
                                            fontWeight = FontWeight.Bold,
                                            color = DndRed,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = registrationState.message,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = onResetRegistrationState,
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text("Reset", fontSize = 11.sp)
                                    }
                                }

                                if (regDomain != "trashserver.net") {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            regDomain = "trashserver.net"
                                            regHost = ""
                                            onResetRegistrationState()
                                        },
                                        modifier = Modifier.fillMaxWidth().height(38.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Coba Server trashserver.net (Mendukung XEP-0077)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                    else -> {}
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        val user = regUsername.trim()
                        val dom = regDomain.trim()
                        val pass = regPassword.trim()
                        val confirmPass = regConfirmPassword.trim()

                        if (user.isBlank()) {
                            regValidationError = "Username tidak boleh kosong!"
                            return@Button
                        }
                        if (user.contains("@") || user.contains(" ")) {
                            regValidationError = "Username tidak boleh mengandung '@' atau spasi!"
                            return@Button
                        }
                        if (dom.isBlank()) {
                            regValidationError = "Domain server tidak boleh kosong!"
                            return@Button
                        }
                        if (pass.isBlank() || pass.length < 4) {
                            regValidationError = "Kata sandi minimal 4 karakter!"
                            return@Button
                        }
                        if (pass != confirmPass) {
                            regValidationError = "Konfirmasi kata sandi tidak cocok!"
                            return@Button
                        }

                        regValidationError = null
                        val parsedPort = regPort.toIntOrNull() ?: 5222

                        onRegisterAccount(
                            user,
                            pass,
                            dom,
                            regHost.trim().ifBlank { dom },
                            parsedPort,
                            regNickname.trim().ifBlank { user },
                            regEmail.trim(),
                            regUseTls
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_register_jid"),
                    enabled = registrationState !is RegistrationState.Registering,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (registrationState is RegistrationState.Registering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mendaftarkan Akun ke Server...", fontSize = 13.sp)
                    } else {
                        Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(19.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Daftar Akun Baru (XEP-0077)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                // Web Registration Links Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pendaftaran Web Portal Server Publik:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Sebagian besar server XMPP publik menonaktifkan pendaftaran otomatis via TCP untuk mencegah bot spam. Anda dapat mendaftar gratis di portal web resmi server berikut, lalu masuk melalui tab 'Masuk Akun':",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AssistChip(
                                onClick = { try { uriHandler.openUri("https://trashserver.net/") } catch (_: Exception){} },
                                label = { Text("trashserver.net", fontSize = 10.sp) },
                                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(12.dp)) }
                            )
                            AssistChip(
                                onClick = { try { uriHandler.openUri("https://www.jabber.de/") } catch (_: Exception){} },
                                label = { Text("jabber.de", fontSize = 10.sp) },
                                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(12.dp)) }
                            )
                            AssistChip(
                                onClick = { try { uriHandler.openUri("https://jabber.at/register") } catch (_: Exception){} },
                                label = { Text("jabber.at", fontSize = 10.sp) },
                                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(12.dp)) }
                            )
                            AssistChip(
                                onClick = { try { uriHandler.openUri("https://conversations.im/register/") } catch (_: Exception){} },
                                label = { Text("conversations.im", fontSize = 10.sp) },
                                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(12.dp)) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Firebase Cloud Messaging (FCM) Push Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("fcm_push_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isPushEnabled) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                contentDescription = null,
                                tint = if (isPushEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "FCM Push Notifications",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Pesan latar belakang (Background Push)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isPushEnabled,
                            onCheckedChange = { FcmPushManager.setPushEnabled(context, it) },
                            modifier = Modifier.testTag("switch_fcm_push_enabled")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Memungkinkan penerimaan pesan group chat (MUC) dan bisikan pribadi secara instan saat aplikasi diminimalkan atau ditutup.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "FCM Registration Token:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = fcmToken ?: "Menghubungkan ke layanan FCM...",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            if (!fcmToken.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(fcmToken ?: ""))
                                        testFeedback = "Token FCM berhasil disalin ke clipboard!"
                                    },
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Salin Token",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Salin", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Izin Sistem Notifikasi:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            if (hasNotificationPermission) {
                                FilterChip(
                                    selected = true,
                                    onClick = {},
                                    label = { Text("Aktif", fontSize = 11.sp, color = OnlineGreen) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = OnlineGreen,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                )
                            } else {
                                Button(
                                    onClick = {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    },
                                    modifier = Modifier.height(34.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Minta Izin", fontSize = 11.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                FcmPushManager.simulateBackgroundPushNotification(
                                    context = context,
                                    roomJid = "general@conference.xmpp.today",
                                    senderNick = "ServerBot",
                                    body = "Halo! Ini adalah notifikasi push FCM latar belakang untuk room obrolan.",
                                    isWhisper = false
                                )
                                testFeedback = "Notifikasi push Room berhasil dikirim!"
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_test_room_push"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tes Room", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                FcmPushManager.simulateBackgroundPushNotification(
                                    context = context,
                                    roomJid = "general@conference.xmpp.today",
                                    senderNick = "Alice",
                                    body = "Halo! Ini adalah pesan bisikan pribadi latar belakang via FCM.",
                                    isWhisper = true
                                )
                                testFeedback = "Notifikasi push Bisikan berhasil dikirim!"
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_test_whisper_push"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tes Bisikan", fontSize = 11.sp)
                        }
                    }

                    testFeedback?.let { feedback ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = feedback,
                            fontSize = 11.sp,
                            color = OnlineGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Backup & Restore Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("backup_restore_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Cadangan & Pemulihan Chat (Encrypted Backup)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Ekspor/impor riwayat pesan, ruangan, & akun terenkripsi AES-256",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = backupPassphrase,
                        onValueChange = { backupPassphrase = it },
                        label = { Text("Kata Sandi Enkripsi Cadangan (Opsional)") },
                        placeholder = { Text("Gunakan kunci unik untuk mengamankan cadangan") },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_backup_passphrase"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { exportLauncher.launch("inchat_backup_${System.currentTimeMillis()}.enc") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_export_backup"),
                            enabled = !isBackupLoading,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ekspor", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { importLauncher.launch("*/*") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_import_backup"),
                            enabled = !isBackupLoading,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.HowToReg, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Impor", fontSize = 12.sp)
                        }
                    }

                    if (isBackupLoading) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Memproses enkripsi/dekripsi cadangan...", fontSize = 12.sp)
                        }
                    }

                    backupStatus?.let { status ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (status.contains("Gagal") || status.contains("Error"))
                                    DndRed.copy(alpha = 0.15f)
                                else
                                    OnlineGreen.copy(alpha = 0.15f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = status,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = onClearBackupStatus,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Tutup Status", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // About Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "InChat XMPP client for Android",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "pembuat © 2026 Ndregil",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Client XMPP modern berbasis Smack & Jetpack Compose dengan dukungan Registrasi JID (XEP-0077), Multi-Room Chat, Jingle Call, dan VCard.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun WelcomeSplashScreen(
    onGoToLogin: () -> Unit,
    onGoToRegister: () -> Unit,
    onInstantGuestLogin: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Splash Logo
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                modifier = Modifier
                    .size(130.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(28.dp))
            ) {
                Image(
                    painter = painterResource(id = com.ndregil.inchat.R.drawable.splash_logo),
                    contentDescription = "Logo Splash InChat",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Selamat Datang di InChat",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Aplikasi Obrolan XMPP / Jabber Modern, Aman, & Leluasa",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))

            // Action 1: Login Ke Akun
            Button(
                onClick = onGoToLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_welcome_login"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Masuk Ke Akun Existing", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action 2: Daftar Akun Baru (Register)
            OutlinedButton(
                onClick = onGoToRegister,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_welcome_register"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Daftar Akun XMPP Baru", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action 3: Mode Instant / Tamu Demo
            TextButton(
                onClick = onInstantGuestLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_welcome_guest")
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Masuk Cepat Mode Tamu / Demo",
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun SplashBrandHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.Transparent,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
        ) {
            Image(
                painter = painterResource(id = com.ndregil.inchat.R.drawable.splash_logo),
                contentDescription = "Logo InChat",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
