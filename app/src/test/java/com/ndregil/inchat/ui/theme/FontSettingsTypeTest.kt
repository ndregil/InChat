package com.ndregil.inchat.ui.theme

import androidx.compose.ui.text.font.FontFamily
import com.ndregil.inchat.data.model.ThemePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FontSettingsTypeTest {

    @Test
    fun themePreferences_defaultsAre14SpAndDefaultFamily() {
        val prefs = ThemePreferences()
        assertEquals(14f, prefs.messageFontSizeSp, 0.01f)
        assertEquals(14f, prefs.appFontSizeSp, 0.01f)
        assertEquals(14f, prefs.contactFontSizeSp, 0.01f)
        assertEquals("default", prefs.fontFamily)
    }

    @Test
    fun createScaledTypography_scalesProportionallyWithAppFontSize() {
        val base = createScaledTypography(appFontSizeSp = 14f, fontFamilyKey = "default")
        val larger = createScaledTypography(appFontSizeSp = 21f, fontFamilyKey = "jakarta")
        val smaller = createScaledTypography(appFontSizeSp = 10.5f, fontFamilyKey = "nunito")

        assertTrue(larger.bodyMedium.fontSize.value > base.bodyMedium.fontSize.value)
        assertTrue(smaller.bodyMedium.fontSize.value < base.bodyMedium.fontSize.value)
        assertEquals(base.bodyMedium.fontSize.value * 1.5f, larger.bodyMedium.fontSize.value, 0.1f)
        assertEquals(base.titleLarge.fontSize.value * 1.5f, larger.titleLarge.fontSize.value, 0.1f)
    }

    @Test
    fun resolveAppFontFamily_returnsExpectedFamilies() {
        assertEquals(FontFamily.Default, resolveAppFontFamily("default"))
        assertEquals(PlusJakartaSansFontFamily, resolveAppFontFamily("jakarta"))
        assertEquals(NunitoFontFamily, resolveAppFontFamily("nunito"))
        assertEquals(MerriweatherFontFamily, resolveAppFontFamily("merriweather"))
        assertEquals(JetBrainsMonoFontFamily, resolveAppFontFamily("mono"))
        assertNotEquals(FontFamily.Default, resolveAppFontFamily("jakarta"))
    }
}
