package monster.greyde.kachalochka.core.data.gym

import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import monster.greyde.kachalochka.core.domain.gym.Machine

/** The project's `max_rows`: a page this long may be cut, a shorter one is the last. */
internal const val TAG_PAGE: Long = 1000L

internal class TagRows(
    val links: List<MachineTag>,
    val tags: List<Tag>,
)

internal fun List<Machine>.withTagRows(rows: TagRows): List<Machine> =
    withTagRows(rows.links, rows.tags)

/**
 * The live `machine_tag` rows [links] picks and the `tag` rows [tags] picks, read in parallel; a
 * group holds more of them than one response carries.
 */
internal suspend fun Postgrest.tagRows(
    links: PostgrestFilterBuilder.() -> Unit,
    tags: PostgrestFilterBuilder.() -> Unit,
): TagRows =
    coroutineScope {
        val linkRows =
            async {
                pagedRows<MachineTagRow>(MACHINE_TAG_TABLE) {
                    eq("deleted", false)
                    links()
                }
            }
        val tagRows = async { pagedRows<TagRow>(TAG_TABLE, tags) }
        TagRows(linkRows.await().map { it.toMachineTag() }, tagRows.await().map { it.toTag() })
    }

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
