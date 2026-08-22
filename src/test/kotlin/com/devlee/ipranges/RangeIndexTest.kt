package com.devlee.ipranges

import com.devlee.ipranges.core.index.RangeIndex
import com.devlee.ipranges.core.index.RangeIndexBuilder
import com.devlee.ipranges.core.io.model.IPRanges
import com.devlee.ipranges.core.net.IpAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RangeIndexTest {

    private fun indexOf(vararg groups: Pair<String, List<String>>): RangeIndex =
        RangeIndexBuilder.build(groups.map { IPRanges(it.first, it.second) })

    private fun RangeIndex.match(ip: String) =
        find(assertNotNull(IpAddress.parse(ip))) { true }

    @Test
    fun `matches the first the last and an interior address of a block`() {
        val index = indexOf("test-region" to listOf("192.0.2.0/24"))

        for (ip in listOf("192.0.2.0", "192.0.2.128", "192.0.2.255")) {
            val match = assertNotNull(index.match(ip), "expected $ip to match")
            assertEquals("test-region", match.region)
            assertEquals("192.0.2.0/24", match.cidr)
        }
    }

    @Test
    fun `returns null for addresses just outside a block`() {
        val index = indexOf("test-region" to listOf("192.0.2.0/24"))

        assertNull(index.match("192.0.1.255"))
        assertNull(index.match("192.0.3.0"))
    }

    @Test
    fun `returns null when the index is empty`() {
        val index = indexOf()

        assertNull(index.match("192.0.2.1"))
        assertNull(index.match("2001:db8::1"))
        assertEquals(0, index.size)
    }

    /*
    * Real provider feeds nest blocks (a /8 published alongside a /24 inside it), which a
    * plain "greatest start below the target" search would get wrong.
    */
    @Test
    fun `prefers the most specific block when ranges are nested`() {
        val index = indexOf(
            "wide" to listOf("10.0.0.0/8"),
            "narrow" to listOf("10.1.2.0/24")
        )

        assertEquals("narrow", assertNotNull(index.match("10.1.2.5")).region)
        assertEquals("wide", assertNotNull(index.match("10.9.9.9")).region)
    }

    /*
    * Providers publish different-sized blocks at the same network address: Amazon lists both
    * 15.193.0.0/19 (GLOBAL) and 15.193.0.0/24 (ap-south-1). Specificity must decide, not the
    * order the feed happens to use, so both orderings are checked.
    */
    @Test
    fun `prefers the most specific block when ranges share a start address`() {
        val broaderFirst = indexOf(
            "wide" to listOf("15.193.0.0/19"),
            "narrow" to listOf("15.193.0.0/24")
        )
        val narrowerFirst = indexOf(
            "narrow" to listOf("15.193.0.0/24"),
            "wide" to listOf("15.193.0.0/19")
        )

        for (index in listOf(broaderFirst, narrowerFirst)) {
            val inner = assertNotNull(index.match("15.193.0.5"))
            assertEquals("narrow", inner.region)
            assertEquals("15.193.0.0/24", inner.cidr)

            /* Outside the /24 but still inside the /19. */
            val outer = assertNotNull(index.match("15.193.16.5"))
            assertEquals("wide", outer.region)
            assertEquals("15.193.0.0/19", outer.cidr)
        }
    }

    @Test
    fun `region filter falls through to an enclosing block`() {
        val index = indexOf(
            "wide" to listOf("10.0.0.0/8"),
            "narrow" to listOf("10.1.2.0/24")
        )
        val address = assertNotNull(IpAddress.parse("10.1.2.5"))

        assertEquals("wide", assertNotNull(index.find(address) { it == "wide" }).region)
        assertEquals("narrow", assertNotNull(index.find(address) { it == "narrow" }).region)
        assertNull(index.find(address) { it == "absent" })
    }

    @Test
    fun `matches IPv6 blocks without leaking into the IPv4 table`() {
        val index = indexOf("test-region" to listOf("192.0.2.0/24", "2001:db8::/32"))

        assertEquals("2001:db8:0:0:0:0:0:0/32", assertNotNull(index.match("2001:db8::1")).cidr)
        assertEquals("192.0.2.0/24", assertNotNull(index.match("192.0.2.1")).cidr)
        assertNull(index.match("2001:dba::1"))
    }

    @Test
    fun `matches a single address IPv6 block`() {
        val index = indexOf("test-region" to listOf("2001:db8::1/128"))

        assertNotNull(index.match("2001:db8::1"))
        assertNull(index.match("2001:db8::2"))
    }

    @Test
    fun `matches IPv4 mapped IPv6 input against IPv4 blocks`() {
        val index = indexOf("test-region" to listOf("192.0.2.0/24"))

        assertEquals("192.0.2.0/24", assertNotNull(index.match("::ffff:192.0.2.7")).cidr)
    }

    @Test
    fun `reports the block in canonical form even when the feed carries host bits`() {
        val index = indexOf("test-region" to listOf("192.0.2.77/24"))

        assertEquals("192.0.2.0/24", assertNotNull(index.match("192.0.2.1")).cidr)
    }

    @Test
    fun `rejects ranges that are not valid CIDR notation`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            indexOf("test-region" to listOf("192.0.2.0"))
        }

        assertEquals(true, failure.message?.contains("192.0.2.0"))
    }

}
