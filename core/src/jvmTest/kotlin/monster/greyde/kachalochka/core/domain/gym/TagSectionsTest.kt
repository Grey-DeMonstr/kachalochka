package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertEquals

class TagSectionsTest {
    private val tags =
        mapOf(
            "press" to setOf("Legs"),
            "curl" to setOf("Arms", "Biceps"),
            "abs" to emptySet(),
            "squat" to setOf("Legs"),
            "hammer" to setOf("Biceps", "Arms"),
            "row" to setOf("Back"),
        )

    private fun sections(vararg items: String) =
        tagSections(items.toList()) { tags.getValue(it) }.map { it.tags to it.items }

    @Test
    fun sections_follow_their_first_item_and_untagged_items_come_last() {
        assertEquals(
            listOf(
                setOf("Legs") to listOf("press", "squat"),
                setOf("Arms", "Biceps") to listOf("curl", "hammer"),
                setOf("Back") to listOf("row"),
                emptySet<String>() to listOf("abs"),
            ),
            sections("press", "abs", "curl", "squat", "hammer", "row"),
        )
    }

    @Test
    fun tag_sets_equal_without_letter_case_share_a_section_spelled_as_its_first_item() {
        val spelled = mapOf("press" to setOf("Legs", "Press"), "squat" to setOf("press", "LEGS"))

        assertEquals(
            listOf(setOf("Legs", "Press") to listOf("press", "squat")),
            tagSections(listOf("press", "squat")) { spelled.getValue(it) }
                .map { it.tags to it.items },
        )
    }

    @Test
    fun without_tags_everything_is_one_untagged_section() {
        assertEquals(listOf(emptySet<String>() to listOf("abs")), sections("abs"))
        assertEquals(emptyList(), sections())
    }
}
