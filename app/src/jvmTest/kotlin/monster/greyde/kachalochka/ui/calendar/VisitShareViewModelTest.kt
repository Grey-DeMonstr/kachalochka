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
    private val legs = Machine.new("Жим ногами", null, t0).copy(tags = setOf("Ноги"))
    private val row = Machine.new("Тяга", null, t0)
    private val visit = VisitId.random()

    private fun set(machine: Machine) =
        WorkoutSet(WorkoutSetId.random(), null, visit, machine.id, 50.0, 8, 0, t0, t0, false)

    private val card =
        VisitCardUi(
            tag = "card",
            person = PersonUi(null, "Я", Avatar()),
            color = null,
            day = thursday,
            tags = listOf("ноги"),
            exercises = listOf(ExerciseUi("Тяга", "50 кг × 8", null)),
            own =
                OwnVisitUi(
                    visit,
                    canRepeatToday = true,
                    date = "четверг, 29 октября",
                    shared =
                        SharedVisit(
                            "ГДМ",
                            thursday,
                            listOf(
                                SharedMachine(row, listOf(set(row))),
                                SharedMachine(legs, listOf(set(legs))),
                            ),
                            PreferredWeightUnit.Kg,
                        ),
                ),
        )

    private fun viewModel() =
        VisitShareViewModel(gym.texts, gym.cardShares, gym.shareGrouping, gym.accounts)

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

        assertEquals(listOf("ГДМ, чт\n\nТяга 50кг 1x8\nЖим ногами 50кг 1x8"), gym.texts.shared)
        assertNull(vm.state.value.offered)
        assertEquals(VisitShareNoticeUi(thursday, "Скопировано"), vm.state.value.notice)

        vm.dismissNotice()
        assertNull(vm.state.value.notice)
    }

    @Test
    fun grouping_is_kept_on_the_device_and_groups_the_text() =
        runTest {
            val vm = viewModel().also { it.offer(card) }

            vm.toggleGroupByTag()

            assertTrue(gym.shareGrouping.groupByTag.value)
            assertTrue(vm.state.value.groupByTag)
            vm.shareText()
            assertEquals(
                "ГДМ, чт\n\nНОГИ\nЖим ногами 50кг 1x8\n\nОстальное\nТяга 50кг 1x8",
                gym.texts.shared.single(),
            )
        }

    @Test
    fun the_grouping_kept_on_the_device_is_read_back() =
        runTest {
            gym.shareGrouping.set(true)

            assertTrue(viewModel().state.value.groupByTag)
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
            val ivan = session("11111111-1111-4111-8111-111111111111", "Иван")
            val misha = session("22222222-2222-4222-8222-222222222222", "Миша")
            val shared = FakeGym().withAccounts(ivan, misha, active = ivan)
            val vm =
                VisitShareViewModel(
                    shared.texts,
                    shared.cardShares,
                    shared.shareGrouping,
                    shared.accounts,
                )
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
