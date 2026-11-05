package com.ndregil.inchat.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ThemeOption(
    val id: String,
    val name: String,
    val mainColor: Color,
    val bgPreview: Color
)

data class WallpaperOption(
    val id: String,
    val name: String,
    val previewGradient: List<Color>
)

val BUBBLE_THEMES = listOf(
    ThemeOption("dregil-theme", "Dregil Violet", Color(0xFF6366F1), Color(0xFF1E1B4B)),
    ThemeOption("purple", "Purple", Color(0xFF9333EA), Color(0xFF3B0764)),
    ThemeOption("green", "Emerald", Color(0xFF10B981), Color(0xFF064E3B)),
    ThemeOption("orange", "Orange", Color(0xFFF97316), Color(0xFF7C2D12)),
    ThemeOption("pink", "Pink Rose", Color(0xFFEC4899), Color(0xFF831843)),
    ThemeOption("blue", "Ocean Blue", Color(0xFF0284C7), Color(0xFF0C4A6E))
)

val WALLPAPER_STYLES = listOf(
    WallpaperOption("default", "Standard", listOf(Color(0xFF0F172A), Color(0xFF0F172A))),
    WallpaperOption("dregil_pattern", "Dregil Pattern", listOf(Color(0xFF1E1B4B), Color(0xFF311B92))),
    WallpaperOption("gradient", "Aurora Cyber", listOf(Color(0xFF18181B), Color(0xFF4338CA), Color(0xFF06B6D4))),
    WallpaperOption("dark_stars", "Night Sky", listOf(Color(0xFF030712), Color(0xFF1E1035))),
    WallpaperOption("warm_wood", "Obsidian Amber", listOf(Color(0xFF1C1917), Color(0xFF451A03))),
    WallpaperOption("doodle", "Doodle Grid", listOf(Color(0xFF111827), Color(0xFF1F2937)))
)

@Composable
fun WallpaperBackground(
    wallpaperStyle: String,
    customWallpaperUri: String? = null,
    opacity: Float = 0.5f,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (customWallpaperUri != null) {
            coil.compose.AsyncImage(
                model = customWallpaperUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = opacity
            )
        } else {
            when (wallpaperStyle.lowercase()) {
                "dregil_pattern" -> {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF0F0D23), Color(0xFF181335), Color(0xFF0F0D23))
                            )
                        )
                        val step = 80.dp.toPx()
                        for (x in 0..size.width.toInt() step step.toInt()) {
                            drawLine(
                                color = Color(0x1A818CF8),
                                start = Offset(x.toFloat(), 0f),
                                end = Offset(x.toFloat(), size.height),
                                strokeWidth = 1f
                            )
                        }
                        for (y in 0..size.height.toInt() step step.toInt()) {
                            drawLine(
                                color = Color(0x1A818CF8),
                                start = Offset(0f, y.toFloat()),
                                end = Offset(size.width, y.toFloat()),
                                strokeWidth = 1f
                            )
                        }
                    }
                }
                "gradient" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFF4338CA), Color(0xFF0F172A), Color(0xFF020617)),
                                    radius = 1200f
                                )
                            )
                    )
                }
                "dark_stars" -> {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF030712), Color(0xFF111827), Color(0xFF030712))
                            )
                        )
                        val points = listOf(
                            Offset(100f, 150f), Offset(400f, 250f), Offset(700f, 180f),
                            Offset(200f, 500f), Offset(650f, 620f), Offset(850f, 400f),
                            Offset(300f, 900f), Offset(550f, 1100f), Offset(150f, 1300f)
                        )
                        points.forEach { pt ->
                            drawCircle(color = Color(0x80F8FAFC), radius = 2.5f, center = pt)
                            drawCircle(color = Color(0x20F8FAFC), radius = 6f, center = pt)
                        }
                    }
                }
                "warm_wood" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0xFF1C1917), Color(0xFF292524), Color(0xFF451A03))
                                )
                            )
                    )
                }
                "doodle" -> {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(color = Color(0xFF0F172A))
                        val step = 120.dp.toPx()
                        for (x in 0..size.width.toInt() step step.toInt()) {
                            for (y in 0..size.height.toInt() step step.toInt()) {
                                drawCircle(
                                    color = Color(0x0E94A3B8),
                                    radius = 24.dp.toPx(),
                                    center = Offset(x.toFloat() + 30f, y.toFloat() + 30f)
                                )
                            }
                        }
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatThemeSettingsDialog(
    currentTheme: String,
    currentWallpaper: String,
    roomJid: String? = null,
    roomName: String? = null,
    roomAvatar: ByteArray? = null,
    onSelectRoomAvatar: (() -> Unit)? = null,
    onSave: (theme: String, wallpaper: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTheme by remember { mutableStateOf(currentTheme) }
    var selectedWallpaper by remember { mutableStateOf(currentWallpaper) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ColorLens,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Setelan Ruang, Tema & Wallpaper",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 0: Custom Room Avatar Image
                if (onSelectRoomAvatar != null) {
                    Text(
                        text = "Foto Avatar Ruangan MUC",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            val bitmap = remember(roomAvatar) {
                                if (roomAvatar != null && roomAvatar.isNotEmpty()) {
                                    BitmapFactory.decodeByteArray(roomAvatar, 0, roomAvatar.size)
                                } else null
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Avatar Room",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = roomName ?: "Ruang MUC",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                            Text(
                                text = "Unggah foto avatar untuk room ini",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = onSelectRoomAvatar,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Ubah Foto", fontSize = 11.5.sp)
                        }
                    }
                }

                // Section 1: Chat Bubble Color Theme
                Text(
                    text = "Warna Bubble Pesan",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BUBBLE_THEMES.chunked(2).forEach { rowThemes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowThemes.forEach { theme ->
                                val isSelected = selectedTheme.equals(theme.id, ignoreCase = true)
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { selectedTheme = theme.id }
                                        .testTag("theme_option_${theme.id}"),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(theme.mainColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = theme.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Section 2: Chat Wallpaper Selection
                Text(
                    text = "Wallpaper Latar Belakang",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(WALLPAPER_STYLES) { wp ->
                        val isSelected = selectedWallpaper.equals(wp.id, ignoreCase = true)
                        Card(
                            modifier = Modifier
                                .size(width = 90.dp, height = 110.dp)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedWallpaper = wp.id }
                                .testTag("wallpaper_option_${wp.id}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            brush = Brush.verticalGradient(colors = wp.previewGradient)
                                        )
                                )

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .padding(6.dp)
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .align(Alignment.TopEnd),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter),
                                    color = Color.Black.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        text = wp.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(selectedTheme, selectedWallpaper) },
                modifier = Modifier.testTag("btn_save_chat_theme")
            ) {
                Text("Simpan", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
