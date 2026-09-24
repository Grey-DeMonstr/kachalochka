package monster.greyde.kachalochka.core.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.core.data.gym.MACHINE_TABLE
import monster.greyde.kachalochka.core.data.gym.VISIT_TABLE
import monster.greyde.kachalochka.core.data.gym.WORKOUT_SET_TABLE
import monster.greyde.kachalochka.core.data.profile.PROFILE_TABLE
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.sync.OutboxEntry

class SyncPass(
    private val outbox: OutboxDao,
    private val rows: LocalSyncRows,
    private val watermarks: SyncWatermarks,
    private val gateway: SyncGateway,
    private val session: SyncSession,
    private val dispatcher: CoroutineDispatcher,
) {
    suspend fun run(owners: List<UserId>) {
        owners.forEach { owner ->
            session.owner = owner
            try {
                push(owner)
            } finally {
                session.owner = null
            }
        }
    }

    // The server enforces a set's foreign keys to its visit and machine; SQLite does not.
    private suspend fun push(owner: UserId) {
        val entries = db { outbox.pending() }.sortedBy { it.tableName == WORKOUT_SET_TABLE }
        entries.forEach { entry ->
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
            }
        }
    }

    private suspend fun <T : Any> pushRow(
        entry: OutboxEntry,
        owner: UserId,
        row: T?,
        ownerOf: (T) -> UserId?,
        send: suspend (T) -> Unit,
    ) {
        val rowOwner = row?.let(ownerOf)
        // An entry with no owned row behind it can never be pushed and would sit in every pass.
        if (row == null || rowOwner == null) {
            db { outbox.removePushed(entry) }
            return
        }
        if (rowOwner != owner) return
        attempt {
            send(row)
            db { outbox.removePushed(entry) }
        }
    }

    // One refused row must not hold back the rest; its entry stays for the next pass.
    private suspend fun attempt(work: suspend () -> Unit) {
        try {
            work()
        } catch (stopped: CancellationException) {
            throw stopped
        } catch (refused: Exception) {
            return
        }
    }

    private suspend fun <T> db(read: () -> T): T = withContext(dispatcher) { read() }
}
