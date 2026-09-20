package monster.greyde.kachalochka

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import monster.greyde.kachalochka.ui.account.ActivityHolder
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val activities: ActivityHolder by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activities.current = this
        setContent { App() }
    }

    override fun onDestroy() {
        // Clear only our own registration: a newer Activity may already own the slot.
        if (activities.current === this) activities.current = null
        super.onDestroy()
    }
}
