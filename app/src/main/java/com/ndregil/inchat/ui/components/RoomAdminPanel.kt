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
        password: String?
    ) -> Unit,
    onGrantModerator: (String) -> Unit,
    onRevokeModerator: (String) -> Unit,
    onGrantMembership: (String) -> Unit,
    onRevokeMembership: (String) -> Unit,
    onGrantAdmin: (String) -> Unit,
    onRevokeAdmin: (String) -> Unit,
    onGrantOwnership: (String) -> Unit = {},
    onRevokeOwnership: (String) -> Unit = {},
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
                        onSaveConfig = onSaveConfig
                    )
                    1 -> ParticipantsTab(
                        occupants = occupants,
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
                        ownersList = ownersList,
                        adminsList = adminsList,
                        membersList = membersList,
                        outcastsList = outcastsList,
                        isLoading = isLoadingAffiliations,
                        onRefresh = onLoadAffiliations,
                        onGrantOwnership = onGrantOwnership,
                        onRevokeOwnership = onRevokeOwnership,
                        onGrantAdmin = onGrantAdmin,
                        onRevokeAdmin = onRevokeAdmin,
                        onGrantMembership = onGrantMembership,
                        onRevokeMembership = onRevokeMembership,
                        onBanUser = { jid -> onBanUser(jid, "Ditambahkan ke blacklist (outcast)") },
                        onUnbanUser = onUnbanUser
                    )
                    3 -> ModerationHistoryContent(messages = moderationMessages)
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
                    Text("Role: ${occupant.role} | Affiliation: ${occupant.affiliation}", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
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
            }
        }
    }
}

@Composable
fun AffiliationsManagementTab(
    ownersList: List<MucAffiliate>,
    adminsList: List<MucAffiliate>,
    membersList: List<MucAffiliate>,
    outcastsList: List<MucAffiliate>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onGrantOwnership: (String) -> Unit,
    onRevokeOwnership: (String) -> Unit,
    onGrantAdmin: (String) -> Unit,
    onRevokeAdmin: (String) -> Unit,
    onGrantMembership: (String) -> Unit,
    onRevokeMembership: (String) -> Unit,
    onBanUser: (String) -> Unit,
    onUnbanUser: (String) -> Unit
) {
    var affiliationSubTab by remember { mutableIntStateOf(0) } // 0 = Owner, 1 = Admin, 2 = Member, 3 = Outcast
    var showAddDialog by remember { mutableStateOf(false) }
    var inputJid by remember { mutableStateOf("") }

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
            title = { Text("Tambah ke $tabTitle") },
            text = {
                Column {
                    Text("Masukkan JID user (contoh: user@jabber.org):", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputJid,
                        onValueChange = { inputJid = it },
                        label = { Text("User Bare JID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputJid.isNotBlank()) {
                            when (affiliationSubTab) {
                                0 -> onGrantOwnership(inputJid.trim())
                                1 -> onGrantAdmin(inputJid.trim())
                                2 -> onGrantMembership(inputJid.trim())
                                3 -> onBanUser(inputJid.trim())
                            }
                            showAddDialog = false
                            inputJid = ""
                        }
                    }
                ) {
                    Text("Tambah")
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
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = { showAddDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tambah", fontSize = 12.sp)
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
                Text("Belum ada daftar $tabTitle", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
fun AffiliateListItem(
    item: MucAffiliate,
    affiliationType: Int, // 0 = Owner, 1 = Admin, 2 = Member, 3 = Outcast
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
                    Text("Nick: ${item.nickname}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(
                onClick = onRemove,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(if (affiliationType == 3) "Unban" else "Hapus", fontSize = 12.sp)
            }
        }
    }
}
