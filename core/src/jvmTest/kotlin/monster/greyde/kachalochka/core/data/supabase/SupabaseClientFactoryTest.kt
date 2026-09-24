@file:OptIn(SupabaseInternal::class)

package monster.greyde.kachalochka.core.data.supabase

import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.data.identity.AccountTokens
import monster.greyde.kachalochka.core.data.identity.FIXTURE_EXPIRY
import monster.greyde.kachalochka.core.data.identity.FakeLiveTokens
import monster.greyde.kachalochka.core.data.identity.InMemoryAccountStorage
import monster.greyde.kachalochka.core.data.identity.PersistedAccountStore
import monster.greyde.kachalochka.core.data.identity.SessionRefresh
import monster.greyde.kachalochka.core.data.identity.accountSession
import monster.greyde.kachalochka.core.data.identity.clockAt
import monster.greyde.kachalochka.core.data.sync.SyncSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class SupabaseClientFactoryTest {
    private val ivan = accountSession("11111111-1111-4111-8111-111111111111", "Ivan")
    private val credentials = SupabaseCredentials("https://example.test", "anon-key")

    private suspend fun tokens(): AccountTokens {
        val store = PersistedAccountStore(InMemoryAccountStorage()).also { it.add(ivan) }
        val noRefresh =
            object : SessionRefresh {
                override suspend fun refresh(session: AccountSession): AccountSession? = null
            }
        return AccountTokens(store, FakeLiveTokens(), noRefresh, clockAt(FIXTURE_EXPIRY - 1.hours))
    }

    @Test
    fun the_sync_client_resolves_the_token_of_the_account_the_pass_is_on() =
        runTest {
            val session = SyncSession()
            val client = syncSupabaseClient(credentials, tokens(), session)

            session.owner = ivan.account.userId

            assertEquals(ivan.accessToken, client.accessToken?.invoke())
        }

    @Test
    fun the_sync_client_has_no_token_between_accounts() =
        runTest {
            val client = syncSupabaseClient(credentials, tokens(), SyncSession())

            assertNull(client.accessToken?.invoke())
        }

    @Test
    fun the_sync_client_leaves_the_live_session_to_the_ui_client() =
        runTest {
            val client = syncSupabaseClient(credentials, tokens(), SyncSession())

            assertNull(client.pluginManager.getPluginOrNull(Auth))
        }

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

    @Test
    fun the_client_keeps_its_own_session_storage_out_of_the_way() {
        val client = supabaseClient(SupabaseCredentials("https://example.supabase.co", "anon-key"))

        assertFalse(client.auth.config.autoLoadFromStorage)
        assertFalse(client.auth.config.autoSaveToStorage)
    }

    @Test
    fun coming_back_to_the_foreground_leaves_the_live_session_alone() {
        val client = supabaseClient(SupabaseCredentials("https://example.supabase.co", "anon-key"))

        assertFalse(client.auth.config.enableLifecycleCallbacks)
    }
}
