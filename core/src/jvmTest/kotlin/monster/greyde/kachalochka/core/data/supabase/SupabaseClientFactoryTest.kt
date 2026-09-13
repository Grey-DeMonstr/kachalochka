package monster.greyde.kachalochka.core.data.supabase

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SupabaseClientFactoryTest {
    @Test
    fun configured_credentials_build_a_client() {
        assertNotNull(
            supabaseClient(SupabaseCredentials("https://example.supabase.co", "anon-key")),
        )
    }

    @Test
    fun unconfigured_credentials_fail_with_a_message_naming_the_settings() {
        val failure =
            assertFailsWith<IllegalStateException> { supabaseClient(SupabaseCredentials("", "")) }

        assertTrue("SUPABASE_URL" in failure.message.orEmpty(), failure.message.orEmpty())
        assertTrue("SUPABASE_ANON_KEY" in failure.message.orEmpty(), failure.message.orEmpty())
    }
}
