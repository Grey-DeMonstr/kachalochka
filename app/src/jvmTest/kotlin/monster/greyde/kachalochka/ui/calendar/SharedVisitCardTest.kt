package monster.greyde.kachalochka.ui.calendar

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.identity.Avatar
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SharedVisitCardTest {
    private val visit = VisitId.random()
    private val card =
        VisitCardUi(
            tag = "card",
            person = PersonUi(null, "ГДМ", Avatar()),
            color = null,
            day = CalendarDay(2026, 10, 29),
            tags = listOf("НОГИ"),
            exercises = (1..5).map { ExerciseUi("Упражнение $it", "18 кг × 15", null) },
            own = OwnVisitUi(visit, canRepeatToday = true, date = "четверг, 29 октября"),
        )

    @Test
    fun the_shared_card_shows_every_machine_and_the_date_without_the_controls() =
        runScreenTest(FakeGym(), screen = { SharedVisitCard(card) }) {
            onNodeWithTag("card-date").assertTextEquals("четверг, 29 октября").assertIsDisplayed()
            onNodeWithTag("card-machine-4").assertIsDisplayed()
            onNodeWithTag("card-more").assertDoesNotExist()
            onNodeWithTag("visit-menu-${visit.value}").assertDoesNotExist()
        }
}
