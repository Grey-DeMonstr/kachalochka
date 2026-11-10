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
    private val press = Machine.new("Leg press", ivan, t0)
    private val row = Machine.new("Row", ivan, t0)
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
            save(press.copy(tags = setOf("Legs", "legs ", "Back")))

            for (k in listOf("legs", "back")) {
                assertEquals(Tag(t(k), ivan, k, t0, false), tag(t(k)))
                assertEquals(MachineTag(l(k), ivan, press.id, t(k), t0, false), link(l(k)))
            }
            assertEquals(
                listOf(
                    TAG_TABLE to listOf(t("legs"), t("back")),
                    MACHINE_TAG_TABLE to listOf(l("legs"), l("back")),
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
            save(press.copy(tags = setOf("legs")))

            assertEquals(listOf("tags"), reads)
        }

    @Test
    fun live_tags_are_written_before_the_links_that_name_them() =
        runTest {
            save(press.copy(tags = setOf("legs")))

            assertEquals(
                listOf(TAG_TABLE to listOf(t("legs")), MACHINE_TAG_TABLE to listOf(l("legs"))),
                written,
            )
        }

    @Test
    fun an_unchanged_machine_writes_nothing() =
        runTest {
            save(press.copy(tags = setOf("legs")))
            save(press.copy(tags = setOf(" LEGS "), updatedAt = later))

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
            save(press.copy(tags = setOf("legs", "back")))
            save(press.copy(tags = setOf("legs"), updatedAt = later))

            assertEquals(
                listOf(MACHINE_TAG_TABLE to listOf(l("back")), TAG_TABLE to listOf(t("back"))),
                written,
            )
            assertEquals(
                MachineTag(l("back"), ivan, press.id, t("back"), later, true),
                link(l("back")),
            )
            assertEquals(Tag(t("back"), ivan, "back", later, true), tag(t("back")))
            assertEquals(Tag(t("legs"), ivan, "legs", t0, false), tag(t("legs")))
            assertEquals(
                MachineTag(l("legs"), ivan, press.id, t("legs"), t0, false),
                link(l("legs")),
            )
        }

    @Test
    fun a_tag_another_live_machine_holds_stays_live() =
        runTest {
            save(press.copy(tags = setOf("legs", "back")))
            save(row.copy(tags = setOf("back")))
            save(press.copy(tags = emptySet(), updatedAt = later))

            assertTrue(assertNotNull(tag(t("legs"))).deleted)
            assertFalse(assertNotNull(tag(t("back"))).deleted)
        }

    @Test
    fun a_tag_that_only_a_deleted_machine_s_live_link_holds_is_unused() =
        runTest {
            save(press.copy(tags = setOf("back")))
            save(row.copy(tags = setOf("back")))
            putMachine(row.copy(deleted = true))
            save(press.copy(tags = emptySet(), updatedAt = later))

            assertTrue(assertNotNull(tag(t("back"))).deleted)
        }

    @Test
    fun a_deleted_machine_loses_every_link_and_its_unused_tags() =
        runTest {
            save(press.copy(tags = setOf("legs")))
            save(press.copy(tags = setOf("legs"), deleted = true, updatedAt = later))

            assertEquals(
                listOf(MACHINE_TAG_TABLE to listOf(l("legs")), TAG_TABLE to listOf(t("legs"))),
                written,
            )
            assertTrue(assertNotNull(link(l("legs"))).deleted)
            assertTrue(assertNotNull(tag(t("legs"))).deleted)
        }

    @Test
    fun a_deleted_machine_never_deletes_a_tag_a_live_machine_holds() =
        runTest {
            save(press.copy(tags = setOf("legs")))
            save(row.copy(tags = setOf("legs")))
            save(row.copy(tags = setOf("legs"), deleted = true, updatedAt = later))

            assertTrue(assertNotNull(link(l("legs", row))).deleted)
            assertFalse(assertNotNull(tag(t("legs"))).deleted)
            assertFalse(assertNotNull(link(l("legs"))).deleted)
        }

    @Test
    fun a_tag_given_back_is_live_again_under_the_same_ids() =
        runTest {
            save(press.copy(tags = setOf("legs")))
            save(press.copy(tags = emptySet(), updatedAt = later))
            save(press.copy(tags = setOf("legs"), updatedAt = later + 1.minutes))

            val again = later + 1.minutes
            assertEquals(Tag(t("legs"), ivan, "legs", again, false), tag(t("legs")))
            assertEquals(
                MachineTag(l("legs"), ivan, press.id, t("legs"), again, false),
                link(l("legs")),
            )
        }

    @Test
    fun a_mate_s_link_on_the_machine_is_never_dropped_or_counted() =
        runTest {
            val theirs = MachineTag(l("theirs"), mate, press.id, t("theirs", mate), t0, false)
            putLink(theirs)

            save(press)

            assertTrue(written.isEmpty())
            assertEquals(theirs, link(theirs.id))
        }

    @Test
    fun an_ownerless_tag_another_ownerless_machine_holds_stays_live() =
        runTest {
            val first = Machine.new("Leg press", null, t0)
            val second = Machine.new("Row", null, t0)
            save(first.copy(tags = setOf("legs")))
            save(second.copy(tags = setOf("legs")))
            save(first.copy(tags = emptySet(), updatedAt = later))

            val kept = assertNotNull(tag(tagId(null, "legs")))
            assertFalse(kept.deleted)
            assertNull(kept.userId)
        }
}
