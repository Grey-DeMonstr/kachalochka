package monster.greyde.kachalochka.core.domain

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Guards technical spec §2 rule 1. */
class DomainPurityTest {
    private val allowed =
        listOf(
            "kotlin.",
            "monster.greyde.kachalochka.core.domain.",
        )

    @Test
    fun domain_imports_nothing_but_the_kotlin_standard_libraries() {
        val root = File("src/commonMain/kotlin/monster/greyde/kachalochka/core/domain")
        assertTrue(root.isDirectory, "no domain sources at ${root.absolutePath}")

        val sources = root.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(sources.isNotEmpty(), "no .kt files under ${root.absolutePath}")

        val offenders =
            sources.flatMap { source ->
                source
                    .readLines()
                    .mapNotNull { line -> line.removePrefixOrNull("import ") }
                    .filterNot { imported -> allowed.any(imported::startsWith) }
                    .map { imported -> "${source.name}: $imported" }
            }

        assertEquals(emptyList(), offenders)
    }
}

private fun String.removePrefixOrNull(prefix: String): String? =
    if (startsWith(prefix)) removePrefix(prefix) else null
