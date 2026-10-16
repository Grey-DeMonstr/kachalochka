package monster.greyde.kachalochka.core.data.gym

import kotlinx.serialization.json.Json
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TagWireTest {
    private val ivan = UserId("11111111-1111-4111-8111-111111111111")
    private val t0 = Instant.parse("2026-10-16T10:00:00Z")
    private val press = Machine.new("Жим ногами", ivan, t0)

    private fun written(
        machine: Machine,
        stored: TagWrites = TagWrites(emptyList(), emptyList()),
        heldElsewhere: Set<String> = emptySet(),
    ) = tagWrites(machine, stored.links, stored.tags, heldElsewhere)

    @Test
    fun ids_are_uuid_v5_of_the_owner_or_machine_and_the_key_as_postgres_makes_them() {
        assertEquals("a074841e-d612-53fe-b401-9f7a61d5b94e", tagId(ivan, " Ноги "))
        assertEquals("84584605-92d5-5cfd-bc5f-b49feb585507", tagId(null, "ноги"))
        assertEquals(
            "2a05d45f-82e3-5def-bf66-e8464f92379a",
            machineTagId(MachineId("0d000000-0000-4000-8000-00000000000d"), "НОГИ"),
        )
    }

    @Test
    fun a_machine_s_tags_become_one_live_tag_and_link_per_key() {
        val writes = written(press.copy(tags = setOf("Ноги", "ноги ", "Спина")))

        assertEquals(
            listOf(
                Tag(tagId(ivan, "ноги"), ivan, "ноги", t0, false),
                Tag(tagId(ivan, "спина"), ivan, "спина", t0, false),
            ),
            writes.tags,
        )
        assertEquals(
            listOf("ноги", "спина").map {
                MachineTag(machineTagId(press.id, it), ivan, press.id, tagId(ivan, it), t0, false)
            },
            writes.links,
        )
    }

    @Test
    fun an_unchanged_machine_writes_nothing() {
        val tagged = press.copy(tags = setOf("ноги"))
        val stored = written(tagged)

        assertTrue(written(tagged.copy(updatedAt = t0 + 1.minutes), stored).isEmpty())
    }

    @Test
    fun a_dropped_tag_loses_its_link_and_its_row_once_no_other_machine_holds_it() {
        val tagged = press.copy(tags = setOf("ноги", "спина"))
        val stored = written(tagged)
        val later = t0 + 1.minutes

        val writes = written(tagged.copy(tags = setOf("ноги"), updatedAt = later), stored)
        val heldWrites =
            written(
                tagged.copy(tags = setOf("ноги"), updatedAt = later),
                stored,
                heldElsewhere = setOf(tagId(ivan, "спина")),
            )

        val back = stored.links.single { it.tagId == tagId(ivan, "спина") }
        assertEquals(listOf(back.copy(updatedAt = later, deleted = true)), writes.links)
        assertEquals(
            listOf(Tag(tagId(ivan, "спина"), ivan, "спина", later, true)),
            writes.tags,
        )
        assertEquals(emptyList(), heldWrites.tags)
    }

    @Test
    fun a_deleted_machine_loses_every_link() {
        val tagged = press.copy(tags = setOf("ноги"))
        val stored = written(tagged)

        val writes = written(tagged.copy(deleted = true), stored)

        assertEquals(stored.links.map { it.copy(deleted = true) }, writes.links)
        assertEquals(stored.tags.map { it.copy(deleted = true) }, writes.tags)
    }

    @Test
    fun a_tag_given_back_is_live_again_under_the_same_ids() {
        val tagged = press.copy(tags = setOf("ноги"))
        val first = written(tagged)
        val removed = written(tagged.copy(tags = emptySet()), first)

        val again = written(tagged, removed)

        assertEquals(first.tags, again.tags)
        assertEquals(first.links, again.links)
    }

    @Test
    fun machines_read_the_names_of_their_live_links() {
        val tagged = written(press.copy(tags = setOf("ноги", "спина")))
        val links =
            tagged.links.map {
                if (it.tagId ==
                    tagId(ivan, "спина")
                ) {
                    it.copy(deleted = true)
                } else {
                    it
                }
            }

        assertEquals(mapOf(press.id to setOf("ноги")), tagsByMachine(links, tagged.tags))
    }

    @Test
    fun rows_travel_in_the_wire_format() {
        val writes = written(press.copy(tags = setOf("ноги")))
        val tag = writes.tags.single()
        val link = writes.links.single()

        assertEquals(
            """{"id":"${tag.id}","user_id":"${ivan.value}","name":"ноги",""" +
                """"updated_at":"2026-10-16T10:00:00Z","deleted":false}""",
            Json.encodeToString(TagRow.of(tag)),
        )
        assertEquals(tag, TagRow.of(tag).toTag())
        assertEquals(link, MachineTagRow.of(link).toMachineTag())
    }
}
