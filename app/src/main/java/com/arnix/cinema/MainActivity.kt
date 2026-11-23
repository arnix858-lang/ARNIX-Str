package com.arnix.cinema

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.arnix.cinema.data.MediaItem
import com.arnix.cinema.data.TmdbApi
import kotlinx.coroutines.launch

private val Bg = Color(0xFF080B12)
private val SurfaceDark = Color(0xFF141A27)
private val Accent = Color(0xFF54C7F5)
private const val IMAGE = "https://image.tmdb.org/t/p/w500"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ArnixCinemaApp() }
    }
}

@Composable
fun ArnixCinemaApp() {
    val api = remember { TmdbApi.create() }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("Главная") }
    var trending by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var movies by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var series by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var results by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<MediaItem?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val favorites = remember { mutableStateListOf<MediaItem>() }

    fun loadHome() {
        if (TmdbKey.value.isBlank()) {
            error = "Добавьте TMDB_API_KEY в local.properties или секреты GitHub Actions."
            return
        }
        scope.launch {
            loading = true
            error = null
            try {
                trending = api.trending(TmdbKey.value).results
                movies = api.movies(TmdbKey.value).results
                series = api.tv(TmdbKey.value).results
            } catch (e: Exception) {
                error = "Не удалось загрузить каталог. Проверьте интернет и TMDB API-ключ."
            } finally { loading = false }
        }
    }

    LaunchedEffect(Unit) { loadHome() }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Bg, surface = SurfaceDark, primary = Accent,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Row(Modifier.fillMaxSize().background(Bg)) {
            NavigationRail(
                containerColor = Color(0xFF0D121D),
                header = { Text("ARNIX", color = Accent, fontWeight = FontWeight.Black, modifier = Modifier.padding(14.dp)) }
            ) {
                listOf(
                    "Главная" to Icons.Default.Home,
                    "Фильмы" to Icons.Default.Movie,
                    "Сериалы" to Icons.Default.Tv,
                    "Поиск" to Icons.Default.Search,
                    "Избранное" to Icons.Default.Favorite
                ).forEach { (label, icon) ->
                    NavigationRailItem(
                        selected = tab == label,
                        onClick = { tab = label; selected = null },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 10.sp) }
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when {
                    selected != null -> DetailScreen(selected!!, onBack = { selected = null }, onFavorite = {
                        if (favorites.none { f -> f.id == selected!!.id }) favorites.add(selected!!)
                    })
                    tab == "Поиск" -> SearchScreen(query, { query = it }, results, loading, { term ->
                        if (term.isNotBlank() && TmdbKey.value.isNotBlank()) scope.launch {
                            loading = true
                            try { results = api.search(TmdbKey.value, term).results }
                            catch (_: Exception) { error = "Ошибка поиска TMDB" }
                            finally { loading = false }
                        }
                    }, { selected = it })
                    tab == "Фильмы" -> CatalogScreen("Популярные фильмы", movies, loading, { selected = it })
                    tab == "Сериалы" -> CatalogScreen("Популярные сериалы", series, loading, { selected = it })
                    tab == "Избранное" -> CatalogScreen("Избранное", favorites, false, { selected = it })
                    else -> HomeScreen(trending, movies, series, loading, error, { selected = it })
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    trending: List<MediaItem>, movies: List<MediaItem>, series: List<MediaItem>,
    loading: Boolean, error: String?, onSelect: (MediaItem) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(290.dp)) {
                val hero = trending.firstOrNull()
                if (hero?.backdropPath != null) AsyncImage(
                    IMAGE.replace("w500", "w1280") + hero.backdropPath,
                    contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Bg))))
                Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                    Text("ARNIX CINEMA", color = Accent, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Text(hero?.displayTitle ?: "Ваш мир кино", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text("Фильмы • Сериалы • Мультфильмы", color = Color.LightGray)
                    if (hero != null) Button(onClick = { onSelect(hero) }, modifier = Modifier.padding(top = 12.dp)) { Text("Подробнее") }
                }
            }
        }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) }
        if (error != null) item { Text(error, color = Color(0xFFFF8A80), modifier = Modifier.padding(20.dp)) }
        item { MediaRow("Сейчас в тренде", trending, onSelect) }
        item { MediaRow("Популярные фильмы", movies, onSelect) }
        item { MediaRow("Популярные сериалы", series, onSelect) }
    }
}

@Composable
private fun MediaRow(title: String, items: List<MediaItem>, onSelect: (MediaItem) -> Unit) {
    Column(Modifier.padding(top = 18.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items) { item -> PosterCard(item, onSelect) }
        }
    }
}

@Composable
private fun CatalogScreen(title: String, items: List<MediaItem>, loading: Boolean, onSelect: (MediaItem) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(24.dp))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(130.dp),
            contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) { items(items) { PosterCard(it, onSelect, width = 140.dp) } }
    }
}

@Composable
private fun PosterCard(item: MediaItem, onSelect: (MediaItem) -> Unit, width: androidx.compose.ui.unit.Dp = 120.dp) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(width).clip(RoundedCornerShape(12.dp)).background(SurfaceDark)
        .onFocusChanged { focused = it.isFocused }.focusable().clickable { onSelect(item) }) {
        AsyncImage(
            model = item.posterPath?.let { IMAGE + it },
            contentDescription = item.displayTitle,
            modifier = Modifier.fillMaxWidth().height(180.dp),
            contentScale = ContentScale.Crop
        )
        Text(item.displayTitle, fontWeight = FontWeight.SemiBold, maxLines = 2, modifier = Modifier.padding(8.dp))
        Text("★ ${"%.1f".format(item.voteAverage)}  ${item.year}", color = Accent, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
    }
}

@Composable
private fun SearchScreen(
    query: String, onQuery: (String) -> Unit, results: List<MediaItem>, loading: Boolean,
    onSearch: (String) -> Unit, onSelect: (MediaItem) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Поиск фильмов и сериалов", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, onQuery, modifier = Modifier.weight(1f), label = { Text("Название") }, singleLine = true)
            Button(onClick = { onSearch(query) }, modifier = Modifier.padding(start = 8.dp)) { Text("Найти") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(130.dp),
            contentPadding = PaddingValues(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) { items(results) { PosterCard(it, onSelect, 140.dp) } }
    }
}

@Composable
private fun DetailScreen(item: MediaItem, onBack: () -> Unit, onFavorite: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                AsyncImage(
                    model = item.backdropPath?.let { IMAGE.replace("w500", "w1280") + it },
                    contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Bg))))
                Text("‹ Назад", color = Color.White, modifier = Modifier.align(Alignment.TopStart).padding(20.dp).clickable { onBack() })
            }
        }
        item {
            Column(Modifier.padding(22.dp)) {
                Text(item.displayTitle, fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text("★ ${"%.1f".format(item.voteAverage)}   ${item.year}", color = Accent, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 16.dp)) {
                    Button(onClick = onFavorite) { Icon(Icons.Default.Favorite, null); Text(" В избранное") }
                    OutlinedButton(onClick = {}) { Text("Трейлер") }
                }
                Text("Описание", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(item.overview?.ifBlank { "Описание пока отсутствует." } ?: "Описание пока отсутствует.", color = Color.LightGray, modifier = Modifier.padding(top = 8.dp))
                Text("Информация предоставлена TMDB. Видеопотоки не входят в каталог.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 24.dp))
            }
        }
    }
}
