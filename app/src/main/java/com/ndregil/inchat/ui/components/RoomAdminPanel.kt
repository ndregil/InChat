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
    onDismiss: () -> Unit,
    onSaveConfig: (name: String?, topic: String?, persistent: Boolean, public: Boolean, password: String?) -> Unit,
    onGrantModerator: (String) -> Unit,
    onRevokeModerator: (String) -> Unit,
    onGrantMembership: (String) -> Unit,
    onRevokeMembership: (String) -> Unit,
    onGrantAdmin: (String) -> Unit,
    onRevokeAdmin: (String) -> Unit,
    onBanUser: (String, String?) -> Unit,
    onKickUser: (String, String?) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Config, 1 = Participants

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Admin Panel: $roomName")
                }
                TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                        Text("Konfigurasi", modifier = Modifier.padding(12.dp))
                    }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                        Text("Partisipan", modifier = Modifier.padding(12.dp))
                    }
                }
            }
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (selectedTab == 0) {
                    RoomConfigTab(
                        initialInfo = initialInfo,
                        onSave = onSaveConfig
                    )
                } else {
                    ParticipantsTab(
                        occupants = occupants,
                        onGrantModerator = onGrantModerator,
                        onRevokeModerator = onRevokeModerator,
                        onGrantMembership = onGrantMembership,
                        onRevokeMembership = onRevokeMembership,
                        onGrantAdmin = onGrantAdmin,
                        onRevokeAdmin = onRevokeAdmin,
                        onBanUser = onBanUser,
                        onKickUser = onKickUser
                    )
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
fun RoomConfigTab(
    initialInfo: RoomInfo?,
    onSave: (name: String?, topic: String?, persistent: Boolean, public: Boolean, password: String?) -> Unit
) {
    var roomName by remember { mutableStateOf(initialInfo?.name ?: "") }
    var topic by remember { mutableStateOf(initialInfo?.description ?: "") }
    var isPersistent by remember { mutableStateOf(initialInfo?.isPersistent ?: true) }
    var isPublic by remember { mutableStateOf(!(initialInfo?.isPasswordProtected ?: false)) }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = roomName,
            onValueChange = { roomName = it },
            label = { Text("Nama Ruangan") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = topic,
            onValueChange = { topic = it },
            label = { Text("Topik / Deskripsi") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Persistent", fontWeight = FontWeight.Bold)
                        Text("Ruangan tetap ada meskipun kosong", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = isPersistent, onCheckedChange = { isPersistent = it })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Public / Terbuka", fontWeight = FontWeight.Bold)
                        Text("Tanpa kata sandi", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                }
            }
        }

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Kata Sandi Ruangan") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Kosongkan jika tidak ingin dikunci") }
        )

        Button(
            onClick = {
                onSave(
                    roomName.ifBlank { null },
                    topic.ifBlank { null },
                    isPersistent,
                    isPublic,
                    password.ifBlank { null }
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Simpan Perubahan")
        }
    }
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
    onBanUser: (String, String?) -> Unit,
    onKickUser: (String, String?) -> Unit
) {
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
                onBanUser = { onBanUser(occupant.jid, "Pelanggaran aturan") },
                onKickUser = { onKickUser(occupant.nickname, "Diminta admin") }
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
                    Text("${occupant.role} / ${occupant.affiliation}", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (occupant.role != OccupantRole.OWNER && !occupant.isMe) {
                        AssistChip(
                            onClick = onKickUser,
                            label = { Text("Kick") },
                            leadingIcon = { Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        AssistChip(
                            onClick = onBanUser,
                            label = { Text("Ban") },
                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error)
                        )
                        
                        if (occupant.role == OccupantRole.ADMIN || occupant.role == OccupantRole.PARTICIPANT || occupant.role == OccupantRole.MEMBER) {
                            if (occupant.role != OccupantRole.ADMIN) {
                                AssistChip(onClick = onGrantModerator, label = { Text("+Moderator") })
                            } else {
                                AssistChip(onClick = onRevokeModerator, label = { Text("-Moderator") })
                            }
                        }

                        AssistChip(onClick = onGrantMembership, label = { Text("+Member") })
                        AssistChip(onClick = onGrantAdmin, label = { Text("+Admin") })
                    }
                }
            }
        }
    }
}
