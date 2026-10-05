package monster.greyde.kachalochka.core.data.family

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.domain.family.Acceptance
import monster.greyde.kachalochka.core.domain.family.Family
import monster.greyde.kachalochka.core.domain.family.FamilyRepository
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private val IVAN = UserId("11111111-1111-4111-8111-111111111111")
private val MISHA = UserId("22222222-2222-4222-8222-222222222222")

class AccountFamilyReadsTest {
    @Test
    fun each_family_is_read_as_its_own_account_and_nobody_after() =
        runTest {
            lateinit var acting: ActingAccount
            val askedAs = mutableListOf<UserId?>()
            val reads =
                AccountFamilyReads { holder ->
                    acting = holder
                    object : FamilyRepository {
                        override suspend fun family(): Family {
                            askedAs += holder.owner
                            return Family(emptyList(), emptyList())
                        }

                        override suspend fun offer(): String = error("unused")

                        override suspend fun accept(code: String): Acceptance = error("unused")

                        override suspend fun end(
                            child: UserId,
                            guardian: UserId,
                        ) {
                            error("unused")
                        }
                    }
                }

            reads.familyOf(IVAN)
            reads.familyOf(MISHA)

            assertEquals(listOf<UserId?>(IVAN, MISHA), askedAs)
            assertNull(acting.owner)
        }

    @Test
    fun a_read_that_fails_reads_as_nobody_after() =
        runTest {
            lateinit var acting: ActingAccount
            val reads =
                AccountFamilyReads { holder ->
                    acting = holder
                    object : FamilyRepository {
                        override suspend fun family(): Family = error("no connection")

                        override suspend fun offer(): String = error("unused")

                        override suspend fun accept(code: String): Acceptance = error("unused")

                        override suspend fun end(
                            child: UserId,
                            guardian: UserId,
                        ) {
                            error("unused")
                        }
                    }
                }

            assertFailsWith<IllegalStateException> { reads.familyOf(IVAN) }

            assertNull(acting.owner)
        }
}
