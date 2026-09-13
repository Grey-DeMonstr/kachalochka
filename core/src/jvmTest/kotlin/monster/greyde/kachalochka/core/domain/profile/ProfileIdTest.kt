package monster.greyde.kachalochka.core.domain.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProfileIdTest {
    @Test
    fun a_uuid_v4_is_accepted() {
        val id = ProfileId("9b1f0c3e-0000-4000-8000-000000000001")

        assertEquals("9b1f0c3e-0000-4000-8000-000000000001", id.value)
    }

    @Test
    fun a_string_that_is_not_a_uuid_is_rejected() {
        assertFailsWith<IllegalArgumentException> { ProfileId("no-such-profile") }
    }

    @Test
    fun a_uuid_of_another_version_is_rejected() {
        assertFailsWith<IllegalArgumentException> {
            ProfileId(
                "9b1f0c3e-0000-1000-8000-000000000001",
            )
        }
    }

    @Test
    fun an_upper_case_uuid_is_rejected() {
        assertFailsWith<IllegalArgumentException> {
            ProfileId(
                "9B1F0C3E-0000-4000-8000-000000000001",
            )
        }
    }
}
