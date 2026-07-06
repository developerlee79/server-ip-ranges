package com.devlee.ipranges

import com.devlee.ipranges.core.io.model.IPRanges
import com.devlee.ipranges.core.provider.Provider
import com.devlee.ipranges.util.IPRangeUtil
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class IPRangeUtilTest {

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
    fun findMatchReturnsProviderAndRegionForKnownAmazonRange() {
        val testIP = firstAmazonIPv4NetworkAddress()

        val match = IPRangeUtil.findMatch(testIP, Provider.Amazon)

        assertNotNull(match, "Expected $testIP to match an Amazon range")
        assertEquals(Provider.Amazon, match.provider)
        assertEquals(true, match.region.isNotBlank())
        assertEquals(true, IPRangeUtil.isServerIP(testIP, Provider.Amazon))
        assertEquals(true, IPRangeUtil.isServerIP(testIP, Provider.Amazon, match.region))
    }

    @Test
    fun findMatchSupportsIPv6Addresses() {
        val testIP = firstAmazonNetworkAddress { '/' in it && ':' in it }

        val match = IPRangeUtil.findMatch(testIP, Provider.Amazon)

        assertNotNull(match, "Expected $testIP to match an Amazon IPv6 range")
        assertEquals(Provider.Amazon, match.provider)
    }

    /*
    * Derives a deterministic in-range IP from the committed range file so the test
    * stays valid whenever the range data is regenerated.
    */
    private fun firstAmazonIPv4NetworkAddress(): String =
        firstAmazonNetworkAddress { '/' in it && '.' in it }

    private fun firstAmazonNetworkAddress(predicate: (String) -> Boolean): String {
        val ranges = Json { prettyPrint = true }
            .decodeFromString<List<IPRanges>>(File("./range/amazon/ip-range.json").readText())

        val cidrRange = ranges.asSequence()
            .flatMap { it.ranges }
            .first(predicate)

        return cidrRange.substringBefore('/')
    }

}
