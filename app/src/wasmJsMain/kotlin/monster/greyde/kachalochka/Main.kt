package monster.greyde.kachalochka

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import monster.greyde.kachalochka.di.appModule
import monster.greyde.kachalochka.di.platformModule
import org.koin.compose.KoinApplication

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        KoinApplication(application = { modules(appModule, platformModule()) }) { App() }
    }
}
