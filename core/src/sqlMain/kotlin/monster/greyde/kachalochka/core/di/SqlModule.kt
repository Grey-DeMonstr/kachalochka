package monster.greyde.kachalochka.core.di

import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.data.profile.LocalProfileRepository
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/** Everything above the driver is the same on both SQLDelight targets; only the driver differs. */
internal fun sqlModule(): Module =
    module {
        single { KachalochkaDatabase(get()) }
        single { OutboxDao(get()) }
        single<ProfileRepository> { LocalProfileRepository(get(), get()) }
    }
