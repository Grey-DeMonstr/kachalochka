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

/**
 * The choices made for an account whose profile this device may not write, a managed child's,
 * kept until the app closes so that a reload of any screen does not undo them.
 */
class UnsavedChoices {
    private val values = mutableMapOf<Pair<UserId?, String>, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T> get(
        owner: UserId?,
        key: String,
    ): T? = values[owner to key] as T?

    fun put(
        owner: UserId?,
        key: String,
        value: Any,
    ) {
        values[owner to key] = value
    }
}

/** Whether the visit and the statistics group machines by tag, kept in `profile.group_by_tag`. */
fun groupByTagChoice(
    profiles: ProfileRepository,
    currentUser: CurrentUser,
    clock: Clock,
    sync: SyncTrigger,
    unsaved: UnsavedChoices,
    scope: CoroutineScope,
): ProfileChoice<Boolean> =
    ProfileChoice(
        profiles,
        currentUser,
        clock,
        sync,
        unsaved,
        scope,
        key = "group_by_tag",
        stored = { it?.groupByTag ?: false },
        written = { profile, grouped -> profile.copy(groupByTag = grouped) },
    )

/** A choice kept in the account's profile, so it follows the account, and written as made. */
class ProfileChoice<T : Any>(
    private val profiles: ProfileRepository,
    private val currentUser: CurrentUser,
    private val clock: Clock,
    private val sync: SyncTrigger,
    private val unsaved: UnsavedChoices,
    private val scope: CoroutineScope,
    private val key: String,
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
        val value = unsaved.get(owner, key) ?: stored(profiles.forOwner(owner))
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
                if (!currentUser.writesPrivateRows()) {
                    unsaved.put(owner, key, value)
                    return@launch
                }
                val now = clock.now()
                val profile = profiles.forOwner(owner) ?: Profile.new(owner, now)
                profiles.upsert(written(profile, value).copy(updatedAt = now))
                sync.request()
            }
    }
}
