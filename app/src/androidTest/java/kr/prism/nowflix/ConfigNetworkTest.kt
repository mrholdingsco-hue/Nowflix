package kr.prism.nowflix

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kr.prism.nowflix.data.FileCacheStore
import kr.prism.nowflix.data.FileConfigCacheStore
import kr.prism.nowflix.data.KioskConfigRepository
import kr.prism.nowflix.data.PlaylistRepository
import kr.prism.nowflix.data.PlaylistResult
import kr.prism.nowflix.data.RetrofitSupabaseSource
import kr.prism.nowflix.data.RetrofitYoutubeSource
import kr.prism.nowflix.data.SupabaseService
import kr.prism.nowflix.data.YoutubeService
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Exercises the real Retrofit data layer end-to-end over MockWebServer with the fixed fixtures —
 * no API keys, identical result every run. This is the data-layer counterpart to the UI tests
 * and the literal "MockWebServer + fixed response files" check from the STEP 9 brief.
 */
@RunWith(AndroidJUnit4::class)
class ConfigNetworkTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val servers = mutableListOf<MockWebServer>()

    private fun server(partsFixture: String): MockWebServer =
        MockWebServer().apply {
            dispatcher = KioskDispatcher(partsFixture)
            start()
            servers += this
        }

    private fun freshDir(): File =
        File(ctx.cacheDir, "cfgtest_${System.nanoTime()}").apply { mkdirs() }

    @After
    fun tearDown() {
        YoutubeService.baseUrlOverride = null
        SupabaseService.baseUrlOverride = null
        servers.forEach { it.shutdown() }
    }

    @Test
    fun config_loadsSixParts_fromRemote() = runBlocking {
        val mock = server("parts_6.json")
        val repo = KioskConfigRepository(
            source = RetrofitSupabaseSource(SupabaseService.create(mock.url("/").toString(), "anon")),
            cache = FileConfigCacheStore(freshDir()),
            bundled = { PartsRepository.load(ctx) },
        )
        val config = repo.load()
        assertEquals(6, config.parts.size)
        assertTrue(config.fromRemote)
        assertEquals(120, config.settings.idleReturnSeconds)
        assertEquals("오늘의 건강 콘텐츠", config.settings.headerText)
    }

    @Test
    fun config_loadsEightParts_fromRemote() = runBlocking {
        val mock = server("parts_8.json")
        val repo = KioskConfigRepository(
            source = RetrofitSupabaseSource(SupabaseService.create(mock.url("/").toString(), "anon")),
            cache = FileConfigCacheStore(freshDir()),
            bundled = { PartsRepository.load(ctx) },
        )
        val config = repo.load()
        assertEquals(8, config.parts.size)
    }

    @Test
    fun playlist_streamsEightVideos_withDurationAndViews() = runBlocking {
        val mock = server("parts_6.json")
        YoutubeService.baseUrlOverride = mock.url("/").toString()
        val source = RetrofitYoutubeSource(YoutubeService.api, "test-key")
        val repo = PlaylistRepository(
            source = source,
            cache = FileCacheStore(freshDir()),
            details = source,
        )

        val emissions = repo.stream("knee", "PLfxolKs8oDR66iswGEXMQz10w1seo8O80").toList()
        val data = emissions.filterIsInstance<PlaylistResult.Data>().last { !it.fromCache }

        assertEquals(8, data.videos.size)
        assertEquals(8, data.totalCount)
        // Enrichment (videos.list) merged duration + view count onto every row.
        assertTrue(data.videos.all { it.durationSeconds > 0 })
        assertTrue(data.videos.all { it.viewCount > 0 })
        // First fixture row, mapped through the real VideoMapper.
        assertEquals("무릎 통증, 이 스트레칭 하나면 끝", data.videos.first().title)
        assertEquals("고쳐줘 NOW", data.videos.first().channelTitle)
    }
}
