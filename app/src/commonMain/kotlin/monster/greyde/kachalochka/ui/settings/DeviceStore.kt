package monster.greyde.kachalochka.ui.settings

/** The name and value type of one stored device value. */
sealed interface DeviceKey<T : Any> {
    val name: String

    data class Text(
        override val name: String,
    ) : DeviceKey<String>

    data class Number(
        override val name: String,
    ) : DeviceKey<Int>

    data class Flag(
        override val name: String,
    ) : DeviceKey<Boolean>

    data class TextSet(
        override val name: String,
    ) : DeviceKey<Set<String>>
}

/** Where the device settings live: DataStore on Android, `localStorage` on the web. */
interface DeviceStore {
    /** The stored value at once, or null when there is none or the store cannot be read. */
    fun <T : Any> read(key: DeviceKey<T>): T?

    /** Stores [value]. A null [value] removes the key. */
    suspend fun <T : Any> write(
        key: DeviceKey<T>,
        value: T?,
    )
}

class InMemoryDeviceStore : DeviceStore {
    private val values = mutableMapOf<DeviceKey<*>, Any>()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> read(key: DeviceKey<T>): T? = values[key] as T?

    override suspend fun <T : Any> write(
        key: DeviceKey<T>,
        value: T?,
    ) {
        if (value == null) values.remove(key) else values[key] = value
    }
}
