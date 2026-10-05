package monster.greyde.kachalochka.ui.friends

import monster.greyde.kachalochka.core.domain.friends.inviteCodeOf
import monster.greyde.kachalochka.ui.account.signInReturnAddress
import monster.greyde.kachalochka.ui.strings.AppStrings

private const val JOIN = "join"
private const val PARENT = "parent"

/** The page itself with the code: GitHub Pages serves the app under a path. */
fun inviteLink(
    pageAddress: String,
    code: String,
): String = signInReturnAddress(pageAddress) + "?$JOIN=" + code

fun guardianLink(
    pageAddress: String,
    code: String,
): String = signInReturnAddress(pageAddress) + "?$PARENT=" + code

fun joinCodeOf(pageAddress: String): String? = codeOf(pageAddress, JOIN)

fun parentCodeOf(pageAddress: String): String? = codeOf(pageAddress, PARENT)

/** supabase-kt's own `code` parameter must still be there when its Auth plugin starts. */
fun withoutInviteCodes(pageAddress: String): String {
    val fragment = pageAddress.substringAfter('#', "").let { if (it.isEmpty()) "" else "#$it" }
    val base = pageAddress.substringBefore('#').substringBefore('?')
    val kept = queryOf(pageAddress).filterNot { it.substringBefore('=') in setOf(JOIN, PARENT) }
    val query = if (kept.isEmpty()) "" else kept.joinToString("&", prefix = "?")
    return base + query + fragment
}

private fun codeOf(
    pageAddress: String,
    parameter: String,
): String? =
    queryOf(pageAddress)
        .firstOrNull { it.substringBefore('=') == parameter }
        ?.substringAfter('=', "")
        ?.let(::inviteCodeOf)

private fun queryOf(pageAddress: String): List<String> =
    pageAddress
        .substringBefore('#')
        .substringAfter('?', "")
        .split('&')
        .filter { it.isNotEmpty() }

/** [call] asks the reader to come in; the code follows it, after the link when there is one. */
data class Invite(
    val call: String,
    val code: String,
    val link: String?,
)

fun inviteText(invite: Invite): String {
    val first = invite.link?.let { "${invite.call}: $it" } ?: invite.call
    return "$first\n${AppStrings.current.codeLine(invite.code)}"
}
