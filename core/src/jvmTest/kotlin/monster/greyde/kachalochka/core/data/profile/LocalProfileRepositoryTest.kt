package monster.greyde.kachalochka.core.data.profile

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.di.coreModule
import monster.greyde.kachalochka.core.di.corePlatformModule
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileId
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
            id = ProfileId("9b1f0c3e-0000-4000-8000-000000000001"),
            userId = UserId("9b1f0c3e-0000-4000-8000-000000000002"),
            displayName = "Sergei",
            updatedAt = Instant.fromEpochMilliseconds(1_700_000_000_123),
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
            val absent = ProfileId("9b1f0c3e-0000-4000-8000-00000000000f")

            assertEquals(null, koin.get<ProfileRepository>().byId(absent))
        }

    @Test
    fun writing_a_profile_enqueues_it_for_the_next_sync_pass() =
        runTest {
            koin.get<ProfileRepository>().upsert(profile)

            val pending = koin.get<OutboxDao>().pending()

            assertEquals(
                listOf("profile" to profile.id.value),
                pending.map { it.tableName to it.rowId },
            )
        }

    @Test
    fun a_sub_second_timestamp_survives_the_round_trip() =
        runTest {
            val repository = koin.get<ProfileRepository>()

            repository.upsert(profile)

            assertEquals(profile.updatedAt, repository.byId(profile.id)?.updatedAt)
        }

    @Test
    fun writing_a_profile_with_no_owner_leaves_it_out_of_the_outbox() =
        runTest {
            val unowned = profile.copy(userId = null)

            koin.get<ProfileRepository>().upsert(unowned)

            assertEquals(unowned, koin.get<ProfileRepository>().byId(unowned.id))
            assertEquals(emptyList(), koin.get<OutboxDao>().pending())
        }
}
