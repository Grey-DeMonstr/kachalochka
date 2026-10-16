package monster.greyde.kachalochka.core.data.gym

import kotlinx.serialization.json.Json
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class TagWireTest {
    private val ivan = UserId("11111111-1111-4111-8111-111111111111")
    private val t0 = Instant.parse("2026-10-16T10:00:00Z")
    private val press = Machine.new("Жим ногами", ivan, t0)

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
    fun machines_read_the_names_of_their_live_links() {
        val live = Tag(tagId(ivan, "ноги"), ivan, "ноги", t0, false)
        val back = Tag(tagId(ivan, "спина"), ivan, "спина", t0, false)
        val links =
            listOf(live to false, back to true).map { (tag, deleted) ->
                MachineTag(machineTagId(press.id, tag.name), ivan, press.id, tag.id, t0, deleted)
            }

        val read = listOf(press).withTagRows(links, listOf(live, back)).single()

        assertEquals(setOf("ноги"), read.tags)
    }

    @Test
    fun a_group_mate_s_link_or_tag_on_a_machine_is_not_its_tag() {
        val mate = UserId("33333333-3333-4333-8333-333333333333")
        val theirs = Tag(tagId(mate, "чужой"), mate, "чужой", t0, false)
        val link =
            MachineTag(machineTagId(press.id, "чужой"), mate, press.id, theirs.id, t0, false)
        val taken = Tag(tagId(ivan, "ноги"), mate, "чужой", t0, false)
        val own = MachineTag(machineTagId(press.id, "ноги"), ivan, press.id, taken.id, t0, false)

        val read = listOf(press).withTagRows(listOf(link, own), listOf(theirs, taken)).single()

        assertEquals(emptySet(), read.tags)
    }

    @Test
    fun rows_travel_in_the_wire_format() {
        val tag = Tag(tagId(ivan, "ноги"), ivan, "ноги", t0, false)
        val link = MachineTag(machineTagId(press.id, "ноги"), ivan, press.id, tag.id, t0, false)

        assertEquals(
            """{"id":"${tag.id}","user_id":"${ivan.value}","name":"ноги",""" +
                """"updated_at":"2026-10-16T10:00:00Z","deleted":false}""",
            Json.encodeToString(TagRow.of(tag)),
        )
        assertEquals(tag, TagRow.of(tag).toTag())
        assertEquals(link, MachineTagRow.of(link).toMachineTag())
    }
}
