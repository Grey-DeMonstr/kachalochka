package monster.greyde.kachalochka.core.data.gym

import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.tagKey

/** Where a machine's tag rows live; [writeTagsOf] decides what each call reads and writes. */
internal interface TagRowStore {
    /** The stored links of [machine], live or deleted; links of another owner can be in it. */
    suspend fun linksOf(machine: Machine): List<MachineTag>

    /** The stored rows of [ids]. */
    suspend fun tags(ids: List<String>): List<Tag>

    /** Of [tags], those a live link from another live machine of [machine]'s owner holds. */
    suspend fun heldElsewhere(
        machine: Machine,
        tags: List<String>,
    ): Set<String>

    /** Writes [tags] as one batch. */
    suspend fun upsertTags(tags: List<Tag>)

    /** Writes [links] as one batch. */
    suspend fun upsertLinks(links: List<MachineTag>)
}

/**
 * Writes the rows that give [machine] its tags: live tags, then links, then the deletion of
 * unused tags, so a link never names a tag that is not live and a tag goes only after its link.
 */
internal suspend fun TagRowStore.writeTagsOf(machine: Machine) {
    val owner = machine.userId
    val stored = linksOf(machine)
    val named = (machine.tags.map { tagId(owner, it) } + stored.map { it.tagId }).distinct()
    val storedTags = if (named.isEmpty()) emptyList() else tags(named)
    val dropped = stored.filter { !it.deleted }.map { it.tagId }.distinct()
    val held = if (dropped.isEmpty()) emptySet() else heldElsewhere(machine, dropped)
    val writes = tagWrites(machine, stored, storedTags, held)
    if (writes.tags.isNotEmpty()) upsertTags(writes.tags)
    if (writes.links.isNotEmpty()) upsertLinks(writes.links)
    if (writes.unused.isNotEmpty()) upsertTags(writes.unused)
}

/**
 * The rows a machine's write changes, in the order the web writes them: a link never names a tag
 * that is not live yet, and a tag is deleted only once its last link is.
 */
internal class TagWrites(
    val tags: List<Tag>,
    val links: List<MachineTag>,
    val unused: List<Tag>,
)

/**
 * The rows that give [machine] its tags, from its [stored] links and the [storedTags] they and
 * its tags name. A tag no live link holds any more, here or in [heldElsewhere], is deleted; a
 * stored link of another owner is not the machine's.
 */
internal fun tagWrites(
    machine: Machine,
    stored: List<MachineTag>,
    storedTags: List<Tag>,
    heldElsewhere: Set<String>,
): TagWrites {
    val owner = machine.userId
    val now = machine.updatedAt
    val names =
        if (machine.deleted) emptySet() else machine.tags.map(::tagKey).filter { it.isNotEmpty() }
    val tagsById = storedTags.associateBy { it.id }
    val own = stored.filter { it.userId == owner }
    val linksById = own.associateBy { it.id }
    val wanted = names.distinct().map { Tag(tagId(owner, it), owner, it, now, false) }
    val liveTags =
        wanted.filter { tag ->
            tagsById[tag.id]?.let { it.deleted || it.name != tag.name || it.userId != owner }
                ?: true
        }
    val wantedLinks =
        wanted.map {
            MachineTag(machineTagId(machine.id, it.name), owner, machine.id, it.id, now, false)
        }
    val liveLinks =
        wantedLinks.filter { link ->
            linksById[link.id]?.let { it.deleted || it.tagId != link.tagId } ?: true
        }
    val wantedIds = wantedLinks.map { it.id }.toSet()
    val dropped =
        own
            .filter { !it.deleted && it.id !in wantedIds }
            .map { it.copy(updatedAt = now, deleted = true) }
    val kept = wanted.map { it.id }.toSet()
    val unused =
        dropped
            .map { it.tagId }
            .distinct()
            .filter { it !in kept && it !in heldElsewhere }
            .mapNotNull { tagsById[it] }
            .filter { !it.deleted }
            .map { it.copy(updatedAt = now, deleted = true) }
    return TagWrites(liveTags, liveLinks + dropped, unused)
}
