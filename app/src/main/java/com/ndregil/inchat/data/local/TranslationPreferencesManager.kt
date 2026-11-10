package com.ndregil.inchat.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.ndregil.inchat.data.model.TranslationPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.translationDataStore: DataStore<Preferences> by preferencesDataStore(name = "translation_prefs")

class TranslationPreferencesManager(private val context: Context) {

    private object PreferencesKeys {
        val IS_AUTO_TRANSLATE_ENABLED = booleanPreferencesKey("is_auto_translate_enabled")
        val TARGET_LANGUAGE_CODE = stringPreferencesKey("target_language_code")
        val TRANSLATE_ROOMS = booleanPreferencesKey("translate_rooms")
        val TRANSLATE_CONTACTS = booleanPreferencesKey("translate_contacts")
        val TRANSLATE_OUTGOING = booleanPreferencesKey("translate_outgoing")
    }

    val translationPreferences: Flow<TranslationPreferences> = context.translationDataStore.data
        .map { preferences ->
            val defaultLang = com.ndregil.inchat.data.network.TranslationService.getDefaultSystemLanguageCode()
            TranslationPreferences(
                isAutoTranslateEnabled = preferences[PreferencesKeys.IS_AUTO_TRANSLATE_ENABLED] ?: true,
                targetLanguageCode = preferences[PreferencesKeys.TARGET_LANGUAGE_CODE] ?: defaultLang,
                translateRooms = preferences[PreferencesKeys.TRANSLATE_ROOMS] ?: true,
                translateContacts = preferences[PreferencesKeys.TRANSLATE_CONTACTS] ?: true,
                translateOutgoing = preferences[PreferencesKeys.TRANSLATE_OUTGOING] ?: true
            )
        }

    suspend fun updatePreferences(prefs: TranslationPreferences) {
        context.translationDataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_AUTO_TRANSLATE_ENABLED] = prefs.isAutoTranslateEnabled
            preferences[PreferencesKeys.TARGET_LANGUAGE_CODE] = prefs.targetLanguageCode
            preferences[PreferencesKeys.TRANSLATE_ROOMS] = prefs.translateRooms
            preferences[PreferencesKeys.TRANSLATE_CONTACTS] = prefs.translateContacts
            preferences[PreferencesKeys.TRANSLATE_OUTGOING] = prefs.translateOutgoing
        }
    }
}
