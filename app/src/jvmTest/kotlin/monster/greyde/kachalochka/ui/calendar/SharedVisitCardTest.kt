package monster.greyde.kachalochka.ui.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.identity.Avatar
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import monster.greyde.kachalochka.ui.format.SharedVisit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        runScreenTest(FakeGym(), screen = { SharedVisitCard(card, "Thursday, 29 October") }) {
            onNodeWithTag("card-date").assertTextEquals("Thursday, 29 October").assertIsDisplayed()
            onNodeWithTag("card-machine-4").assertIsDisplayed()
            onNodeWithTag("card-more").assertDoesNotExist()
            onNodeWithTag("visit-menu-${visit.value}").assertDoesNotExist()
        }

    /** [card] as wide as in a shared picture. */
    @Composable
    private fun InPicture(
        card: VisitCardUi,
        caption: String,
    ) = Box(Modifier.width(368.dp)) { SharedVisitCard(card, caption) }

    @Test
    fun a_caption_that_fits_stays_beside_the_name() =
        runScreenTest(FakeGym(), screen = { InPicture(card, "past month") }) {
            val name = onNodeWithTag("card-name").fetchSemanticsNode().boundsInRoot
            val caption = onNodeWithTag("card-date").fetchSemanticsNode().boundsInRoot
            assertTrue(caption.top < name.bottom)
            assertTrue(caption.left > name.right)
        }

    @Test
    fun a_caption_that_does_not_fit_goes_under_the_name() {
        val long = card.copy(person = PersonUi(null, "A very long nickname of a person", Avatar()))

        runScreenTest(FakeGym(), screen = { InPicture(long, "Thursday, 29 October") }) {
            val name = onNodeWithTag("card-name").fetchSemanticsNode().boundsInRoot
            val caption = onNodeWithTag("card-date").fetchSemanticsNode().boundsInRoot
            assertTrue(caption.top >= name.bottom)
            assertEquals(name.left, caption.left)
        }
    }
}
