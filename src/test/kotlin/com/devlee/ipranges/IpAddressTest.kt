package com.devlee.ipranges

import com.devlee.ipranges.core.net.IpAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IpAddressTest {

    @Test
    fun `parses IPv4 literal into its 32 bit value`() {
        val address = assertNotNull(IpAddress.parse("192.0.2.1"))

        assertEquals(IpAddress.VERSION_4, address.version)
        assertEquals(0L, address.high)
        assertEquals(0xC0000201L, address.low)
    }

    @Test
    fun `parses the highest IPv4 address without sign overflow`() {
        val address = assertNotNull(IpAddress.parse("255.255.255.255"))

        assertEquals(0xFFFFFFFFL, address.low)
        assertTrue(address.low > 0)
    }

    @Test
    fun `parses compressed and uppercase IPv6 to the same value`() {
        val compressed = assertNotNull(IpAddress.parse("2001:db8::1"))
        val expanded = assertNotNull(IpAddress.parse("2001:0DB8:0000:0000:0000:0000:0000:0001"))

        assertEquals(compressed, expanded)
        assertEquals(IpAddress.VERSION_6, compressed.version)
        assertEquals(0x20010db800000000L, compressed.high)
        assertEquals(1L, compressed.low)
    }

    @Test
    fun `treats IPv4 mapped IPv6 as IPv4`() {
        assertEquals(IpAddress.parse("192.0.2.1"), IpAddress.parse("::ffff:192.0.2.1"))
    }

    @Test
    fun `returns null for null blank hostname and malformed input`() {
        assertNull(IpAddress.parse(null))
        assertNull(IpAddress.parse(""))
        assertNull(IpAddress.parse("   "))
        assertNull(IpAddress.parse("host.example.com"))
        assertNull(IpAddress.parse("not-an-ip"))
        assertNull(IpAddress.parse("999.0.0.1"))
        assertNull(IpAddress.parse("192.0.2"))
        assertNull(IpAddress.parse(":::"))
    }

    @Test
    fun `parses every octet boundary value`() {
        assertEquals(0L, assertNotNull(IpAddress.parse("0.0.0.0")).low)
        assertEquals(0x0000FF00L, assertNotNull(IpAddress.parse("0.0.255.0")).low)
        assertEquals(0x63636363L, assertNotNull(IpAddress.parse("99.99.99.99")).low)
        assertEquals(0x64646464L, assertNotNull(IpAddress.parse("100.100.100.100")).low)
    }

    /*
    * Leading zeros make an octet ambiguous — some resolvers read 010 as octal — so they are
    * rejected outright, matching Python's ipaddress and Go's net/netip.
    */
    @Test
    fun `rejects octets with leading zeros`() {
        assertNull(IpAddress.parse("192.0.2.01"))
        assertNull(IpAddress.parse("192.0.2.001"))
        assertNull(IpAddress.parse("192.0.2.099"))
        assertNull(IpAddress.parse("010.0.2.1"))
    }

    @Test
    fun `rejects malformed IPv4 shapes`() {
        for (literal in listOf(
            "192.0.2.",
            ".192.0.2",
            "192.0..1",
            "192.0.2.1.5",
            "192.0.2.256",
            "192.0.2.1000",
            "192.0.2.-1",
            "192.0.2.a",
            "192 .0.2.1",
            "1.2.3.4 5"
        )) {
            assertNull(IpAddress.parse(literal), "expected $literal to be rejected")
        }
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals(IpAddress.parse("192.0.2.1"), IpAddress.parse("  192.0.2.1  "))
    }

    @Test
    fun `round trips through bytes and back to text`() {
        for (literal in listOf("192.0.2.1", "0.0.0.0", "255.255.255.255", "2001:db8::1", "::")) {
            val address = assertNotNull(IpAddress.parse(literal))
            assertEquals(address, IpAddress.ofBytes(address.toBytes()))
        }

        assertEquals("192.0.2.1", IpAddress.parse("192.0.2.1").toString())
        assertEquals("2001:db8:0:0:0:0:0:1", IpAddress.parse("2001:db8::1").toString())
    }

    @Test
    fun `compares addresses as unsigned across the sign bit`() {
        val low = assertNotNull(IpAddress.parse("0:0:0:0:0:0:0:1"))
        val high = assertNotNull(IpAddress.parse("ffff::"))

        assertTrue(IpAddress.compare(low.high, low.low, high.high, high.low) < 0)
        assertTrue(IpAddress.compare(high.high, high.low, low.high, low.low) > 0)
        assertEquals(0, IpAddress.compare(low.high, low.low, low.high, low.low))
    }

    @Test
    fun `reports address width per version`() {
        assertEquals(32, IpAddress.addressBits(IpAddress.VERSION_4))
        assertEquals(128, IpAddress.addressBits(IpAddress.VERSION_6))
    }

}
