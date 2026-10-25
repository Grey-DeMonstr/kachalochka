package monster.greyde.kachalochka.core.data.gym

import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import monster.greyde.kachalochka.core.data.supabase.pagedRows
import monster.greyde.kachalochka.core.domain.gym.Machine

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
        val tagRows = async { pagedRows<TagRow>(TAG_TABLE, match = tags) }
        TagRows(linkRows.await().map { it.toMachineTag() }, tagRows.await().map { it.toTag() })
    }
