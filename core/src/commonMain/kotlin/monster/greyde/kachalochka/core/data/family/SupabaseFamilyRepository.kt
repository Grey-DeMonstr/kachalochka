package monster.greyde.kachalochka.core.data.family

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import monster.greyde.kachalochka.core.domain.family.Acceptance
import monster.greyde.kachalochka.core.domain.family.Family
import monster.greyde.kachalochka.core.domain.family.FamilyMember
import monster.greyde.kachalochka.core.domain.family.FamilyRepository
import monster.greyde.kachalochka.core.domain.identity.UserId

// accept_guardian raises these; PostgREST answers PT404 with a 404. See 0023_guardians.sql.
private const val UNKNOWN_CODE = "PT404"
private const val OWN_CODE = "P0001"
private const val NOT_FOUND = 404

/** The definer functions of 0023_guardians.sql, called as the client's account. */
class SupabaseFamilyRepository(
    private val client: Lazy<SupabaseClient>,
) : FamilyRepository {
    private val postgrest: Postgrest get() = client.value.postgrest

    override suspend fun family(): Family {
        val (children, guardians) =
            postgrest
                .rpc("my_family")
                .decodeList<FamilyRow>()
                .partition { it.relation == CHILD_RELATION }
        return Family(children.byName(), guardians.byName())
    }

    override suspend fun offer(): String = postgrest.rpc("offer_guardianship").decodeAs<String>()

    // Block-bodied with early returns, as SupabaseFriendsRepository.join is for wasm-opt.
    override suspend fun accept(code: String): Acceptance {
        val accepted =
            try {
                postgrest.rpc("accept_guardian", buildJsonObject { put("code", code) })
            } catch (refused: PostgrestRestException) {
                if (refused.code == UNKNOWN_CODE || refused.statusCode == NOT_FOUND) {
                    return Acceptance.UnknownCode
                }
                if (refused.code == OWN_CODE) return Acceptance.OwnCode
                throw refused
            }
        return Acceptance.Linked(UserId(accepted.decodeAs<String>().lowercase()))
    }

    override suspend fun end(
        child: UserId,
        guardian: UserId,
    ) {
        postgrest.rpc(
            "end_guardianship",
            buildJsonObject {
                put("child", child.value)
                put("guardian", guardian.value)
            },
        )
    }

    private fun List<FamilyRow>.byName(): List<FamilyMember> =
        map { it.toMember() }.sortedBy { it.displayName.lowercase() }
}
