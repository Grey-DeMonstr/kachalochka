package monster.greyde.kachalochka.ui.account

import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository

/** The unit [owner] chose to see gym weights in; kilograms until a profile says otherwise. */
suspend fun ProfileRepository.preferredUnit(owner: UserId?): PreferredWeightUnit =
    forOwner(owner)?.weightUnit ?: PreferredWeightUnit.Kg
