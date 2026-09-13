package monster.greyde.kachalochka.core.data.profile

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.di.coreModule
import monster.greyde.kachalochka.core.di.corePlatformModule
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class LocalProfileRepositoryTest {
    private val koin = startKoin { modules(coreModule, corePlatformModule()) }.koin

    private val profile =
        Profile(
            id = "9b1f0c3e-0000-4000-8000-000000000001",
            userId = "9b1f0c3e-0000-4000-8000-000000000002",
            displayName = "Sergei",
            updatedAt = Instant.fromEpochSeconds(1_700_000_000),
            deleted = false,
        )

    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun a_profile_written_through_the_repository_reads_back() =
        runTest {
            val repository = koin.get<ProfileRepository>()

            repository.upsert(profile)

            assertEquals(profile, repository.byId(profile.id))
        }

    @Test
    fun an_unknown_id_reads_back_as_null() =
        runTest {
            assertEquals(null, koin.get<ProfileRepository>().byId("no-such-profile"))
        }

    @Test
    fun writing_a_profile_enqueues_it_for_the_next_sync_pass() =
        runTest {
            koin.get<ProfileRepository>().upsert(profile)

            val pending = koin.get<OutboxDao>().pending()

            assertEquals(listOf("profile" to profile.id), pending.map { it.tableName to it.rowId })
        }
}
