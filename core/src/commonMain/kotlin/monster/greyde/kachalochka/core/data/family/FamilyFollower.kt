package monster.greyde.kachalochka.core.data.family

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountKind
import monster.greyde.kachalochka.core.data.identity.AccountStore
import monster.greyde.kachalochka.core.data.identity.OwnedRowsPurge
import monster.greyde.kachalochka.core.data.identity.SessionActivation
import monster.greyde.kachalochka.core.domain.family.FamilyMember
import monster.greyde.kachalochka.core.domain.identity.UserId

/**
 * Keeps the device's managed accounts in step with the guardian links of its Google accounts.
 * A family that cannot be read may still name any child, so while one is unread no child leaves
 * and none changes guardian away from it. Otherwise a child no family names any more leaves the
 * device with its rows, which the device could never sync again.
 */
class FamilyFollower(
    private val store: AccountStore,
    private val reads: FamilyReads,
    private val sessions: SessionActivation,
    private val purge: OwnedRowsPurge,
) {
    private val lock = Mutex()

    /** True when a child joined, left or changed guardian. */
    suspend fun follow(): Boolean = lock.withLock { followLocked() }

    /**
     * Follows, then runs [pass] before any other follow starts: one meanwhile could purge a child
     * whose rows [pass] then pulls back.
     */
    suspend fun <T> followThen(pass: suspend () -> T): T =
        lock.withLock {
            followLocked()
            pass()
        }

    private suspend fun followLocked(): Boolean {
        val listed = store.accounts.value
        val signedIn = listed.filterNot { it.isManaged }
        val signedInIds = signedIn.map { it.userId }.toSet()
        val before = listed.filter { it.isManaged }
        val children = signedIn.associate { it.userId to childrenOf(it.userId) }
        val after = linkedMapOf<UserId, Account>()
        before
            .filter { it.guardianId?.let(children::get) == null }
            .forEach { after[it.userId] = it }
        for (guardian in signedIn) {
            children[guardian.userId]?.forEach { child ->
                if (child.userId !in signedInIds && child.userId !in after) {
                    after[child.userId] = managed(child, guardian.userId)
                }
            }
        }
        // An account signed in during the reads has a family nobody read.
        val signedInMeanwhile =
            store.accounts.value.any { !it.isManaged && it.userId !in signedInIds }
        val everyFamilyRead = !signedInMeanwhile && children.values.none { it == null }
        if (!everyFamilyRead) {
            before.filter { it.userId !in after }.forEach { after[it.userId] = it }
        }
        store.setManaged(after.values.toList())
        val signedInNow =
            store.accounts.value
                .filterNot { it.isManaged }
                .map { it.userId }
        before
            .map { it.userId }
            .filter { it !in after && it !in signedInNow }
            .forEach { purge.purge(it) }
        keepActingSessionLive(before)
        return before.map { it.userId to it.guardianId }.toSet() !=
            after.values.map { it.userId to it.guardianId }.toSet()
    }

    // The live session acts for the active account, so a child that changed guardian needs the
    // new guardian's.
    private suspend fun keepActingSessionLive(before: List<Account>) {
        val active = store.activeId.value ?: return
        val was = before.firstOrNull { it.userId == active }?.guardianId ?: return
        val now =
            store.accounts.value
                .firstOrNull { it.userId == active }
                ?.guardianId ?: return
        if (now != was) store.actingSessionOf(active)?.let { sessions.activate(it) }
    }

    // Null, unlike an empty list, says nothing about the family.
    private suspend fun childrenOf(owner: UserId): List<FamilyMember>? =
        try {
            reads.familyOf(owner).children
        } catch (stopped: CancellationException) {
            throw stopped
        } catch (failed: Exception) {
            null
        }

    private fun managed(
        child: FamilyMember,
        guardian: UserId,
    ) = Account(
        child.userId,
        "",
        child.displayName,
        child.avatar.picture,
        AccountKind.Managed(guardian, child.avatar.photo),
    )
}
