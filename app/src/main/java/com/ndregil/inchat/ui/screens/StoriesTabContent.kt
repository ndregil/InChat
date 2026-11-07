package com.ndregil.inchat.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.ndregil.inchat.data.model.ContactStoryGroup
import com.ndregil.inchat.data.model.RosterContact
import com.ndregil.inchat.data.model.StoryMediaType
import com.ndregil.inchat.data.model.UserStory
import kotlinx.coroutines.delay
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
    isPublishing: Boolean = false,
    paddingValues: PaddingValues = PaddingValues(0.dp)
) {
    var showCreateTextDialog by remember { mutableStateOf(false) }
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var showCreateMediaDialog by remember { mutableStateOf(false) }
    var viewingStoryGroup by remember { mutableStateOf<ContactStoryGroup?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedMediaUri = uri
            showCreateMediaDialog = true
        }
    }

    // Separate my stories from contacts' stories
    val myStories = remember(stories, myJid) {
        stories.filter { it.isMine || it.senderJid.equals(myJid, ignoreCase = true) }
            .sortedByDescending { it.timestamp }
    }

    // Group contacts stories by senderJid
    val contactStoryGroups = remember(stories, contacts, myJid) {
        val nonMyStories = stories.filter { !it.isMine && !it.senderJid.equals(myJid, ignoreCase = true) }
        val grouped = nonMyStories.groupBy { it.senderJid }
        grouped.map { (jid, storyList) ->
            val contact = contacts.find { it.jid.equals(jid, ignoreCase = true) }
            val name = contact?.displayName ?: storyList.firstOrNull()?.senderName ?: jid.substringBefore("@")
            val avatar = contact?.avatar ?: storyList.firstOrNull()?.senderAvatar
            ContactStoryGroup(
                jid = jid,
                name = name,
                avatar = avatar,
                stories = storyList.sortedBy { it.timestamp }
            )
        }.sortedByDescending { it.lastUpdated }
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
            contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
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
                                        viewingStoryGroup = ContactStoryGroup(
                                            jid = myJid,
                                            name = "Status Saya",
                                            avatar = myAvatar,
                                            stories = myStories
                                        )
                                    } else {
                                        showCreateTextDialog = true
                                    }
                                }
                        ) {
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
                                            .clip(CircleShape)
                                            .then(
                                                if (myStories.isNotEmpty()) Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                                else Modifier
                                            ),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    DefaultAvatar(name = myNickname, hasStories = myStories.isNotEmpty())
                                }
                            } else {
                                DefaultAvatar(name = myNickname, hasStories = myStories.isNotEmpty())
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
                                    contentDescription = "Tambah Status",
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
                                        viewingStoryGroup = ContactStoryGroup(
                                            jid = myJid,
                                            name = "Status Saya",
                                            avatar = myAvatar,
                                            stories = myStories
                                        )
                                    } else {
                                        showCreateTextDialog = true
                                    }
                                }
                        ) {
                            Text(
                                text = "Status Saya",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (myStories.isNotEmpty()) {
                                    val count = myStories.size
                                    val timeStr = formatStoryTime(myStories.first().timestamp)
                                    "$count status aktif • $timeStr"
                                } else {
                                    "Ketuk untuk menambahkan pembaruan status"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Action buttons: Text story and Camera/Gallery story
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { showCreateTextDialog = true },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .testTag("btn_add_text_story")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Tulis Status",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = { photoPickerLauncher.launch("image/*") },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .testTag("btn_add_image_story")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Kirim Foto Status",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
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
                        text = "Pembaruan Terkini",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }

                items(recentUpdates, key = { it.jid }) { group ->
                    ContactStoryRowItem(
                        group = group,
                        onClick = { viewingStoryGroup = group }
                    )
                }
            }

            // Section: Pembaruan yang Dilihat (Viewed Updates)
            if (viewedUpdates.isNotEmpty()) {
                item {
                    Text(
                        text = "Pembaruan yang Dilihat",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    )
                }

                items(viewedUpdates, key = { it.jid }) { group ->
                    ContactStoryRowItem(
                        group = group,
                        onClick = { viewingStoryGroup = group }
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
                                text = "Belum Ada Status",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Bagikan momen harian atau pembaruan teks dengan kontak Anda seperti di WhatsApp. Status otomatis aktif selama 24 jam.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { showCreateTextDialog = true },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Buat Status Pertama")
                            }
                        }
                    }
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
        CreateMediaStoryDialog(
            imageUri = selectedMediaUri!!,
            onDismiss = {
                showCreateMediaDialog = false
                selectedMediaUri = null
            },
            onPublish = { caption ->
                onPublishStory(caption, selectedMediaUri, "#0284C7")
                showCreateMediaDialog = false
                selectedMediaUri = null
            },
            isPublishing = isPublishing
        )
    }

    // Full-screen Story Viewer (WhatsApp style with animated progress bars)
    if (viewingStoryGroup != null) {
        StoryViewerDialog(
            storyGroup = viewingStoryGroup!!,
            onDismiss = { viewingStoryGroup = null },
            onMarkViewed = onMarkViewed,
            onDeleteStory = onDeleteStory,
            onReplyClick = { jid, name ->
                viewingStoryGroup = null
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

/**
 * Text Story Creation Screen (color selector, big text input)
 */
@Composable
fun CreateTextStoryDialog(
    onDismiss: () -> Unit,
    onPublish: (text: String, bgColor: String) -> Unit,
    isPublishing: Boolean
) {
    val presetColors = listOf(
        "#0284C7", "#059669", "#7C3AED", "#DB2777",
        "#D97706", "#DC2626", "#1E293B", "#0D9488"
    )
    var selectedColorIndex by remember { mutableIntStateOf(0) }
    var textContent by remember { mutableStateOf("") }

    val currentColorHex = presetColors[selectedColorIndex]
    val currentColor = try {
        Color(android.graphics.Color.parseColor(currentColorHex))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = currentColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                // Top bar: Close & Color picker cycle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Batal", tint = Color.White)
                    }

                    // Cycle color button
                    IconButton(
                        onClick = {
                            selectedColorIndex = (selectedColorIndex + 1) % presetColors.size
                        }
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = "Ganti Warna", tint = Color.White)
                    }
                }

                // Center big text
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    OutlinedTextField(
                        value = textContent,
                        onValueChange = { if (it.length <= 300) textContent = it },
                        placeholder = {
                            Text(
                                "Ketik status...",
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
                        .padding(bottom = 16.dp, start = 8.dp, end = 8.dp),
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
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Kirim Status")
                        }
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
    var caption by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Photo Preview
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
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Batal", tint = Color.White)
                    }
                }

                // Bottom Caption & Send Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        placeholder = { Text("Tambahkan keterangan...", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_story_caption"),
                        shape = RoundedCornerShape(24.dp),
                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.2f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.15f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true
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
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Kirim Status")
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
    var isPaused by remember { mutableStateOf(false) }

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

                    // Optional Caption on photo
                    if (currentStory.text.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                    )
                                )
                                .padding(horizontal = 20.dp, vertical = 24.dp)
                        ) {
                            Text(
                                text = currentStory.text,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
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

                // Invisible touch zones for previous / next navigation & pause on hold
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tutup", tint = Color.White)
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
                                Icon(Icons.Default.Delete, contentDescription = "Hapus Status", tint = Color.White)
                            }
                        }
                    }
                }

                // Bottom Reply Bar (if viewing a contact's story)
                if (!currentStory.isMine && onReplyClick != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    ) {
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
                            Text("Balas Status ke ${storyGroup.name}", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun formatStoryTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minutes = diff / (60 * 1000)
    val hours = diff / (60 * 60 * 1000)

    return when {
        minutes < 1 -> "Baru saja"
        minutes < 60 -> "$minutes menit yang lalu"
        hours < 24 -> "$hours jam yang lalu"
        else -> SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
