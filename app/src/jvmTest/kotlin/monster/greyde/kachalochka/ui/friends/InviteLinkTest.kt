package monster.greyde.kachalochka.ui.friends

import monster.greyde.kachalochka.ui.strings.inEnglish
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InviteLinkTest {
    @Test
    fun an_invite_link_is_the_page_itself_with_the_code() {
        assertEquals(
            "https://example.test/kachalochka/?join=ABCD2345",
            inviteLink("https://example.test/kachalochka/?code=x#top", "ABCD2345"),
        )
    }

    @Test
    fun a_parent_s_link_is_the_page_itself_with_the_parent_s_code() {
        assertEquals(
            "https://example.test/kachalochka/?parent=PAPA2345",
            guardianLink("https://example.test/kachalochka/?code=x#top", "PAPA2345"),
        )
    }

    @Test
    fun the_join_parameter_is_read_as_a_code() {
        assertEquals("ABCD2345", joinCodeOf("https://example.test/k/?join=abcd2345"))
        assertEquals("ABCD2345", joinCodeOf("https://example.test/k/?code=x&join=ABCD2345#top"))
        assertNull(joinCodeOf("https://example.test/k/?join=ABCD"))
        assertNull(joinCodeOf("https://example.test/k/"))
        assertNull(joinCodeOf("https://example.test/k/?parent=ABCD2345"))
    }

    @Test
    fun the_parent_parameter_is_read_as_a_code() {
        assertEquals("PAPA2345", parentCodeOf("https://example.test/k/?parent=papa2345"))
        assertEquals("PAPA2345", parentCodeOf("https://example.test/k/?code=x&parent=PAPA2345"))
        assertNull(parentCodeOf("https://example.test/k/?parent=PAPA"))
        assertNull(parentCodeOf("https://example.test/k/?join=PAPA2345"))
    }

    @Test
    fun only_the_invite_parameters_leave_the_address() {
        assertEquals(
            "https://example.test/k/",
            withoutInviteCodes("https://example.test/k/?join=A"),
        )
        assertEquals(
            "https://example.test/k/",
            withoutInviteCodes("https://example.test/k/?parent=PAPA2345"),
        )
        assertEquals(
            "https://example.test/k/?code=x",
            withoutInviteCodes("https://example.test/k/?code=x&join=ABCD2345&parent=PAPA2345"),
        )
        assertEquals(
            "https://example.test/k/?code=x#top",
            withoutInviteCodes("https://example.test/k/?join=ABCD2345&code=x#top"),
        )
        val plain = "https://example.test/k/?code=x"
        assertEquals(plain, withoutInviteCodes(plain))
    }

    @Test
    fun the_invitation_is_written_in_english_too() =
        inEnglish {
            assertEquals(
                "Join the group \"Зал\" in Kachalochka\nCode: ABCD2345",
                inviteText(Invite("Join the group \"Зал\" in Kachalochka", "ABCD2345", null)),
            )
        }

    @Test
    fun the_shared_text_carries_the_link_only_when_there_is_one() {
        val call = "Вступай в группу «Зал» в Качалочке"
        assertEquals(
            "$call: https://example.test/k/?join=ABCD2345\nКод: ABCD2345",
            inviteText(Invite(call, "ABCD2345", "https://example.test/k/?join=ABCD2345")),
        )
        assertEquals("$call\nКод: ABCD2345", inviteText(Invite(call, "ABCD2345", null)))
    }
}
