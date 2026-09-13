package monster.greyde.kachalochka.di

import monster.greyde.kachalochka.core.di.coreModule
import monster.greyde.kachalochka.ui.home.HomeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule =
    module {
        includes(coreModule)
        viewModelOf(::HomeViewModel)
    }
