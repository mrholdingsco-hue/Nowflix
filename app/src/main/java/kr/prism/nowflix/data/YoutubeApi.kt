package kr.prism.nowflix.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface YoutubeApi {
    @GET("playlistItems")
    suspend fun playlistItems(
        @Query("part") part: String = "snippet,contentDetails",
        @Query("playlistId") playlistId: String,
        @Query("maxResults") maxResults: Int = PlaylistPaginator.PAGE_SIZE,
        @Query("pageToken") pageToken: String? = null,
        @Query("key") key: String,
    ): PlaylistItemsResponse

    @GET("videos")
    suspend fun videos(
        @Query("part") part: String = "contentDetails,statistics",
        // Up to 50 comma-joined video ids per call.
        @Query("id") id: String,
        @Query("key") key: String,
    ): VideosResponse

    @GET("playlists")
    suspend fun playlists(
        @Query("part") part: String = "snippet",
        // All part playlist ids, comma-joined — fetched in a single call.
        @Query("id") id: String,
        @Query("key") key: String,
    ): PlaylistsResponse
}

/**
 * Retrofit-backed YouTube source for both playlist pages and per-video details.
 * Holds the API key so callers never pass it.
 */
class RetrofitYoutubeSource(
    private val api: YoutubeApi,
    private val apiKey: String,
) : PlaylistItemsSource, VideoDetailsSource, PlaylistMetaSource {

    override suspend fun fetchPage(playlistId: String, pageToken: String?): PlaylistItemsResponse =
        api.playlistItems(playlistId = playlistId, pageToken = pageToken, key = apiKey)

    override suspend fun fetchDetails(ids: List<String>): List<VideoDetail> {
        if (ids.isEmpty()) return emptyList()
        val response = api.videos(id = ids.joinToString(","), key = apiKey)
        return VideoDetailsMapper.toDetails(response.items)
    }

    override suspend fun fetchMeta(playlistIds: List<String>): List<PlaylistMeta> {
        if (playlistIds.isEmpty()) return emptyList()
        val response = api.playlists(id = playlistIds.joinToString(","), key = apiKey)
        return PlaylistMetaMapper.toMeta(response.items)
    }
}

/** Lazily-built singleton Retrofit stack for the YouTube Data API. */
object YoutubeService {
    private const val BASE_URL = "https://www.googleapis.com/youtube/v3/"

    private val json = Json { ignoreUnknownKeys = true }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val api: YoutubeApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(YoutubeApi::class.java)
    }
}
