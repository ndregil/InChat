package com.ndregil.inchat.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.ndregil.inchat.data.model.ThemePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_prefs")

class ThemePreferencesManager(private val context: Context) {

    private object PreferencesKeys {
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val NICKNAME_COLOR = stringPreferencesKey("nickname_color")
        val CONTACT_COLOR = stringPreferencesKey("contact_color")
        val PRESENCE_COLOR = stringPreferencesKey("presence_color")
        val INCOMING_BUBBLE_COLOR = stringPreferencesKey("incoming_bubble_color")
        val OUTGOING_BUBBLE_COLOR = stringPreferencesKey("outgoing_bubble_color")
        val CUSTOM_WALLPAPER_URI = stringPreferencesKey("custom_wallpaper_uri")
        val WALLPAPER_OPACITY = floatPreferencesKey("wallpaper_opacity")
        val IS_CUSTOM_THEME_ENABLED = booleanPreferencesKey("is_custom_theme_enabled")
    }

    val themePreferences: Flow<ThemePreferences> = context.dataStore.data
        .map { preferences ->
            ThemePreferences(
                accentColorHex = preferences[PreferencesKeys.ACCENT_COLOR] ?: "#0284C7",
                nicknameColorHex = preferences[PreferencesKeys.NICKNAME_COLOR] ?: "#0284C7",
                contactColorHex = preferences[PreferencesKeys.CONTACT_COLOR] ?: "#1E293B",
                presenceOnlineColorHex = preferences[PreferencesKeys.PRESENCE_COLOR] ?: "#10B981",
                incomingBubbleColorHex = preferences[PreferencesKeys.INCOMING_BUBBLE_COLOR] ?: "#F1F5F9",
                outgoingBubbleColorHex = preferences[PreferencesKeys.OUTGOING_BUBBLE_COLOR] ?: "#0284C7",
                customWallpaperUri = preferences[PreferencesKeys.CUSTOM_WALLPAPER_URI],
                wallpaperOpacity = preferences[PreferencesKeys.WALLPAPER_OPACITY] ?: 0.5f,
                isCustomThemeEnabled = preferences[PreferencesKeys.IS_CUSTOM_THEME_ENABLED] ?: false
            )
        }

    suspend fun updateThemePreferences(prefs: ThemePreferences) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCENT_COLOR] = prefs.accentColorHex
            preferences[PreferencesKeys.NICKNAME_COLOR] = prefs.nicknameColorHex
            preferences[PreferencesKeys.CONTACT_COLOR] = prefs.contactColorHex
            preferences[PreferencesKeys.PRESENCE_COLOR] = prefs.presenceOnlineColorHex
            preferences[PreferencesKeys.INCOMING_BUBBLE_COLOR] = prefs.incomingBubbleColorHex
            preferences[PreferencesKeys.OUTGOING_BUBBLE_COLOR] = prefs.outgoingBubbleColorHex
            prefs.customWallpaperUri?.let { preferences[PreferencesKeys.CUSTOM_WALLPAPER_URI] = it } ?: preferences.remove(PreferencesKeys.CUSTOM_WALLPAPER_URI)
            preferences[PreferencesKeys.WALLPAPER_OPACITY] = prefs.wallpaperOpacity
            preferences[PreferencesKeys.IS_CUSTOM_THEME_ENABLED] = prefs.isCustomThemeEnabled
        }
    }
}
