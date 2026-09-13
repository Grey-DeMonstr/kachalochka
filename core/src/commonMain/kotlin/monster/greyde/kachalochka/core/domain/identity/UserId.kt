package monster.greyde.kachalochka.core.domain.identity

import kotlin.jvm.JvmInline

/** The Supabase account that owns a row. */
@JvmInline
value class UserId(
    val value: String,
) {
    init {
        requireUuidV4(value, "UserId")
    }
}
