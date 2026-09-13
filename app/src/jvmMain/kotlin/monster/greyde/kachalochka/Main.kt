package monster.greyde.kachalochka

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import monster.greyde.kachalochka.di.appModule
import monster.greyde.kachalochka.di.platformModule
import org.koin.compose.KoinApplication

fun main() =
    application {
        Window(onCloseRequest = ::exitApplication, title = "Kachalochka") {
            KoinApplication(application = { modules(appModule, platformModule()) }) { App() }
        }
    }
