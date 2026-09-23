package monster.greyde.kachalochka.ui.account

import kotlin.test.Test
import kotlin.test.assertEquals

class SignInReturnAddressTest {
    @Test
    fun a_page_under_a_path_returns_to_that_path() {
        assertEquals(
            "https://example.test/kachalochka/",
            signInReturnAddress("https://example.test/kachalochka/"),
        )
    }

    @Test
    fun the_query_is_left_behind() {
        assertEquals(
            "https://example.test/kachalochka/",
            signInReturnAddress("https://example.test/kachalochka/?code=abc"),
        )
    }

    @Test
    fun the_fragment_is_left_behind() {
        assertEquals(
            "http://localhost:8080/",
            signInReturnAddress("http://localhost:8080/#access_token=abc"),
        )
    }
}
