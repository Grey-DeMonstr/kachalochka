package monster.greyde.kachalochka.ui.friends

import monster.greyde.kachalochka.core.domain.friends.inviteCodeOf
import monster.greyde.kachalochka.ui.account.signInReturnAddress

/** The page itself with the code: GitHub Pages serves the app under a path. */
fun inviteLink(
    pageAddress: String,
    code: String,
): String = signInReturnAddress(pageAddress) + "?join=" + code

fun joinCodeOf(pageAddress: String): String? =
    queryOf(pageAddress)
        .firstOrNull { it.substringBefore('=') == "join" }
        ?.substringAfter('=', "")
        ?.let(::inviteCodeOf)

/** supabase-kt's own `code` parameter must still be there when its Auth plugin starts. */
fun withoutJoinCode(pageAddress: String): String {
    val fragment = pageAddress.substringAfter('#', "").let { if (it.isEmpty()) "" else "#$it" }
    val base = pageAddress.substringBefore('#').substringBefore('?')
    val kept = queryOf(pageAddress).filterNot { it.substringBefore('=') == "join" }
    val query = if (kept.isEmpty()) "" else kept.joinToString("&", prefix = "?")
    return base + query + fragment
}

private fun queryOf(pageAddress: String): List<String> =
    pageAddress
        .substringBefore('#')
        .substringAfter('?', "")
        .split('&')
        .filter { it.isNotEmpty() }

data class Invite(
    val groupName: String,
    val code: String,
    val link: String?,
)

fun inviteText(invite: Invite): String {
    val call = "Вступай в группу «${invite.groupName}» в Качалочке"
    val first = invite.link?.let { "$call: $it" } ?: call
    return "$first\nКод: ${invite.code}"
}
