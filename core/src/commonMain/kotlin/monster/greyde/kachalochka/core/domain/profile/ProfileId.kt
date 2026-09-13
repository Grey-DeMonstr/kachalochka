package monster.greyde.kachalochka.core.domain.profile

import monster.greyde.kachalochka.core.domain.identity.requireUuidV4
import kotlin.jvm.JvmInline

@JvmInline
value class ProfileId(
    val value: String,
) {
    init {
        requireUuidV4(value, "ProfileId")
    }
}
