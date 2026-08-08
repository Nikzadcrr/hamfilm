package com.hamfilm.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.hamfilm.app.data.TokenStore
import com.hamfilm.app.ui.components.*
import com.hamfilm.app.ui.navigation.Routes
import com.hamfilm.app.ui.theme.*
import com.hamfilm.app.viewmodel.RoomViewModel
import kotlinx.coroutines.delay

// لینک‌های نمونه برای تست سریع
private val SampleVideos = listOf(
    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
    "https://test-videos.co.uk/vids/bigbuckbunny/mkv/720/Big_Buck_Bunny_720_10s_1MB.mkv",
    "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4"
)

private val Avatars = listOf("🎬", "🍿", "🎧", "🦊", "🐼", "🚀", "🔥", "😎")

@Composable
fun CreateRoomScreen(nav: NavHostController) {
    val vm = remember { RoomViewModel() }
    var name by remember { mutableStateOf(TokenStore.name.ifBlank { "اتاق من" }) }
    var videoUrl by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf(TokenStore.avatar.ifBlank { "🎬" }) }
    var error by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var linkExpanded by remember { mutableStateOf(false) }

    GradientBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ---------- هدر ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.Default.KeyboardArrowDown, "بازگشت", tint = BrandTextMuted)
                }
                Spacer(Modifier.weight(1f))
            }
            AnimatedIn(0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // آیکون انیمیشنی
                    AnimatedIcon(
                        icon = { modifier ->
                            Icon(Icons.Default.VideoCall, null, tint = Color.White, modifier = modifier.size(30.dp))
                        },
                        size = 54.dp,
                        gradient = BrandGradient
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("ساخت اتاق جدید", style = MaterialTheme.typography.headlineSmall)
                        Text("اتاق بساز و دوستانت را دعوت کن", color = BrandTextMuted, fontSize = 12.5.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ---------- نام اتاق ----------
            AnimatedIn(80) {
                SectionLabel("نام اتاق")
                Spacer(Modifier.height(8.dp))
                HamTextField(
                    name, { name = it },
                    label = "نام اتاق",
                    placeholder = "مثلاً: سینمای پنجشنبه شب"
                )
            }

            Spacer(Modifier.height(18.dp))

            // ---------- لینک ویدیو (اختیاری) ----------
            AnimatedIn(160) {
                SectionLabel("ویدیو — اختیاری")
                Spacer(Modifier.height(4.dp))
                Text(
                    "لینک بذار یا بعداً داخل اتاق از گوشی‌ات فایل انتخاب کن",
                    fontSize = 11.5.sp,
                    color = BrandTextMuted
                )
                Spacer(Modifier.height(8.dp))

                // کارت انتخاب
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(BrandCard)
                        .clickable { linkExpanded = !linkExpanded }
                        .animateContentSize()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(BrandCyan.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Link, null, tint = BrandCyan, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (videoUrl.isBlank()) "بدون ویدیو — بعداً انتخاب می‌کنم" else "لینک آماده است ✓",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    if (videoUrl.isBlank()) "بعداً از داخل اتاق می‌تونی لینک یا فایل گوشی بفرستی"
                                    else videoUrl,
                                    color = BrandTextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                null,
                                tint = BrandTextMuted,
                                modifier = Modifier
                                    .graphicsLayer { rotationZ = if (linkExpanded) 180f else 0f }
                            )
                        }

                        AnimatedVisibility(
                            visible = linkExpanded,
                            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 },
                            exit = fadeOut(tween(200))
                        ) {
                            Column(Modifier.padding(top = 12.dp)) {
                                HamTextField(
                                    videoUrl, {
                                        videoUrl = it
                                        urlError = null
                                    },
                                    label = "لینک ویدیو (MP4 / HLS / یوتیوب)",
                                    placeholder = "https://example.com/movie.mp4"
                                )
                                urlError?.let {
                                    Spacer(Modifier.height(6.dp))
                                    Text(it, color = BrandDanger, fontSize = 12.sp)
                                }
                                Spacer(Modifier.height(10.dp))
                                Text("لینک‌های نمونه:", fontSize = 11.sp, color = BrandTextMuted)
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(top = 6.dp)
                                ) {
                                    items(SampleVideos) { url ->
                                        AssistChip(
                                            onClick = { videoUrl = url; urlError = null },
                                            label = { Text("ویدیو ${SampleVideos.indexOf(url) + 1}") },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = BrandCardLight,
                                                labelColor = BrandText
                                            )
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                TextButton(
                                    onClick = { videoUrl = ""; linkExpanded = false },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("بدون ویدیو بساز", color = BrandTextMuted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // ---------- رمز (اختیاری) ----------
            AnimatedIn(240) {
                SectionLabel("رمز اتاق — اختیاری")
                Spacer(Modifier.height(8.dp))
                HamTextField(password, { password = it }, label = "رمز اتاق", placeholder = "فقط مهمان‌هایی که رمز دارند", password = true)
            }

            Spacer(Modifier.height(18.dp))

            // ---------- آواتار ----------
            AnimatedIn(320) {
                SectionLabel("آواتارت")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    items(Avatars) { a ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { avatar = a }
                                .then(
                                    if (a == avatar)
                                        Modifier.background(BrandGradientSoft)
                                    else Modifier
                                )
                                .padding(4.dp)
                        ) {
                            AvatarChip(emoji = a, size = 46.dp)
                        }
                    }
                }
            }

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = BrandDanger, fontSize = 13.sp)
            }

            Spacer(Modifier.height(28.dp))

            // ---------- دکمه ساخت ----------
            AnimatedIn(400) {
                GradientButton(
                    text = "🎉 ساخت اتاق و شروع",
                    loading = creating,
                    onClick = {
                        val v = videoUrl.trim()
                        if (v.isNotBlank() && !v.startsWith("http")) {
                            error = "لینک ویدیو معتبر نیست — فقط http/https"
                            return@GradientButton
                        }
                        creating = true
                        error = null
                        vm.createRoom(
                            name.ifBlank { "اتاق من" }, v, password, avatar
                        ) { res ->
                            creating = false
                            when (res) {
                                is RoomViewModel.RoomInfoResult.Success -> {
                                    TokenStore.avatar = avatar
                                    nav.navigate(Routes.room(res.code, res.password, v)) {
                                        launchSingleTop = true
                                    }
                                }
                                is RoomViewModel.RoomInfoResult.Error -> error = res.message
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "بدون ثبت‌نام هم می‌تونی اتاق بسازی",
                    fontSize = 11.sp,
                    color = BrandTextMuted,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}

// ============================================================
//  ورود با کد اتاق
// ============================================================
@Composable
fun JoinRoomScreen(nav: NavHostController) {
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(TokenStore.name.ifBlank { "مهمان" }) }
    var avatar by remember { mutableStateOf(TokenStore.avatar.ifBlank { "🎬" }) }
    var error by remember { mutableStateOf<String?>(null) }

    GradientBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(30.dp))
            AnimatedIn(0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedIcon(
                        icon = { modifier -> Icon(Icons.Default.Movie, null, tint = Color.White, modifier = modifier.size(30.dp)) },
                        size = 62.dp,
                        gradient = Brush.linearGradient(listOf(BrandCyan, BrandPurple))
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("ورود به اتاق", style = MaterialTheme.typography.headlineMedium)
                    Text("کد ۶ رقمی را از دوستت بگیر", color = BrandTextMuted, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(34.dp))

            AnimatedIn(120) {
                // کد اتاق — بزرگ و با فاصله‌گذاری
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6)
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("کد اتاق") },
                    placeholder = { Text("AB12CD", color = BrandTextMuted) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 10.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii
                    ),
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandCyan,
                        unfocusedBorderColor = BrandCardLight,
                        cursorColor = BrandCyan
                    )
                )
            }

            Spacer(Modifier.height(14.dp))
            AnimatedIn(200) {
                HamTextField(password, { password = it }, label = "رمز اتاق (اگر دارد)", password = true)
            }
            Spacer(Modifier.height(14.dp))
            AnimatedIn(260) {
                HamTextField(name, { name = it }, label = "نام نمایشی")
            }

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = BrandDanger, fontSize = 13.sp)
            }

            Spacer(Modifier.height(26.dp))
            AnimatedIn(340) {
                GradientButton(
                    text = "ورود به اتاق",
                    onClick = {
                        val c = code.trim()
                        if (c.length < 4) {
                            error = "کد اتاق را کامل وارد کن"
                            return@GradientButton
                        }
                        TokenStore.name = name.ifBlank { "مهمان" }
                        TokenStore.avatar = avatar
                        nav.navigate(Routes.room(c, password)) { launchSingleTop = true }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(10.dp))
            AnimatedIn(400) {
                TextButton(onClick = { nav.popBackStack() }) {
                    Text("بازگشت", color = BrandTextMuted)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// ============================================================
//  کامپوننت‌های مشترک این صفحه
// ============================================================

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BrandTextMuted)
}

/** آیکون انیمیشنی با هاله نبض‌دار و چرخش ملایم */
@Composable
fun AnimatedIcon(
    icon: @Composable (Modifier) -> Unit,
    size: androidx.compose.ui.unit.Dp = 52.dp,
    gradient: Brush = BrandGradient
) {
    val t = rememberInfiniteTransition(label = "icon")
    val pulse by t.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1400), repeatMode = RepeatMode.Reverse),
        label = "pulse"
    )
    val glow by t.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), repeatMode = RepeatMode.Reverse),
        label = "glow"
    )
    val rot by t.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(2200), repeatMode = RepeatMode.Reverse),
        label = "rot"
    )

    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
                alpha = 0.5f + glow * 0.5f
            }
            .background(BrandPurple.copy(alpha = 0.22f), CircleShape)
    )
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(gradient)
            .graphicsLayer { rotationZ = rot },
        contentAlignment = Alignment.Center
    ) {
        icon(Modifier)
    }
}

