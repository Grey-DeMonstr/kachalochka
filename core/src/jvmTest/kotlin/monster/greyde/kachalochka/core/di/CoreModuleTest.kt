package monster.greyde.kachalochka.core.di

import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
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
}
