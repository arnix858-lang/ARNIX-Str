package com.arnix.cinema.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// Replace with the deployed Render URL when the service is live.
object HdRezkaConfig {
    const val BASE_URL = "https://arnix-cinema-hdrezka-api.onrender.com/"
}

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

interface HdRezkaService {
    @GET("search")
    suspend fun search(@Query("q") query: String): RezkaSearchResponse

    @GET("stream")
    suspend fun stream(
        @Query("url") url: String,
        @Query("translation") translation: String? = null
    ): RezkaStreamResponse

    companion object {
        fun create(): HdRezkaService = Retrofit.Builder()
            .baseUrl(HdRezkaConfig.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HdRezkaService::class.java)
    }
}
