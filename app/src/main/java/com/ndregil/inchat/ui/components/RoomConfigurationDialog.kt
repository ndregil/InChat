package com.ndregil.inchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.ndregil.inchat.data.model.XmppMessage
import com.ndregil.inchat.data.model.MucAffiliate
import com.ndregil.inchat.data.model.OccupantRole
import com.ndregil.inchat.data.model.OccupantAffiliation
import org.jivesoftware.smackx.muc.RoomInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomConfigurationDialog(
    roomJid: String,
    initialInfo: RoomInfo?,
    moderationMessages: List<XmppMessage> = emptyList(),
    isLoading: Boolean = false,
    isPersonalOnly: Boolean = false,
    onLoadAffiliations: () -> Unit = {},
    ownersList: List<MucAffiliate> = emptyList(),
    adminsList: List<MucAffiliate> = emptyList(),
    membersList: List<MucAffiliate> = emptyList(),
    outcastsList: List<MucAffiliate> = emptyList(),
    isLoadingAffiliations: Boolean = false,
    onGrantOwnership: (String) -> Unit = {},
    onGrantAdmin: (String) -> Unit = {},
    onGrantMembership: (String) -> Unit = {},
    onBanUser: (String, String?) -> Unit = { _, _ -> },
    onRevokeAffiliation: (String) -> Unit = {},
    myRole: OccupantRole = OccupantRole.NONE,
    myAffiliation: OccupantAffiliation = OccupantAffiliation.NONE,
    initialNotificationMode: Int = 0,
    onDismiss: () -> Unit,
    onUpdateNickname: (String) -> Unit = {},
    onUpdateTopic: (String) -> Unit = {},
    onUpdateRoomAvatar: (android.net.Uri) -> Unit = {},
    roomAvatar: ByteArray? = null,
    isNicknameOnly: Boolean = false,
    onSaveConfig: (
        roomName: String?,
        description: String?,
        subject: String?,
        isPersistent: Boolean,
        isPublic: Boolean,
        isMembersOnly: Boolean,
        isModerated: Boolean,
        allowSubjectChange: Boolean,
        password: String?,
        notificationMode: Int,
        maxUsers: Int?,
        enableLogging: Boolean,
        allowInvites: Boolean,
        whois: String?
    ) -> Unit
) {
    val strings = com.ndregil.inchat.ui.i18n.LocalAppStrings.current
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Settings, 1 = Moderation

    LaunchedEffect(activeTab) {
        if (activeTab == 1) {
            onLoadAffiliations()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 680.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (activeTab == 0) Icons.Default.AdminPanelSettings else Icons.Default.Gavel,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isNicknameOnly) strings.changeNicknameTitle else if (isPersonalOnly) strings.changeNicknameNotifTitle else if (activeTab == 0) strings.mucConfigTitle else strings.adminModerationTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = roomJid,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (!isPersonalOnly && !isNicknameOnly) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    TabRow(
                        selectedTabIndex = activeTab,
                        containerColor = Color.Transparent,
                        divider = {}
                    ) {
                        Tab(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 },
                            text = { Text(strings.settingsTabLabel, fontSize = 13.sp) },
                            icon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            text = { Text(strings.tabModeration, fontSize = 13.sp) },
                            icon = { Icon(Icons.Default.Gavel, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
            }
        },
        text = {
            if (isPersonalOnly || activeTab == 0) {
                RoomConfigurationContent(
                    initialInfo = initialInfo,
                    initialNotificationMode = initialNotificationMode,
                    isLoading = isLoading,
                    isPersonalOnly = isPersonalOnly,
                    isNicknameOnly = isNicknameOnly,
                    myRole = myRole,
                    myAffiliation = myAffiliation,
                    onSaveConfig = onSaveConfig,
                    onUpdateNickname = onUpdateNickname,
                    onUpdateTopic = onUpdateTopic,
                    onUpdateRoomAvatar = onUpdateRoomAvatar,
                    roomAvatar = roomAvatar
                )
            } else {
                val hasModerationAccess = myRole == OccupantRole.OWNER || myRole == OccupantRole.ADMIN ||
                        myAffiliation == OccupantAffiliation.OWNER || myAffiliation == OccupantAffiliation.ADMIN
                if (hasModerationAccess) {
                    ModerationTabContent(
                        owners = ownersList,
                        admins = adminsList,
                        members = membersList,
                        outcasts = outcastsList,
                        isLoading = isLoadingAffiliations,
                        isOwnerOrAdmin = hasModerationAccess,
                        onGrantOwnership = onGrantOwnership,
                        onGrantAdmin = onGrantAdmin,
                        onGrantMembership = onGrantMembership,
                        onBanUser = onBanUser,
                        onRevokeAffiliation = onRevokeAffiliation
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Lock, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(12.dp))
                            Text(strings.restrictedAccessTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(strings.ownerOnlyFeatureNotice, textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_room_config_dialog")
            ) {
                Text(strings.close)
            }
        }
    )
}

@Composable
fun RoomConfigurationContent(
    initialInfo: RoomInfo?,
    initialNotificationMode: Int = 0,
    isLoading: Boolean = false,
    isPersonalOnly: Boolean = false,
    myRole: OccupantRole = OccupantRole.NONE,
    myAffiliation: OccupantAffiliation = OccupantAffiliation.NONE,
    onSaveConfig: (
        roomName: String?,
        description: String?,
        subject: String?,
        isPersistent: Boolean,
        isPublic: Boolean,
        isMembersOnly: Boolean,
        isModerated: Boolean,
        allowSubjectChange: Boolean,
        password: String?,
        notificationMode: Int,
        maxUsers: Int?,
        enableLogging: Boolean,
        allowInvites: Boolean,
        whois: String?
    ) -> Unit,
    onUpdateNickname: (String) -> Unit = {},
    onUpdateTopic: (String) -> Unit = {},
    onUpdateRoomAvatar: (android.net.Uri) -> Unit = {},
    roomAvatar: ByteArray? = null,
    isNicknameOnly: Boolean = false
) {
    val strings = com.ndregil.inchat.ui.i18n.LocalAppStrings.current
    val isOwner = myRole == OccupantRole.OWNER || myAffiliation == OccupantAffiliation.OWNER
    
    var roomName by remember(initialInfo) { mutableStateOf(initialInfo?.name ?: "") }
    var description by remember(initialInfo) { mutableStateOf(initialInfo?.description ?: "") }
    var subject by remember(initialInfo) { mutableStateOf(initialInfo?.subject ?: initialInfo?.description ?: "") }
    var isPersistent by remember(initialInfo) { mutableStateOf(initialInfo?.isPersistent ?: true) }
    var isPublic by remember(initialInfo) { mutableStateOf(!(initialInfo?.isPasswordProtected ?: false)) }
    var isMembersOnly by remember(initialInfo) { mutableStateOf(initialInfo?.isMembersOnly ?: false) }
    var isModerated by remember(initialInfo) { mutableStateOf(initialInfo?.isModerated ?: false) }
    var allowSubjectChange by remember(initialInfo) { mutableStateOf(true) }
    
    val canChangeSubject by remember(isOwner, myRole, myAffiliation, allowSubjectChange) {
        derivedStateOf { isOwner || myRole == OccupantRole.ADMIN || myAffiliation == OccupantAffiliation.ADMIN || allowSubjectChange }
    }

    var password by remember(initialInfo) { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var notificationMode by remember(initialNotificationMode) { mutableIntStateOf(initialNotificationMode) }
    var myNickname by remember { mutableStateOf("") }

    val avatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onUpdateRoomAvatar(it) }
    }

    // New MUC Server configuration fields
    var maxUsers by remember(initialInfo) { mutableStateOf("0") }
    var enableLogging by remember(initialInfo) { mutableStateOf(true) }
    var allowInvites by remember(initialInfo) { mutableStateOf(true) }
    var whois by remember(initialInfo) { mutableStateOf("anyone") }
    var maxUsersExpanded by remember { mutableStateOf(false) }

    val maxUsersOptions = listOf(
        "0" to strings.unlimitedServerDefault,
        "10" to "${strings.maxParticipantsPrefix} 10 ${strings.participantsUnit}",
        "20" to "${strings.maxParticipantsPrefix} 20 ${strings.participantsUnit}",
        "30" to "${strings.maxParticipantsPrefix} 30 ${strings.participantsUnit}",
        "50" to "${strings.maxParticipantsPrefix} 50 ${strings.participantsUnit}",
        "100" to "${strings.maxParticipantsPrefix} 100 ${strings.participantsUnit}",
        "200" to "${strings.maxParticipantsPrefix} 200 ${strings.participantsUnit}",
        "500" to "${strings.maxParticipantsPrefix} 500 ${strings.participantsUnit}",
        "1000" to "${strings.maxParticipantsPrefix} 1000 ${strings.participantsUnit}"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Personal Identity (Nickname)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.myNicknameInRoomTitle,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = myNickname,
                        onValueChange = { myNickname = it },
                        label = { Text(strings.changeNicknameTitle) },
                        placeholder = { Text(strings.newNicknamePlaceholder) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("room_nickname_input"),
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) }
                    )

                    Button(
                        onClick = {
                            if (myNickname.isNotBlank()) {
                                onUpdateNickname(myNickname)
                                myNickname = ""
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text(strings.changeBtn)
                    }
                }
                
                Text(
                    text = strings.nicknameChangeHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!isPersonalOnly && !isNicknameOnly) {
            if (isOwner) {
                // Section: Avatar
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val avatarBitmap = remember(roomAvatar) {
                            roomAvatar?.let {
                                try {
                                    BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap()
                                } catch (e: Exception) {
                                    null
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatarBitmap != null) {
                                Image(
                                    bitmap = avatarBitmap,
                                    contentDescription = strings.roomAvatarDescLabel,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.roomAvatarTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (roomAvatar != null) strings.customAvatarActive else strings.useRoomCustomAvatar,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { avatarLauncher.launch("image/*") },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.changeAvatarBtn)
                        }
                    }
                }
            }

            // Section 1: Room Identity & Description
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.roomIdentityHeader,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }

                    if (isOwner) {
                        OutlinedTextField(
                            value = roomName,
                            onValueChange = { roomName = it },
                            label = { Text(strings.mucRoomNameLabel) },
                            placeholder = { Text(strings.mucRoomNamePlaceholder) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("room_name_input"),
                            leadingIcon = { Icon(Icons.Default.Group, contentDescription = null) }
                        )

                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text(strings.roomDescLabel) },
                            placeholder = { Text(strings.roomDescPlaceholder) },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("room_desc_input"),
                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) }
                        )
                    } else {
                        Text(text = roomName.ifBlank { strings.noNameFallback }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(text = description.ifBlank { strings.noDescFallback }, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }


            // Section 2: Subject Management (XEP-0045)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Topic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.roomSubjectHeader,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }

                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text(strings.roomSubjectLabel) },
                        placeholder = { Text(strings.roomSubjectPlaceholder) },
                        singleLine = true,
                        enabled = canChangeSubject,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("room_subject_input"),
                        leadingIcon = { Icon(Icons.Default.Campaign, contentDescription = null) }
                    )

                    if (canChangeSubject) {
                        Button(
                            onClick = {
                                if (subject.isNotBlank()) {
                                    onUpdateTopic(subject)
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.changeSubjectBtn)
                        }
                    } else {
                        Text(
                            text = strings.topicLockedByOwner,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (isOwner) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.allowMembersChangeSubject, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    text = if (allowSubjectChange) strings.allowSubjectTrueDesc else strings.allowSubjectFalseDesc,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = allowSubjectChange,
                                onCheckedChange = { allowSubjectChange = it },
                                modifier = Modifier.testTag("switch_allow_subject_change")
                            )
                        }
                    }
                }
            }


                // Section 3: Server MUC Rules - Batas Jumlah Peserta & Visibilitas
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.serverRulesHeader,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }

                        // Batas Jumlah Peserta (muc#roomconfig_maxusers)
                        Column {
                            Text(strings.maxParticipantsTitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(strings.maxParticipantsDesc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { maxUsersExpanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    val currentLabel = maxUsersOptions.find { it.first == maxUsers }?.second ?: strings.unlimitedServerDefault
                                    Text(currentLabel, modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = maxUsersExpanded,
                                    onDismissRequest = { maxUsersExpanded = false }
                                ) {
                                    maxUsersOptions.forEach { (valStr, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                maxUsers = valStr
                                                maxUsersExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                        // Visibilitas Real JID (muc#roomconfig_whois)
                        Column {
                            Text(strings.whoCanSeeRealJidTitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { whois = "anyone" }
                                    .padding(vertical = 2.dp)
                            ) {
                                RadioButton(selected = whois == "anyone", onClick = { whois = "anyone" })
                                Spacer(Modifier.width(8.dp))
                                Text(strings.whoisAnyone, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { whois = "moderators" }
                                    .padding(vertical = 2.dp)
                            ) {
                                RadioButton(selected = whois == "moderators", onClick = { whois = "moderators" })
                                Spacer(Modifier.width(8.dp))
                                Text(strings.whoisModerators, style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                        // Izinkan Undangan (muc#roomconfig_allowinvites)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.allowInvitesTitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(strings.allowInvitesDesc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = allowInvites, onCheckedChange = { allowInvites = it })
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                        // Catat Log Percakapan (muc#roomconfig_enablelogging)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.enableLoggingTitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(strings.enableLoggingDesc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = enableLogging, onCheckedChange = { enableLogging = it })
                        }
                    }
                }

                // Section 4: Privacy, Access & Security Settings
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.privacySecurityHeader,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }

                        // Directory Public Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.publicDirectoryTitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    text = strings.publicDirectoryDesc,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isPublic,
                                onCheckedChange = { isPublic = it },
                                modifier = Modifier.testTag("switch_public_room")
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                        // Members Only Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.membersOnlyTitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    text = strings.membersOnlyDesc,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isMembersOnly,
                                onCheckedChange = { isMembersOnly = it },
                                modifier = Modifier.testTag("switch_members_only")
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                        // Moderated Room Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.moderatedRoomTitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    text = strings.moderatedRoomDesc,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isModerated,
                                onCheckedChange = { isModerated = it },
                                modifier = Modifier.testTag("switch_moderated_room")
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                        // Persistent Room Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.persistentRoomTitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    text = strings.persistentRoomDesc,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isPersistent,
                                onCheckedChange = { isPersistent = it },
                                modifier = Modifier.testTag("switch_persistent_room")
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                        // Password Protected Field
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(strings.roomPasswordConfigLabel) },
                            placeholder = { Text(strings.roomPasswordConfigPlaceholder) },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("room_password_input"),
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = strings.togglePasswordDesc
                                    )
                                }
                            }
                        )
                    }
                }

                // Save Button Action
                Button(
                    onClick = {
                        val parsedMaxUsers = maxUsers.toIntOrNull()
                        onSaveConfig(
                            roomName.ifBlank { null },
                            description.ifBlank { null },
                            subject.ifBlank { null },
                            isPersistent,
                            isPublic,
                            isMembersOnly,
                            isModerated,
                            allowSubjectChange,
                            password.ifBlank { null },
                            notificationMode,
                            parsedMaxUsers,
                            enableLogging,
                            allowInvites,
                            whois
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_room_config_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.savingLabel)
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.saveMucConfigBtn, fontWeight = FontWeight.Bold)
                    }
                }

            if (!isOwner) {
                // Non-Owner Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = strings.ownerOnlyFeatureNotice,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = strings.nonOwnerConfigDesc,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        if (!isNicknameOnly) {
            // Section: Notification Preferences (Local - Available to all)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.localRoomNotifHeader,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val modes = listOf(strings.notifModeAllMessages, strings.notifModeMentionOnly, strings.notifModeMuted)
                        modes.forEachIndexed { index, label ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { notificationMode = index }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = notificationMode == index,
                                    onClick = { notificationMode = index }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        if (isPersonalOnly || isNicknameOnly) {
            Button(
                onClick = {
                    onSaveConfig(
                        null, null, null, true, true, false, false, true, null,
                        notificationMode, null, true, true, null
                    )
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_personal_config_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.savingLabel)
                } else {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isNicknameOnly) strings.saveNicknameBtn else strings.saveSettingsBtn, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ModerationTabContent(
    owners: List<MucAffiliate>,
    admins: List<MucAffiliate>,
    members: List<MucAffiliate>,
    outcasts: List<MucAffiliate>,
    isLoading: Boolean = false,
    isOwnerOrAdmin: Boolean = true,
    onGrantOwnership: (String) -> Unit,
    onGrantAdmin: (String) -> Unit,
    onGrantMembership: (String) -> Unit,
    onBanUser: (String, String?) -> Unit,
    onRevokeAffiliation: (String) -> Unit
) {
    val strings = com.ndregil.inchat.ui.i18n.LocalAppStrings.current
    var jidInput by remember { mutableStateOf("") }
    var selectedTypeIndex by remember { mutableIntStateOf(2) }
    val affiliationTypes = listOf(strings.roleOwner, strings.roleAdmin, strings.roleMember, strings.banOutcastChip)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isOwnerOrAdmin) {
            // Section: Add Affiliate
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(12.dp)
            ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = strings.addAffiliationRoleTitle,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = jidInput,
                    onValueChange = { jidInput = it },
                    label = { Text(strings.realBareJidLabel) },
                    placeholder = { Text("username@server.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        var expanded by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(affiliationTypes.getOrElse(selectedTypeIndex) { strings.roleMember })
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            affiliationTypes.forEachIndexed { idx, type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        selectedTypeIndex = idx
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (jidInput.isNotBlank()) {
                                when (selectedTypeIndex) {
                                    0 -> onGrantOwnership(jidInput)
                                    1 -> onGrantAdmin(jidInput)
                                    2 -> onGrantMembership(jidInput)
                                    3 -> onBanUser(jidInput, strings.moderationViaAppReason)
                                }
                                jidInput = ""
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.addAction)
                    }
                }
            }
        }
    } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.ownerOnlyFeatureNotice, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        // Section: List Affiliates
        AffiliationListSection(title = strings.ownersPlural, list = owners, onRemove = onRevokeAffiliation)
        AffiliationListSection(title = strings.adminsPlural, list = admins, onRemove = onRevokeAffiliation)
        AffiliationListSection(title = strings.membersPlural, list = members, onRemove = onRevokeAffiliation)
        AffiliationListSection(title = strings.outcastsBannedPlural, list = outcasts, onRemove = onRevokeAffiliation)
        
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun AffiliationListSection(
    title: String,
    list: List<MucAffiliate>,
    onRemove: (String) -> Unit
) {
    if (list.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "$title (${list.size})",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            list.forEach { affiliate ->
                AffiliateItem(affiliate = affiliate, onRemove = { onRemove(affiliate.jid) })
            }
        }
    }
}

@Composable
fun AffiliateItem(
    affiliate: MucAffiliate,
    onRemove: () -> Unit
) {
    val strings = com.ndregil.inchat.ui.i18n.LocalAppStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = affiliate.jid,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                if (!affiliate.nickname.isNullOrBlank()) {
                    Text(
                        text = "${strings.nickPrefix} ${affiliate.nickname}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = strings.delete,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ModerationHistoryContent(messages: List<XmppMessage>) {
    val strings = com.ndregil.inchat.ui.i18n.LocalAppStrings.current
    if (messages.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = strings.noModerationHistory,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = strings.noModerationHistoryDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            messages.reversed().forEach { msg ->
                ModerationHistoryItem(message = msg)
            }
        }
    }
}

@Composable
fun ModerationHistoryItem(message: XmppMessage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Gavel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }
    }
}
