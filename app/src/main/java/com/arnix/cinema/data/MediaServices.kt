package com.arnix.cinema.data

import com.arnix.cinema.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class MediaTitle(
    val id: Int, val mediaType: String, val title: String, val originalTitle: String,
    val overview: String, val poster: String, val backdrop: String, val year: String, val rating: Double
) { val serial: Int get() = if (mediaType == "tv") 1 else 0 }

data class MediaSeason(val number: Int, val name: String, val episodes: Int)
data class MediaDetails(
    val title: String, val overview: String, val poster: String, val backdrop: String,
    val year: String, val rating: Double, val seasons: List<MediaSeason>
)
data class LampacProvider(val name: String, val provider: String, val url: String, val available: Boolean)
data class LampacStream(val title: String, val url: String, val headers: Map<String, String> = emptyMap())

class TmdbService {
    private val apiKey = BuildConfig.TMDB_API_KEY
    private val base = "https://api.themoviedb.org/3"

    private fun request(path: String): JSONObject {
        if (apiKey.isBlank()) throw IllegalStateException("Не задан TMDB_API_KEY в GitHub Actions → Settings → Secrets and variables → Actions.")
        val connection = (URL("$base$path${if (path.contains("?")) "&" else "?"}api_key=$apiKey&language=ru-RU").openConnection() as HttpURLConnection)
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("Accept", "application/json")
        try {
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("TMDB HTTP $code: ${body.take(220)}")
            return JSONObject(body)
        } finally { connection.disconnect() }
    }

    suspend fun home(): Map<String, List<MediaTitle>> = withContext(Dispatchers.IO) {
        mapOf(
            "Популярные фильмы" to list("/trending/movie/week"),
            "Сериалы" to list("/trending/tv/week"),
            "Аниме" to list("/discover/tv?with_genres=16&with_original_language=ja&sort_by=popularity.desc"),
            "Мультфильмы" to list("/discover/movie?with_genres=16&sort_by=popularity.desc")
        )
    }

    suspend fun catalog(type: String, page: Int = 1): List<MediaTitle> = withContext(Dispatchers.IO) {
        val endpoint = when (type) {
            "Сериалы" -> "/tv/popular?page=$page"
            "Мультфильмы" -> "/discover/movie?with_genres=16&sort_by=popularity.desc&page=$page"
            "Аниме" -> "/discover/tv?with_genres=16&with_original_language=ja&sort_by=popularity.desc&page=$page"
            else -> "/movie/popular?page=$page"
        }
        list(endpoint)
    }

    suspend fun search(query: String): List<MediaTitle> = withContext(Dispatchers.IO) {
        if (query.trim().length < 2) return@withContext emptyList()
        val root = request("/search/multi?query=${enc(query.trim())}&page=1&include_adult=false")
        parseResults(root.optJSONArray("results") ?: JSONArray()).filter { it.mediaType == "movie" || it.mediaType == "tv" }
    }

    suspend fun details(item: MediaTitle): MediaDetails = withContext(Dispatchers.IO) {
        val kind = if (item.mediaType == "tv") "tv" else "movie"
        val root = request("/$kind/${item.id}")
        val seasons = if (kind == "tv") {
            val arr = root.optJSONArray("seasons") ?: JSONArray()
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { s ->
                    val number = s.optInt("season_number")
                    if (number <= 0) null else MediaSeason(number, s.optString("name", "Сезон $number"), s.optInt("episode_count"))
                }
            }
        } else emptyList()
        MediaDetails(
            root.optString("title").ifBlank { root.optString("name", item.title) },
            root.optString("overview").ifBlank { item.overview },
            image(root.optString("poster_path")).ifBlank { item.poster },
            image(root.optString("backdrop_path")).ifBlank { item.backdrop },
            root.optString("release_date").ifBlank { root.optString("first_air_date") }.take(4).ifBlank { item.year },
            root.optDouble("vote_average", item.rating), seasons
        )
    }

    private fun list(path: String) = parseResults(request(path).optJSONArray("results") ?: JSONArray())
    private fun parseResults(array: JSONArray): List<MediaTitle> = (0 until array.length()).mapNotNull { i ->
        val o = array.optJSONObject(i) ?: return@mapNotNull null
        val media = o.optString("media_type").ifBlank { if (o.has("first_air_date") || o.has("name")) "tv" else "movie" }
        val title = o.optString("title").ifBlank { o.optString("name") }
        if (title.isBlank()) return@mapNotNull null
        MediaTitle(o.optInt("id"), media, title,
            o.optString("original_title").ifBlank { o.optString("original_name", title) },
            o.optString("overview"), image(o.optString("poster_path")), image(o.optString("backdrop_path")),
            o.optString("release_date").ifBlank { o.optString("first_air_date") }.take(4),
            o.optDouble("vote_average", 0.0))
    }
    private fun image(path: String) = if (path.isBlank() || path == "null") "" else "https://image.tmdb.org/t/p/w500$path"
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
}

