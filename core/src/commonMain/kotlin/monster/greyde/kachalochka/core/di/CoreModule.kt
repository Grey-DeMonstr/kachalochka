package monster.greyde.kachalochka.core.di

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.core.data.family.AccountFamilyReads
import monster.greyde.kachalochka.core.data.family.FamilyFollower
import monster.greyde.kachalochka.core.data.family.FamilyReads
import monster.greyde.kachalochka.core.data.family.SupabaseFamilyRepository
import monster.greyde.kachalochka.core.data.identity.AccountDeletion
import monster.greyde.kachalochka.core.data.identity.AccountStore
import monster.greyde.kachalochka.core.data.identity.AccountTokens
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.identity.ActiveAccountUser
import monster.greyde.kachalochka.core.data.identity.LiveSession
import monster.greyde.kachalochka.core.data.identity.PersistedAccountStore
import monster.greyde.kachalochka.core.data.identity.SessionActivation
import monster.greyde.kachalochka.core.data.identity.SessionRefresh
import monster.greyde.kachalochka.core.data.identity.SupabaseSessions
import monster.greyde.kachalochka.core.data.identity.liveSessionChanges
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import monster.greyde.kachalochka.core.data.supabase.actingSupabaseClient
import monster.greyde.kachalochka.core.data.supabase.supabaseClient
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import org.koin.core.Koin
import org.koin.dsl.module
import kotlin.time.Clock

val coreModule =
    module {
        single { SupabaseCredentials.fromBuild() }
        single<SupabaseClient> { supabaseClient(get()) }
        // Built while Koin starts, so the stored accounts are in hand before the first frame.
        single<AccountStore>(createdAtStart = true) { PersistedAccountStore(get()) }
        single {
            val sessions = SupabaseSessions(inject())
            LiveSession(sessions, sessions, get())
        }
        single<SessionActivation> { get<LiveSession>() }
        single<CurrentUser> { ActiveAccountUser(get()) }
        single { Accounts(get(), get(), get(), get(), get()) }
        single { AccountDeletion(get(), get(), get()) }
        single<SessionRefresh> { SupabaseSessions(inject()) }
        single { AccountTokens(get(), get<LiveSession>(), get(), Clock.System) }
        single<FamilyReads> {
            val credentials: SupabaseCredentials = get()
            val tokens: AccountTokens = get()
            AccountFamilyReads { acting ->
                SupabaseFamilyRepository(
                    lazy { actingSupabaseClient(credentials, tokens) { acting.owner } },
                )
            }
        }
        single { FamilyFollower(get(), get(), get()) }
    }

/** A build without credentials has no live session to follow, and must not build a client. */
fun Koin.followLiveSession() {
    if (!get<SupabaseCredentials>().isConfigured) return
    val live = get<LiveSession>()
    CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
        // Building the client is kept off the main thread a cold start is drawing on.
        val status = withContext(Dispatchers.Default) { get<SupabaseClient>().auth.sessionStatus }
        live.follow(status.liveSessionChanges())
    }
}
