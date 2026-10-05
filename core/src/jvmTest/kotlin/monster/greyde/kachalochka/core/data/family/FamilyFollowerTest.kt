package monster.greyde.kachalochka.core.data.family

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountKind
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.data.identity.InMemoryAccountStorage
import monster.greyde.kachalochka.core.data.identity.LiveSession
import monster.greyde.kachalochka.core.data.identity.PersistedAccountStore
import monster.greyde.kachalochka.core.data.identity.SessionActivation
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

private class RecordingActivation : SessionActivation {
    val activated = mutableListOf<UserId>()
    var refusal: Exception? = null

    override suspend fun activate(session: AccountSession) {
        refusal?.let { throw it }
        activated += session.account.userId
    }

    override suspend fun clear() = Unit
}

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
    private val kolya = FamilyMember(UserId("77777777-7777-4777-8777-777777777777"), "Kolya")
    private val store = PersistedAccountStore(InMemoryAccountStorage())
    private val reads = ScriptedReads()
    private val purged = mutableListOf<UserId>()
    private var purgeGate: CompletableDeferred<Unit>? = null
    private var purgeFailure: Exception? = null
    private var whilePurging: suspend () -> Unit = {}
    private val sessions = RecordingActivation()
    private val live = LiveSession(sessions, { null }, store)
    private val follower =
        FamilyFollower(store, reads, live) {
            whilePurging()
            purgeGate?.await()
            purgeFailure?.let { failure -> throw failure }
            purged += it
        }

    // As Accounts.switchTo: the acting session goes live before the account becomes active.
    private suspend fun switchTo(id: UserId) {
        store.actingSessionOf(id)?.let { live.activate(it) }
        store.switch(id)
    }

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
    fun a_parent_signed_in_during_the_reads_keeps_a_child_its_family_may_name() =
        runTest {
            store.add(ivan)
            reads.children(ivan.account.userId, sasha)
            follower.follow()
            reads.families.remove(ivan.account.userId)
            reads.children(misha.account.userId, sasha)
            reads.whileReading = { store.add(misha) }

            follower.follow()

            assertEquals(listOf(sasha.userId), managedAccounts().map { it.userId })
            assertTrue(purged.isEmpty())
        }

    @Test
    fun the_active_child_moving_to_its_other_parent_makes_that_parent_s_session_live() =
        runTest {
            store.add(ivan)
            store.add(misha)
            reads.children(ivan.account.userId, sasha)
            reads.children(misha.account.userId, sasha)
            follower.follow()
            switchTo(sasha.userId)
            reads.families.remove(ivan.account.userId)

            follower.follow()

            assertEquals(sasha.userId, store.activeId.value)
            assertEquals(misha.account.userId, live.liveId)
        }

    @Test
    fun the_active_child_keeping_its_parent_leaves_the_live_session_alone() =
        runTest {
            store.add(ivan)
            store.add(misha)
            reads.children(ivan.account.userId, sasha)
            reads.children(misha.account.userId, sasha)
            follower.follow()
            switchTo(sasha.userId)

            follower.follow()

            assertEquals(listOf(ivan.account.userId), sessions.activated)
        }

    @Test
    fun a_refused_activation_of_the_active_child_s_new_parent_is_retried_by_the_next_follow() =
        runTest {
            store.add(ivan)
            store.add(misha)
            reads.children(ivan.account.userId, sasha)
            reads.children(misha.account.userId, sasha)
            follower.follow()
            switchTo(sasha.userId)
            reads.families.remove(ivan.account.userId)
            sessions.refusal = IllegalStateException("no connection")
            runCatching { follower.follow() }
            assertEquals(ivan.account.userId, live.liveId)
            sessions.refusal = null

            follower.follow()

            assertEquals(misha.account.userId, live.liveId)
        }

    @Test
    fun a_follow_cancelled_while_purging_never_leaves_the_child_unlisted_with_its_rows() =
        runTest {
            store.add(ivan)
            reads.children(ivan.account.userId, sasha)
            follower.follow()
            reads.families.remove(ivan.account.userId)
            val purging = CompletableDeferred<Unit>()
            purgeGate = purging
            val following = launch { follower.follow() }
            runCurrent()

            following.cancel()
            runCurrent()
            assertEquals(listOf(sasha.userId), managedAccounts().map { it.userId })

            purging.complete(Unit)
            runCurrent()
            assertEquals(listOf(sasha.userId), purged)
            assertTrue(managedAccounts().isEmpty())
        }

    @Test
    fun a_failed_purge_keeps_the_child_listed_until_a_follow_purges_it() =
        runTest {
            store.add(ivan)
            reads.children(ivan.account.userId, sasha)
            follower.follow()
            reads.families.remove(ivan.account.userId)
            purgeFailure = IllegalStateException("disk full")
            runCatching { follower.follow() }
            assertEquals(listOf(sasha.userId), managedAccounts().map { it.userId })
            purgeFailure = null

            follower.follow()

            assertEquals(listOf(sasha.userId), purged)
            assertTrue(managedAccounts().isEmpty())
        }

    @Test
    fun a_parent_signed_in_during_the_purges_keeps_the_children_not_yet_purged() =
        runTest {
            store.add(ivan)
            reads.children(ivan.account.userId, sasha, kolya)
            follower.follow()
            reads.families.remove(ivan.account.userId)
            whilePurging = { store.add(misha) }

            follower.follow()

            assertEquals(1, purged.size)
            assertEquals(
                listOf(sasha.userId, kolya.userId) - purged.toSet(),
                managedAccounts().map { it.userId },
            )
        }

    @Test
    fun the_pass_after_a_follow_has_the_child_linked_since_the_last_one() =
        runTest {
            store.add(ivan)
            reads.children(ivan.account.userId, sasha)

            val listed = follower.followThen { managedAccounts().map { it.userId } }

            assertEquals(listOf(sasha.userId), listed)
        }

    @Test
    fun a_follow_waits_for_the_pass_in_progress_before_purging() =
        runTest {
            store.add(ivan)
            reads.children(ivan.account.userId, sasha)
            val passing = CompletableDeferred<Unit>()
            launch { follower.followThen { passing.await() } }
            runCurrent()
            reads.families.remove(ivan.account.userId)

            launch { follower.follow() }
            runCurrent()
            assertTrue(purged.isEmpty())

            passing.complete(Unit)
            runCurrent()
            assertEquals(listOf(sasha.userId), purged)
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
