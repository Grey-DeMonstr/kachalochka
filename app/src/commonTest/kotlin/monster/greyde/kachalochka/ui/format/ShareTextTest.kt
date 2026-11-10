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
            .removePrefix("Thu\n\n")

    private val oneMachine = listOf(SharedMachine(machine("Row"), sets(50.0 to 8)))

    @Test
    fun the_header_names_the_nickname_and_the_weekday_then_a_blank_line() {
        assertEquals(
            "GDM, Thu\n\nRow 50kg 1x8",
            visitShareText("GDM", thursday, oneMachine, kg),
        )
    }

    @Test
    fun without_a_nickname_the_header_is_the_weekday_alone() {
        assertEquals("Thu\n\nRow 50kg 1x8", visitShareText("", thursday, oneMachine, kg))
        assertEquals("Thu\n\nRow 50kg 1x8", visitShareText("  ", thursday, oneMachine, kg))
    }

    @Test
    fun every_weekday_has_a_short_label() {
        val labels =
            (0L until 7L).map {
                visitShareText("", monday.plusDays(it), oneMachine, kg).substringBefore("\n")
            }

        assertEquals(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), labels)
    }

    @Test
    fun equal_weights_and_equal_reps_count_the_sets() {
        assertEquals(
            "Back extension 14kg 3x12",
            line(machine("Back extension"), 14.0 to 12, 14.0 to 12, 14.0 to 12),
        )
    }

    @Test
    fun differing_weights_are_listed_and_a_platform_outside_the_record_is_named() {
        assertEquals(
            "Leg press (+76kg) 20-20-30-40-40kg 5x10",
            line(
                machine("Leg press", platform = 76.0),
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
        val row = machine("Seated row", platform = 11.3).copy(weightMode = WeightMode.PerSide)

        assertEquals(
            "Seated row (+11.3kg) 15kg each side, 3x12",
            line(row, 15.0 to 12, 15.0 to 12, 15.0 to 12),
        )
        assertEquals("Seated row (+11.3kg) 3x12", line(row, 0.0 to 12, 0.0 to 12, 0.0 to 12))
    }

    @Test
    fun differing_reps_are_listed_after_one_weight() {
        assertEquals(
            "Seated crunch 41kg 10-15-15-15",
            line(machine("Seated crunch"), 41.0 to 10, 41.0 to 15, 41.0 to 15, 41.0 to 15),
        )
    }

    @Test
    fun differing_weights_and_reps_are_both_listed() {
        assertEquals(
            "Chest press 30° 35-35-30kg 10-10-15",
            line(machine("Chest press 30°"), 35.0 to 10, 35.0 to 10, 30.0 to 15),
        )
    }

    @Test
    fun pounds_read_as_the_nearest_half_kilogram() {
        assertEquals(
            "Row 20.5kg 2x10",
            line(
                machine("Row", WeightUnit.Lb),
                45.0 to 10,
                45.0 to 10,
            ),
        )
        assertEquals(
            "Leg press (+45.5kg) 20.5kg 1x10",
            line(machine("Leg press", WeightUnit.Lb, platform = 100.0), 45.0 to 10),
        )
    }

    @Test
    fun with_pounds_chosen_kilograms_read_as_the_nearest_half_pound() {
        assertEquals(
            "Leg press (+44lb) 99lb 2x10",
            line(
                machine("Leg press", platform = 20.0),
                45.0 to 10,
                45.0 to 10,
                preferred = PreferredWeightUnit.Lb,
            ),
        )
        assertEquals(
            "Row 45lb 1x10",
            line(machine("Row", WeightUnit.Lb), 45.0 to 10, preferred = PreferredWeightUnit.Lb),
        )
    }

    @Test
    fun with_mixed_units_every_machine_keeps_its_own() {
        val mixed = PreferredWeightUnit.Mixed
        assertEquals(
            "Row (+100lb) 45-50lb 10-8",
            line(
                machine("Row", WeightUnit.Lb, platform = 100.0),
                45.0 to 10,
                50.0 to 8,
                preferred = mixed,
            ),
        )
        assertEquals("Row 50kg 1x8", line(machine("Row"), 50.0 to 8, preferred = mixed))
    }

    @Test
    fun a_custom_unit_is_never_converted() {
        assertEquals(
            "Cable 3 plate 1x10",
            line(
                machine("Cable", WeightUnit.Custom, label = "plate"),
                3.0 to 10,
                preferred = PreferredWeightUnit.Lb,
            ),
        )
    }

    @Test
    fun a_custom_unit_is_written_as_is_after_a_space() {
        assertEquals(
            "Cable 3-4 plate 2x10",
            line(machine("Cable", WeightUnit.Custom, label = "plate"), 3.0 to 10, 4.0 to 10),
        )
        assertEquals(
            "Cable (+2 plate) 3 plate 1x10",
            line(machine("Cable", WeightUnit.Custom, platform = 2.0, label = "plate"), 3.0 to 10),
        )
    }

    @Test
    fun zero_weights_leave_the_weight_out() {
        assertEquals(
            "Pull-ups 3x10",
            line(machine("Pull-ups"), 0.0 to 10, 0.0 to 10, 0.0 to 10),
        )
        assertEquals(
            "Pull-ups 10-8-6",
            line(machine("Pull-ups"), 0.0 to 10, 0.0 to 8, 0.0 to 6),
        )
    }

    @Test
    fun a_single_set_counts_as_one() {
        assertEquals("Row 50kg 1x8", line(machine("Row"), 50.0 to 8))
    }

    @Test
    fun a_platform_included_in_the_record_is_not_named() {
        assertEquals(
            "Leg press 50kg 1x10",
            line(machine("Leg press", platform = 76.0, included = true), 50.0 to 10),
        )
    }

    @Test
    fun a_sets_summary_is_the_machine_s_shared_line_after_its_name() {
        val cases =
            listOf(
                machine("Leg press", platform = 76.0) to sets(20.0 to 10, 40.0 to 10),
                machine("Row", WeightUnit.Lb, platform = 100.0) to sets(45.0 to 10, 45.0 to 8),
                machine("Cable", WeightUnit.Custom, label = "plate") to sets(3.0 to 10),
                machine("Pull-ups") to sets(0.0 to 10, 0.0 to 10),
            )

        assertEquals(
            listOf("20-40kg 2x10", "20.5kg 10-8", "3 plate 1x10", "2x10"),
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
            listOf("20-40kg", "2x10"),
            setsSummaryParts(machine("Leg press"), sets(20.0 to 10, 40.0 to 10), kg),
        )
        assertEquals(
            listOf("3x10"),
            setsSummaryParts(machine("Pull-ups"), sets(0.0 to 10, 0.0 to 10, 0.0 to 10), kg),
        )
    }

    private fun List<WorkoutSet>.startingPyramidsAt(vararg indices: Int) =
        mapIndexed { index, set -> set.copy(pyramid = index in indices) }

    @Test
    fun pyramids_join_their_values_with_plus_and_equal_reps_are_counted() {
        val sets =
            sets(40.0 to 10, 31.0 to 10, 22.0 to 10, 36.0 to 10, 27.0 to 10, 18.0 to 10)
                .startingPyramidsAt(0, 3)

        assertEquals(
            "40+31+22 - 36+27+18kg 2x(10+10+10)",
            setsSummary(machine("Row"), sets, kg),
        )
    }

    @Test
    fun a_set_outside_a_pyramid_is_a_group_of_its_own() {
        val sets = sets(50.0 to 12, 40.0 to 10, 31.0 to 10, 22.0 to 10).startingPyramidsAt(1)

        assertEquals("50 - 40+31+22kg 12 - 10+10+10", setsSummary(machine("Press"), sets, kg))
    }

    @Test
    fun equal_pyramids_are_written_once_and_one_pyramid_is_not_counted() {
        val twice =
            sets(36.0 to 10, 27.0 to 10, 36.0 to 10, 27.0 to 10).startingPyramidsAt(0, 2)
        val once = sets(36.0 to 10, 27.0 to 8).startingPyramidsAt(0)

        assertEquals("36+27kg 2x(10+10)", setsSummary(machine("Press"), twice, kg))
        assertEquals("36+27kg 10+8", setsSummary(machine("Press"), once, kg))
    }

    @Test
    fun machines_follow_one_per_line_and_one_without_sets_is_skipped() {
        val text =
            visitShareText(
                "GDM",
                thursday,
                listOf(
                    SharedMachine(machine("Back extension"), sets(14.0 to 12)),
                    SharedMachine(machine("Empty"), emptyList()),
                    SharedMachine(machine("Seated crunch"), sets(41.0 to 10, 41.0 to 15)),
                ),
                kg,
            )

        assertEquals("GDM, Thu\n\nBack extension 14kg 1x12\nSeated crunch 41kg 10-15", text)
    }

    @Test
    fun a_gravitron_writes_its_weights_after_a_minus() {
        val gravitron =
            machine("Gravitron pull-ups").copy(weightMode = WeightMode.Counterweight)

        assertEquals(
            "Gravitron pull-ups (-)27-25-22.5-22.5-22.5kg 10-8-6-6-6",
            line(gravitron, 27.0 to 10, 25.0 to 8, 22.5 to 6, 22.5 to 6, 22.5 to 6),
        )
    }

    @Test
    fun a_gravitron_without_weights_shows_only_its_reps() {
        val gravitron = machine("Gravitron").copy(weightMode = WeightMode.Counterweight)

        assertEquals("Gravitron 2x10", line(gravitron, 0.0 to 10, 0.0 to 10))
    }

    @Test
    fun grouped_by_tag_the_text_splits_into_headed_sections_untagged_last_under_other() {
        val press = machine("Leg press", platform = 76.0).copy(tags = setOf("Legs"))
        val curl = machine("Biceps curl").copy(tags = setOf("Arms"))
        val abs = machine("Seated crunch")
        val dips = machine("Dips").copy(tags = setOf("arms"))
        val shared =
            listOf(
                SharedMachine(press, sets(20.0 to 10, 30.0 to 10)),
                SharedMachine(abs, sets(41.0 to 10)),
                SharedMachine(curl, sets(14.0 to 12)),
                SharedMachine(dips, sets(5.0 to 10)),
            )

        assertEquals(
            "GDM, Thu\n\nLEGS\nLeg press (+76kg) 20-30kg 2x10\n\nARMS\nBiceps curl 14kg 1x12\n" +
                "Dips 5kg 1x10\n\nOther\nSeated crunch 41kg 1x10",
            visitShareText("GDM", thursday, shared, kg, groupByTag = true),
        )
    }

    @Test
    fun grouping_without_any_tags_writes_the_plain_list() {
        val shared = listOf(SharedMachine(machine("Seated crunch"), sets(41.0 to 10)))

        assertEquals(
            "Thu\n\nSeated crunch 41kg 1x10",
            visitShareText("", thursday, shared, kg, groupByTag = true),
        )
    }
}
