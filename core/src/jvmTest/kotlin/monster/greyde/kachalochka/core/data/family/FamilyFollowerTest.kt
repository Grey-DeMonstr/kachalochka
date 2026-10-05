package monster.greyde.kachalochka.core.data.family

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountKind
import monster.greyde.kachalochka.core.data.identity.InMemoryAccountStorage
import monster.greyde.kachalochka.core.data.identity.PersistedAccountStore
import monster.greyde.kachalochka.core.data.identity.accountSession
import monster.greyde.kachalochka.core.domain.family.Family
import monster.greyde.kachalochka.core.domain.family.FamilyMember
import monster.greyde.kachalochka.core.domain.gym.PhotoId
import monster.greyde.kachalochka.core.domain.identity.Avatar
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class ScriptedReads : FamilyReads {
    val families = mutableMapOf<UserId, Family>()
    val failing = mutableSetOf<UserId>()
    var whileReading: suspend () -> Unit = {}

    override suspend fun familyOf(owner: UserId): Family {
        whileReading()
        if (owner in failing) error("no connection")
        return families[owner] ?: Family(emptyList(), emptyList())
    }
}

class FamilyFollowerTest {
    private val ivan = accountSession("11111111-1111-4111-8111-111111111111", "Ivan")
    private val misha = accountSession("22222222-2222-4222-8222-222222222222", "Misha")
    private val photo = PhotoId.random()
    private val sasha =
        FamilyMember(
            UserId("66666666-6666-4666-8666-666666666666"),
            "Sasha",
            Avatar(photo, "https://example.test/s.png"),
        )
    private val store = PersistedAccountStore(InMemoryAccountStorage())
    private val reads = ScriptedReads()
    private val purged = mutableListOf<UserId>()
    private val follower = FamilyFollower(store, reads) { purged += it }

    private fun ScriptedReads.children(
        guardian: UserId,
        vararg children: FamilyMember,
    ) {
        families[guardian] = Family(children.toList(), emptyList())
    }

    private fun managedAccounts() = store.accounts.value.filter { it.isManaged }

    @Test
    fun a_child_of_a_signed_in_account_joins_the_device_acting_through_it() =
        runTest {
            store.add(ivan)
            reads.children(ivan.account.userId, sasha)

            assertTrue(follower.follow())

            assertEquals(
                listOf(
                    ivan.account,
                    Account(
                        sasha.userId,
                        "",
                        "Sasha",
                        "https://example.test/s.png",
                        AccountKind.Managed(ivan.account.userId, photo),
                    ),
                ),
                store.accounts.value,
            )
        }

    @Test
    fun a_child_whose_link_ended_leaves_the_device_with_its_rows() =
        runTest {
            store.add(ivan)
            reads.children(ivan.account.userId, sasha)
            follower.follow()
            reads.families.remove(ivan.account.userId)

            assertTrue(follower.follow())

            assertEquals(listOf(ivan.account), store.accounts.value)
            assertEquals(listOf(sasha.userId), purged)
        }

    @Test
    fun a_read_that_fails_keeps_the_child_and_its_rows() =
        runTest {
            store.add(ivan)
            reads.children(ivan.account.userId, sasha)
            follower.follow()
            reads.failing += ivan.account.userId

            assertFalse(follower.follow())

            assertEquals(listOf(sasha.userId), managedAccounts().map { it.userId })
            assertTrue(purged.isEmpty())
        }

    @Test
    fun a_child_let_go_by_one_parent_stays_while_the_other_parent_s_read_fails() =
        runTest {
            store.add(ivan)
            store.add(misha)
            reads.children(ivan.account.userId, sasha)
            reads.children(misha.account.userId, sasha)
            follower.follow()
            reads.families.remove(ivan.account.userId)
            reads.failing += misha.account.userId

            assertFalse(follower.follow())

            assertEquals(listOf(sasha.userId), managedAccounts().map { it.userId })
            assertTrue(purged.isEmpty())
        }

    @Test
    fun a_child_keeps_its_parent_while_that_parent_s_read_fails() =
        runTest {
            store.add(ivan)
            store.add(misha)
            reads.children(ivan.account.userId, sasha)
            reads.children(misha.account.userId, sasha)
            follower.follow()
            reads.failing += ivan.account.userId

            assertFalse(follower.follow())

            assertEquals(listOf(ivan.account.userId), managedAccounts().map { it.guardianId })
        }

    @Test
    fun a_child_signed_in_with_google_during_the_reads_keeps_its_rows() =
        runTest {
            store.add(ivan)
            reads.children(ivan.account.userId, sasha)
            follower.follow()
            reads.families.remove(ivan.account.userId)
            reads.whileReading = { store.add(accountSession(sasha.userId.value, "Sasha")) }

            follower.follow()

            assertTrue(purged.isEmpty())
        }

    @Test
    fun a_child_of_two_parents_here_is_one_account_under_the_first() =
        runTest {
            store.add(ivan)
            store.add(misha)
            reads.children(ivan.account.userId, sasha)
            reads.children(misha.account.userId, sasha)

            follower.follow()

            assertEquals(listOf(ivan.account.userId), managedAccounts().map { it.guardianId })
        }

    @Test
    fun a_child_the_first_parent_let_go_moves_to_the_other_with_its_rows() =
        runTest {
            store.add(ivan)
            store.add(misha)
            reads.children(ivan.account.userId, sasha)
            follower.follow()
            reads.families.remove(ivan.account.userId)
            reads.children(misha.account.userId, sasha)

            assertTrue(follower.follow())

            assertEquals(listOf(misha.account.userId), managedAccounts().map { it.guardianId })
            assertTrue(purged.isEmpty())
        }

    @Test
    fun a_child_whose_acting_parent_signed_out_comes_back_under_the_other() =
        runTest {
            store.add(ivan)
            store.add(misha)
            reads.children(ivan.account.userId, sasha)
            reads.children(misha.account.userId, sasha)
            follower.follow()
            store.remove(ivan.account.userId)

            assertTrue(follower.follow())

            assertEquals(listOf(misha.account.userId), managedAccounts().map { it.guardianId })
            assertTrue(purged.isEmpty())
        }

    @Test
    fun a_child_signed_in_with_google_here_stays_a_google_account_and_keeps_its_rows() =
        runTest {
            val sashaSession = accountSession(sasha.userId.value, "Sasha")
            store.add(ivan)
            store.add(sashaSession)
            reads.children(ivan.account.userId, sasha)
            follower.follow()
            reads.families.remove(ivan.account.userId)

            follower.follow()

            assertEquals(listOf(ivan.account, sashaSession.account), store.accounts.value)
            assertTrue(purged.isEmpty())
        }

    @Test
    fun the_active_child_whose_link_ended_leaves_its_parent_active() =
        runTest {
            store.add(ivan)
            store.add(misha)
            reads.children(misha.account.userId, sasha)
            follower.follow()
            store.switch(sasha.userId)
            reads.families.remove(misha.account.userId)

            follower.follow()

            assertEquals(misha.account.userId, store.activeId.value)
        }

    @Test
    fun nothing_changed_reports_no_change() =
        runTest {
            store.add(ivan)
            reads.children(ivan.account.userId, sasha)
            follower.follow()

            assertFalse(follower.follow())
        }
}
