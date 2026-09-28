package monster.greyde.kachalochka.ui.friends

import monster.greyde.kachalochka.ui.account.signInReturnAddress

/** The page itself with the code: GitHub Pages serves the app under a path. */
fun inviteLink(
    pageAddress: String,
    code: String,
): String = signInReturnAddress(pageAddress) + "?join=" + code

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
