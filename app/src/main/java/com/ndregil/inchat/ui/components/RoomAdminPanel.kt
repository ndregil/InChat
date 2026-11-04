package com.ndregil.inchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndregil.inchat.data.model.MucAffiliate
import com.ndregil.inchat.data.model.OccupantRole
import com.ndregil.inchat.data.model.RoomOccupant
import com.ndregil.inchat.data.model.OccupantAffiliation
import org.jivesoftware.smackx.muc.RoomInfo
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomAdminPanel(
    roomJid: String,
    roomName: String,
    occupants: List<RoomOccupant>,
    initialInfo: RoomInfo?,
    isLoadingConfig: Boolean = false,
    ownersList: List<MucAffiliate> = emptyList(),
    adminsList: List<MucAffiliate> = emptyList(),
    membersList: List<MucAffiliate> = emptyList(),
    outcastsList: List<MucAffiliate> = emptyList(),
    isLoadingAffiliations: Boolean = false,
    onDismiss: () -> Unit,
    onLoadAffiliations: () -> Unit = {},
    onSaveConfig: (
        roomName: String?,
        description: String?,
        subject: String?,
        isPersistent: Boolean,
        isPublic: Boolean,
        isMembersOnly: Boolean,
        isModerated: Boolean,
        allowSubjectChange: Boolean,
        password: String?,
        maxUsers: Int?,
        enableLogging: Boolean,
        allowInvites: Boolean,
        whois: String?
    ) -> Unit,
    onUpdateTopic: (String) -> Unit = {},
    onGrantModerator: (String) -> Unit,
    onRevokeModerator: (String) -> Unit,
    onGrantMembership: (String) -> Unit,
    onRevokeMembership: (String) -> Unit,
    onGrantAdmin: (String) -> Unit,
    onRevokeAdmin: (String) -> Unit,
    onGrantOwnership: (String) -> Unit = {},
    onRevokeOwnership: (String) -> Unit = {},
    onGrantNone: (String) -> Unit = {},
    onGrantVoice: (String) -> Unit = {},
    onRevokeVoice: (String) -> Unit = {},
    onBanUser: (String, String?) -> Unit,
    onUnbanUser: (String) -> Unit = {},
    onKickUser: (String, String?) -> Unit,
    moderationMessages: List<com.ndregil.inchat.data.model.XmppMessage> = emptyList()
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Config, 1 = Partisipan, 2 = Daftar Afiliasi, 3 = Moderasi

    LaunchedEffect(Unit) {
        onLoadAffiliations()
    }

    val myOccupant = occupants.find { it.isMe }
    val isOwnerOrAdmin = myOccupant?.role == OccupantRole.OWNER ||
            myOccupant?.role == OccupantRole.ADMIN ||
            myOccupant?.affiliation == OccupantAffiliation.OWNER ||
            myOccupant?.affiliation == OccupantAffiliation.ADMIN

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 680.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Admin Panel: $roomName", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    edgePadding = 0.dp
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                        Text("Config", modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp), fontSize = 13.sp)
                    }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                        Text("Partisipan", modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp), fontSize = 13.sp)
                    }
                    Tab(selected = selectedTab == 2, onClick = { 
                        selectedTab = 2
                        onLoadAffiliations()
                    }) {
                        Text("Daftar Afiliasi", modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp), fontSize = 13.sp)
                    }
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }) {
                        Text("Moderasi", modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp), fontSize = 13.sp)
                    }
                }
            }
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth()) {
                when (selectedTab) {
                    0 -> RoomConfigurationContent(
                        initialInfo = initialInfo,
                        isLoading = isLoadingConfig,
                        myRole = myOccupant?.role ?: OccupantRole.NONE,
                        myAffiliation = myOccupant?.affiliation ?: OccupantAffiliation.NONE,
                        onUpdateTopic = onUpdateTopic,
                        onSaveConfig = { name, desc, subj, persistent, public, membersOnly, moderated, allowSubjChange, pwd, notifMode, maxUsers, enableLogging, allowInvites, whois ->
                            onSaveConfig(name, desc, subj, persistent, public, membersOnly, moderated, allowSubjChange, pwd, maxUsers, enableLogging, allowInvites, whois)
                        }
                    )
                    1 -> ParticipantsTab(
                        occupants = occupants,
                        isOwnerOrAdmin = isOwnerOrAdmin,
                        onGrantModerator = onGrantModerator,
                        onRevokeModerator = onRevokeModerator,
                        onGrantMembership = onGrantMembership,
                        onRevokeMembership = onRevokeMembership,
                        onGrantAdmin = onGrantAdmin,
                        onRevokeAdmin = onRevokeAdmin,
                        onGrantOwnership = onGrantOwnership,
                        onRevokeOwnership = onRevokeOwnership,
                        onGrantVoice = onGrantVoice,
                        onRevokeVoice = onRevokeVoice,
                        onBanUser = onBanUser,
                        onKickUser = onKickUser
                    )
                    2 -> AffiliationsManagementTab(
                        roomJid = roomJid,
                        ownersList = ownersList,
                        adminsList = adminsList,
                        membersList = membersList,
                        outcastsList = outcastsList,
                        isLoading = isLoadingAffiliations,
                        isOwnerOrAdmin = isOwnerOrAdmin,
                        onRefresh = onLoadAffiliations,
                        onGrantOwnership = onGrantOwnership,
                        onRevokeOwnership = onRevokeOwnership,
                        onGrantAdmin = onGrantAdmin,
                        onRevokeAdmin = onRevokeAdmin,
                        onGrantMembership = onGrantMembership,
                        onRevokeMembership = onRevokeMembership,
                        onGrantNone = onGrantNone,
                        onBanUser = onBanUser,
                        onUnbanUser = onUnbanUser
                    )
                    3 -> if (isOwnerOrAdmin) {
                        ModerationHistoryContent(messages = moderationMessages)
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("fitur ini hanya untuk Owner room.", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

@Composable
fun ParticipantsTab(
    occupants: List<RoomOccupant>,
    isOwnerOrAdmin: Boolean = true,
    onGrantModerator: (String) -> Unit,
    onRevokeModerator: (String) -> Unit,
    onGrantMembership: (String) -> Unit,
    onRevokeMembership: (String) -> Unit,
    onGrantAdmin: (String) -> Unit,
    onRevokeAdmin: (String) -> Unit,
    onGrantOwnership: (String) -> Unit,
    onRevokeOwnership: (String) -> Unit,
    onGrantVoice: (String) -> Unit,
    onRevokeVoice: (String) -> Unit,
    onBanUser: (String, String?) -> Unit,
    onKickUser: (String, String?) -> Unit
) {
    var showReasonDialog by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // targetNickname/jid to isBan
    var reasonInput by remember { mutableStateOf("") }

    if (showReasonDialog != null) {
        val (target, isBan) = showReasonDialog!!
        AlertDialog(
            onDismissRequest = { showReasonDialog = null },
            title = { Text(if (isBan) "Ban $target" else "Kick $target") },
            text = {
                OutlinedTextField(
                    value = reasonInput,
                    onValueChange = { reasonInput = it },
                    label = { Text("Alasan (Opsional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val reason = reasonInput.ifBlank { "Tindakan admin" }
                        if (isBan) {
                            onBanUser(target, reason)
                        } else {
                            onKickUser(target, reason)
                        }
                        showReasonDialog = null
                        reasonInput = ""
                    },
                    colors = if (isBan) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                ) {
                    Text(if (isBan) "Ban" else "Kick")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReasonDialog = null }) {
                    Text("Batal")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(occupants) { occupant ->
            AdminOccupantItem(
                occupant = occupant,
                isOwnerOrAdmin = isOwnerOrAdmin,
                onGrantModerator = { onGrantModerator(occupant.nickname) },
                onRevokeModerator = { onRevokeModerator(occupant.nickname) },
                onGrantMembership = { onGrantMembership(occupant.jid) },
                onRevokeMembership = { onRevokeMembership(occupant.jid) },
                onGrantAdmin = { onGrantAdmin(occupant.jid) },
                onRevokeAdmin = { onRevokeAdmin(occupant.jid) },
                onGrantOwnership = { onGrantOwnership(occupant.jid) },
                onRevokeOwnership = { onRevokeOwnership(occupant.jid) },
                onGrantVoice = { onGrantVoice(occupant.nickname) },
                onRevokeVoice = { onRevokeVoice(occupant.nickname) },
                onBanUser = { showReasonDialog = Pair(occupant.jid.ifBlank { occupant.nickname }, true) },
                onKickUser = { showReasonDialog = Pair(occupant.nickname, false) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminOccupantItem(
    occupant: RoomOccupant,
    isOwnerOrAdmin: Boolean = true,
    onGrantModerator: () -> Unit,
    onRevokeModerator: () -> Unit,
    onGrantMembership: () -> Unit,
    onRevokeMembership: () -> Unit,
    onGrantAdmin: () -> Unit,
    onRevokeAdmin: () -> Unit,
    onGrantOwnership: () -> Unit,
    onRevokeOwnership: () -> Unit,
    onGrantVoice: () -> Unit,
    onRevokeVoice: () -> Unit,
    onBanUser: () -> Unit,
    onKickUser: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(occupant.nickname.take(1).uppercase(), fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(occupant.nickname, fontWeight = FontWeight.Bold)
                    if (isOwnerOrAdmin && occupant.jid.isNotBlank()) {
                        Text("Real JID: ${occupant.jid}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                    }
                    Text("Role: ${occupant.role} | Affiliation: ${occupant.affiliation}", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                if (isOwnerOrAdmin) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (occupant.role != OccupantRole.OWNER && !occupant.isMe) {
                            // Kick / Ban
                            AssistChip(
                                onClick = onKickUser,
                                label = { Text("Kick") },
                                leadingIcon = { Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                            AssistChip(
                                onClick = onBanUser,
                                label = { Text("Ban") },
                                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error)
                            )

                            // Voice control for Visitor / Participant
                            if (occupant.role == OccupantRole.VISITOR) {
                                AssistChip(onClick = onGrantVoice, label = { Text("+Voice") })
                            } else if (occupant.role == OccupantRole.PARTICIPANT) {
                                AssistChip(onClick = onRevokeVoice, label = { Text("-Voice (Visitor)") })
                            }

                            // Moderator control
                            if (occupant.role != OccupantRole.ADMIN && occupant.role != OccupantRole.OWNER) {
                                AssistChip(
                                    onClick = onGrantModerator,
                                    label = { Text("+Moderator") }
                                )
                            } else {
                                AssistChip(
                                    onClick = onRevokeModerator,
                                    label = { Text("-Moderator") }
                                )
                            }

                            // Affiliations (Member, Admin, Owner)
                            AssistChip(onClick = onGrantMembership, label = { Text("+Member") })
                            AssistChip(onClick = onGrantAdmin, label = { Text("+Admin") })
                            AssistChip(onClick = onGrantOwnership, label = { Text("+Owner") })
                        }
                    }
                } else {
                    Text("fitur ini hanya untuk Owner room.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AffiliationsManagementTab(
    roomJid: String,
    ownersList: List<MucAffiliate>,
    adminsList: List<MucAffiliate>,
    membersList: List<MucAffiliate>,
    outcastsList: List<MucAffiliate>,
    isLoading: Boolean,
    isOwnerOrAdmin: Boolean = true,
    onRefresh: () -> Unit,
    onGrantOwnership: (String) -> Unit,
    onRevokeOwnership: (String) -> Unit,
    onGrantAdmin: (String) -> Unit,
    onRevokeAdmin: (String) -> Unit,
    onGrantMembership: (String) -> Unit,
    onRevokeMembership: (String) -> Unit,
    onGrantNone: (String) -> Unit,
    onBanUser: (String, String?) -> Unit,
    onUnbanUser: (String) -> Unit
) {
    var affiliationSubTab by remember { mutableIntStateOf(0) } // 0 = Owner, 1 = Admin, 2 = Member, 3 = Outcast
    var showAddDialog by remember { mutableStateOf(false) }
    var inputJid by remember { mutableStateOf("") }
    var selectedAffiliation by remember { mutableStateOf("member") }

    val currentList = when (affiliationSubTab) {
        0 -> ownersList
        1 -> adminsList
        2 -> membersList
        3 -> outcastsList
        else -> emptyList()
    }

    val tabTitle = when (affiliationSubTab) {
        0 -> "Owner List"
        1 -> "Admin List"
        2 -> "Member List"
        3 -> "Outcast (Banned) List"
        else -> ""
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Tambah Real JID ke Server Manual") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Masukkan Bare JID user (contoh: user@jabber.org) untuk didaftarkan ke server MUC:", fontSize = 13.sp)
                    OutlinedTextField(
                        value = inputJid,
                        onValueChange = { inputJid = it },
                        label = { Text("User Bare JID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text("Pilih Afiliasi Server:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = selectedAffiliation == "owner",
                            onClick = { selectedAffiliation = "owner" },
                            label = { Text("Owner", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedAffiliation == "admin",
                            onClick = { selectedAffiliation = "admin" },
                            label = { Text("Admin", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedAffiliation == "member",
                            onClick = { selectedAffiliation = "member" },
                            label = { Text("Member", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedAffiliation == "outcast",
                            onClick = { selectedAffiliation = "outcast" },
                            label = { Text("Ban", fontSize = 11.sp) }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val jidTrim = inputJid.trim()
                        if (jidTrim.isNotBlank()) {
                            when (selectedAffiliation) {
                                "owner" -> onGrantOwnership(jidTrim)
                                "admin" -> onGrantAdmin(jidTrim)
                                "member" -> onGrantMembership(jidTrim)
                                "outcast" -> onBanUser(jidTrim, "Ditambahkan manual oleh admin")
                            }
                            showAddDialog = false
                            inputJid = ""
                        }
                    }
                ) {
                    Text("Kirim ke Server")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (!isOwnerOrAdmin) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("fitur ini hanya untuk Owner room.", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        TabRow(selectedTabIndex = affiliationSubTab, containerColor = Color.Transparent) {
            Tab(selected = affiliationSubTab == 0, onClick = { affiliationSubTab = 0 }) {
                Text("Owner (${ownersList.size})", modifier = Modifier.padding(vertical = 8.dp), fontSize = 12.sp)
            }
            Tab(selected = affiliationSubTab == 1, onClick = { affiliationSubTab = 1 }) {
                Text("Admin (${adminsList.size})", modifier = Modifier.padding(vertical = 8.dp), fontSize = 12.sp)
            }
            Tab(selected = affiliationSubTab == 2, onClick = { affiliationSubTab = 2 }) {
                Text("Member (${membersList.size})", modifier = Modifier.padding(vertical = 8.dp), fontSize = 12.sp)
            }
            Tab(selected = affiliationSubTab == 3, onClick = { affiliationSubTab = 3 }) {
                Text("Outcast (${outcastsList.size})", modifier = Modifier.padding(vertical = 8.dp), fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$tabTitle (${currentList.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Row {
                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Afiliasi", modifier = Modifier.size(18.dp))
                }
                if (isOwnerOrAdmin) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = {
                            selectedAffiliation = when (affiliationSubTab) {
                                0 -> "owner"
                                1 -> "admin"
                                2 -> "member"
                                3 -> "outcast"
                                else -> "member"
                            }
                            showAddDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tambah Manual", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        } else if (currentList.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Belum ada daftar $tabTitle di server", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(currentList) { item ->
                    AffiliateListItem(
                        item = item,
                        affiliationType = affiliationSubTab,
                        onGrantOwner = { onGrantOwnership(item.jid) },
                        onGrantAdmin = { onGrantAdmin(item.jid) },
                        onGrantMember = { onGrantMembership(item.jid) },
                        onSetNone = { onGrantNone(item.jid) },
                        onBan = { onBanUser(item.jid, "Banned by admin") },
                        onRemove = {
                            when (affiliationSubTab) {
                                0 -> onRevokeOwnership(item.jid)
                                1 -> onRevokeAdmin(item.jid)
                                2 -> onRevokeMembership(item.jid)
                                3 -> onUnbanUser(item.jid)
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AffiliateListItem(
    item: MucAffiliate,
    affiliationType: Int, // 0 = Owner, 1 = Admin, 2 = Member, 3 = Outcast
    onGrantOwner: () -> Unit,
    onGrantAdmin: () -> Unit,
    onGrantMember: () -> Unit,
    onSetNone: () -> Unit,
    onBan: () -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.jid.ifBlank { item.nickname ?: "Unknown" },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    if (!item.nickname.isNullOrBlank() && item.jid.isNotBlank()) {
                        Text("Nick: ${item.nickname} | Role: ${item.role}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Affiliation: ${item.affiliation}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (affiliationType != 0) {
                        AssistChip(onClick = onGrantOwner, label = { Text("Jadikan Owner", fontSize = 11.sp) })
                    }
                    if (affiliationType != 1) {
                        AssistChip(onClick = onGrantAdmin, label = { Text("Jadikan Admin", fontSize = 11.sp) })
                    }
                    if (affiliationType != 2) {
                        AssistChip(onClick = onGrantMember, label = { Text("Jadikan Member", fontSize = 11.sp) })
                    }
                    if (affiliationType != 3) {
                        AssistChip(
                            onClick = onBan,
                            label = { Text("Ban", fontSize = 11.sp) },
                            colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error)
                        )
                    }
                    AssistChip(onClick = onSetNone, label = { Text("Reset (None)", fontSize = 11.sp) })
                    AssistChip(
                        onClick = onRemove,
                        label = { Text(if (affiliationType == 3) "Unban" else "Hapus", fontSize = 11.sp) },
                        colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error)
                    )
                }
            }
        }
    }
}
