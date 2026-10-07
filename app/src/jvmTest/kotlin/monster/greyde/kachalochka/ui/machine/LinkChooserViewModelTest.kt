package monster.greyde.kachalochka.ui.machine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.gym.MachineLinkRepository
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.format.dayMonthLabel
import monster.greyde.kachalochka.ui.format.weightCaption
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.PASHA
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
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
        /** Replaces the search the chooser starts with; null keeps it. */
        query: String? = "",
        start: String? = null,
    ) = LinkChooserViewModel(
        machine,
        start,
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
        gym.utcOffset,
    ).also { vm ->
        vm.load()
        query?.let(vm::onQueryChange)
    }

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
    fun the_search_starts_with_the_machine_s_name_and_keeps_what_is_typed() {
        val vm = viewModel(query = null)

        assertEquals("Жим ногами", vm.state.value.query)
        assertEquals(
            listOf(duplicate.id),
            vm.state.value.own
                .map { it.id },
        )

        vm.onQueryChange("смит")
        vm.load()

        assertEquals("смит", vm.state.value.query)
    }

    @Test
    fun a_suggestion_starts_the_search_with_the_suggested_machine_s_name() {
        val vm = viewModel(query = null, start = "Смит")

        assertEquals("Смит", vm.state.value.query)
        assertEquals(
            listOf(smith.id),
            vm.state.value.own
                .map { it.id },
        )
    }

    @Test
    fun a_friend_s_machine_reached_through_another_friend_s_is_offered_and_linked() {
        gym.friends.group("Зал на Лесной", owner = OLEG, ME, PASHA)
        val (olegs, link) = linkedCopy(press, OLEG.userId, t0)
        val pashas = Machine.new("Платформа", PASHA.userId, t0)
        gym.friends.machines += listOf(olegs, pashas)
        gym.friends.links += link
        gym.friends.links +=
            MachineLink(MachineLinkId.random(), PASHA.userId, pashas.id, olegs.id, t0, false)
        val vm = viewModel(query = null, start = "Платформа")

        assertEquals(
            listOf(listOf(pashas.id)),
            vm.state.value.friendGroups
                ?.map { g -> g.map { it.id } },
        )

        vm.chooseFriend(pashas.id) {}

        val written =
            gym.machineLinks.rows.values
                .single()
        assertEquals(press.id to pashas.id, written.machineId to written.linkedMachineId)
    }

    @Test
    fun a_search_finds_an_own_machine_by_the_name_of_a_linked_friend_s_one() {
        gym.friends.group("Зал на Лесной", owner = OLEG, ME)
        val (olegs, link) = linkedCopy(smith, OLEG.userId, t0)
        gym.friends.machines += olegs.copy(name = "Гакк-машина")
        gym.friends.links += link

        val vm = viewModel(query = "гакк")

        assertEquals(
            listOf(smith.id to listOf(LinkedMachineUi(olegs.id, OLEG, "Гакк-машина"))),
            vm.state.value.own
                .map { it.id to it.linkedWith },
        )
    }

    @Test
    fun copy_settings_hands_the_chosen_friend_s_machine_back() {
        val (_, bench) = olegsMachines()
        val vm = viewModel()
        assertEquals(false, vm.state.value.copySettings)
        var copyFrom: MachineId? = null

        vm.setCopySettings(true)
        vm.chooseFriend(bench.id) { copyFrom = it }

        assertEquals(bench.id, copyFrom)
    }

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
    fun linked_friends_machines_are_each_offered_together() {
        val (_, bench) = olegsMachines()
        val pashas = Machine.new("Жим на скамье", PASHA.userId, t0)
        val (olegsBench, link) = linkedCopy(bench, PASHA.userId, t0)
        gym.friends.group("Зал на Лесной", owner = OLEG, ME, PASHA)
        gym.friends.machines += listOf(pashas, olegsBench.copy(name = "Скамья"))
        gym.friends.links += link

        val groups = viewModel().state.value.friendGroups

        assertEquals(
            listOf(listOf(bench.id, olegsBench.id), listOf(pashas.id)),
            groups?.map { group -> group.map { it.id } },
        )
    }

    @Test
    fun a_search_keeps_a_whole_group_when_one_of_it_matches() {
        val (_, bench) = olegsMachines()
        val (pashasBench, link) = linkedCopy(bench, PASHA.userId, t0)
        gym.friends.group("Зал на Лесной", owner = OLEG, ME, PASHA)
        gym.friends.machines += pashasBench.copy(name = "Скамья")
        gym.friends.links += link
        val vm = viewModel()

        vm.onQueryChange("скамья")

        assertEquals(
            listOf(bench.id, pashasBench.id),
            vm.state.value.friends
                ?.map { it.id },
        )
    }

    @Test
    fun the_rows_show_the_machines_photos() {
        val (_, bench) = olegsMachines()
        val own = Photo.new(duplicate.id, me, t0)
        val olegs = Photo.new(bench.id, OLEG.userId, t0)
        runBlocking { gym.photos.add(own, byteArrayOf(1)) }
        gym.friends.photos += olegs

        val state = viewModel().state.value

        assertEquals(own, state.own.first().photo)
        assertEquals(
            olegs,
            state.friends
                ?.single()
                ?.photo,
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
        var copyFrom: MachineId? = MachineId.random()

        viewModel().chooseFriend(bench.id) {
            done = true
            copyFrom = it
        }
        assertNull(copyFrom)

        val link = runBlocking { gym.machineLinks.all(me) }.single()
        assertEquals(
            MachineLink(link.id, me, press.id, bench.id, gym.clock.current, false),
            link,
        )
        assertTrue(done)
        assertEquals(1, gym.sync.requests)
    }

    @Test
    fun choosing_an_own_machine_asks_which_one_stays_and_suggests_the_one_used_last() {
        runBlocking { gym.sets.upsert(set(duplicate, 0)) }
        val vm = viewModel()

        vm.chooseOwn(duplicate.id)

        val lastDay = dayMonthLabel(CalendarDay.of(t0, Duration.ZERO), gym.today.year)
        assertEquals(
            MergeUi(
                choices =
                    listOf(
                        MergeChoiceUi(press.id, "Жим ногами", "0 подходов", suggested = false),
                        MergeChoiceUi(
                            duplicate.id,
                            "Жим ногами 2",
                            "1 подход · последний $lastDay",
                            suggested = true,
                        ),
                    ),
                kept = duplicate.id,
                text =
                    "Останется «Жим ногами 2», подходы «Жим ногами» перейдут к нему. " +
                        "Это нельзя отменить.",
            ),
            vm.state.value.merge,
        )
        vm.cancelMerge()
        assertNull(vm.state.value.merge)
    }

    @Test
    fun the_machine_not_suggested_can_be_kept_instead() {
        val moved = set(duplicate, 0)
        runBlocking { gym.sets.upsert(moved) }
        val vm = viewModel()
        var kept: MachineId? = null

        vm.chooseOwn(duplicate.id)
        vm.keep(press.id)
        vm.confirmMerge { kept = it }

        assertEquals(press.id, kept)
        assertEquals(
            press.id,
            gym.sets.rows
                .getValue(moved.id)
                .machineId,
        )
        assertTrue(
            gym.machines.rows
                .getValue(duplicate.id)
                .deleted,
        )
    }

    @Test
    fun keeping_the_other_machine_turns_the_text_around() {
        val vm = viewModel()

        vm.chooseOwn(duplicate.id)
        vm.keep(duplicate.id)

        assertEquals(
            "Останется «Жим ногами 2», подходы «Жим ногами» перейдут к нему. " +
                "Это нельзя отменить.",
            vm.state.value.merge
                ?.text,
        )
    }

    /** [duplicate] holds imported totals; [press], used last, has its platform beside the name. */
    private fun platformsDiffer(): WorkoutSet {
        val imported = set(duplicate, 0)
        runBlocking {
            gym.machines.upsert(press.copy(platformWeight = 25.0))
            gym.sets.upsert(imported)
            gym.sets.upsert(set(press, 10))
        }
        return imported
    }

    @Test
    fun differing_platforms_offer_to_keep_the_moved_sets_totals() {
        val imported = platformsDiffer()
        gym.clock.current += 1.days
        val vm = viewModel()

        vm.chooseOwn(duplicate.id)

        val merge = vm.state.value.merge!!
        assertEquals(
            "Пересчитать подходы «Жим ногами 2» под платформу «Жим ногами»: −25 кг",
            merge.adjustText,
        )
        assertTrue(merge.adjust)
        vm.confirmMerge {}
        assertEquals(
            45.0,
            gym.sets.rows
                .getValue(imported.id)
                .weight,
        )
    }

    @Test
    fun the_moved_sets_keep_their_weights_when_asked_to() {
        val imported = platformsDiffer()
        val vm = viewModel()

        vm.chooseOwn(duplicate.id)
        vm.setAdjust(false)
        vm.confirmMerge {}

        assertEquals(
            70.0,
            gym.sets.rows
                .getValue(imported.id)
                .weight,
        )
        assertEquals(
            press.id,
            gym.sets.rows
                .getValue(imported.id)
                .machineId,
        )
    }

    /** [duplicate] is in kilograms, with sets; [press], used last, is a 5 lb machine. */
    private fun unitsDiffer(): WorkoutSet {
        val imported = set(duplicate, 0).copy(weight = 22.7)
        runBlocking {
            gym.machines.upsert(press.copy(unit = WeightUnit.Lb, weightStep = 5.0))
            gym.sets.upsert(imported)
            gym.sets.upsert(set(press, 10))
        }
        return imported
    }

    @Test
    fun differing_units_offer_to_convert_the_moved_sets() {
        val imported = unitsDiffer()
        gym.clock.current += 1.days
        val vm = viewModel()

        vm.chooseOwn(duplicate.id)

        val merge = vm.state.value.merge!!
        assertEquals("Перевести подходы «Жим ногами 2» из кг в lb", merge.convertText)
        assertTrue(merge.convert)
        vm.confirmMerge {}
        assertEquals(
            50.0,
            gym.sets.rows
                .getValue(imported.id)
                .weight,
        )
    }

    @Test
    fun the_moved_sets_keep_their_numbers_when_not_converted() {
        val imported = unitsDiffer()
        val vm = viewModel()

        vm.chooseOwn(duplicate.id)
        vm.setConvert(false)
        vm.confirmMerge {}

        assertEquals(
            22.7,
            gym.sets.rows
                .getValue(imported.id)
                .weight,
        )
    }

    @Test
    fun equal_units_offer_no_conversion() {
        val vm = viewModel()

        vm.chooseOwn(duplicate.id)

        assertNull(
            vm.state.value.merge
                ?.convertText,
        )
    }

    @Test
    fun equal_platforms_offer_no_recalculation() {
        val vm = viewModel()

        vm.chooseOwn(duplicate.id)

        assertNull(
            vm.state.value.merge
                ?.adjustText,
        )
    }

    @Test
    fun a_merge_keeps_the_machine_used_last_and_moves_everything_to_it() {
        val stays = set(duplicate, 10)
        val moved = set(press, 0)
        runBlocking {
            gym.sets.upsert(stays)
            gym.sets.upsert(moved)
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
            moved.copy(machineId = duplicate.id, updatedAt = now),
            gym.sets.rows[moved.id],
        )
        assertEquals(stays, gym.sets.rows[stays.id])
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
            gym.sets.upsert(set(duplicate, 10))
            gym.sets.upsert(set(press, 0))
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
