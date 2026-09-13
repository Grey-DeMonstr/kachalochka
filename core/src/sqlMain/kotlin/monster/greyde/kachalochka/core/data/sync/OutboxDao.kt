package monster.greyde.kachalochka.core.data.sync

import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.domain.sync.OutboxEntry

class OutboxDao(
    database: KachalochkaDatabase,
) {
    private val queries = database.syncQueries

    fun enqueue(entry: OutboxEntry) {
        queries.enqueue(entry.tableName, entry.rowId, entry.enqueuedAt)
    }

    fun pending(): List<OutboxEntry> =
        queries
            .pending { tableName, rowId, enqueuedAt ->
                OutboxEntry(tableName, rowId, enqueuedAt)
            }.executeAsList()

    fun remove(
        tableName: String,
        rowId: String,
    ) {
        queries.remove(tableName, rowId)
    }
}
