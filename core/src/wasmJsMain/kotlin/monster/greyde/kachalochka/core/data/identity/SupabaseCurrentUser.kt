package monster.greyde.kachalochka.core.data.identity

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId

class SupabaseCurrentUser(
    private val client: SupabaseClient,
) : CurrentUser {
    override suspend fun id(): UserId? =
        client.auth
            .currentUserOrNull()
            ?.id
            ?.let(::UserId)
}
