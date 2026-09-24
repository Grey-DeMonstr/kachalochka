package monster.greyde.kachalochka.core.data.sync

import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.data.gym.machineOf
import monster.greyde.kachalochka.core.data.gym.visitOf
import monster.greyde.kachalochka.core.data.gym.workoutSetOf
import monster.greyde.kachalochka.core.data.profile.profileOf
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.profile.Profile

/** Rows as the sync pass sees them: read and written without ever touching the outbox. */
class LocalSyncRows(
    private val database: KachalochkaDatabase,
) {
    fun machine(id: String): Machine? =
        database.machineQueries.byId(id, ::machineOf).executeAsOneOrNull()

    fun visit(id: String): Visit? = database.visitQueries.byId(id, ::visitOf).executeAsOneOrNull()

    fun set(id: String): WorkoutSet? =
        database.workoutSetQueries.byId(id, ::workoutSetOf).executeAsOneOrNull()

    fun profile(id: String): Profile? =
        database.profileQueries.byId(id, ::profileOf).executeAsOneOrNull()
}
