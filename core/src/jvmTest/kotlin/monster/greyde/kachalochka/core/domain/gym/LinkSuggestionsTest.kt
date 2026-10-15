package monster.greyde.kachalochka.core.domain.gym

import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals

class LinkSuggestionsTest {
    private val me = UserId("11111111-1111-4111-8111-111111111111")
    private val oleg = UserId("33333333-3333-4333-8333-333333333333")

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

        val suggested = linkSuggestions(listOf(a, b, c), emptyList())

        assertEquals(mapOf(a.id to listOf(b), b.id to listOf(a)), suggested)
    }

    @Test
    fun own_machines_joined_through_a_friend_s_suggest_each_other() {
        val a = machine("Гакк", me)
        val c = machine("Присед", me)
        val b = machine("Гакк-машина", oleg)

        val suggested = linkSuggestions(listOf(a, c), listOf(link(a, b), link(c, b)))

        assertEquals(mapOf(a.id to listOf(c), c.id to listOf(a)), suggested)
    }

    @Test
    fun a_friend_s_machine_is_never_suggested() {
        val a = machine("Гакк", me)
        val b = machine("Гакк-машина", oleg)
        val c = machine("Присед", oleg)

        val suggested = linkSuggestions(listOf(a), listOf(link(a, b), link(c, b)))

        assertEquals(emptyMap(), suggested)
    }

    @Test
    fun a_deleted_link_joins_nothing() {
        val a = machine("Гакк", me)
        val c = machine("Присед", me)
        val b = machine("Гакк-машина", oleg)

        val suggested = linkSuggestions(listOf(a, c), listOf(link(a, b), link(c, b, true)))

        assertEquals(emptyMap(), suggested)
    }

    @Test
    fun the_machines_to_merge_with_come_by_name() {
        val a = machine("Гакк", me)
        val later = machine("Гакк", me)
        val first = machine("Арка", me)
        val b = machine("Гакк-машина", oleg)

        val suggested =
            linkSuggestions(listOf(a, later, first), listOf(link(a, b), link(first, b)))

        assertEquals(listOf(first, later), suggested[a.id])
    }
}
