package com.ndregil.inchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.ndregil.inchat.data.model.XmppMessage
import com.ndregil.inchat.data.model.MucAffiliate
import com.ndregil.inchat.data.model.OccupantRole
import com.ndregil.inchat.data.model.OccupantAffiliation
import org.jivesoftware.smackx.muc.RoomInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomConfigurationDialog(
    roomJid: String,
    initialInfo: RoomInfo?,
    moderationMessages: List<XmppMessage> = emptyList(),
    isLoading: Boolean = false,
    onLoadAffiliations: () -> Unit = {},
    ownersList: List<MucAffiliate> = emptyList(),
    adminsList: List<MucAffiliate> = emptyList(),
    membersList: List<MucAffiliate> = emptyList(),
    outcastsList: List<MucAffiliate> = emptyList(),
    isLoadingAffiliations: Boolean = false,
    onGrantOwnership: (String) -> Unit = {},
    onGrantAdmin: (String) -> Unit = {},
    onGrantMembership: (String) -> Unit = {},
    onBanUser: (String, String?) -> Unit = { _, _ -> },
    onRevokeAffiliation: (String) -> Unit = {},
    myRole: OccupantRole = OccupantRole.NONE,
    onDismiss: () -> Unit,
    onUpdateNickname: (String) -> Unit = {},
    onSaveConfig: (
        roomName: String?,
        description: String?,
        subject: String?,
        isPersistent: Boolean,
        isPublic: Boolean,
        isMembersOnly: Boolean,
        isModerated: Boolean,
        allowSubjectChange: Boolean,
        password: String?
    ) -> Unit
) {
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Settings, 1 = Moderation

    LaunchedEffect(activeTab) {
        if (activeTab == 1) {
            onLoadAffiliations()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 680.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (activeTab == 0) Icons.Default.AdminPanelSettings else Icons.Default.Gavel,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (activeTab == 0) "Konfigurasi Ruangan MUC" else "Administrasi & Moderasi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = roomJid,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text("Pengaturan", fontSize = 13.sp) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    if (myRole == OccupantRole.OWNER || myRole == OccupantRole.ADMIN) {
                        Tab(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            text = { Text("Moderasi", fontSize = 13.sp) },
                            icon = { Icon(Icons.Default.Gavel, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
            }
        },
        text = {
            if (activeTab == 0) {
                RoomConfigurationContent(
                    initialInfo = initialInfo,
                    isLoading = isLoading,
                    onSaveConfig = onSaveConfig,
                    onUpdateNickname = onUpdateNickname
                )
            } else {
                ModerationTabContent(
                    owners = ownersList,
                    admins = adminsList,
                    members = membersList,
                    outcasts = outcastsList,
                    isLoading = isLoadingAffiliations,
                    onGrantOwnership = onGrantOwnership,
                    onGrantAdmin = onGrantAdmin,
                    onGrantMembership = onGrantMembership,
                    onBanUser = onBanUser,
                    onRevokeAffiliation = onRevokeAffiliation
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_room_config_dialog")
            ) {
                Text("Tutup")
            }
        }
    )
}

@Composable
fun RoomConfigurationContent(
    initialInfo: RoomInfo?,
    isLoading: Boolean = false,
    onSaveConfig: (
        roomName: String?,
        description: String?,
        subject: String?,
        isPersistent: Boolean,
        isPublic: Boolean,
        isMembersOnly: Boolean,
        isModerated: Boolean,
        allowSubjectChange: Boolean,
        password: String?
    ) -> Unit,
    onUpdateNickname: (String) -> Unit = {}
) {
    var roomName by remember(initialInfo) { mutableStateOf(initialInfo?.name ?: "") }
    var description by remember(initialInfo) { mutableStateOf(initialInfo?.description ?: "") }
    var subject by remember(initialInfo) { mutableStateOf(initialInfo?.subject ?: initialInfo?.description ?: "") }
    var isPersistent by remember(initialInfo) { mutableStateOf(initialInfo?.isPersistent ?: true) }
    var isPublic by remember(initialInfo) { mutableStateOf(!(initialInfo?.isPasswordProtected ?: false)) }
    var isMembersOnly by remember(initialInfo) { mutableStateOf(initialInfo?.isMembersOnly ?: false) }
    var isModerated by remember(initialInfo) { mutableStateOf(initialInfo?.isModerated ?: false) }
    var allowSubjectChange by remember(initialInfo) { mutableStateOf(true) }
    var password by remember(initialInfo) { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    // Use current nickname from some source if initialInfo doesn't have it, but we need a way to get it.
    // In MUC, the nickname is part of the occupant info. For now, let's assume we pass it or it's in initialInfo (not standard for RoomInfo though).
    // Actually, we can just let it be blank initially and user can type.
    var myNickname by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Personal Identity (Nickname)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Nickname Saya di Ruangan Ini",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = myNickname,
                        onValueChange = { myNickname = it },
                        label = { Text("Ganti Nickname") },
                        placeholder = { Text("Masukkan nickname baru...") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("room_nickname_input"),
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) }
                    )

                    Button(
                        onClick = {
                            if (myNickname.isNotBlank()) {
                                onUpdateNickname(myNickname)
                                myNickname = ""
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("Ganti")
                    }
                }
                
                Text(
                    text = "Perubahan nickname akan langsung terlihat oleh partisipan lain.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // Section 1: Room Identity & Description
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Identitas & Deskripsi Ruangan",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text("Nama Ruangan MUC") },
                    placeholder = { Text("Contoh: Komunitas InChat Indonesia") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("room_name_input"),
                    leadingIcon = { Icon(Icons.Default.Group, contentDescription = null) }
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Deskripsi / Tentang Ruangan") },
                    placeholder = { Text("Deskripsikan topik dan pedoman komunitas...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("room_desc_input"),
                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) }
                )
            }
        }

        // Section 2: Subject Management (XEP-0045)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Topic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Manajemen Subjek / Topik (XEP-0045)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subjek / Pengumuman Ruangan") },
                    placeholder = { Text("Topik obrolan aktif...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("room_subject_input"),
                    leadingIcon = { Icon(Icons.Default.Campaign, contentDescription = null) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Izinkan Partisipan Mengubah Subjek", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            text = if (allowSubjectChange) "Semua anggota dapat mengganti topik" else "Hanya moderator & admin yang dapat mengganti topik",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = allowSubjectChange,
                        onCheckedChange = { allowSubjectChange = it },
                        modifier = Modifier.testTag("switch_allow_subject_change")
                    )
                }
            }
        }

        // Section 3: Privacy, Access & Security Settings
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pengaturan Privasi & Keamanan",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                // Directory Public Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tampilkan di Direktori Publik", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            text = "Ruangan dapat dicari di Service Discovery (XEP-0030)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isPublic,
                        onCheckedChange = { isPublic = it },
                        modifier = Modifier.testTag("switch_public_room")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Members Only Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Khusus Anggota (Members-Only)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            text = "Hanya akun terdaftar di roster/whitelist yang dapat bergabung",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isMembersOnly,
                        onCheckedChange = { isMembersOnly = it },
                        modifier = Modifier.testTag("switch_members_only")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Moderated Room Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ruangan Moderasi (Moderated)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            text = "Hanya partisipan dengan hak 'voice' yang dapat mengirim pesan",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isModerated,
                        onCheckedChange = { isModerated = it },
                        modifier = Modifier.testTag("switch_moderated_room")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Persistent Room Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ruangan Persisten (Persistent)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            text = "Ruangan tetap disimpan di server meskipun seluruh partisipan keluar",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isPersistent,
                        onCheckedChange = { isPersistent = it },
                        modifier = Modifier.testTag("switch_persistent_room")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Password Protected Field
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Kata Sandi Ruangan (Opsional)") },
                    placeholder = { Text("Biarkan kosong untuk ruangan tanpa kata sandi") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("room_password_input"),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Password"
                            )
                        }
                    }
                )
            }
        }

        // Save Button Action
        Button(
            onClick = {
                onSaveConfig(
                    roomName.ifBlank { null },
                    description.ifBlank { null },
                    subject.ifBlank { null },
                    isPersistent,
                    isPublic,
                    isMembersOnly,
                    isModerated,
                    allowSubjectChange,
                    password.ifBlank { null }
                )
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("save_room_config_button")
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Menyimpan...")
            } else {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Simpan Konfigurasi Ruangan (XEP-0045)", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ModerationTabContent(
    owners: List<MucAffiliate>,
    admins: List<MucAffiliate>,
    members: List<MucAffiliate>,
    outcasts: List<MucAffiliate>,
    isLoading: Boolean = false,
    onGrantOwnership: (String) -> Unit,
    onGrantAdmin: (String) -> Unit,
    onGrantMembership: (String) -> Unit,
    onBanUser: (String, String?) -> Unit,
    onRevokeAffiliation: (String) -> Unit
) {
    var jidInput by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("Member") }
    val affiliationTypes = listOf("Owner", "Admin", "Member", "Outcast (Ban)")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Add Affiliate
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Tambah Afiliasi / Role Baru",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = jidInput,
                    onValueChange = { jidInput = it },
                    label = { Text("Real Bare JID") },
                    placeholder = { Text("username@server.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        var expanded by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(selectedType)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            affiliationTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        selectedType = type
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (jidInput.isNotBlank()) {
                                when (selectedType) {
                                    "Owner" -> onGrantOwnership(jidInput)
                                    "Admin" -> onGrantAdmin(jidInput)
                                    "Member" -> onGrantMembership(jidInput)
                                    "Outcast (Ban)" -> onBanUser(jidInput, "Moderasi via App")
                                }
                                jidInput = ""
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tambah")
                    }
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        // Section: List Affiliates
        AffiliationListSection(title = "Owners", list = owners, onRemove = onRevokeAffiliation)
        AffiliationListSection(title = "Admins", list = admins, onRemove = onRevokeAffiliation)
        AffiliationListSection(title = "Members", list = members, onRemove = onRevokeAffiliation)
        AffiliationListSection(title = "Outcasts (Banned)", list = outcasts, onRemove = onRevokeAffiliation)
        
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun AffiliationListSection(
    title: String,
    list: List<MucAffiliate>,
    onRemove: (String) -> Unit
) {
    if (list.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "$title (${list.size})",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            list.forEach { affiliate ->
                AffiliateItem(affiliate = affiliate, onRemove = { onRemove(affiliate.jid) })
            }
        }
    }
}

@Composable
fun AffiliateItem(
    affiliate: MucAffiliate,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = affiliate.jid,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                if (!affiliate.nickname.isNullOrBlank()) {
                    Text(
                        text = "Nick: ${affiliate.nickname}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Hapus",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ModerationHistoryContent(messages: List<XmppMessage>) {
    if (messages.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Belum ada riwayat moderasi",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Aksi seperti kick, ban, atau perubahan role akan muncul di sini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            messages.reversed().forEach { msg ->
                ModerationHistoryItem(message = msg)
            }
        }
    }
}

@Composable
fun ModerationHistoryItem(message: XmppMessage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Gavel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }
    }
}
