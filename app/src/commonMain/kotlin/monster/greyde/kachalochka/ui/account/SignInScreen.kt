package monster.greyde.kachalochka.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.ui.components.AccentButton
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import org.koin.compose.koinInject

/** The web's frame 5e: no route is reachable until this hands off through [onSignIn]. */
@Composable
fun SignInScreen(
    onSignIn: () -> Unit,
    failure: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val available: SignInAvailable = koinInject()
    Surface(Modifier.fillMaxSize(), color = colors.background) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 320.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(
                    "Качалочка",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onBackground,
                )
                AccentButton(
                    "Войти через Google",
                    PhosphorIcons.ArrowRight,
                    onSignIn,
                    Modifier.testTag("sign-in-google"),
                    enabled = available.value,
                )
                if (!available.value) {
                    Text(
                        "Вход недоступен — сборка не настроена",
                        modifier = Modifier.testTag("sign-in-unconfigured"),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = colors.onBackground.copy(alpha = 0.6f),
                    )
                }
                failure?.let { SignInFailure(it) }
            }
        }
    }
}

/** One line under whichever button offered the sign-in; the design draws no error screen. */
@Composable
internal fun SignInFailure(
    message: String,
    modifier: Modifier = Modifier,
) {
    Text(
        message,
        modifier = modifier.testTag("sign-in-failure"),
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.error,
    )
}
