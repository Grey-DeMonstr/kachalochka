package monster.greyde.kachalochka.core.data.supabase

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.ProxyConfig

/** Sends the requests of the engine through [proxy], and answers its 407 with [login]. */
expect fun HttpClientEngineConfig.goThrough(
    proxy: ProxyConfig?,
    login: ProxyLogin?,
)
