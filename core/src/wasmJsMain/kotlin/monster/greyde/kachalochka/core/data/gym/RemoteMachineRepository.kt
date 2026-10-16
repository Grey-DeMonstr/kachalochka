package monster.greyde.kachalochka.core.data.gym

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.identity.UserId

/**
 * A machine's tags are written after it, as the sync pass pushes them: live tags, then links, then
 * the deletion of unused tags. The ids follow from the content, so a write retried after a failure
 * writes the same rows.
 */
class RemoteMachineRepository(
    private val client: SupabaseClient,
) : MachineRepository {
    override suspend fun upsert(machine: Machine) {
        val postgrest = client.postgrest
        val owner = machine.userId
        postgrest.from(MACHINE_TABLE).upsert(MachineRow.of(machine))
        val stored =
            postgrest
                .pagedRows<MachineTagRow>(MACHINE_TAG_TABLE) {
                    eq("machine_id", machine.id.value)
                    owned(owner)
                }.map { it.toMachineTag() }
        val named = (machine.tags.map { tagId(owner, it) } + stored.map { it.tagId }).distinct()
        val storedTags =
            if (named.isEmpty()) {
                emptyList()
            } else {
                postgrest
                    .pagedRows<TagRow>(TAG_TABLE) { isIn("id", named) }
                    .map { it.toTag() }
            }
        val dropped = stored.filter { !it.deleted }.map { it.tagId }.distinct()
        val writes =
            tagWrites(machine, stored, storedTags, postgrest.heldElsewhere(machine, dropped))
        if (writes.tags.isNotEmpty()) {
            postgrest.from(TAG_TABLE).upsert(writes.tags.map(TagRow::of))
        }
        if (writes.links.isNotEmpty()) {
            postgrest.from(MACHINE_TAG_TABLE).upsert(writes.links.map(MachineTagRow::of))
        }
        if (writes.unused.isNotEmpty()) {
            postgrest.from(TAG_TABLE).upsert(writes.unused.map(TagRow::of))
        }
    }

    /** Of [tags], those a live link from another live machine of the owner still holds. */
    private suspend fun Postgrest.heldElsewhere(
        machine: Machine,
        tags: List<String>,
    ): Set<String> {
        if (tags.isEmpty()) return emptySet()
        val links =
            pagedRows<MachineTagRow>(MACHINE_TAG_TABLE) {
                eq("deleted", false)
                owned(machine.userId)
                neq("machine_id", machine.id.value)
                isIn("tag_id", tags)
            }.map { it.toMachineTag() }
        val machines = links.map { it.machineId.value }.distinct()
        if (machines.isEmpty()) return emptySet()
        val live =
            pagedRows<MachineRow>(MACHINE_TABLE) {
                eq("deleted", false)
                owned(machine.userId)
                isIn("id", machines)
            }.map { it.id }
                .toSet()
        return links.filter { it.machineId.value in live }.map { it.tagId }.toSet()
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
            val tags = async { tagRowsOf(id) }
            machine.await()?.let { listOf(it).withTagRows(tags.await()).single() }
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
            val tags = async { client.postgrest.tagRows({ owned(owner) }, { owned(owner) }) }
            machines.await().withTagRows(tags.await()).sortedBy { it.name.lowercase() }
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
        return listOf(machine).withTagRows(tagRowsOf(machine.id)).single()
    }

    // A friend's machine reads its tags too: row-level security shows a group mate's live tags.
    private suspend fun tagRowsOf(machine: MachineId): TagRows {
        val links =
            client.postgrest
                .pagedRows<MachineTagRow>(MACHINE_TAG_TABLE) {
                    eq("machine_id", machine.value)
                    eq("deleted", false)
                }.map { it.toMachineTag() }
        val ids = links.map { it.tagId }.distinct()
        if (ids.isEmpty()) return TagRows(links, emptyList())
        val tags =
            client.postgrest
                .pagedRows<TagRow>(TAG_TABLE) { isIn("id", ids) }
                .map { it.toTag() }
        return TagRows(links, tags)
    }
}
