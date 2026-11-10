package monster.greyde.kachalochka.ui.measures

import monster.greyde.kachalochka.core.domain.measures.Measure
import monster.greyde.kachalochka.core.domain.measures.MeasureId
import monster.greyde.kachalochka.core.domain.measures.MeasureKind
import monster.greyde.kachalochka.ui.strings.RuStrings
import monster.greyde.kachalochka.ui.strings.inRussian
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class MeasureHintsTest {
    private val hips =
        Measure(
            MeasureId.random(),
            null,
            "hips",
            "",
            MeasureKind.Hips,
            3,
            Instant.DISTANT_PAST,
            false,
        )

    @Test
    fun a_predefined_measure_shows_the_app_s_name_and_unit_whatever_is_stored() {
        val stored = hips.copy(name = "Hip girth", unit = "in")

        assertEquals("Hips" to "cm", stored.shownName() to stored.shownUnit())
        inRussian {
            assertEquals(
                RuStrings.measureHips to RuStrings.cm,
                stored.shownName() to stored.shownUnit(),
            )
        }
    }

    @Test
    fun the_user_s_own_measure_shows_what_was_typed() {
        val own = hips.copy(kind = null, name = "Forearm", unit = "cm")

        assertEquals("Forearm" to "cm", own.shownName() to own.shownUnit())
    }
}
