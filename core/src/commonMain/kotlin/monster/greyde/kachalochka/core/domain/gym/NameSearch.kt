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
    return closestPartDistance(name.lowercase(), needle) <= typosAllowed(needle.length)
}

private fun typosAllowed(length: Int): Int =
    when {
        length <= 3 -> 0
        length <= 5 -> 1
        length <= 8 -> 2
        else -> 3
    }

/** Levenshtein distance from [needle] to the closest substring of [text] (Sellers' algorithm). */
private fun closestPartDistance(
    text: String,
    needle: String,
): Int {
    // A row per needle letter; a match may start at any text position, so row 0 costs nothing.
    var previous = IntArray(text.length + 1)
    for (i in 1..needle.length) {
        val current = IntArray(text.length + 1)
        current[0] = i
        for (j in 1..text.length) {
            val substitution = previous[j - 1] + if (needle[i - 1] == text[j - 1]) 0 else 1
            current[j] = minOf(substitution, previous[j] + 1, current[j - 1] + 1)
        }
        previous = current
    }
    return previous.min()
}
