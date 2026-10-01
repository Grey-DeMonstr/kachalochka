package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class MachineSortTest {
    private val abs = machine(MachineId("0f000000-0000-4000-8000-00000000000f"), "Аб")
    private val press = machine(PRESS, "Жим ногами")
    private val smith = machine(ROW, "Приседания в Смите")
    private val machines = listOf(press, abs, smith)

    private fun used(
        id: MachineId,
        atSeconds: Long,
        visits: Int,
    ) = MachinePeaks(
        id,
        SetPeak(0.0, 0),
        SetPeak(0.0, 0),
        Instant.fromEpochSeconds(atSeconds),
        visits,
    )

    private val peaks = listOf(used(PRESS, 5, 9), used(ROW, 10, 2)).associateBy { it.machineId }

    private fun sorted(sort: MachineSort) = machines.sortedWith(machineOrder(sort, peaks))

    @Test
    fun recent_puts_the_last_used_first_and_the_unused_last_by_name() {
        assertEquals(listOf(smith, press, abs), sorted(MachineSort.Recent))
    }

    @Test
    fun name_ignores_use() {
        assertEquals(listOf(abs, press, smith), sorted(MachineSort.Name))
    }

    @Test
    fun frequent_puts_the_machine_of_most_visits_first() {
        assertEquals(listOf(press, smith, abs), sorted(MachineSort.Frequent))
    }

    @Test
    fun equally_frequent_machines_follow_their_last_use() {
        val tied = listOf(used(PRESS, 5, 3), used(ROW, 10, 3)).associateBy { it.machineId }

        assertEquals(
            listOf(smith, press, abs),
            machines.sortedWith(machineOrder(MachineSort.Frequent, tied)),
        )
    }
}
