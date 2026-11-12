package monster.greyde.kachalochka.core.data.supabase

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.ProxyConfig

// The browser decides how its requests travel.
actual fun HttpClientEngineConfig.goThrough(
    proxy: ProxyConfig?,
    login: ProxyLogin?,
) = Unit
