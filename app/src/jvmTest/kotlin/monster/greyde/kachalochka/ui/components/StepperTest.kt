package monster.greyde.kachalochka.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class StepperTest {
    private fun ComposeUiTest.assertValueFits() {
        val node = onNodeWithTag("weight-value").fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val line = layouts.single().getLineRight(0) - layouts.single().getLineLeft(0)
        assertTrue(
            node.size.width >= line && line > 0f,
            "the field is ${node.size.width}px wide for a ${line}px value",
        )
    }

    @Test
    fun a_value_growing_by_steps_stays_whole_in_its_field() =
        runScreenTest(
            FakeGym(),
            screen = {
                var value by remember { mutableStateOf("5") }
                Stepper(
                    value,
                    caption = null,
                    onMinus = {},
                    onPlus = { value = (value.toDouble() + 2.5).toString() },
                    tag = "weight",
                    onValueChange = { value = it },
                    suffix = "кг",
                )
            },
        ) {
            assertValueFits()
            repeat(4) { onNodeWithTag("weight-plus").performClick() }
            waitForIdle()

            onNodeWithTag("weight-value").assertTextEquals("15.0")
            assertValueFits()
        }
}
