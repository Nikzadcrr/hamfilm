package com.hamfilm.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
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

    GradientBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(20.dp)
        ) {
            Text("🎬 ساخت اتاق جدید", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(20.dp))
            HamTextField(name, { name = it }, "نام اتاق")
            Spacer(Modifier.height(14.dp))
            HamTextField(
                videoUrl, { videoUrl = it; urlError = null },
                "لینک ویدیو (MP4 / HLS / یوتیوب)",
                placeholder = "https://example.com/movie.mp4"
            )
            urlError?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = BrandDanger, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text("یا از لینک‌های نمونه انتخاب کن:", fontSize = 12.sp, color = BrandTextMuted)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                items(SampleVideos) { url ->
                    AssistChip(
                        onClick = { videoUrl = url; urlError = null },
                        label = { Text("ویدیو ${SampleVideos.indexOf(url) + 1}") }
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            HamTextField(password, { password = it }, "رمز اتاق (اختیاری)", password = true)

            Spacer(Modifier.height(16.dp))
            Text("آواتارت:", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                items(Avatars) { a ->
                    Box(
                        Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .clickable { avatar = a }
                            .then(
                                if (a == avatar)
                                    Modifier.background(BrandGradientSoft)
                                else Modifier
                            )
                            .padding(4.dp)
                    ) {
                        AvatarChip(emoji = a, size = 44.dp)
                    }
                }
            }

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = BrandDanger, fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            GradientButton(
                text = "ساخت اتاق و شروع",
                loading = creating,
                onClick = {
                    val v = videoUrl.trim()
                    if (v.isBlank()) { error = "لینک ویدیو را وارد کن"; return@GradientButton }
                    if (!v.startsWith("http")) { error = "فقط لینک‌های http/https معتبر هستند"; return@GradientButton }
                    creating = true; error = null
                    vm.createRoom(name.ifBlank { "اتاق من" }, v, password, avatar) { res ->
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
            Spacer(Modifier.height(16.dp))
        }
    }
}

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
                .padding(20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("🔑", fontSize = 44.sp)
            Text("ورود به اتاق", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
            HamTextField(
                code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(8); error = null },
                "کد اتاق",
                placeholder = "مثلاً AB12CD",
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii
            )
            Spacer(Modifier.height(14.dp))
            HamTextField(password, { password = it }, "رمز اتاق (اگر دارد)", password = true)
            Spacer(Modifier.height(14.dp))
            HamTextField(name, { name = it }, "نام نمایشی")
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = BrandDanger, fontSize = 13.sp)
            }
            Spacer(Modifier.height(20.dp))
            GradientButton("ورود به اتاق", onClick = {
                val c = code.trim()
                if (c.length < 4) { error = "کد اتاق را کامل وارد کن"; return@GradientButton }
                TokenStore.name = name.ifBlank { "مهمان" }
                TokenStore.avatar = avatar
                nav.navigate(Routes.room(c, password)) { launchSingleTop = true }
            }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { nav.popBackStack() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("بازگشت", color = BrandTextMuted)
            }
        }
    }
}
