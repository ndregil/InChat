package com.ndregil.inchat.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jivesoftware.smackx.muc.RoomInfo

@Composable
fun RoomConfigurationDialog(
    roomJid: String,
    initialInfo: RoomInfo?,
    onDismiss: () -> Unit,
    onSave: (roomName: String?, topic: String?, isPersistent: Boolean, isPublic: Boolean, password: String?) -> Unit
) {
    var roomName by remember { mutableStateOf(initialInfo?.name ?: "") }
    var topic by remember { mutableStateOf(initialInfo?.description ?: "") }
    var isPersistent by remember { mutableStateOf(initialInfo?.isPersistent ?: true) }
    var isPublic by remember { mutableStateOf(!(initialInfo?.isPasswordProtected ?: false)) }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Konfigurasi Ruangan")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text("Nama Ruangan") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Topik / Deskripsi") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Persistent")
                    Switch(checked = isPersistent, onCheckedChange = { isPersistent = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Public")
                    Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Kata Sandi (Opsional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    roomName.ifBlank { null },
                    topic.ifBlank { null },
                    isPersistent,
                    isPublic,
                    password.ifBlank { null }
                )
            }) {
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
