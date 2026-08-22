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
        assertEquals(true, match.matchedRange.contains('/'), "Expected a CIDR block, got ${match.matchedRange}")
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
    * isServerIP takes a lookup path that never materializes the matched CIDR string, so it
    * could silently drift from findMatch. Every provider is checked against both a known
    * in-range address and inputs that must not match.
    */
    @Test
    fun isServerIPAgreesWithFindMatchForEveryProvider() {
        for (provider in Provider.entries) {
            for (ip in listOf(firstNetworkAddress(provider), "127.0.0.1", "::1", "not-an-ip", null)) {
                val expected = IPRangeUtil.findMatch(ip, provider) != null

                assertEquals(
                    expected,
                    IPRangeUtil.isServerIP(ip, provider),
                    "isServerIP disagreed with findMatch for $ip on $provider"
                )
            }
        }
    }

    @Test
    fun isServerIPWithRegionAgreesWithFindMatch() {
        val testIP = firstAmazonIPv4NetworkAddress()
        val region = assertNotNull(IPRangeUtil.findMatch(testIP, Provider.Amazon)).region

        assertEquals(true, IPRangeUtil.isServerIP(testIP, Provider.Amazon, region))
        assertEquals(false, IPRangeUtil.isServerIP(testIP, Provider.Amazon, "no-such-region"))
        assertEquals(IPRangeUtil.findMatch(testIP) != null, IPRangeUtil.isServerIP(testIP))
    }

    private fun firstNetworkAddress(provider: Provider): String {
        val ranges = Json { prettyPrint = true }.decodeFromString<List<IPRanges>>(
            File("./range/${provider.name.lowercase()}/ip-range.json").readText()
        )

        return ranges.asSequence().flatMap { it.ranges }.first { '/' in it }.substringBefore('/')
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
