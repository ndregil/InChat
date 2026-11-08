package com.ndregil.inchat.ui.screens

import kotlinx.coroutines.launch
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState

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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ndregil.inchat.data.model.XmppRoom
import com.ndregil.inchat.data.model.RosterContact
import com.ndregil.inchat.data.model.UserPresence
import com.ndregil.inchat.data.model.XmppAccount
import com.ndregil.inchat.ui.XmppViewModel
import com.ndregil.inchat.ui.components.ClientCapsBadge
import com.ndregil.inchat.ui.components.ClientCapsDialog
import com.ndregil.inchat.ui.components.ClientCapsIcon
import com.ndregil.inchat.ui.theme.AwayAmber
import com.ndregil.inchat.ui.theme.DndRed
import com.ndregil.inchat.ui.theme.OfflineGray
import com.ndregil.inchat.ui.theme.OnlineGreen
import com.ndregil.inchat.data.model.ThemePreferences
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RosterContactsScreen(
    viewModel: XmppViewModel,
    onStartDirectChat: ((jid: String, name: String) -> Unit)? = null,
    onRoomClick: ((String) -> Unit)? = null,
    themePrefs: ThemePreferences = ThemePreferences(),
    modifier: Modifier = Modifier
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0) { 3 }
    val selectedSubTab = pagerState.currentPage
    val contacts by viewModel.rosterContacts.collectAsStateWithLifecycle()
    val isRosterLoading by viewModel.isRosterLoading.collectAsStateWithLifecycle()
    val currentAccount by viewModel.currentAccount.collectAsStateWithLifecycle()
    val myPresence by viewModel.myPresence.collectAsStateWithLifecycle()
    val myStatusMessage by viewModel.myStatusMessage.collectAsStateWithLifecycle()
    val allRooms by viewModel.allRooms.collectAsStateWithLifecycle()
    val savedRooms by viewModel.savedRooms.collectAsStateWithLifecycle()
    val isSyncingBookmarks by viewModel.isSyncingBookmarks.collectAsStateWithLifecycle()
    val stories by viewModel.stories.collectAsStateWithLifecycle()
    val isPublishingStory by viewModel.isPublishingStory.collectAsStateWithLifecycle()
    val myAvatar by viewModel.myAvatar.collectAsStateWithLifecycle()

    var selectedFileTargetJid by remember { mutableStateOf<String?>(null) }
    
    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            // In a real app, we'd need more info, but for this demo:
            viewModel.sendFileToContact(selectedFileTargetJid ?: return@let, it)
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, ONLINE, OFFLINE
    var showAddDialog by remember { mutableStateOf(false) }
    var contactToRename by remember { mutableStateOf<RosterContact?>(null) }
    var contactToDelete by remember { mutableStateOf<RosterContact?>(null) }
    var selectedCapsContact by remember { mutableStateOf<RosterContact?>(null) }

    // Filter contacts based on search and granular presence status
    val filteredContacts = remember(contacts, searchQuery, selectedFilter) {
        contacts.filter { contact ->
            val matchesQuery = searchQuery.isBlank() ||
                contact.name.contains(searchQuery, ignoreCase = true) ||
                contact.jid.contains(searchQuery, ignoreCase = true) ||
                contact.groups.any { it.contains(searchQuery, ignoreCase = true) }

            val matchesFilter = when (selectedFilter) {
                "ONLINE" -> contact.presence == UserPresence.ONLINE || contact.presence == UserPresence.CHAT
                "AWAY" -> contact.presence == UserPresence.AWAY || contact.presence == UserPresence.XA
                "DND" -> contact.presence == UserPresence.DND
                "OFFLINE" -> contact.presence == UserPresence.OFFLINE
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = when (selectedSubTab) {
                                0 -> "Stories"
                                1 -> "Kontak"
                                else -> "Grup Chat"
                            },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    actions = {
                        when (selectedSubTab) {
                            0 -> {
                                // Stories tab actions
                                if (isPublishingStory) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .padding(end = 12.dp)
                                            .size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            1 -> {
                                IconButton(
                                    onClick = { viewModel.refreshRoster() },
                                    modifier = Modifier.testTag("refresh_roster_button")
                                ) {
                                    if (isRosterLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Muat Ulang Roster")
                                    }
                                }
                            }
                            2 -> {
                                IconButton(
                                    onClick = { viewModel.syncBookmarks() },
                                    enabled = !isSyncingBookmarks,
                                    modifier = Modifier.testTag("btn_sync_bookmarks_roster")
                                ) {
                                    if (isSyncingBookmarks) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Sinkronkan Bookmark")
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                androidx.compose.material3.TabRow(
                    selectedTabIndex = selectedSubTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {}
                ) {
                    androidx.compose.material3.Tab(
                        selected = selectedSubTab == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        text = {
                            val unseenCount = remember(stories, currentAccount.jid) {
                                stories.filter { !it.isMine && !it.senderJid.equals(currentAccount.jid, ignoreCase = true) && !it.isViewed }
                                    .map { it.senderJid }
                                    .distinct()
                                    .size
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Stories")
                                if (unseenCount > 0) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    )
                    androidx.compose.material3.Tab(
                        selected = selectedSubTab == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text("Kontak") }
                    )
                    androidx.compose.material3.Tab(
                        selected = selectedSubTab == 2,
                        onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                        text = { Text("Grup Chat") }
                    )
                }
            }
        },
        floatingActionButton = {
            when (selectedSubTab) {
                0 -> {
                    // Stories tab handles creation directly inside content card
                }
                1 -> {
                    FloatingActionButton(
                        onClick = { showAddDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("add_contact_fab")
                    ) {
                        Icon(imageVector = Icons.Default.PersonAdd, contentDescription = "Tambah Kontak")
                    }
                }
                2 -> {
                    FloatingActionButton(
                        onClick = { viewModel.isJoinCreateDialogOpen.value = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("add_room_fab_roster")
                    ) {
                        Icon(imageVector = Icons.Default.GroupAdd, contentDescription = "Gabung/Buat Room")
                    }
                }
            }
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            when (page) {
                0 -> {
                    // Stories / Status Tab Content (WhatsApp-style)
                    StoriesTabContent(
                        stories = stories,
                        contacts = contacts,
                        myAvatar = myAvatar,
                        myNickname = currentAccount.nickname.ifBlank { currentAccount.jid.substringBefore("@") },
                        myJid = currentAccount.jid,
                        onPublishStory = { text, mediaUri, bgColor ->
                            viewModel.publishStory(text, mediaUri, bgColor)
                        },
                        onDeleteStory = { storyId ->
                            viewModel.deleteStory(storyId)
                        },
                        onMarkViewed = { storyId ->
                            viewModel.markStoryViewed(storyId)
                        },
                        onStartDirectChat = onStartDirectChat,
                        isPublishing = isPublishingStory,
                        paddingValues = paddingValues
                    )
                }
                1 -> {
                    // Kontak Tab Content
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = 16.dp)
                    ) {
                        val avatarPickerLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.GetContent()
                        ) { uri: android.net.Uri? ->
                            uri?.let { viewModel.updateAvatar(it) }
                        }

                        // User Presence Status & Broadcast Card
                        UserPresenceControlCard(
                            currentPresence = myPresence,
                            statusMessage = myStatusMessage,
                            currentAccount = currentAccount,
                            contactsCount = contacts.size,
                            roomsCount = allRooms.size,
                            myAvatar = myAvatar,
                            onUpdateAvatarClick = { avatarPickerLauncher.launch("image/*") },
                            onPresenceChange = { newPresence, newStatusMsg ->
                                viewModel.updateMyPresence(newPresence, newStatusMsg)
                            },
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )

                        // Search Input
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Cari berdasarkan nama, JID, atau grup...") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Hapus teks")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .testTag("roster_search_input")
                        )

                        // Granular Presence Filter Chips
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedFilter == "ALL",
                                    onClick = { selectedFilter = "ALL" },
                                    label = { Text("Semua (${contacts.size})") },
                                    modifier = Modifier.testTag("filter_all_contacts")
                                )
                            }
                            item {
                                FilterChip(
                                    selected = selectedFilter == "ONLINE",
                                    onClick = { selectedFilter = "ONLINE" },
                                    label = { Text("Online (${contacts.count { it.presence == UserPresence.ONLINE || it.presence == UserPresence.CHAT }})") },
                                    modifier = Modifier.testTag("filter_online_contacts")
                                )
                            }
                            item {
                                FilterChip(
                                    selected = selectedFilter == "AWAY",
                                    onClick = { selectedFilter = "AWAY" },
                                    label = { Text("Away (${contacts.count { it.presence == UserPresence.AWAY || it.presence == UserPresence.XA }})") },
                                    modifier = Modifier.testTag("filter_away_contacts")
                                )
                            }
                            item {
                                FilterChip(
                                    selected = selectedFilter == "DND",
                                    onClick = { selectedFilter = "DND" },
                                    label = { Text("DND (${contacts.count { it.presence == UserPresence.DND }})") },
                                    modifier = Modifier.testTag("filter_dnd_contacts")
                                )
                            }
                            item {
                                FilterChip(
                                    selected = selectedFilter == "OFFLINE",
                                    onClick = { selectedFilter = "OFFLINE" },
                                    label = { Text("Offline (${contacts.count { it.presence == UserPresence.OFFLINE }})") },
                                    modifier = Modifier.testTag("filter_offline_contacts")
                                )
                            }
                        }

                        // Contact List or Empty State
                        if (filteredContacts.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(56.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = if (searchQuery.isNotBlank()) "Kontak tidak ditemukan" else "Belum ada kontak di Roster",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (searchQuery.isNotBlank()) "Coba kata kunci lain" else "Tekan tombol + untuk menambahkan kontak baru menggunakan bare JID.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f)
                                    .testTag("roster_contact_list")
                            ) {
                                items(filteredContacts, key = { it.jid }) { contact ->
                                    RosterContactListItem(
                                        contact = contact,
                                        onChatClick = {
                                            if (onStartDirectChat != null) {
                                                onStartDirectChat(contact.jid, contact.displayName)
                                            } else {
                                                viewModel.startDirectChat(contact.jid, contact.displayName)
                                            }
                                        },
                                        onRenameClick = { contactToRename = contact },
                                        onDeleteClick = { contactToDelete = contact },
                                        onCapsClick = { selectedCapsContact = contact },
                                        onCallClick = { viewModel.initiate1on1Call(contact.jid, contact.displayName, "Voice") },
                                        onVideoCallClick = { viewModel.initiate1on1Call(contact.jid, contact.displayName, "Video") },
                                        onSendFileClick = {
                                            selectedFileTargetJid = contact.jid
                                            filePickerLauncher.launch("*/*")
                                        },
                                        themePrefs = themePrefs
                                    )
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Grup Chat (Room Favorit) Tab Content
                    SavedRoomsInternalContent(
                        savedRooms = savedRooms,
                        onSelectRoom = { jid -> onRoomClick?.invoke(jid) },
                        onRemoveBookmark = { jid -> viewModel.removeSavedRoom(jid) },
                        onAddRoomClick = { viewModel.isJoinCreateDialogOpen.value = true },
                        onSyncBookmarks = { viewModel.syncBookmarks() },
                        isSyncing = isSyncingBookmarks,
                        getRoomAvatar = { jid -> viewModel.repository.getRoomAvatar(jid) },
                        onUpdateRoomColor = { jid, color -> viewModel.updateRoomColor(jid, color) },
                        onToggleAutoJoin = { jid, autoJoin -> viewModel.updateRoomAutoJoin(jid, autoJoin) },
                        themePrefs = themePrefs,
                        paddingValues = paddingValues
                    )
                }
            }
        }
    }

    // Client Caps Inspector Dialog
    selectedCapsContact?.let { contact ->
        ClientCapsDialog(
            clientCaps = contact.clientCaps,
            userName = "${contact.displayName} (${contact.jid})",
            targetJid = contact.jid,
            onQuerySoftwareVersion = { jid, callback -> viewModel.querySoftwareVersion(jid, callback) },
            onDismiss = { selectedCapsContact = null }
        )
    }

    // Add Contact Dialog
    if (showAddDialog) {
        AddContactDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { jid, name, group ->
                viewModel.addRosterContact(jid, name, group)
                showAddDialog = false
            }
        )
    }

    // Rename Contact Dialog
    contactToRename?.let { contact ->
        RenameContactDialog(
            currentContact = contact,
            onDismiss = { contactToRename = null },
            onConfirm = { newName ->
                viewModel.renameRosterContact(contact.jid, newName)
                contactToRename = null
            }
        )
    }

    // Delete Confirmation Dialog
    contactToDelete?.let { contact ->
        AlertDialog(
            onDismissRequest = { contactToDelete = null },
            title = { Text("Hapus Kontak Roster?") },
            text = { Text("Apakah Anda yakin ingin menghapus '${contact.displayName}' (${contact.jid}) dari daftar kontak XMPP?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeRosterContact(contact.jid)
                        contactToDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_contact_button")
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { contactToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun RosterContactListItem(
    contact: RosterContact,
    onChatClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCapsClick: () -> Unit = {},
    onCallClick: () -> Unit,
    onVideoCallClick: () -> Unit,
    onSendFileClick: () -> Unit,
    themePrefs: ThemePreferences = ThemePreferences()
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val presenceColor = if (themePrefs.isCustomThemeEnabled) {
        try {
            when (contact.presence) {
                UserPresence.ONLINE -> Color(android.graphics.Color.parseColor(themePrefs.presenceOnlineColorHex))
                UserPresence.CHAT -> Color(android.graphics.Color.parseColor(themePrefs.presenceChatColorHex))
                UserPresence.AWAY -> Color(android.graphics.Color.parseColor(themePrefs.presenceAwayColorHex))
                UserPresence.XA -> Color(android.graphics.Color.parseColor(themePrefs.presenceXaColorHex))
                UserPresence.DND -> Color(android.graphics.Color.parseColor(themePrefs.presenceDndColorHex))
                UserPresence.OFFLINE -> OfflineGray
            }
        } catch (e: Exception) {
            when (contact.presence) {
                UserPresence.ONLINE -> OnlineGreen
                UserPresence.CHAT -> Color(0xFF0D9488)
                UserPresence.AWAY -> AwayAmber
                UserPresence.XA -> Color(0xFFD97706)
                UserPresence.DND -> DndRed
                UserPresence.OFFLINE -> OfflineGray
            }
        }
    } else {
        when (contact.presence) {
            UserPresence.ONLINE -> OnlineGreen
            UserPresence.CHAT -> Color(0xFF0D9488)
            UserPresence.AWAY -> AwayAmber
            UserPresence.XA -> Color(0xFFD97706)
            UserPresence.DND -> DndRed
            UserPresence.OFFLINE -> OfflineGray
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChatClick() }
            .testTag("contact_item_${contact.jid}"),
        color = Color.Transparent
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // User Avatar with Presence Dot
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (contact.avatar != null) {
                            coil.compose.AsyncImage(
                                model = contact.avatar,
                                contentDescription = "Avatar ${contact.displayName}",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = contact.displayName.take(2).uppercase(Locale.getDefault()),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 16.sp
                            )
                        }
                    }

                    // Presence Colored Dot
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(presenceColor)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Text Details Column
                Column(modifier = Modifier.weight(1f)) {
                    // Line 1: Display Name + Status Category Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Text(
                                text = contact.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (themePrefs.isCustomThemeEnabled) {
                                    try { Color(android.graphics.Color.parseColor(themePrefs.contactColorHex)) } catch (e: Exception) { Color.Unspecified }
                                } else {
                                    Color.Unspecified
                                }
                            )
                            if (!contact.nickname.isNullOrBlank() && contact.nickname != contact.name) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = "Nick",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = presenceColor.copy(alpha = 0.12f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(presenceColor)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = contact.statusCategoryLabel,
                                    color = presenceColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Line 2: Bare JID + Client Caps Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = contact.jid,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        ClientCapsBadge(
                            clientCaps = contact.clientCaps,
                            onClick = onCapsClick
                        )
                    }

                    // Line 3: Status Message and PEP Chips (Tune, Activity, Mood)
                    if (contact.hasPepEvent || contact.statusMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (contact.statusMessage.isNotBlank()) {
                                Text(
                                    text = "\"${contact.statusMessage}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }

                            contact.pepTune?.let { tune ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = "🎵 $tune",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            contact.pepActivity?.let { activity ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = "💼 $activity",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            contact.pepMood?.let { mood ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = "🎯 $mood",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Line End Actions: Chat & More Options Menu
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onChatClick,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("chat_contact_${contact.jid}")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Kirim Pesan",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("contact_options_${contact.jid}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Opsi kontak",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Kirim Pesan (1-on-1)") },
                                leadingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onChatClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Panggil Suara") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Call,
                                        contentDescription = null,
                                        tint = if (contact.presence != UserPresence.OFFLINE) OnlineGreen else Color.Gray
                                    )
                                },
                                enabled = contact.presence != UserPresence.OFFLINE,
                                onClick = {
                                    menuExpanded = false
                                    onCallClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Panggil Video") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = if (contact.presence != UserPresence.OFFLINE) OnlineGreen else Color.Gray
                                    )
                                },
                                enabled = contact.presence != UserPresence.OFFLINE,
                                onClick = {
                                    menuExpanded = false
                                    onVideoCallClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Kirim File") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                enabled = contact.presence != UserPresence.OFFLINE,
                                onClick = {
                                    menuExpanded = false
                                    onSendFileClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Lihat Caps & Klien (XEP-0115)") },
                                leadingIcon = {
                                    ClientCapsIcon(
                                        platform = contact.clientCaps.platform,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onCapsClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Ubah Nama") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onRenameClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Hapus Kontak") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteClick()
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(start = 58.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        }
    }
}

@Composable
fun AddContactDialog(
    onDismiss: () -> Unit,
    onConfirm: (jid: String, name: String, group: String?) -> Unit
) {
    var jidInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var groupInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Kontak XMPP Baru") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Masukkan bare JID kontak (misal: user@xmpp.chat.org) untuk menambahkan ke Roster Smack.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = jidInput,
                    onValueChange = {
                        jidInput = it
                        errorMessage = null
                    },
                    label = { Text("Bare JID *") },
                    placeholder = { Text("teman@xmpp.org") },
                    singleLine = true,
                    isError = errorMessage != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_contact_jid_input")
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Nama Kontak (Opsional)") },
                    placeholder = { Text("Nama panggilan") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_contact_name_input")
                )

                OutlinedTextField(
                    value = groupInput,
                    onValueChange = { groupInput = it },
                    label = { Text("Grup (Opsional)") },
                    placeholder = { Text("misal: Teman, Kerja") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_contact_group_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanJid = jidInput.trim()
                    if (cleanJid.isBlank() || !cleanJid.contains("@")) {
                        errorMessage = "Format JID tidak valid. Harus menyertakan username@domain"
                    } else {
                        onConfirm(cleanJid, nameInput.trim(), groupInput.trim().ifBlank { null })
                    }
                },
                modifier = Modifier.testTag("submit_add_contact_button")
            ) {
                Text("Tambah Kontak")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun RenameContactDialog(
    currentContact: RosterContact,
    onDismiss: () -> Unit,
    onConfirm: (newName: String) -> Unit
) {
    var newNameInput by remember { mutableStateOf(currentContact.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ubah Nama Kontak") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "JID: ${currentContact.jid}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = newNameInput,
                    onValueChange = { newNameInput = it },
                    label = { Text("Nama Tampilan Baru") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_contact_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newNameInput.isNotBlank()) {
                        onConfirm(newNameInput.trim())
                    }
                },
                modifier = Modifier.testTag("confirm_rename_contact_button")
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPresenceControlCard(
    currentPresence: UserPresence,
    statusMessage: String,
    currentAccount: XmppAccount,
    contactsCount: Int,
    roomsCount: Int,
    myAvatar: ByteArray? = null,
    onUpdateAvatarClick: (() -> Unit)? = null,
    onPresenceChange: (UserPresence, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var customMessageInput by remember(statusMessage) { mutableStateOf(statusMessage) }
    var updateFeedback by remember { mutableStateOf<String?>(null) }

    val presenceColor = when (currentPresence) {
        UserPresence.ONLINE -> OnlineGreen
        UserPresence.CHAT -> Color(0xFF0D9488)
        UserPresence.AWAY -> AwayAmber
        UserPresence.XA -> Color(0xFFD97706)
        UserPresence.DND -> DndRed
        UserPresence.OFFLINE -> OfflineGray
    }

    val presenceTitle = when (currentPresence) {
        UserPresence.ONLINE -> "Online"
        UserPresence.CHAT -> "Chat"
        UserPresence.AWAY -> "Away"
        UserPresence.XA -> "Extended Away"
        UserPresence.DND -> "Do Not Disturb"
        UserPresence.OFFLINE -> "Offline"
    }

    val presenceOptions = listOf(
        Triple(UserPresence.ONLINE, "Online", OnlineGreen),
        Triple(UserPresence.CHAT, "Chat", Color(0xFF0D9488)),
        Triple(UserPresence.AWAY, "Away", AwayAmber),
        Triple(UserPresence.XA, "Ext Away", Color(0xFFD97706)),
        Triple(UserPresence.DND, "DND", DndRed),
        Triple(UserPresence.OFFLINE, "Offline", OfflineGray)
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("user_presence_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            presenceColor.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Avatar, Name, Presence Badge, Expand Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // User Avatar with Presence dot & Photo Camera Edit Badge
                Box(
                    contentAlignment = Alignment.BottomEnd,
                    modifier = Modifier.clickable { onUpdateAvatarClick?.invoke() }
                ) {
                    val decodedBitmap = remember(myAvatar) {
                        if (myAvatar != null && myAvatar.isNotEmpty()) {
                            try {
                                BitmapFactory.decodeByteArray(myAvatar, 0, myAvatar.size)
                            } catch (e: Exception) {
                                null
                            }
                        } else null
                    }

                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (decodedBitmap != null) {
                            Image(
                                bitmap = decodedBitmap.asImageBitmap(),
                                contentDescription = "Foto Profil Saya",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = currentAccount.nickname.take(2).uppercase(Locale.getDefault()),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 18.sp
                            )
                        }
                    }

                    // Camera edit icon badge
                    Surface(
                        modifier = Modifier.size(18.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = "Ganti Foto Profil",
                                modifier = Modifier.size(10.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = currentAccount.nickname,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = presenceColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = presenceTitle,
                                color = presenceColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = if (statusMessage.isNotBlank()) "\"$statusMessage\"" else "Tidak ada pesan status",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "📡 Disiarkan ke $contactsCount kontak & $roomsCount ruang MUC",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.testTag("toggle_presence_edit_btn")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Buka pengaturan status",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Presence Option Selector Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("presence_selector_row"),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(presenceOptions) { (presence, label, color) ->
                    val isSelected = currentPresence == presence
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onPresenceChange(presence, customMessageInput)
                            updateFeedback = "Status disiarkan: $label"
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("presence_select_${label.lowercase(Locale.getDefault())}")
                    )
                }
            }

            // Expandable Custom Status Message Input & Quick Presets
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    OutlinedButton(
                        onClick = { onUpdateAvatarClick?.invoke() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ganti Foto Profil XMPP",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "Pesan Status Pribadi XMPP:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = customMessageInput,
                        onValueChange = { customMessageInput = it },
                        placeholder = { Text("Tulis pesan status Anda...", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    onPresenceChange(currentPresence, customMessageInput)
                                    updateFeedback = "Status pesan berhasil diperbarui & disiarkan!"
                                },
                                modifier = Modifier.testTag("apply_presence_status_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Terapkan status",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_status_message_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Preset Chips
                    Text(
                        text = "Pilih Preset Cepat:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val presets = listOf(
                        "Tersedia untuk obrolan 👋",
                        "Sedang fokus coding 💻",
                        "Sedang meeting 📞",
                        "Makan siang 🥪",
                        "AFK sebentar ⏳",
                        "Jangan diganggu ⛔"
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(presets) { preset ->
                            FilterChip(
                                selected = customMessageInput == preset,
                                onClick = {
                                    customMessageInput = preset
                                    onPresenceChange(currentPresence, preset)
                                    updateFeedback = "Status disiarkan: \"$preset\""
                                },
                                label = { Text(preset, fontSize = 10.sp) },
                                modifier = Modifier.testTag("preset_${preset.take(8).trim()}")
                            )
                        }
                    }
                }
            }

            // Brief Feedback Message
            updateFeedback?.let { feedback ->
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = OnlineGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = feedback,
                        fontSize = 11.sp,
                        color = OnlineGreen,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}


@Composable
fun SavedRoomsInternalContent(
    savedRooms: List<XmppRoom>,
    onSelectRoom: (String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onAddRoomClick: () -> Unit,
    onSyncBookmarks: (() -> Unit)? = null,
    isSyncing: Boolean = false,
    getRoomAvatar: ((String) -> ByteArray?)? = null,
    onUpdateRoomColor: ((String, String) -> Unit)? = null,
    onToggleAutoJoin: ((String, Boolean) -> Unit)? = null,
    themePrefs: ThemePreferences = ThemePreferences(),
    paddingValues: PaddingValues = PaddingValues(0.dp)
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

    // Color picker dialog
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
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
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

    Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("search_saved_rooms_roster"),
            placeholder = { Text("Cari favorit...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Bersihkan")
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
                        imageVector = if (searchQuery.isEmpty()) Icons.Default.Bookmark else Icons.Default.SearchOff,
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
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp, top = 8.dp)
            ) {
                items(filteredRooms, key = { it.jid }) { room ->
                    val avatarBytes = getRoomAvatar?.invoke(room.jid)
                    SavedRoomItem(
                        room = room,
                        roomAvatar = avatarBytes,
                        onClick = { onSelectRoom(room.jid) },
                        onRemove = { roomToDelete = room },
                        onChangeColorClick = { roomToColorPick = room },
                        onToggleAutoJoin = onToggleAutoJoin?.let { fn -> { fn(room.jid, !room.isAutoJoin) } },
                        themePrefs = themePrefs
                    )
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
    onToggleAutoJoin: (() -> Unit)? = null,
    themePrefs: ThemePreferences = ThemePreferences()
) {
    val roomColor = try {
        Color(android.graphics.Color.parseColor(room.colorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

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
            .testTag("saved_room_${room.jid}")
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, roomColor.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Room Avatar with accent glow / border
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(roomColor.copy(alpha = 0.16f))
                        .border(2.dp, roomColor, CircleShape)
                        .clickable { onChangeColorClick() },
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Avatar $cleanTitle",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
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

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = cleanTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                            color = if (themePrefs.isCustomThemeEnabled) {
                                try { Color(android.graphics.Color.parseColor(themePrefs.contactColorHex)) } catch (e: Exception) { MaterialTheme.colorScheme.onSurface }
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        // Group chat badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = roomColor.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "MUC",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = roomColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = room.jid,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (room.topic.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = room.topic,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Hapus Bookmark",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Auto-Join Pill Button
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (room.isAutoJoin) roomColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (room.isAutoJoin) roomColor.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.clickable { onToggleAutoJoin?.invoke() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (room.isAutoJoin) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (room.isAutoJoin) roomColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (room.isAutoJoin) "Auto-Join Aktif" else "Auto-Join Nonaktif",
                            fontSize = 12.sp,
                            fontWeight = if (room.isAutoJoin) FontWeight.Bold else FontWeight.Medium,
                            color = if (room.isAutoJoin) roomColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Change Theme Color Button
                OutlinedButton(
                    onClick = onChangeColorClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, roomColor.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = roomColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Warna Room",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

