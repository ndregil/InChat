package com.ndregil.inchat.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
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
    onSolveCaptcha: (com.ndregil.inchat.data.network.CaptchaChallengeData, String, Map<String, String>) -> Unit = { _, _, _ -> },
    onExportBackup: (targetUri: android.net.Uri?, passphrase: String) -> Unit = { _, _ -> },
    onImportBackup: (sourceUri: android.net.Uri, passphrase: String) -> Unit = { _, _ -> },
    onClearBackupStatus: () -> Unit = {},
    onUpdateAvatar: (android.net.Uri) -> Unit = {},
    onSwitchAccount: () -> Unit = {},
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
    var regDomain by remember { mutableStateOf("jabber.de") }
    var isCustomServerMode by remember { mutableStateOf(false) }
    var regPassword by remember { mutableStateOf("") }
    var regConfirmPassword by remember { mutableStateOf("") }
    var regNickname by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regHost by remember { mutableStateOf("") }
    var regPort by remember { mutableStateOf("5222") }
    var regUseTls by remember { mutableStateOf(true) }
    var showRegAdvancedOptions by remember { mutableStateOf(false) }
    var regValidationError by remember { mutableStateOf<String?>(null) }

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



    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(4.dp))

        // Profile Card (When connected)
        if (connectionStatus == ConnectionStatus.CONNECTED) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
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
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (connectionStatus) {
                    ConnectionStatus.CONNECTED -> OnlineGreen.copy(alpha = 0.08f)
                    ConnectionStatus.CONNECTING, ConnectionStatus.AUTHENTICATING -> AwayAmber.copy(alpha = 0.08f)
                    ConnectionStatus.ERROR -> DndRed.copy(alpha = 0.08f)
                    ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                }
            ),
            shape = RoundedCornerShape(16.dp),
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
                modifier = Modifier.padding(12.dp),
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsCategoryHeader("Kredensial Akun", Icons.Default.Security)
                if (jidInput.isNotBlank() || hostInput.isNotBlank() || connectionStatus == ConnectionStatus.CONNECTED) {
                    TextButton(
                        onClick = {
                            jidInput = ""
                            passwordInput = ""
                            nicknameInput = ""
                            hostInput = ""
                            portInput = "5222"
                            statusMsgInput = ""
                            useTls = true
                            onSwitchAccount()
                        }
                    ) {
                        Icon(Icons.Default.SwitchAccount, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ganti Akun Baru", fontSize = 12.sp)
                    }
                }
            }
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
                    OutlinedButton(
                        onClick = {
                            jidInput = ""
                            passwordInput = ""
                            nicknameInput = ""
                            hostInput = ""
                            portInput = "5222"
                            statusMsgInput = ""
                            useTls = true
                            onSwitchAccount()
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.SwitchAccount, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ganti Akun")
                    }
                }
                Button(onClick = {
                    val port = portInput.toIntOrNull() ?: 5222
                    val rawJid = jidInput.trim()
                    val cleanJid = com.ndregil.inchat.data.network.XmppConnectionManager.sanitizeJid(rawJid)
                    val rawDom = if (cleanJid.contains("@")) cleanJid.substringAfter("@") else hostInput.trim()
                    val cleanDom = com.ndregil.inchat.data.network.XmppConnectionManager.sanitizeDomain(rawDom)
                    val user = if (cleanJid.contains("@")) cleanJid.substringBefore("@") else cleanJid
                    val cleanHost = com.ndregil.inchat.data.network.XmppConnectionManager.sanitizeHost(hostInput.trim())
                    onConnect(XmppAccount(cleanJid, user.trim(), cleanDom, nicknameInput.trim().ifBlank { user.trim() }, cleanHost, port, useTls, false, statusMsgInput.trim()), passwordInput)
                }, modifier = Modifier.weight(1f).height(52.dp), enabled = connectionStatus != ConnectionStatus.CONNECTING && connectionStatus != ConnectionStatus.AUTHENTICATING, shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.Link, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (connectionStatus == ConnectionStatus.CONNECTED) "Simpan" else "Masuk")
                }
            }
        } else if (activeTabMode == 2) {
            // Direct In-App XMPP Account Registration (XEP-0077)
            SettingsCategoryHeader("Pendaftaran Akun Baru XMPP", Icons.Default.PersonAdd)

            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    var isServerDropdownExpanded by remember { mutableStateOf(false) }

                    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

                    ExposedDropdownMenuBox(
                        expanded = isServerDropdownExpanded,
                        onExpandedChange = {
                            keyboardController?.hide()
                            isServerDropdownExpanded = !isServerDropdownExpanded
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = if (isCustomServerMode) "Server Kustom (Manual)" else regDomain,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Pilihan Server Pendaftaran") },
                            placeholder = { Text("Pilih server atau server kustom...") },
                            leadingIcon = { Icon(Icons.Default.Dns, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isServerDropdownExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .clickable {
                                    keyboardController?.hide()
                                    isServerDropdownExpanded = !isServerDropdownExpanded
                                },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = isServerDropdownExpanded,
                            onDismissRequest = { isServerDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Custom Server Option at top
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Server Kustom (Input Manual)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Text("Manual", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                onClick = {
                                    isCustomServerMode = true
                                    regValidationError = null
                                    isServerDropdownExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            PROVIDERS_A_INBAND.forEach { item ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(item.domain, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Spacer(Modifier.width(24.dp))
                                            Text(
                                                text = item.description,
                                                fontSize = 12.sp,
                                                color = if (item.requiresCaptcha) MaterialTheme.colorScheme.onSurfaceVariant else OnlineGreen
                                            )
                                        }
                                    },
                                    onClick = {
                                        keyboardController?.hide()
                                        isCustomServerMode = false
                                        regDomain = item.domain
                                        regHost = ""
                                        regValidationError = null
                                        isServerDropdownExpanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }

                    // If Custom Server mode is selected, show custom domain input and domain suggestion chip
                    if (isCustomServerMode) {
                        val suggestedDomain = remember(regDomain) {
                            com.ndregil.inchat.data.network.XmppConnectionManager.suggestDomainCorrection(regDomain)
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Pengaturan Server Kustom", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                OutlinedTextField(
                                    value = regDomain,
                                    onValueChange = {
                                        regDomain = it
                                        regValidationError = null
                                    },
                                    label = { Text("Domain Server XMPP") },
                                    placeholder = { Text("contoh: xmpp.domain.com") },
                                    leadingIcon = { Icon(Icons.Default.Dns, null, tint = MaterialTheme.colorScheme.primary) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                if (suggestedDomain != null && !suggestedDomain.equals(regDomain.trim(), ignoreCase = true)) {
                                    AssistChip(
                                        onClick = { regDomain = suggestedDomain },
                                        label = { Text("Mungkin maksud Anda: $suggestedDomain?") },
                                        leadingIcon = {
                                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    )
                                }
                            }
                        }
                    }

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

                    OutlinedTextField(
                        value = regEmail,
                        onValueChange = { regEmail = it; regValidationError = null },
                        label = { Text("Email (Opsional / Diminta Sebagian Server)") },
                        leadingIcon = { Icon(Icons.Default.Email, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reg_email_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    TextButton(
                        onClick = { showRegAdvancedOptions = !showRegAdvancedOptions },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("btn_reg_advanced_toggle")
                    ) {
                        Icon(
                            imageVector = if (showRegAdvancedOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (showRegAdvancedOptions) "Sembunyikan Koneksi Lanjutan" else "Koneksi Lanjutan (Host SRV, Port, TLS)",
                            fontSize = 12.sp
                        )
                    }

                    if (showRegAdvancedOptions || isCustomServerMode) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = regHost,
                                        onValueChange = { regHost = it },
                                        label = { Text("Host Manual (Kosongkan = Auto SRV)") },
                                        placeholder = { Text("Auto (DNS SRV)") },
                                        modifier = Modifier.weight(2f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    OutlinedTextField(
                                        value = regPort,
                                        onValueChange = { regPort = it },
                                        label = { Text("Port") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Enkripsi TLS/SSL Wajib", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        Text("Matikan hanya jika server memakai sertifikat tidak terverifikasi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = regUseTls,
                                        onCheckedChange = { regUseTls = it },
                                        modifier = Modifier.testTag("switch_reg_tls")
                                    )
                                }
                            }
                        }
                    }

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
                            val sanitizedDom = com.ndregil.inchat.data.network.XmppConnectionManager.sanitizeDomain(regDomain)
                            if (!Regex("""^[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$""").matches(sanitizedDom)) {
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
                                sanitizedDom,
                                regHost.trim(),
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
                        is RegistrationState.WebRegistrationRequired -> {
                            val uriHandler = LocalUriHandler.current
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("web_registration_card"),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Pendaftaran Lewat Web (${registrationState.domain})",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = registrationState.instructions?.takeIf { it.isNotBlank() }
                                            ?: "Server ini meminta pendaftaran dilakukan melalui halaman web berikut:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = registrationState.url,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                try {
                                                    uriHandler.openUri(registrationState.url)
                                                } catch (_: Exception) {}
                                            },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.testTag("btn_open_web_register")
                                        ) {
                                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Buka Halaman Daftar", fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(registrationState.url))
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Salin URL", fontSize = 12.sp)
                                        }
                                        TextButton(
                                            onClick = onResetRegistrationState,
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Text("Tutup", fontSize = 12.sp)
                                        }
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
                                        if (registrationState.isTlsError && regUseTls) {
                                            OutlinedButton(
                                                onClick = {
                                                    regUseTls = false
                                                    showRegAdvancedOptions = true
                                                    onRegisterAccount(
                                                        regUsername.trim(),
                                                        regPassword.trim(),
                                                        com.ndregil.inchat.data.network.XmppConnectionManager.sanitizeDomain(regDomain),
                                                        regHost.trim(),
                                                        regPort.toIntOrNull() ?: 5222,
                                                        regNickname.trim().ifBlank { regUsername.trim() },
                                                        regEmail.trim(),
                                                        false
                                                    )
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.testTag("btn_retry_without_tls")
                                            ) {
                                                Icon(Icons.Default.NoEncryption, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Coba Tanpa TLS Wajib", fontSize = 11.sp)
                                            }
                                        }
                                        if (!regDomain.equals("xmpp.jp", ignoreCase = true)) {
                                            OutlinedButton(
                                                onClick = {
                                                    regDomain = "xmpp.jp"
                                                    regHost = ""
                                                    onResetRegistrationState()
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.FlashOn, null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Coba xmpp.jp", fontSize = 11.sp)
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
        
        // Native CAPTCHA Challenge Screen (XEP-0158 / XEP-0231 Bits of Binary)
        if (registrationState is RegistrationState.CaptchaChallengeRequired) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ) {
                RegistrationCaptchaScreen(
                    challenge = registrationState.challenge,
                    onSolveCaptcha = { answer, extraFields ->
                        onSolveCaptcha(registrationState.challenge, answer, extraFields)
                    },
                    onCancel = onResetRegistrationState
                )
            }
        }
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

data class XmppCategoryAProvider(
    val domain: String,
    val requiresCaptcha: Boolean,
    val description: String
)

val PROVIDERS_A_INBAND = listOf(
    // Bebas Captcha (11 Servers)
    XmppCategoryAProvider("jabber.de", false, "Jerman • Bebas Captcha"),
    XmppCategoryAProvider("jabber.fr", false, "Prancis • Bebas Captcha"),
    XmppCategoryAProvider("yax.im", false, "Populer • Bebas Captcha"),
    XmppCategoryAProvider("carnetnoir.fr", false, "Prancis • Bebas Captcha"),
    XmppCategoryAProvider("chat.between-us.online", false, "Global • Bebas Captcha"),
    XmppCategoryAProvider("jabber-germany.de", false, "Jerman • Bebas Captcha"),
    XmppCategoryAProvider("jabber.foo", false, "Global • Bebas Captcha"),
    XmppCategoryAProvider("jabber.vg", false, "Global • Bebas Captcha"),
    XmppCategoryAProvider("jabber.world", false, "Global • Bebas Captcha"),
    XmppCategoryAProvider("lain.rocks", false, "Komunitas • Bebas Captcha"),
    XmppCategoryAProvider("xmpp.party", false, "Komunitas • Bebas Captcha"),

    // Captcha Native (18 Servers)
    XmppCategoryAProvider("xmpp.jp", true, "Jepang • Captcha Native"),
    XmppCategoryAProvider("conversations.im", true, "Resmi • Captcha Native"),
    XmppCategoryAProvider("07f.de", true, "Jerman • Captcha Native"),
    XmppCategoryAProvider("chapril.org", true, "Prancis • Captcha Native"),
    XmppCategoryAProvider("chalec.org", true, "Prancis • Captcha Native"),
    XmppCategoryAProvider("chatterboxtown.us", true, "AS • Captcha Native"),
    XmppCategoryAProvider("hookipa.net", true, "Jerman • Captcha Native"),
    XmppCategoryAProvider("jabbxi.de", true, "Jerman • Captcha Native"),
    XmppCategoryAProvider("jabjab.de", true, "Jerman • Captcha Native"),
    XmppCategoryAProvider("magicbroccoli.de", true, "Jerman • Captcha Native"),
    XmppCategoryAProvider("nixnet.services", true, "Global • Captcha Native"),
    XmppCategoryAProvider("og.im", true, "Privasi • Captcha Native"),
    XmppCategoryAProvider("openim.nl", true, "Belanda • Captcha Native"),
    XmppCategoryAProvider("priv.vc", true, "Privasi • Captcha Native"),
    XmppCategoryAProvider("projectsegfau.lt", true, "Komunitas • Captcha Native"),
    XmppCategoryAProvider("rows.im", true, "Privasi • Captcha Native"),
    XmppCategoryAProvider("step.im", true, "Global • Captcha Native"),
    XmppCategoryAProvider("yourdata.forsale", true, "Privasi • Captcha Native")
)
