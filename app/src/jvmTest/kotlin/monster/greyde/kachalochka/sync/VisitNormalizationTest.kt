package monster.greyde.kachalochka.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.FailureLog
import monster.greyde.kachalochka.core.data.gym.VisitNormalizer
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.fakes.FakeGym
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class VisitNormalizationTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val ivan = session("11111111-1111-4111-8111-111111111111", "Иван")
    private val misha = session("22222222-2222-4222-8222-222222222222", "Миша")
    private val failures = mutableListOf<Throwable>()

    private fun session(
        id: String,
        name: String,
    ) = AccountSession(Account(UserId(id), "$name@example.test", name), "access", "refresh", t0)

    private fun normalization(
        on: FakeGym = gym,
        visits: VisitRepository = on.visits,
        store: VisitStore = VisitStore.Server,
    ) = VisitNormalization(
        VisitNormalizer(visits, on.sets, on.clock) { Duration.ZERO },
        on.accounts,
        on.sync,
        store,
        FailureLog { failures += it },
    )

    /** Two visits on today; returns the one normalization removes. */
    private fun twoVisitsToday(
        on: FakeGym,
        owner: UserId?,
    ): Visit {
        val older = Visit(VisitId.random(), owner, on.today, t0 - 1.hours, t0, false)
        runBlocking {
            on.visits.upsert(older)
            on.visits.upsert(Visit(VisitId.random(), owner, on.today, t0, t0, false))
        }
        return older
    }

    @Test
    fun the_owners_given_at_start_are_normalized_and_a_write_asks_for_a_pass() =
        runTest(UnconfinedTestDispatcher()) {
            val older = twoVisitsToday(gym, null)

            backgroundScope.launch { normalization().run(listOf(null)) }
            runCurrent()

            assertEquals(true, gym.visits.byId(older.id)?.deleted)
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun on_the_server_an_account_that_becomes_active_is_normalized() =
        runTest(UnconfinedTestDispatcher()) {
            val two = FakeGym().withAccounts(ivan, misha, active = ivan)
            val older = twoVisitsToday(two, misha.account.userId)
            backgroundScope.launch { normalization(two).run(emptyList()) }
            runCurrent()
            assertEquals(false, two.visits.byId(older.id)?.deleted)

            two.accounts.switchTo(misha.account.userId)
            runCurrent()

            assertEquals(true, two.visits.byId(older.id)?.deleted)
        }

    @Test
    fun on_a_device_an_account_that_becomes_active_asks_for_a_pass_and_keeps_its_rows() =
        runTest(UnconfinedTestDispatcher()) {
            val two = FakeGym().withAccounts(ivan, misha, active = ivan)
            val older = twoVisitsToday(two, misha.account.userId)
            backgroundScope.launch {
                normalization(two, store = VisitStore.Device).run(emptyList())
            }
            runCurrent()
            assertEquals(1, two.sync.requests)

            two.accounts.switchTo(misha.account.userId)
            runCurrent()

            assertEquals(2, two.sync.requests)
            assertEquals(false, two.visits.byId(older.id)?.deleted)
        }

    @Test
    fun nothing_to_normalize_asks_for_no_pass() =
        runTest(UnconfinedTestDispatcher()) {
            gym.visits.upsert(Visit(VisitId.random(), null, gym.today, t0, t0, false))

            backgroundScope.launch { normalization().run(listOf(null)) }
            runCurrent()

            assertEquals(0, gym.sync.requests)
        }

    @Test
    fun an_owner_that_fails_is_reported_and_the_next_one_still_runs() =
        runTest(UnconfinedTestDispatcher()) {
            val ivanId = ivan.account.userId
            val older = twoVisitsToday(gym, ivanId)
            val offlineForNull =
                object : VisitRepository by gym.visits {
                    override suspend fun all(owner: UserId?): List<Visit> =
                        if (owner == null) error("no connection") else gym.visits.all(owner)
                }

            backgroundScope.launch {
                normalization(visits = offlineForNull).run(listOf(null, ivanId))
            }
            runCurrent()

            assertEquals(listOf("no connection"), failures.map { it.message })
            assertEquals(true, gym.visits.byId(older.id)?.deleted)
            assertEquals(1, gym.sync.requests)
        }
}
