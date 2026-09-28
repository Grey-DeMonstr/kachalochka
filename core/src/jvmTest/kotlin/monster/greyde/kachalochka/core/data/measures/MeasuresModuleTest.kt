package monster.greyde.kachalochka.core.data.measures

import monster.greyde.kachalochka.core.di.coreModule
import monster.greyde.kachalochka.core.di.corePlatformModule
import monster.greyde.kachalochka.core.domain.measures.MeasureRepository
import monster.greyde.kachalochka.core.domain.measures.MeasurementRepository
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs

class MeasuresModuleTest {
    private val koin = startKoin { modules(coreModule, corePlatformModule()) }.koin

    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun the_sql_module_binds_the_local_measure_repositories() {
        assertIs<LocalMeasureRepository>(koin.get<MeasureRepository>())
        assertIs<LocalMeasurementRepository>(koin.get<MeasurementRepository>())
    }
}
