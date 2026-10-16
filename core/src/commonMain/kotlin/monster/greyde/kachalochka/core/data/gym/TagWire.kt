package monster.greyde.kachalochka.core.data.gym

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.tagKey
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.identity.uuidV5
import kotlin.time.Instant

const val TAG_TABLE: String = "tag"
const val MACHINE_TAG_TABLE: String = "machine_tag"

// Migration 0027 derives the ids from the same namespace.
private const val TAG_NAMESPACE = "6f1c2a4e-8d3b-4c7a-9e51-2b7d0f3a9c84"

/** One row per owner and name, so the same tag written on two devices offline is one row. */
internal fun tagId(
    owner: UserId?,
    name: String,
): String = uuidV5(TAG_NAMESPACE, "${owner?.value.orEmpty()}/${tagKey(name)}")

internal fun machineTagId(
    machine: MachineId,
    name: String,
): String = uuidV5(TAG_NAMESPACE, "${machine.value}/${tagKey(name)}")

/** A tag's name is its [tagKey]; see [tagId]. */
data class Tag(
    val id: String,
    val userId: UserId?,
    val name: String,
    val updatedAt: Instant,
    val deleted: Boolean,
)

/** [machineId] holds the tag [tagId] while the row is live. */
data class MachineTag(
    val id: String,
    val userId: UserId?,
    val machineId: MachineId,
    val tagId: String,
    val updatedAt: Instant,
    val deleted: Boolean,
)

@Serializable
internal data class TagRow(
    val id: String,
    @SerialName("user_id") val userId: String?,
    val name: String,
    @SerialName("updated_at") val updatedAt: String,
    val deleted: Boolean,
) {
    fun toTag(): Tag = Tag(id, userId?.let(::UserId), name, Instant.parse(updatedAt), deleted)

    companion object {
        fun of(tag: Tag): TagRow =
            TagRow(tag.id, tag.userId?.value, tag.name, tag.updatedAt.toString(), tag.deleted)
    }
}

@Serializable
internal data class MachineTagRow(
    val id: String,
    @SerialName("user_id") val userId: String?,
    @SerialName("machine_id") val machineId: String,
    @SerialName("tag_id") val tagId: String,
    @SerialName("updated_at") val updatedAt: String,
    val deleted: Boolean,
) {
    fun toMachineTag(): MachineTag =
        MachineTag(
            id = id,
            userId = userId?.let(::UserId),
            machineId = MachineId(machineId),
            tagId = tagId,
            updatedAt = Instant.parse(updatedAt),
            deleted = deleted,
        )

    companion object {
        fun of(link: MachineTag): MachineTagRow =
            MachineTagRow(
                id = link.id,
                userId = link.userId?.value,
                machineId = link.machineId.value,
                tagId = link.tagId,
                updatedAt = link.updatedAt.toString(),
                deleted = link.deleted,
            )
    }
}

/**
 * [machines] with the tags their live [links] name in [tags]. Only a link and a tag of the
 * machine's own owner count: a group mate's row naming the machine is not its tag.
 */
internal fun List<Machine>.withTagRows(
    links: List<MachineTag>,
    tags: List<Tag>,
): List<Machine> {
    val tagsById = tags.associateBy { it.id }
    val linksByMachine = links.filter { !it.deleted }.groupBy { it.machineId }
    return map { machine ->
        val names =
            linksByMachine[machine.id]
                .orEmpty()
                .filter { it.userId == machine.userId }
                .mapNotNull { tagsById[it.tagId]?.takeIf { tag -> tag.userId == machine.userId } }
                .map { tagKey(it.name) }
        machine.copy(tags = names.toSet())
    }
}
