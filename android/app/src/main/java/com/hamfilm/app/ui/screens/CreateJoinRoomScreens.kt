package com.hamfilm.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.hamfilm.app.R
import com.hamfilm.app.data.TokenStore
import com.hamfilm.app.ui.components.*
import com.hamfilm.app.ui.navigation.Routes
import com.hamfilm.app.ui.theme.*
import com.hamfilm.app.viewmodel.RoomViewModel

// لینک‌های نمونه برای تست سریع
private val SampleVideos = listOf(
    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
    "https://test-videos.co.uk/vids/bigbuckbunny/mkv/720/Big_Buck_Bunny_720_10s_1MB.mkv",
    "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4"
)

@Composable
fun CreateRoomScreen(nav: NavHostController) {
    val vm = remember { RoomViewModel() }
    var name by remember { mutableStateOf(TokenStore.name.ifBlank { "اتاق من" }) }
    var videoUrl by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf(TokenStore.avatar.ifBlank { "a1" }) }
    var error by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf<String?>(null) }

    GradientBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // ── هدر: دکمه بازگشت شیشه‌ای + عنوان ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScaleTap(onClick = { nav.popBackStack() }) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(com.hamfilm.app.R.drawable.ic_hf_back),
                            "بازگشت",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("ساخت اتاق جدید", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = BrandText)
                    Text("بعد از ساخت، یک کد و لینک دعوت یکتا می‌گیری", fontSize = 12.sp, color = BrandTextMuted)
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── کارت جزئیات اتاق — شیشه‌ای با border ظریف ──
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                    .padding(16.dp)
            ) {
                Text("جزئیات اتاق", fontWeight = FontWeight.Black, fontSize = 15.sp, color = BrandText)
                Spacer(Modifier.height(4.dp))
                Text(
                    "لینک فیلم اختیاری است — می‌توانی بعداً داخل اتاق اضافه‌اش کنی.",
                    fontSize = 11.sp,
                    color = BrandTextMuted
                )

                Spacer(Modifier.height(16.dp))

                // ── نام اتاق ──
                SectionLabel("نام اتاق")
                HamTextField(
                    name, { name = it },
                    "نام اتاق",
                    placeholder = "مثلاً: شب فیلم با رها 🤍"
                )

                Spacer(Modifier.height(14.dp))

                // ── لینک ویدیو (اختیاری) ──
                SectionLabel("لینک فیلم (اختیاری)")
                HamTextField(
                    videoUrl, { videoUrl = it; urlError = null },
                    "لینک ویدیو (MP4 / HLS / یوتیوب)",
                    placeholder = "https://example.com/movie.mp4"
                )
                if (urlError != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(urlError!!, color = BrandDanger, fontSize = 12.sp)
                }

                Spacer(Modifier.height(10.dp))
                Text("یا از لینک‌های نمونه:", fontSize = 11.sp, color = BrandTextMuted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    items(SampleVideos) { url ->
                        AssistChip(
                            onClick = { videoUrl = url; urlError = null },
                            label = { Text("ویدیو ${SampleVideos.indexOf(url) + 1}", fontSize = 12.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = BrandCardLight,
                                labelColor = BrandText
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BrandCardLight)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ── رمز اتاق (اختیاری) ──
                SectionLabel("رمز اتاق (اختیاری)")
                HamTextField(
                    password, { password = it },
                    "رمز اتاق (اختیاری)",
                    placeholder = "فقط مهمان‌هایی که رمز دارند وارد می‌شوند",
                    password = true
                )

                Spacer(Modifier.height(16.dp))

                // ── آواتار ──
                SectionLabel("آواتارت:")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                    items(AvatarIds) { a ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { avatar = a }
                                .then(
                                    if (a == avatar)
                                        Modifier.background(BrandGradientSoft)
                                    else Modifier
                                )
                                .padding(4.dp)
                        ) {
                            AvatarChip(avatarId = a, size = 46.dp)
                        }
                    }
                }
            }

            // ── خطا ──
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = BrandDanger, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(18.dp))

            // ── دکمه ساخت ──
            GradientButton(
                text = "ساخت اتاق و شروع",
                loading = creating,
                onClick = {
                    val v = videoUrl.trim()
                    // لینک اختیاری است — فقط اگر وارد شده باشد اعتبارسنجی کن
                    if (v.isNotBlank() && !v.startsWith("http")) {
                        urlError = "فقط لینک‌های http/https معتبر هستند"
                        return@GradientButton
                    }
                    creating = true; error = null
                    vm.createRoom(name.ifBlank { "اتاق من" }, v, password, avatar) { res ->
                        creating = false
                        when (res) {
                            is RoomViewModel.RoomInfoResult.Success -> {
                                TokenStore.avatar = avatar
                                nav.navigate(Routes.room(res.code, password, v)) {
                                    launchSingleTop = true
                                }
                            }
                            is RoomViewModel.RoomInfoResult.Error -> error = res.message
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            Text(
                "با ساخت اتاق، میزبان می‌شوی و کنترل تغییر فیلم با توست.",
                fontSize = 11.sp,
                color = BrandTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun JoinRoomScreen(nav: NavHostController) {
    val vm = remember { RoomViewModel() }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(TokenStore.name.ifBlank { "مهمان" }) }
    var avatar by remember { mutableStateOf(TokenStore.avatar.ifBlank { "a1" }) }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    GradientBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(4.dp))
            // دکمه بازگشت شیشه‌ای
            ScaleTap(onClick = { nav.popBackStack() }, modifier = Modifier.align(Alignment.Start)) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(com.hamfilm.app.R.drawable.ic_hf_back),
                        "بازگشت",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(listOf(BrandPurple.copy(alpha = 0.35f), BrandCyan.copy(alpha = 0.25f)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("🔑", fontSize = 30.sp, color = BrandText)
            }
            Spacer(Modifier.height(14.dp))
            Text("ورود به اتاق", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = BrandText)
            Text("کد ۶ رقمی یا لینک دعوتی که دوستت فرستاده را وارد کن", fontSize = 12.sp, color = BrandTextMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                    .padding(16.dp)
            ) {
                SectionLabel("کد یا لینک دعوت *")
                HamTextField(
                    code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(8); error = null },
                    "کد اتاق",
                    placeholder = "مثلاً AB12CD",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii
                )
                Spacer(Modifier.height(14.dp))
                SectionLabel("رمز اتاق (اگر دارد)")
                HamTextField(password, { password = it }, "رمز اتاق (اگر دارد)", password = true)
                Spacer(Modifier.height(14.dp))
                SectionLabel("نام نمایشی")
                HamTextField(name, { name = it }, "نام نمایشی")

                Spacer(Modifier.height(16.dp))
                SectionLabel("آواتارت:")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                    items(AvatarIds) { a ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { avatar = a }
                                .then(
                                    if (a == avatar)
                                        Modifier.background(BrandGradientSoft)
                                    else Modifier
                                )
                                .padding(4.dp)
                        ) {
                            AvatarChip(avatarId = a, size = 46.dp)
                        }
                    }
                }
            }

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = BrandDanger, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(18.dp))
            GradientButton(
                text = "ورود به اتاق",
                loading = checking,
                onClick = {
                    val c = code.trim().uppercase()
                    if (c.length != 6 || !c.all { it.isLetterOrDigit() }) { error = "کد اتاق ۶ کاراکتری است"; return@GradientButton }
                    error = null
                    checking = true
                    vm.checkRoom(c) { res ->
                        checking = false
                        when (res) {
                            is RoomViewModel.RoomInfoResult.Success -> {
                                if (res.hasPassword && password.isBlank()) {
                                    error = "این اتاق رمز دارد — رمز را وارد کن"
                                    return@checkRoom
                                }
                                TokenStore.name = name.ifBlank { "مهمان" }
                                TokenStore.avatar = avatar
                                nav.navigate(Routes.room(c, password)) { launchSingleTop = true }
                            }
                            is RoomViewModel.RoomInfoResult.Error -> error = res.message
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
}
