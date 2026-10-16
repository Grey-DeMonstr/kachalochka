package monster.greyde.kachalochka.core.data.gym

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.identity.UserId

/**
 * A machine's tags are written after it, tags before their links, as the sync pass pushes them;
 * the ids follow from the content, so a write retried after a failure writes the same rows.
 */
class RemoteMachineRepository(
    private val client: SupabaseClient,
) : MachineRepository {
    override suspend fun upsert(machine: Machine) {
        val postgrest = client.postgrest
        postgrest.from(MACHINE_TABLE).upsert(MachineRow.of(machine))
        val stored =
            postgrest
                .pagedRows<MachineTagRow>(MACHINE_TAG_TABLE) { eq("machine_id", machine.id.value) }
                .map { it.toMachineTag() }
        val named =
            (machine.tags.map { tagId(machine.userId, it) } + stored.map { it.tagId }).distinct()
        val storedTags =
            if (named.isEmpty()) {
                emptyList()
            } else {
                postgrest
                    .pagedRows<TagRow>(TAG_TABLE) { isIn("id", named) }
                    .map { it.toTag() }
            }
        val dropped = stored.filter { !it.deleted }.map { it.tagId }.distinct()
        val heldElsewhere =
            if (dropped.isEmpty()) {
                emptySet()
            } else {
                postgrest
                    .pagedRows<MachineTagRow>(MACHINE_TAG_TABLE) {
                        eq("deleted", false)
                        neq("machine_id", machine.id.value)
                        isIn("tag_id", dropped)
                    }.map { it.tagId }
                    .toSet()
            }
        val writes = tagWrites(machine, stored, storedTags, heldElsewhere)
        if (writes.tags.isNotEmpty()) {
            postgrest.from(TAG_TABLE).upsert(writes.tags.map(TagRow::of))
        }
        if (writes.links.isNotEmpty()) {
            postgrest.from(MACHINE_TAG_TABLE).upsert(writes.links.map(MachineTagRow::of))
        }
    }

    override suspend fun byId(id: MachineId): Machine? =
        coroutineScope {
            val machine =
                async {
                    client.postgrest
                        .from(MACHINE_TABLE)
                        .select { filter { eq("id", id.value) } }
                        .decodeSingleOrNull<MachineRow>()
                        ?.toMachine()
                }
            val tags = async { tagsOf(id) }
            machine.await()?.let { listOf(it).withTags(tags.await()).single() }
        }

    override suspend fun all(owner: UserId?): List<Machine> =
        coroutineScope {
            val machines =
                async {
                    client.postgrest
                        .from(MACHINE_TABLE)
                        .select {
                            filter {
                                eq("deleted", false)
                                owned(owner)
                            }
                        }.decodeList<MachineRow>()
                        .map { it.toMachine() }
                }
            val tags = async { client.postgrest.machineTags({ owned(owner) }, { owned(owner) }) }
            machines.await().withTags(tags.await()).sortedBy { it.name.lowercase() }
        }

    override suspend fun named(
        owner: UserId?,
        name: String,
    ): Machine? {
        val machine =
            client.postgrest
                .from(MACHINE_TABLE)
                .select {
                    filter {
                        eq("deleted", false)
                        eq("name", name)
                        owned(owner)
                    }
                }.decodeList<MachineRow>()
                .firstOrNull()
                ?.toMachine() ?: return null
        return listOf(machine).withTags(tagsOf(machine.id)).single()
    }

    // A friend's machine reads its tags too: row-level security shows a group mate's live tags.
    private suspend fun tagsOf(machine: MachineId): Map<MachineId, Set<String>> {
        val links =
            client.postgrest
                .pagedRows<MachineTagRow>(MACHINE_TAG_TABLE) {
                    eq("machine_id", machine.value)
                    eq("deleted", false)
                }.map { it.toMachineTag() }
        val ids = links.map { it.tagId }.distinct()
        if (ids.isEmpty()) return emptyMap()
        val tags =
            client.postgrest
                .pagedRows<TagRow>(TAG_TABLE) { isIn("id", ids) }
                .map { it.toTag() }
        return tagsByMachine(links, tags)
    }
}
