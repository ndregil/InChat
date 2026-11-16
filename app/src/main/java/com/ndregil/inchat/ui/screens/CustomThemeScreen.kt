package com.ndregil.inchat.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ndregil.inchat.data.model.ThemePreferences
import com.ndregil.inchat.ui.i18n.LocalAppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomThemeScreen(
    currentPrefs: ThemePreferences,
    onPrefsChange: (ThemePreferences) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val strings = LocalAppStrings.current
    var showColorPickerFor by remember { mutableStateOf<String?>(null) }

    val wallpaperLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            onPrefsChange(currentPrefs.copy(customWallpaperUri = it.toString()))
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
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
                        onClick = onBack,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.back,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = strings.customThemeTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp)
        ) {
            item {
                Text(
                    text = strings.enableCustomThemeSection,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.useCustomColorsTitle, style = MaterialTheme.typography.bodyLarge)
                        Text(strings.useCustomColorsDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = currentPrefs.isCustomThemeEnabled,
                        onCheckedChange = { onPrefsChange(currentPrefs.copy(isCustomThemeEnabled = it)) }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }

            if (currentPrefs.isCustomThemeEnabled) {
                item {
                    Text(
                        text = strings.colorSettingsHeader,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                item {
                    ColorPreferenceItem(
                        label = strings.colorAccentLabel,
                        description = strings.colorAccentDesc,
                        colorHex = currentPrefs.accentColorHex,
                        onClick = { showColorPickerFor = "accent" }
                    )
                }

                item {
                    ColorPreferenceItem(
                        label = strings.colorNicknameLabel,
                        description = strings.colorNicknameDesc,
                        colorHex = currentPrefs.nicknameColorHex,
                        onClick = { showColorPickerFor = "nickname" }
                    )
                }

                item {
                    ColorPreferenceItem(
                        label = strings.colorContactLabel,
                        description = strings.colorContactDesc,
                        colorHex = currentPrefs.contactColorHex,
                        onClick = { showColorPickerFor = "contact" }
                    )
                }

                item {
                    ColorPreferenceItem(
                        label = strings.colorPresenceOnlineLabel,
                        description = strings.colorPresenceOnlineDesc,
                        colorHex = currentPrefs.presenceOnlineColorHex,
                        onClick = { showColorPickerFor = "presence" }
                    )
                }

                item {
                    ColorPreferenceItem(
                        label = strings.colorPresenceChatLabel,
                        description = strings.colorPresenceChatDesc,
                        colorHex = currentPrefs.presenceChatColorHex,
                        onClick = { showColorPickerFor = "presence_chat" }
                    )
                }

                item {
                    ColorPreferenceItem(
                        label = strings.colorPresenceAwayLabel,
                        description = strings.colorPresenceAwayDesc,
                        colorHex = currentPrefs.presenceAwayColorHex,
                        onClick = { showColorPickerFor = "presence_away" }
                    )
                }

                item {
                    ColorPreferenceItem(
                        label = strings.colorPresenceXaLabel,
                        description = strings.colorPresenceXaDesc,
                        colorHex = currentPrefs.presenceXaColorHex,
                        onClick = { showColorPickerFor = "presence_xa" }
                    )
                }

                item {
                    ColorPreferenceItem(
                        label = strings.colorPresenceDndLabel,
                        description = strings.colorPresenceDndDesc,
                        colorHex = currentPrefs.presenceDndColorHex,
                        onClick = { showColorPickerFor = "presence_dnd" }
                    )
                }

                item {
                    ColorPreferenceItem(
                        label = strings.colorIncomingBubbleLabel,
                        description = strings.colorIncomingBubbleDesc,
                        colorHex = currentPrefs.incomingBubbleColorHex,
                        onClick = { showColorPickerFor = "incoming" }
                    )
                }

                item {
                    ColorPreferenceItem(
                        label = strings.colorOutgoingBubbleLabel,
                        description = strings.colorOutgoingBubbleDesc,
                        colorHex = currentPrefs.outgoingBubbleColorHex,
                        onClick = { showColorPickerFor = "outgoing" }
                    )
                }

                item {
                    ColorPreferenceItem(
                        label = strings.colorIncomingTextLabel,
                        description = strings.colorIncomingTextDesc,
                        colorHex = currentPrefs.incomingTextColorHex,
                        onClick = { showColorPickerFor = "incoming_text" }
                    )
                }

                item {
                    ColorPreferenceItem(
                        label = strings.colorOutgoingTextLabel,
                        description = strings.colorOutgoingTextDesc,
                        colorHex = currentPrefs.outgoingTextColorHex,
                        onClick = { showColorPickerFor = "outgoing_text" }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = strings.wallpaperBgHeader,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (currentPrefs.customWallpaperUri != null) {
                            AsyncImage(
                                model = currentPrefs.customWallpaperUri,
                                contentDescription = strings.customWallpaperDesc,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                alpha = currentPrefs.wallpaperOpacity
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(strings.noCustomWallpaperYet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Button(
                                    onClick = { wallpaperLauncher.launch("image/*") },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(strings.chooseImageBtn)
                                }
                                
                                if (currentPrefs.customWallpaperUri != null) {
                                    OutlinedButton(
                                        onClick = { onPrefsChange(currentPrefs.copy(customWallpaperUri = null)) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)
                                    ) {
                                        Text(strings.delete)
                                    }
                                }
                            }
                        }
                    }
                }
                
                if (currentPrefs.customWallpaperUri != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(strings.wallpaperOpacityLabel, style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = currentPrefs.wallpaperOpacity,
                        onValueChange = { onPrefsChange(currentPrefs.copy(wallpaperOpacity = it)) },
                        valueRange = 0.1f..1.0f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // Color Picker Dialog
    showColorPickerFor?.let { key ->
        val currentColorHex = when (key) {
            "accent" -> currentPrefs.accentColorHex
            "nickname" -> currentPrefs.nicknameColorHex
            "contact" -> currentPrefs.contactColorHex
            "presence" -> currentPrefs.presenceOnlineColorHex
            "presence_chat" -> currentPrefs.presenceChatColorHex
            "presence_away" -> currentPrefs.presenceAwayColorHex
            "presence_xa" -> currentPrefs.presenceXaColorHex
            "presence_dnd" -> currentPrefs.presenceDndColorHex
            "incoming" -> currentPrefs.incomingBubbleColorHex
            "outgoing" -> currentPrefs.outgoingBubbleColorHex
            "incoming_text" -> currentPrefs.incomingTextColorHex
            "outgoing_text" -> currentPrefs.outgoingTextColorHex
            else -> "#000000"
        }

        SimpleColorPickerDialog(
            initialColorHex = currentColorHex,
            onDismiss = { showColorPickerFor = null },
            onColorSelected = { newHex ->
                val updatedPrefs = when (key) {
                    "accent" -> currentPrefs.copy(accentColorHex = newHex)
                    "nickname" -> currentPrefs.copy(nicknameColorHex = newHex)
                    "contact" -> currentPrefs.copy(contactColorHex = newHex)
                    "presence" -> currentPrefs.copy(presenceOnlineColorHex = newHex)
                    "presence_chat" -> currentPrefs.copy(presenceChatColorHex = newHex)
                    "presence_away" -> currentPrefs.copy(presenceAwayColorHex = newHex)
                    "presence_xa" -> currentPrefs.copy(presenceXaColorHex = newHex)
                    "presence_dnd" -> currentPrefs.copy(presenceDndColorHex = newHex)
                    "incoming" -> currentPrefs.copy(incomingBubbleColorHex = newHex)
                    "outgoing" -> currentPrefs.copy(outgoingBubbleColorHex = newHex)
                    "incoming_text" -> currentPrefs.copy(incomingTextColorHex = newHex)
                    "outgoing_text" -> currentPrefs.copy(outgoingTextColorHex = newHex)
                    else -> currentPrefs
                }
                onPrefsChange(updatedPrefs)
                showColorPickerFor = null
            }
        )
    }
}

@Composable
fun ColorPreferenceItem(
    label: String,
    description: String,
    colorHex: String,
    onClick: () -> Unit
) {
    val color = try { Color(android.graphics.Color.parseColor(colorHex)) } catch (e: Exception) { Color.Gray }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(colorHex.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
            )
        }
    }
}

@Composable
fun SimpleColorPickerDialog(
    initialColorHex: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val presets = listOf(
        "#0284C7", "#0D9488", "#10B981", "#84CC16", 
        "#EAB308", "#F97316", "#EF4444", "#EC4899", 
        "#D946EF", "#8B5CF6", "#6366F1", "#3B82F6",
        "#F8FAFC", "#F1F5F9", "#E2E8F0", "#CBD5E1",
        "#94A3B8", "#64748B", "#475569", "#334155",
        "#1E293B", "#0F172A", "#020617", "#000000"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.pickColorDialogTitle, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(strings.pickColorDialogSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                
                // Using a FlowRow-like layout manually or just two LazyRows
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val rows = presets.chunked(6)
                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { hex ->
                                val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Gray }
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (initialColorHex.equals(hex, ignoreCase = true)) 3.dp else 1.dp,
                                            color = if (initialColorHex.equals(hex, ignoreCase = true)) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { onColorSelected(hex) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.close)
            }
        }
    )
}
