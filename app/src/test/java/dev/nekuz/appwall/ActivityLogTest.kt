package dev.nekuz.appwall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActivityLogTest {

    @Before
    fun reset() = ActivityLog.clear()

    @Test
    fun clearErasesEveryEntry() {
        ActivityLog.record(10001, "example.com", 53, Packets.PROTOCOL_UDP, 1L)
        ActivityLog.clear()
        assertTrue(ActivityLog.snapshot().isEmpty())
    }

    @Test
    fun keepsAtMostThreeHundredEntries() {
        repeat(1000) { ActivityLog.record(10001, "host$it", 443, Packets.PROTOCOL_TCP, it.toLong()) }
        assertEquals(300, ActivityLog.snapshot().size)
    }
}
