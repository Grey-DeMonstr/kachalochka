package monster.greyde.kachalochka.core.di

import io.github.jan.supabase.SupabaseClient
import monster.greyde.kachalochka.core.data.identity.AccountStore
import monster.greyde.kachalochka.core.data.identity.PersistedAccountStore
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import monster.greyde.kachalochka.core.data.supabase.supabaseClient
import org.koin.dsl.module

val coreModule =
    module {
        single { SupabaseCredentials.fromBuild() }
        single<SupabaseClient> { supabaseClient(get()) }
        single<AccountStore> { PersistedAccountStore(get()) }
    }
