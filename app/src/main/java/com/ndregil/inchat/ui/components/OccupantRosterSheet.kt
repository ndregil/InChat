package com.ndregil.inchat.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndregil.inchat.data.model.OccupantAffiliation
import com.ndregil.inchat.data.model.OccupantRole
import com.ndregil.inchat.data.model.RoomOccupant
import com.ndregil.inchat.data.model.UserPresence
import com.ndregil.inchat.ui.theme.AwayAmber
import com.ndregil.inchat.ui.theme.DndRed
import com.ndregil.inchat.ui.theme.OfflineGray
import com.ndregil.inchat.ui.theme.OnlineGreen
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OccupantRosterSheet(
    occupants: List<RoomOccupant>,
    roomName: String,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onWhisperClick: (RoomOccupant) -> Unit,
    onMentionClick: (RoomOccupant) -> Unit,
    onOpenAdminPanel: () -> Unit,
    onQuerySoftwareVersion: ((targetJid: String, onResult: (Result<com.ndregil.inchat.data.model.ClientCaps>) -> Unit) -> Unit)? = null,
    onGrantOwnership: ((targetJid: String) -> Unit)? = null,
    onGrantAdmin: ((targetJid: String) -> Unit)? = null,
    onGrantModerator: ((targetNickname: String) -> Unit)? = null,
    onGrantMembership: ((targetJid: String) -> Unit)? = null,
    onGrantVoice: ((targetNickname: String) -> Unit)? = null,
    onRevokeVoice: ((targetNickname: String) -> Unit)? = null,
    onKickUser: ((targetNickname: String, reason: String?) -> Unit)? = null,
    onBanUser: ((targetJid: String, reason: String?) -> Unit)? = null
) {
    var selectedCapsOccupant by remember { mutableStateOf<RoomOccupant?>(null) }
    var selectedActionOccupant by remember { mutableStateOf<RoomOccupant?>(null) }

    val myOccupant = occupants.find { it.isMe }
    val isMyUserAdmin = myOccupant?.role == OccupantRole.OWNER || myOccupant?.role == OccupantRole.ADMIN || myOccupant?.affiliation == OccupantAffiliation.OWNER || myOccupant?.affiliation == OccupantAffiliation.ADMIN
    val isOwnerOrAdmin = isMyUserAdmin

    var searchQuery by remember { mutableStateOf("") }
    var selectedRoleFilter by remember { mutableStateOf<String?>(null) } // null = All, "admin" = Owner/Admin, "member" = Member/Participant, "visitor" = Visitor

    val onlineCount = occupants.count { it.presence == UserPresence.ONLINE || it.presence == UserPresence.CHAT }
    val awayCount = occupants.count { it.presence == UserPresence.AWAY || it.presence == UserPresence.XA }
    val dndCount = occupants.count { it.presence == UserPresence.DND }

    val filteredOccupants = remember(occupants, searchQuery, selectedRoleFilter) {
        occupants.filter { occ ->
            val matchesSearch = searchQuery.isBlank() ||
                occ.nickname.contains(searchQuery, ignoreCase = true) ||
                occ.statusText.contains(searchQuery, ignoreCase = true) ||
                occ.jid.contains(searchQuery, ignoreCase = true)

            val matchesRole = when (selectedRoleFilter) {
                "admin" -> occ.role == OccupantRole.OWNER || occ.role == OccupantRole.ADMIN || occ.affiliation == OccupantAffiliation.OWNER || occ.affiliation == OccupantAffiliation.ADMIN
                "member" -> occ.role == OccupantRole.PARTICIPANT || occ.role == OccupantRole.MEMBER
                "visitor" -> occ.role == OccupantRole.VISITOR
                else -> true
            }
            matchesSearch && matchesRole
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Anggota Ruangan ($roomName)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isMyUserAdmin) {
                        IconButton(onClick = { 
                            onDismiss()
                            onOpenAdminPanel()
                        }) {
                            Icon(imageVector = Icons.Default.AdminPanelSettings, contentDescription = "Admin Panel", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    
                    IconButton(onClick = onRefresh) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                    }
                }
            }

            // Presence counters summary bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PresenceCounterBadge(color = OnlineGreen, count = onlineCount, label = "Online")
                PresenceCounterBadge(color = AwayAmber, count = awayCount, label = "Away")
                PresenceCounterBadge(color = DndRed, count = dndCount, label = "DND")
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Total: ${occupants.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Search input field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari nickname atau status...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("search_occupants_input")
            )

            // Role filter chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedRoleFilter == null,
                        onClick = { selectedRoleFilter = null },
                        label = { Text("Semua (${occupants.size})") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedRoleFilter == "admin",
                        onClick = { selectedRoleFilter = "admin" },
                        label = { Text("Admin & Owner") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedRoleFilter == "member",
                        onClick = { selectedRoleFilter = "member" },
                        label = { Text("Partisipan") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedRoleFilter == "visitor",
                        onClick = { selectedRoleFilter = "visitor" },
                        label = { Text("Visitor") }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(vertical = 4.dp)
            ) {
                items(filteredOccupants, key = { it.nickname }) { occupant ->
                    OccupantItemRow(
                        occupant = occupant,
                        isOwnerOrAdmin = isOwnerOrAdmin,
                        onWhisper = { onWhisperClick(occupant) },
                        onMention = { onMentionClick(occupant) },
                        onViewCaps = { selectedCapsOccupant = occupant },
                        onOccupantClick = { selectedActionOccupant = occupant }
                    )
                }
            }
        }
    }

    selectedCapsOccupant?.let { occupant ->
        ClientCapsDialog(
            clientCaps = occupant.clientCaps,
            userName = "${occupant.nickname} (${occupant.jid.ifBlank { "MUC Occupant" }})",
            targetJid = occupant.jid,
            onQuerySoftwareVersion = onQuerySoftwareVersion,
            onDismiss = { selectedCapsOccupant = null }
        )
    }

    selectedActionOccupant?.let { occupant ->
        OccupantActionDialog(
            occupant = occupant,
            myOccupant = myOccupant,
            onDismiss = { selectedActionOccupant = null },
            onWhisper = {
                onWhisperClick(occupant)
                selectedActionOccupant = null
                onDismiss()
            },
            onMention = {
                onMentionClick(occupant)
                selectedActionOccupant = null
            },
            onGrantOwnership = { jid ->
                onGrantOwnership?.invoke(jid)
                selectedActionOccupant = null
            },
            onGrantAdmin = { jid ->
                onGrantAdmin?.invoke(jid)
                selectedActionOccupant = null
            },
            onGrantModerator = { nick ->
                onGrantModerator?.invoke(nick)
                selectedActionOccupant = null
            },
            onGrantMembership = { jid ->
                onGrantMembership?.invoke(jid)
                selectedActionOccupant = null
            },
            onGrantVoice = { nick ->
                onGrantVoice?.invoke(nick)
                selectedActionOccupant = null
            },
            onRevokeVoice = { nick ->
                onRevokeVoice?.invoke(nick)
                selectedActionOccupant = null
            },
            onKickUser = { nick, reason ->
                onKickUser?.invoke(nick, reason)
                selectedActionOccupant = null
            },
            onBanUser = { jid, reason ->
                onBanUser?.invoke(jid, reason)
                selectedActionOccupant = null
            }
        )
    }
}

@Composable
fun OccupantItemRow(
    occupant: RoomOccupant,
    isOwnerOrAdmin: Boolean = false,
    onWhisper: () -> Unit,
    onMention: () -> Unit,
    onViewCaps: () -> Unit = {},
    onOccupantClick: () -> Unit = {}
) {
    val presenceColor = when (occupant.presence) {
        UserPresence.ONLINE -> OnlineGreen
        UserPresence.CHAT -> Color(0xFF0D9488)
        UserPresence.AWAY -> AwayAmber
        UserPresence.XA -> Color(0xFFD97706)
        UserPresence.DND -> DndRed
        UserPresence.OFFLINE -> OfflineGray
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onOccupantClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag("occupant_${occupant.nickname}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar + presence status dot with colored border highlight
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    .border(2.dp, presenceColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (occupant.avatar != null) {
                    coil.compose.AsyncImage(
                        model = occupant.avatar,
                        contentDescription = "Avatar ${occupant.nickname}",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Text(
                        text = occupant.nickname.take(2).uppercase(Locale.getDefault()),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(presenceColor)
                    .border(2.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = occupant.nickname,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (occupant.isMe) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(Kamu)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Role badge
                Spacer(modifier = Modifier.width(6.dp))
                when (occupant.role) {
                    OccupantRole.OWNER -> {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFEF3C7))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(text = "Owner", fontSize = 10.sp, color = Color(0xFF92400E), fontWeight = FontWeight.Bold)
                        }
                    }
                    OccupantRole.ADMIN -> {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFE0E7FF))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(text = "Admin", fontSize = 10.sp, color = Color(0xFF3730A3), fontWeight = FontWeight.Bold)
                        }
                    }
                    else -> {}
                }
            }

            // Real JID display
            if (isOwnerOrAdmin && occupant.jid.isNotBlank()) {
                Text(
                    text = occupant.jid,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Client Caps Badge (Clickable to view XEP-0115 info)
                ClientCapsBadge(
                    clientCaps = occupant.clientCaps,
                    onClick = onViewCaps
                )

                Text(
                    text = occupant.statusText.ifBlank {
                        when (occupant.presence) {
                            UserPresence.ONLINE -> "Online"
                            UserPresence.CHAT -> "Chat"
                            UserPresence.AWAY -> "Away"
                            UserPresence.XA -> "Extended Away"
                            UserPresence.DND -> "DND"
                            UserPresence.OFFLINE -> "Offline"
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (!occupant.isMe) {
            // Mention button
            IconButton(
                onClick = onMention,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AlternateEmail,
                    contentDescription = "Mention @${occupant.nickname}",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Whisper private chat button
            IconButton(
                onClick = onWhisper,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Bisikan ke ${occupant.nickname}",
                    tint = Color(0xFFC084FC),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        IconButton(
            onClick = onOccupantClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Opsi Moderasi",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OccupantActionDialog(
    occupant: RoomOccupant,
    myOccupant: RoomOccupant?,
    onDismiss: () -> Unit,
    onWhisper: () -> Unit,
    onMention: () -> Unit,
    onGrantOwnership: (String) -> Unit,
    onGrantAdmin: (String) -> Unit,
    onGrantModerator: (String) -> Unit,
    onGrantMembership: (String) -> Unit,
    onGrantVoice: (String) -> Unit,
    onRevokeVoice: (String) -> Unit,
    onKickUser: (String, String?) -> Unit,
    onBanUser: (String, String?) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var showKickReasonDialog by remember { mutableStateOf(false) }
    var showBanReasonDialog by remember { mutableStateOf(false) }
    var reasonInput by remember { mutableStateOf("") }

    val canModerate = myOccupant?.role == OccupantRole.OWNER ||
            myOccupant?.role == OccupantRole.ADMIN ||
            myOccupant?.affiliation == OccupantAffiliation.OWNER ||
            myOccupant?.affiliation == OccupantAffiliation.ADMIN

    if (showKickReasonDialog) {
        AlertDialog(
            onDismissRequest = { showKickReasonDialog = false },
            title = { Text("Kick ${occupant.nickname}") },
            text = {
                OutlinedTextField(
                    value = reasonInput,
                    onValueChange = { reasonInput = it },
                    label = { Text("Alasan Kick (Opsional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onKickUser(occupant.nickname, reasonInput.ifBlank { "Di-kick oleh moderator" })
                        showKickReasonDialog = false
                    }
                ) {
                    Text("Kick")
                }
            },
            dismissButton = {
                TextButton(onClick = { showKickReasonDialog = false }) { Text("Batal") }
            }
        )
    }

    if (showBanReasonDialog) {
        AlertDialog(
            onDismissRequest = { showBanReasonDialog = false },
            title = { Text("Ban (Outcast) ${occupant.nickname}") },
            text = {
                OutlinedTextField(
                    value = reasonInput,
                    onValueChange = { reasonInput = it },
                    label = { Text("Alasan Ban (Opsional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onBanUser(occupant.jid.ifBlank { occupant.nickname }, reasonInput.ifBlank { "Di-ban oleh moderator" })
                        showBanReasonDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Ban User")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBanReasonDialog = false }) { Text("Batal") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = occupant.nickname.take(2).uppercase(Locale.getDefault()),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(occupant.nickname, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Role: ${occupant.role} | Afiliasi: ${occupant.affiliation}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (canModerate) {
                    // Real JID section
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Real JID", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = occupant.jid.ifBlank { "Anonim (Resource JID)" },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (occupant.jid.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(occupant.jid))
                                        Toast.makeText(context, "Real JID disalin!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Salin JID", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
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

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Interactivity
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!occupant.isMe) {
                        OutlinedButton(
                            onClick = onWhisper,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFC084FC))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Bisikan", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onMention,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AlternateEmail, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Mention", fontSize = 12.sp)
                        }
                    }
                }

                // Moderation Section for Owners / Admins / Moderators
                if (canModerate && !occupant.isMe) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Aksi Moderasi (Owner/Admin)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Grant Roles / Affiliations
                        AssistChip(
                            onClick = { onGrantOwnership(occupant.jid.ifBlank { occupant.nickname }) },
                            label = { Text("Owner") },
                            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(14.dp)) }
                        )
                        AssistChip(
                            onClick = { onGrantAdmin(occupant.jid.ifBlank { occupant.nickname }) },
                            label = { Text("Admin") },
                            leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(14.dp)) }
                        )
                        AssistChip(
                            onClick = { onGrantModerator(occupant.nickname) },
                            label = { Text("Moderator") },
                            leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                        AssistChip(
                            onClick = { onGrantMembership(occupant.jid.ifBlank { occupant.nickname }) },
                            label = { Text("Member") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                        if (occupant.role == OccupantRole.VISITOR) {
                            AssistChip(
                                onClick = { onGrantVoice(occupant.nickname) },
                                label = { Text("Participant (+Voice)") },
                                leadingIcon = { Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                        } else {
                            AssistChip(
                                onClick = { onRevokeVoice(occupant.nickname) },
                                label = { Text("Visitor (-Voice)") },
                                leadingIcon = { Icon(Icons.Default.MicOff, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                        }

                        // Kick / Ban
                        AssistChip(
                            onClick = { showKickReasonDialog = true },
                            label = { Text("Kick") },
                            leadingIcon = { Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                        AssistChip(
                            onClick = { showBanReasonDialog = true },
                            label = { Text("Ban (Outcast)") },
                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

@Composable
private fun PresenceCounterBadge(color: Color, count: Int, label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(0.6.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = "$count $label",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

