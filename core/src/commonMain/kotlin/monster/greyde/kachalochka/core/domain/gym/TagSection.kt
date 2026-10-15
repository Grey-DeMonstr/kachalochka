package monster.greyde.kachalochka.core.domain.gym

data class TagSection<T>(
    val tags: Set<String>,
    val items: List<T>,
)

/**
 * One section per distinct tag set, compared by [tagKey] and spelled as its first item, in the
 * order of that item; untagged items come last.
 */
fun <T> tagSections(
    items: List<T>,
    tagsOf: (T) -> Set<String>,
): List<TagSection<T>> {
    val (tagged, untagged) = items.partition { tagsOf(it).isNotEmpty() }
    val sections =
        tagged.groupBy { item -> tagsOf(item).map(::tagKey).toSet() }.values.map { sectionItems ->
            TagSection(tagsOf(sectionItems.first()), sectionItems)
        }
    return if (untagged.isEmpty()) sections else sections + TagSection(emptySet(), untagged)
}
