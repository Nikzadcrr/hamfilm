package com.hamfilm.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.hamfilm.app.data.TokenStore
import com.hamfilm.app.data.api.AppRepository
import com.hamfilm.app.data.model.PublicSettings
import com.hamfilm.app.ui.components.GradientBackground
import com.hamfilm.app.ui.components.OnboardingDialog
import com.hamfilm.app.ui.navigation.Routes
import com.hamfilm.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// ============================================================
//  صفحه اصلی — لوگوی انیمیشنی + دکمه‌های بزرگ مرتب
// ============================================================
@Composable
fun HomeScreen(nav: NavHostController) {
    val repo = remember { AppRepository() }
    var settings by remember { mutableStateOf<PublicSettings?>(null) }
    var showOnboarding by remember { mutableStateOf(!TokenStore.onboarded) }

    LaunchedEffect(Unit) {
        settings = repo.settings()
    }

    GradientBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---------- نوار بالا (تنظیمات) ----------
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) {
                    Icon(Icons.Default.Settings, "تنظیمات", tint = BrandTextMuted)
                }
            }

            // ---------- لوگوی انیمیشنی ----------
            Spacer(Modifier.height(10.dp))
            AnimatedLogo()

            // ---------- پیام خوش‌آمد ----------
            Spacer(Modifier.height(22.dp))
            Text(
                settings?.welcomeMessage?.takeIf { it.isNotBlank() } ?: "خوش آمدید!",
                style = MaterialTheme.typography.titleMedium,
                color = BrandText
            )
            Text(
                "امشب با دوستانت فیلم ببین 🍿",
                style = MaterialTheme.typography.bodySmall,
                color = BrandTextMuted
            )

            // اعلامیه (اگر فعال باشد)
            if (settings?.announcementActive == true && !settings?.announcement.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(BrandAmber.copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("📣 ${settings!!.announcement}", fontSize = 12.sp, color = BrandAmber)
                }
            }

            // ---------- دکمه‌های بزرگ ----------
            Spacer(Modifier.height(28.dp))

            // ۱) ساخت اتاق — دکمه اصلی
            AnimatedIn(0) {
                PrimaryBigButton(
                    emoji = "🎬",
                    title = "ساخت اتاق",
                    subtitle = "لینک ویدیو بذار و دوستانت رو دعوت کن",
                    onClick = { nav.navigate(Routes.CREATE) }
                )
            }

            Spacer(Modifier.height(14.dp))

            // ۲) ورود با کد اتاق
            AnimatedIn(120) {
                GlassNavButton(
                    emoji = "🔑",
                    title = "ورود با کد اتاق",
                    subtitle = "با کد ۶ رقمی به اتاق دوستت بپیوند",
                    accent = BrandCyan,
                    onClick = { nav.navigate(Routes.JOIN) }
                )
            }

            Spacer(Modifier.height(14.dp))

            // ۳) آرشیو فیلم‌ها
            AnimatedIn(240) {
                GlassNavButton(
                    emoji = "🎞️",
                    title = "آرشیو فیلم‌ها",
                    subtitle = "جدیدترین فیلم‌ها و سریال‌ها",
                    accent = BrandPurple,
                    badge = "جدید",
                    onClick = { nav.navigate(Routes.ARCHIVE) }
                )
            }

            Spacer(Modifier.height(14.dp))

            // ۴) اشتراک ویژه
            AnimatedIn(360) {
                GlassNavButton(
                    emoji = "💎",
                    title = "اشتراک ویژه",
                    subtitle = "پلن‌ها و تخفیف‌های هم‌فیلم",
                    accent = BrandAmber,
                    badge = "تا ۲۰٪",
                    onClick = { nav.navigate(Routes.PLANS) }
                )
            }

            Spacer(Modifier.height(14.dp))

            // ۵) پشتیبانی
            AnimatedIn(480) {
                GlassNavButton(
                    emoji = "🎟️",
                    title = "پشتیبانی",
                    subtitle = "تیکت بزن، راهنما ببین، گزارش بده",
                    accent = BrandGreen,
                    onClick = { nav.navigate(Routes.TICKETS) }
                )
            }

            // ---------- بخش ورود / ساخت حساب ----------
            Spacer(Modifier.height(34.dp))
            AnimatedIn(600) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { nav.navigate(Routes.LOGIN) }) {
                            Text("ورود", color = BrandCyan, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Text("•", color = BrandTextMuted.copy(alpha = 0.5f), fontSize = 14.sp)
                        TextButton(onClick = { nav.navigate(Routes.REGISTER) }) {
                            Text("ساخت حساب", color = BrandCyan, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                    Text(
                        "برای ساخت اتاق نیازی به حساب نداری — مهمان هم می‌تونی وارد شی",
                        fontSize = 11.sp,
                        color = BrandTextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    if (showOnboarding) {
        OnboardingDialog(onFinish = {
            TokenStore.onboarded = true
            showOnboarding = false
        })
    }
}

// ============================================================
//  لوگوی انیمیشنی: حلقه گرادیانی چرخان + هاله نبض‌دار + ذرات شناور
// ============================================================
@Composable
fun AnimatedLogo(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "logo")

    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing)
        ),
        label = "rotation"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(modifier = modifier.size(170.dp), contentAlignment = Alignment.Center) {

        // هاله نبض‌دار پشت لوگو
        Box(
            Modifier
                .size(165.dp)
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                    alpha = glow
                }
                .background(
                    Brush.radialGradient(
                        listOf(
                            BrandPurple.copy(alpha = 0.35f),
                            BrandCyan.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        // ذرات شناور دور لوگو
        FloatingEmoji("🎬", Modifier.align(Alignment.TopStart).offset(x = 6.dp, y = 10.dp), 0)
        FloatingEmoji("🍿", Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 16.dp), 700)
        FloatingEmoji("✨", Modifier.align(Alignment.BottomStart).offset(x = 10.dp, y = (-6).dp), 350)
        FloatingEmoji("🎥", Modifier.align(Alignment.BottomEnd).offset(x = (-8).dp, y = (-2).dp), 1050)

        // حلقه گرادیانی چرخان با دو نقطه نورانی
        Canvas(
            Modifier
                .size(150.dp)
                .graphicsLayer { rotationZ = rotation }
        ) {
            val radius = size.minDimension / 2f
            val strokeWidth = 5.dp.toPx()
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(
                        BrandPurple,
                        BrandCyan,
                        BrandPurple.copy(alpha = 0.15f),
                        BrandPurple
                    )
                ),
                radius = radius - strokeWidth / 2,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            // دو نقطه نورانی روی حلقه
            listOf(0f, 180f).forEach { base ->
                val angle = Math.toRadians(base.toDouble())
                val cx = size.width / 2 + cos(angle).toFloat() * (radius - strokeWidth / 2)
                val cy = size.height / 2 + sin(angle).toFloat() * (radius - strokeWidth / 2)
                drawCircle(Color.White, radius = 4.dp.toPx(), center = Offset(cx, cy))
                drawCircle(BrandCyan.copy(alpha = 0.45f), radius = 9.dp.toPx(), center = Offset(cx, cy))
            }
        }

        // دایره داخلی + مثلث پخش
        Box(
            Modifier
                .size(106.dp)
                .shadow(18.dp, CircleShape, spotColor = BrandPurple.copy(alpha = 0.45f))
                .clip(CircleShape)
                .background(BrandCard)
                .graphicsLayer {
                    scaleX = pulse * 0.92f + 0.08f
                    scaleY = pulse * 0.92f + 0.08f
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(46.dp)) {
                val path = Path().apply {
                    moveTo(size.width * 0.28f, size.height * 0.16f)
                    lineTo(size.width * 0.84f, size.height * 0.5f)
                    lineTo(size.width * 0.28f, size.height * 0.84f)
                    close()
                }
                drawPath(
                    path,
                    brush = Brush.linearGradient(listOf(BrandPurple, BrandCyan))
                )
            }
        }
    }
}

// ---------- ذره شناور ----------
@Composable
private fun FloatingEmoji(emoji: String, modifier: Modifier = Modifier, delayMs: Int = 0) {
    val t = rememberInfiniteTransition(label = "float")
    val floatY by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, delayMillis = delayMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "y"
    )
    val floatAlpha by t.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, delayMillis = delayMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Text(
        emoji,
        fontSize = 19.sp,
        modifier = modifier.graphicsLayer {
            translationY = (-16 * floatY).dp.toPx()
            alpha = floatAlpha
        }
    )
}

// ============================================================
//  دکمه‌ها
// ============================================================

// دکمه اصلی گرادیانی بزرگ (ساخت اتاق)
@Composable
fun PrimaryBigButton(
    emoji: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(22.dp), spotColor = BrandPurple.copy(alpha = 0.5f))
            .clip(RoundedCornerShape(22.dp))
            .background(BrandGradient)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // آیکون داخل دایره شیشه‌ای
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 26.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.78f))
            }
            Text("‹", fontSize = 24.sp, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

// دکمه شیشه‌ای (ورود با کد / آرشیو / اشتراک / پشتیبانی)
@Composable
fun GlassNavButton(
    emoji: String,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BrandCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // آیکون با هاله رنگی
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 22.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = BrandText)
                    badge?.let {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(accent.copy(alpha = 0.18f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(it, fontSize = 10.sp, color = accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 11.5.sp, color = BrandTextMuted)
            }
            Text("‹", fontSize = 20.sp, color = BrandTextMuted.copy(alpha = 0.6f))
        }
    }
}

// ---------- ورود تدریجی با تاخیر ----------
@Composable
fun AnimatedIn(delayMs: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(550)) + slideInVertically(tween(550)) { it / 4 }
    ) {
        content()
    }
}
