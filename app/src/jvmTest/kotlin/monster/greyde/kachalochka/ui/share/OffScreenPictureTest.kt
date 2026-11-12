package monster.greyde.kachalochka.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.dp
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class OffScreenPictureTest {
    @Test
    fun a_tall_picture_comes_in_strips_from_top_to_bottom() {
        var strips = emptyList<ImageBitmap>()
        runScreenTest(
            FakeGym(),
            screen = {
                OffScreenPicture(settled = true, onDrawn = { strips = it }) {
                    Column {
                        Box(Modifier.fillMaxWidth().height(800.dp).background(Color.Red))
                        Box(Modifier.fillMaxWidth().height(200.dp).background(Color.Blue))
                    }
                }
            },
        ) { waitUntil { strips.isNotEmpty() } }

        // 1000 dp and two paddings of 16 dp, at the picture's density.
        assertEquals(listOf(2048, 738), strips.map { it.height })
        assertEquals(listOf(1080, 1080), strips.map { it.width })
        assertEquals(Color.Red, strips[0].toPixelMap()[540, 1000])
        assertEquals(Color.Blue, strips[1].toPixelMap()[540, 400])
    }
}
