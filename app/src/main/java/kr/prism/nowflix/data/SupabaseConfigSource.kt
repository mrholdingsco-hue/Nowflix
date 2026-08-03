package kr.prism.nowflix.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/** Where a [RemoteConfig] comes from — abstracted so the repository is testable. */
interface SupabaseConfigSource {
    /** Fetches parts + settings. Throws on any network/parse failure or empty parts. */
    suspend fun fetch(): RemoteConfig
}

/** PostgREST endpoints for the kiosk's read-only config. */
interface SupabaseApi {
    @GET("parts")
    suspend fun parts(
        @Query("select") select: String = "position,title,thumbnail_url,playlist_id,is_active",
        @Query("order") order: String = "position.asc",
    ): List<PartRow>

    @GET("settings")
    suspend fun settings(
        @Query("select") select: String = "idle_return_seconds,admin_pin_hash,header_text",
        @Query("id") id: String = "eq.1",
    ): List<SettingsRow>
}

class RetrofitSupabaseSource(private val api: SupabaseApi) : SupabaseConfigSource {
    override suspend fun fetch(): RemoteConfig {
        val parts = api.parts()
        // A reachable-but-empty parts table must NOT blank the lobby screen: treat it as
        // a failure so the caller falls back to cache/assets and the cache is left intact.
        if (parts.isEmpty()) error("Supabase returned no parts")
        val settings = api.settings().firstOrNull() ?: SettingsRow()
        return RemoteConfig(parts = parts, settings = settings)
    }
}

/**
 * Lazily-built Retrofit stack for the project's PostgREST API. The anon key is sent on
 * every request (both `apikey` and `Authorization`, as PostgREST expects) via an
 * interceptor, so callers never pass it. All values come from BuildConfig.
 */
object SupabaseService {

    private val json = Json { ignoreUnknownKeys = true }

    fun create(baseUrl: String, anonKey: String): SupabaseApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $anonKey")
                    .build()
                chain.proceed(request)
            }
            .build()
        return Retrofit.Builder()
            // PostgREST lives under /rest/v1/ ; trailing slash matters for relative paths.
            .baseUrl(baseUrl.trimEnd('/') + "/rest/v1/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SupabaseApi::class.java)
    }
}
