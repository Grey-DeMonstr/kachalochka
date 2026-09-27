package monster.greyde.kachalochka.core.domain.friends

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class InviteCodeTest {
    @Test
    fun a_code_is_eight_characters_of_the_invite_alphabet_in_any_case() {
        assertEquals("ABCD2345", inviteCodeOf(" abcd2345 "))
        assertNull(inviteCodeOf("ABCD234"))
        assertNull(inviteCodeOf("ABCD23456"))
        assertNull(inviteCodeOf("ABCD2O45"))
        assertNull(inviteCodeOf("ABCD2145"))
    }

    @Test
    fun a_typed_code_is_upper_cased_and_keeps_only_code_characters() {
        assertEquals("ABC2", typedInviteCode("abc 2"))
        assertEquals("AB", typedInviteCode("a0b1"))
        assertEquals("ABCDEFGH", typedInviteCode("abcdefghjk"))
    }

    @Test
    fun a_group_id_is_a_lower_case_uuid_v4() {
        assertFailsWith<IllegalArgumentException> { GroupId("ABCD2345") }
    }
}
