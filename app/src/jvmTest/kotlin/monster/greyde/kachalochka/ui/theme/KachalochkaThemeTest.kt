package monster.greyde.kachalochka.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
class KachalochkaThemeTest {
    @Test
    fun switching_to_dark_changes_the_colour_scheme() =
        runComposeUiTest {
            var observed = Color.Unspecified

            setContent {
                var mode by remember { mutableStateOf(ThemeMode.Light) }
                KachalochkaTheme(mode) {
                    observed = MaterialTheme.colorScheme.background
                    Text(
                        text = "switch",
                        modifier =
                            Modifier
                                .testTag("switch")
                                .clickable { mode = ThemeMode.Dark },
                    )
                }
            }

            val light = observed
            onNodeWithTag("switch").performClick()
            waitForIdle()

            assertNotEquals(light, observed)
        }
}
