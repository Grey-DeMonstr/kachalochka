package monster.greyde.kachalochka.ui.machine

import monster.greyde.kachalochka.core.domain.friends.FriendMachine
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineSort
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Instant

class MachineListingTest {
    private val t0 = Instant.fromEpochSeconds(1_700_000_000)
    private val today = CalendarDay.of(t0, Duration.ZERO)

    private fun own(name: String) = Machine.new(name, ME.userId, t0)

    private fun theirs(name: String) = Machine.new(name, OLEG.userId, t0)

    /** [group] null reads as "no group read yet". */
    private fun read(
        mine: List<Machine>,
        group: List<Machine>? = emptyList(),
        links: List<MachineLink> = emptyList(),
    ): MachineRead {
        val groupMachines =
            group?.let {
                GroupMachines(
                    ME.userId,
                    it.map { machine -> FriendMachine(machine, OLEG) },
                    links,
                    emptyList(),
                )
            }
        return MachineRead(
            ShownMachines(OwnMachines(ME.userId, mine, emptyList(), links), groupMachines),
            groupMachines?.let { GroupRead(ME.userId, it, fresh = true, offline = false) },
        )
    }

    private fun MachineRead.listing(query: MachineQuery) =
        listing(query, MachineSort.Name, PreferredWeightUnit.Kg, today, Duration.ZERO)

    private fun MachineListing.ownNames() = own.map { it.name }

    private fun MachineListing.friendIds() = friendSections.flatMap { s -> s.cards.map { it.id } }

    @Test
    fun a_search_with_a_typo_keeps_own_and_friends_machines_whose_name_matches() {
        val smith = own("Smith squats")
        val olegSquat = theirs("Squats")
        val read = read(listOf(smith, own("Row")), listOf(olegSquat))

        val found = read.listing(MachineQuery(text = "squets"))

        assertEquals(listOf("Smith squats"), found.ownNames())
        assertEquals(listOf(olegSquat.id), found.friendIds())
        assertFalse(found.nothingFound)

        val none = read.listing(MachineQuery(text = "hack"))

        assertEquals(emptyList(), none.ownNames())
        assertEquals(emptyList(), none.friendIds())
        assertTrue(none.nothingFound)
    }

    @Test
    fun an_own_machine_is_found_by_the_name_of_a_linked_friend_s_one() {
        val olegPress = theirs("Hack machine")
        val (copy, link) = linkedCopy(olegPress, ME.userId, t0)
        val mine = copy.copy(name = "Leg squat")

        val found =
            read(listOf(mine, own("Row")), listOf(olegPress), listOf(link))
                .listing(MachineQuery(text = "hack"))

        assertEquals(listOf("Leg squat"), found.ownNames())
        assertEquals(emptyList(), found.friendSections)
    }

    @Test
    fun chosen_tags_keep_the_machines_carrying_every_one_of_them() {
        val olegPress = theirs("Leg press").copy(tags = setOf("legs"))
        val read =
            read(
                listOf(
                    own("Row").copy(tags = setOf("back")),
                    own("Squat").copy(tags = setOf("legs", "core")),
                ),
                listOf(olegPress),
            )

        val none = read.listing(MachineQuery())
        assertEquals(
            listOf("back" to false, "core" to false, "legs" to false),
            none.tags.map { it.name to it.chosen },
        )

        val legs = read.listing(MachineQuery(tags = setOf("Legs")))
        assertEquals(listOf("Squat"), legs.ownNames())
        assertEquals(listOf(olegPress.id), legs.friendIds())

        val both = read.listing(MachineQuery(tags = setOf("legs", "core")))
        assertEquals(listOf("Squat"), both.ownNames())
        assertEquals(emptyList(), both.friendIds())
    }

    @Test
    fun a_tag_no_machine_holds_leaves_the_query_but_a_friend_s_stays_while_friends_are_unknown() {
        val mine = listOf(own("Squat"), own("Row"))
        val query = MachineQuery(tags = setOf("shoulders"))

        val unknown = read(mine, group = null).listing(query)

        assertEquals(setOf("shoulders"), unknown.query.tags)
        assertEquals(listOf("shoulders" to true), unknown.tags.map { it.name to it.chosen })
        assertEquals(emptyList(), unknown.ownNames())

        val known = read(mine).listing(query)

        assertEquals(emptySet(), known.query.tags)
        assertEquals(listOf("Row", "Squat"), known.ownNames())

        val theirs = theirs("Press").copy(tags = setOf("shoulders"))
        val friends = read(mine, listOf(theirs)).listing(query)

        assertEquals(setOf("shoulders"), friends.query.tags)
        assertEquals(emptyList(), friends.ownNames())
        assertEquals(listOf(theirs.id), friends.friendIds())
    }

    @Test
    fun a_tag_in_another_letter_case_is_one_chip_that_keeps_both_machines() {
        val read =
            read(
                listOf(
                    own("Squat").copy(tags = setOf("Legs")),
                    own("Press").copy(tags = setOf("legs")),
                ),
            )

        val found = read.listing(MachineQuery(tags = setOf("LEGS")))

        assertEquals(listOf("Press", "Squat"), found.ownNames())
        assertEquals(1, found.tags.size)
        assertTrue(found.tags.single().chosen)
    }

    @Test
    fun machines_in_the_visit_are_found_apart_and_still_count_as_found() {
        val bench = own("Bench press")
        val press = own("Leg press")
        val smith = own("Smith")
        val read = read(listOf(bench, press, smith))
        val inVisit = setOf<MachineId>(smith.id)

        val all = read.listing(MachineQuery(inVisit = inVisit))

        assertEquals(listOf("Bench press", "Leg press"), all.ownNames())
        assertEquals(listOf(smith.id), all.inVisit.map { it.id })
        assertTrue(all.inVisit.all { it.inVisit })

        val searched = read.listing(MachineQuery(text = "smith", inVisit = inVisit))

        assertEquals(emptyList(), searched.ownNames())
        assertEquals(listOf(smith.id), searched.inVisit.map { it.id })
        assertFalse(searched.nothingFound)

        val another = read.listing(MachineQuery(text = "press", inVisit = inVisit))

        assertEquals(listOf("Bench press", "Leg press"), another.ownNames())
        assertEquals(emptyList(), another.inVisit)
    }

    @Test
    fun a_new_name_offers_creation_and_an_existing_one_does_not() {
        val read = read(listOf(own("Leg press")))

        assertTrue(read.listing(MachineQuery(text = "hack")).offerCreate)
        assertFalse(read.listing(MachineQuery(text = "LEG press")).offerCreate)
        assertFalse(read.listing(MachineQuery()).offerCreate)
    }

    @Test
    fun only_suggested_keeps_the_own_machines_with_a_suggestion_and_no_friends() {
        val read =
            read(listOf(own("Press"), own("press"), own("Row")), listOf(theirs("Leg press")))

        val only = read.listing(MachineQuery(onlySuggested = true))

        assertEquals(listOf("Press", "press"), only.ownNames())
        assertEquals(emptyList(), only.friendSections)
        assertTrue(only.hasSuggestions)
        assertTrue(only.query.onlySuggested)

        val plain =
            read(listOf(own("Press"), own("Row"))).listing(MachineQuery(onlySuggested = true))

        assertFalse(plain.query.onlySuggested)
        assertFalse(plain.hasSuggestions)
        assertEquals(listOf("Press", "Row"), plain.ownNames())
    }
}
