package monster.greyde.kachalochka.core.data.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import monster.greyde.kachalochka.core.data.identity.AccountTokens
import monster.greyde.kachalochka.core.data.sync.SyncSession

// A build without credentials is normal, so the failure has to wait until something actually
// asks for a client and then say which two settings are missing.
fun supabaseClient(credentials: SupabaseCredentials): SupabaseClient {
    check(credentials.isConfigured) {
        "Supabase is not configured: set SUPABASE_URL and SUPABASE_ANON_KEY in local.properties " +
            "or in the environment."
    }
    return createSupabaseClient(credentials.url, credentials.anonKey) {
        // The account store holds every signed-in session; supabase-kt's own storage keeps one and
        // would contend for the same slot.
        install(Auth) {
            autoLoadFromStorage = false
            autoSaveToStorage = false
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
    check(credentials.isConfigured) {
        "Supabase is not configured: set SUPABASE_URL and SUPABASE_ANON_KEY in local.properties " +
            "or in the environment."
    }
    return createSupabaseClient(credentials.url, credentials.anonKey) {
        accessToken = { session.owner?.let { tokens.tokenFor(it) } }
        install(Postgrest)
    }
}
