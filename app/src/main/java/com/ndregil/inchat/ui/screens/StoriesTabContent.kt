package com.ndregil.inchat.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ndregil.inchat.data.model.ContactStoryGroup
import com.ndregil.inchat.data.model.RosterContact
import com.ndregil.inchat.data.model.StoryMediaType
import com.ndregil.inchat.data.model.UserStory
import com.ndregil.inchat.ui.XmppViewModel
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WhatsApp-style Stories / Status Tab screen.
 * Shows "Status Saya" at the top with options to create text or photo story,
 * followed by "Pembaruan Terkini" (unviewed updates from contacts) and
 * "Pembaruan yang Dilihat" (already viewed updates).
 */
@Composable
fun StoriesTabContent(
    stories: List<UserStory>,
    contacts: List<RosterContact>,
    myAvatar: ByteArray?,
    myNickname: String,
    myJid: String,
    onPublishStory: (text: String, mediaUri: Uri?, bgColor: String) -> Unit,
    onDeleteStory: (storyId: String) -> Unit,
    onMarkViewed: (storyId: String) -> Unit,
    onStartDirectChat: ((jid: String, name: String) -> Unit)? = null,
    onOpenTextComposer: (() -> Unit)? = null,
    onOpenMediaComposer: ((Uri) -> Unit)? = null,
    isPublishing: Boolean = false,
    paddingValues: PaddingValues = PaddingValues(0.dp)
) {
    val strings = com.ndregil.inchat.ui.i18n.LocalAppStrings.current
    val context = LocalContext.current
    var showCreateTextDialog by remember { mutableStateOf(false) }
    var selectedMediaUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreateMediaDialog by rememberSaveable { mutableStateOf(false) }
    var pendingCameraPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var showMediaSourceMenu by remember { mutableStateOf(false) }
    var viewingGroupTarget by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    val selectedMediaUri = remember(selectedMediaUriString) {
        selectedMediaUriString?.let { runCatching { Uri.parse(it) }.getOrNull() }
    }

    val openTextEditor: () -> Unit = {
        if (onOpenTextComposer != null) {
            onOpenTextComposer()
        } else {
            showCreateTextDialog = true
        }
    }

    val openMediaEditor: (Uri) -> Unit = { uri ->
        if (onOpenMediaComposer != null) {
            onOpenMediaComposer(uri)
        } else {
            selectedMediaUriString = uri.toString()
            showCreateMediaDialog = true
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            openMediaEditor(uri)
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val filePath = pendingCameraPhotoPath
        pendingCameraPhotoPath = null
        val capturedFile = filePath?.let { File(it) }
        if (success && capturedFile != null && capturedFile.exists() && capturedFile.length() > 0L) {
            val contentUri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    capturedFile
                )
            } catch (_: Exception) {
                Uri.fromFile(capturedFile)
            }
            openMediaEditor(contentUri)
        } else {
            if (capturedFile != null && capturedFile.exists() && capturedFile.length() == 0L) {
                runCatching { capturedFile.delete() }
            }
        }
    }

    fun launchCameraCapture() {
        try {
            val photoFile = File(context.cacheDir, "story_cam_${System.currentTimeMillis()}.jpg")
            val photoUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            pendingCameraPhotoPath = photoFile.absolutePath
            takePictureLauncher.launch(photoUri)
        } catch (_: Exception) {
            pendingCameraPhotoPath?.let { path -> runCatching { File(path).delete() } }
            pendingCameraPhotoPath = null
            Toast.makeText(context, strings.cameraUnavailable, Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCameraCapture()
        } else {
            Toast.makeText(context, strings.cameraPermissionRequiredToast, Toast.LENGTH_SHORT).show()
        }
    }

    // Separate my stories from contacts' stories
    val myStories = remember(stories, myJid) {
        stories.filter { it.isMine || it.senderJid.equals(myJid, ignoreCase = true) }
            .sortedByDescending { it.timestamp }
    }

    val totalMyUniqueViewers = remember(myStories) {
        myStories.flatMap { it.viewers }
            .distinctBy { it.viewerJid.trim().substringBefore("/").lowercase() }
            .size
    }

    // Group contacts stories by senderJid
    val contactStoryGroups = remember(stories, contacts, myJid) {
        val nonMyStories = stories.filter { !it.isMine && !it.senderJid.equals(myJid, ignoreCase = true) }
        val grouped = nonMyStories.groupBy { it.senderJid }
        grouped.map { (jid, storyList) ->
            val contact = contacts.find { it.jid.equals(jid, ignoreCase = true) }
            val name = contact?.displayName ?: storyList.firstOrNull()?.senderName ?: jid.substringBefore("@")
            val avatar = contact?.avatar
            ContactStoryGroup(
                jid = jid,
                name = name,
                avatar = avatar,
                stories = storyList.sortedBy { it.timestamp }
            )
        }.sortedByDescending { it.lastUpdated }
    }

    val activeViewingGroup = remember(viewingGroupTarget, myStories, contactStoryGroups, myAvatar, myJid, strings.myStatus) {
        val target = viewingGroupTarget ?: return@remember null
        if (target.second) {
            if (myStories.isEmpty()) null
            else ContactStoryGroup(
                jid = myJid,
                name = strings.myStatus,
                avatar = myAvatar,
                stories = myStories
            )
        } else {
            contactStoryGroups.find { it.jid.equals(target.first, ignoreCase = true) }
        }
    }

    val recentUpdates = remember(contactStoryGroups) {
        contactStoryGroups.filter { it.hasUnseen }
    }

    val viewedUpdates = remember(contactStoryGroups) {
        contactStoryGroups.filter { !it.hasUnseen }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 148.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section: Status Saya (My Status)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar with ring or + icon
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clickable {
                                    if (myStories.isNotEmpty()) {
                                        viewingGroupTarget = myJid to true
                                    } else {
                                        openTextEditor()
                                    }
                                }
                        ) {
                            val latestStory = myStories.firstOrNull()
                            if (latestStory != null) {
                                // Show story thumbnail as requested by user
                                StoryThumbnail(
                                    story = latestStory,
                                    modifier = Modifier.then(
                                        if (myStories.isNotEmpty()) Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        else Modifier
                                    )
                                )
                            } else {
                                // No stories: Show normal avatar
                                if (myAvatar != null) {
                                    val bmp = remember(myAvatar) {
                                        BitmapFactory.decodeByteArray(myAvatar, 0, myAvatar.size)
                                    }
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Avatar Saya",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        DefaultAvatar(name = myNickname, hasStories = false)
                                    }
                                } else {
                                    DefaultAvatar(name = myNickname, hasStories = false)
                                }
                            }

                            // Green '+' badge if no stories or to add more
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = strings.addStatusDesc,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (myStories.isNotEmpty()) {
                                        viewingGroupTarget = myJid to true
                                    } else {
                                        openTextEditor()
                                    }
                                }
                        ) {
                            Text(
                                text = strings.myStatus,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            if (myStories.isNotEmpty()) {
                                val count = myStories.size
                                val timeStr = formatStoryTime(myStories.first().timestamp)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "$count • $timeStr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        modifier = Modifier.testTag("badge_my_story_viewers")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Visibility,
                                                contentDescription = strings.viewedByLabel,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "$totalMyUniqueViewers",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = strings.tapToAddStatus,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Section: Pembaruan Terkini (Recent Updates)
            if (recentUpdates.isNotEmpty()) {
                item {
                    Text(
                        text = strings.recentUpdates,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }

                items(recentUpdates, key = { it.jid }) { group ->
                    ContactStoryRowItem(
                        group = group,
                        onClick = { viewingGroupTarget = group.jid to false }
                    )
                }
            }

            // Section: Pembaruan yang Dilihat (Viewed Updates)
            if (viewedUpdates.isNotEmpty()) {
                item {
                    Text(
                        text = strings.viewedUpdates,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    )
                }

                items(viewedUpdates, key = { it.jid }) { group ->
                    ContactStoryRowItem(
                        group = group,
                        onClick = { viewingGroupTarget = group.jid to false }
                    )
                }
            }

            // Empty State when no stories at all
            if (contactStoryGroups.isEmpty() && myStories.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Text(
                                text = strings.noStoriesYet,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = strings.noStoriesDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = openTextEditor,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(strings.createFirstStory)
                            }
                        }
                    }
                }
            }
        }

        // Floating action buttons for creating Text Story (Pencil) and Camera/Gallery Story
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmallFloatingActionButton(
                onClick = openTextEditor,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("btn_add_text_story")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = strings.writeStatusAction,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box {
                FloatingActionButton(
                    onClick = { showMediaSourceMenu = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                    modifier = Modifier.testTag("btn_add_image_story")
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = strings.sendPhotoStatusAction
                    )
                }

                DropdownMenu(
                    expanded = showMediaSourceMenu,
                    onDismissRequest = { showMediaSourceMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(strings.takePhotoCamera) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.testTag("menu_story_camera"),
                        onClick = {
                            showMediaSourceMenu = false
                            val hasCamPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasCamPerm) {
                                launchCameraCapture()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.chooseFromGallery) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.testTag("menu_story_gallery"),
                        onClick = {
                            showMediaSourceMenu = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                }
            }
        }
    }

    // Modal Dialog: Create Text Story
    if (showCreateTextDialog) {
        CreateTextStoryDialog(
            onDismiss = { showCreateTextDialog = false },
            onPublish = { text, bgColor ->
                onPublishStory(text, null, bgColor)
                showCreateTextDialog = false
            },
            isPublishing = isPublishing
        )
    }

    // Modal Dialog: Create Media Story with Caption
    if (showCreateMediaDialog && selectedMediaUri != null) {
        val targetMediaUri = selectedMediaUri
        CreateMediaStoryDialog(
            imageUri = targetMediaUri,
            onDismiss = {
                showCreateMediaDialog = false
                selectedMediaUriString = null
            },
            onPublish = { caption ->
                showCreateMediaDialog = false
                selectedMediaUriString = null
                onPublishStory(caption, targetMediaUri, "#0284C7")
            },
            isPublishing = isPublishing
        )
    }

    // Full-screen Story Viewer (WhatsApp style with animated progress bars)
    if (activeViewingGroup != null) {
        StoryViewerDialog(
            storyGroup = activeViewingGroup,
            onDismiss = { viewingGroupTarget = null },
            onMarkViewed = onMarkViewed,
            onDeleteStory = onDeleteStory,
            onReplyClick = { jid, name ->
                viewingGroupTarget = null
                onStartDirectChat?.invoke(jid, name)
            }
        )
    }
}

