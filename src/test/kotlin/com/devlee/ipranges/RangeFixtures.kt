package com.devlee.ipranges

import com.devlee.ipranges.core.index.RangeIndexBuilder
import com.devlee.ipranges.core.io.RangeBinaryFormat
import com.devlee.ipranges.core.io.model.IPRanges
import com.devlee.ipranges.core.provider.Provider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Synthetic range data for tests.
 *
 * No provider data is committed any more, so tests build their own from documentation
 * ranges (RFC 5737, RFC 3849) rather than sampling a checked-in feed. That also keeps
 * assertions meaningful: a test deriving its expectations from live data can only ever
 * check that the code agrees with itself.
 */
object RangeFixtures {

    const val REGION = "test-region"

    const val INSIDE_V4 = "203.0.113.7"
    const val INSIDE_V6 = "2001:db8::5"
    const val OUTSIDE_V4 = "198.51.100.7"

    val ranges = listOf(IPRanges(REGION, listOf("203.0.113.0/24", "2001:db8::/32")))

    fun temporaryDirectory(prefix: String): File =
        File.createTempFile(prefix, "").let {
            it.delete()
            it.mkdirs()
            it.deleteOnExit()
            it
        }

    /** Writes `range/<provider>/ip-range.json` for each provider, as a checkout holds it. */
    fun writeSourceJson(
        directory: File,
        providers: Collection<Provider>,
        ranges: List<IPRanges> = RangeFixtures.ranges
    ) {
        for (provider in providers) {
            val file = File(directory, "range/${provider.name.lowercase()}/ip-range.json")
            file.parentFile.mkdirs()
            file.writeText(Json.encodeToString(ranges))
        }
    }

    /** Writes `<provider>.bin` for each provider, as a release download leaves it. */
    fun writePackedTables(
        directory: File,
        providers: Collection<Provider>,
        ranges: List<IPRanges> = RangeFixtures.ranges
    ) {
        directory.mkdirs()
        val index = RangeIndexBuilder.build(ranges)

        for (provider in providers) {
            File(directory, "${provider.name.lowercase()}.bin")
                .outputStream()
                .use { RangeBinaryFormat.write(index, it) }
        }
    }

}
