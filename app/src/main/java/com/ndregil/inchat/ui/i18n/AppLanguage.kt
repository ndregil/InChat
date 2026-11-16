package com.ndregil.inchat.ui.i18n

data class AppLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val flagEmoji: String
)

val SUPPORTED_APP_LANGUAGES = listOf(
    AppLanguage("system", "Ikuti Bahasa Sistem", "System Default", "🌐"),
    AppLanguage("id", "Bahasa Indonesia", "Bahasa Indonesia", "🇮🇩"),
    AppLanguage("en", "Inggris", "English", "🇬🇧"),
    AppLanguage("ar", "Arab", "العربية", "🇸🇦"),
    AppLanguage("ru", "Rusia", "Русский", "🇷🇺"),
    AppLanguage("es", "Spanyol", "Español", "🇪🇸"),
    AppLanguage("fr", "Prancis", "Français", "🇫🇷"),
    AppLanguage("de", "Jerman", "Deutsch", "🇩🇪"),
    AppLanguage("ja", "Jepang", "日本語", "🇯🇵"),
    AppLanguage("zh", "Mandarin", "中文 (简体)", "🇨🇳")
)