class LampacService(private val baseUrl: String = "https://arnix-lampac-test.onrender.com") {
    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.setRequestProperty("Accept", "application/json, text/plain, */*")
        connection.setRequestProperty("User-Agent", "ARNIX-Cinema-Android/1.0")
        try {
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Lampac HTTP $code: ${body.take(240)}")
            return body
        } finally { connection.disconnect() }
    }

    suspend fun providers(item: MediaTitle): List<LampacProvider> = withContext(Dispatchers.IO) {
        val ids = externalIds(item)
        val kp = firstNonBlank(ids, "kinopoisk_id", "kp_id", "kp")
        val imdb = firstNonBlank(ids, "imdb_id", "imdb")
        val params = "id=${item.id}&tmdb_id=${item.id}&serial=${item.serial}&title=${enc(item.title)}&original_title=${enc(item.originalTitle)}&year=${enc(item.year)}&imdb_id=${enc(imdb)}&imdb=${enc(imdb)}&kinopoisk_id=${enc(kp)}&kp=${enc(kp)}&source=tmdb&life=true"
        val first = JSONObject(get("$baseUrl/lite/events?$params"))
        val direct = first.optJSONArray("online")
        if (direct != null && direct.length() > 0) return@withContext parseProviders(direct)
        val memkey = first.optString("memkey")
        if (memkey.isNotBlank()) {
            var seen = 0
            var latest = JSONArray()
            repeat(80) {
                val state = JSONObject(get("$baseUrl/lifeevents?memkey=${enc(memkey)}&seen=$seen"))
                latest = state.optJSONArray("online") ?: latest
                seen = maxOf(seen, latest.length())
                if (state.optBoolean("ready")) return@withContext parseProviders(latest)
                delay(450)
            }
            parseProviders(latest)
        } else {
            parseProviders(direct ?: JSONArray())
        }
    }

    suspend fun streams(item: MediaTitle, provider: LampacProvider, season: Int? = null, episode: Int? = null): List<LampacStream> =
        withContext(Dispatchers.IO) {
            val endpoint = provider.url.ifBlank { "$baseUrl/lite/${provider.provider}" }
            fun query(clarification: Boolean) = buildString {
                append("title=${enc(item.title)}&original_title=${enc(item.originalTitle)}&year=${enc(item.year)}")
                val ids = externalIds(item)
                append("&tmdb=${item.id}&tmdb_id=${item.id}&id=${item.id}&serial=${item.serial}")
                if (ids.optString("imdb_id").isNotBlank()) append("&imdb_id=${enc(ids.optString("imdb_id"))}&imdb=${enc(ids.optString("imdb_id"))}")
                if (ids.optString("kinopoisk_id").isNotBlank()) append("&kinopoisk_id=${enc(ids.optString("kinopoisk_id"))}&kp=${enc(ids.optString("kinopoisk_id"))}")
                if (season != null) append("&s=$season")
                if (episode != null) append("&e=$episode")
                append("&rjson=true")
                if (clarification) append("&clarification=1&similar=true")
            }
            val found = mutableListOf<LampacStream>()
            val bodies = mutableListOf<String>()
            bodies.add(get(appendQuery(endpoint, query(false))))
            parseJson(bodies.last(), provider.name)?.let { collectStreams(it, provider.name, found, 0) }
            if (found.isEmpty()) {
                bodies.add(get(appendQuery(endpoint, query(true))))
                parseJson(bodies.last(), provider.name)?.let { collectStreams(it, provider.name, found, 0) }
            }
            // Lampac may first return a title/voice selection. Follow its own /lite/* action URLs.
            val actions = linkedSetOf<String>()
            bodies.forEach { body -> parseJson(body, provider.name)?.let { collectActionUrls(it, actions, 0) } }
            for (action in actions.take(8)) {
                if (found.isNotEmpty()) break
                val nested = get(appendQuery(secureLampac(action), "rjson=true"))
                parseJson(nested, provider.name)?.let { collectStreams(it, provider.name, found, 0) }
            }
            found.distinctBy { it.url }
        }

    private fun externalIds(item: MediaTitle): JSONObject = runCatching {
        JSONObject(get("$baseUrl/externalids?id=${item.id}&tmdb_id=${item.id}&serial=${item.serial}"))
    }.getOrDefault(JSONObject())

    private fun firstNonBlank(json: JSONObject, vararg keys: String): String =
        keys.firstNotNullOfOrNull { key -> json.optString(key).takeIf { it.isNotBlank() && it != "null" } }.orEmpty()

    // Stable list for Lampac installations that return no event results (sleeping Render,
    // source checks timing out, or providers configured but marked unavailable).
    // These are real Lampac routes; each is queried only after the aggregate list is empty.
    private fun parseProviders(array: JSONArray): List<LampacProvider> {
        val providers = mutableListOf<LampacProvider>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val url = secureLampac(o.optString("url"))
            val name = o.optString("name").ifBlank { o.optString("balanser", "Источник") }
            val key = o.optString("balanser").ifBlank {
                url.substringAfterLast("/lite/").substringBefore("?").ifBlank { name.lowercase() }
            }
            providers.add(LampacProvider(name, key, url.ifBlank { "$baseUrl/lite/$key" }, o.optBoolean("show", true)))
        }
        return providers.distinctBy { it.provider.lowercase().trim() }
            .filter { it.available || isAlloha(it) }
            .sortedWith(compareBy<LampacProvider> { if (isAlloha(it)) 0 else 1 }
                .thenBy { it.name.lowercase() })
    }

    private fun isAlloha(provider: LampacProvider): Boolean =
        provider.provider.contains("alloha", ignoreCase = true) ||
            provider.name.contains("alloha", ignoreCase = true) ||
            provider.name.contains("аллоха", ignoreCase = true) ||
            provider.url.contains("alloha", ignoreCase = true)

    private fun parseJson(body: String, label: String): Any? {
        JSONObjectOrNull(body)?.let { return it }
        JSONArrayOrNull(body)?.let { return it }
        return null
    }
    private fun JSONObjectOrNull(body: String) = runCatching { JSONObject(body) }.getOrNull()
    private fun JSONArrayOrNull(body: String) = runCatching { JSONArray(body) }.getOrNull()

    private fun collectActionUrls(value: Any?, output: MutableSet<String>, depth: Int) {
        if (depth > 12) return
        when (value) {
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) collectActionUrls(value.opt(keys.next()), output, depth + 1)
            }
            is JSONArray -> for (i in 0 until value.length()) collectActionUrls(value.opt(i), output, depth + 1)
            is String -> {
                val v = value.trim()
                if ((v.startsWith("http://") || v.startsWith("https://")) && v.contains("/lite/") && !isPlayable(v))
                    output.add(secureLampac(v))
                else if (v.startsWith("/lite/")) output.add(baseUrl + v)
            }
        }
    }

    private fun collectStreams(
        value: Any?, fallback: String, output: MutableList<LampacStream>, depth: Int,
        inheritedHeaders: Map<String, String> = emptyMap()
    ) {
        if (depth > 14) return
        when (value) {
            is JSONObject -> {
                val label = value.optString("title").ifBlank { value.optString("name").ifBlank { value.optString("quality").ifBlank { fallback } } }
                val headers = inheritedHeaders.toMutableMap()
                value.optJSONObject("headers")?.let { h ->
                    val keys = h.keys()
                    while (keys.hasNext()) { val k = keys.next(); headers[k] = h.optString(k) }
                }
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val child = value.opt(key)
                    if (child is String && isPlayable(child)) output.add(LampacStream("$fallback · $label", secureLampac(child), headers))
                    else collectStreams(child, fallback, output, depth + 1, headers)
                }
            }
            is JSONArray -> for (i in 0 until value.length()) collectStreams(value.opt(i), fallback, output, depth + 1, inheritedHeaders)
        }
    }

    private fun secureLampac(value: String): String = value
        .replace("http://arnix-lampac-test.onrender.com", baseUrl, ignoreCase = true)
        .replace("http://localhost:9118", baseUrl, ignoreCase = true)
        .replace("http://127.0.0.1:9118", baseUrl, ignoreCase = true)

    private fun isPlayable(value: String): Boolean {
        val v = value.trim()
        if (!v.startsWith("http://") && !v.startsWith("https://")) return false
        val lower = v.lowercase()
        return lower.contains(".m3u8") || lower.contains(".mpd") || lower.contains(".mp4") || lower.contains("/proxy/") || lower.contains("/proxy-dash/")
    }
    private fun appendQuery(base: String, query: String): String {
        val params = linkedMapOf<String, String>()
        fun add(raw: String) {
            raw.split("&").filter { it.isNotBlank() }.forEach { part ->
                val key = part.substringBefore("=")
                if (key.isNotBlank()) params[key] = part.substringAfter("=", "")
            }
        }
        add(base.substringAfter("?", ""))
        add(query)
        return base.substringBefore("?") + "?" + params.entries.joinToString("&") { "${it.key}=${it.value}" }
    }
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
}
