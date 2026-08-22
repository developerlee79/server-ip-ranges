package com.devlee.ipranges

import com.devlee.ipranges.core.net.CidrBlock
import com.devlee.ipranges.core.net.IpAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CidrBlockTest {

    @Test
    fun `masks host bits off the network address`() {
        val block = assertNotNull(CidrBlock.parse("192.0.2.5/24"))

        assertEquals("192.0.2.0/24", block.toCanonicalString())
        assertEquals(0xC0000200L, block.startLow)
        assertEquals(0xC00002FFL, block.endLow)
    }

    @Test
    fun `single address prefix spans exactly one address`() {
        val v4 = assertNotNull(CidrBlock.parse("192.0.2.5/32"))
        assertEquals(v4.startLow, v4.endLow)

        val v6 = assertNotNull(CidrBlock.parse("2001:db8::1/128"))
        assertEquals(v6.startHigh, v6.endHigh)
        assertEquals(v6.startLow, v6.endLow)
        assertEquals("2001:db8:0:0:0:0:0:1/128", v6.toCanonicalString())
    }

    @Test
    fun `zero prefix spans the whole address space`() {
        val v4 = assertNotNull(CidrBlock.parse("192.0.2.5/0"))
        assertEquals(0L, v4.startLow)
        assertEquals(0xFFFFFFFFL, v4.endLow)

        val v6 = assertNotNull(CidrBlock.parse("2001:db8::/0"))
        assertEquals(0L, v6.startHigh)
        assertEquals(0L, v6.startLow)
        assertEquals(-1L, v6.endHigh)
        assertEquals(-1L, v6.endLow)
    }

    /*
    * Prefix lengths either side of the 64-bit word boundary are where naive shifting breaks,
    * because Kotlin's shl on Long is defined modulo 64.
    */
    @Test
    fun `handles IPv6 prefixes spanning the word boundary`() {
        val exactly64 = assertNotNull(CidrBlock.parse("2001:db8:1:2:3:4:5:6/64"))
        assertEquals(0x20010db800010002L, exactly64.startHigh)
        assertEquals(0L, exactly64.startLow)
        assertEquals(-1L, exactly64.endLow)
        assertEquals(exactly64.startHigh, exactly64.endHigh)

        /* /65 fixes the top bit of the low word, leaving 63 host bits below it. */
        val sixtyFive = assertNotNull(CidrBlock.parse("2001:db8::/65"))
        assertEquals(0L, sixtyFive.startLow)
        assertEquals(Long.MAX_VALUE, sixtyFive.endLow)

        val sixtyFiveUpperHalf = assertNotNull(CidrBlock.parse("2001:db8:0:0:8000::/65"))
        assertEquals(Long.MIN_VALUE, sixtyFiveUpperHalf.startLow)
        assertEquals(-1L, sixtyFiveUpperHalf.endLow)

        val sixtyThree = assertNotNull(CidrBlock.parse("2001:db8:1:3::/63"))
        assertEquals(0x20010db800010002L, sixtyThree.startHigh)
        assertEquals(0x20010db800010003L, sixtyThree.endHigh)
    }

    @Test
    fun `returns null for input that is not a CIDR block`() {
        assertNull(CidrBlock.parse("192.0.2.0"))
        assertNull(CidrBlock.parse("192.0.2.0/33"))
        assertNull(CidrBlock.parse("192.0.2.0/-1"))
        assertNull(CidrBlock.parse("192.0.2.0/x"))
        assertNull(CidrBlock.parse("host.example.com/24"))
        assertNull(CidrBlock.parse("2001:db8::/129"))
    }

    @Test
    fun `of masks the supplied address the same way parse does`() {
        val address = assertNotNull(IpAddress.parse("10.1.2.3"))

        assertEquals("10.1.0.0/16", CidrBlock.of(address, 16).toCanonicalString())
    }

}
