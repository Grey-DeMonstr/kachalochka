package monster.greyde.kachalochka.core.data.gym

import kotlinx.serialization.json.Json
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachinePeaks
import monster.greyde.kachalochka.core.domain.gym.PhotoId
import monster.greyde.kachalochka.core.domain.gym.SetPeak
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class GymWireTest {
    @Test
    fun weight_modes_use_the_names_the_postgres_check_accepts() {
        assertEquals(
            listOf("total", "per_side", "counterweight"),
            WeightMode.entries.map { it.wireName() },
        )
        WeightMode.entries.forEach { assertEquals(it, weightModeOf(it.wireName())) }
    }

    @Test
    fun an_unknown_mode_reads_as_total() {
        assertEquals(WeightMode.Total, weightModeOf("hydraulic"))
    }

    @Test
    fun units_use_the_names_the_postgres_check_accepts() {
        assertEquals(listOf("kg", "lb", "custom"), WeightUnit.entries.map { it.wireName() })
        WeightUnit.entries.forEach { assertEquals(it, weightUnitOf(it.wireName())) }
    }

    @Test
    fun an_unknown_unit_reads_as_kilograms() {
        assertEquals(WeightUnit.Kg, weightUnitOf("stone"))
    }

    private val set =
        WorkoutSet(
            WorkoutSetId.random(),
            null,
            VisitId.random(),
            MachineId.random(),
            80.0,
            8,
            1,
            Instant.fromEpochSeconds(1_700_000_000),
            Instant.fromEpochSeconds(1_700_000_000),
            false,
            comment = "Тяжело",
        )

    @Test
    fun a_set_s_comment_travels_both_ways() {
        assertEquals(set, WorkoutSetRow.of(set).toWorkoutSet())
    }

    @Test
    fun an_emptied_comment_is_still_sent() {
        val json = Json.encodeToString(WorkoutSetRow.of(set.copy(comment = "")))

        assertTrue("\"comment\":\"\"" in json, json)
    }

    @Test
    fun a_machine_s_tags_travel_as_a_json_array() {
        val machine =
            Machine
                .new("Жим ногами", null, Instant.fromEpochSeconds(1_700_000_000))
                .copy(tags = setOf("Ноги", "Жим"))

        assertEquals("""["Ноги","Жим"]""", MachineRow.of(machine).tags)
        assertEquals(machine, MachineRow.of(machine).toMachine())
    }

    @Test
    fun a_machine_s_chosen_cover_travels_by_its_photo_id() {
        val cover = PhotoId.random()
        val machine =
            Machine
                .new("Жим ногами", null, Instant.fromEpochSeconds(1_700_000_000))
                .copy(coverPhoto = cover)

        assertEquals(cover.value, MachineRow.of(machine).coverPhoto)
        assertEquals(machine, MachineRow.of(machine).toMachine())
        val unreadable = MachineRow.of(machine).copy(coverPhoto = "nonsense")
        assertEquals(null, unreadable.toMachine().coverPhoto)
    }

    @Test
    fun a_machine_s_peaks_read_from_the_server_s_reduction() {
        val machine = MachineId.random()
        val row =
            Json.decodeFromString<MachinePeaksRow>(
                """{"machine_id":"${machine.value}","heaviest":80,"heaviest_reps":8,""" +
                    """"lightest":60.5,"lightest_reps":15,""" +
                    """"last_at":"2023-11-14T22:13:20+00:00","visits":3}""",
            )

        assertEquals(
            MachinePeaks(
                machine,
                SetPeak(80.0, 8),
                SetPeak(60.5, 15),
                Instant.fromEpochSeconds(1_700_000_000),
                3,
            ),
            row.toPeaks(),
        )
    }

    @Test
    fun unreadable_tags_read_as_none() {
        assertEquals(emptySet(), tagsOf("{"))
        assertEquals(setOf("Ноги"), tagsOf("""["Ноги", 3, " Ноги "]"""))
    }
}
