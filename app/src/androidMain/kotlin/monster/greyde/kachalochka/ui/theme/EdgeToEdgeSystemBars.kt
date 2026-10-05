package monster.greyde.kachalochka.ui.theme

import android.app.Activity
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * Transparent bars over the scheme's background. An explicit style keeps the system from adding
 * its contrast scrim, which follows the light window theme.
 */
class EdgeToEdgeSystemBars(
    private val activity: () -> Activity?,
) : SystemBars {
    override fun follow(dark: Boolean) {
        val style =
            if (dark) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            }
        (activity() as? ComponentActivity)?.enableEdgeToEdge(style, style)
    }
}
