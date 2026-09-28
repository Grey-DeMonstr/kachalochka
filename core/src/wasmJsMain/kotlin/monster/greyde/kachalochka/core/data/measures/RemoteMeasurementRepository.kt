package monster.greyde.kachalochka.core.data.measures

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import monster.greyde.kachalochka.core.data.gym.owned
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.measures.MeasurementRepository
import monster.greyde.kachalochka.core.domain.measures.newestPerDay

class RemoteMeasurementRepository(
    private val client: SupabaseClient,
) : MeasurementRepository {
    override suspend fun upsert(measurement: Measurement) {
        client.postgrest.from(MEASUREMENT_TABLE).upsert(MeasurementRow.of(measurement))
    }

    override suspend fun all(owner: UserId?): List<Measurement> =
        newestPerDay(
            client.postgrest
                .from(MEASUREMENT_TABLE)
                .select { filter { owned(owner) } }
                .decodeList<MeasurementRow>()
                .map { it.toMeasurement() },
        )
}
