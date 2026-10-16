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
 * A machine's tags are written after it, through [writeTagsOf]. The ids follow from the content,
 * so a write retried after a failure writes the same rows.
 */
class RemoteMachineRepository(
    private val client: SupabaseClient,
) : MachineRepository {
    override suspend fun upsert(machine: Machine) {
        client.postgrest.from(MACHINE_TABLE).upsert(MachineRow.of(machine))
        PostgrestTagRows(client.postgrest).writeTagsOf(machine)
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
