package kr.prism.nowflix.data

import kotlinx.coroutines.test.runTest
import kr.prism.nowflix.Part
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fakes keep the three-stage fallback pure/JVM-testable — no network, no filesystem. */
private class FakeSource(val block: suspend () -> RemoteConfig) : SupabaseConfigSource {
    override suspend fun fetch(): RemoteConfig = block()
}

private class FakeCache(var stored: RemoteConfig? = null) : ConfigCacheStore {
    var writes = 0
    override fun read(): RemoteConfig? = stored
    override fun write(config: RemoteConfig) {
        writes++; stored = config
    }
}

class KioskConfigRepositoryTest {

    private val bundled = listOf(
        Part(id = "knee", title = "괜찮Knee TV", thumbnail = "part_01_knee", playlistId = "pl_knee"),
        Part(id = "chuk", title = "척척박사 TV", thumbnail = "part_02_chuk", playlistId = "pl_chuk"),
    )

    private fun remoteConfig(idle: Int = 90) = RemoteConfig(
        parts = listOf(
            PartRow(position = 2, title = "척척 원격", playlistId = "pl_chuk", isActive = true),
            PartRow(position = 1, title = "무릎 원격", playlistId = "pl_knee", isActive = true),
        ),
        settings = SettingsRow(idleReturnSeconds = idle, adminPinHash = "abc", headerText = "H"),
    )

    private fun RemoteConfig.withPin(pin: String?, previous: String? = null) =
        copy(settings = settings.copy(adminPinHash = pin, previousAdminPinHash = previous))

    // Stage 1 — remote succeeds: use remote data AND refresh the cache.
    @Test
    fun remoteSuccess_usesRemoteAndWritesCache() = runTest {
        val cache = FakeCache(stored = null)
        val repo = KioskConfigRepository(
            source = FakeSource { remoteConfig(idle = 90) },
            cache = cache,
            bundled = { bundled },
        )

        val config = repo.load()

        assertEquals(listOf("무릎 원격", "척척 원격"), config.parts.map { it.title }) // sorted by position
        assertEquals(90, config.settings.idleReturnSeconds)
        assertEquals(1, cache.writes) // persisted for the next cold start
        assertTrue(config.fromRemote) // live fetch -> admin screen can stamp the receipt time
    }

    // Stage 2 — remote fails but a cache exists: serve the cache, don't overwrite it.
    @Test
    fun remoteFail_withCache_usesCache() = runTest {
        val cache = FakeCache(stored = remoteConfig(idle = 77))
        val repo = KioskConfigRepository(
            source = FakeSource { error("offline") },
            cache = cache,
            bundled = { bundled },
        )

        val config = repo.load()

        assertEquals(listOf("무릎 원격", "척척 원격"), config.parts.map { it.title })
        assertEquals(77, config.settings.idleReturnSeconds)
        assertEquals(0, cache.writes) // a failed fetch must not touch the cache
        assertFalse(config.fromRemote) // served from cache, not a live fetch
    }

    // Stage 3 — remote fails and no cache: bundled assets + default settings.
    @Test
    fun remoteFail_noCache_usesBundled() = runTest {
        val cache = FakeCache(stored = null)
        val repo = KioskConfigRepository(
            source = FakeSource { error("offline") },
            cache = cache,
            bundled = { bundled },
        )

        val config = repo.load()

        assertEquals(bundled, config.parts)
        assertEquals(120, config.settings.idleReturnSeconds) // default
        assertEquals(0, cache.writes)
        assertFalse(config.fromRemote) // bundled assets, not a live fetch
    }

    // A remote PIN change carries the tablet's previous PIN forward, so it still opens the gate.
    @Test
    fun remotePinChange_carriesPreviousPinForward() = runTest {
        val cache = FakeCache(stored = remoteConfig().withPin("old"))
        val repo = KioskConfigRepository(
            source = FakeSource { remoteConfig().withPin("new") },
            cache = cache,
            bundled = { bundled },
        )

        val config = repo.load()

        assertEquals("new", config.settings.adminPinHash)
        assertEquals("old", config.settings.previousAdminPinHash)
        assertEquals("old", cache.stored?.settings?.previousAdminPinHash) // survives a cold start
    }

    // Later fetches that change nothing must not expire the grace PIN — a 30-minute auto-refresh
    // landing before the staff reach the tablet would otherwise lock them out again.
    @Test
    fun unchangedRemotePin_keepsExistingGracePin() = runTest {
        val cache = FakeCache(stored = remoteConfig().withPin("new", previous = "old"))
        val repo = KioskConfigRepository(
            source = FakeSource { remoteConfig().withPin("new") },
            cache = cache,
            bundled = { bundled },
        )

        val config = repo.load()

        assertEquals("new", config.settings.adminPinHash)
        assertEquals("old", config.settings.previousAdminPinHash)
    }

    // The grace slot holds exactly one PIN — the last one this tablet actually knew. A second
    // change replaces it, so retired PINs never pile up into a widening set of valid codes.
    @Test
    fun secondPinChange_gracePinIsTheLastKnownOne() = runTest {
        val cache = FakeCache(stored = remoteConfig().withPin("old", previous = "older"))
        val repo = KioskConfigRepository(
            source = FakeSource { remoteConfig().withPin("new") },
            cache = cache,
            bundled = { bundled },
        )

        val config = repo.load()

        assertEquals("new", config.settings.adminPinHash)
        assertEquals("old", config.settings.previousAdminPinHash) // "older" is retired for good
    }

    // The grace PIN must never equal the current one — that slot would be dead weight.
    @Test
    fun gracePinMatchingCurrentIsDropped() = runTest {
        val cache = FakeCache(stored = remoteConfig().withPin(null, previous = "same"))
        val repo = KioskConfigRepository(
            source = FakeSource { remoteConfig().withPin("same") },
            cache = cache,
            bundled = { bundled },
        )

        val config = repo.load()

        assertEquals("same", config.settings.adminPinHash)
        assertEquals("", config.settings.previousAdminPinHash)
    }

    // Offline: the cached grace PIN is still honoured, so an old PIN works with no network.
    @Test
    fun remoteFail_servesCachedGracePin() = runTest {
        val cache = FakeCache(stored = remoteConfig().withPin("new", previous = "old"))
        val repo = KioskConfigRepository(
            source = FakeSource { error("offline") },
            cache = cache,
            bundled = { bundled },
        )

        val config = repo.load()

        assertEquals("new", config.settings.adminPinHash)
        assertEquals("old", config.settings.previousAdminPinHash)
    }

    // Empty remote is treated as failure by the source, so it never blanks the screen.
    @Test
    fun emptyRemote_fallsThroughToBundled() = runTest {
        val cache = FakeCache(stored = null)
        val repo = KioskConfigRepository(
            source = RetrofitSupabaseSource(object : SupabaseApi {
                override suspend fun parts(select: String, order: String) = emptyList<PartRow>()
                override suspend fun settings(select: String, id: String) = emptyList<SettingsRow>()
            }),
            cache = cache,
            bundled = { bundled },
        )

        val config = repo.load()

        assertEquals(bundled, config.parts) // empty parts -> source throws -> bundled
        assertEquals(0, cache.writes)
    }
}