@Composable
fun ContactStoryRowItem(
    group: ContactStoryGroup,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Story ring circle
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .border(
                    width = 2.5.dp,
                    color = if (group.hasUnseen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                )
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            if (group.avatar != null) {
                val bmp = remember(group.avatar) {
                    BitmapFactory.decodeByteArray(group.avatar, 0, group.avatar.size)
                }
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = group.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    DefaultAvatar(name = group.name, hasStories = false)
                }
            } else {
                DefaultAvatar(name = group.name, hasStories = false)
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (group.hasUnseen) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formatStoryTime(group.lastUpdated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StoryThumbnail(
    story: com.ndregil.inchat.data.model.UserStory,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(
                if (story.mediaType == com.ndregil.inchat.data.model.StoryMediaType.TEXT) {
                    try {
                        Color(android.graphics.Color.parseColor(story.backgroundColorHex))
                    } catch (_: Exception) {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                } else {
                    Color.LightGray
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (story.mediaType == com.ndregil.inchat.data.model.StoryMediaType.IMAGE && story.mediaUrl != null) {
            AsyncImage(
                model = story.mediaUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (story.mediaType == com.ndregil.inchat.data.model.StoryMediaType.TEXT) {
            Text(
                text = story.text.take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DefaultAvatar(name: String, hasStories: Boolean) {
    val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StoryComposerOverlay(viewModel: XmppViewModel) {
    val composerState by viewModel.storyComposerState.collectAsStateWithLifecycle()
    val isPublishing by viewModel.isPublishingStory.collectAsStateWithLifecycle()

    AnimatedVisibility(
        visible = composerState !is XmppViewModel.StoryComposerState.None,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        when (val state = composerState) {
            is XmppViewModel.StoryComposerState.TextStory -> {
                CreateTextStoryContent(
                    onDismiss = { viewModel.closeStoryComposer() },
                    onPublish = { text, bgColor ->
                        viewModel.publishStory(text, null, bgColor)
                        viewModel.closeStoryComposer()
                    },
                    isPublishing = isPublishing
                )
            }
            is XmppViewModel.StoryComposerState.MediaStory -> {
                CreateMediaStoryContent(
                    imageUri = state.imageUri,
                    onDismiss = { viewModel.closeStoryComposer() },
                    onPublish = { caption ->
                        viewModel.publishStory(caption, state.imageUri, "#0284C7")
                        viewModel.closeStoryComposer()
                    },
                    isPublishing = isPublishing
                )
            }
            is XmppViewModel.StoryComposerState.None -> Unit
        }
    }
}

/**
 * Text Story Creation Screen (color selector, big text input)
 */
@Composable
fun CreateTextStoryDialog(
    onDismiss: () -> Unit,
    onPublish: (text: String, bgColor: String) -> Unit,
    isPublishing: Boolean
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogView = LocalView.current
        SideEffect {
            (dialogView.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                @Suppress("DEPRECATION")
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
        }
        CreateTextStoryContent(
            onDismiss = onDismiss,
            onPublish = onPublish,
            isPublishing = isPublishing
        )
    }
}

@Composable
fun CreateTextStoryContent(
    onDismiss: () -> Unit,
    onPublish: (text: String, bgColor: String) -> Unit,
    isPublishing: Boolean
) {
    val strings = com.ndregil.inchat.ui.i18n.LocalAppStrings.current
    val presetColors = listOf(
        "#0284C7", "#059669", "#7C3AED", "#DB2777",
        "#D97706", "#DC2626", "#1E293B", "#0D9488"
    )
    var selectedColorIndex by remember { mutableIntStateOf(0) }
    var textContent by remember { mutableStateOf("") }

    BackHandler(onBack = onDismiss)

    val currentColorHex = presetColors[selectedColorIndex]
    val currentColor = try {
        Color(android.graphics.Color.parseColor(currentColorHex))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {},
        color = currentColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(16.dp)
        ) {
            // Top bar: Close & Color picker cycle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = strings.cancel, tint = Color.White)
                }

                // Cycle color button
                IconButton(
                    onClick = {
                        selectedColorIndex = (selectedColorIndex + 1) % presetColors.size
                    }
                ) {
                    Icon(Icons.Default.Palette, contentDescription = strings.changeColor, tint = Color.White)
                }
            }

            // Center big text (scrollable when keyboard reduces height)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                OutlinedTextField(
                    value = textContent,
                    onValueChange = { if (it.length <= 300) textContent = it },
                    placeholder = {
                        Text(
                            strings.typeStatusPlaceholder,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 24.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .testTag("input_story_text"),
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Color.White
                    )
                )
            }

            // Bottom bar: character count and publish FAB
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp, start = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${textContent.length}/300",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )

                FloatingActionButton(
                    onClick = {
                        if (textContent.isNotBlank() && !isPublishing) {
                            onPublish(textContent.trim(), currentColorHex)
                        }
                    },
                    containerColor = Color.White,
                    contentColor = currentColor,
                    modifier = Modifier.testTag("btn_publish_text_story")
                ) {
                    if (isPublishing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = currentColor)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = strings.sendStatus)
                    }
                }
            }
        }
    }
}

/**
 * Media Story Creation Screen (preview selected photo, add caption)
 */
@Composable
fun CreateMediaStoryDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onPublish: (caption: String) -> Unit,
    isPublishing: Boolean
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogView = LocalView.current
        SideEffect {
            (dialogView.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                @Suppress("DEPRECATION")
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
        }
        CreateMediaStoryContent(
            imageUri = imageUri,
            onDismiss = onDismiss,
            onPublish = onPublish,
            isPublishing = isPublishing
        )
    }
}

@Composable
fun CreateMediaStoryContent(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onPublish: (caption: String) -> Unit,
    isPublishing: Boolean
) {
    val strings = com.ndregil.inchat.ui.i18n.LocalAppStrings.current
    var caption by remember(imageUri) { mutableStateOf("") }

    BackHandler(onBack = onDismiss)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {},
        color = Color.Black
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Photo Preview & Top Bar share the upper flexible space above the caption bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "Preview Status",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = strings.cancel, tint = Color.White)
                    }
                }
            }

            // Bottom Caption & Send Bar stays pinned above keyboard & navigation bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.72f), Color.Black.copy(alpha = 0.96f))
                        )
                    )
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { if (it.length <= 500) caption = it },
                        placeholder = {
                            Text(
                                strings.addCaptionPlaceholder,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_story_caption"),
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Default
                        ),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF1F2937).copy(alpha = 0.92f),
                            unfocusedContainerColor = Color(0xFF1F2937).copy(alpha = 0.85f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White.copy(alpha = 0.45f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                            cursorColor = Color.White
                        ),
                        maxLines = 4
                    )

                    FloatingActionButton(
                        onClick = { if (!isPublishing) onPublish(caption.trim()) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("btn_publish_media_story")
                    ) {
                        if (isPublishing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = strings.sendStatus)
                        }
                    }
                }
            }
        }
    }
}

