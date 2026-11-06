package dev.nekuz.appwall

import java.net.InetAddress

data class Flow(
    val protocol: Int,
    val source: InetAddress,
    val sourcePort: Int,
    val destination: InetAddress,
    val destinationPort: Int,
    val dnsName: String?,
)

object Packets {
    const val PROTOCOL_TCP = 6
    const val PROTOCOL_UDP = 17

    private const val IPV4_MIN_HEADER = 20
    private const val IPV6_HEADER = 40
    private const val UDP_HEADER = 8
    private const val DNS_HEADER = 12
    private const val DNS_PORT = 53
    private const val TCP_SYN = 0x02
    private const val TCP_ACK = 0x10
    private const val MAX_DNS_NAME = 253

    fun parse(packet: ByteArray, length: Int): Flow? {
        if (length < 1 || length > packet.size) return null
        return when (packet[0].toInt() ushr 4 and 0xF) {
            4 -> parseIpv4(packet, length)
            6 -> parseIpv6(packet, length)
            else -> null
        }
    }

    private fun parseIpv4(packet: ByteArray, length: Int): Flow? {
        if (length < IPV4_MIN_HEADER) return null
        val headerLength = (packet[0].toInt() and 0xF) * 4
        if (headerLength < IPV4_MIN_HEADER || headerLength > length) return null
        val fragmentOffset = u16(packet, 6) and 0x1FFF
        if (fragmentOffset != 0) return null
        val source = InetAddress.getByAddress(packet.copyOfRange(12, 16))
        val destination = InetAddress.getByAddress(packet.copyOfRange(16, 20))
        return parseTransport(packet, length, headerLength, u8(packet, 9), source, destination)
    }

    private fun parseIpv6(packet: ByteArray, length: Int): Flow? {
        if (length < IPV6_HEADER) return null
        val source = InetAddress.getByAddress(packet.copyOfRange(8, 24))
        val destination = InetAddress.getByAddress(packet.copyOfRange(24, 40))
        return parseTransport(packet, length, IPV6_HEADER, u8(packet, 6), source, destination)
    }

    private fun parseTransport(
        packet: ByteArray,
        length: Int,
        offset: Int,
        protocol: Int,
        source: InetAddress,
        destination: InetAddress,
    ): Flow? {
        return when (protocol) {
            PROTOCOL_TCP -> {
                if (length < offset + IPV4_MIN_HEADER) return null
                val flags = u8(packet, offset + 13)
                if (flags and TCP_SYN == 0 || flags and TCP_ACK != 0) return null
                Flow(protocol, source, u16(packet, offset), destination, u16(packet, offset + 2), null)
            }
            PROTOCOL_UDP -> {
                if (length < offset + UDP_HEADER) return null
                val port = u16(packet, offset + 2)
                val name = if (port == DNS_PORT) parseDnsName(packet, length, offset + UDP_HEADER) else null
                Flow(protocol, source, u16(packet, offset), destination, port, name)
            }
            else -> null
        }
    }

    private fun parseDnsName(packet: ByteArray, length: Int, offset: Int): String? {
        var position = offset + DNS_HEADER
        val name = StringBuilder()
        while (true) {
            if (position >= length) return null
            val labelLength = u8(packet, position)
            position++
            if (labelLength == 0) break
            if (labelLength > 63 || position + labelLength > length) return null
            if (name.isNotEmpty()) name.append('.')
            for (index in position until position + labelLength) {
                val char = u8(packet, index).toChar()
                if (char.code !in 0x21..0x7E) return null
                name.append(char)
            }
            if (name.length > MAX_DNS_NAME) return null
            position += labelLength
        }
        return name.toString().takeIf { it.isNotEmpty() }
    }

    private fun u8(packet: ByteArray, index: Int) = packet[index].toInt() and 0xFF

    private fun u16(packet: ByteArray, index: Int) = (u8(packet, index) shl 8) or u8(packet, index + 1)
}
