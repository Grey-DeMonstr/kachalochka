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
        val smith = own("Приседания в Смите")
        val olegSquat = theirs("Приседания")
        val read = read(listOf(smith, own("Тяга")), listOf(olegSquat))

        val found = read.listing(MachineQuery(text = "присидания"))

        assertEquals(listOf("Приседания в Смите"), found.ownNames())
        assertEquals(listOf(olegSquat.id), found.friendIds())
        assertFalse(found.nothingFound)

        val none = read.listing(MachineQuery(text = "гакк"))

        assertEquals(emptyList(), none.ownNames())
        assertEquals(emptyList(), none.friendIds())
        assertTrue(none.nothingFound)
    }

    @Test
    fun an_own_machine_is_found_by_the_name_of_a_linked_friend_s_one() {
        val olegPress = theirs("Гакк-машина")
        val (copy, link) = linkedCopy(olegPress, ME.userId, t0)
        val mine = copy.copy(name = "Присед в тренажёре")

        val found =
            read(listOf(mine, own("Тяга")), listOf(olegPress), listOf(link))
                .listing(MachineQuery(text = "гакк"))

        assertEquals(listOf("Присед в тренажёре"), found.ownNames())
        assertEquals(emptyList(), found.friendSections)
    }

    @Test
    fun chosen_tags_keep_the_machines_carrying_every_one_of_them() {
        val olegPress = theirs("Жим ногами").copy(tags = setOf("ноги"))
        val read =
            read(
                listOf(
                    own("Тяга").copy(tags = setOf("спина")),
                    own("Присед").copy(tags = setOf("ноги", "база")),
                ),
                listOf(olegPress),
            )

        val none = read.listing(MachineQuery())
        assertEquals(
            listOf("база" to false, "ноги" to false, "спина" to false),
            none.tags.map { it.name to it.chosen },
        )

        val legs = read.listing(MachineQuery(tags = setOf("Ноги")))
        assertEquals(listOf("Присед"), legs.ownNames())
        assertEquals(listOf(olegPress.id), legs.friendIds())

        val both = read.listing(MachineQuery(tags = setOf("ноги", "база")))
        assertEquals(listOf("Присед"), both.ownNames())
        assertEquals(emptyList(), both.friendIds())
    }

    @Test
    fun a_tag_no_machine_holds_leaves_the_query_but_a_friend_s_stays_while_friends_are_unknown() {
        val mine = listOf(own("Присед"), own("Тяга"))
        val query = MachineQuery(tags = setOf("плечи"))

        val unknown = read(mine, group = null).listing(query)

        assertEquals(setOf("плечи"), unknown.query.tags)
        assertEquals(listOf("плечи" to true), unknown.tags.map { it.name to it.chosen })
        assertEquals(emptyList(), unknown.ownNames())

        val known = read(mine).listing(query)

        assertEquals(emptySet(), known.query.tags)
        assertEquals(listOf("Присед", "Тяга"), known.ownNames())
    }

    @Test
    fun a_tag_in_another_letter_case_is_one_chip_that_keeps_both_machines() {
        val read =
            read(
                listOf(
                    own("Присед").copy(tags = setOf("Ноги")),
                    own("Жим").copy(tags = setOf("ноги")),
                ),
            )

        val found = read.listing(MachineQuery(tags = setOf("НОГИ")))

        assertEquals(listOf("Жим", "Присед"), found.ownNames())
        assertEquals(1, found.tags.size)
        assertTrue(found.tags.single().chosen)
    }

    @Test
    fun machines_in_the_visit_are_found_apart_and_still_count_as_found() {
        val bench = own("Жим лёжа")
        val press = own("Жим ногами")
        val smith = own("Смит")
        val read = read(listOf(bench, press, smith))
        val inVisit = setOf<MachineId>(smith.id)

        val all = read.listing(MachineQuery(inVisit = inVisit))

        assertEquals(listOf("Жим лёжа", "Жим ногами"), all.ownNames())
        assertEquals(listOf(smith.id), all.inVisit.map { it.id })
        assertTrue(all.inVisit.all { it.inVisit })

        val searched = read.listing(MachineQuery(text = "смит", inVisit = inVisit))

        assertEquals(emptyList(), searched.ownNames())
        assertEquals(listOf(smith.id), searched.inVisit.map { it.id })
        assertFalse(searched.nothingFound)
    }

    @Test
    fun a_new_name_offers_creation_and_an_existing_one_does_not() {
        val read = read(listOf(own("Жим ногами")))

        assertTrue(read.listing(MachineQuery(text = "гакк")).offerCreate)
        assertFalse(read.listing(MachineQuery(text = "ЖИМ ногами")).offerCreate)
        assertFalse(read.listing(MachineQuery()).offerCreate)
    }

    @Test
    fun only_suggested_keeps_the_own_machines_with_a_suggestion_and_no_friends() {
        val read = read(listOf(own("Жим"), own("жим"), own("Тяга")), listOf(theirs("Жим ногами")))

        val only = read.listing(MachineQuery(onlySuggested = true))

        assertEquals(listOf("Жим", "жим"), only.ownNames())
        assertEquals(emptyList(), only.friendSections)
        assertTrue(only.hasSuggestions)
        assertTrue(only.query.onlySuggested)

        val plain =
            read(listOf(own("Жим"), own("Тяга"))).listing(MachineQuery(onlySuggested = true))

        assertFalse(plain.query.onlySuggested)
        assertFalse(plain.hasSuggestions)
        assertEquals(listOf("Жим", "Тяга"), plain.ownNames())
    }
}
