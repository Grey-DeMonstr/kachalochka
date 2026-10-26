package monster.greyde.kachalochka.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import monster.greyde.kachalochka.core.data.identity.Accounts
import org.koin.compose.koinInject

/** True while a trainee is active: their trainer only looks, and writes plans and machines. */
fun Accounts.traineeActive(): Boolean =
    accounts.value.any {
        it.userId == activeId.value &&
            it.isTrainee
    }

/**
 * [Accounts.traineeActive] for a screen, which then hides what the trainer may not change. Read
 * from the store itself, so it holds from the first frame.
 */
@Composable
fun traineeActive(): Boolean {
    val accounts: Accounts = koinInject()
    val listed by accounts.accounts.collectAsState()
    val active by accounts.activeId.collectAsState()
    return listed.any { it.userId == active && it.isTrainee }
}
