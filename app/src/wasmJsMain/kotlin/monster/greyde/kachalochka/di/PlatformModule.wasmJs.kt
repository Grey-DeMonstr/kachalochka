package monster.greyde.kachalochka.di

import monster.greyde.kachalochka.core.data.identity.GoogleSignIn
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import monster.greyde.kachalochka.ui.account.RedirectGoogleSignIn
import monster.greyde.kachalochka.ui.account.SignInAvailable
import monster.greyde.kachalochka.ui.account.SignInRequired
import monster.greyde.kachalochka.ui.theme.LocalStorageThemePreference
import monster.greyde.kachalochka.ui.theme.ThemePreference
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module =
    module {
        single<ThemePreference> { LocalStorageThemePreference() }
        single<GoogleSignIn> { RedirectGoogleSignIn(get()) }
        single { SignInRequired(true) }
        single { SignInAvailable(get<SupabaseCredentials>().isConfigured) }
    }
