package monster.greyde.kachalochka.core.data.db

import app.cash.sqldelight.ColumnAdapter
import kotlin.time.Instant

// Postgres keeps timestamptz well below the second, and last-write-wins compares the two sides
// directly, so the local column has to hold the same precision the server does.
internal object InstantColumnAdapter : ColumnAdapter<Instant, Long> {
    override fun decode(databaseValue: Long): Instant =
        Instant.fromEpochMilliseconds(databaseValue)

    override fun encode(value: Instant): Long = value.toEpochMilliseconds()
}
