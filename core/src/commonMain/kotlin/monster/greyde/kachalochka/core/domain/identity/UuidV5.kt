package monster.greyde.kachalochka.core.domain.identity

import kotlin.uuid.Uuid

/** RFC 9562 UUID v5 of [name] as UTF-8, as Postgres' `uuid_generate_v5` makes it. */
internal fun uuidV5(
    namespace: String,
    name: String,
): String {
    val hash = sha1(Uuid.parse(namespace).toByteArray() + name.encodeToByteArray())
    val bytes = hash.copyOf(16)
    bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x50).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
    return Uuid.fromByteArray(bytes).toString()
}

/** FIPS 180-4 SHA-1. */
internal fun sha1(message: ByteArray): ByteArray {
    val bitLength = message.size.toLong() * 8
    val padded = ByteArray(((message.size + 8) / 64 + 1) * 64)
    message.copyInto(padded)
    padded[message.size] = 0x80.toByte()
    for (i in 0 until 8) {
        padded[padded.size - 1 - i] = (bitLength ushr (8 * i)).toByte()
    }
    val state = intArrayOf(0x67452301, -0x10325477, -0x67452302, 0x10325476, -0x3c2d1e10)
    val words = IntArray(80)
    for (block in padded.indices step 64) {
        for (t in 0 until 16) {
            val at = block + t * 4
            words[t] =
                (padded[at].toInt() and 0xff shl 24) or
                (padded[at + 1].toInt() and 0xff shl 16) or
                (padded[at + 2].toInt() and 0xff shl 8) or
                (padded[at + 3].toInt() and 0xff)
        }
        for (t in 16 until 80) {
            words[t] =
                (words[t - 3] xor words[t - 8] xor words[t - 14] xor words[t - 16])
                    .rotateLeft(1)
        }
        var a = state[0]
        var b = state[1]
        var c = state[2]
        var d = state[3]
        var e = state[4]
        for (t in 0 until 80) {
            val (f, k) =
                when (t / 20) {
                    0 -> ((b and c) or (b.inv() and d)) to 0x5a827999
                    1 -> (b xor c xor d) to 0x6ed9eba1
                    2 -> ((b and c) or (b and d) or (c and d)) to -0x70e44324
                    else -> (b xor c xor d) to -0x359d3e2a
                }
            val next = a.rotateLeft(5) + f + e + k + words[t]
            e = d
            d = c
            c = b.rotateLeft(30)
            b = a
            a = next
        }
        state[0] += a
        state[1] += b
        state[2] += c
        state[3] += d
        state[4] += e
    }
    return ByteArray(20) { (state[it / 4] ushr (24 - 8 * (it % 4))).toByte() }
}
