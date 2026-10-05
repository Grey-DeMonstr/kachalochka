package monster.greyde.kachalochka.fakes

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.ui.family.SASHA
import monster.greyde.kachalochka.ui.family.childAccount
import monster.greyde.kachalochka.ui.friends.IVAN_SESSION
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.PASHA
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.test.Test
import kotlin.test.assertEquals

/** [FakeFriends] must narrow reads the way RLS's `shares_group_with` narrows the server's. */
class FakeFriendsTest {
    private val gym = signedInGym()
    private val t0 = gym.clock.current

    @Test
    fun a_stranger_s_rows_stay_hidden_from_someone_outside_their_group() =
        runTest {
            gym.friends.group("Зал на Лесной", owner = OLEG, ME)
            val pashaMachine = Machine.new("Скамья", PASHA.userId, t0)
            val pashaVisit = Visit(VisitId.random(), PASHA.userId, gym.today, t0, t0, false)
            val pashaSet =
                WorkoutSet(
                    WorkoutSetId.random(),
                    PASHA.userId,
                    pashaVisit.id,
                    pashaMachine.id,
                    50.0,
                    10,
                    0,
                    t0,
                    t0,
                    false,
                )
            gym.friends.machines += pashaMachine
            gym.friends.visits += pashaVisit
            gym.friends.sets += pashaSet

            assertEquals(emptyList(), gym.friends.visits(PASHA.userId))
            assertEquals(emptyList(), gym.friends.sets(pashaVisit))
            assertEquals(emptyList(), gym.friends.machines(PASHA.userId))
        }

    @Test
    fun a_managed_child_s_mates_are_its_parent_s_group_mates_in_groups_holding_it() =
        runTest {
            val family = signedInGym().withChild(childAccount(SASHA, IVAN_SESSION))
            val sasha = Friend(SASHA.userId, SASHA.displayName)
            family.friends.group("Семья", owner = ME, sasha, OLEG)
            family.friends.group("Работа", owner = PASHA, ME)
            family.friends.group("Чужая", owner = PASHA, sasha)
            runBlocking { family.accounts.switchTo(SASHA.userId) }

            assertEquals(
                setOf(ME.userId, OLEG.userId),
                family.friends
                    .mates(SASHA.userId)
                    .map { it.userId }
                    .toSet(),
            )
        }
}
