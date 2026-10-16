package monster.greyde.kachalochka.core.data.gym

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** What every [TagRowStore] must do under [writeTagsOf]. */
abstract class TagRowStoreContract {
    internal abstract val store: TagRowStore

    /** Stores the machine's own row only, as a client that leaves its tag rows as they are. */
    internal abstract fun putMachine(machine: Machine)

    internal abstract fun putLink(link: MachineTag)

    internal abstract fun tag(id: String): Tag?

    internal abstract fun link(id: String): MachineTag?

    private val ivan = UserId("11111111-1111-4111-8111-111111111111")
    private val mate = UserId("33333333-3333-4333-8333-333333333333")
    private val t0 = Instant.parse("2026-10-16T10:00:00Z")
    private val later = t0 + 1.minutes
    private val press = Machine.new("Жим ногами", ivan, t0)
    private val row = Machine.new("Тяга", ivan, t0)
    private val written = mutableListOf<Pair<String, List<String>>>()

    private val reads = mutableListOf<String>()

    private inner class Recorded(
        private val inner: TagRowStore,
    ) : TagRowStore by inner {
        override suspend fun tags(ids: List<String>): List<Tag> {
            reads += "tags"
            return inner.tags(ids)
        }

        override suspend fun heldElsewhere(
            machine: Machine,
            tags: List<String>,
        ): Set<String> {
            reads += "heldElsewhere"
            return inner.heldElsewhere(machine, tags)
        }

        override suspend fun upsertTags(tags: List<Tag>) {
            written += TAG_TABLE to tags.map { it.id }
            inner.upsertTags(tags)
        }

        override suspend fun upsertLinks(links: List<MachineTag>) {
            written += MACHINE_TAG_TABLE to links.map { it.id }
            inner.upsertLinks(links)
        }
    }

    private suspend fun save(machine: Machine) {
        putMachine(machine)
        written.clear()
        reads.clear()
        Recorded(store).writeTagsOf(machine)
    }

    private fun t(
        key: String,
        owner: UserId? = ivan,
    ) = tagId(owner, key)

    private fun l(
        key: String,
        machine: Machine = press,
    ) = machineTagId(machine.id, key)

    @Test
    fun a_machine_s_tags_become_one_live_tag_and_link_per_key() =
        runTest {
            save(press.copy(tags = setOf("Ноги", "ноги ", "Спина")))

            for (k in listOf("ноги", "спина")) {
                assertEquals(Tag(t(k), ivan, k, t0, false), tag(t(k)))
                assertEquals(MachineTag(l(k), ivan, press.id, t(k), t0, false), link(l(k)))
            }
            assertEquals(
                listOf(
                    TAG_TABLE to listOf(t("ноги"), t("спина")),
                    MACHINE_TAG_TABLE to listOf(l("ноги"), l("спина")),
                ),
                written,
            )
        }

    @Test
    fun a_machine_without_tags_or_links_reads_no_tag_rows_and_no_holders() =
        runTest {
            save(press)

            assertTrue(reads.isEmpty())
        }

    @Test
    fun a_machine_that_drops_no_link_reads_no_holders() =
        runTest {
            save(press.copy(tags = setOf("ноги")))

            assertEquals(listOf("tags"), reads)
        }

    @Test
    fun live_tags_are_written_before_the_links_that_name_them() =
        runTest {
            save(press.copy(tags = setOf("ноги")))

            assertEquals(
                listOf(TAG_TABLE to listOf(t("ноги")), MACHINE_TAG_TABLE to listOf(l("ноги"))),
                written,
            )
        }

    @Test
    fun an_unchanged_machine_writes_nothing() =
        runTest {
            save(press.copy(tags = setOf("ноги")))
            save(press.copy(tags = setOf(" НОГИ "), updatedAt = later))

            assertTrue(written.isEmpty())
        }

    @Test
    fun a_blank_tag_writes_no_row() =
        runTest {
            save(press.copy(tags = setOf("  ")))

            assertTrue(written.isEmpty())
        }

