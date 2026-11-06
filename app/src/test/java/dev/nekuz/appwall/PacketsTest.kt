package dev.nekuz.appwall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class PacketsTest {

    private fun ipv4(protocol: Int, transport: ByteArray): ByteArray {
        val header = ByteArray(20)
        header[0] = 0x45
        header[9] = protocol.toByte()
        byteArrayOf(10, 111, 222.toByte(), 2).copyInto(header, 12)
        byteArrayOf(93, 184.toByte(), 216.toByte(), 34).copyInto(header, 16)
        return header + transport
    }

    private fun tcp(destinationPort: Int, flags: Int): ByteArray {
        val segment = ByteArray(20)
        segment[0] = 0xC3.toByte()
        segment[1] = 0x50
        segment[2] = (destinationPort shr 8).toByte()
        segment[3] = destinationPort.toByte()
        segment[13] = flags.toByte()
        return segment
    }

    private fun dnsQuery(name: String): ByteArray {
        val udp = ByteArray(8)
        udp[3] = 53
        val question = name.split('.').flatMap { label ->
            listOf(label.length.toByte()) + label.toByteArray().toList()
        }.toByteArray() + byteArrayOf(0, 0, 1, 0, 1)
        return udp + ByteArray(12) + question
    }

    @Test
    fun readsTcpConnectionAttempt() {
        val packet = ipv4(Packets.PROTOCOL_TCP, tcp(443, 0x02))
        val flow = assertNotNullFlow(Packets.parse(packet, packet.size))
        assertEquals(Packets.PROTOCOL_TCP, flow.protocol)
        assertEquals(443, flow.destinationPort)
        assertEquals("93.184.216.34", flow.destination.hostAddress)
    }

    @Test
    fun ignoresTcpPacketsThatAreNotNewConnections() {
        val packet = ipv4(Packets.PROTOCOL_TCP, tcp(443, 0x12))
        assertNull(Packets.parse(packet, packet.size))
    }

    @Test
    fun readsDnsName() {
        val packet = ipv4(Packets.PROTOCOL_UDP, dnsQuery("example.com"))
        val flow = assertNotNullFlow(Packets.parse(packet, packet.size))
        assertEquals("example.com", flow.dnsName)
    }

    @Test
    fun rejectsDnsLabelRunningPastThePacket() {
        val packet = ipv4(Packets.PROTOCOL_UDP, ByteArray(8).also { it[3] = 53 } + ByteArray(12) + byteArrayOf(60, 97))
        val flow = assertNotNullFlow(Packets.parse(packet, packet.size))
        assertNull(flow.dnsName)
    }

    @Test
    fun rejectsTruncatedHeaders() {
        val packet = ipv4(Packets.PROTOCOL_TCP, tcp(443, 0x02))
        for (length in 0 until 40) {
            assertNull(Packets.parse(packet, length))
        }
        assertNull(Packets.parse(packet, packet.size + 1))
    }

    @Test
    fun survivesRandomInput() {
        val random = Random(42)
        repeat(200_000) {
            val packet = random.nextBytes(random.nextInt(0, 200))
            packet.firstOrNull()?.let { packet[0] = (if (random.nextBoolean()) 0x45 else 0x60).toByte() }
            Packets.parse(packet, packet.size)
        }
    }

    private fun assertNotNullFlow(flow: Flow?): Flow {
        assertNotNull(flow)
        return flow!!
    }
}
