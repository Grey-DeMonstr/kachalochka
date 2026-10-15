package monster.greyde.kachalochka.core.domain.gym

import kotlin.test.Test
import kotlin.test.assertEquals

class TagSectionsTest {
    private val tags =
        mapOf(
            "press" to setOf("Ноги"),
            "curl" to setOf("Руки", "Бицепс"),
            "abs" to emptySet(),
            "squat" to setOf("Ноги"),
            "hammer" to setOf("Бицепс", "Руки"),
            "row" to setOf("Спина"),
        )

    private fun sections(vararg items: String) =
        tagSections(items.toList()) { tags.getValue(it) }.map { it.tags to it.items }

    @Test
    fun sections_follow_their_first_item_and_untagged_items_come_last() {
        assertEquals(
            listOf(
                setOf("Ноги") to listOf("press", "squat"),
                setOf("Руки", "Бицепс") to listOf("curl", "hammer"),
                setOf("Спина") to listOf("row"),
                emptySet<String>() to listOf("abs"),
            ),
            sections("press", "abs", "curl", "squat", "hammer", "row"),
        )
    }

    @Test
    fun tag_sets_equal_without_letter_case_share_a_section_spelled_as_its_first_item() {
        val spelled = mapOf("press" to setOf("Ноги", "Жим"), "squat" to setOf("жим", "НОГИ"))

        assertEquals(
            listOf(setOf("Ноги", "Жим") to listOf("press", "squat")),
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
