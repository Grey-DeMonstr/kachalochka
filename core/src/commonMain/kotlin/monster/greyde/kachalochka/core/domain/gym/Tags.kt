package monster.greyde.kachalochka.core.domain.gym

/**
 * What tags compare by and are stored as: tags that differ only in letter case or in spaces are
 * one tag. Migration 0027 makes the same key in SQL.
 */
fun tagKey(tag: String): String =
    buildString {
        var space = false
        tag.trim().forEach {
            if (it.isWhitespace()) {
                space = true
            } else {
                if (space) append(' ')
                space = false
                append(it)
            }
        }
    }.lowercase()

fun sameTag(
    a: String,
    b: String,
): Boolean = tagKey(a) == tagKey(b)

/** Each tag of [tags] once, in the spelling met first. */
fun distinctTags(tags: Iterable<String>): List<String> = tags.distinctBy(::tagKey)

/** [distinctTags] in alphabetical order, the order tags show in everywhere. */
fun sortedTags(tags: Iterable<String>): List<String> = distinctTags(tags).sortedBy(::tagKey)

/** [tag] in the spelling of [known] when [known] has it in another letter case. */
fun spelledAs(
    tag: String,
    known: Iterable<String>,
): String = known.firstOrNull { sameTag(it, tag) } ?: tag

/** [tags] taken over by an account that already spells some of them as in [known]. */
fun tagsSpelledAs(
    tags: Set<String>,
    known: Iterable<String>,
): Set<String> = distinctTags(tags.map { spelledAs(it, known) }).toSet()

/** The tags of the [linked] machines that [tags] lack, sorted and spelled as in [known]. */
fun suggestedTags(
    linked: Iterable<String>,
    tags: Iterable<String>,
    known: Iterable<String>,
): List<String> = sortedTags(linked.map { spelledAs(it, known) }).filterNot { hasTag(tags, it) }

fun hasTag(
    tags: Iterable<String>,
    tag: String,
): Boolean = tags.any { sameTag(it, tag) }

fun hasAllTags(
    tags: Iterable<String>,
    chosen: Iterable<String>,
): Boolean = chosen.all { hasTag(tags, it) }

/** [tags] without [tag] in any letter case when they hold it, else with it. */
fun toggledTag(
    tags: Set<String>,
    tag: String,
): Set<String> = if (hasTag(tags, tag)) tags.filterNot { sameTag(it, tag) }.toSet() else tags + tag
