package monster.greyde.kachalochka.core.domain.gym

/** What tags compare by: tags that differ only in letter case or outer spaces are one tag. */
fun tagKey(tag: String): String = tag.trim().lowercase()

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