/**
 * WhatsApp-style Story Viewer with top timer progress bars, tapping left/right to navigate,
 * auto-progress after 5 seconds, reply button, and delete for own stories.
 */
@Composable
fun StoryViewerDialog(
    storyGroup: ContactStoryGroup,
    onDismiss: () -> Unit,
    onMarkViewed: (storyId: String) -> Unit,
    onDeleteStory: (storyId: String) -> Unit,
    onReplyClick: ((jid: String, name: String) -> Unit)? = null
) {
    val strings = com.ndregil.inchat.ui.i18n.LocalAppStrings.current
    var currentIndex by remember { mutableIntStateOf(0) }
    val stories = storyGroup.stories
    val currentStory = stories.getOrNull(currentIndex) ?: stories.firstOrNull()

    // Mark current story as viewed
    LaunchedEffect(currentStory?.id) {
        currentStory?.let {
            if (!it.isViewed && !it.isMine) {
                onMarkViewed(it.id)
            }
        }
    }

    // Auto-advance timer (5 seconds per story)
    var progress by remember(currentIndex) { mutableFloatStateOf(0f) }
    var isViewersSheetOpen by remember { mutableStateOf(false) }
    val isPaused = isViewersSheetOpen

    LaunchedEffect(currentIndex, isPaused) {
        if (!isPaused) {
            val totalSteps = 100
            for (step in 1..totalSteps) {
                delay(50) // 50ms * 100 = 5000ms = 5 seconds
                progress = step / 100f
            }
            if (currentIndex < stories.size - 1) {
                currentIndex++
            } else {
                onDismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            if (currentStory == null) {
                onDismiss()
                return@Surface
            }

            val storyBgColor = remember(currentStory.backgroundColorHex) {
                try {
                    Color(android.graphics.Color.parseColor(currentStory.backgroundColorHex))
                } catch (_: Exception) {
                    Color(0xFF0284C7)
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                // Story Content View: Image or Text
                if (currentStory.mediaType == StoryMediaType.IMAGE && currentStory.mediaUrl != null) {
                    AsyncImage(
                        model = currentStory.mediaUrl,
                        contentDescription = "Foto Status",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    // Full Screen Text Story
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(storyBgColor)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentStory.text,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                }

                // Invisible touch zones for previous / next navigation
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable {
                                if (currentIndex > 0) {
                                    currentIndex--
                                    progress = 0f
                                }
                            }
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable {
                                if (currentIndex < stories.size - 1) {
                                    currentIndex++
                                    progress = 0f
                                } else {
                                    onDismiss()
                                }
                            }
                    )
                }

                // Top Header Overlay: Segmented Progress Bars & User Info
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Progress segments
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        stories.indices.forEach { idx ->
                            val segProgress = when {
                                idx < currentIndex -> 1f
                                idx == currentIndex -> progress
                                else -> 0f
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.35f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(segProgress)
                                        .height(3.dp)
                                        .background(Color.White)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Contact Header: Back button, Avatar, Name, Time, and Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.close, tint = Color.White)
                        }

                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (storyGroup.avatar != null) {
                                val bmp = remember(storyGroup.avatar) {
                                    BitmapFactory.decodeByteArray(storyGroup.avatar, 0, storyGroup.avatar.size)
                                }
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = storyGroup.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    DefaultAvatar(name = storyGroup.name, hasStories = false)
                                }
                            } else {
                                DefaultAvatar(name = storyGroup.name, hasStories = false)
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = storyGroup.name,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatStoryTime(currentStory.timestamp),
                                color = Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        // If it's my story: Delete button
                        if (currentStory.isMine) {
                            IconButton(
                                onClick = {
                                    onDeleteStory(currentStory.id)
                                    if (stories.size <= 1) {
                                        onDismiss()
                                    } else {
                                        if (currentIndex >= stories.size - 1) {
                                            currentIndex = stories.size - 2
                                        }
                                        progress = 0f
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = strings.deleteStatus, tint = Color.White)
                            }
                        }
                    }
                }

                // Bottom Section: Optional Photo Caption stacked cleanly ABOVE Viewers / Reply bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (currentStory.mediaType == StoryMediaType.IMAGE && currentStory.text.isNotBlank()) {
                        Text(
                            text = currentStory.text,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (currentStory.isMine) {
                        // Interactive viewer count bar ("Dilihat oleh X orang")
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color.Black.copy(alpha = 0.72f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .clickable { isViewersSheetOpen = true }
                                .testTag("btn_story_viewers_bar")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = strings.viewedByLabel,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${strings.viewedByLabel} ${currentStory.viewers.size} ${strings.viewersUnit}",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else if (onReplyClick != null) {
                        Button(
                            onClick = { onReplyClick(storyGroup.jid, storyGroup.name) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black.copy(alpha = 0.65f),
                                contentColor = Color.White
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${strings.replyStatusTo} ${storyGroup.name}", fontSize = 13.sp)
                        }
                    }
                }

                // Expandable Viewers List Sheet inside the Story Viewer
                if (isViewersSheetOpen && currentStory.isMine) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable { isViewersSheetOpen = false }
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .clickable(enabled = false) {},
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 20.dp, vertical = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Visibility,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "${strings.viewedByLabel} ${currentStory.viewers.size} ${strings.viewersUnit}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    IconButton(onClick = { isViewersSheetOpen = false }) {
                                        Icon(Icons.Default.Close, contentDescription = strings.close)
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                                if (currentStory.viewers.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Belum ada yang melihat status ini.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(260.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(currentStory.viewers, key = { it.viewerJid }) { viewer ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    val viewerBmp = remember(viewer.viewerAvatar) {
                                                        viewer.viewerAvatar?.takeIf { it.isNotEmpty() }?.let {
                                                            try {
                                                                BitmapFactory.decodeByteArray(it, 0, it.size)
                                                            } catch (_: Exception) {
                                                                null
                                                            }
                                                        }
                                                    }
                                                    if (viewerBmp != null) {
                                                        Image(
                                                            bitmap = viewerBmp.asImageBitmap(),
                                                            contentDescription = viewer.viewerName,
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                    } else {
                                                        DefaultAvatar(name = viewer.viewerName, hasStories = false)
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = viewer.viewerName,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = viewer.viewerJid,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Text(
                                                    text = formatStoryTime(viewer.viewedAt),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
}

@Composable
private fun formatStoryTime(timestamp: Long): String {
    val strings = com.ndregil.inchat.ui.i18n.LocalAppStrings.current
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minutes = diff / (60 * 1000)
    val hours = diff / (60 * 60 * 1000)

    return when {
        minutes < 1 -> strings.justNow
        minutes < 60 -> "$minutes ${strings.minutesAgoSuffix}"
        hours < 24 -> "$hours ${strings.hoursAgoSuffix}"
        else -> SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
