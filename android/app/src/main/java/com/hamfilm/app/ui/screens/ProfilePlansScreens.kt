package com.hamfilm.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.hamfilm.app.ui.navigation.Routes
import com.hamfilm.app.R
import com.hamfilm.app.data.TokenStore
import com.hamfilm.app.data.api.AppRepository
import com.hamfilm.app.data.model.Plan
import com.hamfilm.app.ui.components.*
import com.hamfilm.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun PlansScreen(nav: NavHostController) {
    val repo = remember { AppRepository() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var plans by remember { mutableStateOf<List<Plan>>(emptyList()) }
    var enabled by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var buying by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val res = repo.plans()
        plans = res.plans
        enabled = res.enabled
        loading = false
    }

    GradientBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                BackGlassButton(onClick = { nav.popBackStack() })
                Text("💎 پلن‌های هم‌فیلم", style = MaterialTheme.typography.headlineSmall, color = BrandText)
            }
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = BrandCyan) }
            } else if (!enabled) {
                EmptyState("🔒", "اشتراک فعلاً غیرفعال است", "به‌زودی پلن‌های ویژه هم‌فیلم فعال می‌شوند.")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(plans, key = { it.id }) { plan ->
                        GlassCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(plan.name, style = MaterialTheme.typography.titleLarge, color = BrandText)
                                        if (plan.isPopular) {
                                            Spacer(Modifier.width(8.dp))
                                            StatusBar("محبوب", BrandAmber)
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text("${plan.usersPerRoom} نفر در اتاق • ${plan.durationDays} روز", color = BrandTextMuted, fontSize = 13.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    if (plan.discountPercent > 0) {
                                        Text(
                                            "${plan.priceToman} تومان",
                                            color = BrandTextMuted,
                                            fontSize = 12.sp,
                                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                                        )
                                    }
                                    Text("${plan.finalPriceToman} تومان", color = BrandCyan, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            plan.features.forEach { f ->
                                Row(Modifier.padding(vertical = 2.dp)) {
                                    Text("✅ ", fontSize = 13.sp, color = BrandText)
                                    Text(f, fontSize = 13.sp, color = BrandText)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            GradientButton(
                                text = if (buying == plan.id) "در حال اتصال به درگاه…" else "خرید اشتراک",
                                loading = buying == plan.id,
                                onClick = {
                                    buying = plan.id
                                    scope.launch {
                                        try {
                                            val res = repo.checkout(plan.id)
                                            if (res.redirectUrl.isNotBlank()) {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(res.redirectUrl)))
                                            }
                                        } catch (e: Exception) { /* خطا */ }
                                        buying = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(nav: NavHostController) {
    val repo = remember { AppRepository() }
    var sub by remember { mutableStateOf<com.hamfilm.app.data.model.SubscriptionInfo?>(null) }

    LaunchedEffect(Unit) {
        sub = if (TokenStore.token.isNotBlank()) repo.mySubscription()?.subscription else null
    }

    GradientBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))

            // ── کارت پروفایل: آواتار + اسم + وضعیت ──
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // قاب گرادیانی دور آواتار
                    Box(
                        Modifier
                            .size(104.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(BrandPurple.copy(alpha = 0.55f), BrandCyan.copy(alpha = 0.45f))
                                )
                            )
                            .padding(3.dp)
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(29.dp))
                                .background(Color(0xFF0E0E1C))
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AvatarChip(TokenStore.avatar, size = 86.dp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        TokenStore.name.ifBlank { "مهمان" },
                        style = MaterialTheme.typography.titleLarge,
                        color = BrandText,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "🎬 عضو هم‌فیلم",
                        fontSize = 12.sp,
                        color = BrandTextMuted
                    )
                    Spacer(Modifier.height(14.dp))
                    val active = sub != null && sub!!.endsAt > System.currentTimeMillis()
                    StatusBar(
                        if (active) "💎 اشتراک فعال تا " + java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault()).format(java.util.Date(sub!!.endsAt)) else "بدون اشتراک فعال",
                        if (active) BrandGreen else BrandTextMuted
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                MenuRow("🎟️ تیکت‌های پشتیبانی") { nav.navigate(Routes.TICKETS) }
                HorizontalDivider(color = BrandCardLight)
                MenuRow("💎 پلن‌ها و اشتراک") { nav.navigate(Routes.PLANS) }
                HorizontalDivider(color = BrandCardLight)
                MenuRow("🎬 آرشیو فیلم‌ها") { nav.navigate(Routes.ARCHIVE) }
                HorizontalDivider(color = BrandCardLight)
                MenuRow("⚙️ تنظیمات سرور") { nav.navigate(Routes.SETTINGS) }
                HorizontalDivider(color = BrandCardLight)
                MenuRow("📖 راهنمای استفاده") {
                    TokenStore.onboarded = false
                    nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                }
            }

            }

            Spacer(Modifier.weight(1f))
            if (TokenStore.token.isNotBlank()) {
                OutlinedButton(
                    onClick = {
                        TokenStore.clear()
                        nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandDanger)
                ) {
                    Text("خروج از حساب", fontWeight = FontWeight.Bold, color = BrandText)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

@Composable
private fun MenuRow(title: String, onClick: () -> Unit) {
    ScaleTap(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .padding(vertical = 14.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium, color = BrandText)
            Text("‹", color = BrandTextMuted, fontSize = 18.sp)
        }
    }
}
