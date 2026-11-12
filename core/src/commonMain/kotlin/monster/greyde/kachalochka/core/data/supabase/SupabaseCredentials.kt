package monster.greyde.kachalochka.core.data.supabase

import io.ktor.client.engine.ProxyConfig

data class ProxyLogin(
    val login: String,
    val password: String,
)

/** Absent credentials are normal: the build must work on a machine with no Supabase project. */
data class SupabaseCredentials(
    val url: String,
    val anonKey: String,
    // The id of the Google client Supabase itself trusts, which the Android sign-in must name.
    val googleWebClientId: String = "",
    // The HTTP proxy that every request goes through; null goes direct.
    val proxy: ProxyConfig? = null,
    // What the proxy asks for when it answers with 407; null if it asks for nothing.
    val proxyLogin: ProxyLogin? = null,
) {
    val isConfigured: Boolean = url.isNotBlank() && anonKey.isNotBlank()

    /** Credential Manager names this client id in its request; the web redirect never sees it. */
    val canSignInWithGoogleId: Boolean = isConfigured && googleWebClientId.isNotBlank()

    companion object {
        fun fromBuild(): SupabaseCredentials =
            SupabaseCredentials(
                SupabaseConfig.URL,
                SupabaseConfig.ANON_KEY,
                SupabaseConfig.GOOGLE_WEB_CLIENT_ID,
            )
    }
}
