package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class MachineRankingTest {
    private val press = machine(PRESS, "Жим ногами")
    private val smith = machine(ROW, "Приседания в Смите")
    private val lastUsed =
        mapOf(
            ROW to Instant.fromEpochSeconds(10),
            PRESS to Instant.fromEpochSeconds(5),
        )

    @Test
    fun a_blank_query_lists_every_machine_most_recent_first_without_create() {
        val ranking = rankMachines("  ", listOf(press, smith), lastUsed)

        assertEquals(MachineRanking(offerCreate = false, machines = listOf(smith, press)), ranking)
    }

    @Test
    fun a_query_keeps_the_machines_whose_name_contains_it() {
        val ranking = rankMachines("жим", listOf(press, smith), lastUsed)

        assertEquals(MachineRanking(offerCreate = true, machines = listOf(press)), ranking)
    }

    @Test
    fun a_query_nothing_contains_lists_nothing() {
        val ranking = rankMachines("гакк", listOf(press, smith), lastUsed)

        assertEquals(MachineRanking(offerCreate = true, machines = emptyList()), ranking)
    }

    @Test
    fun tags_keep_the_machines_carrying_every_one_of_them() {
        val legs = press.copy(tags = setOf("Ноги", "Жим"))
        val back = smith.copy(tags = setOf("Ноги"))

        assertEquals(
            listOf(back, legs),
            rankMachines("", listOf(legs, back), lastUsed, setOf("Ноги")).machines,
        )
        assertEquals(
            listOf(legs),
            rankMachines("", listOf(legs, back), lastUsed, setOf("Ноги", "Жим")).machines,
        )
    }

    @Test
    fun an_existing_name_is_not_offered_for_creation() {
        assertEquals(false, rankMachines("жим НОГАМИ ", listOf(press), lastUsed).offerCreate)
    }

    @Test
    fun machines_never_used_follow_the_used_ones_by_name() {
        val abs = machine(MachineId("0f000000-0000-4000-8000-00000000000f"), "Аб")

        val ranking = rankMachines("", listOf(abs, press, smith), lastUsed)

        assertEquals(listOf(smith, press, abs), ranking.machines)
    }

    @Test
    fun deleted_machines_are_left_out() {
        val gone = press.copy(deleted = true)

        assertEquals(listOf(smith), rankMachines("", listOf(gone, smith), lastUsed).machines)
    }
}
