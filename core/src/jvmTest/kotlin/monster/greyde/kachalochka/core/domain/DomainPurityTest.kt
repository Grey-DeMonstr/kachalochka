package monster.greyde.kachalochka.core.domain

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Guards technical spec §2 rule 1, which no compiler setting expresses. */
class DomainPurityTest {
    private val banned =
        listOf(
            "monster.greyde.kachalochka.core.data",
            "io.github.jan.supabase",
            "app.cash.sqldelight",
            "org.koin",
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
                    .filter { line -> line.startsWith("import ") && banned.any(line::contains) }
                    .map { line -> "${source.name}: $line" }
            }

        assertEquals(emptyList(), offenders)
    }
}
