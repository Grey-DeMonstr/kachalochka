package monster.greyde.kachalochka.core.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.core.data.gym.MACHINE_LINK_TABLE
import monster.greyde.kachalochka.core.data.gym.MACHINE_TABLE
import monster.greyde.kachalochka.core.data.gym.VISIT_TABLE
import monster.greyde.kachalochka.core.data.gym.WORKOUT_SET_TABLE
import monster.greyde.kachalochka.core.data.profile.PROFILE_TABLE
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.sync.OutboxEntry
import kotlin.time.Instant

class SyncPass(
    private val outbox: OutboxDao,
    private val rows: LocalSyncRows,
    private val watermarks: SyncWatermarks,
    private val gateway: SyncGateway,
    private val session: SyncSession,
    private val dispatcher: CoroutineDispatcher,
) {
    /** True only when every push and every pull succeeded. */
    suspend fun run(owners: List<UserId>): Boolean =
        owners
            .map { owner ->
                session.owner = owner
                try {
                    val pushed = push(owner)
                    pull(owner) && pushed
                } finally {
                    session.owner = null
                }
            }.all { it }

    private suspend fun push(owner: UserId): Boolean {
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

                    PROFILE_TABLE ->
                        pushRow(entry, owner, db { rows.profile(entry.rowId) }, { it.userId }) {
                            gateway.pushProfile(it)
                        }

                    MACHINE_LINK_TABLE ->
                        pushRow(
                            entry,
                            owner,
                            db { rows.machineLink(entry.rowId) },
                            { it.userId },
                        ) { gateway.pushMachineLink(it) }

                    else -> true
                }
            }.all { it }
    }

    private suspend fun pull(owner: UserId): Boolean {
        val since = db { watermarks.lastPullAt(owner) }
        var pulled: PulledRows? = null
        attempt {
            pulled =
                PulledRows(
                    gateway.pullMachines(owner, since),
                    gateway.pullVisits(owner, since),
                    gateway.pullSets(owner, since),
                    gateway.pullProfiles(owner, since),
                    gateway.pullMachineLinks(owner, since),
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
    ) {
        val newestUpdatedAt: Instant? =
            (
                machines.map { it.updatedAt } + visits.map { it.updatedAt } +
                    sets.map { it.updatedAt } + profiles.map { it.updatedAt } +
                    links.map { it.updatedAt }
            ).maxOrNull()
    }

    private fun rank(table: String): Int =
        PUSH_RANK.indexOf(table).let { if (it < 0) PUSH_RANK.size else it }

    private suspend fun <T : Any> pushRow(
        entry: OutboxEntry,
        owner: UserId,
        row: T?,
        ownerOf: (T) -> UserId?,
        send: suspend (T) -> Unit,
    ): Boolean {
        val rowOwner = row?.let(ownerOf)
        // An entry with no owned row behind it can never be pushed and would sit in every pass.
        if (row == null || rowOwner == null) {
            db { outbox.removePushed(entry) }
            return true
        }
        if (rowOwner != owner) return true
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
        // The server checks a set's visit and machine. It checks nothing a link names, but a
        // link follows the machines so a friend never reads one before its machine.
        val PUSH_RANK =
            listOf(
                MACHINE_TABLE,
                VISIT_TABLE,
                PROFILE_TABLE,
                MACHINE_LINK_TABLE,
                WORKOUT_SET_TABLE,
            )
    }
}
