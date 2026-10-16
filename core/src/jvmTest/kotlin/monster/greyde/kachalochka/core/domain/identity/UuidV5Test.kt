package monster.greyde.kachalochka.core.domain.identity

import kotlin.test.Test
import kotlin.test.assertEquals

class UuidV5Test {
    private fun hex(bytes: ByteArray) =
        bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    @Test
    fun sha1_hashes_the_fips_examples() {
        assertEquals(
            "a9993e364706816aba3e25717850c26c9cd0d89d",
            hex(sha1("abc".encodeToByteArray())),
        )
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", hex(sha1(ByteArray(0))))
        assertEquals(
            "84983e441c3bd26ebaae4aa1f95129e5e54670f1",
            hex(
                sha1(
                    "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray(),
                ),
            ),
        )
    }

    @Test
    fun the_rfc_example_names_www_example_com_in_the_dns_namespace() {
        assertEquals(
            "2ed6657d-e927-568b-95e1-2665a8aea6a2",
            uuidV5("6ba7b810-9dad-11d1-80b4-00c04fd430c8", "www.example.com"),
        )
    }

    @Test
    fun a_cyrillic_name_is_hashed_as_utf_8() {
        assertEquals(
            "a074841e-d612-53fe-b401-9f7a61d5b94e",
            uuidV5(
                "6f1c2a4e-8d3b-4c7a-9e51-2b7d0f3a9c84",
                "11111111-1111-4111-8111-111111111111/ноги",
            ),
        )
    }
}
