package monster.greyde.kachalochka.ui.account

import kotlin.jvm.JvmInline

/** Web has no anonymous mode; Android and the JVM target do. See technical spec §5.4. */
@JvmInline
value class SignInRequired(
    val value: Boolean,
)
