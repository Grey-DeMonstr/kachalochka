package monster.greyde.kachalochka.core.domain.gym

data class TagSection<T>(
    val tags: Set<String>,
    val items: List<T>,
)

/** One section per distinct tag set, in the order of its first item; untagged items come last. */
fun <T> tagSections(
    items: List<T>,
    tagsOf: (T) -> Set<String>,
): List<TagSection<T>> {
    val (tagged, untagged) = items.partition { tagsOf(it).isNotEmpty() }
    val sections =
        tagged.groupBy(tagsOf).map { (tags, sectionItems) ->
            TagSection(tags, sectionItems)
        }
    return if (untagged.isEmpty()) sections else sections + TagSection(emptySet(), untagged)
}
