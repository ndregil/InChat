package com.ndregil.inchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import org.jivesoftware.smackx.muc.RoomInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomConfigurationDialog(
    roomJid: String,
    initialInfo: RoomInfo?,
    isLoading: Boolean = false,
    onDismiss: () -> Unit,
    onSaveConfig: (
        roomName: String?,
        description: String?,
        subject: String?,
        isPersistent: Boolean,
        isPublic: Boolean,
        isMembersOnly: Boolean,
        isModerated: Boolean,
        allowSubjectChange: Boolean,
        password: String?
    ) -> Unit
) {
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
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Konfigurasi Ruangan MUC",
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
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
            }
        },
        text = {
            RoomConfigurationContent(
                initialInfo = initialInfo,
                isLoading = isLoading,
                onSaveConfig = onSaveConfig
            )
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_room_config_dialog")
            ) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun RoomConfigurationContent(
    initialInfo: RoomInfo?,
    isLoading: Boolean = false,
    onSaveConfig: (
        roomName: String?,
        description: String?,
        subject: String?,
        isPersistent: Boolean,
        isPublic: Boolean,
        isMembersOnly: Boolean,
        isModerated: Boolean,
        allowSubjectChange: Boolean,
        password: String?
    ) -> Unit
) {
    var roomName by remember(initialInfo) { mutableStateOf(initialInfo?.name ?: "") }
    var description by remember(initialInfo) { mutableStateOf(initialInfo?.description ?: "") }
    var subject by remember(initialInfo) { mutableStateOf(initialInfo?.subject ?: initialInfo?.description ?: "") }
    var isPersistent by remember(initialInfo) { mutableStateOf(initialInfo?.isPersistent ?: true) }
    var isPublic by remember(initialInfo) { mutableStateOf(!(initialInfo?.isPasswordProtected ?: false)) }
    var isMembersOnly by remember(initialInfo) { mutableStateOf(initialInfo?.isMembersOnly ?: false) }
    var isModerated by remember(initialInfo) { mutableStateOf(initialInfo?.isModerated ?: false) }
    var allowSubjectChange by remember(initialInfo) { mutableStateOf(true) }
    var password by remember(initialInfo) { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                        text = "Identitas & Deskripsi Ruangan",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text("Nama Ruangan MUC") },
                    placeholder = { Text("Contoh: Komunitas InChat Indonesia") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("room_name_input"),
                    leadingIcon = { Icon(Icons.Default.Group, contentDescription = null) }
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Deskripsi / Tentang Ruangan") },
                    placeholder = { Text("Deskripsikan topik dan pedoman komunitas...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("room_desc_input"),
                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) }
                )
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
                        text = "Manajemen Subjek / Topik (XEP-0045)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subjek / Pengumuman Ruangan") },
                    placeholder = { Text("Topik obrolan aktif...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("room_subject_input"),
                    leadingIcon = { Icon(Icons.Default.Campaign, contentDescription = null) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Izinkan Partisipan Mengubah Subjek", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            text = if (allowSubjectChange) "Semua anggota dapat mengganti topik" else "Hanya moderator & admin yang dapat mengganti topik",
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

        // Section 3: Privacy, Access & Security Settings
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
                        text = "Pengaturan Privasi & Keamanan",
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
                        Text("Tampilkan di Direktori Publik", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            text = "Ruangan dapat dicari di Service Discovery (XEP-0030)",
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
                        Text("Khusus Anggota (Members-Only)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            text = "Hanya akun terdaftar di roster/whitelist yang dapat bergabung",
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
                        Text("Ruangan Moderasi (Moderated)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            text = "Hanya partisipan dengan hak 'voice' yang dapat mengirim pesan",
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
                        Text("Ruangan Persisten (Persistent)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            text = "Ruangan tetap disimpan di server meskipun seluruh partisipan keluar",
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
                    label = { Text("Kata Sandi Ruangan (Opsional)") },
                    placeholder = { Text("Biarkan kosong untuk ruangan tanpa kata sandi") },
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
                                contentDescription = "Toggle Password"
                            )
                        }
                    }
                )
            }
        }

        // Save Button Action
        Button(
            onClick = {
                onSaveConfig(
                    roomName.ifBlank { null },
                    description.ifBlank { null },
                    subject.ifBlank { null },
                    isPersistent,
                    isPublic,
                    isMembersOnly,
                    isModerated,
                    allowSubjectChange,
                    password.ifBlank { null }
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
                Text("Menyimpan...")
            } else {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Simpan Konfigurasi Ruangan (XEP-0045)", fontWeight = FontWeight.Bold)
            }
        }
    }
}
