package kr.prism.nowflix

import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/**
 * Reads the fixed JSON fixtures shipped in `androidTest/assets/fixtures/`. These mirror the real
 * YouTube Data API and Supabase PostgREST response shapes but are trimmed to a lobby-sized
 * playlist (8 videos) so the UI tests are fully deterministic and need no API keys.
 */
object Fixtures {
    fun read(name: String): String {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        return ctx.assets.open("fixtures/$name").bufferedReader().use { it.readText() }
    }
}

/**
 * Routes MockWebServer by request path to the matching fixture, covering every endpoint the app
 * calls: Supabase (`/rest/v1/parts`, `/rest/v1/settings`) and YouTube (`/playlistItems`,
 * `/videos`, `/playlists`). [partsFixture] switches the home grid between 6 and 8 parts.
 */
class KioskDispatcher(private val partsFixture: String = "parts_6.json") : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.path ?: return MockResponse().setResponseCode(404)
        val body = when {
            path.startsWith("/rest/v1/parts") -> Fixtures.read(partsFixture)
            path.startsWith("/rest/v1/settings") -> Fixtures.read("settings.json")
            path.startsWith("/playlistItems") -> Fixtures.read("playlist_items.json")
            path.startsWith("/videos") -> Fixtures.read("videos.json")
            path.startsWith("/playlists") -> Fixtures.read("playlists.json")
            else -> return MockResponse().setResponseCode(404)
        }
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)
    }
}

/**
 * Starts a MockWebServer wired to the app's data layer via the [kr.prism.nowflix.data]
 * base-URL test seams, so both the real `PartDetailScreen` fetch and the Supabase config
 * fetch resolve to fixed fixtures. Call [stop] from @After to restore production URLs.
 */
class FakeBackend(partsFixture: String = "parts_6.json") {
    private val server = MockWebServer().apply {
        dispatcher = KioskDispatcher(partsFixture)
        start()
    }

    fun install() {
        val base = server.url("/").toString()
        kr.prism.nowflix.data.YoutubeService.baseUrlOverride = base
        kr.prism.nowflix.data.SupabaseService.baseUrlOverride = base
    }

    fun stop() {
        kr.prism.nowflix.data.YoutubeService.baseUrlOverride = null
        kr.prism.nowflix.data.SupabaseService.baseUrlOverride = null
        server.shutdown()
    }
}
