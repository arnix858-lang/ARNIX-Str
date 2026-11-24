package com.arnix.cinema.data

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/**
 * Catalog and metadata are fetched through the server-side ndenissov/HDRezka client.
 * Playback remains in HdRezkaService and is intentionally untouched.
 */
class HdRezkaCatalogService private constructor() {
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

    companion object {
        fun create(context: Context): HdRezkaCatalogService = HdRezkaCatalogService()
    }
}
