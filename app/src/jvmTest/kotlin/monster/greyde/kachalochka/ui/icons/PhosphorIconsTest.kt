package monster.greyde.kachalochka.ui.icons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhosphorIconsTest {
    @Test
    fun every_icon_is_drawn_on_the_phosphor_grid() {
        assertEquals(20, PhosphorIcons.all.size)
        PhosphorIcons.all.forEach { icon ->
            assertEquals(256f, icon.viewportWidth, icon.name)
            assertTrue(icon.root.size > 0, "${icon.name} has no paths")
        }
    }
}
