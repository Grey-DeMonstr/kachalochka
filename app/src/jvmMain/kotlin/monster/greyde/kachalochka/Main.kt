package monster.greyde.kachalochka

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import monster.greyde.kachalochka.core.di.corePlatformModule
import monster.greyde.kachalochka.di.appModule
import monster.greyde.kachalochka.di.platformModule
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration

fun main() =
    application {
        Window(onCloseRequest = ::exitApplication, title = "Kachalochka") {
            KoinApplication(
                configuration =
                    koinConfiguration {
                        modules(appModule, corePlatformModule(), platformModule())
                    },
            ) { App() }
        }
    }
