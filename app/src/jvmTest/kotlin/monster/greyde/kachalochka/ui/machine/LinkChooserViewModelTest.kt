package monster.greyde.kachalochka.ui.machine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.gym.MachineLinkRepository
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.format.weightCaption
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class LinkChooserViewModelTest {
    private var gym = signedInGym()
    private val t0 = gym.clock.current
    private val me: UserId? get() = gym.accounts.activeId.value
    private lateinit var press: Machine
    private lateinit var duplicate: Machine
    private lateinit var smith: Machine

    private fun machinesOf(owner: UserId?) {
        press = Machine.new("Жим ногами", owner, t0)
        duplicate = Machine.new("Жим ногами 2", owner, t0)
        smith = Machine.new("Смит", owner, t0)
        runBlocking { listOf(press, duplicate, smith).forEach { gym.machines.upsert(it) } }
    }

    private fun set(
        machine: Machine,
        minutes: Int,
    ) = WorkoutSet(
        WorkoutSetId.random(),
        machine.userId,
        VisitId.random(),
        machine.id,
        70.0,
        10,
        0,
        t0 + minutes.minutes,
        t0,
        false,
    )

    private fun viewModel(
        machine: MachineId = press.id,
        machines: MachineRepository = gym.machines,
        sets: WorkoutSetRepository = gym.sets,
        links: MachineLinkRepository = gym.machineLinks,
    ) = LinkChooserViewModel(
        machine,
        machines,
        sets,
        links,
        gym.friends,
        gym.currentUser,
        gym.accounts,
        gym.clock,
        gym.sync,
        gym.profiles,
        gym.photos,
        gym.catalogue,
    ).also { it.load() }

    /** Олег's machines: his copy of [press], linked to it, and one of his own. */
    private fun olegsMachines(): Pair<Machine, Machine> {
        gym.friends.group("Зал на Лесной", owner = OLEG, ME)
        val (copy, link) = linkedCopy(press, OLEG.userId, t0)
        val bench = Machine.new("Жим лёжа", OLEG.userId, t0)
        gym.friends.machines += listOf(copy, bench)
        gym.friends.links += link
        return copy to bench
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        machinesOf(ME.userId)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun the_own_section_lists_every_other_own_machine() {
        val state = viewModel().state.value

        assertEquals(listOf(duplicate.id, smith.id), state.own.map { it.id })
        assertEquals(
            ChooserRowUi(
                duplicate.id,
                "Жим ногами 2",
                weightCaption(duplicate, PreferredWeightUnit.Kg),
            ),
            state.own.first(),
        )
    }

    @Test
    fun a_machine_s_step_reads_in_the_unit_chosen_in_the_profile() {
        runBlocking {
            gym.profiles.upsert(Profile.new(me, t0).copy(weightUnit = PreferredWeightUnit.Lb))
        }

        val state = viewModel().state.value

        assertEquals("lb всего · ±5.5", state.own.first().detail)
    }

    @Test
    fun the_friends_section_leaves_out_the_machine_s_own_cluster() {
        val (_, bench) = olegsMachines()

        val friends = viewModel().state.value.friends

        assertEquals(
            listOf(
                ChooserRowUi(
                    bench.id,
                    "Жим лёжа",
                    "Олег · ${weightCaption(bench, PreferredWeightUnit.Kg)}",
                ),
            ),
            friends,
        )
    }

    @Test
    fun offline_only_own_machines_are_offered() {
        olegsMachines()
        gym.friends.offline = true

        val state = viewModel().state.value

        assertNull(state.friends)
        assertEquals(listOf(duplicate.id, smith.id), state.own.map { it.id })
    }

    @Test
    fun a_search_filters_both_sections() {
        olegsMachines()
        val vm = viewModel()

        vm.onQueryChange("жим")

        assertEquals(
            listOf(duplicate.id),
            vm.state.value.own
                .map { it.id },
        )
        assertEquals(
            listOf("Жим лёжа"),
            vm.state.value.friends
                ?.map { it.name },
        )

        vm.onQueryChange("смит")

        assertEquals(
            listOf(smith.id),
            vm.state.value.own
                .map { it.id },
        )
        assertEquals(emptyList(), vm.state.value.friends)
    }

    @Test
    fun choosing_a_friend_s_machine_links_this_one_to_it() {
        val (_, bench) = olegsMachines()
        gym.clock.current += 1.minutes
        var done = false

        viewModel().chooseFriend(bench.id) { done = true }

        val link = runBlocking { gym.machineLinks.all(me) }.single()
        assertEquals(
            MachineLink(link.id, me, press.id, bench.id, gym.clock.current, false),
            link,
        )
        assertTrue(done)
        assertEquals(1, gym.sync.requests)
    }

    @Test
    fun choosing_an_own_machine_asks_which_one_stays() {
        runBlocking { gym.sets.upsert(set(duplicate, 0)) }
        val vm = viewModel()

        vm.chooseOwn(duplicate.id)

        assertEquals(
            MergeUi(
                "Объединить упражнения?",
                "Останется «Жим ногами 2», подходы «Жим ногами» перейдут к нему. " +
                    "Это нельзя отменить.",
            ),
            vm.state.value.merge,
        )
        vm.cancelMerge()
        assertNull(vm.state.value.merge)
    }

    @Test
    fun a_merge_keeps_the_older_machine_and_moves_everything_to_it() {
        val older = set(duplicate, 0)
        val newer = set(press, 10)
        runBlocking {
            gym.sets.upsert(older)
            gym.sets.upsert(newer)
        }
        val ownLink =
            MachineLink(MachineLinkId.random(), me, press.id, MachineId.random(), t0, false)
        runBlocking { gym.machineLinks.upsert(ownLink) }
        gym.clock.current += 1.days
        val now = gym.clock.current
        val vm = viewModel()
        var kept: MachineId? = null

        vm.chooseOwn(duplicate.id)
        vm.confirmMerge { kept = it }

        assertEquals(duplicate.id, kept)
        assertEquals(
            newer.copy(machineId = duplicate.id, updatedAt = now),
            gym.sets.rows[newer.id],
        )
        assertEquals(older, gym.sets.rows[older.id])
        assertEquals(
            ownLink.copy(machineId = duplicate.id, updatedAt = now),
            gym.machineLinks.rows[ownLink.id],
        )
        assertEquals(press.copy(deleted = true, updatedAt = now), gym.machines.rows[press.id])
        assertEquals(1, gym.sync.requests)
        assertEquals(emptyList(), gym.friends.repointed)
    }

    @Test
    fun a_merge_moves_the_removed_machine_s_photos_to_the_kept_one() {
        runBlocking { gym.sets.upsert(set(duplicate, 0)) }
        val photo = Photo.new(press.id, me, t0)
        runBlocking { gym.photos.add(photo, byteArrayOf(1)) }
        gym.clock.current += 1.days
        val vm = viewModel()

        vm.chooseOwn(duplicate.id)
        vm.confirmMerge {}

        assertEquals(
            listOf(photo.copy(machineId = duplicate.id, updatedAt = gym.clock.current)),
            runBlocking { gym.photos.forMachine(duplicate.id) },
        )
    }

    @Test
    fun a_merge_moves_friends_links_to_the_kept_machine_first() {
        val (copy, _) = olegsMachines()
        runBlocking { gym.sets.upsert(set(duplicate, 0)) }
        val vm = viewModel()

        vm.chooseOwn(duplicate.id)
        vm.confirmMerge {}

        assertEquals(listOf(press.id to duplicate.id), gym.friends.repointed)
        assertEquals(
            duplicate.id,
            gym.friends.links
                .single { it.machineId == copy.id }
                .linkedMachineId,
        )
        assertTrue(
            gym.machines.rows
                .getValue(press.id)
                .deleted,
        )
    }

    @Test
    fun a_merge_writes_the_sets_then_the_links_and_the_machine_last() {
        runBlocking {
            gym.sets.upsert(set(duplicate, 0))
            gym.sets.upsert(set(press, 10))
            gym.machineLinks.upsert(
                MachineLink(MachineLinkId.random(), me, press.id, MachineId.random(), t0, false),
            )
        }
        val order = mutableListOf<String>()
        val machines =
            object : MachineRepository by gym.machines {
                override suspend fun upsert(machine: Machine) {
                    order += "machine"
                    gym.machines.upsert(machine)
                }
            }
        val sets =
            object : WorkoutSetRepository by gym.sets {
                override suspend fun upsert(set: WorkoutSet) {
                    order += "set"
                    gym.sets.upsert(set)
                }
            }
        val links =
            object : MachineLinkRepository by gym.machineLinks {
                override suspend fun upsert(link: MachineLink) {
                    order += "link"
                    gym.machineLinks.upsert(link)
                }
            }
        val vm = viewModel(machines = machines, sets = sets, links = links)

        vm.chooseOwn(duplicate.id)
        vm.confirmMerge {}

        assertEquals(listOf("set", "link", "machine"), order)
    }

    @Test
    fun a_mate_s_link_into_the_kept_machine_needs_no_repointing() {
        olegsMachines()
        runBlocking { gym.sets.upsert(set(press, 0)) }
        val vm = viewModel()

        vm.chooseOwn(duplicate.id)
        vm.confirmMerge {}

        assertEquals(emptyList(), gym.friends.repointed)
        assertTrue(
            gym.machines.rows
                .getValue(duplicate.id)
                .deleted,
        )
    }

    @Test
    fun a_merge_whose_machine_went_meanwhile_closes_the_dialog() {
        val vm = viewModel()
        vm.chooseOwn(duplicate.id)
        runBlocking { gym.machines.upsert(duplicate.copy(deleted = true)) }
        var done = false

        vm.confirmMerge { done = true }

        assertNull(vm.state.value.merge)
        assertEquals(false, done)
        assertEquals(
            false,
            gym.machines.rows
                .getValue(press.id)
                .deleted,
        )
    }

    @Test
    fun offline_a_merge_says_so_and_writes_nothing() {
        olegsMachines()
        val moved = set(press, 0)
        runBlocking { gym.sets.upsert(moved) }
        val vm = viewModel()
        gym.friends.offline = true

        vm.chooseOwn(duplicate.id)
        vm.confirmMerge {}

        assertEquals("Нет связи с сервером", vm.state.value.error)
        assertNull(vm.state.value.merge)
        assertEquals(moved, gym.sets.rows[moved.id])
        assertEquals(
            false,
            gym.machines.rows
                .getValue(duplicate.id)
                .deleted,
        )
        assertEquals(0, gym.sync.requests)
    }

    @Test
    fun without_an_account_a_merge_stays_on_the_device() =
        runTest {
            gym = FakeGym()
            machinesOf(null)
            val vm = viewModel()
            var kept: MachineId? = null

            vm.chooseOwn(duplicate.id)
            vm.confirmMerge { kept = it }

            assertEquals(press.id, kept)
            assertTrue(
                gym.machines.rows
                    .getValue(duplicate.id)
                    .deleted,
            )
            assertNull(vm.state.value.friends)
            assertEquals(0, gym.friends.reads)
        }
}
