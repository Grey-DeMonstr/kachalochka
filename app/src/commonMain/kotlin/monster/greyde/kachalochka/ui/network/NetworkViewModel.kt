package monster.greyde.kachalochka.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.AppVersion
import monster.greyde.kachalochka.core.data.catching
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import monster.greyde.kachalochka.ui.share.TextSharing
import monster.greyde.kachalochka.ui.stats.StatsPictureRows
import kotlin.time.Clock

/** Runs every check once, all at the same time, as the screen opens. */
class NetworkViewModel(
    checks: NetworkChecks,
    device: DeviceNetwork,
    credentials: SupabaseCredentials,
    accounts: Accounts,
    clock: Clock,
    private val proxies: ProxyPreference,
    private val pictureRows: StatsPictureRows,
    private val sharing: TextSharing,
) : ViewModel() {
    val facts: List<Pair<String, String>> =
        listOf(
            "App" to AppVersion.NAME,
            "Time" to clock.now().toString(),
            "Server" to Url(credentials.url).host,
            "Proxy in use" to proxyFact(credentials),
            "Signed in" to
                "${if (accounts.activeId.value != null) "yes" else "no"}, " +
                "${accounts.accounts.value.size} accounts",
        ) + device.facts()

    private val rows = MutableStateFlow(checks.all.map { CheckRow(it.name) })
    val checks: StateFlow<List<CheckRow>> = rows

    // Kept here as well, so typed letters show before the device has stored them.
    private val typedProxy = MutableStateFlow(proxies.setting.value)
    val proxy: StateFlow<ProxySetting> = typedProxy

    private val typedRows = MutableStateFlow(pictureRows.rows.value.toString())
    val pictureRowsText: StateFlow<String> = typedRows

    init {
        checks.all.forEachIndexed { index, check ->
            viewModelScope.launch {
                val result = check.result()
                rows.update { all ->
                    all.mapIndexed { i, row -> if (i == index) row.copy(result = result) else row }
                }
            }
        }
    }

    fun share() {
        viewModelScope.launch { catching { sharing.share(networkReport(facts, rows.value)) } }
    }

    private fun proxyFact(credentials: SupabaseCredentials): String {
        val proxy = credentials.proxy?.toString() ?: return "none"
        return if (credentials.proxyLogin != null) "$proxy (with login)" else proxy
    }

    /** Keeps only a whole number of 1 or more. */
    fun setPictureRows(text: String) {
        typedRows.value = text
        val rows = text.trim().toIntOrNull()?.takeIf { it >= 1 } ?: return
        viewModelScope.launch { pictureRows.set(rows) }
    }

    fun setProxy(setting: ProxySetting) {
        typedProxy.value = setting
        viewModelScope.launch { proxies.set(setting) }
    }
}
