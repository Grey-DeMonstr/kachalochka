package monster.greyde.kachalochka.core.data.gym

import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import monster.greyde.kachalochka.core.domain.identity.UserId

/** One filter for every owned table: an owner rule that differs per table is a leak waiting. */
internal fun PostgrestFilterBuilder.owned(owner: UserId?) {
    if (owner != null) {
        eq("user_id", owner.value)
    } else {
        exact("user_id", null)
    }
}
