package monster.greyde.kachalochka.core.data.supabase

import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder

/** The project's `max_rows`: no response carries more rows. */
internal const val MAX_ROWS: Long = 1000L

/** Every row [match] picks, a page at a time: a page of [MAX_ROWS] may be cut, a shorter is last. */
internal suspend inline fun <reified R : Any> Postgrest.pagedRows(
    table: String,
    orderedBy: List<String> = listOf("id"),
    noinline match: PostgrestFilterBuilder.() -> Unit,
): List<R> {
    val rows = mutableListOf<R>()
    do {
        val page =
            from(table)
                .select {
                    filter(match)
                    orderedBy.forEach { order(it, Order.ASCENDING) }
                    range(rows.size.toLong(), rows.size + MAX_ROWS - 1)
                }.decodeList<R>()
        rows += page
    } while (page.size.toLong() == MAX_ROWS)
    return rows
}
