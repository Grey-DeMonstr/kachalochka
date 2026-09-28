package monster.greyde.kachalochka.ui.friends

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
    fun the_join_parameter_is_read_as_a_code() {
        assertEquals("ABCD2345", joinCodeOf("https://example.test/k/?join=abcd2345"))
        assertEquals("ABCD2345", joinCodeOf("https://example.test/k/?code=x&join=ABCD2345#top"))
        assertNull(joinCodeOf("https://example.test/k/?join=ABCD"))
        assertNull(joinCodeOf("https://example.test/k/"))
    }

    @Test
    fun only_the_join_parameter_leaves_the_address() {
        assertEquals("https://example.test/k/", withoutJoinCode("https://example.test/k/?join=A"))
        assertEquals(
            "https://example.test/k/?code=x",
            withoutJoinCode("https://example.test/k/?code=x&join=ABCD2345"),
        )
        assertEquals(
            "https://example.test/k/?code=x#top",
            withoutJoinCode("https://example.test/k/?join=ABCD2345&code=x#top"),
        )
        val plain = "https://example.test/k/?code=x"
        assertEquals(plain, withoutJoinCode(plain))
    }

    @Test
    fun the_shared_text_carries_the_link_only_when_there_is_one() {
        assertEquals(
            "Вступай в группу «Зал» в Качалочке: https://example.test/k/?join=ABCD2345\n" +
                "Код: ABCD2345",
            inviteText(Invite("Зал", "ABCD2345", "https://example.test/k/?join=ABCD2345")),
        )
        assertEquals(
            "Вступай в группу «Зал» в Качалочке\nКод: ABCD2345",
            inviteText(Invite("Зал", "ABCD2345", null)),
        )
    }
}
