package com.cato.duneadmin.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp

object MochaColors {
    val Background = Color(0xFF1E1614)
    val Surface = Color(0xFF2B211E)
    val SurfaceElevated = Color(0xFF382A25)
    val SurfaceHighest = Color(0xFF44332D)
    val Border = Color(0xFF5A443B)
    val TextPrimary = Color(0xFFF2E7DF)
    val TextSecondary = Color(0xFFCDBBB1)
    val Accent = Color(0xFFC98F73)
    val AccentHover = Color(0xFFDEA98E)
    val Success = Color(0xFFA8BEA0)
    val Warning = Color(0xFFD7B477)
    val Error = Color(0xFFD98787)
}

object SciFiMetricColors {
    val Memory = Color(0xFF57E6FF)
    val Cpu = Color(0xFFFFC857)
    val Disk = Color(0xFFB7F774)
    val Running = Color(0xFF7DFF9B)
    val Uptime = Color(0xFFE7B7FF)
    val Offline = Color(0xFFFF7A90)
}

private val MinecraftFontFamily = FontFamily(
    Font("fonts/Minecraft.otf", weight = FontWeight.Normal),
)

private val CatosTypography = Typography(
    headlineSmall = TextStyle(fontFamily = MinecraftFontFamily, fontSize = 25.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = MinecraftFontFamily, fontSize = 19.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = MinecraftFontFamily, fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontFamily = MinecraftFontFamily, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = MinecraftFontFamily, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = MinecraftFontFamily, fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = MinecraftFontFamily, fontSize = 12.sp, lineHeight = 17.sp),
    labelMedium = TextStyle(fontFamily = MinecraftFontFamily, fontSize = 13.sp, lineHeight = 18.sp),
)

private val CatosColorScheme = darkColorScheme(
    primary = MochaColors.Accent,
    onPrimary = MochaColors.Background,
    secondary = MochaColors.AccentHover,
    onSecondary = MochaColors.Background,
    background = MochaColors.Background,
    onBackground = MochaColors.TextPrimary,
    surface = MochaColors.Surface,
    onSurface = MochaColors.TextPrimary,
    surfaceVariant = MochaColors.SurfaceElevated,
    onSurfaceVariant = MochaColors.TextSecondary,
    outline = MochaColors.Border,
    error = MochaColors.Error,
    onError = MochaColors.Background,
)

@Composable
fun CatosDuneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CatosColorScheme, typography = CatosTypography, content = content)
}
