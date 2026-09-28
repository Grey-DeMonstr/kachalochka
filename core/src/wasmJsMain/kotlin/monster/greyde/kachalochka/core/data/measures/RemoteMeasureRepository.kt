package monster.greyde.kachalochka.core.data.measures

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import monster.greyde.kachalochka.core.data.gym.owned
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.measures.Measure
import monster.greyde.kachalochka.core.domain.measures.MeasureKind
import monster.greyde.kachalochka.core.domain.measures.MeasureRepository
import monster.greyde.kachalochka.core.domain.measures.measureOrder

class RemoteMeasureRepository(
    private val client: SupabaseClient,
) : MeasureRepository {
    override suspend fun upsert(measure: Measure) {
        client.postgrest.from(MEASURE_TABLE).upsert(MeasureRow.of(measure))
    }

    override suspend fun all(owner: UserId?): List<Measure> =
        everyRow(owner).filterNot { it.deleted }.sortedWith(measureOrder)

    override suspend fun kinds(owner: UserId?): Set<MeasureKind> =
        everyRow(owner).mapNotNull { it.kind }.toSet()

    private suspend fun everyRow(owner: UserId?): List<Measure> =
        client.postgrest
            .from(MEASURE_TABLE)
            .select { filter { owned(owner) } }
            .decodeList<MeasureRow>()
            .map { it.toMeasure() }
}
