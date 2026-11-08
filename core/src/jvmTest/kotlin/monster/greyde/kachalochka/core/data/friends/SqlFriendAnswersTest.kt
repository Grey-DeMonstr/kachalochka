package monster.greyde.kachalochka.core.data.friends

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.db.inMemoryDatabase
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class SqlFriendAnswersTest {
    private val answers = SqlFriendAnswers(inMemoryDatabase(), Dispatchers.Unconfined)
    private val ivan = UserId("11111111-1111-4111-8111-111111111111")
    private val misha = UserId("22222222-2222-4222-8222-222222222222")
    private val t0 = Instant.fromEpochSeconds(1_700_000_000)

    @Test
    fun each_account_reads_only_its_own_answer_to_a_request() =
        runTest {
            answers.keep(ivan, "mates", "[1]", t0)
            answers.keep(misha, "mates", "[2]", t0)
            answers.keep(ivan, "mates", "[3]", t0)

            assertEquals("[3]", answers.answer(ivan, "mates"))
            assertEquals("[2]", answers.answer(misha, "mates"))
            assertNull(answers.answer(ivan, "links"))
        }

    @Test
    fun keeping_an_answer_forgets_those_kept_more_than_a_month_before() =
        runTest {
            answers.keep(ivan, "old", "[1]", t0)
            answers.keep(ivan, "month", "[2]", t0 + 1.days)

            answers.keep(misha, "new", "[3]", t0 + 32.days)

            assertNull(answers.answer(ivan, "old"))
            assertEquals("[2]", answers.answer(ivan, "month"))
        }

    @Test
    fun only_the_listed_accounts_keep_their_answers() =
        runTest {
            answers.keep(ivan, "mates", "[1]", t0)
            answers.keep(misha, "mates", "[2]", t0)

            answers.keepOnly(listOf(misha))

            assertNull(answers.answer(ivan, "mates"))
            assertEquals("[2]", answers.answer(misha, "mates"))

            answers.keepOnly(emptyList())

            assertNull(answers.answer(misha, "mates"))
        }
}
