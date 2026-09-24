package monster.greyde.kachalochka.core.data.gym

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.identity.UserId

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

    override suspend fun all(owner: UserId?): List<Machine> =
        client.postgrest
            .from(MACHINE_TABLE)
            .select {
                filter {
                    eq("deleted", false)
                    owned(owner)
                }
            }.decodeList<MachineRow>()
            .map { it.toMachine() }
            .sortedBy { it.name.lowercase() }

    override suspend fun named(
        owner: UserId?,
        name: String,
    ): Machine? =
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
            ?.toMachine()
}
