package monster.greyde.kachalochka.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.domain.gym.restRemaining
import monster.greyde.kachalochka.ui.account.AccountMenu
import monster.greyde.kachalochka.ui.format.formatRest
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.timer.RestTimer
import monster.greyde.kachalochka.ui.timer.Ticker
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
fun rememberNow(): Instant {
    val clock: Clock = koinInject()
    val ticker: Ticker = koinInject()
    val now by produceState(clock.now()) {
        while (true) {
            ticker.awaitTick()
            value = clock.now()
        }
    }
    return now
}

@Composable
fun Screen(
    title: String,
    onBack: (() -> Unit)?,
    onOpenSettings: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxSize(), color = colors.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (onBack != null) {
                    SquareIconButton(
                        PhosphorIcons.ArrowLeft,
                        "Назад",
                        onBack,
                        Modifier.testTag("top-bar-back"),
                    )
                }
                Text(
                    title,
                    modifier = Modifier.weight(1f).testTag("top-bar-title"),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.onBackground,
                )
                RestTimerChip(Modifier.testTag("rest-timer"))
                AccountMenu(onOpenSettings, Modifier.testTag("account-avatar"))
            }
            Rule()
            content()
        }
    }
}

/** The bar's square chip, or the sheet's rounded [pill]; both restart the same timer. */
@Composable
fun RestTimerChip(
    modifier: Modifier = Modifier,
    pill: Boolean = false,
) {
    val timer: RestTimer = koinInject()
    val startedAt by timer.startedAt.collectAsState()
    val now = rememberNow()
    val colors = MaterialTheme.colorScheme
    val shape = if (pill) RoundedCornerShape(22.dp) else ControlShape
    Row(
        modifier =
            modifier
                .height(if (pill) 44.dp else 50.dp)
                .clip(shape)
                .border(1.dp, colors.onBackground.copy(alpha = if (pill) 0.20f else 0.18f), shape)
                .clickable(onClick = timer::start)
                .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            PhosphorIcons.Timer,
            null,
            tint = colors.secondary,
            modifier = Modifier.size(if (pill) 20.dp else 22.dp),
        )
        Text(
            formatRest(restRemaining(startedAt, timer.duration, now)),
            fontSize = if (pill) 15.sp else 16.sp,
            color = colors.onBackground,
        )
    }
}
