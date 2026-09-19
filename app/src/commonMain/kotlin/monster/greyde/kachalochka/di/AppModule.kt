package monster.greyde.kachalochka.di

import kotlinx.coroutines.delay
import monster.greyde.kachalochka.core.di.coreModule
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.platformUtcOffset
import monster.greyde.kachalochka.ui.home.HomeViewModel
import monster.greyde.kachalochka.ui.timer.RestTimer
import monster.greyde.kachalochka.ui.timer.Ticker
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

val appModule =
    module {
        includes(coreModule)
        single<Clock> { Clock.System }
        single<Ticker> { Ticker { delay(1.seconds) } }
        single<UtcOffset> { UtcOffset(::platformUtcOffset) }
        single { RestTimer(get()) }
        viewModelOf(::HomeViewModel)
    }
