package com.ndregil.inchat.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch

@Composable
fun JoinCreateRoomDialog(
    defaultNickname: String,
    isLoading: Boolean = false,
    status: String? = null,
    onDismiss: () -> Unit,
    onJoinOrCreate: (roomJid: String, roomName: String, nickname: String, topic: String, password: String, saveToBookmarks: Boolean) -> Unit
) {
    val strings = com.ndregil.inchat.ui.i18n.LocalAppStrings.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Buat Room, 1 = Gabung Room
    var roomName by remember { mutableStateOf("") }
    var roomJid by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf(defaultNickname) }
    var topic by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var saveToBookmarks by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (selectedTab == 0) Icons.Default.Add else Icons.Default.GroupAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (selectedTab == 0) strings.createRoomDialogTitle else strings.joinMucDialogTitle)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(strings.createRoomTab) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(strings.joinRoomTab) }
                    )
                }

                if (selectedTab == 0) {
                    OutlinedTextField(
                        value = roomName,
                        onValueChange = {
                            roomName = it
                            if (roomJid.isBlank() || roomJid.endsWith("@conference.xmpp.today")) {
                                val clean = it.lowercase().replace(" ", "-").replace("[^a-z0-9-]".toRegex(), "")
                                roomJid = if (clean.isNotBlank()) "$clean@conference.xmpp.today" else ""
                            }
                        },
                        label = { Text(strings.roomNameFieldLabel) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_room_name"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = roomJid,
                        onValueChange = { roomJid = it },
                        label = { Text(strings.roomJidMucLabel) },
                        placeholder = { Text("room-name@conference.domain.org") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_room_jid"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = topic,
                        onValueChange = { topic = it },
                        label = { Text(strings.roomTopicOptionalLabel) },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = roomJid,
                        onValueChange = { roomJid = it },
                        label = { Text(strings.chatRoomJidLabel) },
                        placeholder = { Text(strings.chatRoomJidPlaceholder) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_room_jid"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = strings.recommendedPublicMuc,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("cyber-hub", "developer", "komunitas").forEach { preset ->
                            FilterChip(
                                selected = roomJid.startsWith(preset),
                                onClick = {
                                    roomJid = "$preset@conference.xmpp.today"
                                    roomName = "# " + preset.replaceFirstChar { it.uppercase() }
                                },
                                label = { Text(preset, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text(strings.yourRoomNicknameLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(strings.roomPasswordOptionalLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = strings.saveToFavoritesSwitch,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = saveToBookmarks,
                        onCheckedChange = { saveToBookmarks = it }
                    )
                }

                if (status != null) {
                    val isErrorStatus = status.contains("Gagal", ignoreCase = true) ||
                        status.contains("Fail", ignoreCase = true) ||
                        status.contains("Error", ignoreCase = true)
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = if (isErrorStatus) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = status,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isErrorStatus) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (roomJid.isNotBlank()) {
                        val finalName = if (roomName.isNotBlank()) roomName else "# " + roomJid.substringBefore("@")
                        onJoinOrCreate(roomJid.trim(), finalName.trim(), nickname.trim(), topic.trim(), password.trim(), saveToBookmarks)
                    }
                },
                enabled = roomJid.isNotBlank() && !isLoading,
                modifier = Modifier.testTag("btn_confirm_room_dialog")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (selectedTab == 0) strings.createAndJoinBtn else strings.joinRoomBtn)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
