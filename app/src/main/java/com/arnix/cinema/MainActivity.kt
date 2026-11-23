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
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg = Color(0xFFF5F7FB)
private val SurfaceDark = Color(0xFFFFFFFF)
private val Accent = Color(0xFF087FB5)
private val TextPrimary = Color(0xFF172333)
private val TextSecondary = Color(0xFF657386)
private val BorderSoft = Color(0xFFE4EAF1)
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
    val context = LocalContext.current
    var updateInfo by remember { mutableStateOf<AppUpdate?>(null) }
    var updateBusy by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }
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

    LaunchedEffect(Unit) {
        loadHome()
        updateInfo = withContext(Dispatchers.IO) {
            runCatching { AppUpdater.checkForUpdate(context) }.getOrNull()
        }
    }

    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600
    val destinations = listOf(
        "Главная" to Icons.Default.Home,
        "Фильмы" to Icons.Default.Movie,
        "Сериалы" to Icons.Default.Tv,
        "Поиск" to Icons.Default.Search,
        "Избранное" to Icons.Default.Favorite
    )

    MaterialTheme(colorScheme = lightColorScheme(
        background = Bg, surface = SurfaceDark, primary = Accent,
        onBackground = TextPrimary, onSurface = TextPrimary,
        surfaceVariant = Color(0xFFEEF3F8), onSurfaceVariant = TextSecondary,
        outline = BorderSoft
    )) {
        updateInfo?.let { update ->
            AlertDialog(
                onDismissRequest = { if (!updateBusy) updateInfo = null },
                title = { Text("Доступно обновление ARNIX Cinema") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Новая версия ${update.versionName}")
                        Text("Нажмите «Обновить» — приложение скачает APK и откроет системную установку.")
                        if (update.releaseNotes.isNotBlank()) Text(update.releaseNotes, color = TextSecondary)
                        updateError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (updateBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    TextButton(enabled = !updateBusy, onClick = {
                        scope.launch {
                            updateBusy = true
                            updateError = null
                            try {
                                if (AppUpdater.downloadAndInstall(context, update)) updateInfo = null
                            } catch (e: Exception) {
                                updateError = e.message ?: "Ошибка загрузки обновления."
                            } finally {
                                updateBusy = false
                            }
                        }
                    }) { Text(if (updateBusy) "Загрузка…" else "Обновить") }
                },
                dismissButton = {
                    TextButton(enabled = !updateBusy, onClick = { updateInfo = null }) { Text("Позже") }
                }
            )
        }
        if (compact) {
            Scaffold(
                containerColor = Bg,
                bottomBar = {
                    NavigationBar(
                        containerColor = Color.White,
                        contentColor = TextPrimary,
                        tonalElevation = 8.dp,
                        windowInsets = NavigationBarDefaults.windowInsets
                    ) {
                        destinations.forEach { (label, icon) ->
                            NavigationBarItem(
                                selected = tab == label && selected == null,
                                onClick = { tab = label; selected = null },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, fontSize = 10.sp, maxLines = 1) },
                                alwaysShowLabel = true,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Accent,
                                    selectedTextColor = Accent,
                                    indicatorColor = Accent.copy(alpha = 0.14f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    Modifier.fillMaxSize()
                        .padding(innerPadding)
                        .background(Bg)
                ) {
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
        } else {
            Row(Modifier.fillMaxSize().background(Bg)) {
                NavigationRail(
                    containerColor = Color(0xFF0D121D),
                    header = { Text("ARNIX", color = Accent, fontWeight = FontWeight.Black, modifier = Modifier.padding(14.dp)) }
                ) {
                    destinations.forEach { (label, icon) ->
                        NavigationRailItem(
                            selected = tab == label && selected == null,
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
}

@Composable
private fun HomeScreen(
    trending: List<MediaItem>, movies: List<MediaItem>, series: List<MediaItem>,
    loading: Boolean, error: String?, onSelect: (MediaItem) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(270.dp)) {
                val hero = trending.firstOrNull()
                if (hero?.backdropPath != null) AsyncImage(
                    IMAGE.replace("w500", "w1280") + hero.backdropPath,
                    contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Bg))))
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp, vertical = 20.dp)) {
                    Text("ARNIX CINEMA", color = Accent, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Text(hero?.displayTitle ?: "Ваш мир кино", fontSize = 27.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 2)
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
        Text(title, color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items) { item -> PosterCard(item, onSelect) }
        }
    }
}

@Composable
private fun CatalogScreen(title: String, items: List<MediaItem>, loading: Boolean, onSelect: (MediaItem) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(title, color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 12.dp))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(142.dp),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) { items(items) { PosterCard(it, onSelect) } }
    }
}

@Composable
private fun PosterCard(
    item: MediaItem,
    onSelect: (MediaItem) -> Unit,
    width: androidx.compose.ui.unit.Dp = 136.dp
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val cardWidth = if (configuration.screenWidthDp < 360) 116.dp else width
    Column(
        Modifier
            .width(cardWidth)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .onFocusChanged { }
            .focusable()
            .clickable { onSelect(item) }
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            AsyncImage(
                model = item.posterPath?.let { IMAGE + it },
                contentDescription = item.displayTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (item.voteAverage > 0) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(7.dp),
                    color = Color(0xEFFFFFFF),
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("★", color = Color(0xFFE5A000), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            " ${"%.1f".format(item.voteAverage)}",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                item.displayTitle.ifBlank { "Без названия" },
                color = TextPrimary,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 2,
                minLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                item.year?.toString()?.takeIf { it.isNotBlank() } ?: "Год не указан",
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SearchScreen(
    query: String, onQuery: (String) -> Unit, results: List<MediaItem>, loading: Boolean,
    onSearch: (String) -> Unit, onSelect: (MediaItem) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Поиск фильмов и сериалов", color = TextPrimary, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold)
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
                Text(item.displayTitle, color = TextPrimary, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black)
                Text("★ ${"%.1f".format(item.voteAverage)}   ${item.year}", color = Accent, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 16.dp)) {
                    Button(onClick = onFavorite) { Icon(Icons.Default.Favorite, null); Text(" В избранное") }
                    OutlinedButton(onClick = {}) { Text("Трейлер") }
                }
                Text("Описание", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(item.overview?.ifBlank { "Описание пока отсутствует." } ?: "Описание пока отсутствует.", color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
                Text("Информация предоставлена TMDB. Видеопотоки не входят в каталог.", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 24.dp))
            }
        }
    }
}
