package monster.greyde.kachalochka.di

import monster.greyde.kachalochka.core.di.coreModule
import org.koin.dsl.module

val appModule =
    module {
        includes(coreModule)
    }
