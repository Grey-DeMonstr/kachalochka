package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertEquals

class VisitSummaryTest {
    private val rowFirst = set(VISIT_B, 45.0, atSeconds = 1, machine = ROW)
    private val press1 = set(VISIT_B, 60.0, atSeconds = 2)
    private val row2 = set(VISIT_B, 45.0, atSeconds = 3, machine = ROW)
    private val press2 = set(VISIT_B, 70.0, atSeconds = 4)

    @Test
    fun sets_group_by_machine_in_order_of_first_use() {
        val groups = groupByMachine(listOf(press2, row2, press1, rowFirst))

        assertEquals(
            listOf(
                MachineSets(ROW, listOf(rowFirst, row2)),
                MachineSets(PRESS, listOf(press1, press2)),
            ),
            groups,
        )
    }

    @Test
    fun the_summary_counts_machines_and_sets_and_names_the_last_set() {
        val summary = summarize(listOf(rowFirst, press1, row2, press2))

        assertEquals(VisitSummary(machineCount = 2, setCount = 4, lastSet = press2), summary)
    }

    @Test
    fun an_empty_visit_summarises_to_zeroes() {
        assertEquals(VisitSummary(0, 0, null), summarize(emptyList()))
    }
}
