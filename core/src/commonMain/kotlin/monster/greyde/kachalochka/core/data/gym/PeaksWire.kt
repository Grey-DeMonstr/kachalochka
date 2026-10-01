package monster.greyde.kachalochka.core.data.gym

import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachinePeaks
import monster.greyde.kachalochka.core.domain.gym.SetPeak
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Instant

/** One machine's reduction by `machine_peaks`, the server's `machinePeaks`; see 0021. */
@Serializable
internal data class MachinePeaksRow(
    @SerialName("machine_id") val machineId: String,
    val heaviest: Double,
    @SerialName("heaviest_reps") val heaviestReps: Int,
    val lightest: Double,
    @SerialName("lightest_reps") val lightestReps: Int,
    @SerialName("last_at") val lastAt: String,
    val visits: Int,
) {
    fun toPeaks(): MachinePeaks =
        MachinePeaks(
            MachineId(machineId),
            SetPeak(heaviest, heaviestReps),
            SetPeak(lightest, lightestReps),
            Instant.parse(lastAt),
            visits,
        )
}

/** The peaks of [owners]' machines, reduced on the server so no row cap cuts a history short. */
suspend fun Postgrest.machinePeaks(owners: Collection<UserId>): List<MachinePeaks> {
    if (owners.isEmpty()) return emptyList()
    return rpc(
        "machine_peaks",
        buildJsonObject { putJsonArray("owners") { owners.forEach { add(it.value) } } },
    ).decodeList<MachinePeaksRow>()
        .map { it.toPeaks() }
}
