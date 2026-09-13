package monster.greyde.kachalochka.core.di

import monster.greyde.kachalochka.core.data.profile.RemoteProfileRepository
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun corePlatformModule(): Module =
    module {
        single<ProfileRepository> { RemoteProfileRepository(get()) }
    }
