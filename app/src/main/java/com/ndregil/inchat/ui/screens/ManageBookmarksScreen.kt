package com.ndregil.inchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndregil.inchat.data.model.XmppRoom
import com.ndregil.inchat.ui.components.RoomAvatarBadge

val ROOM_PRESET_COLORS = listOf(
    "#6366F1" to "Violet",
    "#9333EA" to "Purple",
    "#10B981" to "Emerald",
    "#F97316" to "Orange",
    "#EC4899" to "Pink",
    "#0284C7" to "Ocean Blue",
    "#EF4444" to "Red",
    "#EAB308" to "Yellow"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageBookmarksScreen(
    savedRooms: List<XmppRoom>,
    onToggleAutoJoin: (String, Boolean) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onUpdateRoomColor: (String, String) -> Unit,
    onBack: () -> Unit,
    avatarRevision: Int = 0,
    getRoomAvatar: ((String) -> ByteArray?)? = null,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Kelola Favorit & Warna Room",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { paddingValues ->
        if (savedRooms.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Tidak ada ruangan favorit tersimpan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp)
            ) {
                items(savedRooms, key = { it.jid }) { room ->
                    val roomColor = try {
                        Color(android.graphics.Color.parseColor(room.colorHex))
                    } catch (e: Exception) {
                        MaterialTheme.colorScheme.primary
                    }

                    val avatarBytes = remember(room.jid, avatarRevision) {
                        getRoomAvatar?.invoke(room.jid)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bookmark_card_${room.jid}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, roomColor.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RoomAvatarBadge(
                                    roomJid = room.jid,
                                    roomName = room.name,
                                    avatarBytes = avatarBytes,
                                    size = 42.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(room.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(room.jid, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(
                                    onClick = { onRemoveBookmark(room.jid) },
                                    modifier = Modifier.testTag("remove_bookmark_${room.jid}")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            Spacer(Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Auto-Join Saat Koneksi", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Switch(
                                    checked = room.isAutoJoin,
                                    onCheckedChange = { onToggleAutoJoin(room.jid, it) }
                                )
                            }

                            Spacer(Modifier.height(12.dp))
                            Text("Warna Tema Kustom Room", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(ROOM_PRESET_COLORS) { (hex, name) ->
                                    val isSelected = room.colorHex.equals(hex, ignoreCase = true)
                                    val colorObj = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Gray }

                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(colorObj)
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable { onUpdateRoomColor(room.jid, hex) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = name,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
