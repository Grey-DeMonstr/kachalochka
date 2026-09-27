package monster.greyde.kachalochka.core.domain.friends

/** The alphabet `new_invite_code` in `0007_groups.sql` draws from: no 0/O or 1/I to confuse. */
const val INVITE_CODE_ALPHABET: String = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
const val INVITE_CODE_LENGTH: Int = 8

/** The code in [text] as the server stores it, or null when [text] cannot be one. */
fun inviteCodeOf(text: String): String? =
    text
        .trim()
        .uppercase()
        .takeIf { code ->
            code.length == INVITE_CODE_LENGTH && code.all { it in INVITE_CODE_ALPHABET }
        }

fun typedInviteCode(text: String): String =
    text
        .uppercase()
        .filter { it in INVITE_CODE_ALPHABET }
        .take(INVITE_CODE_LENGTH)
