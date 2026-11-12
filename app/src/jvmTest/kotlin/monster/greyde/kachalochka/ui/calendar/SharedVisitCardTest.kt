package monster.greyde.kachalochka.ui.calendar

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.identity.Avatar
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import monster.greyde.kachalochka.ui.format.SharedVisit
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SharedVisitCardTest {
    private val visit = VisitId.random()
    private val day = CalendarDay(2026, 10, 29)
    private val card =
        VisitCardUi(
            tag = "card",
            person = PersonUi(null, "GDM", Avatar()),
            color = null,
            day = day,
            tags = listOf("LEGS"),
            exercises = (1..5).map { ExerciseUi("Machine $it", "18 kg × 15", null) },
            own =
                OwnVisitUi(
                    visit,
                    canRepeatToday = true,
                    date = "Thursday, 29 October",
                    shared = SharedVisit("GDM", day, emptyList(), PreferredWeightUnit.Kg),
                ),
        )

    @Test
    fun the_shared_card_shows_every_machine_and_the_date_without_the_controls() =
        runScreenTest(FakeGym(), screen = { SharedVisitCard(card) }) {
            onNodeWithTag("card-date").assertTextEquals("Thursday, 29 October").assertIsDisplayed()
            onNodeWithTag("card-machine-4").assertIsDisplayed()
            onNodeWithTag("card-more").assertDoesNotExist()
            onNodeWithTag("visit-menu-${visit.value}").assertDoesNotExist()
        }

    @Test
    fun the_shared_card_names_each_machine_as_the_shared_text_does() {
        val sled = ExerciseUi("Leg press", "80 kg × 8", null, sharedName = "Leg press (+20kg)")
        val shared = card.copy(exercises = listOf(sled))

        runScreenTest(FakeGym(), screen = { SharedVisitCard(shared) }) {
            onNodeWithText("Leg press (+20kg)").assertIsDisplayed()
        }
    }
}
