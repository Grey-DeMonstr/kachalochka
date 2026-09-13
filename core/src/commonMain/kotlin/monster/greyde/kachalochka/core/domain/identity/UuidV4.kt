package monster.greyde.kachalochka.core.domain.identity

// Postgres declares every id column `uuid` and folds the text form to lower case, so two ids that
// differ only in case are one row there and two rows in SQLite. Lower case is the only spelling.
private val UUID_V4 =
    Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")

internal fun requireUuidV4(
    value: String,
    label: String,
): String {
    require(UUID_V4.matches(value)) { "$label must be a lower-case UUID v4, was \"$value\"" }
    return value
}
