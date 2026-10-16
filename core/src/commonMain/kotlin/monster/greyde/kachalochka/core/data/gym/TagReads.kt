package monster.greyde.kachalochka.core.data.gym

import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId

/** The project's `max_rows`: a page this long may be cut, a shorter one is the last. */
internal const val TAG_PAGE: Long = 1000L

/**
 * Each machine's tags from the live `machine_tag` rows [links] picks and the `tag` rows [tags]
 * picks, both read in parallel; a group holds more of them than one response carries.
 */
internal suspend fun Postgrest.machineTags(
    links: PostgrestFilterBuilder.() -> Unit,
    tags: PostgrestFilterBuilder.() -> Unit,
): Map<MachineId, Set<String>> =
    coroutineScope {
        val linkRows =
            async {
                pagedRows<MachineTagRow>(MACHINE_TAG_TABLE) {
                    eq("deleted", false)
                    links()
                }
            }
        val tagRows = async { pagedRows<TagRow>(TAG_TABLE, tags) }
        tagsByMachine(
            linkRows.await().map { it.toMachineTag() },
            tagRows.await().map { it.toTag() },
        )
    }

internal fun List<Machine>.withTags(tags: Map<MachineId, Set<String>>): List<Machine> =
    map { it.copy(tags = tags[it.id].orEmpty()) }

internal suspend inline fun <reified R : Any> Postgrest.pagedRows(
    table: String,
    noinline match: PostgrestFilterBuilder.() -> Unit,
): List<R> {
    val rows = mutableListOf<R>()
    do {
        val page =
            from(table)
                .select {
                    filter(match)
                    order("id", Order.ASCENDING)
                    range(rows.size.toLong(), rows.size + TAG_PAGE - 1)
                }.decodeList<R>()
        rows += page
    } while (page.size.toLong() == TAG_PAGE)
    return rows
}
