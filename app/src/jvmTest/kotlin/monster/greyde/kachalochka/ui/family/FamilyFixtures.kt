package monster.greyde.kachalochka.ui.family

import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.domain.family.FamilyMember
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.friends.IVAN_SESSION

internal val OLGA_SESSION =
    IVAN_SESSION.copy(
        account =
            Account(
                UserId("88888888-8888-4888-8888-888888888888"),
                "olga@example.test",
                "Ольга",
            ),
    )

internal val IVAN_MEMBER = FamilyMember(IVAN_SESSION.account.userId, "Иван")
internal val SASHA = FamilyMember(UserId("66666666-6666-4666-8666-666666666666"), "Саша")
internal val PAPA = FamilyMember(UserId("77777777-7777-4777-8777-777777777777"), "Папа")
