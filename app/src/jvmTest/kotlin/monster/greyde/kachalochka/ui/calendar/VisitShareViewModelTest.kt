package monster.greyde.kachalochka.ui.calendar

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.identity.Avatar
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.format.SharedMachine
import monster.greyde.kachalochka.ui.format.SharedVisit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VisitShareViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val thursday = CalendarDay(2026, 10, 29)
    private val legs = Machine.new("Leg press", null, t0).copy(tags = setOf("Legs"))
    private val row = Machine.new("Row", null, t0)
    private val visit = VisitId.random()

    private fun set(machine: Machine) =
        WorkoutSet(WorkoutSetId.random(), null, visit, machine.id, 50.0, 8, 0, t0, t0, false)

    private val card =
        VisitCardUi(
            tag = "card",
            person = PersonUi(null, "Me", Avatar()),
            color = null,
            day = thursday,
            tags = listOf("legs"),
            exercises = listOf(ExerciseUi("Row", "50 kg × 8", null)),
            own =
                OwnVisitUi(
                    visit,
                    canRepeatToday = true,
                    date = "Thursday, 29 October",
                    shared =
                        SharedVisit(
                            "GDM",
                            thursday,
                            listOf(
                                SharedMachine(row, listOf(set(row))),
                                SharedMachine(legs, listOf(set(legs))),
                            ),
                            PreferredWeightUnit.Kg,
                        ),
                ),
        )

    private fun viewModel(on: FakeGym = gym) =
        VisitShareViewModel(
            on.texts,
            on.cardShares,
            on.profiles,
            on.currentUser,
            on.deviceClock,
            on.sync,
            on.unsaved,
            on.accounts,
        )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun the_popup_opens_for_a_card_ungrouped_with_the_picture_where_the_platform_has_one() {
        val vm = viewModel()
        assertNull(vm.state.value.offered)

        vm.offer(card)

        val state = vm.state.value
        assertEquals(card, state.offered)
        assertFalse(state.groupByTag)
        assertTrue(state.canShareImage)
    }

    @Test
    fun a_visit_with_a_tagged_machine_offers_grouping() {
        val vm = viewModel().also { it.offer(card) }

        assertTrue(vm.state.value.canGroupByTag)
    }

    @Test
    fun a_visit_without_tags_offers_no_grouping() {
        val vm = viewModel().also { it.offer(card.copy(tags = emptyList())) }

        assertFalse(vm.state.value.canGroupByTag)
    }

    @Test
    fun a_platform_without_card_sharing_offers_no_picture() {
        gym.cardShares.available = false
        val vm = viewModel().also { it.offer(card) }

        assertFalse(vm.state.value.canShareImage)
    }

    @Test
    fun as_text_hands_the_visit_over_closes_the_popup_and_shows_the_notice_under_its_day() {
        val vm = viewModel().also { it.offer(card) }

        vm.shareText()

        assertEquals(listOf("GDM, Thu\n\nRow 50kg 1x8\nLeg press 50kg 1x8"), gym.texts.shared)
        assertNull(vm.state.value.offered)
        assertEquals(VisitShareNoticeUi(thursday, "Copied"), vm.state.value.notice)

        vm.dismissNotice()
        assertNull(vm.state.value.notice)
    }

    @Test
    fun grouping_is_kept_in_the_account_s_profile_and_groups_the_text() =
        runTest {
            val vm = viewModel().also { it.offer(card) }

            vm.toggleGroupByTag()

            assertEquals(true, gym.profiles.forOwner(null)?.groupByTag)
            assertTrue(vm.state.value.groupByTag)
            vm.shareText()
            assertEquals(
                "GDM, Thu\n\nLEGS\nLeg press 50kg 1x8\n\nOther\nRow 50kg 1x8",
                gym.texts.shared.single(),
            )
        }

    @Test
    fun the_popup_opens_with_the_account_s_grouping() =
        runTest {
            val vm = viewModel()
            gym.profiles.upsert(Profile.new(null, t0).copy(groupByTag = true))

            vm.offer(card)

            assertTrue(vm.state.value.groupByTag)
        }

    @Test
    fun as_image_closes_the_popup_and_draws_the_card_then_shares_its_picture() {
        val vm = viewModel().also { it.offer(card) }

        vm.shareImage()

        assertNull(vm.state.value.offered)
        assertEquals(card, vm.state.value.picture)

        val picture = ImageBitmap(4, 4)
        vm.pictureDrawn(picture)

        assertNull(vm.state.value.picture)
        assertEquals(listOf(picture), gym.cardShares.shared)
    }

    @Test
    fun cancelling_shares_nothing() {
        val vm = viewModel().also { it.offer(card) }

        vm.cancel()

        assertNull(vm.state.value.offered)
        assertTrue(gym.texts.shared.isEmpty())
    }

    @Test
    fun another_account_becoming_active_hides_the_notice() =
        runTest {
            val ivan = session("11111111-1111-4111-8111-111111111111", "Ivan")
            val misha = session("22222222-2222-4222-8222-222222222222", "Misha")
            val shared = FakeGym().withAccounts(ivan, misha, active = ivan)
            val vm = viewModel(shared)
            vm.offer(card)
            vm.shareText()

            shared.accounts.switchTo(misha.account.userId)

            assertNull(vm.state.value.notice)
        }

    private fun session(
        id: String,
        name: String,
    ) = AccountSession(Account(UserId(id), "$name@example.test", name), "access", "refresh", t0)
}
