package com.ndregil.inchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndregil.inchat.data.model.PublicDirectoryRoom
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import android.widget.Toast
import com.ndregil.inchat.data.model.XmppRoom

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDirectoryScreen(
    directoryRooms: List<PublicDirectoryRoom>,
    discoveredServices: List<com.ndregil.inchat.data.model.DiscoveredItem>,
    joinedRooms: List<XmppRoom>,
    isLoading: Boolean,
    isServicesLoading: Boolean,
    serverDomain: String,
    onRefresh: (String) -> Unit,
    onDiscoverLocal: () -> Unit,
    onJoinRoom: (PublicDirectoryRoom) -> Unit,
    onSaveRoom: ((PublicDirectoryRoom) -> Unit)? = null,
    onSelectJoinedRoom: (String) -> Unit,
    onOpenCreateRoom: (() -> Unit)? = null,
    discoveredMucRooms: List<com.ndregil.inchat.data.model.DiscoveredRoom> = emptyList(),
    isDiscoveringServerRooms: Boolean = false,
    onDiscoverServerRooms: () -> Unit = {},
    onJoinDiscoveredRoom: ((com.ndregil.inchat.data.model.DiscoveredRoom) -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var showComplianceDialog by remember { mutableStateOf(false) }
    var discoveryMode by remember { mutableIntStateOf(0) } // 0: Global Search, 1: Server MUC (XEP-0030), 2: Local Services
    var selectedService by remember { mutableStateOf<com.ndregil.inchat.data.model.DiscoveredItem?>(null) }

    if (selectedService != null) {
        val service = selectedService!!
        val context = LocalContext.current
        val clipboardManager = LocalClipboardManager.current
        val isMuc = service.jid.contains("conference") || service.name.lowercase().contains("muc") || service.name.lowercase().contains("chat")

        AlertDialog(
            onDismissRequest = { selectedService = null },
            icon = {
                Icon(
                    imageVector = when {
                        service.jid.contains("conference") -> Icons.Default.Group
                        service.jid.contains("upload") -> Icons.Default.CloudUpload
                        service.jid.contains("proxy") -> Icons.Default.Router
                        service.jid.contains("pubsub") -> Icons.Default.RssFeed
                        service.jid.contains("search") -> Icons.Default.Search
                        else -> Icons.Default.Extension
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = service.name.ifBlank { service.jid },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = service.jid,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = when {
                                    service.jid.contains("conference") -> "Tipe: Layanan Multi-User Chat (MUC)"
                                    service.jid.contains("upload") -> "Tipe: Layanan HTTP File Upload (XEP-0363)"
                                    service.jid.contains("proxy") -> "Tipe: Layanan SOCKS5 Proxy (XEP-0065)"
                                    service.jid.contains("pubsub") -> "Tipe: Layanan Publish-Subscribe (XEP-0060)"
                                    service.jid.contains("search") -> "Tipe: Layanan Pencarian Direktori Server"
                                    else -> "Tipe: Layanan Komponen Server XMPP"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when {
                                    service.jid.contains("conference") -> "Server MUC ini dapat digunakan untuk bergabung atau membuat ruang percakapan grup baru."
                                    service.jid.contains("upload") -> "Layanan ini memungkinkan pengiriman foto, rekaman suara, dan berkas ke penerima."
                                    service.jid.contains("proxy") -> "Digunakan sebagai perantara transfer berkas langsung antar klien XMPP."
                                    service.jid.contains("pubsub") -> "Mendukung saluran transmisi data, feed berita, dan notifikasi."
                                    else -> "Komponen layanan aktif di server $serverDomain (XEP-0030 Service Discovery)."
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!service.node.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Node: ${service.node}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (isMuc) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = {
                                val mucJid = service.jid
                                selectedService = null
                                discoveryMode = 0
                                searchQuery = mucJid
                                onRefresh(mucJid)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cari Room di Service Ini", fontSize = 12.sp)
                        }

                        if (onOpenCreateRoom != null) {
                            OutlinedButton(
                                onClick = {
                                    selectedService = null
                                    onOpenCreateRoom()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Buat Room Baru di Server Ini", fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(service.jid))
                        Toast.makeText(context, "JID ${service.jid} disalin ke papan klip", Toast.LENGTH_SHORT).show()
                        selectedService = null
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Salin JID Service")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedService = null }) {
                    Text("Tutup")
                }
            }
        )
    }

    if (showComplianceDialog) {
        com.ndregil.inchat.ui.components.ComplianceSuiteDialog(
            serviceName = if (discoveryMode == 0) "search.jabber.network" else serverDomain,
            onDismiss = { showComplianceDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "Discovery",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Text(
                                text = when (discoveryMode) {
                                    0 -> "search.jabber.network"
                                    1 -> "Server MUC (XEP-0030)"
                                    else -> serverDomain
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { 
                            when (discoveryMode) {
                                0 -> onRefresh(searchQuery)
                                1 -> onDiscoverServerRooms()
                                else -> onDiscoverLocal()
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Muat Ulang",
                            modifier = Modifier.size(20.dp)
                        )
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
                .padding(horizontal = 12.dp)
        ) {
            androidx.compose.material3.TabRow(
                selectedTabIndex = discoveryMode,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {},
                indicator = { tabPositions ->
                   if (discoveryMode in tabPositions.indices) {
                       androidx.compose.material3.TabRowDefaults.SecondaryIndicator(
                           modifier = Modifier.tabIndicatorOffset(tabPositions[discoveryMode]),
                           color = MaterialTheme.colorScheme.primary
                       )
                   }
                },
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                androidx.compose.material3.Tab(
                    selected = discoveryMode == 0,
                    onClick = { discoveryMode = 0 },
                    text = { Text("Global Search", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                androidx.compose.material3.Tab(
                    selected = discoveryMode == 1,
                    onClick = { 
                        discoveryMode = 1
                        onDiscoverServerRooms()
                    },
                    text = { Text("Server MUC (XEP-0030)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                androidx.compose.material3.Tab(
                    selected = discoveryMode == 2,
                    onClick = { 
                        discoveryMode = 2
                        onDiscoverLocal()
                    },
                    text = { Text("Local Services", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }

            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            val quickKeywords = listOf("Semua", "Indonesia", "Linux", "Conversations", "XSF", "Gajim", "Android")

            if (discoveryMode == 0) {
                // Compact Search Bar (kolom pencarian kecil)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .padding(vertical = 1.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Cari room (indonesia, linux, game)...",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            androidx.compose.foundation.text.BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                                ),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                    onSearch = {
                                        onRefresh(searchQuery)
                                        focusManager.clearFocus()
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_directory_input")
                            )
                        }

                        if (searchQuery.isNotBlank()) {
                            IconButton(
                                onClick = { 
                                    searchQuery = ""
                                    onRefresh("")
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Hapus",
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                onRefresh(searchQuery)
                                focusManager.clearFocus()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Cari",
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Compact Quick Topic Filter Chips
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(quickKeywords) { kw ->
                        val isSelected = (kw == "Semua" && searchQuery.isBlank()) || (searchQuery.equals(kw, ignoreCase = true))
                        androidx.compose.material3.FilterChip(
                            selected = isSelected,
                            onClick = {
                                val target = if (kw == "Semua") "" else kw
                                searchQuery = target
                                onRefresh(target)
                            },
                            label = { Text(kw, fontSize = 11.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "Room populer global XMPP:" else "Hasil untuk '$searchQuery':",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${directoryRooms.size} room",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Router, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Layanan Server $serverDomain",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Komponen protokol XEP-0030 (MUC, Upload, Proxy)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(
                            onClick = onDiscoverLocal,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Pindai Ulang", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            val currentLoading = when (discoveryMode) {
                0 -> isLoading
                1 -> false // DiscoveredMucRoomsList handles its own loading state smoothly
                else -> isServicesLoading
            }
            if (currentLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (discoveryMode == 0) "Memuat daftar room global..." else "Memindai komponen server $serverDomain...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (discoveryMode == 0) {
                if (directoryRooms.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (searchQuery.isBlank()) "Belum ada room ditemukan" else "Tidak ada room ditemukan untuk '$searchQuery'",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Coba kata kunci lain atau muat ulang daftar room populer search.jabber.network",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    searchQuery = ""
                                    onRefresh("")
                                }
                            ) {
                                Text("Tampilkan Room Populer")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 120.dp, top = 4.dp)
                    ) {
                        items(directoryRooms, key = { it.jid }) { roomItem ->
                            val isAlreadyJoined = joinedRooms.any { it.jid == roomItem.jid && it.isJoined }
                            DirectoryRoomCard(
                                roomItem = roomItem,
                                isAlreadyJoined = isAlreadyJoined,
                                onSelectJoinedRoom = onSelectJoinedRoom,
                                onJoinRoom = onJoinRoom,
                                onSaveRoom = onSaveRoom
                            )
                        }
                    }
                }
            } else if (discoveryMode == 1) {
                // Server MUC Room Discovery (XEP-0030)
                com.ndregil.inchat.ui.components.DiscoveredMucRoomsList(
                    rooms = discoveredMucRooms,
                    isLoading = isDiscoveringServerRooms,
                    joinedRoomJids = joinedRooms.filter { it.isJoined }.map { it.jid }.toSet(),
                    onJoinRoom = { discRoom ->
                        if (onJoinDiscoveredRoom != null) {
                            onJoinDiscoveredRoom(discRoom)
                        } else {
                            onJoinRoom(discRoom.toPublicDirectoryRoom())
                        }
                    },
                    onSelectJoinedRoom = onSelectJoinedRoom,
                    onRefresh = onDiscoverServerRooms,
                    onBookmarkRoom = onSaveRoom?.let { saveFn ->
                        { discRoom -> saveFn(discRoom.toPublicDirectoryRoom()) }
                    },
                    serverDomain = serverDomain
                )
            } else {
                // Local Services (discoveryMode == 2)
                if (discoveredServices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Router,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Belum Ada Layanan Terdeteksi",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tekan tombol di bawah untuk memindai komponen MUC, HTTP File Upload, dan Proxy di server $serverDomain",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onDiscoverLocal
                            ) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pindai Layanan Server")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 120.dp, top = 4.dp)
                    ) {
                        items(discoveredServices, key = { it.jid + (it.node ?: "") }) { serviceItem ->
                            ServiceItemCard(
                                item = serviceItem,
                                onClick = { selectedService = serviceItem }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DirectoryRoomCard(
    roomItem: PublicDirectoryRoom,
    isAlreadyJoined: Boolean,
    onSelectJoinedRoom: (String) -> Unit,
    onJoinRoom: (PublicDirectoryRoom) -> Unit,
    onSaveRoom: ((PublicDirectoryRoom) -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .testTag("directory_room_${roomItem.jid}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = roomItem.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = roomItem.jid,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (roomItem.isPasswordProtected) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Sandi",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    if (roomItem.isMembersOnly) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Member Only",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${roomItem.occupantsCount}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = roomItem.description.ifBlank { "Tidak ada deskripsi tersedia." },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            if (roomItem.category != "General") {
                Text(
                    text = "Info: ${roomItem.category}",
                    fontSize = 10.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onSaveRoom != null) {
                    OutlinedButton(
                        onClick = { onSaveRoom(roomItem) },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Simpan Room", fontSize = 12.sp)
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (isAlreadyJoined) {
                    Button(
                        onClick = { onSelectJoinedRoom(roomItem.jid) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Buka Obrolan", fontSize = 12.sp)
                    }
                } else {
                    Button(
                        onClick = { onJoinRoom(roomItem) },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Gabung Room", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ServiceItemCard(
    item: com.ndregil.inchat.data.model.DiscoveredItem,
    onClick: () -> Unit = {}
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("service_item_${item.jid}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        item.jid.contains("conference") -> Icons.Default.Group
                        item.jid.contains("upload") -> Icons.Default.CloudUpload
                        item.jid.contains("proxy") -> Icons.Default.Router
                        item.jid.contains("pubsub") -> Icons.Default.RssFeed
                        item.jid.contains("search") -> Icons.Default.Search
                        else -> Icons.Default.Extension
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name.ifBlank { item.jid },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item.jid,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!item.node.isNullOrBlank()) {
                    Text(
                        text = "Node: ${item.node}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            item.jid.contains("conference") -> "MUC"
                            item.jid.contains("upload") -> "Upload"
                            item.jid.contains("proxy") -> "Proxy"
                            item.jid.contains("pubsub") -> "PubSub"
                            item.jid.contains("search") -> "Search"
                            else -> "Service"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Detail",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}
