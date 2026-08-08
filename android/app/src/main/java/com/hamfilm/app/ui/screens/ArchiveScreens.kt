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
        val list = repo.movies(p, selectedGenre, query.ifBlank { null })
        movies = if (reset) list else movies + list
        hasMore = list.isNotEmpty()
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
                IconButton(onClick = { nav.popBackStack() }) { Icon(androidx.compose.material.icons.Icons.Default.ArrowForward, "بازگشت", tint = BrandTextMuted) }
                Text("🎞️ آرشیو فیلم‌ها", style = MaterialTheme.typography.headlineSmall)
            }

            // جستجو
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("جستجوی فیلم…", color = BrandTextMuted) },
                shape = RoundedCornerShape(14.dp),
                leadingIcon = { Icon(androidx.compose.material.icons.Icons.Default.Search, null, tint = BrandTextMuted) },
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
                            m.year.takeIf { it > 0 }?.let {
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
                if (error != null) Text(error!!, color = BrandDanger) else CircularProgressIndicator(color = BrandCyan)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().statusBarsPadding()) {
                item {
                    Box(Modifier.fillMaxWidth().height(280.dp)) {
                        if (m.coverUrl.isNotBlank()) {
                            AsyncImage(model = m.coverUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Box(Modifier.fillMaxSize().background(BrandGradientSoft), contentAlignment = Alignment.Center) { Text("🎬", fontSize = 70.sp) }
                        }
                        Box(
                            Modifier.fillMaxSize().background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(Color.Transparent, BrandBg)
                                )
                            )
                        )
                        IconButton(onClick = { nav.popBackStack() }, modifier = Modifier.statusBarsPadding()) {
                            Icon(androidx.compose.material.icons.Icons.Default.ArrowForward, "بازگشت", tint = Color.White)
                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Text(m.displayTitle, style = MaterialTheme.typography.headlineMedium)
                        if (m.titleEn.isNotBlank()) Text(m.titleEn, color = BrandTextMuted, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            m.year.takeIf { it > 0 }?.let { InfoBadge("📅 $it") }
                            m.country.takeIf { it.isNotBlank() }?.let { InfoBadge("🌍 $it") }
                            m.durationMin?.let { InfoBadge("⏱️ ${it} دقیقه") }
                            m.imdbRating?.let { InfoBadge("⭐ $it") }
                        }
                        if (m.genres.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                m.genres.take(4).forEach { g -> InfoBadge(g) }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(m.description.ifBlank { "توضیحی ثبت نشده است." }, style = MaterialTheme.typography.bodyMedium, color = BrandTextMuted)
                        Spacer(Modifier.height(20.dp))

                        GradientButton(
                            text = "▶ پخش در اتاق",
                            onClick = { nav.navigate(Routes.CREATE) }
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("با زدن دکمه بالا، یک اتاق جدید با این فیلم می‌سازی و کدش را برای دوستانت می‌فرستی.", fontSize = 12.sp, color = BrandTextMuted)
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoBadge(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(BrandCardLight)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, fontSize = 11.sp)
    }
}
