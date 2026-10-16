package monster.greyde.kachalochka.core.data.gym

import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId

internal class InMemoryTagRows : TagRowStore {
    val machines: MutableMap<MachineId, Machine> = linkedMapOf()
    val tags: MutableMap<String, Tag> = linkedMapOf()
    val links: MutableMap<String, MachineTag> = linkedMapOf()

    override suspend fun linksOf(machine: Machine): List<MachineTag> =
        links.values.filter { it.machineId == machine.id }

    override suspend fun tags(ids: List<String>): List<Tag> = ids.mapNotNull { tags[it] }

    override suspend fun heldElsewhere(
        machine: Machine,
        tags: List<String>,
    ): Set<String> =
        links.values
            .filter { link ->
                val holder = machines[link.machineId]
                !link.deleted &&
                    link.tagId in tags &&
                    link.userId == machine.userId &&
                    link.machineId != machine.id &&
                    holder != null &&
                    !holder.deleted &&
                    holder.userId == link.userId
            }.map { it.tagId }
            .toSet()

    override suspend fun upsertTags(tags: List<Tag>) {
        tags.forEach { this.tags[it.id] = it }
    }

    override suspend fun upsertLinks(links: List<MachineTag>) {
        links.forEach { this.links[it.id] = it }
    }
}
