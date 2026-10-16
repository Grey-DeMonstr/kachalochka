package monster.greyde.kachalochka.core.data.gym

import app.cash.sqldelight.db.AfterVersion
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.data.db.MachineTagQueries
import monster.greyde.kachalochka.core.data.db.TagQueries
import monster.greyde.kachalochka.core.data.db.kachalochkaDatabase
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.tagKey
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
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

/** Writes and enqueues the rows that give [machine] its tags; the caller holds a transaction. */
internal fun KachalochkaDatabase.writeTagsOf(machine: Machine) =
    withoutSuspending { SqlTagRows(this).writeTagsOf(machine) }

// SQLDelight's transaction takes a blocking body, and SqlTagRows never suspends.
private fun withoutSuspending(block: suspend () -> Unit) {
    var result: Result<Unit>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { result = it })
    checkNotNull(result) { "SqlTagRows suspended" }.getOrThrow()
}

/** The tag flow's rows in SQLite; the caller holds a transaction. */
internal class SqlTagRows(
    private val database: KachalochkaDatabase,
) : TagRowStore {
    override suspend fun linksOf(machine: Machine): List<MachineTag> =
        database.machineTagQueries.forMachine(machine.id.value, ::machineTagOf).executeAsList()

    override suspend fun tags(ids: List<String>): List<Tag> =
        database.tagQueries.byIds(ids, ::tagOf).executeAsList()

    override suspend fun heldElsewhere(
        machine: Machine,
        tags: List<String>,
    ): Set<String> =
        database.machineTagQueries
            .heldElsewhere(machine.id.value, tags)
            .executeAsList()
            .toSet()

    override suspend fun upsertTags(tags: List<Tag>) = tags.forEach { database.write(it) }

    override suspend fun upsertLinks(links: List<MachineTag>) =
        links.forEach { database.write(it) }
}

internal fun KachalochkaDatabase.write(tag: Tag) {
    tagQueries.write(tag)
    if (tag.userId != null) enqueue(TAG_TABLE, tag.id, tag.updatedAt)
}

internal fun KachalochkaDatabase.write(link: MachineTag) {
    machineTagQueries.write(link)
    if (link.userId != null) enqueue(MACHINE_TAG_TABLE, link.id, link.updatedAt)
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

// Another client's text is outside this one's control, and one unreadable entry must not stop the
// conversion or cost the tags that can be read.
internal fun tagsOf(text: String): Set<String> {
    val entries =
        try {
            Json.parseToJsonElement(text) as? JsonArray
        } catch (_: SerializationException) {
            null
        } ?: return emptySet()
    return entries
        .mapNotNull { (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content?.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
}

/**
 * Gives every live machine whose `tags` column holds names, which has no link at all and which
 * never reached the server (see `tagMirrors`) its live tag rows, with the ids migration 0027 gives
 * them. A machine that cannot be converted keeps no tags rather than keep the database from
 * opening.
 */
internal fun KachalochkaDatabase.convertTagMirrors(now: Instant) =
    transaction {
        machineQueries.tagMirrors().executeAsList().forEach { mirror ->
            try {
                convert(mirror.id, mirror.user_id, mirror.tags, now)
            } catch (_: Exception) {
            }
        }
    }

private fun KachalochkaDatabase.convert(
    id: String,
    userId: String?,
    names: String,
    now: Instant,
) {
    val owner = userId?.let(::UserId)
    val machine = MachineId(id)
    tagsOf(names).map(::tagKey).filter { it.isNotEmpty() }.distinct().forEach {
        val tag = Tag(tagId(owner, it), owner, it, now, deleted = false)
        val stored = tagQueries.byId(tag.id, ::tagOf).executeAsOneOrNull()
        if (stored == null || stored.deleted) write(tag)
        write(MachineTag(machineTagId(machine, it), owner, machine, tag.id, now, deleted = false))
    }
}

/** Runs the conversion right after `19.sqm` adds the tables, before anything reads them. */
fun tagConversion(clock: Clock): AfterVersion =
    AfterVersion(19) { driver -> kachalochkaDatabase(driver).convertTagMirrors(clock.now()) }
