package com.telerelay.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.telerelay.app.data.Store
import com.telerelay.app.data.ThemeMode

@Immutable
data class TeleColors(
    val isDark: Boolean,
    val bg: Color,
    val card: Color,
    val cardHi: Color,
    val fill: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val separator: Color,
    val accent: Color,
    val accentSoft: Color,
    val green: Color,
    val red: Color,
    val orange: Color,
    val yellow: Color,
    val tabBar: Color,
    val hero: Brush
)

val DarkColors = TeleColors(
    isDark = true,
    bg = Color(0xFF000000),
    card = Color(0xFF101013),
    cardHi = Color(0xFF1D1D22),
    fill = Color(0x2E8E8E99),
    text = Color(0xFFFFFFFF),
    text2 = Color(0x9EEBEBF5),
    text3 = Color(0x62EBEBF5),
    separator = Color(0x16FFFFFF),
    accent = Color(0xFF0A84FF),
    accentSoft = Color(0x2E0A84FF),
    green = Color(0xFF30D158),
    red = Color(0xFFFF453A),
    orange = Color(0xFFFF9F0A),
    yellow = Color(0xFFFFD60A),
    tabBar = Color(0xE80B0B0E),
    hero = Brush.linearGradient(
        listOf(Color(0xFF0B3E9C), Color(0xFF0A84FF), Color(0xFF062E6E))
    )
)

val LightColors = TeleColors(
    isDark = false,
    bg = Color(0xFFF2F2F7),
    card = Color(0xFFFFFFFF),
    cardHi = Color(0xFFFFFFFF),
    fill = Color(0x29787880),
    text = Color(0xFF000000),
    text2 = Color(0x993C3C43),
    text3 = Color(0x5C3C3C43),
    separator = Color(0x1F3C3C43),
    accent = Color(0xFF007AFF),
    accentSoft = Color(0x1F007AFF),
    green = Color(0xFF34C759),
    red = Color(0xFFFF3B30),
    orange = Color(0xFFFF9500),
    yellow = Color(0xFFFFCC00),
    tabBar = Color(0xE6FFFFFF),
    hero = Brush.linearGradient(
        listOf(Color(0xFF1565D8), Color(0xFF0A84FF), Color(0xFF0B4FB2))
    )
)

val LocalTele = staticCompositionLocalOf { DarkColors }

/** iOS-flavored type scale. */
object TeleType {
    val largeTitle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, lineHeight = 40.sp)
    val title2 = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, lineHeight = 28.sp)
    val title3 = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp, lineHeight = 25.sp)
    val headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, lineHeight = 22.sp)
    val body = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.2).sp, lineHeight = 22.sp)
    val callout = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp)
    val subhead = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp)
    val footnote = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp)
    val caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp, lineHeight = 16.sp)
}

@Composable
fun TeleTheme(content: @Composable () -> Unit) {
    val palette = when (Store.themeMode.value) {
        ThemeMode.LIGHT -> LightColors
        ThemeMode.DARK -> DarkColors
        ThemeMode.AUTO -> if (isSystemInDarkTheme()) DarkColors else LightColors
    }
    CompositionLocalProvider(LocalTele provides palette) {
        MaterialTheme(
            colorScheme = if (palette.isDark) darkColorScheme() else lightColorScheme(),
            content = content
        )
    }
}
