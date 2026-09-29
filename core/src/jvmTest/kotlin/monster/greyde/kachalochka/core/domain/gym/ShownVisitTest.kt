package monster.greyde.kachalochka.core.domain.gym

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail
import kotlin.time.Duration.Companion.hours

class ShownVisitTest {
    private val fourteenth = CalendarDay(2023, 11, 14)
    private val early = Visit(VISIT_A, null, fourteenth, T0 - 1.hours, T0, false)
    private val later = Visit(VISIT_B, null, fourteenth, T0, T0, false)
    private val earlySet = set(VISIT_A, 60.0, atSeconds = -3_500)
    private val laterSet = set(VISIT_B, 80.0, atSeconds = 60)

    private fun setsOf(vararg sets: WorkoutSet): suspend (Visit) -> List<WorkoutSet> =
        { visit -> sets.filter { it.visitId == visit.id } }

    @Test
    fun no_visit_on_the_day_shows_nothing() =
        runTest {
            assertNull(shownVisit(emptyList()) { fail("no sets to read") })
        }

    @Test
    fun the_only_visit_shows_with_its_sets_read_once() =
        runTest {
            var reads = 0
            val shown =
                shownVisit(listOf(early)) {
                    reads++
                    listOf(earlySet)
                }

            assertEquals(ShownVisit(early, listOf(earlySet)), shown)
            assertEquals(1, reads)
        }

    @Test
    fun an_earlier_visit_with_sets_shows_over_a_later_empty_one() =
        runTest {
            assertEquals(
                ShownVisit(early, listOf(earlySet)),
                shownVisit(listOf(later, early), setsOf(earlySet)),
            )
        }

    @Test
    fun of_two_visits_with_sets_the_later_shows() =
        runTest {
            assertEquals(
                ShownVisit(later, listOf(laterSet)),
                shownVisit(listOf(early, later), setsOf(earlySet, laterSet)),
            )
        }

    @Test
    fun of_two_empty_visits_the_later_shows_without_sets() =
        runTest {
            assertEquals(
                ShownVisit(later, emptyList()),
                shownVisit(listOf(early, later), setsOf()),
            )
        }
}
