package monster.greyde.kachalochka.core.domain.identity

import monster.greyde.kachalochka.core.domain.gym.PhotoId

/** What stands for a person: the [photo] they chose, else their Google [picture]. */
data class Avatar(
    val photo: PhotoId? = null,
    val picture: String? = null,
)
