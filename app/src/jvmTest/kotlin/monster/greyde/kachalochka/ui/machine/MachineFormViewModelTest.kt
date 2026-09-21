package monster.greyde.kachalochka.ui.machine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.fakes.FakeGym
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class MachineFormViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val ivan = session("11111111-1111-4111-8111-111111111111", "Иван")
    private val misha = session("22222222-2222-4222-8222-222222222222", "Миша")

    private fun session(
        id: String,
        name: String,
    ) = AccountSession(Account(UserId(id), "$name@example.test", name), "access", "refresh", t0)

    private fun viewModel(args: MachineFormArgs) =
        MachineFormViewModel(args, gym.machines, gym.currentUser, gym.accounts, gym.clock)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun a_new_machine_starts_from_the_typed_name_and_the_defaults() {
        val vm = viewModel(MachineFormArgs(null, null, "гакк")).also { it.load() }

        assertEquals(MachineFormState(name = "гакк"), vm.state.value)
        assertEquals(true, vm.state.value.canSave)
    }

    @Test
    fun loading_again_keeps_the_unsaved_edits() {
        val vm = viewModel(MachineFormArgs(null, null, "Гакк")).also { it.load() }
        vm.update { it.copy(setupNote = "Упоры на 3") }

        vm.load()

        assertEquals("Упоры на 3", vm.state.value.setupNote)
    }

    @Test
    fun a_blank_name_or_a_bad_platform_weight_cannot_be_saved() {
        assertEquals(false, MachineFormState(name = " ").canSave)
        assertEquals(false, MachineFormState(name = "Гакк", platformWeight = "-5").canSave)
        assertEquals(false, MachineFormState(name = "Гакк", platformWeight = "abc").canSave)
        assertEquals(25.5, MachineFormState(platformWeight = "25,5").platformWeightValue)
        assertEquals(0.0, MachineFormState(platformWeight = "").platformWeightValue)
    }

    @Test
    fun saving_a_new_machine_stores_every_field() =
        runTest {
            val vm = viewModel(MachineFormArgs(null, null, "Гакк-машина")).also { it.load() }
            vm.update {
                it.copy(
                    setupNote = "Упоры на 3",
                    weightMode = WeightMode.PerSide,
                    platformWeight = "25",
                    platformIncluded = true,
                    unit = WeightUnit.Lb,
                    weightStep = 5.0,
                )
            }
            var saved: MachineId? = null

            vm.save { saved = it }

            val machine = assertNotNull(gym.machines.byId(assertNotNull(saved)))
            assertEquals(
                listOf(
                    "Гакк-машина",
                    "Упоры на 3",
                    WeightMode.PerSide,
                    25.0,
                    true,
                    WeightUnit.Lb,
                    5.0,
                ),
                listOf(
                    machine.name,
                    machine.setupNote,
                    machine.weightMode,
                    machine.platformWeight,
                    machine.platformIncluded,
                    machine.unit,
                    machine.weightStep,
                ),
            )
        }

    @Test
    fun a_copy_keeps_the_settings_under_a_new_id_and_name() =
        runTest {
            val source =
                Machine
                    .new(
                        "Жим ногами",
                        null,
                        t0,
                    ).copy(setupNote = "Сиденье на 4", platformWeight = 20.0)
            gym.machines.upsert(source)
            val vm =
                viewModel(
                    MachineFormArgs(null, source.id, "Жим одной ногой"),
                ).also { it.load() }
            var saved: MachineId? = null

            vm.save { saved = it }

            val copy = assertNotNull(gym.machines.byId(assertNotNull(saved)))
            assertNotEquals(source.id, copy.id)
            assertEquals("Жим одной ногой", copy.name)
            assertEquals("Сиденье на 4", copy.setupNote)
            assertEquals(20.0, copy.platformWeight)
        }

    @Test
    fun editing_an_existing_machine_keeps_its_id() =
        runTest {
            val source = Machine.new("Жим ногами", null, t0)
            gym.machines.upsert(source)
            gym.clock.current += 1.minutes
            val vm = viewModel(MachineFormArgs(source.id, null, "")).also { it.load() }
            assertEquals("Жим ногами", vm.state.value.name)

            vm.update { it.copy(weightStep = 10.0) }
            vm.save {}

            val saved = assertNotNull(gym.machines.byId(source.id))
            assertEquals(10.0, saved.weightStep)
            assertEquals(gym.clock.current, saved.updatedAt)
            assertEquals(1, gym.machines.all(null).size)
        }

    @Test
    fun a_switch_while_editing_saves_a_copy_for_the_account_that_became_active() =
        runTest {
            gym.withAccounts(misha, ivan, active = misha)
            val hers = Machine.new("Жим ногами", misha.account.userId, t0)
            gym.machines.upsert(hers)
            val vm = viewModel(MachineFormArgs(hers.id, null, "")).also { it.load() }

            gym.accounts.switchTo(ivan.account.userId)
            vm.update { it.copy(weightStep = 10.0, setupNote = "Упоры на 3") }
            var saved: MachineId? = null
            vm.save { saved = it }

            val untouched = assertNotNull(gym.machines.byId(hers.id))
            assertEquals(2.5, untouched.weightStep)
            assertEquals("", untouched.setupNote)
            val mirrored = assertNotNull(gym.machines.byId(assertNotNull(saved)))
            assertNotEquals(hers.id, mirrored.id)
            assertEquals(ivan.account.userId, mirrored.userId)
            assertEquals(10.0, mirrored.weightStep)
        }
}
