package monster.greyde.kachalochka.ui.machine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.fakes.FakeGym
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MachinePickerViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val visit = Visit(VisitId.random(), null, t0, null, t0, false)
    private val otherVisit = VisitId.random()
    private val press = Machine.new("Жим ногами", null, t0)
    private val smith = Machine.new("Приседания в Смите", null, t0)

    private val ivan = session("11111111-1111-4111-8111-111111111111", "Иван")
    private val misha = session("22222222-2222-4222-8222-222222222222", "Миша")

    private fun session(
        id: String,
        name: String,
    ) = AccountSession(Account(UserId(id), "$name@example.test", name), "access", "refresh", t0)

    private fun set(
        visitId: VisitId,
        machine: Machine,
        weight: Double,
        reps: Int,
        recordedAt: Instant,
        owner: UserId? = null,
    ) = WorkoutSet(
        WorkoutSetId.random(),
        owner,
        visitId,
        machine.id,
        weight,
        reps,
        recordedAt,
        t0,
        false,
    )

    private fun viewModel() =
        MachinePickerViewModel(
            visit.id,
            gym.machines,
            gym.sets,
            gym.visits,
            gym.currentUser,
            gym.accounts,
            gym.clock,
            gym.utcOffset,
        )

    @BeforeTest
    fun setUp() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            gym.visits.upsert(visit)
            gym.machines.upsert(press)
            gym.machines.upsert(smith)
            listOf(70.0 to 10, 70.0 to 10, 75.0 to 8).forEachIndexed { i, (w, r) ->
                gym.sets.upsert(set(visit.id, press, w, r, t0 + i.minutes))
            }
            gym.sets.upsert(set(otherVisit, smith, 80.0, 8, t0 - 4.days))
        }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun rows_say_what_happened_today_or_last_time() {
        val vm = viewModel().also { it.load() }

        assertEquals(
            listOf(
                "Жим ногами" to "3 подхода сегодня",
                "Приседания в Смите" to "Было 80 кг × 8 · 4 дня назад",
            ),
            vm.state.value.rows
                .map { it.name to it.detail },
        )
        assertEquals("Недавние", vm.state.value.sectionLabel)
        assertNull(vm.state.value.createLabel)
    }

    @Test
    fun typing_a_new_name_offers_to_create_it_and_shows_similar_machines() {
        val vm = viewModel().also { it.load() }

        vm.onQueryChange("гакк")

        assertEquals("Создать «гакк»", vm.state.value.createLabel)
        assertEquals("Похожие", vm.state.value.sectionLabel)
        assertEquals(2, vm.state.value.rows.size)
    }

    @Test
    fun typing_an_existing_name_does_not_offer_creation() {
        val vm = viewModel().also { it.load() }

        vm.onQueryChange("жим ногами")

        assertNull(vm.state.value.createLabel)
        assertEquals(
            listOf("Жим ногами"),
            vm.state.value.rows
                .map { it.name },
        )
    }

    /** The avatar switches accounts from this screen too, so the list has to follow it. */
    @Test
    fun the_rows_and_their_counts_follow_the_account_that_became_active() =
        runTest {
            val shared = FakeGym().withAccounts(misha, ivan, active = misha)
            val hers = Visit(VisitId.random(), misha.account.userId, t0, null, t0, false)
            val his = Visit(VisitId.random(), ivan.account.userId, t0, null, t0, false)
            val herPress = Machine.new("Жим ногами", misha.account.userId, t0)
            val hisPress = Machine.new("Жим Ивана", ivan.account.userId, t0)
            shared.visits.upsert(hers)
            shared.visits.upsert(his)
            shared.machines.upsert(herPress)
            shared.machines.upsert(hisPress)
            repeat(3) {
                shared.sets.upsert(
                    set(hers.id, herPress, 70.0, 10, t0 + it.minutes, misha.account.userId),
                )
            }
            repeat(2) {
                shared.sets.upsert(
                    set(his.id, hisPress, 60.0, 10, t0 + it.minutes, ivan.account.userId),
                )
            }
            val vm =
                MachinePickerViewModel(
                    hers.id,
                    shared.machines,
                    shared.sets,
                    shared.visits,
                    shared.currentUser,
                    shared.accounts,
                    shared.clock,
                    shared.utcOffset,
                ).also { it.load() }
            assertEquals(
                listOf("Жим ногами" to "3 подхода сегодня"),
                vm.state.value.rows
                    .map { it.name to it.detail },
            )

            shared.accounts.switchTo(ivan.account.userId)
            advanceUntilIdle()

            assertEquals(
                listOf("Жим Ивана" to "2 подхода сегодня"),
                vm.state.value.rows
                    .map { it.name to it.detail },
            )
        }
}
