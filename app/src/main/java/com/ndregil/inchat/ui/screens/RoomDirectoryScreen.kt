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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.ndregil.inchat.data.model.ServerDiscoResult
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
import com.ndregil.inchat.ui.components.DiscoveredRoomCardItem
import com.ndregil.inchat.ui.components.RoomAvatarBadge
import com.ndregil.inchat.ui.theme.LocalThemePreferences

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
    serverDiscoResult: ServerDiscoResult = ServerDiscoResult(),
    isDiscoServerLoading: Boolean = false,
    onDiscoServer: (String, String?) -> Unit = { _, _ -> },
    onJoinDiscoveredRoom: ((com.ndregil.inchat.data.model.DiscoveredRoom) -> Unit)? = null,
    avatarRevision: Int = 0,
    getRoomAvatar: ((String) -> ByteArray?)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var discoInput by remember { mutableStateOf(serverDiscoResult.targetJid) }
    var discoFilterQuery by remember { mutableStateOf("") }
    val discoHistory = remember { mutableStateListOf<Pair<String, String?>>() }
    var showComplianceDialog by remember { mutableStateOf(false) }
    var discoveryMode by remember { mutableIntStateOf(0) } // 0: Global Search, 1: Disco Server, 2: Local Services
    var selectedService by remember { mutableStateOf<com.ndregil.inchat.data.model.DiscoveredItem?>(null) }

    fun triggerDisco(target: String, node: String? = null, pushHistory: Boolean = false) {
        val clean = target.trim()
        if (clean.isBlank()) return
        if (pushHistory && serverDiscoResult.targetJid.isNotBlank() &&
            !serverDiscoResult.targetJid.equals(clean, ignoreCase = true)
        ) {
            discoHistory.add(serverDiscoResult.targetJid to null)
        } else if (!pushHistory) {
            discoHistory.clear()
        }
        discoInput = clean
        discoFilterQuery = ""
        onDiscoServer(clean, node)
    }

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

                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            val targetJid = service.jid
                            val targetNode = service.node
                            selectedService = null
                            val shouldPushHistory = discoveryMode == 1
                            discoveryMode = 1
                            triggerDisco(targetJid, targetNode, pushHistory = shouldPushHistory)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Disco Layanan Ini", fontSize = 12.sp)
                    }

                    if (isMuc && onOpenCreateRoom != null) {
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                                    1 -> serverDiscoResult.targetJid.ifBlank { "XMPP Service Discovery" }
                                    else -> serverDomain
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
                                1 -> {
                                    val target = discoInput.trim().ifBlank { serverDiscoResult.targetJid }
                                    if (target.isNotBlank()) {
                                        onDiscoServer(target, null)
                                    }
                                }
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
                    onClick = { discoveryMode = 1 },
                    modifier = Modifier.testTag("tab_discover_server"),
                    text = { Text("Disco Server", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
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
            } else if (discoveryMode == 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Router,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (discoInput.isEmpty()) {
                                    Text(
                                        text = "Domain server (cth: jabber.ru, conference.jabber.ru)",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                androidx.compose.foundation.text.BasicTextField(
                                    value = discoInput,
                                    onValueChange = { discoInput = it },
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
                                            focusManager.clearFocus()
                                            triggerDisco(discoInput)
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("disco_server_input")
                                )
                            }
                            if (discoInput.isNotEmpty()) {
                                IconButton(
                                    onClick = { discoInput = "" },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Hapus",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            triggerDisco(discoInput)
                        },
                        enabled = discoInput.isNotBlank() && !isDiscoServerLoading,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("btn_disco_server")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Disco", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (discoHistory.isNotEmpty()) {
                    val parentTarget = discoHistory.last()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                val backTarget = discoHistory.removeAt(discoHistory.lastIndex)
                                discoInput = backTarget.first
                                discoFilterQuery = ""
                                onDiscoServer(backTarget.first, backTarget.second)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Kembali ke ${parentTarget.first}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
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
                1 -> isDiscoServerLoading
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
                            text = when (discoveryMode) {
                                0 -> "Memuat daftar room global..."
                                1 -> "Melakukan disco ke ${discoInput.trim().ifBlank { "server" }}..."
                                else -> "Memindai komponen server $serverDomain..."
                            },
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
                                textAlign = TextAlign.Center
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
                        contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp)
                    ) {
                        items(directoryRooms, key = { it.jid }) { roomItem ->
                            val isAlreadyJoined = joinedRooms.any { it.jid.equals(roomItem.jid, ignoreCase = true) && it.isJoined }
                            val isBookmarked = joinedRooms.any { it.jid.equals(roomItem.jid, ignoreCase = true) && it.isBookmarked }
                            val avatarBytes = remember(roomItem.jid, avatarRevision) {
                                getRoomAvatar?.invoke(roomItem.jid)
                            }
                            DirectoryRoomCard(
                                roomItem = roomItem,
                                isAlreadyJoined = isAlreadyJoined,
                                isBookmarked = isBookmarked,
                                avatarBytes = avatarBytes,
                                onSelectJoinedRoom = onSelectJoinedRoom,
                                onJoinRoom = onJoinRoom,
                                onSaveRoom = onSaveRoom
                            )
                        }
                    }
                }
            } else if (discoveryMode == 1) {
                val filteredRooms = remember(serverDiscoResult.rooms, discoFilterQuery) {
                    val q = discoFilterQuery.trim()
                    if (q.isBlank()) serverDiscoResult.rooms
                    else serverDiscoResult.rooms.filter {
                        it.name.contains(q, ignoreCase = true) ||
                            it.jid.contains(q, ignoreCase = true) ||
                            it.description.contains(q, ignoreCase = true)
                    }
                }

                when {
                    !serverDiscoResult.errorMessage.isNullOrBlank() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Router,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Gagal Disco Server",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = serverDiscoResult.errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        val target = discoInput.trim().ifBlank { serverDiscoResult.targetJid }
                                        if (target.isNotBlank()) onDiscoServer(target, null)
                                    }
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Coba Lagi")
                                }
                            }
                        }
                    }

                    serverDiscoResult.targetJid.isBlank() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Router,
                                    contentDescription = null,
                                    modifier = Modifier.size(52.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Disco Server XMPP",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Masukkan domain server atau layanan MUC di atas lalu tekan Disco, misalnya jabber.ru atau conference.jabber.ru.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    serverDiscoResult.services.isEmpty() && serverDiscoResult.rooms.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Explore,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Tidak ada item pada ${serverDiscoResult.targetJid}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )
                                if (serverDiscoResult.serverIdentity.isNotBlank() || serverDiscoResult.features.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "${serverDiscoResult.serverIdentity.ifBlank { "Server" }} • ${serverDiscoResult.features.size} fitur terdeteksi",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("disco_server_results_list"),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp)
                        ) {
                            item(key = "disco_summary_header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 2.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = serverDiscoResult.serverIdentity.ifBlank { serverDiscoResult.targetJid },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    val countParts = buildList {
                                        if (serverDiscoResult.services.isNotEmpty()) add("${serverDiscoResult.services.size} layanan")
                                        if (serverDiscoResult.rooms.isNotEmpty()) add("${serverDiscoResult.rooms.size} room")
                                    }.joinToString(" • ")
                                    Text(
                                        text = countParts,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (serverDiscoResult.rooms.size > 6) {
                                item(key = "disco_rooms_filter") {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(modifier = Modifier.weight(1f)) {
                                                if (discoFilterQuery.isEmpty()) {
                                                    Text(
                                                        text = "Filter hasil (${serverDiscoResult.rooms.size} room)...",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                    )
                                                }
                                                androidx.compose.foundation.text.BasicTextField(
                                                    value = discoFilterQuery,
                                                    onValueChange = { discoFilterQuery = it },
                                                    singleLine = true,
                                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                        fontSize = 13.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    ),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                            if (discoFilterQuery.isNotEmpty()) {
                                                IconButton(
                                                    onClick = { discoFilterQuery = "" },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "Reset filter", modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (serverDiscoResult.services.isNotEmpty()) {
                                items(
                                    serverDiscoResult.services,
                                    key = { "svc_${it.jid}_${it.node.orEmpty()}" }
                                ) { serviceItem ->
                                    val isMucService = serviceItem.jid.contains("conference", ignoreCase = true) ||
                                        serviceItem.jid.contains("muc", ignoreCase = true) ||
                                        serviceItem.jid.contains("chat", ignoreCase = true)
                                    ServiceItemCard(
                                        item = serviceItem,
                                        onClick = {
                                            if (isMucService) {
                                                triggerDisco(serviceItem.jid, serviceItem.node, pushHistory = true)
                                            } else {
                                                selectedService = serviceItem
                                            }
                                        }
                                    )
                                }
                            }

                            if (filteredRooms.isNotEmpty()) {
                                items(
                                    filteredRooms,
                                    key = { "room_${it.jid}" }
                                ) { room ->
                                    val isAlreadyJoined = joinedRooms.any { it.jid.equals(room.jid, ignoreCase = true) && it.isJoined }
                                    val isBookmarked = joinedRooms.any { it.jid.equals(room.jid, ignoreCase = true) && it.isBookmarked }
                                    val avatarBytes = remember(room.jid, avatarRevision) {
                                        getRoomAvatar?.invoke(room.jid)
                                    }
                                    DiscoveredRoomCardItem(
                                        room = room,
                                        isAlreadyJoined = isAlreadyJoined,
                                        isBookmarked = isBookmarked,
                                        avatarBytes = avatarBytes,
                                        onJoin = {
                                            if (onJoinDiscoveredRoom != null) {
                                                onJoinDiscoveredRoom(room)
                                            } else {
                                                onJoinRoom(room.toPublicDirectoryRoom())
                                            }
                                        },
                                        onOpen = { onSelectJoinedRoom(room.jid) },
                                        onBookmark = onSaveRoom?.let { saveFn ->
                                            { saveFn(room.toPublicDirectoryRoom()) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
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
                        contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp)
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
    onSaveRoom: ((PublicDirectoryRoom) -> Unit)? = null,
    avatarBytes: ByteArray? = null,
    isBookmarked: Boolean = false
) {
    val contactFontSizeSp = LocalThemePreferences.current.contactFontSizeSp
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
                RoomAvatarBadge(
                    roomJid = roomItem.jid,
                    roomName = roomItem.name,
                    avatarBytes = avatarBytes,
                    avatarUrl = roomItem.avatarUrl,
                    size = 46.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = roomItem.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = contactFontSizeSp.sp,
                        lineHeight = (contactFontSizeSp * 1.3f).sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = roomItem.jid,
                        fontSize = (contactFontSizeSp - 2f).coerceAtLeast(10f).sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

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
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isBookmarked) "Tersimpan" else "Simpan Room", fontSize = 12.sp)
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
