package monster.greyde.kachalochka.core.domain.gym

import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals

class LinkSuggestionsTest {
    private val me = UserId("11111111-1111-4111-8111-111111111111")
    private val oleg = UserId("33333333-3333-4333-8333-333333333333")
    private val pasha = UserId("44444444-4444-4444-8444-444444444444")

    private fun machine(
        name: String,
        owner: UserId,
    ) = Machine.new(name, owner, T0)

    private fun link(
        from: Machine,
        to: Machine,
        deleted: Boolean = false,
    ) = MachineLink(MachineLinkId.random(), from.userId, from.id, to.id, T0, deleted)

    @Test
    fun own_machines_of_the_same_name_suggest_each_other() {
        val a = machine("Жим ногами", me)
        val b = machine(" жим НОГАМИ ", me)
        val c = machine("Жим", me)

        val suggested = linkSuggestions(listOf(a, b, c), emptyList(), emptyList())

        assertEquals(mapOf(a.id to listOf(b), b.id to listOf(a)), suggested)
    }

    @Test
    fun own_machines_joined_through_a_friend_s_suggest_each_other() {
        val a = machine("Гакк", me)
        val c = machine("Присед", me)
        val b = machine("Гакк-машина", oleg)

        val suggested = linkSuggestions(listOf(a, c), listOf(b), listOf(link(a, b), link(c, b)))

        assertEquals(mapOf(a.id to listOf(c), c.id to listOf(a)), suggested)
    }

    @Test
    fun a_friend_s_machine_reached_only_through_another_friend_s_is_suggested() {
        val a = machine("Гакк", me)
        val b = machine("Гакк-машина", oleg)
        val c = machine("Присед", pasha)

        val suggested = linkSuggestions(listOf(a), listOf(b, c), listOf(link(a, b), link(c, b)))

        assertEquals(mapOf(a.id to listOf(c)), suggested)
    }

    @Test
    fun one_direct_link_to_a_friend_s_machine_covers_that_friend() {
        val a = machine("Гакк", me)
        val b = machine("Гакк-машина", oleg)
        val other = machine("Гакк 2", oleg)
        val c = machine("Присед", pasha)
        val pashasOther = machine("Гакк", pasha)

        val suggested =
            linkSuggestions(
                listOf(a),
                listOf(b, other, c, pashasOther),
                listOf(link(a, b), link(other, b), link(c, b), link(pashasOther, c)),
            )

        assertEquals(mapOf(a.id to listOf(pashasOther)), suggested)
    }

    @Test
    fun a_link_in_either_direction_counts_and_a_deleted_one_does_not() {
        val a = machine("Гакк", me)
        val b = machine("Гакк-машина", oleg)
        val c = machine("Присед", pasha)

        val linkedBack =
            linkSuggestions(listOf(a), listOf(b, c), listOf(link(b, a), link(c, b), link(c, a)))
        val unlinked =
            linkSuggestions(
                listOf(a),
                listOf(b, c),
                listOf(link(a, b), link(c, b), link(a, c, deleted = true)),
            )

        assertEquals(emptyMap(), linkedBack)
        assertEquals(mapOf(a.id to listOf(c)), unlinked)
    }

    @Test
    fun merges_come_before_links() {
        val a = machine("Гакк", me)
        val same = machine("Гакк", me)
        val b = machine("Гакк-машина", oleg)
        val c = machine("Присед", pasha)

        val suggested =
            linkSuggestions(listOf(a, same), listOf(b, c), listOf(link(a, b), link(c, b)))

        assertEquals(listOf(same, c), suggested[a.id])
    }
}
