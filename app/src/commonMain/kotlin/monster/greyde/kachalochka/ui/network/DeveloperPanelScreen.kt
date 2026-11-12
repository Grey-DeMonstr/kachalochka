package monster.greyde.kachalochka.ui.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import monster.greyde.kachalochka.ui.components.AccentButton
import monster.greyde.kachalochka.ui.components.Rule
import monster.greyde.kachalochka.ui.components.Screen
import monster.greyde.kachalochka.ui.components.TextInput
import monster.greyde.kachalochka.ui.icons.PhosphorIcons
import monster.greyde.kachalochka.ui.strings.Strings
import monster.greyde.kachalochka.ui.strings.strings
import org.koin.compose.viewmodel.koinViewModel

private enum class PanelTab(
    val tag: String,
    val title: (Strings) -> String,
) {
    Info("tab-info", Strings::infoTab),
    Proxy("tab-proxy", Strings::proxyTab),
    Options("tab-options", Strings::optionsTab),
}

/** The facts and checks of the report that "Share" sends, and the proxy settings. */
@Composable
fun DeveloperPanelScreen(onBack: () -> Unit) {
    val s = strings()
    var tab by rememberSaveable { mutableStateOf(PanelTab.Info) }
    Screen(s.developerPanel, onBack = onBack, onOpenSettings = null) {
        PrimaryTabRow(selectedTabIndex = tab.ordinal) {
            PanelTab.entries.forEach {
                Tab(
                    selected = it == tab,
                    onClick = { tab = it },
                    modifier = Modifier.testTag(it.tag),
                    text = { Text(it.title(s)) },
                )
            }
        }
        when (tab) {
            PanelTab.Info -> InfoTab()
            PanelTab.Proxy -> ProxyTab()
            PanelTab.Options -> OptionsTab()
        }
    }
}

@Composable
private fun ColumnScope.InfoTab() {
    val viewModel: NetworkViewModel = koinViewModel()
    val checks by viewModel.checks.collectAsState()
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        viewModel.facts.forEach { (label, value) ->
            Text("$label: $value", fontSize = 13.sp, color = colors.onBackground)
        }
        Rule()
        checks.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (row.result == null) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                }
                Text(
                    row.line(),
                    fontSize = 13.sp,
                    color = if (row.result?.ok == false) colors.error else colors.onBackground,
                )
            }
        }
    }
    Box(Modifier.padding(16.dp)) {
        AccentButton(
            strings().share,
            PhosphorIcons.ShareNetwork,
            viewModel::share,
            Modifier.testTag("network-share"),
        )
    }
}

@Composable
private fun ColumnScope.ProxyTab() {
    val viewModel: NetworkViewModel = koinViewModel()
    val proxy by viewModel.proxy.collectAsState()
    val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
    val s = strings()
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextInput(
            proxy.address,
            s.proxyAddress,
            { viewModel.setProxy(proxy.copy(address = it)) },
            Modifier.testTag("proxy-address"),
            KeyboardOptions(keyboardType = KeyboardType.Uri),
            valid = !proxy.used || proxy.endpoint != null,
        )
        TextInput(
            proxy.login,
            s.proxyLogin,
            { viewModel.setProxy(proxy.copy(login = it)) },
            Modifier.testTag("proxy-login"),
        )
        TextInput(
            proxy.password,
            s.proxyPassword,
            { viewModel.setProxy(proxy.copy(password = it)) },
            Modifier.testTag("proxy-password"),
            KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(proxy.used) { viewModel.setProxy(proxy.copy(used = it)) }
                .testTag("use-proxy"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = proxy.used, onCheckedChange = null)
            Text(s.useProxy, Modifier.padding(start = 8.dp), fontSize = 16.sp)
        }
        Text(s.proxyAfterRestart, fontSize = 13.5.sp, color = muted)
    }
}

@Composable
private fun ColumnScope.OptionsTab() {
    val viewModel: NetworkViewModel = koinViewModel()
    val text by viewModel.pictureRowsText.collectAsState()
    val s = strings()
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(s.statsPictureRows, fontSize = 16.sp)
        TextInput(
            text,
            "",
            viewModel::setPictureRows,
            Modifier.testTag("picture-rows"),
            KeyboardOptions(keyboardType = KeyboardType.Number),
            valid = text.trim().toIntOrNull()?.let { it >= 1 } == true,
        )
    }
}
