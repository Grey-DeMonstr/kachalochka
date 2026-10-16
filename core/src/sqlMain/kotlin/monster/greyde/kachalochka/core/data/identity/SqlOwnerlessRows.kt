package monster.greyde.kachalochka.core.data.identity

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.data.gym.MACHINE_LINK_TABLE
import monster.greyde.kachalochka.core.data.gym.MACHINE_TABLE
import monster.greyde.kachalochka.core.data.gym.MACHINE_TAG_TABLE
import monster.greyde.kachalochka.core.data.gym.PHOTO_TABLE
import monster.greyde.kachalochka.core.data.gym.PLAN_TABLE
import monster.greyde.kachalochka.core.data.gym.TAG_TABLE
import monster.greyde.kachalochka.core.data.gym.Tag
import monster.greyde.kachalochka.core.data.gym.VISIT_TABLE
import monster.greyde.kachalochka.core.data.gym.WORKOUT_SET_TABLE
import monster.greyde.kachalochka.core.data.gym.machineOf
import monster.greyde.kachalochka.core.data.gym.machineTagOf
import monster.greyde.kachalochka.core.data.gym.tagId
import monster.greyde.kachalochka.core.data.gym.tagOf
import monster.greyde.kachalochka.core.data.gym.write
import monster.greyde.kachalochka.core.data.measures.MEASUREMENT_TABLE
import monster.greyde.kachalochka.core.data.measures.MEASURE_TABLE
import monster.greyde.kachalochka.core.data.profile.PROFILE_TABLE
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.sync.OutboxEntry
import kotlin.time.Clock
import kotlin.time.Instant

class SqlOwnerlessRows(
    private val database: KachalochkaDatabase,
    private val outbox: OutboxDao,
    private val clock: Clock,
    private val dispatcher: CoroutineDispatcher,
) : OwnerlessRows {
    override suspend fun claim(owner: UserId) =
        withContext(dispatcher) {
            val now = clock.now()
            database.transaction {
                val visitIds = database.visitQueries.ownerlessIds().executeAsList()
                val machineIds = database.machineQueries.ownerlessIds().executeAsList()
                val setIds = database.workoutSetQueries.ownerlessIds().executeAsList()
                val profileIds = database.profileQueries.ownerlessIds().executeAsList()
                val linkIds = database.machineLinkQueries.ownerlessIds().executeAsList()
                val measureIds = database.measureQueries.ownerlessIds().executeAsList()
                val measurementIds = database.measurementQueries.ownerlessIds().executeAsList()
                val photoIds = database.photoQueries.ownerlessIds().executeAsList()
                val planIds = database.planQueries.ownerlessIds().executeAsList()

                database.visitQueries.claimOwnerless(owner.value, now)
                database.machineQueries.claimOwnerless(owner.value, now)
                database.workoutSetQueries.claimOwnerless(owner.value, now)
                database.profileQueries.claimOwnerless(owner.value)
                database.machineLinkQueries.claimOwnerless(owner.value, now)
                database.measureQueries.claimOwnerless(owner.value, now)
                database.measurementQueries.claimOwnerless(owner.value, now)
                database.photoQueries.claimOwnerless(owner.value, now)
                database.planQueries.claimOwnerless(owner.value, now)

                enqueue(VISIT_TABLE, visitIds, now)
                enqueue(MACHINE_TABLE, machineIds, now)
                enqueue(WORKOUT_SET_TABLE, setIds, now)
                enqueue(PROFILE_TABLE, profileIds, now)
                enqueue(MACHINE_LINK_TABLE, linkIds, now)
                enqueue(MEASURE_TABLE, measureIds, now)
                enqueue(MEASUREMENT_TABLE, measurementIds, now)
                enqueue(PHOTO_TABLE, photoIds, now)
                enqueue(PLAN_TABLE, planIds, now)
                claimTags(owner, now)
            }
        }

    // A tag's id names its owner, so a claimed tag takes the owner's id and joins the owner's own
    // row of that name. Only what a live machine holds is claimed: an anonymous row nothing holds
    // was never pushed, and written as the owner's it could delete a tag the server holds live.
    private fun claimTags(
        owner: UserId,
        now: Instant,
    ) {
        val tags = database.tagQueries.ownerless(::tagOf).executeAsList()
        val names = tags.associate { it.id to it.name }
        val held = mutableMapOf<String, String>()
        database.machineTagQueries.ownerless(::machineTagOf).executeAsList().forEach { link ->
            val name = names[link.tagId]
            val machine =
                database.machineQueries
                    .byId(
                        link.machineId.value,
                        ::machineOf,
                    ).executeAsOneOrNull()
            if (link.deleted || name == null || machine == null || machine.deleted) {
                database.machineTagQueries.forget(link.id)
                return@forEach
            }
            val claimed = tagId(owner, name)
            held[claimed] = name
            database.machineTagQueries.write(
                link.copy(userId = owner, tagId = claimed, updatedAt = now),
            )
            enqueue(MACHINE_TAG_TABLE, listOf(link.id), now)
        }
        tags.forEach { database.tagQueries.forget(it.id) }
        held.forEach { (id, name) ->
            database.tagQueries.write(Tag(id, owner, name, now, deleted = false))
            enqueue(TAG_TABLE, listOf(id), now)
        }
    }

    private fun enqueue(
        table: String,
        ownerlessIds: List<String>,
        now: Instant,
    ) {
        ownerlessIds.forEach { outbox.enqueue(OutboxEntry(table, it, now)) }
    }
}
