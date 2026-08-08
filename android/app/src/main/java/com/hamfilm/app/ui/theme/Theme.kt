package com.hamfilm.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hamfilm.app.R

// ---------- رنگ‌های برند (همان هویت سایت) ----------
val BrandBg = Color(0xFF08080F)
val BrandCard = Color(0xFF12121E)
val BrandCardLight = Color(0xFF1A1A2A)
val BrandPurple = Color(0xFF8B5CF6)
val BrandCyan = Color(0xFF22D3EE)
val BrandText = Color(0xFFEDEDF5)
val BrandTextMuted = Color(0xFF9A9AB0)
val BrandDanger = Color(0xFFEF4444)
val BrandGreen = Color(0xFF22C55E)
val BrandAmber = Color(0xFFF59E0B)

val BrandGradient = Brush.linearGradient(listOf(BrandPurple, BrandCyan))
val BrandGradientSoft = Brush.linearGradient(
    listOf(Color(0x338B5CF6), Color(0x3322D3EE))
)

// ---------- فونت وزیرمتن (همان فونت سایت) ----------
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_light, FontWeight.Light),
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, fontSize = 34.sp),
    headlineMedium = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, fontSize = 26.sp),
    headlineSmall = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, fontSize = 21.sp),
    titleLarge = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, fontSize = 19.sp),
    titleMedium = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium, fontSize = 10.sp)
)

private val DarkColors = darkColorScheme(
    primary = BrandPurple,
    onPrimary = Color.White,
    secondary = BrandCyan,
    onSecondary = Color(0xFF00333D),
    background = BrandBg,
    onBackground = BrandText,
    surface = BrandCard,
    onSurface = BrandText,
    surfaceVariant = BrandCardLight,
    onSurfaceVariant = BrandTextMuted,
    error = BrandDanger,
    onError = Color.White
)

@Composable
fun HamfilmTheme(content: @Composable () -> Unit) {
    // همیشه تم تیره — هویت برند (مثل اپ ببینیم)
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        content = content
    )
}
