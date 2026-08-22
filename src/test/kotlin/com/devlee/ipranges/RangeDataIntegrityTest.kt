package com.devlee.ipranges

import com.devlee.ipranges.core.io.model.IPRanges
import com.devlee.ipranges.core.provider.Provider
import com.devlee.ipranges.util.IPRangeUtil
import kotlinx.serialization.json.Json
import java.io.File
import java.math.BigInteger
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Walks the committed range data through the public API.
 *
 * Boundary and interior addresses are derived with [BigInteger] arithmetic rather than with
 * [com.devlee.ipranges.core.net.CidrBlock], so the index is not checked against its own
 * address maths.
 */
class RangeDataIntegrityTest {

    /* Keeps runtime low while still covering every provider and both IP versions. */
    private val sampleStep = 50

    @Test
    fun `every sampled block matches at its first last and interior address`() {
        val failures = mutableListOf<String>()

        for (provider in Provider.entries) {
            val rangeFile = File("./range/${provider.name.lowercase()}/ip-range.json")
            if (!rangeFile.exists()) {
                failures.add("$provider: missing ip-range.json")
                continue
            }

            val samples = Json.decodeFromString<List<IPRanges>>(rangeFile.readText())
                .flatMap { it.ranges }
                .filterIndexed { index, _ -> index % sampleStep == 0 }

            for (range in samples) {
                for (address in boundaryAndInteriorAddresses(range)) {
                    val match = IPRangeUtil.findMatch(address, provider)

                    when {
                        match == null -> failures.add("$provider: $range does not match $address")
                        !covers(match.matchedRange, address) ->
                            failures.add("$provider: $address matched ${match.matchedRange}, which excludes it")
                    }
                }
            }
        }

        assertEquals(emptyList(), failures)
    }

    @Test
    fun `an unroutable address matches no provider`() {
        for (provider in Provider.entries) {
            assertEquals(null, IPRangeUtil.findMatch("0.0.0.0", provider), "$provider matched 0.0.0.0")
        }
    }

    private fun boundaryAndInteriorAddresses(range: String): List<String> {
        val base = InetAddress.getByName(range.substringBefore('/'))
        val prefixLength = range.substringAfter('/').toInt()

        val addressBytes = base.address
        val hostBits = addressBytes.size * 8 - prefixLength
        val hostMask = BigInteger.ONE.shiftLeft(hostBits).subtract(BigInteger.ONE)

        val network = BigInteger(1, addressBytes).andNot(hostMask)
        val last = network.add(hostMask)
        val interior = network.add(BigInteger.ONE.shiftLeft(hostBits).shiftRight(1))

        return listOf(network, interior, last).map { toAddressText(it, addressBytes.size) }
    }

    private fun covers(cidr: String, address: String): Boolean {
        val prefixLength = cidr.substringAfter('/').toInt()
        val blockBytes = InetAddress.getByName(cidr.substringBefore('/')).address
        val addressBytes = InetAddress.getByName(address).address

        if (blockBytes.size != addressBytes.size) {
            return false
        }

        val hostBits = blockBytes.size * 8 - prefixLength
        val hostMask = BigInteger.ONE.shiftLeft(hostBits).subtract(BigInteger.ONE)

        return BigInteger(1, addressBytes).andNot(hostMask) == BigInteger(1, blockBytes).andNot(hostMask)
    }

    private fun toAddressText(value: BigInteger, byteCount: Int): String {
        val rawBytes = value.toByteArray()
        val bytes = ByteArray(byteCount)
        val copyLength = minOf(rawBytes.size, byteCount)

        System.arraycopy(rawBytes, rawBytes.size - copyLength, bytes, byteCount - copyLength, copyLength)

        return InetAddress.getByAddress(bytes).hostAddress
    }

}
