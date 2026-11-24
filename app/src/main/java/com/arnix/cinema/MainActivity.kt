package com.arnix.cinema

import android.os.Bundle
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

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
                        selected != null -> DetailScreen(selected!!, onBack = { selected = null }, onFavorite = {
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
private fun DetailScreen(item: MediaItem, onBack: () -> Unit, onFavorite: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                AsyncImage(
                    model = item.backdropPath?.let { IMAGE.replace("w500", "w1280") + it },
                    contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Bg))))
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 18.dp)
                        .clickable { onBack() },
                    color = Color(0xF2FFFFFF),
                    shape = RoundedCornerShape(24.dp),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Назад",
                            tint = TextPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                        Text(
                            "Назад",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
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
