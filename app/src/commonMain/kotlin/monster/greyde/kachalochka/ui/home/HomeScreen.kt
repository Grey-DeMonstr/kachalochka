package monster.greyde.kachalochka.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.ui.account.AccountsViewModel
import monster.greyde.kachalochka.ui.components.AccentButton
import monster.greyde.kachalochka.ui.components.DISABLED_ALPHA
import monster.greyde.kachalochka.ui.components.Rule
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    onOpenVisit: (VisitId) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: HomeViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val accountsViewModel: AccountsViewModel = koinViewModel()
    val accounts by accountsViewModel.state.collectAsState()
    val credentials: SupabaseCredentials = koinInject()
    val signedIn = accounts.activeId != null
    LaunchedEffect(Unit) { viewModel.refresh() }
    Screen("Качалочка", onBack = null, onOpenSettings = onOpenSettings) {
        val current = state ?: return@Screen
        Box(Modifier.padding(16.dp)) {
            val visit = current.activeVisit
            if (visit == null) {
                AccentButton(
                    "Начать визит",
                    PhosphorIcons.Plus,
                    { viewModel.startVisit(onOpenVisit) },
                    Modifier.testTag("start-visit"),
                )
            } else {
                VisitCard(visit, onContinue = { onOpenVisit(visit.id) })
            }
        }
        SectionRow(PhosphorIcons.ListChecks, "Планы", "section-plans")
        SectionRow(PhosphorIcons.ChartLineUp, "Статистика", "section-stats")
        SectionRow(PhosphorIcons.UsersThree, "Друзья", "section-friends", locked = !signedIn)
        Rule()
        if (!signedIn && credentials.isConfigured) {
            Box(Modifier.padding(16.dp)) {
                AccentButton(
                    "Войти через Google",
                    PhosphorIcons.ArrowRight,
                    accountsViewModel::addAccount,
                    Modifier.testTag("home-sign-in"),
                )
            }
        }
    }
}

@Composable
private fun VisitCard(
    visit: ActiveVisitUi,
    onContinue: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, colors.primary, shape)
            .background(colors.primary.copy(alpha = 0.12f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    "ВИЗИТ ИДЁТ",
                    fontSize = 11.sp,
                    letterSpacing = 0.09.em,
                    color = colors.secondary,
                )
                Text(
                    visit.counts,
                    modifier = Modifier.testTag("visit-counts"),
                    fontSize = 14.sp,
                    color = colors.tertiary,
                )
            }
            visit.lastSet?.let {
                Text(
                    "Последний подход\n$it",
                    modifier = Modifier.testTag("visit-last-set"),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.End,
                    color = colors.onBackground.copy(alpha = 0.6f),
                )
            }
        }
        AccentButton(
            "Продолжить",
            PhosphorIcons.ArrowRight,
            onContinue,
            Modifier.testTag("continue-visit"),
        )
    }
}

/**
 * Sections without screens yet: drawn as in the design, disabled until they exist. [locked]
 * marks a section that additionally needs a signed-in account, with its own trailing icon.
 */
@Composable
private fun SectionRow(
    icon: ImageVector,
    label: String,
    tag: String,
    locked: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Rule()
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(DISABLED_ALPHA)
            .clickable(enabled = false) {}
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = colors.secondary, modifier = Modifier.size(26.dp))
        Text(label, modifier = Modifier.weight(1f), fontSize = 19.sp, color = colors.onBackground)
        if (locked) {
            Icon(
                PhosphorIcons.LockSimple,
                "Заблокировано",
                tint = colors.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp).testTag("$tag-lock"),
            )
        } else {
            Icon(
                PhosphorIcons.CaretRight,
                null,
                tint = colors.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
