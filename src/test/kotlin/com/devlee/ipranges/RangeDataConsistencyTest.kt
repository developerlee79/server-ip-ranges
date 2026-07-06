package com.devlee.ipranges

import com.devlee.ipranges.core.extractor.RegexConverter
import com.devlee.ipranges.core.io.model.IPRanges
import com.devlee.ipranges.core.provider.Provider
import kotlinx.serialization.json.Json
import java.io.File
import java.math.BigInteger
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals

class RangeDataConsistencyTest {

    /*
    * Keeps runtime low while still covering every provider and both IP versions.
    */
    private val sampleStep = 50

    @Test
    fun sampledRangesConvertToRegexMatchingNetworkAndMidBlockAddress() {
        val failures = mutableListOf<String>()

        for (provider in Provider.entries) {
            val rangeFile = File("./range/${provider.name.lowercase()}/ip-range.json")
            if (!rangeFile.exists()) {
                failures.add("$provider: missing ip-range.json")
                continue
            }

            val allRanges = Json.decodeFromString<List<IPRanges>>(rangeFile.readText())
                .flatMap { it.ranges }
                .filter { '/' in it }

            val samples = allRanges.filterIndexed { index, _ -> index % sampleStep == 0 }

            for (range in samples) {
                val result = runCatching {
                    val regex = Regex(RegexConverter.extract(range))
                    listOf(networkAddress(range), midBlockAddress(range))
                        .filter { !regex.matches(it) }
                }

                when {
                    result.isFailure ->
                        failures.add("$provider: $range -> ${result.exceptionOrNull()}")
                    result.getOrThrow().isNotEmpty() ->
                        failures.add("$provider: $range -> regex misses ${result.getOrThrow()}")
                }
            }
        }

        assertEquals(emptyList(), failures)
    }

    private fun networkAddress(range: String): String =
        InetAddress.getByName(range.substringBefore('/')).hostAddress

    /*
    * Interior address halfway into the CIDR block; catches generation bugs that
    * boundary-only checks cannot (block edges can match even when the interior is broken).
    */
    private fun midBlockAddress(range: String): String {
        val base = InetAddress.getByName(range.substringBefore('/'))
        val cidr = range.substringAfter('/').toInt()
        val addressBytes = base.address
        val totalBits = addressBytes.size * 8

        val midValue = BigInteger(1, addressBytes)
            .add(BigInteger.ONE.shiftLeft(totalBits - cidr).shiftRight(1))

        val rawBytes = midValue.toByteArray()
        val midBytes = ByteArray(addressBytes.size)
        val copyLength = minOf(rawBytes.size, midBytes.size)
        System.arraycopy(rawBytes, rawBytes.size - copyLength, midBytes, midBytes.size - copyLength, copyLength)

        return InetAddress.getByAddress(midBytes).hostAddress
    }

}
