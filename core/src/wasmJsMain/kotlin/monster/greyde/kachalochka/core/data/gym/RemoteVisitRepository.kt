package monster.greyde.kachalochka.core.data.gym

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Instant

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
                order("started_at", Order.DESCENDING)
                limit(1)
            }.decodeList<VisitRow>()
            .firstOrNull()
            ?.toVisit()
}

private fun PostgrestFilterBuilder.owned(owner: UserId?) {
    if (owner != null) {
        eq("user_id", owner.value)
    } else {
        exact("user_id", null)
    }
}

@Serializable
private data class VisitRow(
    val id: String,
    @SerialName("user_id") val userId: String?,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String?,
    @SerialName("updated_at") val updatedAt: String,
    val deleted: Boolean,
) {
    fun toVisit(): Visit =
        Visit(
            id = VisitId(id),
            userId = userId?.let(::UserId),
            startedAt = Instant.parse(startedAt),
            endedAt = endedAt?.let(Instant::parse),
            updatedAt = Instant.parse(updatedAt),
            deleted = deleted,
        )

    companion object {
        fun of(visit: Visit): VisitRow =
            VisitRow(
                id = visit.id.value,
                userId = visit.userId?.value,
                startedAt = visit.startedAt.toString(),
                endedAt = visit.endedAt?.toString(),
                updatedAt = visit.updatedAt.toString(),
                deleted = visit.deleted,
            )
    }
}
