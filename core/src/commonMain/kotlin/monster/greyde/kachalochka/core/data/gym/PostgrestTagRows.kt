package monster.greyde.kachalochka.core.data.gym

import io.github.jan.supabase.postgrest.Postgrest
import monster.greyde.kachalochka.core.domain.gym.Machine

/** The tag rows in Supabase, over PostgREST. */
internal class PostgrestTagRows(
    private val postgrest: Postgrest,
) : TagRowStore {
    override suspend fun linksOf(machine: Machine): List<MachineTag> =
        postgrest
            .pagedRows<MachineTagRow>(MACHINE_TAG_TABLE) {
                eq("machine_id", machine.id.value)
                owned(machine.userId)
            }.map { it.toMachineTag() }

    override suspend fun tags(ids: List<String>): List<Tag> =
        postgrest
            .pagedRows<TagRow>(TAG_TABLE) { isIn("id", ids) }
            .map { it.toTag() }

    override suspend fun heldElsewhere(
        machine: Machine,
        tags: List<String>,
    ): Set<String> {
        val links =
            postgrest
                .pagedRows<MachineTagRow>(MACHINE_TAG_TABLE) {
                    eq("deleted", false)
                    owned(machine.userId)
                    neq("machine_id", machine.id.value)
                    isIn("tag_id", tags)
                }.map { it.toMachineTag() }
        val machines = links.map { it.machineId.value }.distinct()
        if (machines.isEmpty()) return emptySet()
        val live =
            postgrest
                .pagedRows<MachineRow>(MACHINE_TABLE) {
                    eq("deleted", false)
                    owned(machine.userId)
                    isIn("id", machines)
                }.map { it.id }
                .toSet()
        return links.filter { it.machineId.value in live }.map { it.tagId }.toSet()
    }

    override suspend fun upsertTags(tags: List<Tag>) {
        postgrest.from(TAG_TABLE).upsert(tags.map(TagRow::of))
    }

    override suspend fun upsertLinks(links: List<MachineTag>) {
        postgrest.from(MACHINE_TAG_TABLE).upsert(links.map(MachineTagRow::of))
    }
}
