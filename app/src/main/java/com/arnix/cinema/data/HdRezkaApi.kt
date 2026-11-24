package com.arnix.cinema.data

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RezkaPage(val results: List<RezkaTitle> = emptyList(), val page: Int = 1, val category: String = "")
data class RezkaTitle(
    val title: String = "",
    val url: String = "",
    val image: String = "",
    val rating: Double = 0.0,
    val description: String = "",
    val category: String = "",
    val year: String = ""
)
data class RezkaTranslator(val id: String = "", val name: String = "")
data class RezkaSubtitle(val language: String = "", val url: String = "")
data class RezkaEpisode(val episode: Int = 0, val title: String = "", val translations: List<RezkaTranslator> = emptyList())
data class RezkaSeason(val season: Int = 0, val title: String = "", val episodes: List<RezkaEpisode> = emptyList())
data class RezkaDetails(
    val title: String = "",
    val description: String = "",
    val poster: String = "",
    val rating: Double = 0.0,
    val votes: Int = 0,
    val category: String = "",
    val type: String = "",
    val seasons: List<RezkaSeason> = emptyList()
)
data class RezkaStreamResponse(
    val title: String = "",
    val translators: List<RezkaTranslator> = emptyList(),
    val qualities: Map<String, List<String>> = emptyMap(),
    val subtitles: List<RezkaSubtitle> = emptyList()
)

class HdRezkaService private constructor(context: Context) {
    private val gson = Gson()
    private val appContext = context.applicationContext

    private fun module(): com.chaquo.python.PyObject {
        if (!Python.isStarted()) Python.start(AndroidPlatform(appContext))
        return Python.getInstance().getModule("arnix_rezka")
    }

    private suspend fun call(name: String, vararg args: Any?): String = withContext(Dispatchers.IO) {
        module().callAttr(name, *args).toString()
    }

    suspend fun catalog(category: String, page: Int = 1): RezkaPage =
        gson.fromJson(call("catalog_json", category, page), RezkaPage::class.java)

    suspend fun search(query: String): RezkaPage {
        require(query.trim().length >= 2) { "Введите минимум 2 символа" }
        return gson.fromJson(call("search_json", query.trim()), RezkaPage::class.java)
    }

    suspend fun details(url: String): RezkaDetails =
        gson.fromJson(call("details_json", url), RezkaDetails::class.java)

    suspend fun stream(
        url: String,
        translation: String? = null,
        season: Int? = null,
        episode: Int? = null
    ): RezkaStreamResponse = gson.fromJson(
        call("stream_json", url, translation, season, episode),
        RezkaStreamResponse::class.java
    )

    companion object {
        fun create(context: Context): HdRezkaService = HdRezkaService(context)
    }
}
