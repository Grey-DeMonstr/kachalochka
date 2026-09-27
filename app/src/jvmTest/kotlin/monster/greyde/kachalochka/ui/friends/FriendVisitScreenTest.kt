package monster.greyde.kachalochka.ui.friends

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.runScreenTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalTestApi::class)
class FriendVisitScreenTest {
    private val gym = signedInGym()
    private val t0 = gym.clock.current
    private val yesterday = gym.today.plusDays(-1)
    private val myPress = Machine.new("Жим ногами", ME.userId, t0)
    private val olegPress = linkedCopy(myPress, OLEG.userId, t0).copy(name = "Платформа")
    private val olegRow = Machine.new("Тяга", OLEG.userId, t0).copy(unit = WeightUnit.Lb)
    private val olegVisit = Visit(VisitId.random(), OLEG.userId, yesterday, t0 - 1.days, t0, false)

    private fun olegSet(
        machine: Machine,
        weight: Double,
        reps: Int,
        minutes: Int,
    ) = WorkoutSet(
        WorkoutSetId.random(),
        OLEG.userId,
        olegVisit.id,
        machine.id,
        weight,
        reps,
        0,
        t0 - 1.days + minutes.minutes,
        t0,
        false,
    )

    @BeforeTest
    fun setUp() =
        runBlocking {
            gym.friends.group("Зал на Лесной", owner = OLEG, ME)
            gym.friends.machines += listOf(olegPress, olegRow)
            gym.friends.visits += olegVisit
            gym.friends.sets +=
                listOf(
                    olegSet(olegPress, 80.0, 8, 0),
                    olegSet(olegPress, 85.0, 6, 1),
                    olegSet(olegRow, 100.0, 10, 2),
                )
            gym.machines.upsert(myPress)
        }

    @Test
    fun a_friend_s_visit_is_read_only() {
        runScreenTest(gym, screen = {
            FriendVisitScreen(OLEG.userId, "Олег", yesterday, {}, {})
        }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Олег · 13 ноября")
            onNodeWithTag("friend-group-${olegPress.id.value}").assertIsDisplayed()
            val first = gym.friends.sets.first()
            onNodeWithTag("friend-set-${first.id.value}").assertIsDisplayed()
            onNodeWithTag("pick-machine").assertDoesNotExist()
            onNodeWithTag("reorder-toggle").assertDoesNotExist()
            onNodeWithTag("set-sheet").assertDoesNotExist()
        }
    }

    @Test
    fun offline_it_offers_a_retry() {
        gym.friends.offline = true
        runScreenTest(gym, screen = {
            FriendVisitScreen(OLEG.userId, "Олег", yesterday, {}, {})
        }) {
            onNodeWithTag("friends-offline").assertTextEquals("Нет связи с сервером")
            gym.friends.offline = false
            onNodeWithTag("friends-retry").performClick()
            waitForIdle()
            onNodeWithTag("friends-offline").assertDoesNotExist()
        }
    }
}
