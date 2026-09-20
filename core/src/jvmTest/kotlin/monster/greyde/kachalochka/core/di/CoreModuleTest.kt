package monster.greyde.kachalochka.core.di

import monster.greyde.kachalochka.core.data.identity.AccountStore
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull

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
}
