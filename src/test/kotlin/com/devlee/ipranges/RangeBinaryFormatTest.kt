package com.devlee.ipranges

import com.devlee.ipranges.core.index.RangeIndex
import com.devlee.ipranges.core.index.RangeIndexBuilder
import com.devlee.ipranges.core.io.RangeBinaryFormat
import com.devlee.ipranges.core.io.model.IPRanges
import com.devlee.ipranges.core.net.IpAddress
import com.devlee.ipranges.core.provider.Provider
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class RangeBinaryFormatTest {

    private fun roundTrip(index: RangeIndex): RangeIndex {
        val buffer = ByteArrayOutputStream()
        RangeBinaryFormat.write(index, buffer)
        return RangeBinaryFormat.read(ByteArrayInputStream(buffer.toByteArray()))
    }

    private fun RangeIndex.match(ip: String) =
        find(assertNotNull(IpAddress.parse(ip))) { true }

    @Test
    fun `round trip preserves lookups across regions and both IP versions`() {
        val original = RangeIndexBuilder.build(
            listOf(
                IPRanges("region-one", listOf("192.0.2.0/24", "2001:db8::/32")),
                IPRanges("region-two", listOf("198.51.100.0/22", "2001:db8:1::1/128"))
            )
        )

        val decoded = roundTrip(original)

        assertEquals(original.size, decoded.size)
        for (ip in listOf("192.0.2.9", "198.51.100.1", "2001:db8::5", "2001:db8:1::1")) {
            assertEquals(original.match(ip), decoded.match(ip), "mismatch for $ip")
        }
    }

    /*
    * /0 and /128 sit at the edges of the prefix byte; /128 in particular does not fit in a
    * signed byte and must survive the round trip as an unsigned value.
    */
    @Test
    fun `round trip preserves boundary prefix lengths`() {
        val original = RangeIndexBuilder.build(
            listOf(IPRanges("edge", listOf("0.0.0.0/0", "2001:db8::1/128")))
        )

        val decoded = roundTrip(original)

        assertEquals("0.0.0.0/0", assertNotNull(decoded.match("203.0.113.9")).cidr)
        assertEquals("2001:db8:0:0:0:0:0:1/128", assertNotNull(decoded.match("2001:db8::1")).cidr)
    }

    @Test
    fun `round trip preserves an empty index`() {
        val decoded = roundTrip(RangeIndexBuilder.build(emptyList()))

        assertEquals(0, decoded.size)
    }

    @Test
    fun `rejects a file that is not packed range data`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            RangeBinaryFormat.read(ByteArrayInputStream(ByteArray(32)))
        }

        assertEquals(true, failure.message?.contains("Not a packed range file"))
    }

}
