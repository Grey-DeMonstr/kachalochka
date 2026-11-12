package monster.greyde.kachalochka.core.data.supabase

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.ProxyConfig
import io.ktor.client.engine.okhttp.OkHttpConfig
import okhttp3.Credentials

// Requests are HTTPS, so the proxy sees only the CONNECT request. A header cannot reach it;
// the answer to its 407 can.
actual fun HttpClientEngineConfig.goThrough(
    proxy: ProxyConfig?,
    login: ProxyLogin?,
) {
    this.proxy = proxy
    if (login == null || login.login.isBlank() || this !is OkHttpConfig) return
    config {
        proxyAuthenticator { _, response ->
            response.request
                .newBuilder()
                .header("Proxy-Authorization", Credentials.basic(login.login, login.password))
                .build()
        }
    }
}
