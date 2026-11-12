package monster.greyde.kachalochka.ui.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import monster.greyde.kachalochka.core.data.catching
import monster.greyde.kachalochka.core.data.profile.PROFILE_TABLE
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import monster.greyde.kachalochka.core.data.supabase.goThrough
import kotlin.time.TimeSource

/** What only the device can tell about its network. Only Android binds one. */
interface DeviceNetwork {
    /** Each fact as a label and a value, such as the network type. */
    fun facts(): List<Pair<String, String>>

    suspend fun addressesOf(host: String): List<String>
}

fun proxiedClient(credentials: SupabaseCredentials): HttpClient =
    HttpClient { engine { goThrough(credentials.proxy, credentials.proxyLogin) } }

/** [run] gives the answer it got, or throws the error it got. */
class NetworkCheck(
    val name: String,
    val run: suspend () -> String,
)

data class CheckResult(
    val ok: Boolean,
    val millis: Long,
    val detail: String,
)

/** A check with no [result] still runs. */
data class CheckRow(
    val name: String,
    val result: CheckResult? = null,
)

suspend fun NetworkCheck.result(): CheckResult {
    val start = TimeSource.Monotonic.markNow()
    val outcome = catching { run() }
    val millis = start.elapsedNow().inWholeMilliseconds
    return outcome.fold(
        { CheckResult(true, millis, it) },
        { failure ->
            // The cause often holds the real error, such as the host that was not found.
            // Coroutines can copy an error and give the original as its cause.
            val chain = generateSequence(failure) { it.cause }.take(4).map { it.toString() }
            CheckResult(false, millis, chain.distinct().joinToString(" / "))
        },
    )
}

/** Any HTTP answer passes: the check is whether the host can be reached at all. */
private suspend fun HttpClient.answer(
    url: String,
    anonKey: String? = null,
): String {
    val response = get(url) { anonKey?.let { header("apikey", it) } }
    val server = response.headers[HttpHeaders.Server]?.let { "server $it" }
    return listOfNotNull("HTTP ${response.status.value}", server).joinToString(", ")
}

/** The checks of the network screen, in the order of the report. */
class NetworkChecks(
    client: HttpClient,
    device: DeviceNetwork,
    credentials: SupabaseCredentials,
) {
    private val server = credentials.url.trimEnd('/')
    private val host = Url(server).host
    private val key = credentials.anonKey

    val all: List<NetworkCheck> =
        listOf(
            NetworkCheck("DNS $host") { device.addressesOf(host).joinToString() },
            NetworkCheck("Supabase auth") { client.answer("$server/auth/v1/health", key) },
            NetworkCheck("Supabase database") {
                client.answer("$server/rest/v1/$PROFILE_TABLE?select=id&limit=1", key)
            },
            NetworkCheck("Supabase storage") { client.answer("$server/storage/v1/bucket", key) },
            NetworkCheck("ya.ru") { client.answer("https://ya.ru") },
            NetworkCheck("google.com") { client.answer("https://www.google.com/generate_204") },
        )
}

fun networkReport(
    facts: List<Pair<String, String>>,
    checks: List<CheckRow>,
): String {
    val factLines = facts.map { (label, value) -> "$label: $value" }
    return (factLines + "" + checks.map(CheckRow::line)).joinToString("\n")
}

fun CheckRow.line(): String {
    val result = result ?: return "$name: running"
    val status = if (result.ok) "OK" else "FAILED"
    return "$name: $status, ${result.millis} ms, ${result.detail}"
}
