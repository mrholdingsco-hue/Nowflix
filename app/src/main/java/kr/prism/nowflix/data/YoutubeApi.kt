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
}

/** Retrofit-backed [PlaylistItemsSource]. Holds the API key so callers never pass it. */
class RetrofitPlaylistSource(
    private val api: YoutubeApi,
    private val apiKey: String,
) : PlaylistItemsSource {
    override suspend fun fetchPage(playlistId: String, pageToken: String?): PlaylistItemsResponse =
        api.playlistItems(playlistId = playlistId, pageToken = pageToken, key = apiKey)
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
