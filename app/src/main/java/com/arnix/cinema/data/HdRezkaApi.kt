package com.arnix.cinema.data

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RezkaSearchResponse(val results: List<RezkaSearchItem> = emptyList())
data class RezkaSearchItem(
    val title: String = "",
    val url: String = "",
    val rating: Double? = null
)
data class RezkaTranslator(val id: String = "", val name: String = "")
data class RezkaSubtitle(val language: String = "", val url: String = "")
data class RezkaStreamResponse(
    val title: String = "",
    val translators: List<RezkaTranslator> = emptyList(),
    val qualities: Map<String, List<String>> = emptyMap(),
    val subtitles: List<RezkaSubtitle> = emptyList()
)

/**
 * Runs the upstream HdRezkaApi Python parser inside this app's embedded
 * Python runtime. No external ARNIX API server or user-installed runtime
 * is required. Network access to the source website is still required.
 */
class HdRezkaService private constructor(context: Context) {
    private val gson = Gson()
    private val appContext = context.applicationContext

    private fun pythonModule() : com.chaquo.python.PyObject {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(appContext))
        }
        return Python.getInstance().getModule("arnix_rezka")
    }

    suspend fun search(query: String): RezkaSearchResponse = withContext(Dispatchers.IO) {
        require(query.trim().length >= 2) { "Введите минимум 2 символа" }
        val json = pythonModule().callAttr("search_json", query.trim()).toString()
        gson.fromJson(json, RezkaSearchResponse::class.java)
    }

    suspend fun stream(url: String, translation: String? = null): RezkaStreamResponse =
        withContext(Dispatchers.IO) {
            val json = pythonModule().callAttr("stream_json", url, translation).toString()
            gson.fromJson(json, RezkaStreamResponse::class.java)
        }

    companion object {
        fun create(context: Context): HdRezkaService = HdRezkaService(context)
    }
}
