package monster.greyde.kachalochka.core.domain.sync

import kotlin.time.Instant

data class OutboxEntry(
    val tableName: String,
    val rowId: String,
    val enqueuedAt: Instant,
)
