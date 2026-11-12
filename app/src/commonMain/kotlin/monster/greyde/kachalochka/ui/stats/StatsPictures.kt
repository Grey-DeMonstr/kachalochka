package monster.greyde.kachalochka.ui.stats

import kotlinx.coroutines.flow.StateFlow
import monster.greyde.kachalochka.ui.settings.DeviceKey
import monster.greyde.kachalochka.ui.settings.DeviceSetting
import monster.greyde.kachalochka.ui.settings.DeviceStore

const val DEFAULT_PICTURE_ROWS = 15

/** The most rows on one picture of the statistics overview. */
class StatsPictureRows(
    private val store: DeviceStore,
) : DeviceSetting<Int>(store.read(KEY)?.takeIf { it >= 1 } ?: DEFAULT_PICTURE_ROWS) {
    val rows: StateFlow<Int> = state

    override suspend fun save(value: Int) = store.write(KEY, value)

    private companion object {
        val KEY = DeviceKey.Number("stats_picture_rows")
    }
}

/**
 * The rows of each picture. A section of more than [limit] rows splits into near-equal parts;
 * other sections join their neighbours while the rows fit.
 */
fun <T> pictureChunks(
    sections: List<List<T>>,
    limit: Int,
): List<List<T>> {
    val chunks = mutableListOf<List<T>>()
    var open = emptyList<T>()
    for (section in sections) {
        if (section.size > limit) {
            if (open.isNotEmpty()) chunks += open
            open = emptyList()
            chunks += nearEqualParts(section, limit)
        } else if (open.size + section.size <= limit) {
            open = open + section
        } else {
            if (open.isNotEmpty()) chunks += open
            open = section
        }
    }
    if (open.isNotEmpty()) chunks += open
    return chunks
}

private fun <T> nearEqualParts(
    rows: List<T>,
    limit: Int,
): List<List<T>> {
    val count = (rows.size + limit - 1) / limit
    val small = rows.size / count
    val bigger = rows.size % count
    var from = 0
    return List(count) { index ->
        val size = if (index < bigger) small + 1 else small
        rows.subList(from, from + size).also { from += size }
    }
}
