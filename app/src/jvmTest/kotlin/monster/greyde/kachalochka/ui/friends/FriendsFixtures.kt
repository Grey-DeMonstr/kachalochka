package monster.greyde.kachalochka.ui.friends

import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.Visit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.fakes.FakeGym
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Олег, in a group with [viewer], trained yesterday on his copy of [machine]: 80×8, 85×6. */
internal fun FakeGym.olegTrainedOn(
    machine: Machine,
    viewer: Friend,
) {
    val t0 = clock.current
    friends.group("Зал на Лесной", owner = OLEG, viewer)
    val olegPress = linkedCopy(machine, OLEG.userId, t0)
    val olegVisit =
        Visit(VisitId.random(), OLEG.userId, today.plusDays(-1), t0 - 1.days, t0, false)
    friends.machines += olegPress
    friends.visits += olegVisit
    friends.sets +=
        listOf(80.0 to 8, 85.0 to 6).mapIndexed { i, (weight, reps) ->
            WorkoutSet(
                WorkoutSetId.random(),
                OLEG.userId,
                olegVisit.id,
                olegPress.id,
                weight,
                reps,
                i + 1,
                t0 - 1.days + i.minutes,
                t0,
                false,
            )
        }
}

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

/**
 * Oleg's yesterday visit, shared by [FriendVisitViewModelTest] and [FriendVisitScreenTest]: his
 * "Платформа" links to Ivan's own "Жим ногами", his "Тяга" does not, and both carry sets.
 */
internal class OlegVisitFixture(
    private val gym: FakeGym,
) {
    private val t0 = gym.clock.current
    val yesterday = gym.today.plusDays(-1)
    val myPress = Machine.new("Жим ногами", ME.userId, t0)
    val olegPress = linkedCopy(myPress, OLEG.userId, t0).copy(name = "Платформа")
    val olegRow = Machine.new("Тяга", OLEG.userId, t0).copy(unit = WeightUnit.Lb)
    val olegVisit = Visit(VisitId.random(), OLEG.userId, yesterday, t0 - 1.days, t0, false)

    private fun olegSet(
        machine: Machine,
        weight: Double,
        reps: Int,
        minutes: Int,
    ) = WorkoutSet(
        WorkoutSetId.random(),
        OLEG.userId,
        olegVisit.id,
        machine.id,
        weight,
        reps,
        0,
        t0 - 1.days + minutes.minutes,
        t0,
        false,
    )

    val sets =
        listOf(
            olegSet(olegPress, 80.0, 8, 0),
            olegSet(olegPress, 85.0, 6, 1),
            olegSet(olegRow, 100.0, 10, 2),
        )

    /** Wires a group, Oleg's machines/visit/sets and Ivan's own press into [gym]. */
    suspend fun install() {
        gym.friends.group("Зал на Лесной", owner = OLEG, ME)
        gym.friends.machines += listOf(olegPress, olegRow)
        gym.friends.visits += olegVisit
        gym.friends.sets += sets
        gym.machines.upsert(myPress)
    }
}
