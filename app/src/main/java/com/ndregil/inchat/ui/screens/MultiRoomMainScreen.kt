package com.ndregil.inchat.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.NightsStay
import com.ndregil.inchat.data.model.XmppMessage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ndregil.inchat.data.model.CallInfo
import com.ndregil.inchat.data.model.ConnectionStatus
import com.ndregil.inchat.data.model.RoomOccupant
import com.ndregil.inchat.data.model.UserPresence
import com.ndregil.inchat.data.model.XmppRoom
import com.ndregil.inchat.ui.XmppViewModel
import com.ndregil.inchat.ui.components.ClientCapsBadge
import com.ndregil.inchat.ui.components.ClientCapsDialog
import com.ndregil.inchat.ui.components.ClientCapsIcon
import com.ndregil.inchat.ui.components.getClientPlatformColor
import com.ndregil.inchat.data.network.fcm.FcmPushManager
import com.ndregil.inchat.ui.components.JoinCreateRoomDialog
import com.ndregil.inchat.ui.components.MessageBubble
import com.ndregil.inchat.ui.components.OccupantRosterSheet
import com.ndregil.inchat.ui.components.RoomConfigurationDialog
import com.ndregil.inchat.ui.components.RoomItemCard
import com.ndregil.inchat.ui.components.RoomTopicBanner
import com.ndregil.inchat.ui.components.StanzaConsoleSheet
import com.ndregil.inchat.ui.theme.AwayAmber
import com.ndregil.inchat.ui.theme.DndRed
import com.ndregil.inchat.ui.theme.OfflineGray
import com.ndregil.inchat.ui.theme.OnlineGreen
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiRoomMainScreen(
    viewModel: XmppViewModel,
    modifier: Modifier = Modifier
) {
    var activeNavIndex by remember { mutableIntStateOf(0) } // 0 = Rooms Chat, 1 = Roster Contacts, 2 = Directory, 3 = Account Settings
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showRoomMenu by remember { mutableStateOf(false) }
    var selectedCapsOccupant by remember { mutableStateOf<RoomOccupant?>(null) }
    var showAccountSettingsDirectly by remember { mutableStateOf(false) }

    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val currentAccount by viewModel.currentAccount.collectAsStateWithLifecycle()
    val registrationState by viewModel.registrationState.collectAsStateWithLifecycle()
    val allRooms by viewModel.allRooms.collectAsStateWithLifecycle()
    val filteredRooms by viewModel.filteredRooms.collectAsStateWithLifecycle()
    val isDarkModeOverride by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDarkMode = isDarkModeOverride ?: systemDark

    val savedRooms by viewModel.savedRooms.collectAsStateWithLifecycle()
    var showOnlySavedRooms by remember { mutableStateOf(false) }
    val activeRoom by viewModel.activeRoom.collectAsStateWithLifecycle()
    val messages by viewModel.activeRoomMessages.collectAsStateWithLifecycle()
    val occupants by viewModel.activeRoomOccupants.collectAsStateWithLifecycle()
    val stanzaLogs by viewModel.stanzaLogs.collectAsStateWithLifecycle()
    val directoryRooms by viewModel.directoryRooms.collectAsStateWithLifecycle()
    val isDirectoryLoading by viewModel.isDirectoryLoading.collectAsStateWithLifecycle()
    val whisperTarget by viewModel.whisperTargetOccupant.collectAsStateWithLifecycle()
    val replyingToMessage by viewModel.replyingToMessage.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val isBackupLoading by viewModel.isBackupLoading.collectAsStateWithLifecycle()
    val currentInput by viewModel.currentInputText.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val isRosterOpen by viewModel.isRosterSheetOpen.collectAsStateWithLifecycle()
    val isStanzaConsoleOpen by viewModel.isStanzaConsoleOpen.collectAsStateWithLifecycle()
    val isJoinCreateOpen by viewModel.isJoinCreateDialogOpen.collectAsStateWithLifecycle()
    val isThemeWallpaperOpen by viewModel.isThemeWallpaperDialogOpen.collectAsStateWithLifecycle()
    val isRoomConfigOpen by viewModel.isRoomConfigOpen.collectAsStateWithLifecycle()
    val isAdminPanelOpen by viewModel.isAdminPanelOpen.collectAsStateWithLifecycle()
    val roomAdminInfo by viewModel.roomAdminInfo.collectAsStateWithLifecycle()
    val isTopicModalOpen by viewModel.isTopicModalOpen.collectAsStateWithLifecycle()
    var showTopicDetailDialog by remember { mutableStateOf(false) }
    var editTopicText by remember(activeRoom?.topic) { mutableStateOf(activeRoom?.topic ?: "") }

    var roomConfigInfo by remember { mutableStateOf<org.jivesoftware.smackx.muc.RoomInfo?>(null) }

    val rosterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val stanzaSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val filePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val cursor = context.contentResolver.query(it, null, null, null, null)
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    val name = if (nameIndex >= 0) c.getString(nameIndex) else "file"
                    val size = if (sizeIndex >= 0) c.getLong(sizeIndex) else 0L
                    val mimeType = context.contentResolver.getType(it) ?: "application/octet-stream"
                    viewModel.sendFile(it, name, size, mimeType)
                }
            }
        }
    }

    // Auto scroll to latest message when messages change
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Keep active room in sync with FcmPushManager to suppress redundant notifications
    LaunchedEffect(activeNavIndex, activeRoom?.jid) {
        FcmPushManager.activeRoomJid = if (activeNavIndex == 0) activeRoom?.jid else null
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val totalUnread = allRooms.sumOf { it.unreadCount }
                    CompactNavItem(
                        selected = activeNavIndex == 0,
                        onClick = { activeNavIndex = 0 },
                        icon = Icons.Default.Forum,
                        label = "Multi-Room",
                        badgeCount = totalUnread,
                        testTag = "nav_tab_rooms"
                    )

                    CompactNavItem(
                        selected = activeNavIndex == 1,
                        onClick = { activeNavIndex = 1 },
                        icon = Icons.Default.Bookmark,
                        label = "Favorit",
                        testTag = "nav_tab_saved"
                    )

                    CompactNavItem(
                        selected = activeNavIndex == 2,
                        onClick = {
                            activeNavIndex = 2
                            viewModel.refreshRoster()
                        },
                        icon = Icons.Default.Contacts,
                        label = "Kontak",
                        testTag = "nav_tab_roster"
                    )

                    CompactNavItem(
                        selected = activeNavIndex == 3,
                        onClick = {
                            activeNavIndex = 3
                            viewModel.loadDirectory()
                        },
                        icon = Icons.Default.Explore,
                        label = "Discover",
                        testTag = "nav_tab_discover"
                    )

                    CompactNavItem(
                        selected = activeNavIndex == 4,
                        onClick = {
                            activeNavIndex = 4
                            showAccountSettingsDirectly = false
                        },
                        icon = Icons.Default.Settings,
                        label = "Pengaturan",
                        testTag = "nav_tab_settings"
                    )
                }
            }
        },
        topBar = {
            if (activeNavIndex == 0) {
                TopAppBar(
                    title = {
                        if (isSearchExpanded) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.updateSearchQuery(it) },
                                placeholder = { Text("Cari ruangan atau topik...", fontSize = 14.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_rooms_input"),
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = {
                                        viewModel.updateSearchQuery("")
                                        isSearchExpanded = false
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Tutup Pencarian")
                                    }
                                }
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { showTopicDetailDialog = true }
                                    .padding(vertical = 2.dp)
                            ) {
                                // Connection Status Dot
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (connectionStatus) {
                                                ConnectionStatus.CONNECTED -> OnlineGreen
                                                ConnectionStatus.CONNECTING, ConnectionStatus.AUTHENTICATING -> AwayAmber
                                                ConnectionStatus.ERROR -> DndRed
                                                ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = activeRoom?.name ?: "InChat",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (activeRoom != null && activeRoom?.topic?.isNotBlank() == true) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = "Topik Ruang",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (activeRoom != null && activeRoom?.topic?.isNotBlank() == true) {
                                            activeRoom?.topic ?: ""
                                        } else {
                                            "${occupants.size} online • ${activeRoom?.jid ?: currentAccount.domain}"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (activeRoom != null && activeRoom?.topic?.isNotBlank() == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.toggleDarkMode(systemDark) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.WbSunny else Icons.Default.NightsStay,
                                contentDescription = "Toggle Tema",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        if (!isSearchExpanded) {
                            IconButton(
                                onClick = { isSearchExpanded = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Cari Room", modifier = Modifier.size(19.dp))
                            }

                            // Audio Call Action
                            IconButton(
                                onClick = { viewModel.initiateCall("Audio") },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("btn_top_call_audio")
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Panggilan Audio", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                            }

                            // Video Call Action
                            IconButton(
                                onClick = { viewModel.initiateCall("Video") },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("btn_top_call_video")
                            ) {
                                Icon(Icons.Default.VideoCall, contentDescription = "Panggilan Video", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                            }

                            // Roster Sheet button
                            IconButton(
                                onClick = { viewModel.isRosterSheetOpen.value = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("btn_open_roster")
                            ) {
                                BadgedBox(badge = {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ) {
                                        Text("${occupants.size}", fontSize = 9.sp)
                                    }
                                }) {
                                    Icon(Icons.Default.Group, contentDescription = "Daftar Anggota", modifier = Modifier.size(19.dp))
                                }
                            }

                            // Stanza Console Shortcut
                            IconButton(
                                onClick = { viewModel.isStanzaConsoleOpen.value = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("btn_open_stanzas")
                            ) {
                                Icon(Icons.Default.Code, contentDescription = "Stanza XML Stream", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                            }

                            // More options
                            IconButton(
                                onClick = { showRoomMenu = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Menu", modifier = Modifier.size(19.dp))
                            }

                            DropdownMenu(
                                expanded = showRoomMenu,
                                onDismissRequest = { showRoomMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Lihat Topik & Info Ruang") },
                                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        showRoomMenu = false
                                        showTopicDetailDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Panggilan Audio Room") },
                                    leadingIcon = { Icon(Icons.Default.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        showRoomMenu = false
                                        viewModel.initiateCall("Audio")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Panggilan Video Room") },
                                    leadingIcon = { Icon(Icons.Default.VideoCall, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        showRoomMenu = false
                                        viewModel.initiateCall("Video")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Buat / Gabung Room Baru") },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                    onClick = {
                                        showRoomMenu = false
                                        viewModel.isJoinCreateDialogOpen.value = true
                                    }
                                )
                                val isBookmarked = activeRoom?.isBookmarked == true
                                DropdownMenuItem(
                                    text = { Text(if (isBookmarked) "Hapus dari Ruangan Tersimpan" else "Simpan Room ke Favorit") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                            contentDescription = null,
                                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        showRoomMenu = false
                                        activeRoom?.let { viewModel.toggleSaveRoom(it.jid) }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Ubah Topik Room") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showRoomMenu = false
                                        editTopicText = activeRoom?.topic ?: ""
                                        viewModel.isTopicModalOpen.value = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Warna Chat & Wallpaper") },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    onClick = {
                                        showRoomMenu = false
                                        viewModel.isThemeWallpaperDialogOpen.value = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Pengaturan Server Room") },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    onClick = {
                                        showRoomMenu = false
                                        activeRoom?.let { room ->
                                            coroutineScope.launch {
                                                val info = viewModel.repository.getRoomInfo(room.jid)
                                                roomConfigInfo = info.getOrNull()
                                                viewModel.isRoomConfigOpen.value = true
                                            }
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Tinggalkan Room Ini") },
                                    leadingIcon = { Icon(Icons.Default.ExitToApp, contentDescription = null) },
                                    onClick = {
                                        showRoomMenu = false
                                        activeRoom?.let { viewModel.leaveRoom(it.jid) }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Bersihkan Riwayat Chat") },
                                    leadingIcon = { Icon(Icons.Default.Clear, contentDescription = null) },
                                    onClick = {
                                        showRoomMenu = false
                                        activeRoom?.let { viewModel.clearRoomChatHistory(it.jid) }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Hapus Room dari Daftar") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showRoomMenu = false
                                        activeRoom?.let { viewModel.deleteRoom(it.jid) }
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (activeNavIndex) {
                0 -> {
                    // Multi-Room Chat View with dynamic horizontal multi-room switcher tabs
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Multi-Room Channel Switcher Tabs with integrated Bookmark Filter Toggle
                        MultiRoomTabHeader(
                            rooms = if (showOnlySavedRooms) savedRooms else filteredRooms,
                            selectedRoomJid = activeRoom?.jid,
                            showOnlySavedRooms = showOnlySavedRooms,
                            onToggleSavedOnly = { showOnlySavedRooms = !showOnlySavedRooms },
                            onSelectRoom = { jid -> viewModel.selectRoom(jid) },
                            onAddRoomClick = { viewModel.isJoinCreateDialogOpen.value = true }
                        )

                        // Message Feed (Maximized space for leluasa group chat view)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            com.ndregil.inchat.ui.components.WallpaperBackground(
                                wallpaperStyle = activeRoom?.wallpaperStyle ?: "default"
                            )

                            if (messages.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Forum,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Belum ada pesan di ruang ${activeRoom?.name ?: ""}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Kirim pesan pertama untuk memulai obrolan dengan seluruh partisipan.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    items(messages, key = { it.id }) { msg ->
                                        MessageBubble(
                                            message = msg,
                                            myNickname = activeRoom?.myNickname ?: currentAccount.nickname,
                                            onSenderClick = { senderNick ->
                                                val found = occupants.find { it.nickname == senderNick }
                                                viewModel.setWhisperTarget(found)
                                            },
                                            onReplyClick = { replyMsg -> viewModel.setReplyMessage(replyMsg) },
                                            onQuoteClick = { quoteMsg -> viewModel.quoteMessage(quoteMsg) },
                                            onMentionClick = { nick -> viewModel.insertMention(nick) },
                                            onQuerySoftwareVersion = { jid, callback -> viewModel.querySoftwareVersion(jid, callback) },
                                            bubbleTheme = activeRoom?.bubbleTheme ?: "dregil-theme",
                                            isDark = isDarkMode
                                        )
                                    }
                                }
                            }
                        }

                        // Whisper Target Banner (if whispering)
                        AnimatedVisibility(visible = whisperTarget != null) {
                            whisperTarget?.let { target ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        ClientCapsIcon(
                                            platform = target.clientCaps.platform,
                                            clientCaps = target.clientCaps,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier
                                                .size(15.dp)
                                                .clickable { selectedCapsOccupant = target }
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Mode Bisikan ke @${target.nickname} (${target.clientCaps.clientName})",
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.setWhisperTarget(null) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Batalkan Bisikan",
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Input Bar
                        ChatInputBar(
                            inputText = currentInput,
                            onInputChange = { viewModel.currentInputText.value = it },
                            onSendMessage = { viewModel.sendMessage(it) },
                            onAttachClick = { filePicker.launch("*/*") },
                            onCallClick = { viewModel.initiateCall(it) },
                            whisperTarget = whisperTarget,
                            replyingToMessage = replyingToMessage,
                            onCancelReply = { viewModel.clearReplyMessage() }
                        )
                    }
                }

                1 -> {
                    // Dedicated Saved Rooms / Bookmarks Screen
                    SavedRoomsScreen(
                        savedRooms = savedRooms,
                        onSelectRoom = { jid ->
                            viewModel.selectRoom(jid)
                            activeNavIndex = 0
                        },
                        onRemoveBookmark = { jid -> viewModel.toggleSaveRoom(jid) },
                        onAddRoomClick = { viewModel.isJoinCreateDialogOpen.value = true }
                    )
                }

                2 -> {
                    // XMPP Roster & Contacts
                    RosterContactsScreen(
                        viewModel = viewModel
                    )
                }

                3 -> {
                    // Public MUC Directory
                    RoomDirectoryScreen(
                        directoryRooms = directoryRooms,
                        joinedRooms = allRooms,
                        isLoading = isDirectoryLoading,
                        onRefresh = { query -> viewModel.loadDirectory(query) },
                        onJoinRoom = { dirRoom ->
                            viewModel.joinOrCreateRoom(
                                roomJid = dirRoom.jid,
                                roomName = dirRoom.name,
                                nickname = currentAccount.nickname,
                                topic = dirRoom.description
                            )
                            activeNavIndex = 0
                        },
                        onSaveRoom = { dirRoom ->
                            viewModel.saveRoom(
                                roomJid = dirRoom.jid,
                                roomName = dirRoom.name,
                                nickname = currentAccount.nickname,
                                topic = dirRoom.description
                            )
                        },
                        onSelectJoinedRoom = { jid ->
                            viewModel.selectRoom(jid)
                            activeNavIndex = 0
                        }
                    )
                }

                4 -> {
                    if (!showAccountSettingsDirectly) {
                        SettingsScreen(
                            currentAccount = currentAccount,
                            connectionStatus = connectionStatus,
                            registrationState = registrationState,
                            onConnect = { acc, pwd -> viewModel.connectAccount(acc, pwd) },
                            onDisconnect = { viewModel.disconnect() },
                            onRegisterAccount = { user, pass, dom, host, port, nick, email, tls ->
                                viewModel.registerAccount(
                                    username = user,
                                    password = pass,
                                    domain = dom,
                                    host = host,
                                    port = port,
                                    nickname = nick,
                                    email = email,
                                    useTls = tls,
                                    autoLogin = true
                                )
                            },
                            onCreateInstantAccount = { user, dom, nick ->
                                viewModel.createInstantAccount(user, dom, nick)
                            },
                            onResetRegistrationState = { viewModel.resetRegistrationState() },
                            backupStatus = backupStatus,
                            isBackupLoading = isBackupLoading,
                            onExportBackup = { uri, pwd -> viewModel.exportBackup(uri, pwd) },
                            onImportBackup = { uri, pwd -> viewModel.importBackup(uri, pwd) },
                            onClearBackupStatus = { viewModel.clearBackupStatus() },
                            onUpdateAvatar = { viewModel.updateAvatar(it) },
                            myAvatar = viewModel.myAvatar.collectAsStateWithLifecycle().value,
                            isDarkMode = isDarkMode,
                            onToggleDarkMode = { viewModel.toggleDarkMode(systemDark) },
                            onSwitchToAccountTab = { showAccountSettingsDirectly = true }
                        )
                    } else {
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = { Text("Konfigurasi Akun & Server") },
                                    navigationIcon = {
                                        IconButton(onClick = { showAccountSettingsDirectly = false }) {
                                            Icon(Icons.Default.Close, contentDescription = "Tutup")
                                        }
                                    }
                                )
                            }
                        ) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding)) {
                                AccountConnectionScreen(
                                    currentAccount = currentAccount,
                                    connectionStatus = connectionStatus,
                                    registrationState = registrationState,
                                    onConnect = { acc, pwd -> viewModel.connectAccount(acc, pwd) },
                                    onDisconnect = { viewModel.disconnect() },
                                    onRegisterAccount = { user, pass, dom, host, port, nick, email, tls ->
                                        viewModel.registerAccount(
                                            username = user,
                                            password = pass,
                                            domain = dom,
                                            host = host,
                                            port = port,
                                            nickname = nick,
                                            email = email,
                                            useTls = tls,
                                            autoLogin = true
                                        )
                                    },
                                    onCreateInstantAccount = { user, dom, nick ->
                                        viewModel.createInstantAccount(user, dom, nick)
                                    },
                                    onResetRegistrationState = { viewModel.resetRegistrationState() },
                                    backupStatus = backupStatus,
                                    isBackupLoading = isBackupLoading,
                                    onExportBackup = { uri, pwd -> viewModel.exportBackup(uri, pwd) },
                                    onImportBackup = { uri, pwd -> viewModel.importBackup(uri, pwd) },
                                    onClearBackupStatus = { viewModel.clearBackupStatus() },
                                    onUpdateAvatar = { viewModel.updateAvatar(it) },
                                    myAvatar = viewModel.myAvatar.collectAsStateWithLifecycle().value
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheets & Dialogs
    if (isRosterOpen) {
        OccupantRosterSheet(
            occupants = occupants,
            roomName = activeRoom?.name ?: "Room",
            sheetState = rosterSheetState,
            onDismiss = { viewModel.isRosterSheetOpen.value = false },
            onRefresh = { viewModel.refreshRoomOccupants() },
            onWhisperClick = { occ -> viewModel.setWhisperTarget(occ) },
            onMentionClick = { occ ->
                viewModel.currentInputText.value = "${viewModel.currentInputText.value} @${occ.nickname} ".trimStart()
                viewModel.isRosterSheetOpen.value = false
            },
            onOpenAdminPanel = {
                activeRoom?.let { viewModel.openAdminPanel(it.jid) }
            },
            onQuerySoftwareVersion = { jid, callback -> viewModel.querySoftwareVersion(jid, callback) }
        )
    }

    if (isStanzaConsoleOpen) {
        StanzaConsoleSheet(
            logs = stanzaLogs,
            sheetState = stanzaSheetState,
            onDismiss = { viewModel.isStanzaConsoleOpen.value = false },
            onSendRawStanza = { xml -> viewModel.sendRawXmlStanza(xml) }
        )
    }

    if (isJoinCreateOpen) {
        JoinCreateRoomDialog(
            defaultNickname = currentAccount.nickname,
            onDismiss = { viewModel.isJoinCreateDialogOpen.value = false },
            onJoinOrCreate = { jid, name, nick, topic, pwd, save ->
                viewModel.joinOrCreateRoom(jid, name, nick, topic, pwd, save)
                activeNavIndex = 0
            }
        )
    }

    if (isRoomConfigOpen) {
        activeRoom?.let { room ->
            RoomConfigurationDialog(
                roomJid = room.jid,
                initialInfo = roomConfigInfo,
                onDismiss = { viewModel.isRoomConfigOpen.value = false },
                onSave = { name, topic, persistent, public, pass ->
                    viewModel.configureRoom(room.jid, name, topic, persistent, public, pass)
                }
            )
        }
    }

    if (isThemeWallpaperOpen) {
        com.ndregil.inchat.ui.components.ChatThemeSettingsDialog(
            currentTheme = activeRoom?.bubbleTheme ?: "dregil-theme",
            currentWallpaper = activeRoom?.wallpaperStyle ?: "default",
            onSave = { theme, wallpaper ->
                activeRoom?.let { room ->
                    viewModel.updateRoomThemeAndWallpaper(room.jid, theme, wallpaper)
                }
            },
            onDismiss = { viewModel.isThemeWallpaperDialogOpen.value = false }
        )
    }

    if (isAdminPanelOpen && activeRoom != null) {
        com.ndregil.inchat.ui.components.RoomAdminPanel(
            roomJid = activeRoom!!.jid,
            roomName = activeRoom!!.name,
            occupants = occupants,
            initialInfo = roomAdminInfo,
            onDismiss = { viewModel.isAdminPanelOpen.value = false },
            onSaveConfig = { name, topic, persistent, public, pass ->
                viewModel.configureRoom(activeRoom!!.jid, name, topic, persistent, public, pass)
            },
            onGrantModerator = { nick -> viewModel.grantModerator(activeRoom!!.jid, nick) },
            onRevokeModerator = { nick -> viewModel.revokeModerator(activeRoom!!.jid, nick) },
            onGrantMembership = { jid -> viewModel.grantMembership(activeRoom!!.jid, jid) },
            onRevokeMembership = { jid -> viewModel.revokeMembership(activeRoom!!.jid, jid) },
            onGrantAdmin = { jid -> viewModel.grantAdmin(activeRoom!!.jid, jid) },
            onRevokeAdmin = { jid -> viewModel.revokeAdmin(activeRoom!!.jid, jid) },
            onBanUser = { jid, reason -> viewModel.banUser(activeRoom!!.jid, jid, reason) },
            onKickUser = { nick, reason -> viewModel.kickParticipant(activeRoom!!.jid, nick, reason) }
        )
    }

    selectedCapsOccupant?.let { occ ->
        ClientCapsDialog(
            clientCaps = occ.clientCaps,
            userName = "${occ.nickname} (${occ.jid.ifBlank { "MUC Occupant" }})",
            onDismiss = { selectedCapsOccupant = null }
        )
    }

    if (showTopicDetailDialog && activeRoom != null) {
        val currentTopic = activeRoom!!.topic
        AlertDialog(
            onDismissRequest = { showTopicDetailDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Topik Ruang: ${activeRoom!!.name}", style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (currentTopic.isNotBlank()) currentTopic else "(Belum ada topik yang diatur untuk ruang ini)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Text(
                        text = "JID: ${activeRoom!!.jid}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showTopicDetailDialog = false
                    editTopicText = currentTopic
                    viewModel.isTopicModalOpen.value = true
                }) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ubah Topik")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTopicDetailDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }

    if (isTopicModalOpen && activeRoom != null) {
        AlertDialog(
            onDismissRequest = { viewModel.isTopicModalOpen.value = false },
            title = { Text("Ubah Topik Ruangan") },
            text = {
                Column {
                    Text(
                        text = "Topik akan disiarkan ke seluruh partisipan MUC (${activeRoom!!.name}).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = editTopicText,
                        onValueChange = { editTopicText = it },
                        label = { Text("Topik Baru") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateTopic(editTopicText)
                    }
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.isTopicModalOpen.value = false }) {
                    Text("Batal")
                }
            }
        )
    }

    PanggilanOverlay(viewModel = viewModel)
}

@Composable
fun RoomOccupantsCapsBar(
    occupants: List<RoomOccupant>,
    onOccupantClick: (RoomOccupant) -> Unit,
    onOpenRosterClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(occupants, key = { it.nickname }) { occupant ->
                    val presenceColor = when (occupant.presence) {
                        UserPresence.ONLINE -> OnlineGreen
                        UserPresence.CHAT -> androidx.compose.ui.graphics.Color(0xFF0D9488)
                        UserPresence.AWAY -> AwayAmber
                        UserPresence.XA -> androidx.compose.ui.graphics.Color(0xFFD97706)
                        UserPresence.DND -> DndRed
                        UserPresence.OFFLINE -> OfflineGray
                    }
                    val platformColor = getClientPlatformColor(occupant.clientCaps.platform)

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            0.8.dp,
                            platformColor.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onOccupantClick(occupant) }
                            .testTag("quick_occupant_${occupant.nickname}")
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Avatar with presence
                            Box(contentAlignment = Alignment.BottomEnd) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (occupant.avatar != null) {
                                        coil.compose.AsyncImage(
                                            model = occupant.avatar,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            text = occupant.nickname.take(1).uppercase(Locale.getDefault()),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(presenceColor)
                                )
                            }

                            Text(
                                text = occupant.nickname,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )

                            // Client Caps Icon
                            ClientCapsIcon(
                                platform = occupant.clientCaps.platform,
                                clientCaps = occupant.clientCaps,
                                modifier = Modifier.size(13.dp),
                                tint = platformColor
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onOpenRosterClick,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = "Buka Roster Anggota",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun MultiRoomTabHeader(
    rooms: List<XmppRoom>,
    selectedRoomJid: String?,
    showOnlySavedRooms: Boolean,
    onToggleSavedOnly: () -> Unit,
    onSelectRoom: (String) -> Unit,
    onAddRoomClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Saved / Bookmarked filter toggle
            IconButton(
                onClick = onToggleSavedOnly,
                modifier = Modifier
                    .size(28.dp)
                    .testTag("btn_filter_saved_rooms")
            ) {
                Icon(
                    imageVector = if (showOnlySavedRooms) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "Filter Room Tersimpan",
                    tint = if (showOnlySavedRooms) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (rooms.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "Memuat daftar room...",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(rooms, key = { it.jid }) { room ->
                        val isSelected = room.jid == selectedRoomJid
                        val tabColor = try {
                            Color(android.graphics.Color.parseColor(room.colorHex))
                        } catch (e: Exception) {
                            MaterialTheme.colorScheme.primary
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) tabColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 1.2.dp else 0.5.dp,
                                color = if (isSelected) tabColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectRoom(room.jid) }
                                .testTag("tab_room_${room.jid}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Channel dot
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(tabColor)
                                )

                                Spacer(modifier = Modifier.width(4.dp))

                                Text(
                                    text = room.name,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )

                                if (room.isBookmarked) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(
                                        imageVector = Icons.Default.Bookmark,
                                        contentDescription = "Tersimpan",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }

                                if (room.unreadCount > 0) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        Text(
                                            text = if (room.unreadCount > 99) "99+" else "${room.unreadCount}",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Quick add room button
            IconButton(
                onClick = onAddRoomClick,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .testTag("btn_quick_add_room")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Tambah Ruangan",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun CompactNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    badgeCount: Int = 0,
    testTag: String
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    val color = if (selected) activeColor else inactiveColor

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (badgeCount > 0) {
            BadgedBox(
                badge = {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ) {
                        Text(
                            text = if (badgeCount > 99) "99+" else "$badgeCount",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(19.dp)
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(19.dp)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = color,
            maxLines = 1
        )
    }
}

@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onAttachClick: () -> Unit,
    onCallClick: (String) -> Unit,
    whisperTarget: RoomOccupant?,
    replyingToMessage: XmppMessage? = null,
    onCancelReply: () -> Unit = {}
) {
    var showEmojiKeyboard by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column {
            // Reply Preview Header
            if (replyingToMessage != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Reply,
                                contentDescription = "Membalas",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Membalas @${replyingToMessage.senderNickname}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = replyingToMessage.body,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = onCancelReply,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Batal Balas",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(
                    onClick = onAttachClick,
                    modifier = Modifier.align(Alignment.Bottom)
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Lampirkan File",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = { showEmojiKeyboard = !showEmojiKeyboard },
                    modifier = Modifier
                        .align(Alignment.Bottom)
                        .testTag("btn_toggle_emoji")
                ) {
                    Icon(
                        imageVector = Icons.Default.SentimentSatisfied,
                        contentDescription = "Pilih Emoji",
                        tint = if (showEmojiKeyboard) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp, max = 180.dp)
                        .testTag("chat_input_field"),
                    placeholder = {
                        Text(
                            text = if (whisperTarget != null) "Bisikkan ke @${whisperTarget.nickname}..." else "Ketik pesan MUC...",
                            fontSize = 13.sp
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                    minLines = 1,
                    maxLines = 8
                )

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSendMessage(inputText)
                            showEmojiKeyboard = false
                        }
                    },
                    enabled = inputText.isNotBlank(),
                    modifier = Modifier
                        .size(46.dp)
                        .align(Alignment.Bottom)
                        .clip(CircleShape)
                        .background(
                            if (inputText.isNotBlank()) {
                                if (whisperTarget != null) Color(0xFF7C3AED) else MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .testTag("btn_send_message")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Kirim Pesan",
                        tint = if (inputText.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(visible = showEmojiKeyboard) {
                com.ndregil.inchat.ui.components.EmojiKeyboard(
                    onEmojiSelected = { emoji ->
                        onInputChange(inputText + emoji)
                    }
                )
            }
        }
    }
}
