package monster.greyde.kachalochka.di

import monster.greyde.kachalochka.core.data.identity.GoogleSignIn
import monster.greyde.kachalochka.ui.account.SignInRequired
import monster.greyde.kachalochka.ui.account.UnavailableGoogleSignIn
import monster.greyde.kachalochka.ui.theme.InMemoryThemePreference
import monster.greyde.kachalochka.ui.theme.ThemePreference
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module =
    module {
        single<ThemePreference> { InMemoryThemePreference() }
        single<GoogleSignIn> { UnavailableGoogleSignIn }
        single { SignInRequired(false) }
    }
