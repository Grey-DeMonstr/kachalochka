package monster.greyde.kachalochka.core.data.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

fun supabaseClient(credentials: SupabaseCredentials): SupabaseClient =
    createSupabaseClient(credentials.url, credentials.anonKey) {
        install(Auth)
        install(Postgrest)
        install(Storage)
        install(Realtime)
    }
