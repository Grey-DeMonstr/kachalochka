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
                    weightStep = "5",
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

            vm.update { it.copy(weightStep = "10") }
            vm.save {}

            val saved = assertNotNull(gym.machines.byId(source.id))
            assertEquals(10.0, saved.weightStep)
            assertEquals(gym.clock.current, saved.updatedAt)
            assertEquals(1, gym.machines.all(null).size)
        }

    @Test
    fun an_own_unit_needs_a_name() {
        val custom = MachineFormState(name = "Гравитрон", unit = WeightUnit.Custom)

        assertEquals(false, custom.canSave)
        assertEquals(false, custom.copy(unitLabel = "  ").canSave)
        assertEquals(true, custom.copy(unitLabel = "плитка").canSave)
    }

    @Test
    fun an_own_unit_is_saved_with_its_name_and_kilograms_without_one() =
        runTest {
            val vm = viewModel(MachineFormArgs(null, null, "Гравитрон")).also { it.load() }
            vm.update { it.copy(unit = WeightUnit.Custom, unitLabel = " плитка ") }
            var saved: MachineId? = null

            vm.save { saved = it }

            val machine = assertNotNull(gym.machines.byId(assertNotNull(saved)))
            assertEquals(WeightUnit.Custom to "плитка", machine.unit to machine.unitLabel)

            val again = viewModel(MachineFormArgs(machine.id, null, "")).also { it.load() }
            assertEquals("плитка", again.state.value.unitLabel)
            again.update { it.copy(unit = WeightUnit.Kg) }
            again.save {}

            val kilograms = assertNotNull(gym.machines.byId(machine.id))
            assertEquals(WeightUnit.Kg to "", kilograms.unit to kilograms.unitLabel)
        }

    @Test
    fun a_switch_while_editing_saves_a_copy_for_the_account_that_became_active() =
        runTest {
            gym.withAccounts(misha, ivan, active = misha)
            val hers = Machine.new("Жим ногами", misha.account.userId, t0)
            gym.machines.upsert(hers)
            val vm = viewModel(MachineFormArgs(hers.id, null, "")).also { it.load() }

            gym.accounts.switchTo(ivan.account.userId)
            vm.update { it.copy(weightStep = "10", setupNote = "Упоры на 3") }
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

    @Test
    fun the_weight_step_is_any_positive_decimal() {
        assertEquals(1.25, MachineFormState(name = "Гакк", weightStep = "1,25").weightStepValue)
        assertEquals(0.5, MachineFormState(name = "Гакк", weightStep = "0.5").weightStepValue)
        assertEquals(false, MachineFormState(name = "Гакк", weightStep = "0").canSave)
        assertEquals(false, MachineFormState(name = "Гакк", weightStep = "0,0001").canSave)
        assertEquals(false, MachineFormState(name = "Гакк", weightStep = "").canSave)
        assertEquals(false, MachineFormState(name = "Гакк", weightStep = "-1").canSave)
    }

    @Test
    fun an_existing_step_loads_as_text() =
        runTest {
            val press = Machine.new("Жим ногами", null, t0).copy(weightStep = 1.25)
            gym.machines.upsert(press)

            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }

            assertEquals("1,25", vm.state.value.weightStep)
        }
}
