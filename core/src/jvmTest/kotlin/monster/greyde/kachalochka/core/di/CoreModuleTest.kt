package monster.greyde.kachalochka.core.di

import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.data.identity.AccountStore
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.identity.GoogleSignIn
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull

private object UnusedSignIn : GoogleSignIn {
    override suspend fun signIn(): AccountSession = error("the graph is only built, never used")
}

class CoreModuleTest {
    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun the_core_module_provides_supabase_credentials() {
        val koin = startKoin { modules(coreModule) }.koin

        assertNotNull(koin.get<SupabaseCredentials>())
    }

    @Test
    fun the_graph_resolves_the_account_store_and_the_current_user() {
        val koin =
            koinApplication {
                modules(coreModule, corePlatformModule())
            }.koin
        assertNotNull(koin.get<AccountStore>())
        assertNotNull(koin.get<CurrentUser>())
    }

    /** A clone with no `local.properties` has to reach the screens and work anonymously (§5.4). */
    @Test
    fun the_account_graph_is_built_without_supabase_credentials() {
        val koin =
            koinApplication {
                modules(
                    coreModule,
                    corePlatformModule(),
                    module {
                        single { SupabaseCredentials("", "") }
                        single<GoogleSignIn> { UnusedSignIn }
                    },
                )
            }.koin

        assertNotNull(koin.get<Accounts>())
    }
}
