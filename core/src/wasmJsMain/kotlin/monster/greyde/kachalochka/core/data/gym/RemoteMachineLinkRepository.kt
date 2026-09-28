package monster.greyde.kachalochka.core.data.gym

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkRepository
import monster.greyde.kachalochka.core.domain.identity.UserId

class RemoteMachineLinkRepository(
    private val client: SupabaseClient,
) : MachineLinkRepository {
    override suspend fun upsert(link: MachineLink) {
        client.postgrest.from(MACHINE_LINK_TABLE).upsert(MachineLinkRow.of(link))
    }

    override suspend fun all(owner: UserId?): List<MachineLink> =
        client.postgrest
            .from(MACHINE_LINK_TABLE)
            .select {
                filter {
                    eq("deleted", false)
                    owned(owner)
                }
            }.decodeList<MachineLinkRow>()
            .map { it.toMachineLink() }
}
