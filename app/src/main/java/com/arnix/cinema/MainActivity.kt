package com.arnix.cinema

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
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
import coil.compose.AsyncImage
import com.arnix.cinema.data.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val catalogApi = remember { HdRezkaCatalogService.create(context) }
    val videoApi = remember { HdRezkaService.create(context) }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("Главная") }
    var category by remember { mutableStateOf("Фильмы") }
    var homeLists by remember { mutableStateOf<Map<String, List<RezkaTitle>>>(emptyMap()) }
    var catalog by remember { mutableStateOf<List<RezkaTitle>>(emptyList()) }
    var results by remember { mutableStateOf<List<RezkaTitle>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<RezkaTitle?>(null) }
    var details by remember { mutableStateOf<RezkaDetails?>(null) }
    var favorites = remember { mutableStateListOf<RezkaTitle>() }
    var loading by remember { mutableStateOf(false) }
    var searchLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(1) }
    var selectedSeason by remember { mutableIntStateOf(1) }
    var selectedEpisode by remember { mutableIntStateOf(1) }
    var stream by remember { mutableStateOf<RezkaStreamResponse?>(null) }
    var selectedTranslator by remember { mutableStateOf<String?>(null) }
    var streamLoading by remember { mutableStateOf(false) }
    var streamError by remember { mutableStateOf<String?>(null) }
    var playerUrl by remember { mutableStateOf<String?>(null) }

    fun loadCategory(cat: String, nextPage: Int = 1) {
        scope.launch {
            loading = true
            error = null
            try {
                val response = catalogApi.catalog(cat, nextPage)
                catalog = if (nextPage == 1) response.results else catalog + response.results
                page = nextPage
            } catch (e: Exception) {
                error = "Не удалось загрузить раздел HDRezka: ${e.message ?: "источник недоступен"}"
            } finally { loading = false }
        }
    }

    fun openTitle(item: RezkaTitle) {
        selected = item
        details = null
        stream = null
        streamError = null
        selectedSeason = 1
        selectedEpisode = 1
        scope.launch {
            loading = true
            try { details = catalogApi.details(item.url) }
            catch (e: Exception) { error = "Не удалось загрузить карточку: ${e.message ?: "ошибка источника"}" }
            finally { loading = false }
        }
    }

    LaunchedEffect(Unit) {
        Categories.forEach { cat ->
            runCatching { homeLists = homeLists + (cat to api.catalog(cat, 1).results.take(18)) }
        }
    }

    LaunchedEffect(tab, query) {
        if (tab != "Поиск") return@LaunchedEffect
        val term = query.trim()
        if (term.length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(400)
        searchLoading = true
        error = null
        try { results = catalogApi.search(term).results }
        catch (e: Exception) {
            results = emptyList()
            error = "Поиск HDRezka не выполнен: ${e.message ?: "источник недоступен"}"
        } finally { searchLoading = false }
    }

    LaunchedEffect(tab, category) {
        if (tab == "Каталог") loadCategory(category)
    }

    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600
    MaterialTheme(colorScheme = darkColorScheme(
        background = Bg, surface = SurfaceDark, primary = Accent,
        onBackground = TextPrimary, onSurface = TextPrimary,
        surfaceVariant = Color(0xFF202B38), onSurfaceVariant = TextSecondary
    )) {
        if (selected != null) {
            DetailScreen(
                item = selected!!, details = details, loading = loading,
                onBack = { selected = null; details = null; stream = null },
                onFavorite = { if (favorites.none { it.url == selected!!.url }) favorites.add(selected!!) },
                onPlay = { season, episode, translator ->
                    streamLoading = true
                    streamError = null
                    scope.launch {
                        try {
                            stream = videoApi.stream(selected!!.url, translator, season, episode)
                            selectedTranslator = translator ?: stream?.translators?.firstOrNull()?.id
                        } catch (e: Exception) {
                            streamError = "Не удалось получить видеоссылки: ${e.message ?: "HDRezka не вернула потоки"}"
                        } finally { streamLoading = false }
                    }
                }
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
                                Text("Поиск HDRezka", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                OutlinedTextField(value = query, onValueChange = { query = it },
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                    placeholder = { Text("Название фильма или сериала") }, singleLine = true)
                                if (searchLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                TitleGrid(results, ::openTitle)
                            }
                            "Каталог" -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                                Text("Каталог HDRezka", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(vertical = 12.dp)) {
                                    items(Categories) { cat ->
                                        FilterChip(selected = category == cat, onClick = { category = cat },
                                            label = { Text(cat) })
                                    }
                                }
                                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                                TitleGrid(catalog, ::openTitle)
                                TextButton(onClick = { loadCategory(category, page + 1) }, enabled = !loading) {
                                    Text("Загрузить ещё")
                                }
                            }
                            "Избранное" -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                                Text("Избранное", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                TitleGrid(favorites, ::openTitle)
                            }
                            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                                item {
                                    Box(Modifier.fillMaxWidth().height(190.dp).background(
                                        Brush.verticalGradient(listOf(Accent.copy(alpha = .28f), Bg))),
                                        contentAlignment = Alignment.BottomStart) {
                                        Column(Modifier.padding(20.dp)) {
                                            Text("ARNIX CINEMA", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text("Фильмы и сериалы HDRezka", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                                            Text("Каталог загружается непосредственно из источника", color = TextSecondary)
                                        }
                                    }
                                }
                                Categories.forEach { cat ->
                                    val list = homeLists[cat].orEmpty()
                                    item { Text(cat, color = TextPrimary, fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 18.dp)) }
                                    if (list.isEmpty()) item { Text("Раздел пока не загрузился", color = TextSecondary,
                                        modifier = Modifier.padding(16.dp)) }
                                    item {
                                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            items(list) { title -> TitleCard(title, Modifier.width(138.dp)) { openTitle(title) } }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        streamLoading.let { busy ->
            if (busy) AlertDialog(onDismissRequest = {}, title = { Text("Получение видеоссылок") },
                text = { Column { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Запрашиваем потоки HDRezka…") } },
                confirmButton = {})
        }
        streamError?.let { msg ->
            AlertDialog(onDismissRequest = { streamError = null }, title = { Text("HDRezka") },
                text = { Text(msg) }, confirmButton = { TextButton(onClick = { streamError = null }) { Text("Закрыть") } })
        }
        stream?.let { data ->
            AlertDialog(onDismissRequest = { stream = null }, title = { Text(data.title.ifBlank { "Выбор потока" }) },
                text = {
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ОЗВУЧКА", color = Accent, fontWeight = FontWeight.Bold)
                        data.translators.forEach { translator ->
                            FilterChip(selected = selectedTranslator == translator.id,
                                onClick = {
                                    selectedTranslator = translator.id
                                    val s = if (details?.seasons?.isNotEmpty() == true) selectedSeason else null
                                    val e = if (details?.seasons?.isNotEmpty() == true) selectedEpisode else null
                                    streamLoading = true
                                    scope.launch {
                                        try { stream = videoApi.stream(selected!!.url, translator.id, s, e) }
                                        catch (ex: Exception) { streamError = ex.message ?: "Ошибка выбора озвучки" }
                                        finally { streamLoading = false }
                                    }
                                }, label = { Text(translator.name) })
                        }
                        Text("КАЧЕСТВО", color = Accent, fontWeight = FontWeight.Bold)
                        data.qualities.forEach { (quality, links) ->
                            links.forEach { link ->
                                Button(onClick = { playerUrl = link; stream = null },
                                    modifier = Modifier.fillMaxWidth()) { Text(quality) }
                            }
                        }
                        if (data.qualities.isEmpty()) Text("Ссылки на потоки не найдены", color = TextSecondary)
                    }
                }, confirmButton = { TextButton(onClick = { stream = null }) { Text("Закрыть") } })
        }
        playerUrl?.let { url ->
            val player = remember(url) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(PlayerMediaItem.fromUri(url)); prepare(); playWhenReady = true
                }
            }
            DisposableEffect(player) { onDispose { player.release() } }
            Dialog(onDismissRequest = { playerUrl = null }) {
                Surface(Modifier.fillMaxWidth().aspectRatio(16f / 9f), color = Color.Black,
                    shape = RoundedCornerShape(12.dp)) {
                    AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } },
                        modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun TitleGrid(items: List<RezkaTitle>, onSelect: (RezkaTitle) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)) {
        items(items) { title -> TitleCard(title, Modifier.fillMaxWidth()) { onSelect(title) } }
    }
}

@Composable
private fun TitleCard(item: RezkaTitle, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.clip(RoundedCornerShape(12.dp)).background(SurfaceDark).clickable(onClick = onClick)
        .padding(8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AsyncImage(model = item.image.ifBlank { null }, contentDescription = item.title,
            modifier = Modifier.width(82.dp).height(118.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(item.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 3)
            if (item.year.isNotBlank()) Text(item.year, color = TextSecondary, fontSize = 12.sp)
            if (item.rating > 0) Text("★ ${item.rating}", color = Color(0xFFFFC857), fontSize = 12.sp)
            if (item.category.isNotBlank()) Text(item.category, color = Accent, fontSize = 11.sp)
        }
    }
}

@Composable
private fun DetailScreen(
    item: RezkaTitle, details: RezkaDetails?, loading: Boolean,
    onBack: () -> Unit, onFavorite: () -> Unit,
    onPlay: (Int?, Int?, String?) -> Unit
) {
    BackHandler(onBack = onBack)
    var season by remember(item.url) { mutableIntStateOf(1) }
    var episode by remember(item.url) { mutableIntStateOf(1) }
    var translator by remember(item.url) { mutableStateOf<String?>(null) }
    val seasons = details?.seasons.orEmpty()
    val selectedSeason = seasons.firstOrNull { it.season == season } ?: seasons.firstOrNull()
    val episodes = selectedSeason?.episodes.orEmpty()
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад", tint = TextPrimary) }
            Text(details?.title ?: item.title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            IconButton(onClick = onFavorite) { Icon(Icons.Default.Favorite, "В избранное", tint = Accent) }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                AsyncImage(model = details?.poster?.ifBlank { null } ?: item.image.ifBlank { null },
                    contentDescription = item.title, modifier = Modifier.fillMaxWidth().height(270.dp),
                    contentScale = ContentScale.Crop)
            }
            item {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(details?.title ?: item.title, color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Black)
                    val rating = details?.rating ?: item.rating
                    if (rating > 0) Text("★ $rating  ·  HDRezka", color = Color(0xFFFFC857))
                    Text(details?.description?.ifBlank { null } ?: item.description.ifBlank { "Описание отсутствует." },
                        color = TextSecondary, lineHeight = 22.sp)
                    Button(onClick = { onPlay(null, null, translator) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Смотреть")
                    }
                    if (seasons.isNotEmpty()) {
                        Text("СЕЗОН", color = Accent, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(seasons) { s ->
                                FilterChip(selected = season == s.season, onClick = {
                                    season = s.season; episode = s.episodes.firstOrNull()?.episode ?: 1
                                }, label = { Text(s.title.ifBlank { "Сезон ${s.season}" }) })
                            }
                        }
                        Text("СЕРИЯ", color = Accent, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(episodes) { ep ->
                                FilterChip(selected = episode == ep.episode, onClick = {
                                    episode = ep.episode
                                    translator = ep.translations.firstOrNull()?.id
                                }, label = { Text(ep.title.ifBlank { "Серия ${ep.episode}" }) })
                            }
                        }
                        Button(onClick = { onPlay(season, episode, translator) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Смотреть серию ${episode}")
                        }
                    }
                    if (item.category.isNotBlank()) Text("Категория: ${item.category}", color = TextSecondary)
                }
            }
        }
    }
}
