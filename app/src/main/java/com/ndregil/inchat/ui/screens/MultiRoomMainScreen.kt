package com.ndregil.inchat.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Badge
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.scale
import androidx.compose.material3.Switch
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import com.ndregil.inchat.util.VoiceRecorderManager
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalContext
import com.ndregil.inchat.data.model.OccupantRole
import com.ndregil.inchat.data.model.OccupantAffiliation
import com.ndregil.inchat.data.model.XmppMessage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge as M3Badge
import androidx.compose.material3.HorizontalDivider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
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
    initialNavIndex: Int = 0,
    initialAccountTab: Int = 1,
    modifier: Modifier = Modifier
) {
    var activeNavIndex by remember { mutableIntStateOf(initialNavIndex) } // 0 = Rooms Chat, 1 = Direct Chat, 2 = Contacts, 3 = Directory, 4 = Settings
    var isDirectConversationOpen by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showRoomMenu by remember { mutableStateOf(false) }
    var selectedCapsOccupant by remember { mutableStateOf<RoomOccupant?>(null) }
    var showAccountSettingsDirectly by remember { mutableStateOf(initialNavIndex == 4) }
    var initialAccountTabSet by remember { mutableIntStateOf(initialAccountTab) }
    var showBookmarksScreen by remember { mutableStateOf(false) }
    var showCustomThemeScreen by remember { mutableStateOf(false) }
    var isConfigNicknameOnly by remember { mutableStateOf(false) }
    var roomConfigInfo by remember { mutableStateOf<org.jivesoftware.smackx.muc.RoomInfo?>(null) }

    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val connectionError by viewModel.connectionError.collectAsStateWithLifecycle()
    val currentAccount by viewModel.currentAccount.collectAsStateWithLifecycle()
    val registrationState by viewModel.registrationState.collectAsStateWithLifecycle()
    val allRooms by viewModel.allRooms.collectAsStateWithLifecycle()
    val filteredRooms by viewModel.filteredRooms.collectAsStateWithLifecycle()
    val isDarkModeOverride by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDarkMode = isDarkModeOverride ?: systemDark

    val savedRooms by viewModel.savedRooms.collectAsStateWithLifecycle()
    val themePrefs by viewModel.themePreferences.collectAsStateWithLifecycle()
    var showOnlySavedRooms by remember { mutableStateOf(false) }
    val activeRoom by viewModel.activeRoom.collectAsStateWithLifecycle()
    val messages by viewModel.activeRoomMessages.collectAsStateWithLifecycle()
    val occupants by viewModel.activeRoomOccupants.collectAsStateWithLifecycle()
    val stanzaLogs by viewModel.stanzaLogs.collectAsStateWithLifecycle()
    val directoryRooms by viewModel.directoryRooms.collectAsStateWithLifecycle()
    val isDirectoryLoading by viewModel.isDirectoryLoading.collectAsStateWithLifecycle()
    val whisperTarget by viewModel.whisperTargetOccupant.collectAsStateWithLifecycle()
    val replyingToMessage by viewModel.replyingToMessage.collectAsStateWithLifecycle()
    val moderationHistory by viewModel.activeRoomModerationHistory.collectAsStateWithLifecycle()
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
    val isLoadingOlder by viewModel.isLoadingOlderMessages.collectAsStateWithLifecycle()
    val translationPrefs by viewModel.translationPreferences.collectAsStateWithLifecycle()
    var showTopicDetailDialog by remember { mutableStateOf(false) }
    var editTopicText by remember(activeRoom?.topic) { mutableStateOf(activeRoom?.topic ?: "") }

    val displayedRooms = if (showOnlySavedRooms) savedRooms else filteredRooms
    val activeWindows = displayedRooms.filter { it.isJoined }
    var selectedOccupantForMenu by remember { mutableStateOf<RoomOccupant?>(null) }
    var selectedProfileOccupant by remember { mutableStateOf<Pair<RoomOccupant, org.jivesoftware.smackx.vcardtemp.packet.VCard?>?>(null) }
    var isLoadingProfile by remember { mutableStateOf(false) }
    val roomPagerState = rememberPagerState(initialPage = 0) { activeWindows.size + 1 }


    val rosterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val stanzaSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val listState = remember(activeRoom?.jid) { androidx.compose.foundation.lazy.LazyListState() }
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

    val roomAvatarPicker = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            activeRoom?.let { room ->
                viewModel.updateRoomAvatar(room.jid, room.name, it)
            }
        }
    }

    // Auto scroll to latest message when active room changes or new messages arrive
    val lastMsgId = remember(messages) { messages.lastOrNull()?.id }
    val isLastMsgMine = remember(messages) { messages.lastOrNull()?.isMine == true }

    // Scroll to bottom when room changes
    LaunchedEffect(activeRoom?.jid) {
        if (messages.isNotEmpty()) {
            val targetIndex = messages.size
            try {
                listState.scrollToItem(targetIndex)
            } catch (_: Exception) {}
        }
    }

    // Auto scroll when user sends a message or when already near bottom (only if user is NOT currently touch-scrolling)
    LaunchedEffect(messages.size, lastMsgId) {
        if (messages.isNotEmpty() && !listState.isScrollInProgress) {
            val targetIndex = messages.size
            val isNearBottom = listState.firstVisibleItemIndex >= (messages.size - 4).coerceAtLeast(0)
            if (isLastMsgMine || isNearBottom) {
                try {
                    listState.animateScrollToItem(targetIndex)
                } catch (_: Exception) {
                    listState.scrollToItem(targetIndex)
                }
            }
        }
    }

    // Reset whisper target when active room changes
    LaunchedEffect(activeRoom?.jid) {
        viewModel.setWhisperTarget(null)
    }

    // Automatically maintain continuous whisper session when incoming whisper arrives
    LaunchedEffect(messages.lastOrNull()?.id) {
        val lastMsg = messages.lastOrNull()
        if (lastMsg != null && lastMsg.messageType == com.ndregil.inchat.data.model.MessageType.WHISPER && !lastMsg.isMine) {
            val senderNick = lastMsg.senderNickname
            if (senderNick.isNotBlank()) {
                val found = occupants.find { it.nickname == senderNick }
                    ?: com.ndregil.inchat.data.model.RoomOccupant(nickname = senderNick, jid = lastMsg.senderJid)
                viewModel.setWhisperTarget(found)
            }
        }
    }

    // Keep active room in sync with FcmPushManager to suppress redundant notifications
    LaunchedEffect(activeNavIndex, activeRoom?.jid) {
        FcmPushManager.activeRoomJid = if (activeNavIndex == 0) activeRoom?.jid else null
    }

    LaunchedEffect(activeRoom?.jid, activeRoom?.isGroupChat) {
        if (activeRoom != null && !activeRoom!!.isGroupChat) {
            isDirectConversationOpen = true
        }
    }

    // Show Toast for profile avatar update status
    val avatarUpdateStatus by viewModel.avatarUpdateStatus.collectAsStateWithLifecycle()
    LaunchedEffect(avatarUpdateStatus) {
        val status = avatarUpdateStatus
        if (!status.isNullOrBlank()) {
            android.widget.Toast.makeText(context, status, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearAvatarUpdateStatus()
        }
    }

    // Show Toast for room config status
    val roomConfigStatus by viewModel.roomConfigStatus.collectAsStateWithLifecycle()
    val isRoomConfigLoading by viewModel.isRoomConfigLoading.collectAsStateWithLifecycle()
    LaunchedEffect(roomConfigStatus) {
        val status = roomConfigStatus
        if (!status.isNullOrBlank()) {
            android.widget.Toast.makeText(context, status, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearRoomConfigStatus()
        }
    }

    // Show Toast for automatic MUC room join status upon app startup
    val autoJoinToastStatus by viewModel.autoJoinToastStatus.collectAsStateWithLifecycle()
    LaunchedEffect(autoJoinToastStatus) {
        val status = autoJoinToastStatus
        if (!status.isNullOrBlank()) {
            android.widget.Toast.makeText(context, status, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearAutoJoinToastStatus()
        }
    }

    val ownersList by viewModel.ownersList.collectAsStateWithLifecycle()
    val adminsList by viewModel.adminsList.collectAsStateWithLifecycle()
    val membersList by viewModel.membersList.collectAsStateWithLifecycle()
    val outcastsList by viewModel.outcastsList.collectAsStateWithLifecycle()
    val isLoadingAffiliations by viewModel.isLoadingAffiliations.collectAsStateWithLifecycle()
    val roomConfigs by viewModel.roomConfigs.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                    val roomUnread = viewModel.groupRooms.collectAsStateWithLifecycle().value.sumOf { it.unreadCount }
                    val directUnread = viewModel.directChats.collectAsStateWithLifecycle().value.sumOf { it.unreadCount }

                    val groupRoomsList by viewModel.groupRooms.collectAsStateWithLifecycle()

                    CompactNavItem(
                        selected = activeNavIndex == 0,
                        onClick = {
                            activeNavIndex = 0
                        },
                        icon = Icons.Default.Forum,
                        label = "Room",
                        badgeCount = roomUnread,
                        testTag = "nav_tab_rooms"
                    )

                    CompactNavItem(
                        selected = activeNavIndex == 1,
                        onClick = {
                            activeNavIndex = 1
                            if (activeRoom?.isGroupChat == true) {
                                isDirectConversationOpen = false
                            }
                        },
                        icon = Icons.Default.Person,
                        label = "Chat",
                        badgeCount = directUnread,
                        testTag = "nav_tab_direct"
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
                        label = "Setelan",
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
                                    .clickable { viewModel.isRosterSheetOpen.value = true }
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
                                
                                if (connectionStatus == ConnectionStatus.ERROR || connectionStatus == ConnectionStatus.DISCONNECTED) {
                                    OutlinedButton(
                                        onClick = { viewModel.reconnect() },
                                        enabled = connectionStatus != ConnectionStatus.CONNECTING && connectionStatus != ConnectionStatus.AUTHENTICATING,
                                        modifier = Modifier.height(28.dp).padding(end = 8.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Hubungkan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(
                                    text = if (activeRoom?.isGroupChat == true) (activeRoom?.name ?: "Ruang Obrolan") else (if (roomPagerState.currentPage == 0) "Ruang Obrolan" else (filteredRooms.firstOrNull()?.name ?: "InChat")),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
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

                            // Quick Bookmark Toggle (XEP-0048)
                            val isCurrentBookmarked = activeRoom?.isBookmarked == true
                            IconButton(
                                onClick = { activeRoom?.let { viewModel.toggleSaveRoom(it.jid) } },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("btn_quick_bookmark")
                            ) {
                                Icon(
                                    imageVector = if (isCurrentBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = if (isCurrentBookmarked) "Hapus dari Favorit" else "Simpan ke Favorit (XEP-0048)",
                                    tint = if (isCurrentBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            // More options
                            Box {
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
                                        text = { Text("Warna Chat & Wallpaper") },
                                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                        onClick = {
                                            showRoomMenu = false
                                            viewModel.isThemeWallpaperDialogOpen.value = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Ganti nickname") },
                                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                                        onClick = {
                                            showRoomMenu = false
                                            isConfigNicknameOnly = true
                                            activeRoom?.let { room ->
                                                coroutineScope.launch {
                                                    val info = viewModel.repository.getRoomInfo(room.jid)
                                                    roomConfigInfo = info.getOrNull()
                                                    viewModel.isRoomConfigOpen.value = true
                                                }
                                            }
                                        }
                                    )
                                    if (activeRoom?.myRole == OccupantRole.ADMIN || activeRoom?.myRole == OccupantRole.OWNER || activeRoom?.myAffiliation == OccupantAffiliation.ADMIN || activeRoom?.myAffiliation == OccupantAffiliation.OWNER) {
                                        DropdownMenuItem(
                                            text = { Text("Konfigurasi & Kelola Room") },
                                            leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                            onClick = {
                                                showRoomMenu = false
                                                activeRoom?.let { room ->
                                                    coroutineScope.launch {
                                                        val info = viewModel.repository.getRoomInfo(room.jid)
                                                        viewModel.roomAdminInfo.value = info.getOrNull()
                                                        viewModel.loadRoomAffiliations(room.jid)
                                                        viewModel.isAdminPanelOpen.value = true
                                                    }
                                                }
                                            }
                                        )
                                    }
                                    if (activeRoom != null) {
                                        if (activeRoom!!.isJoined) {
                                            DropdownMenuItem(
                                                text = { Text("Tinggalkan Room Ini") },
                                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    showRoomMenu = false
                                                    activeRoom?.let { room ->
                                                        viewModel.leaveRoom(room.jid) 
                                                    }
                                                }
                                            )
                                        } else {
                                            DropdownMenuItem(
                                                text = { Text("Gabung ke Room Ini") },
                                                leadingIcon = { Icon(Icons.Default.Login, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                                onClick = {
                                                    showRoomMenu = false
                                                    activeRoom?.let { room ->
                                                        val nick = room.myNickname.ifBlank { currentAccount.nickname.ifBlank { currentAccount.username } }
                                                        viewModel.joinRoom(room.jid, nick, room.password)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                    DropdownMenuItem(
                                         text = { Text("Bersihkan Riwayat Chat") },
                                         leadingIcon = { Icon(Icons.Default.Clear, contentDescription = null) },
                                         onClick = {
                                             showRoomMenu = false
                                             activeRoom?.let { viewModel.clearRoomChatHistory(it.jid) }
                                         }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Hapus Penanda Room") },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showRoomMenu = false
                                            activeRoom?.let { viewModel.deleteRoom(it.jid) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        val isImeOpen = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = paddingValues.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    end = paddingValues.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    top = if (activeNavIndex == 0) paddingValues.calculateTopPadding() else 0.dp,
                    bottom = if (isImeOpen) 0.dp else paddingValues.calculateBottomPadding()
                )
                .imePadding()
        ) {
            when (activeNavIndex) {
                0 -> {
                    // Multi-Room Chat View with dynamic horizontal multi-room switcher tabs
                    // Page 0 = Room List, Page 1..N = Active Rooms
                    
                    LaunchedEffect(activeRoom?.jid, activeWindows.map { it.jid }) {
                        val activeJid = activeRoom?.jid
                        if (activeJid == null) {
                            if (roomPagerState.currentPage != 0) {
                                roomPagerState.scrollToPage(0)
                            }
                            return@LaunchedEffect
                        }
                        
                        val index = activeWindows.indexOfFirst { it.jid == activeJid }
                        if (index >= 0) {
                            val targetPage = index + 1
                            if (roomPagerState.currentPage != targetPage) {
                                roomPagerState.scrollToPage(targetPage)
                            }
                        } else {
                            if (roomPagerState.currentPage != 0) {
                                roomPagerState.scrollToPage(0)
                            }
                        }
                    }

                    if (roomPagerState.currentPage > 0) {
                        BackHandler {
                            coroutineScope.launch { roomPagerState.animateScrollToPage(0) }
                        }
                    }

                        HorizontalPager(
                            state = roomPagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = false
                        ) { page ->
                            if (page == 0) {
                                // Page 0: Active Group Chat List (Daftar Ruangan Aktif)
                                Column(modifier = Modifier.fillMaxSize()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                                    color = MaterialTheme.colorScheme.surface,
                                    shadowElevation = 2.dp
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "Ruang Obrolan",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Grup Chat & Diskusi Publik",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        
                                        Spacer(modifier = Modifier.height(12.dp))
                                        
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            FilterChip(
                                                selected = !showOnlySavedRooms,
                                                onClick = { showOnlySavedRooms = false },
                                                label = { Text("Semua (${viewModel.groupRooms.collectAsStateWithLifecycle().value.size})", fontSize = 12.sp) }
                                            )
                                            FilterChip(
                                                selected = showOnlySavedRooms,
                                                onClick = { showOnlySavedRooms = true },
                                                label = { Text("Favorit (${savedRooms.size})", fontSize = 12.sp) },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Bookmark, null, modifier = Modifier.size(14.dp))
                                                }
                                            )
                                        }
                                    }
                                }
                                
                                if (displayedRooms.isEmpty()) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.Forum, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("Belum Ada Ruangan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("Cari atau buat ruangan baru di tab Discover atau tekan tombol tambah.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Button(onClick = { viewModel.isJoinCreateDialogOpen.value = true }) {
                                            Icon(Icons.Default.Add, null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Buat / Gabung Room")
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(displayedRooms, key = { it.jid }) { room ->
                                            RoomItemCard(
                                                room = room,
                                                isSelected = activeRoom?.jid == room.jid,
                                                onClick = { 
                                                    viewModel.selectRoom(room.jid)
                                                },
                                                roomAvatar = viewModel.repository.getRoomAvatar(room.jid),
                                                themePrefs = themePrefs
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Page 1..N: Individual Chat Room Content
                            val room = activeWindows.getOrNull(page - 1)
                            if (room != null) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Multi-Room Channel Switcher Tabs (Keeps context while in room)
                                    MultiRoomTabHeader(
                                        rooms = activeWindows,
                                        selectedRoomJid = room.jid,
                                        showOnlySavedRooms = showOnlySavedRooms,
                                        onToggleSavedOnly = { showOnlySavedRooms = !showOnlySavedRooms },
                                        onSelectRoom = { jid -> 
                                            viewModel.selectRoom(jid)
                                            coroutineScope.launch {
                                                val idx = displayedRooms.indexOfFirst { it.jid == jid }
                                                if (idx >= 0) roomPagerState.animateScrollToPage(idx + 1)
                                            }
                                        },
                                        onAddRoomClick = {
                                            coroutineScope.launch {
                                                roomPagerState.animateScrollToPage(0)
                                            }
                                        }
                                    )

                                    // Room Occupancy and Capabilities Indicator
                                    RoomOccupantsCapsBar(
                                        occupants = occupants,
                                        onOccupantClick = { occ -> 
                                            selectedOccupantForMenu = occ
                                        },
                                        onOpenRosterClick = { viewModel.isRosterSheetOpen.value = true }
                                    )

                                    // Message Feed
                                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                        com.ndregil.inchat.ui.components.WallpaperBackground(
                                            wallpaperStyle = room.wallpaperStyle ?: "default",
                                            customWallpaperUri = themePrefs.customWallpaperUri,
                                            opacity = themePrefs.wallpaperOpacity
                                        )

                                        if (messages.isEmpty()) {
                                            Column(
                                                modifier = Modifier.fillMaxSize().padding(32.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(Icons.Default.Forum, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text("Belum ada pesan di ruang ${room.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                                Text("Kirim pesan pertama untuk memulai obrolan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        } else {
                                            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                item(key = "load_older_header") {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 6.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isLoadingOlder) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                                Text(
                                                                    text = "Memuat pesan sebelumnya...",
                                                                    style = MaterialTheme.typography.labelMedium,
                                                                    color = MaterialTheme.colorScheme.primary
                                                                )
                                                            }
                                                        } else {
                                                            androidx.compose.material3.TextButton(
                                                                onClick = { viewModel.loadOlderMessagesForActiveRoom() }
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Refresh,
                                                                    contentDescription = "Muat Pesan Sebelumnya",
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text(
                                                                    text = "Muat Pesan Sebelumnya",
                                                                    style = MaterialTheme.typography.labelMedium
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                items(messages, key = { msg -> msg.id }) { msg ->
                                                    MessageBubble(
                                                        message = msg,
                                                        myNickname = room.myNickname ?: currentAccount.nickname,
                                                        onSenderClick = { senderNick ->
                                                            val found = occupants.find { it.nickname == senderNick }
                                                            viewModel.setWhisperTarget(found)
                                                        },
                                                        onReplyClick = { replyMsg -> viewModel.setReplyMessage(replyMsg) },
                                                        onQuoteClick = { quoteMsg -> viewModel.quoteMessage(quoteMsg) },
                                                        onWhisperClick = { whisperMsg ->
                                                            val targetNick = whisperMsg.senderNickname
                                                            val found = occupants.find { it.nickname == targetNick }
                                                                ?: com.ndregil.inchat.data.model.RoomOccupant(nickname = targetNick, jid = whisperMsg.senderJid)
                                                            viewModel.setWhisperTarget(found)
                                                        },
                                                        onMentionClick = { nick -> viewModel.insertMention(nick) },
                                                         onTranslateClick = { translateMsg -> viewModel.translateMessage(translateMsg.id) },
                                                        onViewProfile = { profileMsg ->
                                                            val targetNick = profileMsg.senderNickname
                                                            val occ = occupants.find { it.nickname == targetNick }
                                                                ?: com.ndregil.inchat.data.model.RoomOccupant(nickname = targetNick, jid = profileMsg.senderJid, avatar = profileMsg.senderAvatar)
                                                            val target = occ.fullJid.ifBlank { occ.jid.ifBlank { profileMsg.senderJid.ifBlank { "${room.jid}/$targetNick" } } }
                                                            if (target.isNotBlank()) {
                                                                isLoadingProfile = true
                                                                coroutineScope.launch {
                                                                    val profile = viewModel.repository.loadVCard(target)
                                                                    val fetchedAvatar = viewModel.repository.smackAvatarManager.getAvatar(target)
                                                                    if (profile != null && fetchedAvatar != null && profile.avatar == null) {
                                                                        profile.avatar = fetchedAvatar
                                                                    }
                                                                    val finalAvatar = fetchedAvatar ?: profile?.avatar ?: profileMsg.senderAvatar ?: occ.avatar
                                                                    isLoadingProfile = false
                                                                    selectedProfileOccupant = Pair(occ.copy(avatar = finalAvatar), profile)
                                                                }
                                                            } else {
                                                                selectedProfileOccupant = Pair(occ, null)
                                                            }
                                                        },
                                                        onQuerySoftwareVersion = { jid, callback -> viewModel.querySoftwareVersion(jid, callback) },
                                                        bubbleTheme = room.bubbleTheme ?: "dregil-theme",
                                                        isDark = isDarkMode,
                                                        themePrefs = themePrefs
                                                    )
                                                }
                                            }
                                        }

                                        ScrollNavigationButtons(
                                            listState = listState,
                                            totalMessagesCount = messages.size,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(end = 12.dp, bottom = 12.dp)
                                        )
                                    }

                                    // Whisper Target Banner
                                    AnimatedVisibility(visible = whisperTarget != null) {
                                        whisperTarget?.let { target ->
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                color = Color(0xFF6D28D9),
                                                shadowElevation = 2.dp
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Chat,
                                                            contentDescription = null,
                                                            tint = Color(0xFFDDD6FE),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        ClientCapsIcon(
                                                            platform = target.clientCaps.platform,
                                                            clientCaps = target.clientCaps,
                                                            tint = Color(0xFFDDD6FE),
                                                            modifier = Modifier.size(15.dp).clickable { selectedCapsOccupant = target }
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Column {
                                                            Text(
                                                                text = "Percakapan Pribadi Aktif",
                                                                color = Color(0xFFDDD6FE),
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Normal
                                                            )
                                                            Text(
                                                                text = "Tertuju ke @${target.nickname}",
                                                                color = Color.White,
                                                                fontSize = 13.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }

                                                    // Explicit 'Akhiri' (End Whisper) Button
                                                    Surface(
                                                        shape = RoundedCornerShape(16.dp),
                                                        color = Color.White.copy(alpha = 0.2f),
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(16.dp))
                                                            .clickable { viewModel.setWhisperTarget(null) }
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = "Akhiri Percakapan Pribadi",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                            Text(
                                                                text = "Akhiri",
                                                                color = Color.White,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (room.isGroupChat && !room.isJoined) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "Anda telah meninggalkan ruangan ini",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "Gabung kembali untuk mulai mengobrol",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Spacer(Modifier.width(8.dp))
                                                Button(
                                                    onClick = {
                                                        activeRoom?.let { room ->
                                                            val nick = room.myNickname.ifBlank { currentAccount.nickname.ifBlank { currentAccount.username } }
                                                            viewModel.joinRoom(room.jid, nick, room.password)
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Gabung")
                                                }
                                            }
                                        }
                                    } else {
                                        // Input Bar
                                        ChatInputBar(
                                            inputText = currentInput,
                                            onInputChange = { viewModel.currentInputText.value = it },
                                            onSendMessage = { viewModel.sendMessage(it) },
                                            onAttachClick = { filePicker.launch("*/*") },
                                            onShareLocation = { lat, lng, label -> viewModel.shareLocation(lat, lng, label) },
                                            onAttachPhoto = { uri, name, size -> viewModel.sendFile(uri, name, size, "image/jpeg") },
                                            onCallClick = { viewModel.initiateCall(it) },
                                            whisperTarget = whisperTarget,
                                            replyingToMessage = replyingToMessage,
                                            onCancelReply = { viewModel.clearReplyMessage() },
                                            onSendVoice = { audioFile, durationSeconds -> viewModel.sendVoiceMessage(audioFile, durationSeconds) },
                                            translationPreferences = translationPrefs,
                                            onUpdateTranslationPreferences = { viewModel.updateTranslationPreferences(it) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // Direct Chats (1-on-1) View - Swipeable Dual Mode (List View / Active Conversation Screen)
                    val directChats by viewModel.filteredDirectChats.collectAsStateWithLifecycle()
                    val savedDirects by viewModel.savedDirectChats.collectAsStateWithLifecycle()
                    var showOnlySavedDirects by remember { mutableStateOf(false) }
                    var searchDirectQuery by remember { mutableStateOf("") }

                    val isDirectActive = isDirectConversationOpen && activeRoom != null && !activeRoom!!.isGroupChat
                    
                    val chatPagerState = rememberPagerState(initialPage = if (isDirectActive) 1 else 0) { 2 }
                    
                    LaunchedEffect(isDirectActive) {
                        chatPagerState.animateScrollToPage(if (isDirectActive) 1 else 0)
                    }
                    
                    LaunchedEffect(chatPagerState.currentPage) {
                        if (chatPagerState.currentPage == 0 && isDirectActive) {
                            isDirectConversationOpen = false
                        }
                    }

                    if (isDirectActive && chatPagerState.currentPage == 1) {
                        BackHandler {
                            coroutineScope.launch { chatPagerState.animateScrollToPage(0) }
                        }
                    }

                    HorizontalPager(
                        state = chatPagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = chatPagerState.currentPage == 0
                    ) { page ->
                        if (page == 0) {
                            // MODE A: Direct Chat List View
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                            ) {
                                // Top Bar Header for Direct Chats List with Status Bar Inset
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .statusBarsPadding(),
                                    color = MaterialTheme.colorScheme.surface,
                                    shadowElevation = 2.dp
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Chat Pribadi",
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Pesan 1-on-1 & Obrolan Langsung",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            // Button to Start New Chat from Roster
                                            Button(
                                                onClick = {
                                                    activeNavIndex = 2
                                                    viewModel.refreshRoster()
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Chat Baru", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Search Field for Direct Chats
                                        OutlinedTextField(
                                            value = searchDirectQuery,
                                            onValueChange = { searchDirectQuery = it },
                                            placeholder = { Text("Cari obrolan atau kontak...", fontSize = 13.5.sp) },
                                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                            trailingIcon = {
                                                if (searchDirectQuery.isNotEmpty()) {
                                                    IconButton(onClick = { searchDirectQuery = "" }) {
                                                        Icon(Icons.Default.Close, contentDescription = "Bersihkan", modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp)
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Filter Chips (Semua / Favorit)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            FilterChip(
                                                selected = !showOnlySavedDirects,
                                                onClick = { showOnlySavedDirects = false },
                                                label = { Text("Semua Chat (${directChats.size})", fontSize = 12.sp) }
                                            )
                                            FilterChip(
                                                selected = showOnlySavedDirects,
                                                onClick = { showOnlySavedDirects = true },
                                                label = { Text("Favorit (${savedDirects.size})", fontSize = 12.sp) },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Default.Star,
                                                        contentDescription = null,
                                                        tint = if (showOnlySavedDirects) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }

                                val displayedList = (if (showOnlySavedDirects) savedDirects else directChats).filter {
                                    searchDirectQuery.isBlank() || it.name.contains(searchDirectQuery, ignoreCase = true) || it.jid.contains(searchDirectQuery, ignoreCase = true)
                                }

                                if (displayedList.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = if (searchDirectQuery.isNotBlank()) "Tidak Ada Chat yang Cocok" else "Belum Ada Chat Pribadi",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = if (searchDirectQuery.isNotBlank()) "Coba kata kunci lain untuk mencari kontak." else "Mulai obrolan 1-on-1 dengan memilih kontak dari daftar kontak Anda.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                        Button(
                                            onClick = {
                                                activeNavIndex = 2
                                                viewModel.refreshRoster()
                                            },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Pilih Kontak")
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp, horizontal = 20.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(displayedList, key = { it.jid }) { room ->
                                            val avatar = viewModel.repository.getRoomAvatar(room.jid)
                                            RoomItemCard(
                                                room = room,
                                                isSelected = activeRoom?.jid == room.jid,
                                                onClick = {
                                                    viewModel.selectRoom(room.jid)
                                                    isDirectConversationOpen = true
                                                },
                                                roomAvatar = avatar,
                                                themePrefs = themePrefs
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // MODE B: Active Direct Conversation Screen
                            val activeContact = viewModel.rosterContacts.collectAsStateWithLifecycle().value.find { it.jid == activeRoom?.jid }
                            val roomAvatar = remember(activeRoom?.jid) { activeRoom?.let { viewModel.repository.getRoomAvatar(it.jid) } }
                            var showDirectMenu by remember { mutableStateOf(false) }

                            Column(modifier = Modifier.fillMaxSize()) {
                                // Top Bar Header for Active Direct Conversation with Status Bar Inset
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .statusBarsPadding(),
                                    color = MaterialTheme.colorScheme.surface,
                                    shadowElevation = 3.dp
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Back Button to return to Direct Chat List
                                        IconButton(
                                            onClick = { isDirectConversationOpen = false },
                                            modifier = Modifier.testTag("btn_back_to_direct_list")
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Kembali ke Daftar Chat Pribadi"
                                            )
                                        }

                                        // Avatar & Presence Indicator
                                        Box(contentAlignment = Alignment.BottomEnd) {
                                            if (roomAvatar != null) {
                                                coil.compose.AsyncImage(
                                                    model = roomAvatar,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = (activeRoom?.name ?: "?").take(1).uppercase(Locale.getDefault()),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 18.sp,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }

                                            if (activeContact != null) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(12.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.surface)
                                                        .padding(1.5.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .clip(CircleShape)
                                                            .background(
                                                                when (activeContact.presence) {
                                                                    UserPresence.ONLINE, UserPresence.CHAT -> OnlineGreen
                                                                    UserPresence.AWAY, UserPresence.XA -> AwayAmber
                                                                    UserPresence.DND -> DndRed
                                                                    else -> OfflineGray
                                                                }
                                                            )
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        // Name & Presence Status
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = activeRoom?.name ?: "",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = if (themePrefs.isCustomThemeEnabled) {
                                                    try { Color(android.graphics.Color.parseColor(themePrefs.contactColorHex)) } catch (e: Exception) { Color.Unspecified }
                                                } else {
                                                    Color.Unspecified
                                                }
                                            )
                                            Text(
                                                text = if (activeContact != null) {
                                                    val presText = activeContact.presence.name.lowercase(Locale.getDefault()).replaceFirstChar { it.uppercase() }
                                                    if (activeContact.statusMessage.isNotBlank()) "${activeContact.statusMessage} • $presText" else presText
                                                } else {
                                                    activeRoom?.jid ?: "Chat 1-on-1"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (themePrefs.isCustomThemeEnabled) {
                                                    try { Color(android.graphics.Color.parseColor(themePrefs.presenceOnlineColorHex)) } catch (e: Exception) { MaterialTheme.colorScheme.onSurfaceVariant }
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        // Action Buttons
                                        IconButton(
                                            onClick = { viewModel.initiateCall("Audio") },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Call,
                                                contentDescription = "Panggilan Audio",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { viewModel.initiateCall("Video") },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.VideoCall,
                                                contentDescription = "Panggilan Video",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Box {
                                            IconButton(
                                                onClick = { showDirectMenu = true },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Default.MoreVert, contentDescription = "Menu Chat", modifier = Modifier.size(20.dp))
                                            }

                                            DropdownMenu(
                                                expanded = showDirectMenu,
                                                onDismissRequest = { showDirectMenu = false }
                                            ) {
                                                val isCurrentBookmarked = activeRoom?.isBookmarked == true
                                                DropdownMenuItem(
                                                    text = { Text(if (isCurrentBookmarked) "Hapus dari Favorit" else "Tambah ke Favorit") },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = if (isCurrentBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                            contentDescription = null
                                                        )
                                                    },
                                                    onClick = {
                                                        showDirectMenu = false
                                                        activeRoom?.let { viewModel.toggleSaveRoom(it.jid) }
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Warna Chat & Wallpaper") },
                                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                                    onClick = {
                                                        showDirectMenu = false
                                                        viewModel.isThemeWallpaperDialogOpen.value = true
                                                    }
                                                )
                                                 DropdownMenuItem(
                                                    text = { Text("Bersihkan Riwayat Chat") },
                                                    leadingIcon = { Icon(Icons.Default.Clear, contentDescription = null) },
                                                    onClick = {
                                                        showDirectMenu = false
                                                        activeRoom?.let { room ->
                                                            viewModel.clearRoomChatHistory(room.jid)
                                                            if (!room.isGroupChat) {
                                                                isDirectConversationOpen = false
                                                            }
                                                        }
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Tutup Conversation") },
                                                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                                                    onClick = {
                                                        showDirectMenu = false
                                                        activeRoom?.let { room ->
                                                            viewModel.closeConversation(room.jid)
                                                        }
                                                        isDirectConversationOpen = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Message Feed (Direct Chat)
                                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                    com.ndregil.inchat.ui.components.WallpaperBackground(
                                        wallpaperStyle = activeRoom?.wallpaperStyle ?: "default",
                                        customWallpaperUri = themePrefs.customWallpaperUri,
                                        opacity = themePrefs.wallpaperOpacity
                                    )

                                    if (messages.isEmpty()) {
                                        Column(
                                            modifier = Modifier.fillMaxSize().padding(32.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(Icons.Default.Person, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text("Belum ada pesan pribadi dengan ${activeRoom?.name ?: "kontak ini"}", style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Kirim pesan pertama untuk memulai obrolan langsung.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    } else {
                                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            item(key = "load_older_header_direct") {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 6.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isLoadingOlder) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                            Text(
                                                                text = "Memuat pesan sebelumnya...",
                                                                style = MaterialTheme.typography.labelMedium,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    } else {
                                                        androidx.compose.material3.TextButton(
                                                            onClick = { viewModel.loadOlderMessagesForActiveRoom() }
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Refresh,
                                                                contentDescription = "Muat Pesan Sebelumnya",
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = "Muat Pesan Sebelumnya",
                                                                style = MaterialTheme.typography.labelMedium
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            items(messages, key = { msg -> msg.id }) { msg ->
                                                MessageBubble(
                                                    message = msg,
                                                    myNickname = activeRoom?.myNickname ?: currentAccount.nickname,
                                                    onSenderClick = { },
                                                    onReplyClick = { replyMsg -> viewModel.setReplyMessage(replyMsg) },
                                                    onQuoteClick = { quoteMsg -> viewModel.quoteMessage(quoteMsg) },
                                                    onMentionClick = { nick -> viewModel.insertMention(nick) },
                                                    onTranslateClick = { translateMsg -> viewModel.translateMessage(translateMsg.id) },
                                                    onViewProfile = { profileMsg ->
                                                        val targetNick = profileMsg.senderNickname
                                                        val occ = occupants.find { it.nickname == targetNick }
                                                            ?: com.ndregil.inchat.data.model.RoomOccupant(nickname = targetNick, jid = profileMsg.senderJid, avatar = profileMsg.senderAvatar)
                                                        val target = occ.fullJid.ifBlank { occ.jid.ifBlank { profileMsg.senderJid.ifBlank { if (activeRoom != null) "${activeRoom!!.jid}/$targetNick" else "" } } }
                                                        if (target.isNotBlank()) {
                                                            isLoadingProfile = true
                                                            coroutineScope.launch {
                                                                val profile = viewModel.repository.loadVCard(target)
                                                                val fetchedAvatar = viewModel.repository.smackAvatarManager.getAvatar(target)
                                                                if (profile != null && fetchedAvatar != null && profile.avatar == null) {
                                                                    profile.avatar = fetchedAvatar
                                                                }
                                                                val finalAvatar = fetchedAvatar ?: profile?.avatar ?: profileMsg.senderAvatar ?: occ.avatar
                                                                isLoadingProfile = false
                                                                selectedProfileOccupant = Pair(occ.copy(avatar = finalAvatar), profile)
                                                            }
                                                        } else {
                                                            selectedProfileOccupant = Pair(occ, null)
                                                        }
                                                    },
                                                    onQuerySoftwareVersion = { jid, callback -> viewModel.querySoftwareVersion(jid, callback) },
                                                    bubbleTheme = activeRoom?.bubbleTheme ?: "dregil-theme",
                                                    isDark = isDarkMode,
                                                    themePrefs = themePrefs
                                                )
                                            }
                                        }

                                        ScrollNavigationButtons(
                                            listState = listState,
                                            totalMessagesCount = messages.size,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(end = 12.dp, bottom = 12.dp)
                                        )
                                    }
                                }

                                // Chat Input Bar
                                ChatInputBar(
                                    inputText = currentInput,
                                    onInputChange = { viewModel.currentInputText.value = it },
                                    onSendMessage = { viewModel.sendMessage(it) },
                                    onAttachClick = { filePicker.launch("*/*") },
                                    onShareLocation = { lat, lng, label -> viewModel.shareLocation(lat, lng, label) },
                                    onAttachPhoto = { uri, name, size -> viewModel.sendFile(uri, name, size, "image/jpeg") },
                                    onCallClick = { viewModel.initiateCall(it) },
                                    whisperTarget = null,
                                    replyingToMessage = replyingToMessage,
                                    onCancelReply = { viewModel.clearReplyMessage() },
                                    onSendVoice = { audioFile, durationSeconds ->
                                        viewModel.sendVoiceMessage(audioFile, durationSeconds)
                                    },
                                    translationPreferences = translationPrefs,
                                    onUpdateTranslationPreferences = { viewModel.updateTranslationPreferences(it) }
                                )
                            }
                        }
                    }
                }

                2 -> {
                    // XMPP Roster & Contacts (RFC 6121 / XEP-0115 / XEP-0172)
                    LaunchedEffect(Unit) {
                        viewModel.refreshRoster()
                    }
                    RosterContactsScreen(
                        viewModel = viewModel,
                        onStartDirectChat = { jid, name ->
                            viewModel.startDirectChat(jid, name)
                            activeNavIndex = 1
                        },
                        onRoomClick = { jid ->
                            viewModel.selectRoom(jid)
                            activeNavIndex = 0
                        },
                        themePrefs = themePrefs
                    )
                }

                3 -> {
                    // Public MUC Directory & Local Service Discovery (XEP-0030)
                    val discoveredServices by viewModel.discoveredServices.collectAsStateWithLifecycle()
                    val isServicesLoading by viewModel.isServicesLoading.collectAsStateWithLifecycle()
                    val discoveredRooms by viewModel.discoveredRooms.collectAsStateWithLifecycle()
                    val isDiscovering by viewModel.isDiscovering.collectAsStateWithLifecycle()
                    
                    LaunchedEffect(Unit) {
                        if (directoryRooms.isEmpty()) {
                            viewModel.loadDirectory()
                        }
                        if (discoveredServices.isEmpty()) {
                            viewModel.discoverLocalServices()
                        }
                        if (discoveredRooms.isEmpty()) {
                            viewModel.discoverServerRooms()
                        }
                    }
                    
                    RoomDirectoryScreen(
                        directoryRooms = directoryRooms,
                        discoveredServices = discoveredServices,
                        joinedRooms = allRooms,
                        isLoading = isDirectoryLoading,
                        isServicesLoading = isServicesLoading,
                        serverDomain = currentAccount.domain,
                        onRefresh = { query -> viewModel.loadDirectory(query) },
                        onDiscoverLocal = { viewModel.discoverLocalServices() },
                        onJoinRoom = { dirRoom ->
                            viewModel.joinOrCreateRoom(
                                roomJid = dirRoom.jid,
                                roomName = dirRoom.name,
                                nickname = currentAccount.nickname,
                                topic = dirRoom.description
                            )
                            activeNavIndex = if (isMucJid(dirRoom.jid)) 0 else 1
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
                            activeNavIndex = if (isMucJid(jid)) 0 else 1
                        },
                        onOpenCreateRoom = {
                            viewModel.isJoinCreateDialogOpen.value = true
                        },
                        discoveredMucRooms = discoveredRooms,
                        isDiscoveringServerRooms = isDiscovering,
                        onDiscoverServerRooms = { viewModel.discoverServerRooms() },
                        onJoinDiscoveredRoom = { discRoom ->
                            viewModel.joinOrCreateRoom(
                                roomJid = discRoom.jid,
                                roomName = discRoom.name,
                                nickname = currentAccount.nickname,
                                topic = discRoom.description.ifBlank { discRoom.subject }
                            )
                            activeNavIndex = if (isMucJid(discRoom.jid)) 0 else 1
                        }
                    )
                }

                4 -> {
                    if (showBookmarksScreen) {
                        ManageBookmarksScreen(
                            savedRooms = savedRooms,
                            onToggleAutoJoin = { jid, autoJoin -> viewModel.updateRoomAutoJoin(jid, autoJoin) },
                            onRemoveBookmark = { jid -> viewModel.removeSavedRoom(jid) },
                            onUpdateRoomColor = { jid, color -> viewModel.updateRoomColor(jid, color) },
                            onBack = { showBookmarksScreen = false }
                        )
                    } else if (showCustomThemeScreen) {
                        CustomThemeScreen(
                            currentPrefs = themePrefs,
                            onPrefsChange = { viewModel.updateThemePreferences(it) },
                            onBack = { showCustomThemeScreen = false }
                        )
                    } else if (!showAccountSettingsDirectly) {
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
                            onNavigateToBookmarks = { showBookmarksScreen = true },
                            onNavigateToCustomTheme = { showCustomThemeScreen = true },
                            onSwitchToAccountTab = {
                                viewModel.switchAccount {
                                    initialAccountTabSet = 1
                                    showAccountSettingsDirectly = true
                                }
                            },
                            autoApproveSubscription = viewModel.autoApproveSubscription.collectAsStateWithLifecycle().value,
                            onToggleAutoApproveSubscription = { viewModel.setAutoApproveSubscription(it) },
                            translationPreferences = viewModel.translationPreferences.collectAsStateWithLifecycle().value,
                            onUpdateTranslationPreferences = { viewModel.updateTranslationPreferences(it) },
                            themePreferences = viewModel.themePreferences.collectAsStateWithLifecycle().value,
                            onUpdateThemePreferences = { viewModel.updateThemePreferences(it) },
                            onSignOut = {
                                viewModel.signOut {
                                    initialAccountTabSet = 1
                                    showAccountSettingsDirectly = true
                                }
                            },
                            onExitApp = { viewModel.exitApp() }
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
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
                                        onClick = { showAccountSettingsDirectly = false },
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
                                        text = "Konfigurasi Akun & Server",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            AccountConnectionScreen(
                                currentAccount = currentAccount,
                                connectionStatus = connectionStatus,
                                registrationState = registrationState,
                                initialTab = initialAccountTabSet,
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
                                onSolveCaptcha = { challenge, answer ->
                                    viewModel.submitCaptchaAnswer(challenge, answer)
                                },
                                backupStatus = backupStatus,
                                isBackupLoading = isBackupLoading,
                                onExportBackup = { uri, pwd -> viewModel.exportBackup(uri, pwd) },
                                onImportBackup = { uri, pwd -> viewModel.importBackup(uri, pwd) },
                                onClearBackupStatus = { viewModel.clearBackupStatus() },
                                onUpdateAvatar = { viewModel.updateAvatar(it) },
                                onSwitchAccount = { viewModel.switchAccount() },
                                myAvatar = viewModel.myAvatar.collectAsStateWithLifecycle().value,
                                lastErrorMessage = connectionError
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheets & Dialogs
    if (selectedOccupantForMenu != null) {
        val occ = selectedOccupantForMenu!!
        OccupantActionDialog(
            occupant = occ,
            roomJid = activeRoom?.jid ?: "",
            onDismiss = { selectedOccupantForMenu = null },
            onMention = {
                viewModel.insertMention(occ.nickname)
            },
            onWhisper = {
                viewModel.setWhisperTarget(occ)
            },
            onQueryVersion = {
                // Open ClientCapsDialog directly for this occupant
                selectedCapsOccupant = occ
            },
            onViewProfile = {
                val target = occ.fullJid.ifBlank { occ.jid.ifBlank { if (activeRoom != null) "${activeRoom!!.jid}/${occ.nickname}" else "" } }
                if (target.isNotBlank()) {
                    isLoadingProfile = true
                    coroutineScope.launch {
                        val profile = viewModel.repository.loadVCard(target)
                        val fetchedAvatar = viewModel.repository.smackAvatarManager.getAvatar(target)
                        if (profile != null && fetchedAvatar != null && profile.avatar == null) {
                            profile.avatar = fetchedAvatar
                        }
                        val finalAvatar = fetchedAvatar ?: profile?.avatar ?: occ.avatar
                        isLoadingProfile = false
                        selectedProfileOccupant = Pair(occ.copy(avatar = finalAvatar), profile)
                    }
                } else {
                    selectedProfileOccupant = Pair(occ, null)
                }
            }
        )
    }

    // Loading overlay for profile fetch
    if (isLoadingProfile) {
        AlertDialog(
            onDismissRequest = { isLoadingProfile = false },
            title = { Text("Memuat Profil...") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("Mengambil data vCard dari server...")
                }
            },
            confirmButton = {}
        )
    }

    // Rich Profile Dialog (vCard)
    selectedProfileOccupant?.let { (occupant, vCard) ->
        val effectiveJid = occupant.fullJid.ifBlank { occupant.jid.ifBlank { if (activeRoom != null) "${activeRoom!!.jid}/${occupant.nickname}" else "" } }
        val displayName = if (vCard != null) {
            val nick = vCard.nickName ?: occupant.nickname
            val first = vCard.firstName ?: ""
            val last = vCard.lastName ?: ""
            "$first $last".trim().ifBlank { nick }
        } else {
            occupant.nickname
        }
        val org = vCard?.organization ?: "-"
        val email = vCard?.emailHome ?: vCard?.emailWork ?: "-"
        val avatarBytes = vCard?.avatar ?: occupant.avatar
        var showFullPhoto by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { selectedProfileOccupant = null },
            title = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .clickable { if (avatarBytes != null) showFullPhoto = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarBytes != null) {
                            coil.compose.AsyncImage(
                                model = avatarBytes,
                                contentDescription = "Foto Profil ${occupant.nickname}",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = occupant.nickname.take(1).uppercase(Locale.getDefault()),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = displayName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "@${occupant.nickname}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Status / Presence:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(occupant.statusText.ifBlank { occupant.presence.name }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Peran di Room:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${occupant.role.name} (${occupant.affiliation.name})", style = MaterialTheme.typography.bodyMedium)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Organisasi:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(org, style = MaterialTheme.typography.bodyMedium)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Email:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(email, style = MaterialTheme.typography.bodyMedium)
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("JID / Alamat XMPP:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(effectiveJid, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedProfileOccupant = null }) {
                    Text("Tutup")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )

        if (showFullPhoto && avatarBytes != null) {
            AlertDialog(
                onDismissRequest = { showFullPhoto = false },
                title = { Text("Foto vCard ${occupant.nickname}") },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        coil.compose.AsyncImage(
                            model = avatarBytes,
                            contentDescription = "Foto vCard Full",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showFullPhoto = false }) {
                        Text("Tutup Foto")
                    }
                }
            )
        }
    }

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
            onQuerySoftwareVersion = { jid, callback -> viewModel.querySoftwareVersion(jid, callback) },
            onGrantOwnership = { jid -> activeRoom?.let { room -> viewModel.grantOwnership(room.jid, jid) } },
            onGrantAdmin = { jid -> activeRoom?.let { room -> viewModel.grantAdmin(room.jid, jid) } },
            onGrantModerator = { nick -> activeRoom?.let { room -> viewModel.grantModerator(room.jid, nick) } },
            onGrantMembership = { jid -> activeRoom?.let { room -> viewModel.grantMembership(room.jid, jid) } },
            onGrantVoice = { nick -> activeRoom?.let { room -> viewModel.grantVoice(room.jid, nick) } },
            onRevokeVoice = { nick -> activeRoom?.let { room -> viewModel.revokeVoice(room.jid, nick) } },
            onKickUser = { nick, reason -> activeRoom?.let { room -> viewModel.kickParticipant(room.jid, nick, reason) } },
            onBanUser = { jid, reason -> activeRoom?.let { room -> viewModel.banUser(room.jid, jid, reason) } }
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
        val joinStatus by viewModel.joinStatus.collectAsStateWithLifecycle()
        val isJoinLoading by viewModel.isJoinLoading.collectAsStateWithLifecycle()
        
        JoinCreateRoomDialog(
            defaultNickname = currentAccount.nickname,
            isLoading = isJoinLoading,
            status = joinStatus,
            onDismiss = { 
                viewModel.isJoinCreateDialogOpen.value = false 
                viewModel.clearJoinStatus()
            },
            onJoinOrCreate = { jid, name, nick, topic, pwd, save ->
                viewModel.joinOrCreateRoom(jid, name, nick, topic, pwd, save)
                // activeNavIndex = 0 // Removed to keep dialog open if failure occurs
            }
        )
    }

    if (isRoomConfigOpen) {
        activeRoom?.let { room ->
            val config = roomConfigs.find { it.roomJid == room.jid }
            RoomConfigurationDialog(
                roomJid = room.jid,
                initialInfo = roomConfigInfo,
                moderationMessages = moderationHistory,
                isLoading = isRoomConfigLoading,
                isPersonalOnly = !isConfigNicknameOnly,
                isNicknameOnly = isConfigNicknameOnly,
                ownersList = ownersList,
                adminsList = adminsList,
                membersList = membersList,
                outcastsList = outcastsList,
                isLoadingAffiliations = isLoadingAffiliations,
                onLoadAffiliations = { viewModel.loadRoomAffiliations(room.jid) },
                onGrantOwnership = { jid -> viewModel.grantOwnership(room.jid, jid) },
                onGrantAdmin = { jid -> viewModel.grantAdmin(room.jid, jid) },
                onGrantMembership = { jid -> viewModel.grantMembership(room.jid, jid) },
                onBanUser = { jid, reason -> viewModel.banUser(room.jid, jid, reason) },
                onRevokeAffiliation = { jid -> viewModel.grantNone(room.jid, jid) },
                myRole = room.myRole,
                myAffiliation = room.myAffiliation,
                initialNotificationMode = config?.notificationMode ?: 0,
                onDismiss = { viewModel.isRoomConfigOpen.value = false },
                onUpdateNickname = { newNick -> viewModel.updateRoomNickname(room.jid, newNick) },
                onUpdateTopic = { newTopic -> viewModel.updateTopic(newTopic) },
                onUpdateRoomAvatar = { uri -> viewModel.updateRoomAvatar(room.jid, room.name, uri) },
                roomAvatar = viewModel.repository.getRoomAvatar(room.jid),
                onSaveConfig = { name, desc, subj, persistent, public, membersOnly, moderated, allowSubjChange, pass, notifMode, maxUsers, enableLogging, allowInvites, whois ->
                    viewModel.configureRoom(
                        roomJid = room.jid,
                        roomName = name,
                        description = desc,
                        subject = subj,
                        isPersistent = persistent,
                        isPublic = public,
                        isMembersOnly = membersOnly,
                        isModerated = moderated,
                        allowSubjectChange = allowSubjChange,
                        password = pass,
                        notificationMode = notifMode,
                        maxUsers = maxUsers,
                        enableLogging = enableLogging,
                        allowInvites = allowInvites,
                        whois = whois
                    )
                }
            )
        }
    }

    if (isThemeWallpaperOpen) {

        val activeRoomAvatar = activeRoom?.jid?.let { viewModel.repository.getRoomAvatar(it) }
        com.ndregil.inchat.ui.components.ChatThemeSettingsDialog(
            currentTheme = activeRoom?.bubbleTheme ?: "dregil-theme",
            currentWallpaper = activeRoom?.wallpaperStyle ?: "default",
            roomJid = activeRoom?.jid,
            roomName = activeRoom?.name,
            roomAvatar = activeRoomAvatar,
            onSelectRoomAvatar = { roomAvatarPicker.launch("image/*") },
            onSave = { theme: String, wallpaper: String ->
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
            isLoadingConfig = isRoomConfigLoading,
            ownersList = ownersList,
            adminsList = adminsList,
            membersList = membersList,
            outcastsList = outcastsList,
            isLoadingAffiliations = isLoadingAffiliations,
            onDismiss = { viewModel.isAdminPanelOpen.value = false },
            onLoadAffiliations = { viewModel.loadRoomAffiliations(activeRoom!!.jid) },
            onUpdateTopic = { newTopic -> viewModel.updateTopic(newTopic) },
            onUpdateNickname = { newNick -> viewModel.updateRoomNickname(activeRoom!!.jid, newNick) },
            onUpdateRoomAvatar = { uri -> viewModel.updateRoomAvatar(activeRoom!!.jid, activeRoom!!.name, uri) },
            roomAvatar = viewModel.repository.getRoomAvatar(activeRoom!!.jid),
            onSaveConfig = { name, desc, subj, persistent, public, membersOnly, moderated, allowSubjChange, pass, maxUsers, enableLogging, allowInvites, whois ->
                viewModel.configureRoom(
                    roomJid = activeRoom!!.jid,
                    roomName = name,
                    description = desc,
                    subject = subj,
                    isPersistent = persistent,
                    isPublic = public,
                    isMembersOnly = membersOnly,
                    isModerated = moderated,
                    allowSubjectChange = allowSubjChange,
                    password = pass,
                    maxUsers = maxUsers,
                    enableLogging = enableLogging,
                    allowInvites = allowInvites,
                    whois = whois
                )
            },
            onGrantModerator = { nick -> viewModel.grantModerator(activeRoom!!.jid, nick) },
            onRevokeModerator = { nick -> viewModel.revokeModerator(activeRoom!!.jid, nick) },
            onGrantMembership = { jid -> viewModel.grantMembership(activeRoom!!.jid, jid) },
            onRevokeMembership = { jid -> viewModel.revokeMembership(activeRoom!!.jid, jid) },
            onGrantAdmin = { jid -> viewModel.grantAdmin(activeRoom!!.jid, jid) },
            onRevokeAdmin = { jid -> viewModel.revokeAdmin(activeRoom!!.jid, jid) },
            onGrantOwnership = { jid -> viewModel.grantOwnership(activeRoom!!.jid, jid) },
            onRevokeOwnership = { jid -> viewModel.revokeOwnership(activeRoom!!.jid, jid) },
            onGrantNone = { jid -> viewModel.grantNone(activeRoom!!.jid, jid) },
            onGrantVoice = { nick -> viewModel.grantVoice(activeRoom!!.jid, nick) },
            onRevokeVoice = { nick -> viewModel.revokeVoice(activeRoom!!.jid, nick) },
            onBanUser = { jid, reason -> viewModel.banUser(activeRoom!!.jid, jid, reason) },
            onUnbanUser = { jid -> viewModel.unbanUser(activeRoom!!.jid, jid) },
            onKickUser = { nick, reason -> viewModel.kickParticipant(activeRoom!!.jid, nick, reason) },
            moderationMessages = moderationHistory
        )
    }

    selectedCapsOccupant?.let { occ ->
        ClientCapsDialog(
            clientCaps = occ.clientCaps,
            userName = "${occ.nickname} (${occ.jid.ifBlank { "MUC Occupant" }})",
            targetJid = occ.fullJid,
            onQuerySoftwareVersion = { jid, onResult -> viewModel.querySoftwareVersion(jid, onResult) },
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
                val isOwner = activeRoom!!.myRole == OccupantRole.OWNER || activeRoom!!.myAffiliation == OccupantAffiliation.OWNER
                val isAdmin = activeRoom!!.myRole == OccupantRole.ADMIN || activeRoom!!.myAffiliation == OccupantAffiliation.ADMIN
                val allowSubjectChange = roomAdminInfo?.isSubjectModifiable ?: false
                val canChange = isOwner || isAdmin || allowSubjectChange
                if (canChange) {
                    Button(onClick = {
                        showTopicDetailDialog = false
                        editTopicText = currentTopic
                        viewModel.isTopicModalOpen.value = true
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ubah Topik")
                    }
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

                            // Client icon only if strictly identified
                            if (occupant.clientCaps.clientName.isNotBlank()) {
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
            }

            // Roster Button with Member Count Badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenRosterClick)
                    .testTag("btn_open_roster_caps_bar")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = "Buka Roster Anggota",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${occupants.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
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
    val tabListState = rememberLazyListState()

    androidx.compose.runtime.LaunchedEffect(selectedRoomJid, rooms) {
        val index = rooms.indexOfFirst { it.jid == selectedRoomJid }
        if (index >= 0) {
            tabListState.animateScrollToItem(index)
        }
    }

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
                    state = tabListState,
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
                                    M3Badge(
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
                    imageVector = Icons.Default.Forum,
                    contentDescription = "Daftar Room Saat Ini",
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
                    M3Badge(
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
    onShareLocation: (Double, Double, String) -> Unit,
    onAttachPhoto: ((android.net.Uri, String, Long) -> Unit)? = null,
    onCallClick: (String) -> Unit,
    whisperTarget: RoomOccupant?,
    replyingToMessage: XmppMessage? = null,
    onCancelReply: () -> Unit = {},
    onSendVoice: ((java.io.File, Int) -> Unit)? = null,
    translationPreferences: com.ndregil.inchat.data.model.TranslationPreferences = com.ndregil.inchat.data.model.TranslationPreferences(),
    onUpdateTranslationPreferences: ((com.ndregil.inchat.data.model.TranslationPreferences) -> Unit)? = null
) {
    var showEmojiKeyboard by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var showTranslationMenu by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var currentAmpRatio by remember { mutableFloatStateOf(0f) }

    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    LaunchedEffect(replyingToMessage) {
        if (replyingToMessage != null) {
            showEmojiKeyboard = false
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    var tfvState by remember {
        mutableStateOf(
            androidx.compose.ui.text.input.TextFieldValue(
                text = inputText,
                selection = androidx.compose.ui.text.TextRange(inputText.length)
            )
        )
    }

    LaunchedEffect(inputText) {
        if (inputText != tfvState.text) {
            tfvState = androidx.compose.ui.text.input.TextFieldValue(
                text = inputText,
                selection = androidx.compose.ui.text.TextRange(inputText.length)
            )
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            try {
                val photoFile = java.io.File(context.cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
                java.io.FileOutputStream(photoFile).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                }
                val uri = try {
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        photoFile
                    )
                } catch (e: Exception) {
                    android.net.Uri.fromFile(photoFile)
                }
                onAttachPhoto?.invoke(uri, photoFile.name, photoFile.length())
                android.widget.Toast.makeText(context, "Foto berhasil diambil!", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Gagal menyimpan foto: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            takePictureLauncher.launch(null)
        } else {
            android.widget.Toast.makeText(context, "Izin kamera diperlukan untuk mengambil foto", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Recording timer & amplitude polling
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording) {
                kotlinx.coroutines.delay(100)
                recordingSeconds = VoiceRecorderManager.getElapsedSeconds()
                currentAmpRatio = VoiceRecorderManager.getAmplitudeRatio()
            }
        }
    }

    // Permission launcher for Voice Recording
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val result = VoiceRecorderManager.startRecording(context)
            if (result.isSuccess) {
                isRecording = true
                showEmojiKeyboard = false
            } else {
                android.widget.Toast.makeText(context, "Gagal memulai rekaman audio: ${result.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            android.widget.Toast.makeText(context, "Izin mikrofon diperlukan untuk merekam pesan suara", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
        val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val loc = if (hasFine || hasCoarse) {
            try {
                @Suppress("MissingPermission")
                val fineLoc = locationManager?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                @Suppress("MissingPermission")
                val coarseLoc = locationManager?.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                fineLoc ?: coarseLoc
            } catch (e: Exception) { null }
        } else null

        val lat = loc?.latitude ?: -6.200000
        val lng = loc?.longitude ?: 106.816666
        onShareLocation(lat, lng, "Lokasi GPS Terkini")
        if (isGranted) {
            android.widget.Toast.makeText(context, "Lokasi GPS berhasil dibagikan!", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(context, "Izin lokasi tidak diberikan, membagikan lokasi standar", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column {
            // Reply Preview Header
            if (replyingToMessage != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
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
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Membalas ${replyingToMessage.senderNickname.replace("@System", "").trim()}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
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
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            // Outgoing Translation Active Badge Bar
            AnimatedVisibility(visible = translationPreferences.translateOutgoing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val activeLangName = com.ndregil.inchat.data.network.TranslationService.getLanguageName(translationPreferences.targetLanguageCode)
                    Surface(
                        onClick = { showTranslationMenu = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Terjemahkan Outgoing: $activeLangName (${translationPreferences.targetLanguageCode.uppercase()})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            onUpdateTranslationPreferences?.invoke(translationPreferences.copy(translateOutgoing = false))
                        },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Nonaktifkan",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Main Input Row or Recording Active Row
            if (isRecording) {
                VoiceRecordingActiveBar(
                    recordingSeconds = recordingSeconds,
                    amplitudeRatio = currentAmpRatio,
                    onCancel = {
                        VoiceRecorderManager.cancelRecording()
                        isRecording = false
                    },
                    onSend = {
                        val (file, duration) = VoiceRecorderManager.stopRecording()
                        isRecording = false
                        if (file != null && file.exists() && file.length() > 0) {
                            onSendVoice?.invoke(file, duration)
                        } else {
                            android.widget.Toast.makeText(context, "Rekaman terlalu pendek", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Optimized Form Box Capsule
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .animateContentSize(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                            // 1. Emoji button
                            IconButton(
                                onClick = {
                                    if (!showEmojiKeyboard) {
                                        keyboardController?.hide()
                                    }
                                    showEmojiKeyboard = !showEmojiKeyboard
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("btn_toggle_emoji")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SentimentSatisfied,
                                    contentDescription = "Emoji",
                                    tint = if (showEmojiKeyboard) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // 2. Text Input
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp, vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (inputText.isEmpty()) {
                                    Text(
                                        text = if (whisperTarget != null) "Bisikkan ke @${whisperTarget.nickname}..." else "Tulis pesan...",
                                        fontSize = 15.sp,
                                        color = if (whisperTarget != null) Color(0xFFA855F7).copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }

                                BasicTextField(
                                    value = tfvState,
                                    onValueChange = { newVal ->
                                        tfvState = newVal
                                        if (newVal.text != inputText) {
                                            onInputChange(newVal.text)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester)
                                        .testTag("chat_input_field"),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    minLines = 1,
                                    maxLines = 6
                                )
                            }

                            // 3. Compact Action Row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                // Tombol Terjemahan Outgoing
                                IconButton(
                                    onClick = { showTranslationMenu = true },
                                    modifier = Modifier.size(32.dp).testTag("btn_translate_input")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Translate,
                                        contentDescription = "Pilihan Bahasa Terjemahan",
                                        tint = if (translationPreferences.translateOutgoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                if (inputText.isBlank()) {
                                    // Lokasi
                                    IconButton(
                                        onClick = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                                        modifier = Modifier.size(32.dp).testTag("btn_loc")
                                    ) {
                                        Icon(Icons.Default.LocationOn, null, tint = Color(0xFFEF4444), modifier = Modifier.size(19.dp))
                                    }

                                    // Lampiran
                                    IconButton(
                                        onClick = onAttachClick,
                                        modifier = Modifier.size(32.dp).testTag("btn_attach")
                                    ) {
                                        Icon(Icons.Default.AttachFile, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                                    }

                                    // Tombol Suara (Mic)
                                    IconButton(
                                        onClick = {
                                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        },
                                        modifier = Modifier.size(32.dp).testTag("btn_voice")
                                    ) {
                                        Icon(Icons.Default.Mic, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Dropdown Menu Pilihan Bahasa Terjemahan
                    DropdownMenu(
                        expanded = showTranslationMenu,
                        onDismissRequest = { showTranslationMenu = false },
                        modifier = Modifier
                            .width(260.dp)
                            .heightIn(max = 380.dp)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Terjemahkan Outgoing",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Switch(
                                        checked = translationPreferences.translateOutgoing,
                                        onCheckedChange = { isChecked: Boolean ->
                                            onUpdateTranslationPreferences?.invoke(
                                                translationPreferences.copy(
                                                    isAutoTranslateEnabled = true,
                                                    translateOutgoing = isChecked
                                                )
                                            )
                                        },
                                        modifier = Modifier.scale(0.85f)
                                    )
                                }
                            },
                            onClick = { }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        Text(
                            text = "Pilih Bahasa Terjemahan Pesan Keluar:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )

                        com.ndregil.inchat.data.network.TranslationService.SUPPORTED_LANGUAGES.forEach { lang ->
                            val isSelected = lang.code.equals(translationPreferences.targetLanguageCode, ignoreCase = true)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${lang.displayName} (${lang.nativeName})",
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    onUpdateTranslationPreferences?.invoke(
                                        translationPreferences.copy(
                                            isAutoTranslateEnabled = true,
                                            translateOutgoing = true,
                                            targetLanguageCode = lang.code
                                        )
                                    )
                                    showTranslationMenu = false
                                }
                            )
                        }
                    }

                    if (inputText.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        // Floating Send Button
                        FloatingActionButton(
                            onClick = {
                                onSendMessage(inputText)
                                showEmojiKeyboard = false
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("btn_send_message"),
                            shape = CircleShape,
                            containerColor = if (whisperTarget != null) Color(0xFF7C3AED) else MaterialTheme.colorScheme.primary,
                            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Kirim",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showEmojiKeyboard) {
                com.ndregil.inchat.ui.components.EmojiKeyboard(
                    onEmojiSelected = { emoji ->
                        onInputChange(inputText + emoji)
                    }
                )
            }

            if (showLocationDialog) {
                com.ndregil.inchat.ui.components.InteractiveMapPickerDialog(
                    onDismiss = { showLocationDialog = false },
                    onShareLocation = { lat, lng, label ->
                        onShareLocation(lat, lng, label)
                    }
                )
            }
        }
    }
}

@Composable
private fun VoiceRecordingActiveBar(
    recordingSeconds: Int,
    amplitudeRatio: Float,
    onCancel: () -> Unit,
    onSend: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_rec")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_dot_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Cancel / Delete Trash Button
        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
                .testTag("btn_cancel_voice")
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Batal Rekaman",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
        }

        // Recording Visualizer Capsule
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Pulsing Red Dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444).copy(alpha = alphaAnim))
                    )

                    // Timer Text
                    Text(
                        text = String.format(Locale.US, "%02d:%02d", recordingSeconds / 60, recordingSeconds % 60),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Audio Equalizer Visualizer Bars
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val baseBars = listOf(0.4f, 0.7f, 0.5f, 0.9f, 0.6f, 0.8f, 0.4f, 0.7f)
                    baseBars.forEach { base ->
                        val dynamicWeight = (base * 0.4f + amplitudeRatio * 0.6f).coerceIn(0.2f, 1f)
                        val barHeight = (18 * dynamicWeight).dp
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }

        // Send Voice Button
        IconButton(
            onClick = onSend,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .testTag("btn_send_voice")
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Kirim Pesan Suara",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun isMucJid(jid: String): Boolean {
    val domain = jid.substringAfter("@", "")
    return domain.contains("conference", true) || 
           domain.contains("muc.", true) || 
           domain.contains("chat.", true) ||
           domain.contains("rooms.", true)
}

@Composable
fun OccupantActionDialog(
    occupant: RoomOccupant,
    roomJid: String,
    onDismiss: () -> Unit,
    onMention: () -> Unit,
    onWhisper: () -> Unit,
    onQueryVersion: () -> Unit,
    onViewProfile: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
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
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = occupant.nickname, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(text = occupant.jid.ifBlank { "Anggota Room" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(bottom = 4.dp))
                
                DropdownMenuItem(
                    text = { Text("Mention (@${occupant.nickname})") },
                    leadingIcon = { Icon(Icons.Default.AlternateEmail, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { onMention(); onDismiss() }
                )
                DropdownMenuItem(
                    text = { Text("Kirim Pesan Pribadi (Bisikan)") },
                    leadingIcon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { onWhisper(); onDismiss() }
                )
                DropdownMenuItem(
                    text = { Text("Cek Versi Klien (XEP-0092)") },
                    leadingIcon = { Icon(Icons.Default.Code, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { onQueryVersion(); onDismiss() }
                )
                DropdownMenuItem(
                    text = { Text("Lihat Profil Anggota") },
                    leadingIcon = { Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { onViewProfile(); onDismiss() }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun ScrollNavigationButtons(
    listState: androidx.compose.foundation.lazy.LazyListState,
    totalMessagesCount: Int,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val showScrollToBottom by remember(listState, totalMessagesCount) {
        derivedStateOf {
            totalMessagesCount > 0 && listState.firstVisibleItemIndex < (totalMessagesCount - 3).coerceAtLeast(0)
        }
    }
    val showScrollToTop by remember(listState, totalMessagesCount) {
        derivedStateOf {
            totalMessagesCount > 0 && listState.firstVisibleItemIndex > 2
        }
    }

    AnimatedVisibility(
        visible = showScrollToTop || showScrollToBottom,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showScrollToTop) {
                SmallFloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("btn_scroll_top")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Ke Pesan Teratas",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (showScrollToBottom) {
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            val lastIndex = totalMessagesCount
                            listState.animateScrollToItem(lastIndex)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("btn_scroll_bottom")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Ke Pesan Terbaru",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
