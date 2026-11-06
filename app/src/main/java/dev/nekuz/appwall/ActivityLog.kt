package dev.nekuz.appwall

data class Attempt(
    val uid: Int,
    val destination: String,
    val port: Int,
    val protocol: Int,
    val count: Int,
    val lastSeenMillis: Long,
)

object ActivityLog {
    private const val MAX_ENTRIES = 300
    private const val INITIAL_CAPACITY = 16
    private const val LOAD_FACTOR = 0.75f

    private val entries = object : LinkedHashMap<String, Attempt>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Attempt>) = size > MAX_ENTRIES
    }

    @Synchronized
    fun record(uid: Int, destination: String, port: Int, protocol: Int, nowMillis: Long) {
        val key = "$uid|$protocol|$destination|$port"
        val count = (entries[key]?.count ?: 0) + 1
        entries[key] = Attempt(uid, destination, port, protocol, count, nowMillis)
    }

    @Synchronized
    fun snapshot(): List<Attempt> = entries.values.sortedByDescending { it.lastSeenMillis }

    @Synchronized
    fun clear() = entries.clear()
}
