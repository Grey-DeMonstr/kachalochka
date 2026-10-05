package monster.greyde.kachalochka.core.data.identity

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import monster.greyde.kachalochka.core.data.gym.PHOTO_BUCKET
import monster.greyde.kachalochka.core.domain.identity.UserId

/** Deletes an account and everything it recorded on the server; acts as that account. */
fun interface AccountServer {
    suspend fun deleteEverything(owner: UserId)
}

/** Removes an owner's rows from the device; the web keeps none. */
fun interface OwnedRowsPurge {
    suspend fun purge(owner: UserId)
}

/**
 * The server goes first: offline it throws and the device keeps everything. Once the server has
 * forgotten the account, the device forgets it too and signs it out.
 */
class AccountDeletion(
    private val server: AccountServer,
    private val purge: OwnedRowsPurge,
    private val accounts: Accounts,
) {
    suspend fun delete(owner: UserId) {
        val all = accounts.accounts.value
        // A child's requests carry its guardian's token, so deleting it would delete the guardian.
        check(all.none { it.userId == owner && it.guardianId != null }) {
            "A managed account cannot be deleted from the device"
        }
        // Read first: signing out takes them off the account list.
        val children = all.filter { it.guardianId == owner }.map { it.userId }
        server.deleteEverything(owner)
        purge.purge(owner)
        children.forEach { purge.purge(it) }
        accounts.signOut(owner)
    }
}

private const val LIST_PAGE = 100

/**
 * `delete_my_account` removes the user, and every owned row cascades from it. Storage keeps its
 * objects apart from the rows, so the photos go through its API afterwards, on the token issued
 * before: a failed call never leaves rows naming photos that are gone. [clientFor] acts as the
 * given account, whoever becomes active meanwhile.
 */
class SupabaseAccountServer(
    private val clientFor: (UserId) -> SupabaseClient,
) : AccountServer {
    override suspend fun deleteEverything(owner: UserId) {
        val client = clientFor(owner)
        client.postgrest.rpc("delete_my_account")
        val bucket = client.storage.from(PHOTO_BUCKET)
        var previous: List<String>? = null
        while (true) {
            val paths =
                bucket
                    .list(owner.value) { limit = LIST_PAGE }
                    .map { "${owner.value}/${it.name}" }
            // A page that comes back unchanged was not deleted, and would come back forever.
            if (paths.isEmpty() || paths == previous) break
            bucket.delete(paths)
            previous = paths
        }
    }
}
