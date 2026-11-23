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
