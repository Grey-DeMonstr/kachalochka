package monster.greyde.kachalochka.ui.machine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.Visit
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
import monster.greyde.kachalochka.ui.friends.IVAN_SESSION
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.PASHA
import monster.greyde.kachalochka.ui.friends.signedInGym
import monster.greyde.kachalochka.ui.strings.inEnglish
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MachinePickerViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val visit = Visit(VisitId.random(), null, gym.today, t0, t0, false)
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
        0,
        recordedAt,
        t0,
        false,
    )

    private fun viewModel(day: CalendarDay = gym.today) =
        MachinePickerViewModel(
            day,
            gym.machines,
            gym.sets,
            gym.visits,
            gym.currentUser,
            gym.accounts,
            gym.clock,
            gym.utcOffset,
            gym.sync,
            gym.friends,
            gym.machineLinks,
            gym.profiles,
            gym.photos,
        )

    private fun pickerOn(on: FakeGym) =
        MachinePickerViewModel(
            on.today,
            on.machines,
            on.sets,
            on.visits,
            on.currentUser,
            on.accounts,
            on.clock,
            on.utcOffset,
            on.sync,
            on.friends,
            on.machineLinks,
            on.profiles,
            on.photos,
        )

    @Test
    fun a_machine_without_its_own_photo_shows_a_linked_friend_s_one() =
        runTest {
            val on = signedInGym()
            on.friends.group("Зал на Лесной", owner = OLEG, ME)
            val olegs = Machine.new("Жим ногами", OLEG.userId, on.clock.current)
            val (mine, link) = linkedCopy(olegs, ME.userId, on.clock.current)
            val olegsPhoto = Photo.new(olegs.id, OLEG.userId, on.clock.current)
            on.friends.machines += olegs
            on.friends.photos += olegsPhoto
            on.machines.upsert(mine)
            on.machineLinks.upsert(link)

            val vm = pickerOn(on).also { it.load() }

            assertEquals(
                listOf(olegsPhoto),
                vm.state.value.rows
                    .map { it.photo },
            )
        }

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

    /** Oleg's original machine and Pasha's copy of it, linked to the same physical machine. */
    private fun sharedLinkGym(): Pair<FakeGym, Machine> {
        val on = signedInGym()
        on.friends.group("Зал на Лесной", owner = OLEG, ME, PASHA)
        val olegPress = Machine.new("Жим ногами", OLEG.userId, t0)
        val (pashaCopy, link) = linkedCopy(olegPress, PASHA.userId, t0)
        on.friends.machines += listOf(pashaCopy, olegPress)
        on.friends.links += link
        return on to olegPress
    }

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
    fun a_finished_sync_lists_the_machines_it_pulled() {
        val vm = viewModel().also { it.load() }
        val pulled = Machine.new("Бабочка", null, t0)

        runBlocking { gym.machines.upsert(pulled) }
        gym.sync.completePass()

        assertTrue(
            pulled.id in
                vm.state.value.rows
                    .map { it.id },
        )
    }

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
    fun a_row_s_detail_names_a_custom_unit() =
        runTest {
            val gravitron =
                Machine
                    .new("Гравитрон", null, t0)
                    .copy(unit = WeightUnit.Custom, unitLabel = "плитка")
            gym.machines.upsert(gravitron)
            gym.sets.upsert(set(otherVisit, gravitron, 7.0, 10, t0 - 1.days))
            val vm = viewModel().also { it.load() }

            assertEquals(
                "Было 7 плитка × 10 · вчера",
                vm.state.value.rows
                    .single { it.id == gravitron.id }
                    .detail,
            )
        }

    @Test
    fun a_last_result_reads_in_the_unit_chosen_in_the_profile() =
        runTest {
            val cable = Machine.new("Кроссовер", null, t0).copy(unit = WeightUnit.Lb)
            gym.machines.upsert(cable)
            gym.sets.upsert(set(otherVisit, cable, 90.0, 8, t0 - 1.days))
            val vm = viewModel().also { it.load() }

            fun details() =
                vm.state.value.rows
                    .associate { it.name to it.detail }
            assertEquals("Было 41 кг × 8 · вчера", details()["Кроссовер"])

            gym.profiles.upsert(Profile.new(null, t0).copy(weightUnit = PreferredWeightUnit.Lb))
            vm.load()

            assertEquals("Было 90 lb × 8 · вчера", details()["Кроссовер"])
            assertEquals("Было 176.5 lb × 8 · 4 дня назад", details()["Приседания в Смите"])
        }

    @Test
    fun a_visit_no_client_has_dated_counts_on_the_day_it_was_recorded_on() =
        runTest {
            val tenth = CalendarDay(2023, 11, 10)
            val undated = Visit(VisitId.random(), null, null, t0 - 4.days, t0, false)
            gym.visits.upsert(undated)
            gym.sets.upsert(set(undated.id, smith, 80.0, 8, t0 - 4.days))

            val vm = viewModel(tenth).also { it.load() }

            assertEquals(
                "1 подход в этом визите",
                vm.state.value.rows
                    .single { it.id == smith.id }
                    .detail,
            )
        }

    @Test
    fun another_day_counts_the_sets_of_its_own_visit() =
        runTest {
            val tenth = CalendarDay(2023, 11, 10)
            val past = Visit(VisitId.random(), null, tenth, t0 - 4.days, t0, false)
            gym.visits.upsert(past)
            repeat(2) { gym.sets.upsert(set(past.id, smith, 80.0, 8, t0 - 4.days + it.minutes)) }

            val vm = viewModel(tenth).also { it.load() }

            assertEquals(
                "2 подхода в этом визите",
                vm.state.value.rows
                    .single { it.id == smith.id }
                    .detail,
            )
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
    fun the_picker_speaks_english_when_entered_again() {
        val vm = viewModel().also { it.load() }
        vm.onQueryChange("гакк")

        inEnglish {
            vm.load()
            assertEquals("Create \"гакк\"", vm.state.value.createLabel)
            assertEquals("Similar", vm.state.value.sectionLabel)
        }
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
            val hers = Visit(VisitId.random(), misha.account.userId, shared.today, t0, t0, false)
            val his = Visit(VisitId.random(), ivan.account.userId, shared.today, t0, t0, false)
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
                    shared.today,
                    shared.machines,
                    shared.sets,
                    shared.visits,
                    shared.currentUser,
                    shared.accounts,
                    shared.clock,
                    shared.utcOffset,
                    shared.sync,
                    shared.friends,
                    shared.machineLinks,
                    shared.profiles,
                    shared.photos,
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

    @Test
    fun friends_machines_are_offered_with_their_owner_and_settings() {
        val (on, olegPress) = olegsGym()

        val vm = pickerOn(on).also { it.load() }

        assertEquals(
            listOf(PickerRowUi(olegPress.id, "Жим ногами", "Олег · кг на сторону · ±5")),
            vm.state.value.friendRows,
        )
    }

    @Test
    fun picking_a_friend_s_machine_saves_a_linked_copy_and_hides_the_friend_s_row() =
        runTest {
            val (on, olegPress) = olegsGym()
            val vm = pickerOn(on).also { it.load() }
            var picked: MachineId? = null

            vm.pickFriend(olegPress.id) { picked = it }

            val copy = assertNotNull(on.machines.byId(assertNotNull(picked)))
            assertEquals(ME.userId, copy.userId)
            assertEquals(5.0, copy.weightStep)
            val link =
                on.machineLinks.rows.values
                    .single()
            assertEquals(
                Triple(ME.userId, copy.id, olegPress.id),
                Triple(link.userId, link.machineId, link.linkedMachineId),
            )
            assertEquals(1, on.sync.requests)
            vm.load()
            assertEquals(emptyList(), vm.state.value.friendRows)
        }

    @Test
    fun a_friend_s_machine_linked_to_an_own_one_is_not_offered() =
        runTest {
            val (on, olegPress) = olegsGym()
            val mine = Machine.new("Жим", ME.userId, t0)
            on.machines.upsert(mine)
            on.friends.links +=
                MachineLink(MachineLinkId.random(), OLEG.userId, olegPress.id, mine.id, t0, false)

            val vm = pickerOn(on).also { it.load() }

            assertEquals(emptyList(), vm.state.value.friendRows)
        }

    @Test
    fun the_search_narrows_friends_machines_too() {
        val (on, _) = olegsGym()
        val vm = pickerOn(on).also { it.load() }

        vm.onQueryChange("тяга")

        assertEquals(emptyList(), vm.state.value.friendRows)
    }

    @Test
    fun offline_the_friends_section_is_absent() {
        val (on, _) = olegsGym()
        on.friends.offline = true

        val vm = pickerOn(on).also { it.load() }

        assertEquals(emptyList(), vm.state.value.friendRows)
    }

    @Test
    fun own_machines_show_while_the_friends_read_is_still_in_flight() {
        val (on, _) = olegsGym()
        val press = Machine.new("Жим ногами", ME.userId, t0)
        runBlocking { on.machines.upsert(press) }
        on.friends.gate = CompletableDeferred()

        val vm = pickerOn(on).also { it.load() }

        assertEquals(
            listOf("Жим ногами"),
            vm.state.value.rows
                .map { it.name },
        )
    }

    @Test
    fun friends_read_for_the_previous_account_never_show_or_get_picked() =
        runTest {
            val on = FakeGym().withAccounts(IVAN_SESSION, misha, active = IVAN_SESSION)
            on.friends.group("Зал на Лесной", owner = OLEG, ME)
            val olegPress = Machine.new("Жим ногами", OLEG.userId, t0)
            on.friends.machines += olegPress
            val ivansRead = CompletableDeferred<Unit>()
            on.friends.gate = ivansRead
            val vm = pickerOn(on).also { it.load() }

            on.friends.gate = CompletableDeferred()
            on.accounts.switchTo(misha.account.userId)
            ivansRead.complete(Unit)
            vm.pickFriend(olegPress.id) {}

            assertEquals(emptyList(), vm.state.value.friendRows)
            assertEquals(emptyList(), on.machines.all(misha.account.userId))
        }

    @Test
    fun without_an_account_no_friends_are_asked_for() {
        viewModel().load()

        assertEquals(0, gym.friends.reads)
    }

    @Test
    fun a_machine_two_friends_linked_is_offered_once_as_the_original() {
        val (on, olegPress) = sharedLinkGym()

        val vm = pickerOn(on).also { it.load() }

        assertEquals(
            listOf(PickerRowUi(olegPress.id, "Жим ногами", "Олег · кг всего · ±2.5")),
            vm.state.value.friendRows,
        )
    }

    @Test
    fun picking_the_shared_row_links_to_the_original() =
        runTest {
            val (on, olegPress) = sharedLinkGym()
            val vm = pickerOn(on).also { it.load() }
            var picked: MachineId? = null

            vm.pickFriend(olegPress.id) { picked = it }

            val link =
                on.machineLinks.rows.values
                    .single()
            assertEquals(
                assertNotNull(picked) to olegPress.id,
                link.machineId to link.linkedMachineId,
            )
        }
}
