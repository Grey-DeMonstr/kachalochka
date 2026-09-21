package monster.greyde.kachalochka.ui.account

import kotlin.jvm.JvmInline

/**
 * Whether the build holds the credentials its own Google flow needs: Android's Credential Manager
 * wants a web client id on top of the Supabase pair, the web redirect does not. See §5.4.
 */
@JvmInline
value class SignInAvailable(
    val value: Boolean,
)
