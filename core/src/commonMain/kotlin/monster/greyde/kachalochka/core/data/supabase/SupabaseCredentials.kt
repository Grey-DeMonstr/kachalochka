package monster.greyde.kachalochka.core.data.supabase

/** Absent credentials are normal: the build must work on a machine with no Supabase project. */
data class SupabaseCredentials(
    val url: String,
    val anonKey: String,
) {
    val isConfigured: Boolean = url.isNotBlank() && anonKey.isNotBlank()

    companion object {
        fun fromBuild(): SupabaseCredentials =
            SupabaseCredentials(SupabaseConfig.URL, SupabaseConfig.ANON_KEY)
    }
}
