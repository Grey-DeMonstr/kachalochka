package monster.greyde.kachalochka.ui.format

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.CalendarMonth
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.ui.strings.inEnglish
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class EnglishFormatsTest {
    private val t0 = Instant.fromEpochSeconds(1_700_000_000)
    private val kg = PreferredWeightUnit.Kg
    private val press = Machine.new("Leg press", null, t0).copy(platformWeight = 76.0)

    private fun sets(vararg weightReps: Pair<Double, Int>) =
        weightReps.mapIndexed { i, (weight, reps) ->
            WorkoutSet(
                WorkoutSetId.random(),
                null,
                VisitId.random(),
                press.id,
                weight,
                reps,
                i,
                t0,
                t0,
                false,
            )
        }

    @Test
    fun weights_counts_and_dates_read_in_english() =
        inEnglish {
            assertEquals("70 kg × 10", setValue(70.0, 10, press, kg))
            assertEquals("kg total · ±2.5", weightCaption(press, kg))
            assertEquals(
                "kg per side · ±2.5",
                weightCaption(press.copy(weightMode = WeightMode.PerSide), kg),
            )
            assertEquals("u.", unitLabel(WeightUnit.Custom, ""))
            assertEquals(
                listOf("1 set", "3 machines", "2 members"),
                listOf(setCount(1), machineCount(3), memberCount(2)),
            )
            assertEquals("yesterday", daysAgoLabel(1))
            assertEquals("12 November", dayMonthLabel(CalendarDay(2023, 11, 12), 2023))
            assertEquals("November 2023", monthTitle(CalendarMonth(2023, 11)))
            assertEquals("Tuesday", weekdayName(2))
            assertEquals("Mo", weekdayLabels().first())
            assertEquals(
                listOf("Save", "Add"),
                listOf(saveLabel(editing = true), saveLabel(editing = false)),
            )
        }

    @Test
    fun a_shared_visit_is_written_in_english() =
        inEnglish {
            val row =
                press.copy(
                    name = "Row",
                    platformWeight = 0.0,
                    weightMode = WeightMode.PerSide,
                )
            val shared =
                listOf(
                    SharedMachine(press, sets(20.0 to 10, 30.0 to 10)),
                    SharedMachine(
                        row,
                        sets(
                            15.0 to 12,
                        ),
                    ),
                )

            assertEquals(
                "Ivan, Thu\n\nLeg press (+76kg) 20-30kg 2x10\nRow 15kg each side, 1x12",
                visitShareText("Ivan", CalendarDay(2026, 10, 1), shared, kg),
            )
        }
}
