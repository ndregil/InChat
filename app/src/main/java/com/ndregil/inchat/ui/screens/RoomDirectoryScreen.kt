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
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
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
    onSelectJoinedRoom: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showComplianceDialog by remember { mutableStateOf(false) }
    var discoveryMode by remember { mutableIntStateOf(0) } // 0: Global Search, 1: Local Services

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
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Discovery", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (discoveryMode == 0) "search.jabber.network" else serverDomain,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showComplianceDialog = true }) {
                        Icon(imageVector = Icons.Default.Verified, contentDescription = "Compliance Suite", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { 
                        if (discoveryMode == 0) onRefresh(searchQuery) else onDiscoverLocal() 
                    }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Muat Ulang")
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
                .padding(horizontal = 16.dp)
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
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                androidx.compose.material3.Tab(
                    selected = discoveryMode == 0,
                    onClick = { discoveryMode = 0 },
                    text = { Text("Global Search", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                )
                androidx.compose.material3.Tab(
                    selected = discoveryMode == 1,
                    onClick = { 
                        discoveryMode = 1
                        onDiscoverLocal()
                    },
                    text = { Text("Local Services", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable { showComplianceDialog = true },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Compliance Suite (${if (discoveryMode == 0) "search.jabber.network" else serverDomain})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Audit RFC 6120, RFC 7590 & XEP-0030",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                    androidx.compose.material3.TextButton(onClick = { showComplianceDialog = true }) {
                        Text("Audit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (discoveryMode == 0) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Cari room di search.jabber.network...") },
                    placeholder = { Text("Contoh: indonesia, linux, game") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { 
                                searchQuery = ""
                                onRefresh("")
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Clear")
                            }
                        }
                    }
                )

                Text(
                    text = "Menampilkan hasil pencarian dari direktori global XMPP:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Daftar komponen dan layanan di server $serverDomain (XEP-0030):",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            val currentLoading = if (discoveryMode == 0) isLoading else isServicesLoading
            if (currentLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (discoveryMode == 0) {
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
                    } else {
                        items(discoveredServices, key = { it.jid + (it.node ?: "") }) { serviceItem ->
                            ServiceItemCard(serviceItem)
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
fun ServiceItemCard(item: com.ndregil.inchat.data.model.DiscoveredItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
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
            Column {
                Text(
                    text = item.name,
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
        }
    }
}
