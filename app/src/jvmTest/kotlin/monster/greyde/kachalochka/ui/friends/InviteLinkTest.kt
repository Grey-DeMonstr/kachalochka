package monster.greyde.kachalochka.ui.friends

import kotlin.test.Test
import kotlin.test.assertEquals

class InviteLinkTest {
    @Test
    fun an_invite_link_is_the_page_itself_with_the_code() {
        assertEquals(
            "https://example.test/kachalochka/?join=ABCD2345",
            inviteLink("https://example.test/kachalochka/?code=x#top", "ABCD2345"),
        )
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
