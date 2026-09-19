package monster.greyde.kachalochka.core.data.gym

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Instant

class RemoteMachineRepository(
    private val client: SupabaseClient,
) : MachineRepository {
    override suspend fun upsert(machine: Machine) {
        client.postgrest.from(MACHINE_TABLE).upsert(MachineRow.of(machine))
    }

    override suspend fun byId(id: MachineId): Machine? =
        client.postgrest
            .from(MACHINE_TABLE)
            .select { filter { eq("id", id.value) } }
            .decodeSingleOrNull<MachineRow>()
            ?.toMachine()

    override suspend fun all(): List<Machine> =
        client.postgrest
            .from(MACHINE_TABLE)
            .select { filter { eq("deleted", false) } }
            .decodeList<MachineRow>()
            .map { it.toMachine() }
            .sortedBy { it.name.lowercase() }
}

@Serializable
private data class MachineRow(
    val id: String,
    @SerialName("user_id") val userId: String?,
    val name: String,
    @SerialName("setup_note") val setupNote: String,
    @SerialName("weight_mode") val weightMode: String,
    @SerialName("platform_weight") val platformWeight: Double,
    @SerialName("platform_included") val platformIncluded: Boolean,
    val unit: String,
    @SerialName("weight_step") val weightStep: Double,
    @SerialName("updated_at") val updatedAt: String,
    val deleted: Boolean,
) {
    fun toMachine(): Machine =
        Machine(
            id = MachineId(id),
            userId = userId?.let(::UserId),
            name = name,
            setupNote = setupNote,
            weightMode = weightModeOf(weightMode),
            platformWeight = platformWeight,
            platformIncluded = platformIncluded,
            unit = weightUnitOf(unit),
            weightStep = weightStep,
            updatedAt = Instant.parse(updatedAt),
            deleted = deleted,
        )

    companion object {
        fun of(machine: Machine): MachineRow =
            MachineRow(
                id = machine.id.value,
                userId = machine.userId?.value,
                name = machine.name,
                setupNote = machine.setupNote,
                weightMode = machine.weightMode.wireName(),
                platformWeight = machine.platformWeight,
                platformIncluded = machine.platformIncluded,
                unit = machine.unit.wireName(),
                weightStep = machine.weightStep,
                updatedAt = machine.updatedAt.toString(),
                deleted = machine.deleted,
            )
    }
}
