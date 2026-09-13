package monster.greyde.kachalochka.core.data.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

// A build without credentials is normal, so the failure has to wait until something actually
// asks for a client and then say which two settings are missing.
fun supabaseClient(credentials: SupabaseCredentials): SupabaseClient {
    check(credentials.isConfigured) {
        "Supabase is not configured: set SUPABASE_URL and SUPABASE_ANON_KEY in local.properties " +
            "or in the environment."
    }
    return createSupabaseClient(credentials.url, credentials.anonKey) {
        install(Auth)
        install(Postgrest)
        install(Storage)
        install(Realtime)
    }
}
