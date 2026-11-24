package com.arnix.cinema.data

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/**
 * Catalog, metadata and public media URLs are fetched through the LordFilm bridge.
 * ExoPlayer UI and playback controls remain unchanged.
 */
class LordFilmCatalogService private constructor() {
    private val gson = Gson()
    private val baseUrl = "https://arnix-cinema-hdrezka-api.onrender.com"

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20000
            readTimeout = 35000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "ARNIX-Cinema/1.0")
        }
        try {
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { gson.fromJson(body, Map::class.java)["detail"]?.toString() }.getOrNull()
                throw IllegalStateException(detail ?: "HTTP $code")
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    suspend fun catalog(category: String, page: Int = 1): RezkaPage {
        val encoded = URLEncoder.encode(category, "UTF-8")
        return gson.fromJson(get("/catalog?category=$encoded&page=$page"), RezkaPage::class.java)
    }

    suspend fun search(query: String): RezkaPage {
        require(query.trim().length >= 2) { "Введите минимум 2 символа" }
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        return gson.fromJson(get("/search?q=$encoded&page=1"), RezkaPage::class.java)
    }

    suspend fun details(url: String): RezkaDetails {
        val encoded = URLEncoder.encode(url, "UTF-8")
        return gson.fromJson(get("/details?url=$encoded"), RezkaDetails::class.java)
    }

    suspend fun stream(url: String, translation: String? = null, season: Int? = null, episode: Int? = null): RezkaStreamResponse {
        val encodedUrl = URLEncoder.encode(url, "UTF-8")
        val encodedTranslation = translation?.let { "&translation=" + URLEncoder.encode(it, "UTF-8") } ?: ""
        val seasonParam = season?.let { "&season=$it" } ?: ""
        val episodeParam = episode?.let { "&episode=$it" } ?: ""
        return gson.fromJson(
            get("/stream?url=$encodedUrl$encodedTranslation$seasonParam$episodeParam"),
            RezkaStreamResponse::class.java
        )
    }

    companion object {
        fun create(context: Context): LordFilmCatalogService = LordFilmCatalogService()
    }
}
