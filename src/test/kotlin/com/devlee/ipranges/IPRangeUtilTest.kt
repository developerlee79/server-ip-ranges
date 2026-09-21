package com.devlee.ipranges

import com.devlee.ipranges.core.io.RangeFileUtil
import com.devlee.ipranges.core.provider.Provider
import com.devlee.ipranges.util.IPRangeData
import com.devlee.ipranges.util.IPRangeUtil
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class IPRangeUtilTest {

    @BeforeTest
    fun useFixtureTables() {
        val directory = RangeFixtures.temporaryDirectory("ipranges-util")
        RangeFixtures.writePackedTables(directory, Provider.entries)

        IPRangeData.usePackedDirectory(directory)
    }

    @AfterTest
    fun resetDataSource() {
        RangeFileUtil.usePackedDirectory(null)
    }

    @Test
    fun isServerIPReturnsFalseForNullOrBlank() {
        assertEquals(false, IPRangeUtil.isServerIP(null))
        assertEquals(false, IPRangeUtil.isServerIP(""))
        assertEquals(false, IPRangeUtil.isServerIP("   "))
        assertEquals(false, IPRangeUtil.isServerIP(null, Provider.Amazon))
    }

    @Test
    fun isServerIPReturnsFalseForNonIPLiteralWithoutDnsLookup() {
        assertEquals(false, IPRangeUtil.isServerIP("host.example.com"))
        assertEquals(false, IPRangeUtil.isServerIP("not-an-ip", Provider.Amazon))
    }

    @Test
    fun isServerIPReturnsFalseForLoopbackAddress() {
        assertEquals(false, IPRangeUtil.isServerIP("127.0.0.1"))
    }

    @Test
    fun findMatchReturnsProviderAndRegionForKnownRange() {
        val match = IPRangeUtil.findMatch(RangeFixtures.INSIDE_V4, Provider.Amazon)

        assertNotNull(match)
        assertEquals(Provider.Amazon, match.provider)
        assertEquals(RangeFixtures.REGION, match.region)
        assertEquals("203.0.113.0/24", match.matchedRange)
        assertEquals(true, IPRangeUtil.isServerIP(RangeFixtures.INSIDE_V4, Provider.Amazon))
    }

    @Test
    fun findMatchSupportsIPv6Addresses() {
        val match = IPRangeUtil.findMatch(RangeFixtures.INSIDE_V6, Provider.Amazon)

        assertNotNull(match)
        assertEquals(Provider.Amazon, match.provider)
        assertEquals("2001:db8:0:0:0:0:0:0/32", match.matchedRange)
    }

    /*
    * isServerIP takes a lookup path that never materializes the matched CIDR string, so it
    * could silently drift from findMatch. Every provider is checked against both in-range
    * addresses and inputs that must not match.
    */
    @Test
    fun isServerIPAgreesWithFindMatchForEveryProvider() {
        val addresses = listOf(
            RangeFixtures.INSIDE_V4,
            RangeFixtures.INSIDE_V6,
            RangeFixtures.OUTSIDE_V4,
            "127.0.0.1",
            "::1",
            "not-an-ip",
            null
        )

        for (provider in Provider.entries) {
            for (ip in addresses) {
                assertEquals(
                    IPRangeUtil.findMatch(ip, provider) != null,
                    IPRangeUtil.isServerIP(ip, provider),
                    "isServerIP disagreed with findMatch for $ip on $provider"
                )
            }
        }
    }

    @Test
    fun isServerIPWithRegionAgreesWithFindMatch() {
        val testIP = RangeFixtures.INSIDE_V4

        assertEquals(true, IPRangeUtil.isServerIP(testIP, Provider.Amazon, RangeFixtures.REGION))
        assertEquals(false, IPRangeUtil.isServerIP(testIP, Provider.Amazon, "no-such-region"))
        assertEquals(IPRangeUtil.findMatch(testIP) != null, IPRangeUtil.isServerIP(testIP))
    }

}
