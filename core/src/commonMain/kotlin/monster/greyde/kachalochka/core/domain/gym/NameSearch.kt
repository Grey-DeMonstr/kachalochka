package monster.greyde.kachalochka.core.domain.gym

/**
 * True when some part of [name] is within a few typos of [query], in any letter case: none for
 * up to 3 letters, one for 4-5, two for 6-8 and three for longer queries.
 */
fun nameMatches(
    name: String,
    query: String,
): Boolean {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return true
    return closestPart(name, needle).distance <= typosAllowed(needle.length)
}

fun anyNameMatches(
    names: List<String>,
    query: String,
): Boolean = names.any { nameMatches(it, query) }

/** The indices of the part of [name] that [nameMatches] found for [query]; null for none. */
fun matchedPart(
    name: String,
    query: String,
): IntRange? {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return null
    val part = closestPart(name, needle)
    return part.range.takeIf { part.distance <= typosAllowed(needle.length) }
}

private fun typosAllowed(length: Int): Int =
    when {
        length <= 3 -> 0
        length <= 5 -> 1
        length <= 8 -> 2
        else -> 3
    }

private class ClosestPart(
    val distance: Int,
    val range: IntRange,
)

/**
 * The substring of [name] with the least Levenshtein distance to [needle] (Sellers' algorithm).
 * Of equal ones, the first to end on [needle]'s last letter wins, else the first to end.
 */
private fun closestPart(
    name: String,
    needle: String,
): ClosestPart {
    // Letter by letter, so the indices stay those of [name].
    val text = name.map { it.lowercaseChar() }
    // A row per needle letter; a match may start at any text position, so row 0 costs nothing.
    val rows = Array(needle.length + 1) { IntArray(text.size + 1) }
    for (i in 1..needle.length) {
        rows[i][0] = i
        for (j in 1..text.size) {
            val substitution = rows[i - 1][j - 1] + if (needle[i - 1] == text[j - 1]) 0 else 1
            rows[i][j] = minOf(substitution, rows[i - 1][j] + 1, rows[i][j - 1] + 1)
        }
    }
    val last = rows[needle.length]
    val least = last.min()
    val ends = last.indices.filter { last[it] == least }
    val end = ends.firstOrNull { it > 0 && text[it - 1] == needle.last() } ?: ends.first()
    var i = needle.length
    var j = end
    while (i > 0) {
        val cost = if (j > 0 && needle[i - 1] == text[j - 1]) 0 else 1
        when {
            j > 0 && rows[i][j] == rows[i - 1][j - 1] + cost -> {
                i--
                j--
            }
            rows[i][j] == rows[i - 1][j] + 1 -> i--
            else -> j--
        }
    }
    return ClosestPart(last[end], j until end)
}
