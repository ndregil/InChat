package com.ndregil.inchat.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndregil.inchat.data.model.XmppRoom
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedRoomsScreen(
    savedRooms: List<XmppRoom>,
    onSelectRoom: (String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onAddRoomClick: () -> Unit,
    onOpenManageBookmarks: (() -> Unit)? = null,
    onSyncBookmarks: (() -> Unit)? = null,
    isSyncing: Boolean = false,
    getRoomAvatar: ((String) -> ByteArray?)? = null,
    onUpdateRoomColor: ((String, String) -> Unit)? = null,
    onToggleAutoJoin: ((String, Boolean) -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var roomToColorPick by remember { mutableStateOf<XmppRoom?>(null) }
    var roomToDelete by remember { mutableStateOf<XmppRoom?>(null) }

    val filteredRooms = remember(savedRooms, searchQuery) {
        if (searchQuery.isBlank()) savedRooms
        else savedRooms.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.topic.contains(searchQuery, ignoreCase = true) ||
            it.jid.contains(searchQuery, ignoreCase = true)
        }
    }

    // Color picker dialog for rapid color customization
    if (roomToColorPick != null && onUpdateRoomColor != null) {
        val targetRoom = roomToColorPick!!
        AlertDialog(
            onDismissRequest = { roomToColorPick = null },
            icon = {
                Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            title = {
                Text(
                    text = "Pilih Warna Tema Room",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Atur warna aksen untuk ruangan \"${targetRoom.name.substringBefore("@")}\" agar mudah dikenali di daftar chat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        items(ROOM_PRESET_COLORS) { (hex, name) ->
                            val isSelected = targetRoom.colorHex.equals(hex, ignoreCase = true)
                            val colorObj = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Gray }
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(colorObj)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        onUpdateRoomColor(targetRoom.jid, hex)
                                        roomToColorPick = null
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = name,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { roomToColorPick = null }) {
                    Text("Tutup")
                }
            }
        )
    }

    // Delete bookmark confirmation dialog
    if (roomToDelete != null) {
        val delRoom = roomToDelete!!
        AlertDialog(
            onDismissRequest = { roomToDelete = null },
            icon = {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = { Text("Hapus dari Favorit?") },
            text = {
                Text("Ruangan \"${delRoom.name}\" (${delRoom.jid}) akan dihapus dari daftar favorit.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveBookmark(delRoom.jid)
                        roomToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { roomToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Ruangan Favorit", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(
                                "${savedRooms.size} bookmark tersimpan",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Sync bookmarks from server (pointed to by top-right red arrow)
                    if (onSyncBookmarks != null) {
                        IconButton(
                            onClick = onSyncBookmarks,
                            enabled = !isSyncing,
                            modifier = Modifier.testTag("btn_sync_bookmarks")
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sinkronkan dari Server (XEP-0048)",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Open full Manage Bookmarks & Theme Colors screen
                    if (onOpenManageBookmarks != null) {
                        IconButton(
                            onClick = onOpenManageBookmarks,
                            modifier = Modifier.testTag("btn_manage_bookmarks")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Kelola Favorit & Warna",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(
                        onClick = onAddRoomClick,
                        modifier = Modifier.testTag("btn_add_bookmark_top")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Tambah Ruangan",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddRoomClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(bottom = 72.dp)
                    .testTag("fab_add_bookmark")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Bookmark")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("search_saved_rooms"),
                placeholder = { Text("Cari favorit...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Bersihkan")
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            if (filteredRooms.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (searchQuery.isEmpty()) Icons.Default.BookmarkBorder else Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "Belum ada bookmark" else "Tidak ditemukan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (searchQuery.isEmpty())
                                "Simpan ruangan dari Discovery atau Multi-Room untuk akses cepat di sini."
                            else "Tidak ada ruangan yang cocok dengan kata kunci '$searchQuery'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (searchQuery.isEmpty() && onSyncBookmarks != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            FilledTonalButton(onClick = onSyncBookmarks) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tarik Favorit dari Server")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    // Generous bottom padding to ensure the last room card is never cut off
                    contentPadding = PaddingValues(bottom = 140.dp, top = 6.dp)
                ) {
                    items(filteredRooms, key = { it.jid }) { room ->
                        val avatarBytes = getRoomAvatar?.invoke(room.jid)
                        SavedRoomItem(
                            room = room,
                            roomAvatar = avatarBytes,
                            onClick = { onSelectRoom(room.jid) },
                            onRemove = { roomToDelete = room },
                            onChangeColorClick = { roomToColorPick = room },
                            onToggleAutoJoin = onToggleAutoJoin?.let { fn -> { fn(room.jid, !room.isAutoJoin) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SavedRoomItem(
    room: XmppRoom,
    roomAvatar: ByteArray? = null,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onChangeColorClick: () -> Unit,
    onToggleAutoJoin: (() -> Unit)? = null
) {
    val roomColor = try {
        Color(android.graphics.Color.parseColor(room.colorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    // Clean up room display title if it duplicates the raw JID
    val cleanTitle = remember(room.name, room.jid) {
        if (room.name.isNotBlank() && !room.name.contains("@")) {
            room.name
        } else {
            val local = room.jid.substringBefore("@")
            if (local.isNotBlank()) "# $local" else room.jid
        }
    }

    val bitmap = remember(roomAvatar) {
        if (roomAvatar != null && roomAvatar.isNotEmpty()) {
            try {
                BitmapFactory.decodeByteArray(roomAvatar, 0, roomAvatar.size)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("saved_room_${room.jid}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, roomColor.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Avatar / Theme Color Icon with tap-to-change color feature
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(roomColor.copy(alpha = 0.15f))
                        .border(1.8.dp, roomColor, CircleShape)
                        .clickable { onChangeColorClick() }
                        .testTag("avatar_color_${room.jid}"),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Avatar $cleanTitle",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = cleanTitle.trimStart('#', ' ').take(2).uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = roomColor
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = cleanTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // Color dot badge
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(roomColor)
                                .clickable { onChangeColorClick() }
                        )
                    }

                    Text(
                        text = room.jid,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (room.topic.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = room.topic,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Delete Bookmark Action
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Hapus Bookmark",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Actions & Badges Row (Auto-join toggle & quick color selector)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Auto-Join Status Chip
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (room.isAutoJoin) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .clickable { onToggleAutoJoin?.invoke() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (room.isAutoJoin) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = if (room.isAutoJoin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (room.isAutoJoin) "Auto-Join Aktif" else "Auto-Join Nonaktif",
                        fontSize = 11.sp,
                        fontWeight = if (room.isAutoJoin) FontWeight.Bold else FontWeight.Normal,
                        color = if (room.isAutoJoin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Change Theme Color quick button
                OutlinedButton(
                    onClick = onChangeColorClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(14.dp), tint = roomColor)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ganti Warna", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
