package monster.greyde.kachalochka.core.domain.friends

import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.random.Random

const val FRIEND_PALETTE_SIZE = 8

/** [existing] plus an index for every friend without one, drawn among the least-used ones. */
fun assignedColors(
    existing: Map<UserId, Int>,
    friends: List<UserId>,
    paletteSize: Int,
    random: Random,
): Map<UserId, Int> {
    val kept = existing.filterValues { it in 0 until paletteSize }
    val result = kept.toMutableMap()
    friends.distinct().filter { it !in kept }.forEach { friend ->
        val uses = (0 until paletteSize).associateWith { i -> result.values.count { it == i } }
        val least = uses.values.min()
        result[friend] =
            uses
                .filterValues { it == least }
                .keys
                .toList()
                .random(random)
    }
    return result
}
