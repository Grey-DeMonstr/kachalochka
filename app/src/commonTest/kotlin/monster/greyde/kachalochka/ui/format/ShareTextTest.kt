package monster.greyde.kachalochka.ui.format

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ShareTextTest {
    private val t0 = Instant.fromEpochSeconds(1_700_000_000)
    private val visitId = VisitId.random()
    private val monday = CalendarDay(2026, 9, 28)
    private val thursday = CalendarDay(2026, 10, 1)

    private fun machine(
        name: String,
        unit: WeightUnit = WeightUnit.Kg,
        platform: Double = 0.0,
        included: Boolean = false,
        label: String = "",
    ): Machine =
        Machine.new(name, null, t0).copy(
            unit = unit,
            platformWeight = platform,
            platformIncluded = included,
            unitLabel = label,
        )

    private fun sets(vararg weightReps: Pair<Double, Int>): List<WorkoutSet> =
        weightReps.mapIndexed { index, (weight, reps) ->
            WorkoutSet(
                WorkoutSetId.random(),
                null,
                visitId,
                Machine.new("", null, t0).id,
                weight,
                reps,
                index,
                t0,
                t0,
                false,
            )
        }

    private val kg = PreferredWeightUnit.Kg

    private fun line(
        machine: Machine,
        vararg weightReps: Pair<Double, Int>,
        preferred: PreferredWeightUnit = kg,
    ): String =
        visitShareText("", thursday, listOf(SharedMachine(machine, sets(*weightReps))), preferred)
            .removePrefix("чт\n\n")

    private val oneMachine = listOf(SharedMachine(machine("Тяга"), sets(50.0 to 8)))

    @Test
    fun the_header_names_the_nickname_and_the_weekday_then_a_blank_line() {
        assertEquals(
            "ГДМ, чт\n\nТяга 50кг 1x8",
            visitShareText("ГДМ", thursday, oneMachine, kg),
        )
    }

    @Test
    fun without_a_nickname_the_header_is_the_weekday_alone() {
        assertEquals("чт\n\nТяга 50кг 1x8", visitShareText("", thursday, oneMachine, kg))
        assertEquals("чт\n\nТяга 50кг 1x8", visitShareText("  ", thursday, oneMachine, kg))
    }

    @Test
    fun every_weekday_has_a_short_label() {
        val labels =
            (0L until 7L).map {
                visitShareText("", monday.plusDays(it), oneMachine, kg).substringBefore("\n")
            }

        assertEquals(listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс"), labels)
    }

    @Test
    fun equal_weights_and_equal_reps_count_the_sets() {
        assertEquals(
            "Гиперэкстензия 14кг 3x12",
            line(machine("Гиперэкстензия"), 14.0 to 12, 14.0 to 12, 14.0 to 12),
        )
    }

    @Test
    fun differing_weights_are_listed_and_a_platform_outside_the_record_is_named() {
        assertEquals(
            "Жим ногами (+76кг) 20-20-30-40-40кг 5x10",
            line(
                machine("Жим ногами", platform = 76.0),
                20.0 to 10,
                20.0 to 10,
                30.0 to 10,
                40.0 to 10,
                40.0 to 10,
            ),
        )
    }

    @Test
    fun a_per_side_weight_says_it_is_on_each_side() {
        val row = machine("Гребная тяга", platform = 11.3).copy(weightMode = WeightMode.PerSide)

        assertEquals(
            "Гребная тяга (+11.3кг) 15кг на каждую, 3x12",
            line(row, 15.0 to 12, 15.0 to 12, 15.0 to 12),
        )
        assertEquals("Гребная тяга (+11.3кг) 3x12", line(row, 0.0 to 12, 0.0 to 12, 0.0 to 12))
    }

    @Test
    fun differing_reps_are_listed_after_one_weight() {
        assertEquals(
            "Пресс сидя 41кг 10-15-15-15",
            line(machine("Пресс сидя"), 41.0 to 10, 41.0 to 15, 41.0 to 15, 41.0 to 15),
        )
    }

    @Test
    fun differing_weights_and_reps_are_both_listed() {
        assertEquals(
            "Жим от груди 30° 35-35-30кг 10-10-15",
            line(machine("Жим от груди 30°"), 35.0 to 10, 35.0 to 10, 30.0 to 15),
        )
    }

    @Test
    fun pounds_read_as_the_nearest_half_kilogram() {
        assertEquals(
            "Тяга 20.5кг 2x10",
            line(
                machine("Тяга", WeightUnit.Lb),
                45.0 to 10,
                45.0 to 10,
            ),
        )
        assertEquals(
            "Жим ногами (+45.5кг) 20.5кг 1x10",
            line(machine("Жим ногами", WeightUnit.Lb, platform = 100.0), 45.0 to 10),
        )
    }

    @Test
    fun with_pounds_chosen_kilograms_read_as_the_nearest_half_pound() {
        assertEquals(
            "Жим ногами (+44lb) 99lb 2x10",
            line(
                machine("Жим ногами", platform = 20.0),
                45.0 to 10,
                45.0 to 10,
                preferred = PreferredWeightUnit.Lb,
            ),
        )
        assertEquals(
            "Тяга 45lb 1x10",
            line(machine("Тяга", WeightUnit.Lb), 45.0 to 10, preferred = PreferredWeightUnit.Lb),
        )
    }

    @Test
    fun with_mixed_units_every_machine_keeps_its_own() {
        val mixed = PreferredWeightUnit.Mixed
        assertEquals(
            "Тяга (+100lb) 45-50lb 10-8",
            line(
                machine("Тяга", WeightUnit.Lb, platform = 100.0),
                45.0 to 10,
                50.0 to 8,
                preferred = mixed,
            ),
        )
        assertEquals("Тяга 50кг 1x8", line(machine("Тяга"), 50.0 to 8, preferred = mixed))
    }

    @Test
    fun a_custom_unit_is_never_converted() {
        assertEquals(
            "Блок 3 плитка 1x10",
            line(
                machine("Блок", WeightUnit.Custom, label = "плитка"),
                3.0 to 10,
                preferred = PreferredWeightUnit.Lb,
            ),
        )
    }

    @Test
    fun a_custom_unit_is_written_as_is_after_a_space() {
        assertEquals(
            "Блок 3-4 плитка 2x10",
            line(machine("Блок", WeightUnit.Custom, label = "плитка"), 3.0 to 10, 4.0 to 10),
        )
        assertEquals(
            "Блок (+2 плитка) 3 плитка 1x10",
            line(machine("Блок", WeightUnit.Custom, platform = 2.0, label = "плитка"), 3.0 to 10),
        )
    }

    @Test
    fun zero_weights_leave_the_weight_out() {
        assertEquals(
            "Подтягивания 3x10",
            line(machine("Подтягивания"), 0.0 to 10, 0.0 to 10, 0.0 to 10),
        )
        assertEquals(
            "Подтягивания 10-8-6",
            line(machine("Подтягивания"), 0.0 to 10, 0.0 to 8, 0.0 to 6),
        )
    }

    @Test
    fun a_single_set_counts_as_one() {
        assertEquals("Тяга 50кг 1x8", line(machine("Тяга"), 50.0 to 8))
    }

    @Test
    fun a_platform_included_in_the_record_is_not_named() {
        assertEquals(
            "Жим ногами 50кг 1x10",
            line(machine("Жим ногами", platform = 76.0, included = true), 50.0 to 10),
        )
    }

    @Test
    fun a_sets_summary_is_the_machine_s_shared_line_after_its_name() {
        val cases =
            listOf(
                machine("Жим ногами", platform = 76.0) to sets(20.0 to 10, 40.0 to 10),
                machine("Тяга", WeightUnit.Lb, platform = 100.0) to sets(45.0 to 10, 45.0 to 8),
                machine("Блок", WeightUnit.Custom, label = "плитка") to sets(3.0 to 10),
                machine("Подтягивания") to sets(0.0 to 10, 0.0 to 10),
            )

        assertEquals(
            listOf("20-40кг 2x10", "20.5кг 10-8", "3 плитка 1x10", "2x10"),
            cases.map { (machine, sets) -> setsSummary(machine, sets, kg) },
        )
        cases.forEach { (machine, sets) ->
            val line = visitShareText("", thursday, listOf(SharedMachine(machine, sets)), kg)
            assertTrue(line.endsWith(" " + setsSummary(machine, sets, kg)), line)
        }
    }

    @Test
    fun a_summary_splits_into_its_weights_and_its_reps() {
        assertEquals(
            listOf("20-40кг", "2x10"),
            setsSummaryParts(machine("Жим ногами"), sets(20.0 to 10, 40.0 to 10), kg),
        )
        assertEquals(
            listOf("3x10"),
            setsSummaryParts(machine("Подтягивания"), sets(0.0 to 10, 0.0 to 10, 0.0 to 10), kg),
        )
    }

    @Test
    fun machines_follow_one_per_line_and_one_without_sets_is_skipped() {
        val text =
            visitShareText(
                "ГДМ",
                thursday,
                listOf(
                    SharedMachine(machine("Гиперэкстензия"), sets(14.0 to 12)),
                    SharedMachine(machine("Пустой"), emptyList()),
                    SharedMachine(machine("Пресс сидя"), sets(41.0 to 10, 41.0 to 15)),
                ),
                kg,
            )

        assertEquals("ГДМ, чт\n\nГиперэкстензия 14кг 1x12\nПресс сидя 41кг 10-15", text)
    }

    @Test
    fun a_gravitron_writes_its_weights_after_a_minus() {
        val gravitron =
            machine("Подтягивания в гравитроне").copy(weightMode = WeightMode.Counterweight)

        assertEquals(
            "Подтягивания в гравитроне (-)27-25-22.5-22.5-22.5кг 10-8-6-6-6",
            line(gravitron, 27.0 to 10, 25.0 to 8, 22.5 to 6, 22.5 to 6, 22.5 to 6),
        )
    }

    @Test
    fun a_gravitron_without_weights_shows_only_its_reps() {
        val gravitron = machine("Гравитрон").copy(weightMode = WeightMode.Counterweight)

        assertEquals("Гравитрон 2x10", line(gravitron, 0.0 to 10, 0.0 to 10))
    }

    @Test
    fun grouped_by_tag_the_text_splits_into_headed_sections_untagged_last() {
        val press = machine("Жим ногами", platform = 76.0).copy(tags = setOf("Ноги"))
        val curl = machine("Бицепс").copy(tags = setOf("Руки"))
        val abs = machine("Пресс сидя")
        val dips = machine("Трицепс").copy(tags = setOf("Руки"))
        val shared =
            listOf(
                SharedMachine(press, sets(20.0 to 10, 30.0 to 10)),
                SharedMachine(abs, sets(41.0 to 10)),
                SharedMachine(curl, sets(14.0 to 12)),
                SharedMachine(dips, sets(5.0 to 10)),
            )

        assertEquals(
            "ГДМ, чт\n\nНоги\nЖим ногами (+76кг) 20-30кг 2x10\n\nРуки\nБицепс 14кг 1x12\n" +
                "Трицепс 5кг 1x10\n\nПресс сидя 41кг 1x10",
            visitShareText("ГДМ", thursday, shared, kg, groupByTag = true),
        )
    }

    @Test
    fun grouping_without_any_tags_writes_the_plain_list() {
        val shared = listOf(SharedMachine(machine("Пресс сидя"), sets(41.0 to 10)))

        assertEquals(
            "чт\n\nПресс сидя 41кг 1x10",
            visitShareText("", thursday, shared, kg, groupByTag = true),
        )
    }
}
