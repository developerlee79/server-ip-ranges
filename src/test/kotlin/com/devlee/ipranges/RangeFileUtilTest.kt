package com.devlee.ipranges

import com.devlee.ipranges.core.io.DataDirectory
import com.devlee.ipranges.core.io.RangeFileUtil
import com.devlee.ipranges.core.io.model.IPRanges
import com.devlee.ipranges.core.net.IpAddress
import com.devlee.ipranges.core.provider.Provider
import com.devlee.ipranges.util.IPRangeData
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Covers where range data is sourced from now that none ships in the artifact: the JSON a
 * checkout regenerates, or the packed tables a release download leaves in a cache.
 */
class RangeFileUtilTest {

    @AfterTest
    fun resetDataSource() {
        System.clearProperty(DataDirectory.PROPERTY)
        RangeFileUtil.usePackedDirectory(null)
    }

    private fun find(provider: Provider, ip: String) =
        RangeFileUtil.getIndex(provider).find(assertNotNull(IpAddress.parse(ip))) { true }

    private fun useSourceJson(vararg providers: Provider): File {
        val directory = RangeFixtures.temporaryDirectory("ipranges-json")
        RangeFixtures.writeSourceJson(directory, providers.toList())

        System.setProperty(DataDirectory.PROPERTY, directory.absolutePath)
        RangeFileUtil.clearIndexCache()

        return directory
    }

    @Test
    fun `reads packed tables from the configured directory`() {
        val directory = RangeFixtures.temporaryDirectory("ipranges-packed")
        RangeFixtures.writePackedTables(directory, Provider.entries)

        IPRangeData.usePackedDirectory(directory)

        for (provider in Provider.entries) {
            val match = assertNotNull(
                find(provider, RangeFixtures.INSIDE_V4),
                "$provider did not match from its packed table"
            )

            assertEquals(RangeFixtures.REGION, match.region)
            assertNull(find(provider, RangeFixtures.OUTSIDE_V4))
        }
    }

    @Test
    fun `packed tables carry IPv6 blocks`() {
        val directory = RangeFixtures.temporaryDirectory("ipranges-packed-v6")
        RangeFixtures.writePackedTables(directory, listOf(Provider.Amazon))

        IPRangeData.usePackedDirectory(directory)

        assertEquals(
            RangeFixtures.REGION,
            assertNotNull(find(Provider.Amazon, RangeFixtures.INSIDE_V6)).region
        )
    }

    @Test
    fun `source JSON takes precedence over packed tables`() {
        val packed = RangeFixtures.temporaryDirectory("ipranges-packed-loser")
        RangeFixtures.writePackedTables(packed, listOf(Provider.Amazon))
        IPRangeData.usePackedDirectory(packed)

        useSourceJson(Provider.Amazon)
        RangeFileUtil.updateRangeFile(Provider.Amazon) {
            listOf(IPRanges("json-region", listOf("203.0.113.0/24")))
        }

        assertEquals("json-region", assertNotNull(find(Provider.Amazon, RangeFixtures.INSIDE_V4)).region)
    }

    @Test
    fun `a provider missing from the packed directory is a setup error not a miss`() {
        val directory = RangeFixtures.temporaryDirectory("ipranges-partial")
        RangeFixtures.writePackedTables(directory, listOf(Provider.Amazon))

        IPRangeData.usePackedDirectory(directory)

        assertNotNull(find(Provider.Amazon, RangeFixtures.INSIDE_V4))
        assertFailsWith<IllegalStateException> { find(Provider.Google, RangeFixtures.INSIDE_V4) }
    }

    @Test
    fun `lookups fail loudly when no source is configured`() {
        val failure = assertFailsWith<IllegalStateException> {
            find(Provider.Amazon, RangeFixtures.INSIDE_V4)
        }

        assertEquals(true, failure.message?.contains("IPRangeData.useRelease()"))
    }

    @Test
    fun `updateRangeFile writes into the data directory and invalidates the cache`() {
        val directory = useSourceJson(Provider.Amazon)
        assertEquals(
            RangeFixtures.REGION,
            assertNotNull(find(Provider.Amazon, RangeFixtures.INSIDE_V4)).region
        )

        RangeFileUtil.updateRangeFile(Provider.Amazon) {
            listOf(IPRanges("updated-region", listOf("198.51.100.0/24")))
        }

        assertEquals(
            true,
            File(directory, "range/amazon/ip-range.json").readText().contains("updated-region")
        )
        assertNull(find(Provider.Amazon, RangeFixtures.INSIDE_V4), "stale index survived the update")
        assertEquals("updated-region", assertNotNull(find(Provider.Amazon, RangeFixtures.OUTSIDE_V4)).region)
    }

    @Test
    fun `updateRangeFile fails when no data directory is configured`() {
        System.clearProperty(DataDirectory.PROPERTY)

        val failure = assertFailsWith<IllegalStateException> {
            RangeFileUtil.updateRangeFile(Provider.Amazon) { emptyList() }
        }

        assertEquals(true, failure.message?.contains(DataDirectory.PROPERTY))
    }

}
