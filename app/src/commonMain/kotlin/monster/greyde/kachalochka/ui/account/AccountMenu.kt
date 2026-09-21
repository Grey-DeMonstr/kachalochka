package monster.greyde.kachalochka.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.ui.components.SectionLabel
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AccountMenu(
    onOpenSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val viewModel: AccountsViewModel = koinViewModel()
    val ui by viewModel.state.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val active = ui.accounts.firstOrNull { it.active }

    Box {
        Avatar(active, modifier, onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SectionLabel(
                "Пишем подходы в",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ui.accounts.forEach { account ->
                AccountRow(account) {
                    expanded = false
                    viewModel.switchTo(account.id)
                }
            }
            DropdownMenuItem(
                text = { Text("Добавить аккаунт") },
                onClick = {
                    expanded = false
                    viewModel.addAccount()
                },
                modifier = Modifier.testTag("account-add"),
            )
            if (onOpenSettings != null) {
                DropdownMenuItem(
                    text = { Text("Настройки") },
                    onClick = {
                        expanded = false
                        onOpenSettings()
                    },
                    modifier = Modifier.testTag("account-settings"),
                )
            }
            if (active != null) {
                DropdownMenuItem(
                    text = { Text("Выйти из аккаунта «${active.displayName}»") },
                    onClick = {
                        expanded = false
                        viewModel.signOutActive()
                    },
                    modifier = Modifier.testTag("account-sign-out"),
                )
            }
        }
    }
}

/**
 * The clickable circle always answers to [modifier]'s tag, active or not; the dashed marker is a
 * sibling rather than a child, so it stays queryable on its own instead of merging into the click
 * target's semantics.
 */
@Composable
private fun Avatar(
    active: AccountUi?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = CircleShape
    Box {
        Box(
            modifier =
                modifier
                    .size(50.dp)
                    .clip(shape)
                    .then(
                        if (active != null) {
                            Modifier
                                .background(colors.primary.copy(alpha = 0.16f))
                                .border(1.dp, colors.primary, shape)
                        } else {
                            Modifier
                        },
                    ).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            active?.let {
                Text(
                    it.monogram,
                    fontWeight = FontWeight.Medium,
                    color = colors.onPrimaryContainer,
                )
            }
        }
        if (active == null) {
            Box(
                Modifier
                    .matchParentSize()
                    .testTag("account-avatar-empty")
                    .dashedCircle(colors.onBackground.copy(alpha = 0.35f)),
            )
        }
    }
}

@Composable
private fun AccountRow(
    account: AccountUi,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val tag = "account-${account.id.value}" + if (account.active) "-active" else ""
    DropdownMenuItem(
        text = {
            Column {
                Text(account.displayName, color = colors.onSurface)
                Text(account.email, fontSize = 13.sp, color = colors.onSurface.copy(alpha = 0.6f))
            }
        },
        leadingIcon = { MonogramBadge(account.monogram) },
        trailingIcon = {
            if (account.active) {
                Icon(
                    PhosphorIcons.Check,
                    null,
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        onClick = onClick,
        modifier = Modifier.testTag(tag),
    )
}

/** The letter circle, drawn in the menu's rows and in the set sheet's person chips. */
@Composable
internal fun MonogramBadge(
    monogram: String,
    size: Dp = 32.dp,
    accent: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val shape = CircleShape
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .then(
                if (accent) Modifier.background(colors.primary.copy(alpha = 0.16f)) else Modifier,
            ).border(
                1.dp,
                if (accent) colors.primary else colors.onBackground.copy(alpha = 0.22f),
                shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            monogram,
            fontSize = if (size > 32.dp) 15.sp else 13.sp,
            color = if (accent) colors.onPrimaryContainer else colors.onBackground,
        )
    }
}

internal fun Modifier.dashedCircle(color: Color): Modifier =
    drawBehind {
        val stroke =
            Stroke(
                width = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
        drawCircle(color = color, style = stroke)
    }
