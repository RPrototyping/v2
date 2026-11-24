package com.example.shichiire.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 白い紙・墨・朱の封蝋・クラフト紙の封筒 */
@Immutable
data class AppColors(
    val bg: Color,
    val paper: Color,
    val ink: Color,
    val sub: Color,
    val line: Color,
    val shu: Color,
    val shuSoft: Color,
    val ok: Color,
    val okSoft: Color,
    val env: Color,
    val envDark: Color,
    val envFlap: Color,
    val letter: Color = Color(0xFFFFFDF8),
)

private val Light = AppColors(
    bg = Color(0xFFFFFFFF), paper = Color(0xFFF8F5F0), ink = Color(0xFF1D1B1A), sub = Color(0xFF6B6660),
    line = Color(0xFFE6E0D6), shu = Color(0xFFD23A22), shuSoft = Color(0xFFFBE3DD),
    ok = Color(0xFF2D6A4F), okSoft = Color(0xFFDCEFE4),
    env = Color(0xFFD9B98C), envDark = Color(0xFFC4A072), envFlap = Color(0xFFE6C99F),
)

private val Dark = AppColors(
    bg = Color(0xFF141312), paper = Color(0xFF1E1C1A), ink = Color(0xFFF2EFE9), sub = Color(0xFFAAA39A),
    line = Color(0xFF34302C), shu = Color(0xFFFF6C50), shuSoft = Color(0xFF3A1D17),
    ok = Color(0xFF7DD3A8), okSoft = Color(0xFF17291F),
    env = Color(0xFFA88A62), envDark = Color(0xFF8A6F4C), envFlap = Color(0xFFB89A70),
)

val LocalAppColors = staticCompositionLocalOf { Light }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = if (dark) Dark else Light
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.shu, onPrimary = Color.White,
            background = c.bg, onBackground = c.ink,
            surface = c.bg, onSurface = c.ink, onSurfaceVariant = c.sub, outline = c.line,
        )
    } else {
        lightColorScheme(
            primary = c.shu, onPrimary = Color.White,
            background = c.bg, onBackground = c.ink,
            surface = c.bg, onSurface = c.ink, onSurfaceVariant = c.sub, outline = c.line,
        )
    }
    CompositionLocalProvider(LocalAppColors provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** アプリの名前と、ひとことの説明 */
const val APP_NAME = "信じてMe!"
const val APP_TAGLINE = "約束に、質（しち）を入れる。\n守れば担保は燃え、破れば封が開く。"