    @Test
    fun a_dropped_tag_is_deleted_only_after_its_link() =
        runTest {
            save(press.copy(tags = setOf("ноги", "спина")))
            save(press.copy(tags = setOf("ноги"), updatedAt = later))

            assertEquals(
                listOf(MACHINE_TAG_TABLE to listOf(l("спина")), TAG_TABLE to listOf(t("спина"))),
                written,
            )
            assertEquals(
                MachineTag(l("спина"), ivan, press.id, t("спина"), later, true),
                link(l("спина")),
            )
            assertEquals(Tag(t("спина"), ivan, "спина", later, true), tag(t("спина")))
            assertEquals(Tag(t("ноги"), ivan, "ноги", t0, false), tag(t("ноги")))
            assertEquals(
                MachineTag(l("ноги"), ivan, press.id, t("ноги"), t0, false),
                link(l("ноги")),
            )
        }

    @Test
    fun a_tag_another_live_machine_holds_stays_live() =
        runTest {
            save(press.copy(tags = setOf("ноги", "спина")))
            save(row.copy(tags = setOf("спина")))
            save(press.copy(tags = emptySet(), updatedAt = later))

            assertTrue(assertNotNull(tag(t("ноги"))).deleted)
            assertFalse(assertNotNull(tag(t("спина"))).deleted)
        }

    @Test
    fun a_tag_that_only_a_deleted_machine_s_live_link_holds_is_unused() =
        runTest {
            save(press.copy(tags = setOf("спина")))
            save(row.copy(tags = setOf("спина")))
            putMachine(row.copy(deleted = true))
            save(press.copy(tags = emptySet(), updatedAt = later))

            assertTrue(assertNotNull(tag(t("спина"))).deleted)
        }

    @Test
    fun a_deleted_machine_loses_every_link_and_its_unused_tags() =
        runTest {
            save(press.copy(tags = setOf("ноги")))
            save(press.copy(tags = setOf("ноги"), deleted = true, updatedAt = later))

            assertEquals(
                listOf(MACHINE_TAG_TABLE to listOf(l("ноги")), TAG_TABLE to listOf(t("ноги"))),
                written,
            )
            assertTrue(assertNotNull(link(l("ноги"))).deleted)
            assertTrue(assertNotNull(tag(t("ноги"))).deleted)
        }

    @Test
    fun a_deleted_machine_never_deletes_a_tag_a_live_machine_holds() =
        runTest {
            save(press.copy(tags = setOf("ноги")))
            save(row.copy(tags = setOf("ноги")))
            save(row.copy(tags = setOf("ноги"), deleted = true, updatedAt = later))

            assertTrue(assertNotNull(link(l("ноги", row))).deleted)
            assertFalse(assertNotNull(tag(t("ноги"))).deleted)
            assertFalse(assertNotNull(link(l("ноги"))).deleted)
        }

    @Test
    fun a_tag_given_back_is_live_again_under_the_same_ids() =
        runTest {
            save(press.copy(tags = setOf("ноги")))
            save(press.copy(tags = emptySet(), updatedAt = later))
            save(press.copy(tags = setOf("ноги"), updatedAt = later + 1.minutes))

            val again = later + 1.minutes
            assertEquals(Tag(t("ноги"), ivan, "ноги", again, false), tag(t("ноги")))
            assertEquals(
                MachineTag(l("ноги"), ivan, press.id, t("ноги"), again, false),
                link(l("ноги")),
            )
        }

    @Test
    fun a_mate_s_link_on_the_machine_is_never_dropped_or_counted() =
        runTest {
            val theirs = MachineTag(l("чужой"), mate, press.id, t("чужой", mate), t0, false)
            putLink(theirs)

            save(press)

            assertTrue(written.isEmpty())
            assertEquals(theirs, link(theirs.id))
        }

    @Test
    fun an_ownerless_tag_another_ownerless_machine_holds_stays_live() =
        runTest {
            val first = Machine.new("Жим ногами", null, t0)
            val second = Machine.new("Тяга", null, t0)
            save(first.copy(tags = setOf("ноги")))
            save(second.copy(tags = setOf("ноги")))
            save(first.copy(tags = emptySet(), updatedAt = later))

            val kept = assertNotNull(tag(tagId(null, "ноги")))
            assertFalse(kept.deleted)
            assertNull(kept.userId)
        }
}
