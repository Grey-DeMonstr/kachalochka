package monster.greyde.kachalochka

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.github.jan.supabase.SupabaseClient
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import monster.greyde.kachalochka.core.data.identity.AccountStore
import monster.greyde.kachalochka.core.data.identity.SessionActivation
import monster.greyde.kachalochka.core.di.corePlatformModule
import monster.greyde.kachalochka.di.appModule
import monster.greyde.kachalochka.di.platformModule
import monster.greyde.kachalochka.ui.account.completeSignIn
import monster.greyde.kachalochka.ui.account.resumeActiveAccount
import monster.greyde.kachalochka.ui.account.sessionFromRedirect
import org.koin.core.Koin
import org.koin.core.context.startKoin
import kotlin.time.Duration.Companion.seconds

private val SESSION_RESTORE_LIMIT = 10.seconds

/**
 * Every screen reads Supabase, and row-level security turns away a request the stored session has
 * not been handed to, so the session comes back before the first frame does.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val koin = startKoin { modules(appModule, corePlatformModule(), platformModule()) }.koin
    MainScope().launch {
        // A Supabase that answers with an error, or not at all, still leaves a page worth showing.
        runCatching { withTimeoutOrNull(SESSION_RESTORE_LIMIT) { koin.restoreSession() } }
        ComposeViewport(document.body!!) { App() }
    }
}

private suspend fun Koin.restoreSession() {
    val store = get<AccountStore>()
    val sessions = get<SessionActivation>()
    val returned = get<SupabaseClient>().sessionFromRedirect()
    if (!completeSignIn(returned, store, sessions)) resumeActiveAccount(store, sessions)
}
