package monster.greyde.kachalochka.core.data.supabase

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupabaseCredentialsTest {
    @Test
    fun credentials_with_both_values_are_configured() {
        val credentials = SupabaseCredentials("https://example.supabase.co", "anon-key")
        assertTrue(credentials.isConfigured)
    }

    @Test
    fun credentials_missing_the_key_are_not_configured() {
        assertFalse(SupabaseCredentials("https://example.supabase.co", "").isConfigured)
    }

    @Test
    fun credentials_missing_the_url_are_not_configured() {
        assertFalse(SupabaseCredentials("", "anon-key").isConfigured)
    }

    @Test
    fun blank_values_are_not_configured() {
        assertFalse(SupabaseCredentials("   ", "  ").isConfigured)
    }

    @Test
    fun a_google_id_token_sign_in_needs_the_web_client_id_too() {
        val configured = SupabaseCredentials("https://example.supabase.co", "anon-key")
        assertFalse(configured.canSignInWithGoogleId)
        assertTrue(configured.copy(googleWebClientId = "client-id").canSignInWithGoogleId)
    }

    @Test
    fun a_web_client_id_alone_signs_nobody_in() {
        assertFalse(SupabaseCredentials("", "", "client-id").canSignInWithGoogleId)
    }
}
