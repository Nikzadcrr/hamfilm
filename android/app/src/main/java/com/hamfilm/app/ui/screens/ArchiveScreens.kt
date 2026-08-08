package com.hamfilm.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import coil.compose.AsyncImage
import com.hamfilm.app.data.api.AppRepository
import com.hamfilm.app.data.model.Genre
import com.hamfilm.app.data.model.Movie
import com.hamfilm.app.ui.components.*
import com.hamfilm.app.ui.navigation.Routes
import com.hamfilm.app.ui.theme.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.lazy.items
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.Download
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush

@Composable
fun ArchiveScreen(nav: NavHostController) {
    val repo = remember { AppRepository() }
    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var genres by remember { mutableStateOf<List<Genre>>(emptyList()) }
    var selectedGenre by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var page by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }

    suspend fun load(reset: Boolean) {
        if (reset) { page = 1; hasMore = true }
        val p = if (reset) 1 else page
        val res = repo.movies(p, selectedGenre, query.ifBlank { null })
        val list = res.movies
        movies = if (reset) list else movies + list
        hasMore = res.page < res.pages
        if (list.isNotEmpty()) page = p + 1
    }

    LaunchedEffect(Unit) {
        genres = repo.genres()
        load(true)
        loading = false
    }

    GradientBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowForward, "بازگشت", tint = BrandTextMuted) }
                Text("🎞️ آرشیو فیلم‌ها", style = MaterialTheme.typography.headlineSmall)
            }

            // جستجو
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("جستجوی فیلم…", color = BrandTextMuted) },
                shape = RoundedCornerShape(14.dp),
                leadingIcon = { Icon(Icons.Default.Search, null, tint = BrandTextMuted) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandCyan, unfocusedBorderColor = BrandCardLight)
            )

            // ژانرها
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedGenre == null,
                        onClick = { selectedGenre = null },
                        label = { Text("همه") }
                    )
                }
                items(genres) { g ->
                    FilterChip(
                        selected = selectedGenre == g.name,
                        onClick = { selectedGenre = g.name },
                        label = { Text(g.name) }
                    )
                }
            }

            LaunchedEffect(selectedGenre, query) {
                load(true)
            }

            // گرید فیلم‌ها
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandCyan)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(movies, key = { it.slug }) { m ->
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { nav.navigate(Routes.movie(m.slug)) }
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BrandCardLight),
                                contentAlignment = Alignment.Center
                            ) {
                                if (m.coverUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = m.coverUrl,
                                        contentDescription = m.displayTitle,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text("🎬", fontSize = 28.sp)
                                }
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(m.displayTitle, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            m.year?.takeIf { it > 0 }?.let {
                                Text("$it", fontSize = 10.sp, color = BrandTextMuted)
                            }
                        }
                    }
                    if (hasMore) {
                        item {
                            LaunchedEffect(Unit) { loadingMore = true; load(false); loadingMore = false }
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                if (loadingMore) CircularProgressIndicator(Modifier.size(24.dp), color = BrandCyan, strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MovieDetailScreen(nav: NavHostController, slug: String) {
    val repo = remember { AppRepository() }
    val context = LocalContext.current
    var movie by remember { mutableStateOf<Movie?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(slug) {
        try {
            movie = repo.movie(slug)
        } catch (e: Exception) {
            error = e.message
        }
    }

    GradientBackground(Modifier.fillMaxSize()) {
        val m = movie
        if (m == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (error != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("😕", fontSize = 40.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(error!!, color = BrandDanger)
                        TextButton(onClick = { nav.popBackStack() }) { Text("بازگشت", color = BrandCyan) }
                    }
                } else {
                    CircularProgressIndicator(color = BrandCyan)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().statusBarsPadding()) {
                // ---------- بک‌دراپ ----------
                item {
                    Box(Modifier.fillMaxWidth().height(300.dp)) {
                        if (m.coverUrl.isNotBlank()) {
                            AsyncImage(
                                model = m.coverUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                Modifier.fillMaxSize().background(BrandGradientSoft),
                                contentAlignment = Alignment.Center
                            ) { Text("🎬", fontSize = 80.sp) }
                        }
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    listOf(Color(0x33000000), BrandBg)
                                )
                            )
                        )
                        IconButton(onClick = { nav.popBackStack() }, modifier = Modifier.statusBarsPadding()) {
                            Icon(Icons.Default.ArrowForward, "بازگشت", tint = Color.White)
                        }
                    }
                }

                // ---------- اطلاعات اصلی ----------
                item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            // پوستر کوچک
                            Box(
                                Modifier
                                    .width(104.dp)
                                    .height(150.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(BrandCardLight)
                                    .shadow(10.dp, RoundedCornerShape(16.dp))
                            ) {
                                if (m.coverUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = m.coverUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("🎬", fontSize = 34.sp)
                                    }
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f).padding(top = 6.dp)) {
                                Text(m.displayTitle, style = MaterialTheme.typography.headlineSmall)
                                if (m.titleEn.isNotBlank()) {
                                    Text(m.titleEn, color = BrandTextMuted, fontSize = 12.sp)
                                }
                                Spacer(Modifier.height(10.dp))

                                // امتیازها
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    m.imdbRating?.let {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("⭐ $it", color = BrandAmber, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Text("IMDb", fontSize = 10.sp, color = BrandTextMuted)
                                        }
                                    }
                                    m.satisfaction?.let {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("😍 %$it", color = BrandGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Text("رضایت", fontSize = 10.sp, color = BrandTextMuted)
                                        }
                                    }
                                    if (m.views > 0) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("👁 ${m.views}", color = BrandText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Text("بازدید", fontSize = 10.sp, color = BrandTextMuted)
                                        }
                                    }
                                }

                                Spacer(Modifier.height(8.dp))
                                // نوار رضایت
                                m.satisfaction?.let { sat ->
                                    LinearProgressIndicator(
                                        progress = { sat / 100f },
                                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                                        color = BrandGreen,
                                        trackColor = BrandCardLight
                                    )
                                }
                            }
                        }

                        // ---------- متا ----------
                        Spacer(Modifier.height(16.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            m.year?.takeIf { it > 0 }?.let { InfoBadge("📅 $it", Modifier.weight(1f)) }
                            m.country.takeIf { it.isNotBlank() }?.let { InfoBadge("🌍 $it", Modifier.weight(1f)) }
                            m.durationMin?.let { InfoBadge("⏱ ${it} دقیقه", Modifier.weight(1f)) }
                            m.ageRating.takeIf { it.isNotBlank() }?.let { InfoBadge("🔞 $it", Modifier.weight(1f)) }
                        }
                        if (m.language.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            InfoBadge("🗣 $m.language", Modifier.weight(1f))
                        }

                        // ---------- ژانرها ----------
                        if (m.genres.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                m.genres.forEach { g ->
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(BrandPurple.copy(alpha = 0.15f))
                                            .padding(horizontal = 12.dp, vertical = 5.dp)
                                    ) {
                                        Text(g, fontSize = 12.sp, color = BrandPurple)
                                    }
                                }
                            }
                        }

                        // ---------- توضیحات ----------
                        Spacer(Modifier.height(18.dp))
                        Text("درباره فیلم", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            m.description.ifBlank { "توضیحی ثبت نشده است." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = BrandTextMuted,
                            lineHeight = 22.sp
                        )

                        // ---------- دکمه‌ها ----------
                        Spacer(Modifier.height(20.dp))
                        GradientButton(
                            text = "▶ پخش در اتاق",
                            onClick = { nav.navigate(Routes.CREATE) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (m.sourceUrl.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(m.sourceUrl))
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandCyan)
                            ) {
                                Text("🔗 پخش آنلاین (سایت)", fontWeight = FontWeight.Bold)
                            }
                        }

                        // ---------- لینک‌های دانلود ----------
                        if (m.downloadLinks.isNotEmpty()) {
                            Spacer(Modifier.height(24.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⬇️ لینک‌های دانلود", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(BrandCyan.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("${m.downloadLinks.size} لینک", fontSize = 10.sp, color = BrandCyan, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            m.downloadLinks.forEachIndexed { idx, link ->
                                DownloadRow(link = link, index = idx)
                                Spacer(Modifier.height(8.dp))
                            }
                        }

                        Spacer(Modifier.height(26.dp))
                    }
                }
            }
        }
    }
}

// ---------- ردیف لینک دانلود ----------
@Composable
private fun DownloadRow(link: com.hamfilm.app.data.model.DownloadLink, index: Int) {
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BrandCard)
            .clickable {
                if (link.url.isNotBlank()) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                }
            }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // استخراج کیفیت و حجم از برچسب لینک (بک‌اند: label شامل 1080p و حجم است)
            val quality = Regex("(\\d{3,4}p|4K|HD)").find(link.label)?.value ?: "HD"
            val size = Regex("([\\d.]+\\s*(GB|MB))").find(link.label)?.value ?: ""
            // آیکون کیفیت
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (quality.contains("4K") || quality.contains("1080"))
                            BrandGreen.copy(alpha = 0.15f)
                        else BrandCyan.copy(alpha = 0.13f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    quality,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (quality.contains("4K") || quality.contains("1080")) BrandGreen else BrandCyan
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    link.label.ifBlank { "لینک دانلود ${index + 1}" },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )
                if (size.isNotBlank()) {
                    Text(size, fontSize = 11.sp, color = BrandTextMuted)
                }
            }
            Icon(Icons.Default.Download, "دانلود", tint = BrandCyan, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun InfoBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(BrandCardLight)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 11.sp, maxLines = 1)
    }
}