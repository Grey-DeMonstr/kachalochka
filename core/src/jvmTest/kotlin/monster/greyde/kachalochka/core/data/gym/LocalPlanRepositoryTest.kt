package monster.greyde.kachalochka.core.data.gym

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.db.inMemoryDatabase
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.gym.PRESS
import monster.greyde.kachalochka.core.domain.gym.Plan
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.core.domain.gym.ROW
import monster.greyde.kachalochka.core.domain.gym.T0
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours

class LocalPlanRepositoryTest {
    private val database = inMemoryDatabase()
    private val outbox = OutboxDao(database)
    private val repository = LocalPlanRepository(database, outbox, Dispatchers.Unconfined)
    private val ivan = UserId("11111111-1111-4111-8111-111111111111")

    private fun plan(
        owner: UserId? = null,
        createdHoursAgo: Int = 0,
        deleted: Boolean = false,
    ) = Plan(
        PlanId.random(),
        owner,
        "Ноги",
        listOf(ROW, PRESS),
        T0 - createdHoursAgo.hours,
        T0,
        deleted,
    )

    @Test
    fun a_plan_reads_back_whole() =
        runTest {
            val legs = plan()

            repository.upsert(legs)

            assertEquals(legs, repository.byId(legs.id))
        }

    @Test
    fun an_owner_s_live_plans_list_oldest_first() =
        runTest {
            val newer = plan()
            val older = plan(createdHoursAgo = 2)
            val deleted = plan(createdHoursAgo = 3, deleted = true)
            val ivans = plan(owner = ivan)
            listOf(newer, older, deleted, ivans).forEach { repository.upsert(it) }

            assertEquals(listOf(older, newer), repository.all(null))
            assertEquals(listOf(ivans), repository.all(ivan))
        }

    @Test
    fun only_an_owned_plan_enters_the_outbox() =
        runTest {
            val ivans = plan(owner = ivan)
            repository.upsert(plan())
            repository.upsert(ivans)

            assertEquals(
                listOf(PLAN_TABLE to ivans.id.value),
                outbox.pending().map { it.tableName to it.rowId },
            )
        }
}
