package com.arnix.cinema

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.VideoLibrary
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
import com.arnix.cinema.data.TmdbGenre
import com.arnix.cinema.data.TmdbDetails
import com.arnix.cinema.data.TmdbCast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

private val Bg = Color(0xFF090D14)
private val SurfaceDark = Color(0xFF141B26)
private val Accent = Color(0xFF45C4D6)
private val TextPrimary = Color(0xFFF3F6FA)
private val TextSecondary = Color(0xFFA1ADBC)
private val BorderSoft = Color(0xFF273342)
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
    var catalogFilter by remember { mutableStateOf("Все") }
    var catalogGenre by remember { mutableStateOf(0) }
    var catalogYear by remember { mutableStateOf("Любой") }
    var catalogGenres by remember { mutableStateOf<List<TmdbGenre>>(emptyList()) }
    var catalogItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var catalogLoading by remember { mutableStateOf(false) }
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
        if (TmdbKey.value.isNotBlank()) {
            runCatching {
                val movieGenres = api.movieGenres(TmdbKey.value).genres
                val tvGenres = api.tvGenres(TmdbKey.value).genres
                catalogGenres = (movieGenres + tvGenres).distinctBy { it.id }.sortedBy { it.name }
            }
        }
        updateInfo = withContext(Dispatchers.IO) {
            runCatching { AppUpdater.checkForUpdate(context) }.getOrNull()
        }
    }

    LaunchedEffect(tab, query) {
        val term = query.trim()
        if (tab != "Поиск") return@LaunchedEffect
        if (term.length < 2) {
            results = emptyList()
            loading = false
            return@LaunchedEffect
        }
        delay(450)
        if (TmdbKey.value.isBlank()) {
            error = "Добавьте TMDB_API_KEY в настройках сборки."
            return@LaunchedEffect
        }
        loading = true
        error = null
        try {
            results = api.search(TmdbKey.value, term).results
        } catch (_: Exception) {
            error = "Не удалось выполнить поиск. Проверьте интернет."
            results = emptyList()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(tab, catalogFilter, catalogGenre, catalogYear) {
        if (tab != "Каталог") return@LaunchedEffect
        if (TmdbKey.value.isBlank()) return@LaunchedEffect
        catalogLoading = true
        try {
            val selectedYear = catalogYear.toIntOrNull()
            val selectedGenre = catalogGenre.takeIf { it != 0 }
            val movieResults = if (catalogFilter != "Сериалы") {
                api.discoverMovies(TmdbKey.value, genreId = selectedGenre, year = selectedYear)
                    .results.map { it.copy(media_type = "movie") }
            } else emptyList()
            val tvResults = if (catalogFilter != "Фильмы") {
                api.discoverTv(TmdbKey.value, genreId = selectedGenre, year = selectedYear)
                    .results.map { it.copy(media_type = "tv") }
            } else emptyList()
            catalogItems = movieResults + tvResults
        } catch (_: Exception) {
            catalogItems = emptyList()
            error = "Не удалось загрузить каталог по выбранным фильтрам."
        } finally {
            catalogLoading = false
        }
    }

    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600
    val destinations = listOf(
        "Главная" to Icons.Default.Home,
        "Каталог" to Icons.Default.VideoLibrary,
        "Поиск" to Icons.Default.Search,
        "Избранное" to Icons.Default.Favorite
    )

    MaterialTheme(colorScheme = darkColorScheme(
        background = Bg, surface = SurfaceDark, primary = Accent,
        onBackground = TextPrimary, onSurface = TextPrimary,
        surfaceVariant = Color(0xFF202B38), onSurfaceVariant = TextSecondary,
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
                        if (update.releaseNotes.isNotBlank()) {
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 320.dp)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                update.releaseNotes.lines().filter { it.isNotBlank() }.forEach { line ->
                                    val heading = line.trim().removePrefix("#").trim()
                                    val isHeading = line.trim().startsWith("#") ||
                                        heading.equals("Добавлено", true) ||
                                        heading.equals("Переработано", true) ||
                                        heading.equals("Удалено", true) ||
                                        heading.equals("Исправлено", true)
                                    if (isHeading) {
                                        Surface(
                                            color = Accent.copy(alpha = 0.10f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = heading,
                                                color = Accent,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = line.trim().removePrefix("-").trim(),
                                            color = TextSecondary,
                                            fontWeight = FontWeight.Normal,
                                            fontSize = 13.sp,
                                            lineHeight = 21.sp,
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
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
                        containerColor = Color(0xFF111923),
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
                        selected != null -> DetailScreen(selected!!, api = api, apiKey = TmdbKey.value, onBack = { selected = null }, onFavorite = {
                            if (favorites.none { f -> f.id == selected!!.id }) favorites.add(selected!!)
                        })
                        tab == "Поиск" -> SearchScreen(query, { query = it }, results, loading, error, { selected = it })
                        tab == "Каталог" -> CatalogScreen(
                            items = catalogItems,
                            loading = catalogLoading,
                            genres = catalogGenres,
                            filter = catalogFilter,
                            genreId = catalogGenre,
                            year = catalogYear,
                            onFilter = { catalogFilter = it },
                            onGenre = { catalogGenre = it },
                            onYear = { catalogYear = it },
                            onSelect = { selected = it }
                        )
                        tab == "Избранное" -> CatalogScreen(
                            items = favorites, loading = false, filter = "Все",
                            onFilter = {}, onSelect = { selected = it }, title = "Избранное"
                        )
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
                        selected != null -> DetailScreen(selected!!, api = api, apiKey = TmdbKey.value, onBack = { selected = null }, onFavorite = {
                            if (favorites.none { f -> f.id == selected!!.id }) favorites.add(selected!!)
                        })
                        tab == "Поиск" -> SearchScreen(query, { query = it }, results, loading, error, { selected = it })
                        tab == "Каталог" -> CatalogScreen(
                            items = catalogItems,
                            loading = catalogLoading,
                            genres = catalogGenres,
                            filter = catalogFilter,
                            genreId = catalogGenre,
                            year = catalogYear,
                            onFilter = { catalogFilter = it },
                            onGenre = { catalogGenre = it },
                            onYear = { catalogYear = it },
                            onSelect = { selected = it }
                        )
                        tab == "Избранное" -> CatalogScreen(
                            items = favorites, loading = false, filter = "Все",
                            onFilter = {}, onSelect = { selected = it }, title = "Избранное"
                        )
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
private fun CatalogScreen(
    items: List<MediaItem>,
    loading: Boolean,
    filter: String,
    onFilter: (String) -> Unit,
    onSelect: (MediaItem) -> Unit,
    genres: List<TmdbGenre> = emptyList(),
    genreId: Int = 0,
    year: String = "Любой",
    onGenre: (Int) -> Unit = {},
    onYear: (String) -> Unit = {},
    title: String = "Каталог"
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isTvLayout = configuration.screenWidthDp >= 600
    val typeFilters = listOf("Все", "Фильмы", "Сериалы")
    val selectedGenreName = genres.firstOrNull { it.id == genreId }?.name ?: "Все жанры"
    val years = listOf("Любой") + (2026 downTo 1950).map { it.toString() }
    var genreMenu by remember { mutableStateOf(false) }
    var yearMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                start = if (isTvLayout) 30.dp else 18.dp,
                end = if (isTvLayout) 30.dp else 18.dp,
                top = if (isTvLayout) 26.dp else 18.dp,
                bottom = 10.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = TextPrimary, fontSize = if (isTvLayout) 30.sp else 25.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (title == "Каталог") {
                Text("TMDB", color = TextSecondary, fontSize = if (isTvLayout) 14.sp else 11.sp)
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = if (isTvLayout) 26.dp else 14.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isTvLayout) 12.dp else 8.dp)
        ) {
            items(typeFilters) { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilter(option) },
                    label = { Text(option, fontSize = if (isTvLayout) 16.sp else 13.sp) },
                    modifier = Modifier.heightIn(min = if (isTvLayout) 48.dp else 40.dp).focusable(),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Accent.copy(alpha = 0.14f),
                        selectedLabelColor = Accent
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = if (isTvLayout) 26.dp else 16.dp,
                vertical = if (isTvLayout) 14.dp else 8.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(if (isTvLayout) 14.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1.35f)) {
                OutlinedButton(
                    onClick = { genreMenu = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = if (isTvLayout) 54.dp else 46.dp).focusable(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("Жанр: $selectedGenreName", maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        fontSize = if (isTvLayout) 15.sp else 12.sp, modifier = Modifier.weight(1f))
                    Text("▾", fontSize = 17.sp)
                }
                DropdownMenu(
                    expanded = genreMenu,
                    onDismissRequest = { genreMenu = false },
                    modifier = Modifier.heightIn(max = if (isTvLayout) 480.dp else 360.dp).background(Color.White)
                ) {
                    DropdownMenuItem(text = { Text("Все жанры") }, onClick = { onGenre(0); genreMenu = false })
                    genres.forEach { genre ->
                        DropdownMenuItem(
                            text = { Text(genre.name) },
                            onClick = { onGenre(genre.id); genreMenu = false }
                        )
                    }
                }
            }
            Box(Modifier.weight(0.85f)) {
                OutlinedButton(
                    onClick = { yearMenu = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = if (isTvLayout) 54.dp else 46.dp).focusable(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(if (year == "Любой") "Год: любой" else "Год: $year",
                        maxLines = 1, fontSize = if (isTvLayout) 15.sp else 12.sp)
                    Text("▾", fontSize = 17.sp)
                }
                DropdownMenu(
                    expanded = yearMenu,
                    onDismissRequest = { yearMenu = false },
                    modifier = Modifier.heightIn(max = if (isTvLayout) 480.dp else 360.dp).background(Color.White)
                ) {
                    years.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(if (option == "Любой") "Любой год" else option) },
                            onClick = { onYear(option); yearMenu = false }
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(
                start = if (isTvLayout) 30.dp else 18.dp,
                end = if (isTvLayout) 30.dp else 18.dp,
                top = 2.dp,
                bottom = 8.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (loading) "Загружаем подборку…" else "Найдено: ${items.size}",
                color = TextSecondary, fontSize = if (isTvLayout) 14.sp else 12.sp)
            Spacer(Modifier.weight(1f))
            if (genreId != 0 || year != "Любой" || filter != "Все") {
                TextButton(onClick = {
                    onFilter("Все")
                    onGenre(0)
                    onYear("Любой")
                }) { Text("Сбросить", fontSize = if (isTvLayout) 14.sp else 12.sp) }
            }
        }

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!loading && items.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Ничего не найдено", color = TextPrimary,
                    fontSize = if (isTvLayout) 22.sp else 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Измените жанр, год или тип контента", color = TextSecondary,
                    fontSize = if (isTvLayout) 16.sp else 13.sp,
                    modifier = Modifier.padding(top = 8.dp))
            }
        } else {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(
                    if (isTvLayout) 190.dp else 142.dp
                ),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(
                    start = if (isTvLayout) 28.dp else 14.dp,
                    end = if (isTvLayout) 28.dp else 14.dp,
                    top = 10.dp,
                    bottom = 28.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(if (isTvLayout) 20.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (isTvLayout) 22.dp else 16.dp)
            ) {
                items(items) { PosterCard(it, onSelect, if (isTvLayout) 190.dp else 136.dp) }
            }
        }
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
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .width(cardWidth)
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) Accent else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(if (focused) 3.dp else 0.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable { onSelect(item) }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = item.posterPath?.let { IMAGE + it },
                contentDescription = item.displayTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(94.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xE6000000))
                        )
                    )
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 9.dp, end = 9.dp, bottom = 9.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    item.displayTitle.ifBlank { "Без названия" },
                    color = Color.White,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    item.year?.toString()?.takeIf { it.isNotBlank() } ?: "Год не указан",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
            if (item.voteAverage > 0) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    color = Color(0xEFFFFFFF),
                    shape = RoundedCornerShape(7.dp),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("★", color = Color(0xFFE5A000), fontSize = 11.sp)
                        Text(
                            " ${"%.1f".format(item.voteAverage)}",
                            color = TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchScreen(
    query: String, onQuery: (String) -> Unit, results: List<MediaItem>, loading: Boolean,
    error: String?, onSelect: (MediaItem) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Поиск фильмов и сериалов", color = TextPrimary, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            label = { Text("Название фильма или сериала") },
            placeholder = { Text("Начните вводить название…") },
            singleLine = true,
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Очистить поиск")
                    }
                }
            }
        )
        Text(
            text = when {
                query.trim().length < 2 -> "Введите минимум 2 символа — поиск начнётся автоматически"
                loading -> "Ищем…"
                error != null -> error
                results.isEmpty() -> "Ничего не найдено"
                else -> "Найдено: ${results.size}"
            },
            color = if (error != null) MaterialTheme.colorScheme.error else TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
        )
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(130.dp),
            contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) { items(results) { PosterCard(it, onSelect, 140.dp) } }
    }
}

