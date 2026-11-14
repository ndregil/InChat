package com.ndregil.inchat.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ndregil.inchat.R

data class AppFontOption(
    val key: String,
    val label: String,
    val subtitle: String,
    val fontFamily: FontFamily
)

val PlusJakartaSansFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal)
)

val NunitoFontFamily = FontFamily(
    Font(R.font.nunito, FontWeight.Normal)
)

val MerriweatherFontFamily = FontFamily(
    Font(R.font.merriweather, FontWeight.Normal)
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal)
)

val APP_FONT_OPTIONS = listOf(
    AppFontOption("default", "Sistem Default", "Font standar perangkat Android", FontFamily.Default),
    AppFontOption("jakarta", "Plus Jakarta", "Modern & bersih untuk chat", PlusJakartaSansFontFamily),
    AppFontOption("nunito", "Nunito", "Bulat, lembut & mudah dibaca", NunitoFontFamily),
    AppFontOption("merriweather", "Merriweather", "Gaya serif klasik elegan", MerriweatherFontFamily),
    AppFontOption("mono", "JetBrains Mono", "Gaya monospace teknis", JetBrainsMonoFontFamily)
)

fun resolveAppFontFamily(key: String): FontFamily {
    return when (key.lowercase()) {
        "jakarta" -> PlusJakartaSansFontFamily
        "nunito" -> NunitoFontFamily
        "merriweather" -> MerriweatherFontFamily
        "mono" -> JetBrainsMonoFontFamily
        else -> FontFamily.Default
    }
}

private val BaseTypography = Typography()

private fun TextStyle.scaled(scale: Float, family: FontFamily): TextStyle {
    val newFontSize = if (fontSize.isSp) (fontSize.value * scale).sp else fontSize
    val newLineHeight = if (lineHeight.isSp) (lineHeight.value * scale).sp else lineHeight
    return copy(
        fontFamily = family,
        fontSize = newFontSize,
        lineHeight = newLineHeight
    )
}

fun createScaledTypography(
    appFontSizeSp: Float = 14f,
    fontFamilyKey: String = "default"
): Typography {
    val scale = (appFontSizeSp / 14f).coerceIn(0.7f, 1.8f)
    val family = resolveAppFontFamily(fontFamilyKey)
    return Typography(
        displayLarge = BaseTypography.displayLarge.scaled(scale, family),
        displayMedium = BaseTypography.displayMedium.scaled(scale, family),
        displaySmall = BaseTypography.displaySmall.scaled(scale, family),
        headlineLarge = BaseTypography.headlineLarge.scaled(scale, family),
        headlineMedium = BaseTypography.headlineMedium.scaled(scale, family),
        headlineSmall = BaseTypography.headlineSmall.scaled(scale, family),
        titleLarge = BaseTypography.titleLarge.scaled(scale, family),
        titleMedium = BaseTypography.titleMedium.scaled(scale, family),
        titleSmall = BaseTypography.titleSmall.scaled(scale, family),
        bodyLarge = BaseTypography.bodyLarge.scaled(scale, family),
        bodyMedium = BaseTypography.bodyMedium.scaled(scale, family),
        bodySmall = BaseTypography.bodySmall.scaled(scale, family),
        labelLarge = BaseTypography.labelLarge.scaled(scale, family),
        labelMedium = BaseTypography.labelMedium.scaled(scale, family),
        labelSmall = BaseTypography.labelSmall.scaled(scale, family)
    )
}

val Typography = createScaledTypography()

