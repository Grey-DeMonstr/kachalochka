package monster.greyde.kachalochka.core.domain.profile

import monster.greyde.kachalochka.core.domain.identity.newUuidV4
import monster.greyde.kachalochka.core.domain.identity.requireUuidV4
import kotlin.jvm.JvmInline

@JvmInline
value class ProfileId(
    val value: String,
) {
    init {
        requireUuidV4(value, "ProfileId")
    }

    companion object {
        fun random(): ProfileId = ProfileId(newUuidV4())
    }
}
