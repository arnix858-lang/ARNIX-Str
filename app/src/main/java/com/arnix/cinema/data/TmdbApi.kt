package com.arnix.cinema.data

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class TmdbPage<T>(
    val page: Int = 1,
    @SerializedName("total_pages") val totalPages: Int = 1,
    val results: List<T> = emptyList()
)

data class TmdbGenre(val id: Int, val name: String)
data class TmdbGenreResponse(val genres: List<TmdbGenre> = emptyList())
data class TmdbDetails(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("vote_count") val voteCount: Int = 0,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    val runtime: Int? = null,
    @SerializedName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    val status: String? = null,
    val tagline: String? = null,
    val genres: List<TmdbGenre> = emptyList(),
    @SerializedName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerializedName("number_of_episodes") val numberOfEpisodes: Int? = null,
    @SerializedName("original_language") val originalLanguage: String? = null,
    @SerializedName("production_countries") val productionCountries: List<TmdbCountry> = emptyList()
) {
    val displayTitle: String get() = title ?: name ?: "Без названия"
    val year: String get() = (releaseDate ?: firstAirDate ?: "").take(4)
}
data class TmdbCountry(val name: String = "")
data class TmdbCredits(val cast: List<TmdbCast> = emptyList())
data class TmdbCast(
    val id: Int,
    val name: String = "",
    @SerializedName("character") val character: String = "",
    @SerializedName("profile_path") val profilePath: String? = null
)
data class TmdbVideos(val results: List<TmdbVideo> = emptyList())
data class TmdbVideo(
    val key: String = "",
    val name: String = "",
    val site: String = "",
    val type: String = ""
)

data class MediaItem(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    val genre_ids: List<Int> = emptyList(),
    val media_type: String? = null
) {
    val displayTitle: String get() = title ?: name ?: "Без названия"
    val year: String get() = (releaseDate ?: firstAirDate ?: "").take(4)
}

interface TmdbApi {
    @GET("trending/all/week")
    suspend fun trending(@Query("api_key") key: String, @Query("language") language: String = "ru-RU"): TmdbPage<MediaItem>

    @GET("movie/popular")
    suspend fun movies(@Query("api_key") key: String, @Query("language") language: String = "ru-RU", @Query("page") page: Int = 1): TmdbPage<MediaItem>

    @GET("tv/popular")
    suspend fun tv(@Query("api_key") key: String, @Query("language") language: String = "ru-RU", @Query("page") page: Int = 1): TmdbPage<MediaItem>

    @GET("genre/movie/list")
    suspend fun movieGenres(@Query("api_key") key: String, @Query("language") language: String = "ru-RU"): TmdbGenreResponse

    @GET("genre/tv/list")
    suspend fun tvGenres(@Query("api_key") key: String, @Query("language") language: String = "ru-RU"): TmdbGenreResponse

    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("api_key") key: String,
        @Query("language") language: String = "ru-RU",
        @Query("with_genres") genreId: Int? = null,
        @Query("primary_release_year") year: Int? = null,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1
    ): TmdbPage<MediaItem>

    @GET("discover/tv")
    suspend fun discoverTv(
        @Query("api_key") key: String,
        @Query("language") language: String = "ru-RU",
        @Query("with_genres") genreId: Int? = null,
        @Query("first_air_date_year") year: Int? = null,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1
    ): TmdbPage<MediaItem>

    @GET("{type}/{id}")
    suspend fun fullDetails(
        @Path("type") type: String,
        @Path("id") id: Int,
        @Query("api_key") key: String,
        @Query("language") language: String = "ru-RU"
    ): TmdbDetails

    @GET("{type}/{id}/credits")
    suspend fun credits(
        @Path("type") type: String,
        @Path("id") id: Int,
        @Query("api_key") key: String,
        @Query("language") language: String = "ru-RU"
    ): TmdbCredits

    @GET("{type}/{id}/videos")
    suspend fun videos(
        @Path("type") type: String,
        @Path("id") id: Int,
        @Query("api_key") key: String,
        @Query("language") language: String = "ru-RU"
    ): TmdbVideos

    @GET("search/multi")
    suspend fun search(@Query("api_key") key: String, @Query("query") query: String, @Query("language") language: String = "ru-RU"): TmdbPage<MediaItem>

    @GET("{type}/{id}")
    suspend fun details(@Path("type") type: String, @Path("id") id: Int, @Query("api_key") key: String, @Query("language") language: String = "ru-RU"): MediaItem

    companion object {
        fun create(): TmdbApi = Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TmdbApi::class.java)
    }
}
