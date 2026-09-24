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
import monster.greyde.kachalochka.core.di.followLiveSession
import monster.greyde.kachalochka.di.appModule
import monster.greyde.kachalochka.di.platformModule
import monster.greyde.kachalochka.ui.account.disownActiveAccount
import monster.greyde.kachalochka.ui.account.restoreSession
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
        // The net under the reporting in restoreSession, not a substitute for it: a start-up
        // that goes wrong in a way nobody foresaw still owes the user a page to look at.
        try {
            koin.restoreSession()
        } catch (failure: Throwable) {
            report("Start-up could not finish: $failure")
        }
        // After the restore, so the refresh the restore itself may trigger is written back.
        koin.followLiveSession()
        ComposeViewport(document.body!!) { App() }
    }
}

// The bound is what stops a Supabase that never answers from costing the user the page.
private suspend fun Koin.restoreSession() {
    val store = get<AccountStore>()
    val sessions = get<SessionActivation>()
    val client = get<SupabaseClient>()
    val restored =
        withTimeoutOrNull(SESSION_RESTORE_LIMIT) {
            restoreSession(store, sessions, ::report) { client.sessionFromRedirect() }
        }
    if (restored == null) {
        report("Supabase did not answer within $SESSION_RESTORE_LIMIT.")
        disownActiveAccount(store, sessions)
    }
}

// The browser console is where anyone debugging a page that will not load looks first.
private fun report(message: String): Unit = js("console.error(message)")
