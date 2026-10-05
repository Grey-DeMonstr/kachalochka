package monster.greyde.kachalochka.core.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.core.data.gym.MACHINE_LINK_TABLE
import monster.greyde.kachalochka.core.data.gym.MACHINE_TABLE
import monster.greyde.kachalochka.core.data.gym.PHOTO_TABLE
import monster.greyde.kachalochka.core.data.gym.PLAN_TABLE
import monster.greyde.kachalochka.core.data.gym.PhotoFiles
import monster.greyde.kachalochka.core.data.gym.VISIT_TABLE
import monster.greyde.kachalochka.core.data.gym.WORKOUT_SET_TABLE
import monster.greyde.kachalochka.core.data.measures.MEASUREMENT_TABLE
import monster.greyde.kachalochka.core.data.measures.MEASURE_TABLE
import monster.greyde.kachalochka.core.data.profile.PROFILE_TABLE
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.Plan
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.measures.MEASURE_SEEDED_AT
import monster.greyde.kachalochka.core.domain.measures.Measure
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.sync.OutboxEntry
import kotlin.time.Instant

class SyncPass(
    private val outbox: OutboxDao,
    private val rows: LocalSyncRows,
    private val watermarks: SyncWatermarks,
    private val gateway: SyncGateway,
    private val session: SyncSession,
    private val files: PhotoFiles,
    private val dispatcher: CoroutineDispatcher,
) {
    /**
     * True only when every push and every pull succeeded. A [managed] owner's guardian may write
     * its gym rows and plans, nothing else (technical spec §4.2).
     */
    suspend fun run(
        owners: List<UserId>,
        managed: Set<UserId> = emptySet(),
    ): Boolean =
        owners
            .map { owner ->
                session.owner = owner
                try {
                    val guarded = owner in managed
                    val pushed = push(owner, guarded)
                    pull(owner, guarded) && pushed
                } finally {
                    session.owner = null
                }
            }.all { it }

    private suspend fun push(
        owner: UserId,
        guarded: Boolean,
    ): Boolean {
        val entries = db { outbox.pending() }.sortedBy { rank(it.tableName) }
        return entries
            .map { entry ->
                when (entry.tableName) {
                    MACHINE_TABLE ->
                        pushRow(entry, owner, db { rows.machine(entry.rowId) }, { it.userId }) {
                            gateway.pushMachine(it)
                        }

                    VISIT_TABLE ->
                        pushRow(entry, owner, db { rows.visit(entry.rowId) }, { it.userId }) {
                            gateway.pushVisit(it)
                        }

                    WORKOUT_SET_TABLE ->
                        pushRow(entry, owner, db { rows.set(entry.rowId) }, { it.userId }) {
                            gateway.pushSet(it)
                        }

                    PLAN_TABLE ->
                        pushRow(entry, owner, db { rows.plan(entry.rowId) }, { it.userId }) {
                            gateway.pushPlan(it)
                        }

                    PROFILE_TABLE ->
                        pushRow(
                            entry,
                            owner,
                            db { rows.profile(entry.rowId) },
                            { it.userId },
                            kept = guarded,
                        ) { gateway.pushProfile(it) }

                    MACHINE_LINK_TABLE ->
                        pushRow(
                            entry,
                            owner,
                            db { rows.machineLink(entry.rowId) },
                            { it.userId },
                        ) { gateway.pushMachineLink(it) }

                    MEASURE_TABLE ->
                        pushRow(
                            entry,
                            owner,
                            db { rows.measure(entry.rowId) },
                            { it.userId },
                            kept = guarded,
                        ) { gateway.pushMeasure(it) }

                    MEASUREMENT_TABLE ->
                        pushRow(
                            entry,
                            owner,
                            db { rows.measurement(entry.rowId) },
                            { it.userId },
                            kept = guarded,
                        ) { gateway.pushMeasurement(it) }

                    PHOTO_TABLE ->
                        pushRow(entry, owner, db { rows.photo(entry.rowId) }, { it.userId }) {
                            val jpeg = if (it.deleted) null else db { files.read(it.id) }
                            gateway.pushPhoto(it, jpeg)
                        }

                    else -> true
                }
            }.all { it }
    }

    private suspend fun pull(
        owner: UserId,
        guarded: Boolean,
    ): Boolean {
        val since = db { watermarks.lastPullAt(owner) }
        var pulled: PulledRows? = null
        attempt {
            pulled =
                PulledRows(
                    gateway.pullMachines(owner, since),
                    gateway.pullVisits(owner, since),
                    gateway.pullSets(owner, since),
                    if (guarded) emptyList() else gateway.pullProfiles(owner, since),
                    gateway.pullMachineLinks(owner, since),
                    if (guarded) emptyList() else gateway.pullMeasures(owner, since),
                    if (guarded) emptyList() else gateway.pullMeasurements(owner, since),
                    gateway.pullPhotos(owner, since),
                    gateway.pullPlans(owner, since),
                )
        }
        val rowsPulled = pulled ?: return false
        db {
            rows.transaction {
                val pending = outbox.pending().map { it.tableName to it.rowId }.toSet()
                rowsPulled.machines.forEach {
                    if ((MACHINE_TABLE to it.id.value) !in pending) rows.writeMachine(it)
                }
                rowsPulled.visits.forEach {
                    if ((VISIT_TABLE to it.id.value) !in pending) rows.writeVisit(it)
                }
                rowsPulled.sets.forEach {
                    if ((WORKOUT_SET_TABLE to it.id.value) !in pending) rows.writeSet(it)
                }
                rowsPulled.profiles.forEach {
                    if ((PROFILE_TABLE to it.id.value) !in pending) rows.writeProfile(it)
                }
                rowsPulled.links.forEach {
                    if ((MACHINE_LINK_TABLE to it.id.value) !in pending) rows.writeMachineLink(it)
                }
                rowsPulled.measures.forEach {
                    if ((MEASURE_TABLE to it.id.value) !in pending) {
                        rows.writeMeasure(it)
                    } else if (rows.measure(it.id.value)?.updatedAt == MEASURE_SEEDED_AT) {
                        // The server ignores a seed pushed over a newer row, so the seed yields.
                        rows.writeMeasure(it)
                        outbox.remove(MEASURE_TABLE, it.id.value)
                    }
                }
                rowsPulled.measurements.forEach {
                    if ((MEASUREMENT_TABLE to it.id.value) !in pending) rows.writeMeasurement(it)
                }
                rowsPulled.photos.forEach {
                    if ((PHOTO_TABLE to it.id.value) !in pending) {
                        rows.writePhoto(it)
                        if (it.deleted) files.delete(it.id)
                    }
                }
                rowsPulled.plans.forEach {
                    if ((PLAN_TABLE to it.id.value) !in pending) rows.writePlan(it)
                }
                rowsPulled.newestUpdatedAt?.let { watermarks.advance(owner, it) }
            }
        }
        return true
    }

    private class PulledRows(
        val machines: List<Machine>,
        val visits: List<Visit>,
        val sets: List<WorkoutSet>,
        val profiles: List<Profile>,
        val links: List<MachineLink>,
        val measures: List<Measure>,
        val measurements: List<Measurement>,
        val photos: List<Photo>,
        val plans: List<Plan>,
    ) {
        val newestUpdatedAt: Instant? =
            (
                machines.map { it.updatedAt } + visits.map { it.updatedAt } +
                    sets.map { it.updatedAt } + profiles.map { it.updatedAt } +
                    links.map { it.updatedAt } + measures.map { it.updatedAt } +
                    measurements.map { it.updatedAt } + photos.map { it.updatedAt } +
                    plans.map { it.updatedAt }
            ).maxOrNull()
    }

    private fun rank(table: String): Int =
        PUSH_RANK.indexOf(table).let { if (it < 0) PUSH_RANK.size else it }

    private suspend fun <T : Any> pushRow(
        entry: OutboxEntry,
        owner: UserId,
        row: T?,
        ownerOf: (T) -> UserId?,
        kept: Boolean = false,
        send: suspend (T) -> Unit,
    ): Boolean {
        val rowOwner = row?.let(ownerOf)
        // An entry with no owned row behind it can never be pushed and would sit in every pass.
        if (row == null || rowOwner == null) {
            db { outbox.removePushed(entry) }
            return true
        }
        if (rowOwner != owner) return true
        // The guardian's session may not write it, so it stays on this device only.
        if (kept) {
            db { outbox.removePushed(entry) }
            return true
        }
        return attempt {
            send(row)
            db { outbox.removePushed(entry) }
        }
    }

    // One refused row must not hold back the rest; its entry stays for the next pass.
    private suspend fun attempt(work: suspend () -> Unit): Boolean =
        try {
            work()
            true
        } catch (stopped: CancellationException) {
            throw stopped
        } catch (refused: Exception) {
            false
        }

    private suspend fun <T> db(read: () -> T): T = withContext(dispatcher) { read() }

    private companion object {
        // The server checks a set's visit and machine. It checks nothing a link, a plan or a value
        // names, but each follows the rows it names, so a reader never meets it before them.
        val PUSH_RANK =
            listOf(
                MACHINE_TABLE,
                VISIT_TABLE,
                PLAN_TABLE,
                PROFILE_TABLE,
                MEASURE_TABLE,
                MACHINE_LINK_TABLE,
                PHOTO_TABLE,
                WORKOUT_SET_TABLE,
                MEASUREMENT_TABLE,
            )
    }
}
