package monster.greyde.kachalochka.core.data.gym

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.identity.UserId

class RemoteVisitRepository(
    private val client: SupabaseClient,
) : VisitRepository {
    override suspend fun upsert(visit: Visit) {
        client.postgrest.from(VISIT_TABLE).upsert(VisitRow.of(visit))
    }

    override suspend fun byId(id: VisitId): Visit? =
        client.postgrest
            .from(VISIT_TABLE)
            .select { filter { eq("id", id.value) } }
            .decodeSingleOrNull<VisitRow>()
            ?.toVisit()

    override suspend fun active(owner: UserId?): Visit? =
        client.postgrest
            .from(VISIT_TABLE)
            .select {
                filter {
                    exact("ended_at", null)
                    eq("deleted", false)
                    owned(owner)
                }
                order("recorded_at", Order.DESCENDING)
                limit(1)
            }.decodeList<VisitRow>()
            .firstOrNull()
            ?.toVisit()

    override suspend fun all(owner: UserId?): List<Visit> =
        client.postgrest
            .from(VISIT_TABLE)
            .select {
                filter {
                    eq("deleted", false)
                    owned(owner)
                }
                order("recorded_at", Order.DESCENDING)
            }.decodeList<VisitRow>()
            .map { it.toVisit() }
}
