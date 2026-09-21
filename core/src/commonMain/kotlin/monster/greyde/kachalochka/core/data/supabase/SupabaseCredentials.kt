package monster.greyde.kachalochka.core.data.supabase

/** Absent credentials are normal: the build must work on a machine with no Supabase project. */
data class SupabaseCredentials(
    val url: String,
    val anonKey: String,
    // The id of the Google client Supabase itself trusts, which the Android sign-in must name.
    val googleWebClientId: String = "",
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
