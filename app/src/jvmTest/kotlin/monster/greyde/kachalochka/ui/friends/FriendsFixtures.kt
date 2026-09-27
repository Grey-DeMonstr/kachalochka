package monster.greyde.kachalochka.ui.friends

import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.fakes.FakeGym
import kotlin.time.Instant

internal val IVAN_SESSION =
    AccountSession(
        Account(UserId("11111111-1111-4111-8111-111111111111"), "ivan@example.test", "Иван"),
        "access",
        "refresh",
        Instant.fromEpochSeconds(1_700_000_000),
    )
internal val ME = Friend(IVAN_SESSION.account.userId, "Иван")
internal val OLEG = Friend(UserId("33333333-3333-4333-8333-333333333333"), "Олег")
internal val PASHA = Friend(UserId("44444444-4444-4444-8444-444444444444"), "Паша")

/** Иван signed in and active. */
internal fun signedInGym(): FakeGym = FakeGym().withAccounts(IVAN_SESSION, active = IVAN_SESSION)
