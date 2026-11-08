package monster.greyde.kachalochka.core.data.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.SupabaseClientBuilder
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.ProxyConfig
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

// supabase-kt marks its Ktor config internal, but it is the only way to give the engine a proxy.
@OptIn(SupabaseInternal::class)
private fun SupabaseClientBuilder.goThrough(proxy: ProxyConfig?) =
    httpConfig { engine { this.proxy = proxy } }

fun supabaseClient(credentials: SupabaseCredentials): SupabaseClient {
    requireConfigured(credentials)
    return createSupabaseClient(credentials.url, credentials.anonKey) {
        goThrough(credentials.proxy)
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

private fun tokenClient(
    credentials: SupabaseCredentials,
    token: suspend () -> String?,
): SupabaseClient {
    requireConfigured(credentials)
    return createSupabaseClient(credentials.url, credentials.anonKey) {
        goThrough(credentials.proxy)
        accessToken = token
        install(Postgrest)
        install(Storage)
    }
}

// The sync pass moves between accounts within one run, so its client resolves the token of
// whichever account the pass is currently on instead of holding one live session like the UI
// client does.
fun syncSupabaseClient(
    credentials: SupabaseCredentials,
    tokens: AccountTokens,
    session: SyncSession,
): SupabaseClient = tokenClient(credentials) { session.owner?.let { tokens.tokenFor(it) } }

// Android's UI client holds a session only after a sign-in or a switch in the same process, so
// friends' reads resolve the active account's token per request, as the sync client does.
fun activeAccountSupabaseClient(
    credentials: SupabaseCredentials,
    tokens: AccountTokens,
    store: AccountStore,
): SupabaseClient = tokenClient(credentials) { store.activeId.value?.let { tokens.tokenFor(it) } }

// Deleting an account must act as that account to the end, whoever becomes active meanwhile.
fun ownerSupabaseClient(
    credentials: SupabaseCredentials,
    tokens: AccountTokens,
    owner: UserId,
): SupabaseClient = tokenClient(credentials) { tokens.tokenFor(owner) }

// The family follower reads as each account in turn, one at a time, as the sync pass does. A read
// without a token would go out as anon and find no children, so it fails instead.
fun actingSupabaseClient(
    credentials: SupabaseCredentials,
    tokens: AccountTokens,
    acting: () -> UserId?,
): SupabaseClient =
    tokenClient(credentials) {
        val owner = checkNotNull(acting()) { "A family read names no account" }
        checkNotNull(tokens.tokenFor(owner)) { "No token to read the family as" }
    }
