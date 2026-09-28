package monster.greyde.kachalochka.core.data.profile

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.data.db.kachalochkaDatabase
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.di.coreModule
import monster.greyde.kachalochka.core.di.corePlatformModule
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileId
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.core.domain.profile.Sex
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
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

    @Test
    fun the_new_fields_survive_the_round_trip() =
        runTest {
            val repository = koin.get<ProfileRepository>()
            val friend = UserId("11111111-1111-4111-8111-111111111111")
            val full =
                profile.copy(
                    friendColors = mapOf(friend to 0xFF2196F3.toInt()),
                    sex = Sex.Male,
                    birthYear = 1985,
                    heightCm = 181.5,
                )

            repository.upsert(full)

            assertEquals(full, repository.byId(full.id))
        }

    @Test
    fun an_owner_s_profile_is_the_newest_live_one_of_theirs() =
        runTest {
            val repository = koin.get<ProfileRepository>()
            val owner = profile.userId
            val older = profile.copy(id = ProfileId.random(), displayName = "Старый")
            val newest =
                profile.copy(
                    id = ProfileId.random(),
                    updatedAt = older.updatedAt + 2.hours,
                )
            val deleted =
                profile.copy(
                    id = ProfileId.random(),
                    updatedAt = older.updatedAt + 3.hours,
                    deleted = true,
                )
            val stranger =
                profile.copy(
                    id = ProfileId.random(),
                    userId = UserId("22222222-2222-4222-8222-222222222222"),
                    updatedAt = older.updatedAt + 4.hours,
                )
            listOf(older, newest, deleted, stranger).forEach { repository.upsert(it) }

            assertEquals(newest, repository.forOwner(owner))
        }

    @Test
    fun of_two_profiles_written_at_once_the_greater_id_wins() =
        runTest {
            val repository = koin.get<ProfileRepository>()
            val greater = profile.copy(id = ProfileId("9b1f0c3e-0000-4000-8000-00000000000e"))
            val lesser = profile.copy(id = ProfileId("9b1f0c3e-0000-4000-8000-00000000000a"))
            repository.upsert(greater)
            repository.upsert(lesser)

            assertEquals(greater, repository.forOwner(profile.userId))
        }

    @Test
    fun the_anonymous_profile_belongs_to_no_owner() =
        runTest {
            val repository = koin.get<ProfileRepository>()
            val anonymous = profile.copy(id = ProfileId.random(), userId = null)
            repository.upsert(profile)
            repository.upsert(anonymous)

            assertEquals(anonymous, repository.forOwner(null))
        }

    @Test
    fun an_owner_without_a_profile_has_none() =
        runTest {
            val repository = koin.get<ProfileRepository>()
            repository.upsert(profile.copy(userId = null))

            assertEquals(null, repository.forOwner(profile.userId))
        }

    @Test
    fun queries_run_on_the_injected_dispatcher_rather_than_the_caller() =
        runTest {
            val dispatcher = RecordingDispatcher(StandardTestDispatcher(testScheduler))
            val database = inMemoryDatabase()
            val repository = LocalProfileRepository(database, OutboxDao(database), dispatcher)

            repository.upsert(profile)
            repository.byId(profile.id)

            assertTrue(dispatcher.dispatches > 0, "the repository never left the caller")
        }

    private fun inMemoryDatabase(): KachalochkaDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KachalochkaDatabase.Schema.create(driver)
        return kachalochkaDatabase(driver)
    }
}

private class RecordingDispatcher(
    private val delegate: CoroutineDispatcher,
) : CoroutineDispatcher() {
    var dispatches = 0
        private set

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        dispatches++
        delegate.dispatch(context, block)
    }
}
