package monster.greyde.kachalochka.core.data.gym

import app.cash.sqldelight.db.AfterVersion
import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.data.db.MachineTagQueries
import monster.greyde.kachalochka.core.data.db.TagQueries
import monster.greyde.kachalochka.core.data.db.kachalochkaDatabase
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.tagKey
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Clock
import kotlin.time.Instant

internal fun tagOf(
    id: String,
    userId: String?,
    name: String,
    updatedAt: Instant,
    deleted: Boolean,
) = Tag(id, userId?.let(::UserId), name, updatedAt, deleted)

internal fun machineTagOf(
    id: String,
    userId: String?,
    machineId: String,
    tagId: String,
    updatedAt: Instant,
    deleted: Boolean,
) = MachineTag(id, userId?.let(::UserId), MachineId(machineId), tagId, updatedAt, deleted)

internal fun TagQueries.write(tag: Tag) =
    upsert(tag.id, tag.userId?.value, tag.name, tag.updatedAt, tag.deleted)

internal fun MachineTagQueries.write(link: MachineTag) =
    upsert(
        link.id,
        link.userId?.value,
        link.machineId.value,
        link.tagId,
        link.updatedAt,
        link.deleted,
    )

/** Writes and enqueues the tag rows that give [machine] its tags; the caller holds a transaction. */
internal fun KachalochkaDatabase.writeTagsOf(machine: Machine) {
    val stored = machineTagQueries.forMachine(machine.id.value, ::machineTagOf).executeAsList()
    val named = machine.tags.map { tagId(machine.userId, it) } + stored.map { it.tagId }
    val storedTags = tagQueries.byIds(named.distinct(), ::tagOf).executeAsList()
    val dropped = stored.filter { !it.deleted }.map { it.tagId }.distinct()
    val heldElsewhere =
        if (dropped.isEmpty()) {
            emptySet()
        } else {
            machineTagQueries.heldElsewhere(machine.id.value, dropped).executeAsList().toSet()
        }
    write(tagWrites(machine, stored, storedTags, heldElsewhere))
}

internal fun KachalochkaDatabase.write(writes: TagWrites) {
    writes.tags.forEach {
        tagQueries.write(it)
        if (it.userId != null) enqueue(TAG_TABLE, it.id, it.updatedAt)
    }
    writes.links.forEach {
        machineTagQueries.write(it)
        if (it.userId != null) enqueue(MACHINE_TAG_TABLE, it.id, it.updatedAt)
    }
}

private fun KachalochkaDatabase.enqueue(
    table: String,
    id: String,
    at: Instant,
) = syncQueries.enqueue(table, id, at)

/** Each machine's tags, from its live links; a machine without one is left out. */
internal fun KachalochkaDatabase.machineTags(
    machines: List<MachineId>,
): Map<MachineId, Set<String>> =
    if (machines.isEmpty()) {
        emptyMap()
    } else {
        machineTagQueries
            .liveNamesOf(machines.map { it.value })
            .executeAsList()
            .groupBy({ MachineId(it.machine_id) }, { tagKey(it.name) })
            .mapValues { (_, names) -> names.toSet() }
    }

internal fun KachalochkaDatabase.ownerMachineTags(owner: UserId?): Map<MachineId, Set<String>> =
    machineTagQueries
        .liveNames(owner?.value)
        .executeAsList()
        .groupBy({ MachineId(it.machine_id) }, { tagKey(it.name) })
        .mapValues { (_, names) -> names.toSet() }

/**
 * Gives every machine whose `tags` column holds names and which has no link at all its tag rows,
 * with the ids migration 0027 gives them on the server. Live machines go first, so a tag that
 * only deleted machines hold is the one written deleted.
 */
internal fun KachalochkaDatabase.convertTagMirrors(now: Instant) =
    transaction {
        val mirrors =
            machineQueries.tagMirrors().executeAsList().sortedBy { it.deleted }
        mirrors.forEach { mirror ->
            val owner = mirror.user_id?.let(::UserId)
            val machine = MachineId(mirror.id)
            tagsOf(mirror.tags).map(::tagKey).filter { it.isNotEmpty() }.distinct().forEach {
                val tag = Tag(tagId(owner, it), owner, it, now, mirror.deleted)
                val stored = tagQueries.byId(tag.id, ::tagOf).executeAsOneOrNull()
                val revived = stored == null || (stored.deleted && !tag.deleted)
                val tags = if (revived) listOf(tag) else emptyList()
                val link =
                    MachineTag(
                        machineTagId(machine, it),
                        owner,
                        machine,
                        tag.id,
                        now,
                        mirror.deleted,
                    )
                write(TagWrites(tags, listOf(link)))
            }
        }
    }

/** Runs the conversion right after `19.sqm` adds the tables, before anything reads them. */
fun tagConversion(clock: Clock): AfterVersion =
    AfterVersion(19) { driver -> kachalochkaDatabase(driver).convertTagMirrors(clock.now()) }
