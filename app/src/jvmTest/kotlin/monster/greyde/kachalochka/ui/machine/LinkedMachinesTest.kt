package monster.greyde.kachalochka.ui.machine

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.PASHA
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class LinkedMachinesTest {
    private val long = "Тяга на спину горизонтальная раздельными"

    private fun linked(
        owner: Friend,
        name: String,
    ) = LinkedMachineUi(
        Machine.new(name, owner.userId, Instant.fromEpochSeconds(0)).id,
        owner,
        name,
    )

    /** 200 dp leaves 176 dp for the names after the icon. */
    private fun narrow(
        linked: List<LinkedMachineUi>,
        ownName: String? = null,
        assertions: ComposeUiTest.() -> Unit,
    ) = runScreenTest(FakeGym(), screen = {
        Box(Modifier.width(200.dp)) { LinkedMachines(linked, "linked", ownName) }
    }) {
        waitForIdle()
        assertions()
    }

    private fun ComposeUiTest.bounds(tag: String): DpRect =
        onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun ComposeUiTest.avatar(machine: LinkedMachineUi) =
        bounds("linked-avatar-${machine.machineId.value}")

    private fun ComposeUiTest.name(machine: LinkedMachineUi): DpRect =
        onNode(
            hasText(machine.name) and
                hasAnyAncestor(hasTestTag("linked-${machine.machineId.value}")),
            useUnmergedTree = true,
        ).getUnclippedBoundsInRoot()

    private val DpRect.centreY get() = (top + bottom) / 2

    /** 2 dp takes up the line-height trim of the name's first line. */
    private fun assertNear(
        expected: Dp,
        actual: Dp,
    ) = assertTrue(abs((expected - actual).value) <= 2f, "$actual is not near $expected")

    @Test
    fun a_long_name_starts_on_the_icon_s_line_after_overlapping_avatars() {
        val oleg = linked(OLEG, long)
        val pasha = linked(PASHA, long)
        narrow(listOf(oleg, pasha)) {
            val icon = bounds("linked-icon")
            val first = avatar(oleg)
            val second = avatar(pasha)
            val name = name(oleg)

            assertNear(icon.centreY, first.centreY)
            assertNear(first.centreY, second.centreY)
            assertNear(first.top, name.top)
            assertTrue(name.height > 30.dp, "the name wraps")
            assertTrue(name.left >= second.right, "the name wraps under itself")
            assertNear(5.dp, first.right - second.left)
        }
    }

    @Test
    fun an_avatar_stays_round_at_a_small_font_size() {
        val oleg = linked(OLEG, "Тяга")
        runScreenTest(FakeGym(), screen = {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 0.85f),
            ) { LinkedMachines(listOf(oleg), "linked") }
        }) {
            waitForIdle()
            val avatar = avatar(oleg)

            assertTrue(
                abs((avatar.width - avatar.height).value) <= 0.5f,
                "${avatar.width} x ${avatar.height} is not round",
            )
        }
    }

    @Test
    fun a_name_that_does_not_fit_after_another_starts_its_line_under_the_first_avatar() {
        // 12 overlapping avatars take 161 dp: no avatar and name fit after them in 176 dp.
        val stack = List(12) { linked(OLEG, "Тяга") }
        val press = linked(ME, "Жим")
        narrow(stack + press, ownName = "Тяга") {
            val icon = bounds("linked-icon")
            val first = avatar(stack.first())
            val next = avatar(press)

            assertNear(icon.centreY, first.centreY)
            assertTrue(next.top > first.bottom, "the name starts a new line")
            assertNear(first.left, next.left)
        }
    }
}
