package monster.greyde.kachalochka.ui.machine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.gym.MachineSort
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.family.SASHA
import monster.greyde.kachalochka.ui.family.childAccount
import monster.greyde.kachalochka.ui.friends.FriendColorStore
import monster.greyde.kachalochka.ui.friends.IVAN_SESSION
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.PASHA
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class MachineListViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val ivan = session("11111111-1111-4111-8111-111111111111", "Иван")
    private val misha = session("22222222-2222-4222-8222-222222222222", "Миша")

    private fun session(
        id: String,
        name: String,
    ) = AccountSession(Account(UserId(id), "$name@example.test", name), "access", "refresh", t0)

    private fun viewModel(on: FakeGym = gym) =
        MachineListViewModel(
            on.catalogue,
            on.currentUser,
            on.accounts,
            on.sync,
            on.profiles,
            on.sets,
            on.friends,
            FriendColorStore(on.profiles, on.clock, on.friends),
            on.clock,
            on.utcOffset,
            on.unsaved,
        )

    private fun olegsGym(): Pair<FakeGym, Machine> {
        val on = signedInGym()
        on.friends.group("Зал на Лесной", owner = OLEG, ME)
        val olegPress =
            Machine
                .new("Жим ногами", OLEG.userId, t0)
                .copy(weightMode = WeightMode.PerSide, weightStep = 5.0)
        on.friends.machines += olegPress
        return on to olegPress
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun a_chosen_sort_reorders_the_machines_and_is_kept_in_the_profile() =
        runTest {
            val abs = Machine.new("Аб", null, t0)
            val row = Machine.new("Тяга", null, t0)
            gym.machines.upsert(abs)
            gym.machines.upsert(row)
            gym.sets.upsert(set(row, 50.0, 10))
            val vm = viewModel().also { it.load() }

            fun names() =
                vm.state.value.own
                    ?.map { it.name }
            assertEquals(listOf("Тяга", "Аб"), names())
            assertEquals(MachineSort.Recent, vm.state.value.sort)

            vm.chooseSort(MachineSort.Name)

            assertEquals(listOf("Аб", "Тяга"), names())
            assertEquals(MachineSort.Name, vm.state.value.sort)
            assertEquals(MachineSort.Name, gym.profiles.forOwner(null)?.machineSort)
        }

    @Test
    fun a_search_keeps_the_own_and_friends_machines_whose_name_matches() =
        runTest {
            val (on, olegPress) = olegsGym()
            on.machines.upsert(Machine.new("Тяга", ME.userId, t0))
            on.machines.upsert(Machine.new("Жим лёжа", ME.userId, t0))
            val vm = viewModel(on).also { it.load() }

            vm.onQueryChange("жим")

            assertEquals("жим", vm.state.value.query)
            assertEquals(
                listOf("Жим лёжа"),
                vm.state.value.own
                    ?.map { it.name },
            )
            assertEquals(
                listOf(olegPress.id),
                vm.state.value.friends
                    .map { it.id },
            )
            assertEquals(false, vm.state.value.nothingFound)

            vm.onQueryChange("гакк")

            assertEquals(emptyList(), vm.state.value.own)
            assertEquals(emptyList(), vm.state.value.friends)
            assertEquals(true, vm.state.value.nothingFound)
        }

    @Test
    fun chosen_tags_keep_the_machines_carrying_every_one_of_them() =
        runTest {
            val (on, olegPress) = olegsGym()
            on.friends.machines.replaceAll {
                if (it.id == olegPress.id) it.copy(tags = setOf("Ноги")) else it
            }
            on.machines.upsert(Machine.new("Тяга", ME.userId, t0).copy(tags = setOf("Спина")))
            on.machines.upsert(
                Machine.new("Присед", ME.userId, t0).copy(tags = setOf("Ноги", "База")),
            )
            val vm = viewModel(on).also { it.load() }
            assertEquals(
                listOf("База" to false, "Ноги" to false, "Спина" to false),
                vm.state.value.tags
                    .map { it.name to it.chosen },
            )

            vm.toggleTag("Ноги")

            assertEquals(
                listOf("Присед"),
                vm.state.value.own
                    ?.map { it.name },
            )
            assertEquals(
                listOf(olegPress.id),
                vm.state.value.friends
                    .map { it.id },
            )

            vm.toggleTag("База")

            assertEquals(
                listOf("Присед"),
                vm.state.value.own
                    ?.map { it.name },
            )
            assertEquals(emptyList(), vm.state.value.friends)
        }

    @Test
    fun a_managed_child_s_sort_survives_a_finished_sync() =
        runTest {
            val on = signedInGym().withChild(childAccount(SASHA, IVAN_SESSION))
            on.accounts.switchTo(SASHA.userId)
            on.machines.upsert(Machine.new("Тяга", SASHA.userId, t0))
            on.machines.upsert(Machine.new("Аб", SASHA.userId, t0))
            val vm = viewModel(on).also { it.load() }

            vm.chooseSort(MachineSort.Name)
            on.sync.completePass()

            assertEquals(MachineSort.Name, vm.state.value.sort)
            assertEquals(
                listOf("Аб", "Тяга"),
                vm.state.value.own
                    ?.map { it.name },
            )
        }

    @Test
    fun the_profile_s_sort_puts_the_most_used_machine_first() =
        runTest {
            gym.profiles.upsert(Profile.new(null, t0).copy(machineSort = MachineSort.Frequent))
            val abs = Machine.new("Аб", null, t0)
            val row = Machine.new("Тяга", null, t0)
            gym.machines.upsert(abs)
            gym.machines.upsert(row)
            gym.sets.upsert(set(row, 50.0, 10).copy(recordedAt = t0 + 5.minutes))
            gym.sets.upsert(set(abs, 50.0, 10))
            gym.sets.upsert(set(abs, 50.0, 10))

            val vm = viewModel().also { it.load() }

            assertEquals(
                listOf("Аб", "Тяга"),
                vm.state.value.own
                    ?.map { it.name },
            )
            assertEquals(MachineSort.Frequent, vm.state.value.sort)
        }

    @Test
    fun the_machines_are_listed_most_recent_first_with_their_tags_comment_last_use_and_record() =
        runTest {
            val row = Machine.new("Тяга", null, t0)
            gym.machines.upsert(row)
            val press =
                Machine
                    .new("Жим ногами", null, t0)
                    .copy(setupNote = "Сиденье на 4", tags = setOf("Ноги", "Жим"))
            gym.machines.upsert(press)
            gym.machines.upsert(Machine.new("Гакк", null, t0).copy(deleted = true))
            gym.sets.upsert(set(press, 80.0, 8))
            gym.sets.upsert(set(press, 80.0, 10))

            val vm = viewModel().also { it.load() }

            assertEquals(
                listOf(
                    MachineCardUi(
                        press.id,
                        "Жим ногами",
                        null,
                        listOf("Жим", "Ноги"),
                        "Сиденье на 4",
                        "14 ноября",
                        "80 кг × 10",
                    ),
                    MachineCardUi(row.id, "Тяга", null, emptyList(), "", null, null),
                ),
                vm.state.value.own,
            )
        }

    private fun set(
        machine: Machine,
        weight: Double,
        reps: Int,
    ) = WorkoutSet(
        WorkoutSetId.random(),
        machine.userId,
        VisitId.random(),
        machine.id,
        weight,
        reps,
        0,
        t0,
        t0,
        false,
    )

    @Test
    fun a_record_reads_in_the_unit_chosen_in_the_profile() =
        runTest {
            val cable = Machine.new("Кроссовер", null, t0).copy(unit = WeightUnit.Lb)
            gym.machines.upsert(cable)
            gym.sets.upsert(set(cable, 90.0, 8))
            val vm = viewModel().also { it.load() }

            assertEquals(
                listOf("41 кг × 8"),
                vm.state.value.own
                    ?.map { it.record },
            )

            gym.profiles.upsert(Profile.new(null, t0).copy(weightUnit = PreferredWeightUnit.Mixed))
            vm.load()

            assertEquals(
                listOf("90 lb × 8"),
                vm.state.value.own
                    ?.map { it.record },
            )
        }

    @Test
    fun an_own_machine_shows_its_own_first_photo() =
        runTest {
            val press = Machine.new("Жим ногами", null, t0)
            gym.machines.upsert(press)
            val photo = Photo.new(press.id, null, t0)
            gym.photos.add(photo, byteArrayOf(1))
            gym.photos.add(Photo.new(press.id, null, t0 + 1.minutes), byteArrayOf(2))

            val vm = viewModel().also { it.load() }

            assertEquals(
                listOf(photo),
                vm.state.value.own
                    ?.map { it.photo },
            )
        }

    @Test
    fun without_an_own_photo_a_linked_friend_s_photo_stands_for_the_machine() =
        runTest {
            val on = signedInGym()
            on.friends.group("Зал на Лесной", owner = OLEG, ME)
            val olegs = Machine.new("Жим ногами", OLEG.userId, t0)
            val (mine, link) = linkedCopy(olegs, ME.userId, t0)
            val olegsPhoto = Photo.new(olegs.id, OLEG.userId, t0)
            on.friends.machines += olegs
            on.friends.photos += olegsPhoto
            on.machines.upsert(mine)
            on.machineLinks.upsert(link)

            val vm = viewModel(on).also { it.load() }

            assertEquals(
                listOf(olegsPhoto),
                vm.state.value.own
                    ?.map { it.photo },
            )
        }

    @Test
    fun a_search_finds_an_own_machine_by_the_name_of_a_linked_friend_s_one() =
        runTest {
            val on = signedInGym()
            on.friends.group("Зал на Лесной", owner = OLEG, ME)
            val olegs = Machine.new("Гакк-машина", OLEG.userId, t0)
            val (copy, link) = linkedCopy(olegs, ME.userId, t0)
            val mine = copy.copy(name = "Присед в тренажёре")
            on.friends.machines += olegs
            on.machines.upsert(mine)
            on.machineLinks.upsert(link)
            val vm = viewModel(on).also { it.load() }

            vm.onQueryChange("гакк")

            val card =
                vm.state.value.own
                    ?.single()
            assertEquals(mine.id, card?.id)
            assertEquals(listOf(LinkedMachineUi(olegs.id, OLEG, "Гакк-машина")), card?.linkedWith)
        }

    @Test
    fun own_machines_of_the_same_name_suggest_each_other_and_the_chip_keeps_only_them() =
        runTest {
            val (on, _) = olegsGym()
            val press = Machine.new("Жим ногами", ME.userId, t0)
            val twin = Machine.new("жим ногами", ME.userId, t0)
            val row = Machine.new("Тяга", ME.userId, t0)
            listOf(press, twin, row).forEach { on.machines.upsert(it) }
            val vm = viewModel(on).also { it.load() }

            assertEquals(true, vm.state.value.hasSuggestions)
            assertEquals(
                mapOf(press.id to "жим ногами", twin.id to "Жим ногами", row.id to null),
                vm.state.value.own
                    ?.associate { it.id to it.suggestion },
            )
            assertEquals(1, vm.state.value.friendSections.size)

            vm.toggleSuggested()

            assertEquals(true, vm.state.value.onlySuggested)
            assertEquals(
                setOf(press.id, twin.id),
                vm.state.value.own
                    ?.map { it.id }
                    ?.toSet(),
            )
            assertEquals(emptyList(), vm.state.value.friendSections)
        }

    @Test
    fun without_suggestions_there_is_no_chip_and_nothing_is_kept_out() =
        runTest {
            val row = Machine.new("Тяга", null, t0)
            gym.machines.upsert(row)
            val vm = viewModel().also { it.load() }

            vm.toggleSuggested()

            assertEquals(false, vm.state.value.hasSuggestions)
            assertEquals(false, vm.state.value.onlySuggested)
            assertEquals(
                listOf(row.id),
                vm.state.value.own
                    ?.map { it.id },
            )
        }

    @Test
    fun a_card_names_the_own_machine_joined_through_a_friend_s_with_the_account() =
        runTest {
            val on = signedInGym()
            on.friends.group("Зал на Лесной", owner = OLEG, ME)
            val olegs = Machine.new("Гакк-машина", OLEG.userId, t0)
            val (first, firstLink) = linkedCopy(olegs, ME.userId, t0)
            val (second, secondLink) = linkedCopy(olegs, ME.userId, t0)
            on.friends.machines += olegs
            on.machines.upsert(first.copy(name = "Гакк"))
            on.machines.upsert(second.copy(name = "Присед"))
            on.machineLinks.upsert(firstLink)
            on.machineLinks.upsert(secondLink)
            val vm = viewModel(on).also { it.load() }

            val card =
                vm.state.value.own
                    ?.single { it.id == first.id }
            assertEquals(
                listOf(
                    LinkedMachineUi(second.id, ME, "Присед", own = true),
                    LinkedMachineUi(olegs.id, OLEG, "Гакк-машина"),
                ),
                card?.linkedWith,
            )
        }

    @Test
    fun a_friend_s_record_reads_in_the_viewer_s_unit() =
        runTest {
            val (on, olegPress) = olegsGym()
            on.friends.sets += set(olegPress, 40.0, 12)
            on.profiles.upsert(
                Profile.new(ME.userId, t0).copy(weightUnit = PreferredWeightUnit.Lb),
            )

            val vm = viewModel(on).also { it.load() }

            assertEquals(
                listOf("88 lb × 12"),
                vm.state.value.friends
                    .map { it.record },
            )
        }

    @Test
    fun a_switch_lists_the_other_account_s_machines() =
        runTest {
            gym.withAccounts(ivan, misha, active = ivan)
            gym.machines.upsert(Machine.new("Жим ногами", ivan.account.userId, t0))
            gym.machines.upsert(Machine.new("Тяга", misha.account.userId, t0))
            val vm = viewModel().also { it.load() }

            gym.accounts.switchTo(misha.account.userId)

            assertEquals(
                listOf("Тяга"),
                vm.state.value.own
                    ?.map { it.name },
            )
        }

    @Test
    fun a_finished_sync_shows_the_machines_it_pulled() =
        runTest {
            val vm = viewModel().also { it.load() }

            gym.machines.upsert(Machine.new("Тяга", null, t0))
            gym.sync.completePass()

            assertEquals(
                listOf("Тяга"),
                vm.state.value.own
                    ?.map { it.name },
            )
        }

    @Test
    fun own_machines_show_while_the_friends_read_is_still_in_flight() {
        val (on, _) = olegsGym()
        runBlocking { on.machines.upsert(Machine.new("Тяга", ME.userId, t0)) }
        on.friends.gate = CompletableDeferred()

        val vm = viewModel(on).also { it.load() }

        assertEquals(
            listOf("Тяга"),
            vm.state.value.own
                ?.map { it.name },
        )
        assertEquals(emptyList(), vm.state.value.friends)
    }

    @Test
    fun friends_machines_follow_with_their_owner_and_settings() {
        val (on, olegPress) = olegsGym()

        val vm = viewModel(on).also { it.load() }

        assertEquals(
            listOf(MachineCardUi(olegPress.id, "Жим ногами", null, emptyList(), "", null, null)),
            vm.state.value.friends,
        )
        val section =
            vm.state.value.friendSections
                .single()
        assertEquals(OLEG.userId, section.friend.userId)
        assertNotNull(section.color)
    }

    @Test
    fun a_friend_s_machine_linked_to_an_own_one_is_not_listed() =
        runTest {
            val (on, olegPress) = olegsGym()
            val mine = Machine.new("Жим", ME.userId, t0)
            on.machines.upsert(mine)
            on.friends.links +=
                MachineLink(MachineLinkId.random(), OLEG.userId, olegPress.id, mine.id, t0, false)

            val vm = viewModel(on).also { it.load() }

            assertEquals(emptyList(), vm.state.value.friends)
        }

    @Test
    fun a_machine_two_friends_linked_is_listed_once_as_the_original() {
        val on = signedInGym()
        on.friends.group("Зал на Лесной", owner = OLEG, ME, PASHA)
        val olegPress = Machine.new("Жим ногами", OLEG.userId, t0)
        val (pashaCopy, link) = linkedCopy(olegPress, PASHA.userId, t0)
        on.friends.machines += listOf(pashaCopy, olegPress)
        on.friends.links += link

        val vm = viewModel(on).also { it.load() }

        assertEquals(
            listOf(olegPress.id),
            vm.state.value.friends
                .map { it.id },
        )
    }

    @Test
    fun offline_the_friends_section_is_absent() {
        val (on, _) = olegsGym()
        runBlocking { on.machines.upsert(Machine.new("Тяга", ME.userId, t0)) }
        on.friends.offline = true

        val vm = viewModel(on).also { it.load() }

        assertEquals(
            listOf("Тяга"),
            vm.state.value.own
                ?.map { it.name },
        )
        assertEquals(emptyList(), vm.state.value.friends)
    }

    @Test
    fun without_an_account_no_friends_are_asked_for() {
        viewModel().load()

        assertEquals(0, gym.friends.reads)
    }

    @Test
    fun friends_read_for_the_previous_account_are_dropped_on_a_switch() =
        runTest {
            val on = FakeGym().withAccounts(IVAN_SESSION, misha, active = IVAN_SESSION)
            on.friends.group("Зал на Лесной", owner = OLEG, ME)
            on.friends.machines += Machine.new("Жим ногами", OLEG.userId, t0)
            val vm = viewModel(on).also { it.load() }
            assertEquals(1, vm.state.value.friends.size)

            on.friends.gate = CompletableDeferred()
            on.accounts.switchTo(misha.account.userId)

            assertEquals(emptyList(), vm.state.value.friends)
        }
}
