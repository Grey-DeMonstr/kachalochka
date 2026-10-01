package monster.greyde.kachalochka.ui.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import kotlin.time.Clock

/** A choice kept in the account's profile, so it follows the account, and written as made. */
class ProfileChoice<T>(
    private val profiles: ProfileRepository,
    private val currentUser: CurrentUser,
    private val clock: Clock,
    private val sync: SyncTrigger,
    private val scope: CoroutineScope,
    private val stored: (Profile?) -> T,
    private val written: (Profile, T) -> Profile,
) {
    var current: T = stored(null)
        private set

    /** Counts choices, so a profile read before the latest one cannot undo it. */
    private var choices = 0
    private var writing: Job? = null

    suspend fun read(owner: UserId?) {
        val asked = choices
        val value = stored(profiles.forOwner(owner))
        if (asked == choices) current = value
    }

    fun choose(value: T) {
        current = value
        choices++
        val previous = writing
        writing =
            scope.launch {
                // One write at a time, so the last choice is the one the server keeps.
                previous?.join()
                val owner = currentUser.id()
                val now = clock.now()
                val profile = profiles.forOwner(owner) ?: Profile.new(owner, now)
                profiles.upsert(written(profile, value).copy(updatedAt = now))
                sync.request()
            }
    }
}