@Composable
private fun DetailScreen(
    item: MediaItem,
    api: TmdbApi,
    apiKey: String,
    onBack: () -> Unit,
    onFavorite: () -> Unit
) {
    var details by remember(item.id) { mutableStateOf<TmdbDetails?>(null) }
    var cast by remember(item.id) { mutableStateOf<List<TmdbCast>>(emptyList()) }
    var loadingDetails by remember(item.id) { mutableStateOf(true) }
    var detailsError by remember(item.id) { mutableStateOf(false) }
    val type = if (item.media_type == "tv" || item.firstAirDate != null) "tv" else "movie"

    LaunchedEffect(item.id, type, apiKey) {
        if (apiKey.isBlank()) {
            loadingDetails = false
            detailsError = true
        } else {
            loadingDetails = true
            try {
                val loaded = api.fullDetails(type, item.id, apiKey)
                details = loaded
                cast = runCatching { api.credits(type, item.id, apiKey).cast.take(12) }.getOrDefault(emptyList())
            } catch (_: Exception) {
                detailsError = true
            } finally {
                loadingDetails = false
            }
        }
    }

    val info = details
    val backdrop = info?.backdropPath ?: item.backdropPath
    val poster = info?.posterPath ?: item.posterPath
    val title = info?.displayTitle ?: item.displayTitle
    val rating = info?.voteAverage?.takeIf { it > 0 } ?: item.voteAverage
    val year = info?.year?.ifBlank { item.year } ?: item.year
    val scroll = rememberScrollState()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().background(Bg).verticalScroll(scroll)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 250.dp, max = 390.dp)) {
            AsyncImage(
                model = backdrop?.let { "https://image.tmdb.org/t/p/w1280$it" },
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0x33000000), Color(0xB9090D14), Bg))
            ))
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp).clickable { onBack() },
                color = Color(0xCC111923),
                shape = RoundedCornerShape(50),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .16f))
            ) {
                Row(Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = Color.White)
                    Text("Назад", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(
                    horizontal = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600) 34.dp else 18.dp,
                    vertical = 18.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                AsyncImage(
                    model = poster?.let { "https://image.tmdb.org/t/p/w500$it" },
                    contentDescription = title,
                    modifier = Modifier.width(if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600) 142.dp else 112.dp)
                        .aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
                Column(Modifier.weight(1f)) {
                    Text(if (type == "tv") "СЕРИАЛ" else "ФИЛЬМ",
                        color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(title, color = Color.White,
                        fontSize = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600) 30.sp else 23.sp,
                        lineHeight = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600) 36.sp else 28.sp,
                        fontWeight = FontWeight.Black, maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (year.isNotBlank()) Text(year, color = Color.White.copy(alpha = .8f),
                        modifier = Modifier.padding(top = 5.dp))
                    if (rating > 0) Row(
                        modifier = Modifier.padding(top = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("★", color = Color(0xFFFFC857))
                        Text(String.format("%.1f", rating), color = Color.White, fontWeight = FontWeight.Bold)
                        Text("  TMDB", color = Color.White.copy(alpha = .65f), fontSize = 11.sp)
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(
                horizontal = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600) 34.dp else 18.dp,
                vertical = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (!info?.tagline.isNullOrBlank()) {
                Text(info!!.tagline!!, color = TextSecondary, fontSize = 15.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        Toast.makeText(
                            context,
                            "Просмотр будет доступен после подключения видеопотока.",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(13.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Смотреть", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onFavorite,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(13.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  В избранное", fontWeight = FontWeight.SemiBold)
                }
            }

            if (loadingDetails) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Загружаем подробную информацию…", color = TextSecondary, fontSize = 13.sp)
            }
            if (detailsError) Text("Часть подробной информации временно недоступна.", color = TextSecondary, fontSize = 13.sp)

            if (!info?.genres.isNullOrEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("ЖАНРЫ", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(info!!.genres) { genre ->
                            Surface(color = Color(0xFF1A2A37), shape = RoundedCornerShape(50),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Accent.copy(alpha = .25f))) {
                                Text(genre.name, color = Accent, fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp))
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("О ФИЛЬМЕ", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(info?.overview?.takeIf { it.isNotBlank() } ?: item.overview?.takeIf { it.isNotBlank() }
                    ?: "Описание пока отсутствует.", color = TextPrimary, fontSize = 15.sp, lineHeight = 23.sp)
            }

            val metadata = buildList {
                if (year.isNotBlank()) add("Год выхода" to year)
                if (info?.runtime != null) add("Длительность" to "${info.runtime} мин.")
                if (info?.episodeRunTime?.firstOrNull() != null) add("Серия" to "${info.episodeRunTime.first()} мин.")
                if (info?.numberOfSeasons != null) add("Сезоны" to info.numberOfSeasons.toString())
                if (info?.numberOfEpisodes != null) add("Эпизоды" to info.numberOfEpisodes.toString())
                if (!info?.status.isNullOrBlank()) add("Статус" to info!!.status!!)
                if (!info?.productionCountries.isNullOrEmpty()) add("Страна" to info!!.productionCountries.joinToString { it.name })
                if (!info?.originalLanguage.isNullOrBlank()) add("Язык оригинала" to info!!.originalLanguage!!.uppercase())
                if (info?.voteCount != null && info.voteCount > 0) add("Оценок TMDB" to info.voteCount.toString())
            }
            if (metadata.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text("ИНФОРМАЦИЯ", color = TextSecondary, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(bottom = 8.dp))
                    metadata.forEach { (label, value) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        HorizontalDivider(color = BorderSoft)
                    }
                }
            }

            if (cast.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("В ГЛАВНЫХ РОЛЯХ", color = TextSecondary, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(cast) { person ->
                            Column(Modifier.width(104.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                AsyncImage(
                                    model = person.profilePath?.let { "https://image.tmdb.org/t/p/w185$it" },
                                    contentDescription = person.name,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                                        .clip(RoundedCornerShape(12.dp)).background(SurfaceDark),
                                    contentScale = ContentScale.Crop
                                )
                                Text(person.name, color = TextPrimary, fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold, maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(person.character, color = TextSecondary, fontSize = 10.sp,
                                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

        }
    }
}
