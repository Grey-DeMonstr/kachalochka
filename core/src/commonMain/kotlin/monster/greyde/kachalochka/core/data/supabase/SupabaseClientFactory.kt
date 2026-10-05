package monster.greyde.kachalochka.core.data.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import monster.greyde.kachalochka.core.data.identity.AccountStore
import monster.greyde.kachalochka.core.data.identity.AccountTokens
import monster.greyde.kachalochka.core.data.sync.SyncSession
import monster.greyde.kachalochka.core.domain.identity.UserId

// A build without credentials is normal, so the failure has to wait until something actually
// asks for a client and then say which two settings are missing.
private fun requireConfigured(credentials: SupabaseCredentials) {
    check(credentials.isConfigured) {
        "Supabase is not configured: set SUPABASE_URL and SUPABASE_ANON_KEY in local.properties " +
            "or in the environment."
    }
}

fun supabaseClient(credentials: SupabaseCredentials): SupabaseClient {
    requireConfigured(credentials)
    return createSupabaseClient(credentials.url, credentials.anonKey) {
        // The account store holds every signed-in session; supabase-kt's own storage keeps one and
        // would contend for the same slot.
        install(Auth) {
            autoLoadFromStorage = false
            autoSaveToStorage = false
            // The foreground hook would reload that empty storage and end the live session.
            enableLifecycleCallbacks = false
        }
        install(Postgrest)
        install(Storage)
        install(Realtime)
    }
}

// The sync pass moves between accounts within one run, so its client resolves the token of
// whichever account the pass is currently on instead of holding one live session like the UI
// client does.
fun syncSupabaseClient(
    credentials: SupabaseCredentials,
    tokens: AccountTokens,
    session: SyncSession,
): SupabaseClient {
    requireConfigured(credentials)
    return createSupabaseClient(credentials.url, credentials.anonKey) {
        accessToken = { session.owner?.let { tokens.tokenFor(it) } }
        install(Postgrest)
        install(Storage)
    }
}

// Android's UI client holds a session only after a sign-in or a switch in the same process, so
// friends' reads resolve the active account's token per request, as the sync client does.
fun activeAccountSupabaseClient(
    credentials: SupabaseCredentials,
    tokens: AccountTokens,
    store: AccountStore,
): SupabaseClient {
    requireConfigured(credentials)
    return createSupabaseClient(credentials.url, credentials.anonKey) {
        accessToken = { store.activeId.value?.let { tokens.tokenFor(it) } }
        install(Postgrest)
        install(Storage)
    }
}

// Deleting an account must act as that account to the end, whoever becomes active meanwhile.
fun ownerSupabaseClient(
    credentials: SupabaseCredentials,
    tokens: AccountTokens,
    owner: UserId,
): SupabaseClient {
    requireConfigured(credentials)
    return createSupabaseClient(credentials.url, credentials.anonKey) {
        accessToken = { tokens.tokenFor(owner) }
        install(Postgrest)
        install(Storage)
    }
}

// The family follower reads as each account in turn, one at a time, as the sync pass does.
fun actingSupabaseClient(
    credentials: SupabaseCredentials,
    tokens: AccountTokens,
    acting: () -> UserId?,
): SupabaseClient {
    requireConfigured(credentials)
    return createSupabaseClient(credentials.url, credentials.anonKey) {
        accessToken = { acting()?.let { tokens.tokenFor(it) } }
        install(Postgrest)
    }
}
