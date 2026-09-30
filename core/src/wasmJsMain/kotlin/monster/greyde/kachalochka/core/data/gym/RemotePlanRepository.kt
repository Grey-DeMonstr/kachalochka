package monster.greyde.kachalochka.core.data.gym

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import monster.greyde.kachalochka.core.domain.gym.Plan
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.core.domain.gym.PlanRepository
import monster.greyde.kachalochka.core.domain.gym.planOrder
import monster.greyde.kachalochka.core.domain.identity.UserId

class RemotePlanRepository(
    private val client: SupabaseClient,
) : PlanRepository {
    override suspend fun upsert(plan: Plan) {
        client.postgrest.from(PLAN_TABLE).upsert(PlanRow.of(plan))
    }

    override suspend fun byId(id: PlanId): Plan? =
        client.postgrest
            .from(PLAN_TABLE)
            .select { filter { eq("id", id.value) } }
            .decodeSingleOrNull<PlanRow>()
            ?.toPlan()

    // One order on both platforms, whatever the server's collation.
    override suspend fun all(owner: UserId?): List<Plan> =
        client.postgrest
            .from(PLAN_TABLE)
            .select {
                filter {
                    eq("deleted", false)
                    owned(owner)
                }
            }.decodeList<PlanRow>()
            .map { it.toPlan() }
            .sortedWith(planOrder)
}
