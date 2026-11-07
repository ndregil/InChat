package com.ndregil.inchat.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ndregil.inchat.R
import com.ndregil.inchat.data.model.ConnectionStatus
import com.ndregil.inchat.data.model.XmppAccount
import com.ndregil.inchat.data.network.fcm.FcmPushManager
import com.ndregil.inchat.ui.RegistrationState
import com.ndregil.inchat.ui.theme.AwayAmber
import com.ndregil.inchat.ui.theme.DndRed
import com.ndregil.inchat.ui.theme.OnlineGreen

data class XmppWebRegistrationProvider(
    val name: String,
    val domain: String,
    val registrationUrl: String,
    val badge: String,
    val description: String
)

val POPULAR_REGISTRATION_SERVERS = listOf(
    XmppWebRegistrationProvider(
        name = "Conversations.im",
        domain = "conversations.im",
        registrationUrl = "https://conversations.im/account/",
        badge = "Utama & Resmi",
        description = "Server standar resmi Conversations, cepat, aman & push notification optimal."
    ),
    XmppWebRegistrationProvider(
        name = "XMPP.jp",
        domain = "xmpp.jp",
        registrationUrl = "https://xmpp.jp/signup",
        badge = "Alternatif",
        description = "Layanan XMPP internasional gratis dengan uptime tinggi."
    ),
    XmppWebRegistrationProvider(
        name = "Trashserver.net",
        domain = "trashserver.net",
        registrationUrl = "https://trashserver.net/register/",
        badge = "Jerman",
        description = "Server Jerman ramah privasi dengan formulir pendaftaran praktis."
    ),
    XmppWebRegistrationProvider(
        name = "XMPP.today",
        domain = "xmpp.today",
        registrationUrl = "https://xmpp.today/",
        badge = "Komunitas",
        description = "Server ramah komunitas bebas digunakan."
    ),
    XmppWebRegistrationProvider(
        name = "Dismail.de",
        domain = "dismail.de",
        registrationUrl = "https://dismail.de/server.html",
        badge = "Privasi",
        description = "Server Jerman ramah privasi dengan standar enkripsi tinggi."
    ),
    XmppWebRegistrationProvider(
        name = "Jabber.at",
        domain = "jabber.at",
        registrationUrl = "https://jabber.at/account/register",
        badge = "Austria",
        description = "Server publik Austria terkemuka via Web Portal."
    )
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    onSolveCaptcha: (com.ndregil.inchat.data.network.CaptchaChallengeData, String) -> Unit = { _, _ -> },
    onExportBackup: (targetUri: android.net.Uri?, passphrase: String) -> Unit = { _, _ -> },
    onImportBackup: (sourceUri: android.net.Uri, passphrase: String) -> Unit = { _, _ -> },
    onClearBackupStatus: () -> Unit = {},
    onUpdateAvatar: (android.net.Uri) -> Unit = {},
    myAvatar: ByteArray? = null,
    lastErrorMessage: String? = null,
    initialTab: Int = -1,
    modifier: Modifier = Modifier
) {
    var activeTabMode by remember(initialTab) {
        mutableIntStateOf(
            if (initialTab >= 0) initialTab 
            else if (connectionStatus != ConnectionStatus.CONNECTED && currentAccount.jid.isBlank()) 0 
            else 1
        )
    }

    var jidInput by remember(currentAccount) { mutableStateOf(currentAccount.jid) }
    var passwordInput by remember { mutableStateOf("") }
    var nicknameInput by remember(currentAccount) { mutableStateOf(currentAccount.nickname) }
    var hostInput by remember(currentAccount) { mutableStateOf(currentAccount.host) }
    var portInput by remember(currentAccount) { mutableStateOf(currentAccount.port.toString()) }
    var useTls by remember(currentAccount) { mutableStateOf(currentAccount.useTls) }
    var statusMsgInput by remember(currentAccount) { mutableStateOf(currentAccount.statusMessage) }

    var regUsername by remember { mutableStateOf("") }
    var regDomain by remember { mutableStateOf("conversations.im") }
    var regPassword by remember { mutableStateOf("") }
    var regConfirmPassword by remember { mutableStateOf("") }
    var regNickname by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regHost by remember { mutableStateOf("conversations.im") }
    var regPort by remember { mutableStateOf("5222") }
    var regUseTls by remember { mutableStateOf(true) }
    var regValidationError by remember { mutableStateOf<String?>(null) }
    var regSubTabMode by remember { mutableIntStateOf(0) }

    // Direct Browser Registration states
    var selectedRegServer by remember { mutableStateOf(POPULAR_REGISTRATION_SERVERS[0]) }
    var browserUrl by remember { mutableStateOf(POPULAR_REGISTRATION_SERVERS[0].registrationUrl) }
    var customUrlInput by remember { mutableStateOf("") }
    var showCustomUrlDialog by remember { mutableStateOf(false) }
    var isFullScreenBrowser by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isLoadingWeb by remember { mutableStateOf(false) }
    var webProgress by remember { mutableFloatStateOf(0f) }
    var postRegUsername by remember { mutableStateOf("") }
    var showLegacyXep0077 by remember { mutableStateOf(false) }

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

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onUpdateAvatar(it) }
    }

    LaunchedEffect(registrationState) {
        if (registrationState is RegistrationState.RedirectRequired) {
            browserUrl = registrationState.url
            val foundServer = POPULAR_REGISTRATION_SERVERS.find {
                it.registrationUrl.equals(registrationState.url, ignoreCase = true)
            }
            if (foundServer != null) {
                selectedRegServer = foundServer
            }
            if (regUsername.isNotBlank()) {
                postRegUsername = regUsername
            }
            activeTabMode = 2 // Auto-switch to Tab 2 (Daftar Baru)
            regSubTabMode = 1 // Auto-switch to Sub-Tab 1 (Portal Web Server)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // Profile Card (When connected)
        if (connectionStatus == ConnectionStatus.CONNECTED) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { avatarPicker.launch("image/*") },
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
                            Image(
                                bitmap = decodedBitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        
                        // Edit Badge
                        Surface(
                            modifier = Modifier.align(Alignment.BottomEnd).size(20.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface)
                        ) {
                            Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.padding(3.dp))
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentAccount.nickname.ifBlank { currentAccount.username },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = currentAccount.jid,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(
                            onClick = { avatarPicker.launch("image/*") },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Perbarui Foto Profil (PEP)", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Connection Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (connectionStatus) {
                    ConnectionStatus.CONNECTED -> OnlineGreen.copy(alpha = 0.08f)
                    ConnectionStatus.CONNECTING, ConnectionStatus.AUTHENTICATING -> AwayAmber.copy(alpha = 0.08f)
                    ConnectionStatus.ERROR -> DndRed.copy(alpha = 0.08f)
                    ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                }
            ),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(
                1.dp,
                when (connectionStatus) {
                    ConnectionStatus.CONNECTED -> OnlineGreen.copy(alpha = 0.4f)
                    ConnectionStatus.CONNECTING, ConnectionStatus.AUTHENTICATING -> AwayAmber.copy(alpha = 0.4f)
                    ConnectionStatus.ERROR -> DndRed.copy(alpha = 0.4f)
                    ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                }
            )
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(
                        when (connectionStatus) {
                            ConnectionStatus.CONNECTED -> OnlineGreen.copy(alpha = 0.2f)
                            ConnectionStatus.CONNECTING, ConnectionStatus.AUTHENTICATING -> AwayAmber.copy(alpha = 0.2f)
                            ConnectionStatus.ERROR -> DndRed.copy(alpha = 0.2f)
                            ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        }
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    when (connectionStatus) {
                        ConnectionStatus.CONNECTED -> Icon(Icons.Default.CheckCircle, null, tint = OnlineGreen, modifier = Modifier.size(28.dp))
                        ConnectionStatus.CONNECTING, ConnectionStatus.AUTHENTICATING -> CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AwayAmber, strokeWidth = 3.dp)
                        ConnectionStatus.ERROR -> Icon(Icons.Default.Error, null, tint = DndRed, modifier = Modifier.size(28.dp))
                        ConnectionStatus.DISCONNECTED -> Icon(Icons.Default.SyncDisabled, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (connectionStatus) {
                            ConnectionStatus.CONNECTED -> "Status: Terhubung"
                            ConnectionStatus.CONNECTING -> "Menghubungkan..."
                            ConnectionStatus.AUTHENTICATING -> "Autentikasi..."
                            ConnectionStatus.ERROR -> "Koneksi Error"
                            ConnectionStatus.DISCONNECTED -> "Status: Terputus"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (connectionStatus) {
                            ConnectionStatus.CONNECTED -> OnlineGreen
                            ConnectionStatus.ERROR -> DndRed
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = if (connectionStatus == ConnectionStatus.CONNECTED) {
                            "JID Aktif: ${currentAccount.jid}"
                        } else if (connectionStatus == ConnectionStatus.ERROR && !lastErrorMessage.isNullOrBlank()) {
                            lastErrorMessage
                        } else {
                            "Siap untuk menyambungkan ke server"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (connectionStatus == ConnectionStatus.ERROR) DndRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Tabs with Icons
        Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            TabRow(
                selectedTabIndex = activeTabMode,
                containerColor = Color.Transparent,
                indicator = { tabPositions ->
                    SecondaryIndicator(modifier = Modifier.tabIndicatorOffset(tabPositions[activeTabMode]), color = MaterialTheme.colorScheme.primary)
                },
                divider = {}
            ) {
                Tab(
                    selected = activeTabMode == 0,
                    onClick = { activeTabMode = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Home, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Welcome", fontSize = 13.sp, fontWeight = if (activeTabMode == 0) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                )
                Tab(
                    selected = activeTabMode == 1,
                    onClick = { activeTabMode = 1; onResetRegistrationState() },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Login, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Masuk", fontSize = 13.sp, fontWeight = if (activeTabMode == 1) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                )
                Tab(
                    selected = activeTabMode == 2,
                    onClick = { activeTabMode = 2; onResetRegistrationState() },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Daftar Baru", fontSize = 13.sp, fontWeight = if (activeTabMode == 2) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                )
            }
        }

        if (activeTabMode == 0) {
            WelcomeSplashScreen(
                onGoToLogin = { activeTabMode = 1 },
                onGoToRegister = { activeTabMode = 2 },
                onInstantGuestLogin = { onCreateInstantAccount("User", "xmpp.today", "User") }
            )
        } else if (activeTabMode == 1) {
            SettingsCategoryHeader("Kredensial Akun", Icons.Default.Security)
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = jidInput, onValueChange = { jidInput = it }, label = { Text("Jabber ID (JID)") }, leadingIcon = { Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(value = passwordInput, onValueChange = { passwordInput = it }, label = { Text("Kata Sandi") }, leadingIcon = { Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
                }
            }

            SettingsCategoryHeader("Konfigurasi Lanjutan", Icons.Default.Lan)
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = hostInput, onValueChange = { hostInput = it }, label = { Text("Host") }, placeholder = { Text("Auto") }, leadingIcon = { Icon(Icons.Default.Dns, null, tint = MaterialTheme.colorScheme.primary) }, modifier = Modifier.weight(2f), singleLine = true, shape = RoundedCornerShape(12.dp))
                        OutlinedTextField(value = portInput, onValueChange = { portInput = it }, label = { Text("Port") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp))
                    }
                    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Enkripsi TLS/SSL", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text("Amankan transmisi data", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = useTls, onCheckedChange = { useTls = it })
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (connectionStatus == ConnectionStatus.CONNECTED) {
                    OutlinedButton(onClick = onDisconnect, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Default.LinkOff, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Putus")
                    }
                }
                Button(onClick = {
                    val port = portInput.toIntOrNull() ?: 5222
                    val rawJid = jidInput.trim()
                    val cleanJid = com.ndregil.inchat.data.network.XmppConnectionManager.sanitizeJid(rawJid)
                    val rawDom = if (cleanJid.contains("@")) cleanJid.substringAfter("@") else hostInput.trim()
                    val cleanDom = com.ndregil.inchat.data.network.XmppConnectionManager.sanitizeDomain(rawDom)
                    val user = if (cleanJid.contains("@")) cleanJid.substringBefore("@") else cleanJid
                    val cleanHost = com.ndregil.inchat.data.network.XmppConnectionManager.sanitizeHost(hostInput.trim(), cleanDom)
                    onConnect(XmppAccount(cleanJid, user.trim(), cleanDom, nicknameInput.trim().ifBlank { user.trim() }, cleanHost, port, useTls, false, statusMsgInput.trim()), passwordInput)
                }, modifier = Modifier.weight(1f).height(52.dp), enabled = connectionStatus != ConnectionStatus.CONNECTING && connectionStatus != ConnectionStatus.AUTHENTICATING, shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.Link, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (connectionStatus == ConnectionStatus.CONNECTED) "Simpan" else "Masuk")
                }
            }
        } else if (activeTabMode == 2) {
            // Direct In-App XMPP Account Registration (XEP-0077) & Web Portal Fallback
            SettingsCategoryHeader("Pendaftaran Akun Baru XMPP", Icons.Default.PersonAdd)

            // Sub-Tab Switcher inside Registration Tab
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { regSubTabMode = 0 },
                        color = if (regSubTabMode == 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.FlashOn,
                                contentDescription = null,
                                tint = if (regSubTabMode == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Daftar Langsung (In-App)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (regSubTabMode == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { regSubTabMode = 1 },
                        color = if (regSubTabMode == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Language,
                                contentDescription = null,
                                tint = if (regSubTabMode == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Portal Web Server",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (regSubTabMode == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (regSubTabMode == 0) {
                // =============================================================
                // SUB-TAB 0: DIRECT IN-APP REGISTRATION (XEP-0077)
                // =============================================================
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Pilih Server XMPP Publik",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            "Pilih salah satu server gratis teruji berikut yang mendukung pendaftaran langsung (In-Band Registration XEP-0077) tanpa captcha:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val directServers = listOf(
                            Pair("conversations.im", "Utama & Resmi"),
                            Pair("xmpp.jp", "Alternatif"),
                            Pair("trashserver.net", "Jerman"),
                            Pair("xmpp.today", "Komunitas"),
                            Pair("dismail.de", "Privasi"),
                            Pair("jabber.de", "Populer")
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            directServers.forEach { (srvDomain, badgeLabel) ->
                                val isSelected = regDomain.equals(srvDomain, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        regDomain = srvDomain
                                        regHost = srvDomain
                                        regValidationError = null
                                        if (badgeLabel == "Web Portal") {
                                            regSubTabMode = 1
                                            val found = POPULAR_REGISTRATION_SERVERS.find { it.domain.equals(srvDomain, ignoreCase = true) }
                                            if (found != null) {
                                                selectedRegServer = found
                                                browserUrl = found.registrationUrl
                                            } else {
                                                browserUrl = "https://$srvDomain"
                                            }
                                        }
                                    },
                                    label = { Text("$srvDomain ($badgeLabel)", fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp)) }
                                    } else null,
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        Text(
                            "Formulir Registrasi Akun Baru",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = regDomain,
                            onValueChange = { regDomain = it; regHost = it },
                            label = { Text("Domain Server XMPP") },
                            placeholder = { Text("contoh: trashserver.net") },
                            leadingIcon = { Icon(Icons.Default.Dns, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = regUsername,
                            onValueChange = { regUsername = it; regValidationError = null },
                            label = { Text("Username Baru") },
                            placeholder = { Text("contoh: pengguna_baru") },
                            leadingIcon = { Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingIcon = {
                                Text(
                                    if (regDomain.isNotBlank()) "@${regDomain.trim()}" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 10.dp)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = regPassword,
                            onValueChange = { regPassword = it; regValidationError = null },
                            label = { Text("Kata Sandi") },
                            leadingIcon = { Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary) },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = regConfirmPassword,
                            onValueChange = { regConfirmPassword = it; regValidationError = null },
                            label = { Text("Konfirmasi Kata Sandi") },
                            leadingIcon = { Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary) },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = regNickname,
                            onValueChange = { regNickname = it },
                            label = { Text("Nama Panggilan (Nickname Opsional)") },
                            leadingIcon = { Icon(Icons.Default.Badge, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        regValidationError?.let { err ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                            ) {
                                Text(
                                    text = err,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (regUsername.isBlank()) {
                                    regValidationError = "Username tidak boleh kosong"
                                    return@Button
                                }
                                if (regPassword.length < 4) {
                                    regValidationError = "Kata sandi minimal 4 karakter"
                                    return@Button
                                }
                                if (regPassword != regConfirmPassword) {
                                    regValidationError = "Konfirmasi kata sandi tidak cocok"
                                    return@Button
                                }
                                if (regDomain.isBlank()) {
                                    regValidationError = "Domain server tidak boleh kosong"
                                    return@Button
                                }
                                if (!Regex("""^[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$""").matches(regDomain.trim())) {
                                    regValidationError = "Format domain tidak valid"
                                    return@Button
                                }
                                if (regEmail.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(regEmail.trim()).matches()) {
                                    regValidationError = "Format email tidak valid"
                                    return@Button
                                }

                                regValidationError = null
                                onRegisterAccount(
                                    regUsername.trim(),
                                    regPassword.trim(),
                                    regDomain.trim(),
                                    regHost.trim().ifBlank { regDomain.trim() },
                                    regPort.toIntOrNull() ?: 5222,
                                    regNickname.trim().ifBlank { regUsername.trim() },
                                    regEmail.trim(),
                                    regUseTls
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            enabled = registrationState !is RegistrationState.Registering,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (registrationState is RegistrationState.Registering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Mendaftarkan Akun Ke Server...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.PersonAdd, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Daftar Akun Baru & Langsung Masuk", fontWeight = FontWeight.Bold)
                            }
                        }

                        when (registrationState) {
                            is RegistrationState.Success -> {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = OnlineGreen.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, OnlineGreen.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, null, tint = OnlineGreen)
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                "Pendaftaran Akun Berhasil!",
                                                fontWeight = FontWeight.Bold,
                                                color = OnlineGreen
                                            )
                                            Text(
                                                "JID: ${registrationState.jid}\nMenghubungkan otomatis ke server...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                            is RegistrationState.Error -> {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = DndRed.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, DndRed.copy(alpha = 0.4f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Error, null, tint = DndRed)
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                "Pendaftaran Gagal",
                                                fontWeight = FontWeight.Bold,
                                                color = DndRed
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = registrationState.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            val targetWebUrl = if (regDomain.contains("conversations.im", ignoreCase = true)) {
                                                "https://conversations.im/account/"
                                            } else if (regDomain.contains("trashserver.net", ignoreCase = true)) {
                                                "https://trashserver.net/register/"
                                            } else {
                                                "https://${regDomain.trim()}"
                                            }

                                            Button(
                                                onClick = {
                                                    regSubTabMode = 1
                                                    browserUrl = targetWebUrl
                                                    val found = POPULAR_REGISTRATION_SERVERS.find { it.domain.equals(regDomain.trim(), ignoreCase = true) }
                                                    if (found != null) selectedRegServer = found
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.Language, null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Buka Web Portal ${regDomain.trim()}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            if (!regDomain.equals("xmpp.jp", ignoreCase = true)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        regDomain = "xmpp.jp"
                                                        regHost = "xmpp.jp"
                                                        onResetRegistrationState()
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Icon(Icons.Default.FlashOn, null, modifier = Modifier.size(14.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Coba xmpp.jp (Direct OK)", fontSize = 11.sp)
                                                }
                                            }

                                            TextButton(
                                                onClick = onResetRegistrationState,
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("Coba Lagi", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            } else {
                // =============================================================
                // SUB-TAB 1: IN-APP BROWSER REGISTRATION FOR CAPTCHA WEB SERVERS
                // =============================================================
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Language, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Web Portal Server Browser",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Server seperti Conversations.im memerlukan registrasi via Web Portal karena proteksi anti-bot captcha. Selesaikan pendaftaran di situs resmi server berikut tanpa keluar aplikasi.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Server Selection Chips
                Text(
                    "Pilih Web Portal Server:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    POPULAR_REGISTRATION_SERVERS.forEach { provider ->
                        val isSelected = selectedRegServer == provider
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedRegServer = provider
                                browserUrl = provider.registrationUrl
                                webViewInstance?.loadUrl(provider.registrationUrl)
                            },
                            label = {
                                Text(provider.name, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    FilterChip(
                        selected = false,
                        onClick = { showCustomUrlDialog = true },
                        label = { Text("+ URL Kustom", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.AddLink, null, modifier = Modifier.size(16.dp)) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Selected Server Description badge
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${selectedRegServer.badge}: ${selectedRegServer.description}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Browser Frame Container
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column {
                        // Browser Top Chrome
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { webViewInstance?.goBack() },
                                    enabled = canGoBack,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { webViewInstance?.goForward() },
                                    enabled = canGoForward,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.ArrowForward, contentDescription = "Maju", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { webViewInstance?.reload() },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Muat Ulang", modifier = Modifier.size(18.dp))
                                }

                                // Address Bar Pill
                                Surface(
                                    modifier = Modifier.weight(1f).height(34.dp).padding(horizontal = 4.dp),
                                    shape = RoundedCornerShape(17.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Lock, null, tint = OnlineGreen, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = browserUrl.replace("https://", ""),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { isFullScreenBrowser = true },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.Fullscreen, contentDescription = "Layar Penuh", modifier = Modifier.size(20.dp))
                                }

                                IconButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(browserUrl))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = "Buka di Browser Luar", modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        // Progress bar
                        if (isLoadingWeb) {
                            LinearProgressIndicator(
                                progress = { webProgress },
                                modifier = Modifier.fillMaxWidth().height(2.5.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Spacer(Modifier.height(2.5.dp))
                        }

                        // The WebView itself inside inline container
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(480.dp)
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        settings.apply {
                                            javaScriptEnabled = true
                                            domStorageEnabled = true
                                            databaseEnabled = true
                                            javaScriptCanOpenWindowsAutomatically = true
                                            loadWithOverviewMode = true
                                            useWideViewPort = true
                                            builtInZoomControls = true
                                            displayZoomControls = false
                                            setSupportZoom(true)
                                            allowContentAccess = true
                                            allowFileAccess = true
                                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                            userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                        }
                                        android.webkit.CookieManager.getInstance().let { cm ->
                                            cm.setAcceptCookie(true)
                                            cm.setAcceptThirdPartyCookies(this, true)
                                        }
                                        webViewClient = object : WebViewClient() {
                                            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                                try {
                                                    (view?.parent as? ViewGroup)?.removeView(view)
                                                    view?.destroy()
                                                } catch (_: Exception) {}
                                                isLoadingWeb = false
                                                webViewInstance = null
                                                return true
                                            }

                                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                                super.onPageStarted(view, url, favicon)
                                                if (url != null) browserUrl = url
                                                isLoadingWeb = true
                                                canGoBack = view?.canGoBack() == true
                                                canGoForward = view?.canGoForward() == true
                                            }

                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                super.onPageFinished(view, url)
                                                if (url != null) browserUrl = url
                                                isLoadingWeb = false
                                                canGoBack = view?.canGoBack() == true
                                                canGoForward = view?.canGoForward() == true
                                            }
                                        }
                                        webChromeClient = object : WebChromeClient() {
                                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                                super.onProgressChanged(view, newProgress)
                                                webProgress = newProgress / 100f
                                                if (newProgress >= 100) isLoadingWeb = false
                                            }
                                        }
                                        loadUrl(browserUrl)
                                        webViewInstance = this
                                    }
                                },
                                update = { view ->
                                    webViewInstance = view
                                },
                                onRelease = { wv ->
                                    try {
                                        wv.stopLoading()
                                        wv.webViewClient = WebViewClient()
                                        wv.loadUrl("about:blank")
                                        (wv.parent as? ViewGroup)?.removeView(wv)
                                        wv.destroy()
                                    } catch (_: Exception) {}
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Post-Registration Quick Login Bridge
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = OnlineGreen, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sudah Selesai Mendaftar di Web?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        }
                        Text(
                            "Ketik username baru Anda untuk langsung menuju ke halaman login:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = postRegUsername,
                                onValueChange = { postRegUsername = it },
                                label = { Text("Username Baru") },
                                placeholder = { Text("contoh: budi") },
                                trailingIcon = {
                                    Text(
                                        "@${selectedRegServer.domain}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 10.dp)
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Button(
                                onClick = {
                                    val cleanUser = postRegUsername.trim().substringBefore("@")
                                    val fullJid = if (cleanUser.isNotBlank()) "$cleanUser@${selectedRegServer.domain}" else "@${selectedRegServer.domain}"
                                    jidInput = fullJid
                                    hostInput = selectedRegServer.domain
                                    activeTabMode = 1 // Switch to Masuk Tab
                                },
                                enabled = postRegUsername.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(54.dp)
                            ) {
                                Icon(Icons.Default.Login, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Masuk", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Native CAPTCHA Challenge Screen (XEP-0158 / XEP-0231 Bits of Binary)
            if (registrationState is RegistrationState.CaptchaChallengeRequired) {
                RegistrationCaptchaScreen(
                    challenge = registrationState.challenge,
                    onSolveCaptcha = { answer -> onSolveCaptcha(registrationState.challenge, answer) },
                    onCancel = onResetRegistrationState
                )
            }

            // Custom URL Dialog
            if (showCustomUrlDialog) {
                AlertDialog(
                    onDismissRequest = { showCustomUrlDialog = false },
                    title = { Text("Buka URL Registrasi Kustom") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Masukkan alamat web portal server XMPP Anda:", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = customUrlInput,
                                onValueChange = { customUrlInput = it },
                                placeholder = { Text("https://example.org/register") },
                                label = { Text("URL Web") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            var targetUrl = customUrlInput.trim()
                            if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                                targetUrl = "https://$targetUrl"
                            }
                            browserUrl = targetUrl
                            webViewInstance?.loadUrl(targetUrl)
                            showCustomUrlDialog = false
                        }) {
                            Text("Buka di Browser")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCustomUrlDialog = false }) { Text("Batal") }
                    }
                )
            }

            // Full Screen In-App Browser Dialog
            if (isFullScreenBrowser) {
                Dialog(
                    onDismissRequest = { isFullScreenBrowser = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    BackHandler {
                        if (webViewInstance?.canGoBack() == true) {
                            webViewInstance?.goBack()
                        } else {
                            isFullScreenBrowser = false
                        }
                    }

                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Lock, null, tint = OnlineGreen, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(selectedRegServer.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text(browserUrl.replace("https://", ""), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                },
                                navigationIcon = {
                                    IconButton(onClick = { isFullScreenBrowser = false }) {
                                        Icon(Icons.Default.Close, contentDescription = "Tutup Layar Penuh")
                                    }
                                },
                                actions = {
                                    IconButton(onClick = { webViewInstance?.goBack() }, enabled = canGoBack) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                                    }
                                    IconButton(onClick = { webViewInstance?.goForward() }, enabled = canGoForward) {
                                        Icon(Icons.Default.ArrowForward, contentDescription = "Maju")
                                    }
                                    IconButton(onClick = { webViewInstance?.reload() }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Muat Ulang")
                                    }
                                    IconButton(onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(browserUrl))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }) {
                                        Icon(Icons.Default.OpenInBrowser, contentDescription = "Buka di Browser Eksternal")
                                    }
                                }
                            )
                        },
                        bottomBar = {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                tonalElevation = 6.dp,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = postRegUsername,
                                        onValueChange = { postRegUsername = it },
                                        placeholder = { Text("Username Baru") },
                                        trailingIcon = { Text("@${selectedRegServer.domain}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp)) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    Button(
                                        onClick = {
                                            val cleanUser = postRegUsername.trim().substringBefore("@")
                                            val fullJid = if (cleanUser.isNotBlank()) "$cleanUser@${selectedRegServer.domain}" else "@${selectedRegServer.domain}"
                                            jidInput = fullJid
                                            hostInput = selectedRegServer.domain
                                            isFullScreenBrowser = false
                                            activeTabMode = 1 // Switch to Masuk Tab
                                        },
                                        enabled = postRegUsername.isNotBlank(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Selesai & Masuk", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        settings.apply {
                                            javaScriptEnabled = true
                                            domStorageEnabled = true
                                            databaseEnabled = true
                                            javaScriptCanOpenWindowsAutomatically = true
                                            loadWithOverviewMode = true
                                            useWideViewPort = true
                                            builtInZoomControls = true
                                            displayZoomControls = false
                                            setSupportZoom(true)
                                            allowContentAccess = true
                                            allowFileAccess = true
                                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                            userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                        }
                                        android.webkit.CookieManager.getInstance().let { cm ->
                                            cm.setAcceptCookie(true)
                                            cm.setAcceptThirdPartyCookies(this, true)
                                        }
                                        webViewClient = object : WebViewClient() {
                                            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                                try {
                                                    (view?.parent as? ViewGroup)?.removeView(view)
                                                    view?.destroy()
                                                } catch (_: Exception) {}
                                                isLoadingWeb = false
                                                webViewInstance = null
                                                return true
                                            }

                                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                                super.onPageStarted(view, url, favicon)
                                                if (url != null) browserUrl = url
                                                isLoadingWeb = true
                                                canGoBack = view?.canGoBack() == true
                                                canGoForward = view?.canGoForward() == true
                                            }

                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                super.onPageFinished(view, url)
                                                if (url != null) browserUrl = url
                                                isLoadingWeb = false
                                                canGoBack = view?.canGoBack() == true
                                                canGoForward = view?.canGoForward() == true
                                            }
                                        }
                                        webChromeClient = object : WebChromeClient() {
                                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                                super.onProgressChanged(view, newProgress)
                                                webProgress = newProgress / 100f
                                                if (newProgress >= 100) isLoadingWeb = false
                                            }
                                        }
                                        loadUrl(browserUrl)
                                        webViewInstance = this
                                    }
                                },
                                update = { view ->
                                    webViewInstance = view
                                },
                                onRelease = { wv ->
                                    try {
                                        wv.stopLoading()
                                        wv.webViewClient = WebViewClient()
                                        wv.loadUrl("about:blank")
                                        (wv.parent as? ViewGroup)?.removeView(wv)
                                        wv.destroy()
                                    } catch (_: Exception) {}
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            if (isLoadingWeb) {
                                LinearProgressIndicator(
                                    progress = { webProgress },
                                    modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.TopCenter),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // FCM
        SettingsCategoryHeader("Push Service", Icons.Default.NotificationsActive)
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
            Column(Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("FCM Push", fontWeight = FontWeight.Bold); Text("Terima pesan saat offline", style = MaterialTheme.typography.labelSmall) }
                    Switch(checked = isPushEnabled, onCheckedChange = { FcmPushManager.setPushEnabled(context, it) })
                }
            }
        }

        // Backup
        SettingsCategoryHeader("Cadangan", Icons.Default.Backup)
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = backupPassphrase, onValueChange = { backupPassphrase = it }, label = { Text("Kunci AES-256") }, leadingIcon = { Icon(Icons.Default.Key, null) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { exportLauncher.launch("inchat_backup_${System.currentTimeMillis()}.enc") }, modifier = Modifier.weight(1f), enabled = !isBackupLoading, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.SystemUpdateAlt, null); Spacer(Modifier.width(4.dp)); Text("Ekspor") }
                    OutlinedButton(onClick = { importLauncher.launch("*/*") }, modifier = Modifier.weight(1f), enabled = !isBackupLoading, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Sync, null); Spacer(Modifier.width(4.dp)); Text("Impor") }
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun WelcomeSplashScreen(onGoToLogin: () -> Unit, onGoToRegister: () -> Unit, onInstantGuestLogin: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(modifier = Modifier.size(100.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f), border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))) {
                Image(painterResource(R.drawable.splash_logo), "Logo", Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Selamat Datang", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Aplikasi chat XMPP modern dan aman.", textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
            Button(onClick = onGoToLogin, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Login, null); Spacer(Modifier.width(8.dp)); Text("Masuk Ke Akun") }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onGoToRegister, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Language, null); Spacer(Modifier.width(8.dp)); Text("Daftar Akun Baru") }
        }
    }
}

@Composable
fun SplashBrandHeader(title: String, subtitle: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = Color.Transparent) { Image(painterResource(R.drawable.splash_logo), null, Modifier.fillMaxSize()) }
        Spacer(modifier = Modifier.width(16.dp))
        Column { Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); Text(subtitle, style = MaterialTheme.typography.bodySmall) }
    }
}
