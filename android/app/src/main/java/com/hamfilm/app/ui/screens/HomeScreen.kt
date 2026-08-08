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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import coil.compose.AsyncImage
import com.hamfilm.app.data.TokenStore
import com.hamfilm.app.data.api.AppRepository
import com.hamfilm.app.data.model.Movie
import com.hamfilm.app.data.model.PublicSettings
import com.hamfilm.app.ui.components.*
import com.hamfilm.app.ui.navigation.Routes
import com.hamfilm.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val repo = remember { AppRepository() }

    var settings by remember { mutableStateOf<PublicSettings?>(null) }
    var featured by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var showOnboarding by remember { mutableStateOf(!TokenStore.onboarded) }

    LaunchedEffect(Unit) {
        settings = repo.settings()
        featured = repo.featured()
    }

    GradientBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            // هدر
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("هم‌فیلم", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Spacer(Modifier.weight(1f))
                if (TokenStore.token.isNotBlank()) {
                    IconButton(onClick = { nav.navigate(Routes.PROFILE) }) {
                        Text(TokenStore.avatar, fontSize = 22.sp)
                    }
                } else {
                    TextButton(onClick = { nav.navigate(Routes.LOGIN) }) {
                        Text("ورود", color = BrandCyan)
                    }
                }
            }

            // پیام خوش‌آمد
            if (settings?.announcementActive == true && !settings?.announcement.isNullOrBlank()) {
                GlassCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("📣 ${settings!!.announcement}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                settings?.welcomeMessage ?: "خوش آمدید!",
                style = MaterialTheme.typography.titleMedium,
                color = BrandTextMuted,
                modifier = Modifier.padding(top = 8.dp)
            )

            // دکمه‌های اصلی
            Spacer(Modifier.height(20.dp))
            GradientButton(
                text = "🎬 ساخت اتاق جدید",
                onClick = { nav.navigate(Routes.CREATE) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { nav.navigate(Routes.JOIN) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BrandCyan.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandCyan)
            ) {
                Text("🔑 ورود با کد اتاق", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            // فیلم‌های ویژه
            if (featured.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Text("✨ فیلم‌های ویژه", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(featured, key = { it.slug }) { movie ->
                        MoviePoster(movie) { nav.navigate(Routes.movie(movie.slug)) }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { nav.navigate(Routes.ARCHIVE) },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("🎞️ مشاهده آرشیو فیلم‌ها", color = BrandTextMuted)
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showOnboarding) {
        OnboardingDialog(onFinish = {
            TokenStore.onboarded = true
            showOnboarding = false
        })
    }
}

@Composable
fun MoviePoster(movie: Movie, onClick: () -> Unit, width: Dp = 130.dp) {
    Column(
        Modifier
            .width(width)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(width * 1.4f)
                .clip(RoundedCornerShape(14.dp))
                .background(BrandCardLight),
            contentAlignment = Alignment.Center
        ) {
            if (movie.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = movie.coverUrl,
                    contentDescription = movie.displayTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text("🎬", fontSize = 34.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            movie.displayTitle,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        movie.imdbRating?.let {
            Text("⭐ $it", fontSize = 11.sp, color = BrandAmber)
        }
    }
}

// ---------- راهنمای قدم‌به‌قدم (الهام از اپ ببینیم) ----------
@Composable
fun OnboardingDialog(onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val steps = listOf(
        Triple("🎬", "اتاق بساز", "لینک فیلم یا ویدیوی یوتیوب را بگذار و یک کد اتاق بگیر."),
        Triple("🔑", "کد را بفرست", "کد را برای دوستانت بفرست؛ آن‌ها بدون ثبت‌نام وارد می‌شوند."),
        Triple("⚡", "هم‌زمان ببینید", "پخش ویدیو برای همه هماهنگ است؛ چت و واکنش لحظه‌ای هم دارید!")
    )
    AlertDialog(
        onDismissRequest = {},
        shape = RoundedCornerShape(24.dp),
        containerColor = BrandCard,
        title = { Text("خوش آمدید به هم‌فیلم 👋", textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val (emoji, title, desc) = steps[step]
                Text(emoji, fontSize = 52.sp)
                Spacer(Modifier.height(10.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(desc, style = MaterialTheme.typography.bodyMedium, color = BrandTextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Row {
                    steps.indices.forEach { i ->
                        Box(
                            Modifier
                                .padding(3.dp)
                                .size(if (i == step) 18.dp else 8.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (i == step) BrandGradient else BrandCardLight)
                        )
                    }
                }
            }
        },
        confirmButton = {
            GradientButton(
                text = if (step < 2) "بعدی" else "شروع!",
                onClick = {
                    if (step < 2) step++ else onFinish()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}
