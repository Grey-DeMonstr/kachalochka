package monster.greyde.kachalochka.core.domain.friends

import monster.greyde.kachalochka.core.domain.gym.PRESS
import monster.greyde.kachalochka.core.domain.gym.ROW
import monster.greyde.kachalochka.core.domain.gym.T0
import monster.greyde.kachalochka.core.domain.gym.VISIT_A
import monster.greyde.kachalochka.core.domain.gym.VISIT_B
import monster.greyde.kachalochka.core.domain.gym.VISIT_C
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.core.domain.gym.machine
import monster.greyde.kachalochka.core.domain.gym.set
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals

class FriendResultsTest {
    private val oleg = Friend(UserId("33333333-3333-4333-8333-333333333333"), "Олег")
    private val pasha = Friend(UserId("44444444-4444-4444-8444-444444444444"), "Паша")

    private fun Friend.trained(
        visit: VisitId,
        weight: Double,
        atSeconds: Long,
    ): WorkoutSet = set(visit, weight, 8, atSeconds).copy(userId = userId)

    @Test
    fun each_friend_s_newest_set_names_their_latest_visit_newest_first() {
        val sets =
            listOf(
                oleg.trained(VISIT_A, 80.0, 0),
                pasha.trained(VISIT_C, 60.0, 3_600),
                oleg.trained(VISIT_B, 85.0, 86_400),
            )

        assertEquals(listOf(VISIT_B, VISIT_C), latestVisitsByMember(sets, limit = 3))
        assertEquals(listOf(VISIT_B), latestVisitsByMember(sets, limit = 1))
    }

    @Test
    fun a_result_holds_the_visit_s_sets_in_visit_order_under_its_friend() {
        val later = oleg.trained(VISIT_B, 85.0, 120)
        val earlier = oleg.trained(VISIT_B, 80.0, 60)
        val older = oleg.trained(VISIT_A, 70.0, 0)
        val his = pasha.trained(VISIT_C, 60.0, 0)

        val results =
            friendResults(
                listOf(oleg, pasha),
                listOf(VISIT_B, VISIT_C),
                listOf(later, his, older, earlier),
            )

        assertEquals(
            listOf(FriendResult(oleg, listOf(earlier, later)), FriendResult(pasha, listOf(his))),
            results,
        )
    }

    @Test
    fun a_visit_of_someone_who_is_not_a_friend_is_left_out() {
        val stranger =
            set(VISIT_A, 70.0, 8, 0)
                .copy(userId = UserId("55555555-5555-4555-8555-555555555555"))

        assertEquals(emptyList(), friendResults(listOf(oleg), listOf(VISIT_A), listOf(stranger)))
    }

    @Test
    fun a_friend_s_machine_linked_to_one_of_the_viewer_s_reads_under_the_viewer_s_name() {
        val mine = machine(PRESS, "Жим ногами")
        val theirLinked = linkedCopy(mine, oleg.userId, T0).copy(name = "Платформа")
        val theirOwn = machine(ROW, "Тяга").copy(userId = oleg.userId)

        assertEquals(
            mapOf(theirLinked.id to "Жим ногами", theirOwn.id to "Тяга"),
            namesForViewer(listOf(theirLinked, theirOwn), listOf(mine)),
        )
    }
}
