package com.arnix.cinema

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MimeTypes
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.arnix.cinema.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF090D14)
private val SurfaceDark = Color(0xFF141B26)
private val Accent = Color(0xFF45C4D6)
private val TextPrimary = Color(0xFFF3F6FA)
private val TextSecondary = Color(0xFFA1ADBC)
private val Categories = listOf("Фильмы", "Сериалы", "Мультфильмы", "Аниме")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ArnixCinemaApp() }
    }
}

@Composable
fun ArnixCinemaApp() {
    val context = LocalContext.current
    val tmdb = remember { TmdbService() }
    val lampac = remember { LampacService() }
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf("Главная") }
    var category by remember { mutableStateOf("Фильмы") }
    var home by remember { mutableStateOf<Map<String, List<MediaTitle>>>(emptyMap()) }
    var catalog by remember { mutableStateOf<List<MediaTitle>>(emptyList()) }
    var results by remember { mutableStateOf<List<MediaTitle>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<MediaTitle?>(null) }
    var details by remember { mutableStateOf<MediaDetails?>(null) }
    val favorites = remember { mutableStateListOf<MediaTitle>() }
    var loading by remember { mutableStateOf(false) }
    var searchLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(1) }
    var season by remember { mutableIntStateOf(1) }
    var episode by remember { mutableIntStateOf(1) }
    var providers by remember { mutableStateOf<List<LampacProvider>>(emptyList()) }
    var streams by remember { mutableStateOf<List<LampacStream>>(emptyList()) }
    var selectedProvider by remember { mutableStateOf<LampacProvider?>(null) }
    var selectedStream by remember { mutableStateOf<LampacStream?>(null) }
    var providerLoading by remember { mutableStateOf(false) }
    var streamLoading by remember { mutableStateOf(false) }
    var videoError by remember { mutableStateOf<String?>(null) }
    var availableUpdate by remember { mutableStateOf<AppUpdate?>(null) }
    var updateDownloading by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }

    fun loadCatalog(cat: String, nextPage: Int = 1) {
        scope.launch {
            loading = true
            error = null
            try {
                val data = tmdb.catalog(cat, nextPage)
                catalog = if (nextPage == 1) data else catalog + data
                page = nextPage
            } catch (e: Exception) {
                error = "Не удалось загрузить каталог TMDB: ${e.message ?: "ошибка сети"}"
            } finally { loading = false }
        }
    }

    fun openTitle(item: MediaTitle) {
        selected = item
        details = null
        providers = emptyList()
        streams = emptyList()
        selectedProvider = null
        selectedStream = null
        season = 1
        episode = 1
        error = null
        scope.launch {
            loading = true
            try { details = tmdb.details(item) }
            catch (e: Exception) { error = "Не удалось загрузить описание TMDB: ${e.message ?: "ошибка сети"}" }
            finally { loading = false }
        }
    }

    fun findProviders(s: Int? = null, e: Int? = null) {
        val item = selected ?: return
        scope.launch {
            providerLoading = true
            videoError = null
            providers = emptyList()
            streams = emptyList()
            selectedProvider = null
            selectedStream = null
            try {
                val found = lampac.providers(item)
                providers = found
                if (found.isEmpty()) videoError = "Lampac не вернул список источников. Проверьте доступность сервера и ответ /lite/events."
            } catch (ex: Exception) {
                videoError = "Ошибка Lampac: ${ex.message ?: "не удалось получить источники"}"
            } finally { providerLoading = false }
        }
    }

    fun loadProvider(provider: LampacProvider) {
        val item = selected ?: return
        selectedProvider = provider
        streamLoading = true
        streams = emptyList()
        videoError = null
        scope.launch {
            try {
                val found = lampac.streams(item, provider,
                    if (item.serial == 1) season else null,
                    if (item.serial == 1) episode else null)
                streams = found
                if (found.isEmpty()) videoError = "${provider.name}: сервер не вернул прямые ссылки. Попробуйте другой источник."
            } catch (ex: Exception) {
                videoError = "${provider.name}: ${ex.message ?: "ошибка получения потоков"}"
            } finally { streamLoading = false }
        }
    }

    LaunchedEffect(Unit) {
        launch {
            while (true) {
                runCatching { withContext(Dispatchers.IO) { AppUpdater.checkForUpdate(context) } }
                    .onSuccess { if (it != null) availableUpdate = it }
                    .onFailure { android.util.Log.w("ARNIX-Updater", "Update check failed", it) }
                delay(2 * 60 * 60 * 1000L)
            }
        }
        loading = true
        try { home = tmdb.home() }
        catch (e: Exception) { error = "TMDB: ${e.message ?: "не удалось загрузить главную"}" }
        finally { loading = false }
    }

    LaunchedEffect(tab, query) {
        if (tab != "Поиск") return@LaunchedEffect
        val term = query.trim()
        if (term.length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(450)
        searchLoading = true
        error = null
        try { results = tmdb.search(term) }
        catch (e: Exception) { results = emptyList(); error = "Поиск TMDB: ${e.message ?: "ошибка"}" }
        finally { searchLoading = false }
    }

    LaunchedEffect(tab, category) { if (tab == "Каталог") loadCatalog(category) }

    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600
    MaterialTheme(colorScheme = darkColorScheme(
        background = Bg, surface = SurfaceDark, primary = Accent,
        onBackground = TextPrimary, onSurface = TextPrimary,
        surfaceVariant = Color(0xFF202B38), onSurfaceVariant = TextSecondary
    )) {
        if (selected != null) {
            DetailScreen(
                item = selected!!, details = details, loading = loading,
                season = season, episode = episode,
                onSeason = { season = it; episode = 1 },
                onEpisode = { episode = it },
                onBack = { selected = null; details = null; providers = emptyList(); streams = emptyList() },
                onFavorite = { if (favorites.none { it.id == selected!!.id && it.mediaType == selected!!.mediaType }) favorites.add(selected!!) },
                onPlay = { findProviders() },
                onPlayEpisode = { findProviders(season, episode) }
            )
        } else {
            Scaffold(
                containerColor = Bg,
                bottomBar = {
                    if (compact) NavigationBar(containerColor = Color(0xFF111923)) {
                        listOf("Главная" to Icons.Default.Home, "Каталог" to Icons.Default.VideoLibrary,
                            "Поиск" to Icons.Default.Search, "Избранное" to Icons.Default.Favorite).forEach { (name, icon) ->
                            NavigationBarItem(selected = tab == name, onClick = { tab = name },
                                icon = { Icon(icon, name) }, label = { Text(name, fontSize = 10.sp) })
                        }
                    }
                }
            ) { padding ->
                Row(Modifier.fillMaxSize().padding(padding)) {
                    if (!compact) NavigationRail(containerColor = Color(0xFF0D121D)) {
                        listOf("Главная" to Icons.Default.Home, "Каталог" to Icons.Default.VideoLibrary,
                            "Поиск" to Icons.Default.Search, "Избранное" to Icons.Default.Favorite).forEach { (name, icon) ->
                            NavigationRailItem(selected = tab == name, onClick = { tab = name },
                                icon = { Icon(icon, name) }, label = { Text(name) })
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        when (tab) {
                            "Поиск" -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                                Text("Поиск фильмов и сериалов", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                OutlinedTextField(value = query, onValueChange = { query = it },
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                    placeholder = { Text("Название фильма, сериала или аниме") }, singleLine = true)
                                if (searchLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                MediaGrid(results, ::openTitle)
                            }
                            "Каталог" -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                                Text("Каталог", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                                    items(Categories) { cat ->
                                        FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) })
                                    }
                                }
                                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                                MediaGrid(catalog, ::openTitle)
                                TextButton(onClick = { loadCatalog(category, page + 1) }, enabled = !loading) { Text("Загрузить ещё") }
                            }
                            "Избранное" -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                                Text("Избранное", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                MediaGrid(favorites, ::openTitle)
                            }
                            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                                item {
                                    Box(Modifier.fillMaxWidth().height(190.dp).background(
                                        Brush.verticalGradient(listOf(Accent.copy(alpha = .28f), Bg))),
                                        contentAlignment = Alignment.BottomStart) {
                                        Column(Modifier.padding(20.dp)) {
                                            Text("ARNIX CINEMA", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text("Фильмы и сериалы", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
                                            Text("Каталог TMDB · Видео и источники Lampac", color = TextSecondary)
                                        }
                                    }
                                }
                                if (loading && home.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                                error?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
                                home.forEach { (name, list) ->
                                    item { Text(name, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 8.dp)) }
                                    item {
                                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            items(list) { title -> MediaCard(title, Modifier.width(138.dp)) { openTitle(title) } }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Lampac provider and stream picker.
        if (providerLoading || streamLoading) AlertDialog(
            onDismissRequest = {}, title = { Text(if (providerLoading) "Поиск источников Lampac" else "Получение потока") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if (providerLoading) "Проверяем доступные кинотеатры и видеопровайдеры…" else "Запрашиваем ссылку у выбранного провайдера…")
            } }, confirmButton = {}
        )

        if (providers.isNotEmpty() && selectedProvider == null) AlertDialog(
            onDismissRequest = { providers = emptyList() },
            title = { Text("Источники Lampac") },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Рабочие источники: ${providers.size}", color = TextSecondary)
                    providers.forEach { p ->
                        OutlinedButton(onClick = { loadProvider(p) }, modifier = Modifier.fillMaxWidth()) {
                            Text(p.name + " · рабочий")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { providers = emptyList() }) { Text("Закрыть") } }
        )

        if (streams.isNotEmpty()) AlertDialog(
            onDismissRequest = { streams = emptyList(); selectedProvider = null },
            title = { Text(selectedProvider?.name ?: "Варианты видео") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    streams.forEachIndexed { index, stream ->
                        Button(onClick = { selectedStream = stream; streams = emptyList(); providers = emptyList(); selectedProvider = null },
                            modifier = Modifier.fillMaxWidth()) {
                            Text(stream.title.ifBlank { "Вариант ${index + 1}" }, maxLines = 2)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { streams = emptyList(); selectedProvider = null }) { Text("Назад") } }
        )

        videoError?.let { msg ->
            AlertDialog(onDismissRequest = { videoError = null },
                title = { Text("Lampac") }, text = { Text(msg) },
                confirmButton = { TextButton(onClick = { videoError = null; selectedProvider = null }) { Text("Закрыть") } })
        }

        selectedStream?.let { stream ->
            val view = LocalView.current
            val player = remember(stream.url, stream.headers) {
                val httpFactory = DefaultHttpDataSource.Factory()
                    .setDefaultRequestProperties(stream.headers)
                val mediaFactory = DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory))
                val mediaItem = PlayerMediaItem.Builder().setUri(stream.url)
                    .setMimeType(stream.mimeType ?: detectStreamMime(stream.url)).build()
                ExoPlayer.Builder(context).setMediaSourceFactory(mediaFactory).build().apply {
                    if (stream.url.startsWith("rtsp://", ignoreCase = true)) {
                        setMediaSource(RtspMediaSource.Factory().createMediaSource(mediaItem))
                    } else {
                        setMediaSource(mediaFactory.createMediaSource(mediaItem))
                    }
                    prepare()
                    playWhenReady = true
                }
            }
            DisposableEffect(player, view) {
                val window = (context as? android.app.Activity)?.window
                val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
                controller?.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller?.hide(WindowInsetsCompat.Type.systemBars())
                onDispose {
                    player.release()
                    controller?.show(WindowInsetsCompat.Type.systemBars())
                }
            }
            BackHandler { selectedStream = null }
            Dialog(
                onDismissRequest = { selectedStream = null },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
            ) {
                Surface(Modifier.fillMaxSize(), color = Color.Black, shape = RoundedCornerShape(0.dp)) {
                    AndroidView(
                        factory = { ctx -> PlayerView(ctx).apply {
                            this.player = player
                            useController = true
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                            setControllerAutoShow(true)
                        } },
                        update = { it.player = player },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        availableUpdate?.let { update ->
            AlertDialog(
                onDismissRequest = { if (!updateDownloading) availableUpdate = null },
                title = { Text("Доступно обновление ARNIX Cinema") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Новая версия: ${update.versionName}")
                    if (update.releaseNotes.isNotBlank()) Text(update.releaseNotes)
                    if (updateDownloading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Загружаем APK…") }
                    updateError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                } },
                confirmButton = { TextButton(enabled = !updateDownloading, onClick = {
                    updateDownloading = true; updateError = null
                    scope.launch {
                        try { AppUpdater.downloadAndInstall(context, update) }
                        catch (e: Exception) { updateError = e.message ?: "Не удалось установить обновление." }
                        finally { updateDownloading = false }
                    }
                }) { Text("Обновить") } },
                dismissButton = { TextButton(enabled = !updateDownloading, onClick = { availableUpdate = null }) { Text("Позже") } }
            )
        }
    }
}

@Composable
private fun MediaGrid(items: List<MediaTitle>, onSelect: (MediaTitle) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(items) { item -> MediaCard(item, Modifier.fillMaxWidth()) { onSelect(item) } }
    }
}

@Composable
private fun MediaCard(item: MediaTitle, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.clip(RoundedCornerShape(12.dp)).background(SurfaceDark).clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (item.poster.isNotBlank()) AsyncImage(model = item.poster, contentDescription = item.title,
            modifier = Modifier.width(82.dp).height(118.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
        else Box(Modifier.width(82.dp).height(118.dp).background(Color(0xFF263445)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.VideoLibrary, null, tint = Accent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(item.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 3)
            if (item.year.isNotBlank()) Text(item.year, color = TextSecondary, fontSize = 12.sp)
            if (item.rating > 0) Text("★ ${"%.1f".format(item.rating)}", color = Color(0xFFFFC857), fontSize = 12.sp)
            Text(if (item.mediaType == "tv") "Сериал" else "Фильм", color = Accent, fontSize = 11.sp)
        }
    }
}

@Composable
private fun DetailScreen(
    item: MediaTitle, details: MediaDetails?, loading: Boolean, season: Int, episode: Int,
    onSeason: (Int) -> Unit, onEpisode: (Int) -> Unit, onBack: () -> Unit,
    onFavorite: () -> Unit, onPlay: () -> Unit, onPlayEpisode: () -> Unit
) {
    BackHandler(onBack = onBack)
    val seasons = details?.seasons.orEmpty()
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад", tint = TextPrimary) }
            Text(details?.title ?: item.title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = onFavorite) { Icon(Icons.Default.Favorite, "В избранное", tint = Accent) }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                val backdrop = details?.backdrop?.ifBlank { null } ?: item.backdrop.ifBlank { item.poster }
                AsyncImage(model = backdrop, contentDescription = item.title,
                    modifier = Modifier.fillMaxWidth().height(240.dp), contentScale = ContentScale.Crop)
            }
            item {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(details?.title ?: item.title, color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Black)
                    val rating = details?.rating ?: item.rating
                    if (rating > 0) Text("★ ${"%.1f".format(rating)}  ·  ${details?.year ?: item.year}", color = Color(0xFFFFC857))
                    Text(details?.overview?.ifBlank { null } ?: item.overview.ifBlank { "Описание отсутствует." },
                        color = TextSecondary, lineHeight = 22.sp)
                    Button(onClick = onPlay, modifier = Modifier.fillMaxWidth()) { Text("Найти источники и смотреть") }
                    if (seasons.isNotEmpty()) {
                        Text("СЕЗОН", color = Accent, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(seasons) { s ->
                                FilterChip(selected = season == s.number, onClick = { onSeason(s.number) },
                                    label = { Text(s.name) })
                            }
                        }
                        Text("Серия №", color = Accent, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { onEpisode((episode - 1).coerceAtLeast(1)) }) { Text("−") }
                            Text(episode.toString(), color = TextPrimary)
                            OutlinedButton(onClick = { onEpisode((episode + 1).coerceAtMost(100)) }) { Text("+") }
                        }
                        Button(onClick = onPlayEpisode, modifier = Modifier.fillMaxWidth()) { Text("Найти серию в Lampac") }
                    }
                }
            }
        }
    }
}

 
private fun detectStreamMime(rawUrl: String): String? {
    val path = rawUrl.substringBefore('?').substringBefore('#').lowercase()
    return when {
        path.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
        path.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
        path.endsWith(".ism") || path.endsWith(".isml") || path.contains(".ism/manifest") -> MimeTypes.APPLICATION_SS
        path.endsWith(".ts") || path.endsWith(".mpegts") -> MimeTypes.VIDEO_MP2T
        path.startsWith("rtsp://") -> MimeTypes.APPLICATION_RTSP
        else -> null
    }
}
