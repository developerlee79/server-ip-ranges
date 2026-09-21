package com.devlee.ipranges

import com.devlee.ipranges.core.io.RangeFileUtil
import com.devlee.ipranges.core.io.model.IPRanges
import com.devlee.ipranges.core.provider.Provider
import com.devlee.ipranges.util.IPRangeData
import com.devlee.ipranges.util.IPRangeUtil
import java.math.BigInteger
import java.net.InetAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Walks blocks at every prefix length through the public API.
 *
 * Boundary and interior addresses are derived with [BigInteger] arithmetic rather than with
 * [com.devlee.ipranges.core.net.CidrBlock], so the index is not checked against its own
 * address maths.
 */
class RangeDataIntegrityTest {

    /* /0 and /1 would cover the whole space and swallow the negative checks. */
    private val v4Blocks = (2..32).map { prefix -> networkAddressOf("203.0.113.0", prefix) }
    private val v6Blocks = (2..128).map { prefix -> networkAddressOf("2001:db8::", prefix) }

    @AfterTest
    fun resetDataSource() {
        RangeFileUtil.usePackedDirectory(null)
    }

    private fun networkAddressOf(literal: String, prefixLength: Int): String {
        val bytes = InetAddress.getByName(literal).address
        val hostBits = bytes.size * 8 - prefixLength
        val hostMask = BigInteger.ONE.shiftLeft(hostBits).subtract(BigInteger.ONE)

        return "${toAddressText(BigInteger(1, bytes).andNot(hostMask), bytes.size)}/$prefixLength"
    }

    private fun useBlocks(blocks: List<String>) {
        val directory = RangeFixtures.temporaryDirectory("ipranges-integrity")
        RangeFixtures.writePackedTables(
            directory,
            listOf(Provider.Amazon),
            listOf(IPRanges(RangeFixtures.REGION, blocks))
        )

        IPRangeData.usePackedDirectory(directory)
    }

    @Test
    fun `every block matches at its first last and interior address`() {
        val failures = mutableListOf<String>()

        for (blocks in listOf(v4Blocks, v6Blocks)) {
            useBlocks(blocks)

            for (range in blocks) {
                for (address in boundaryAndInteriorAddresses(range)) {
                    val match = IPRangeUtil.findMatch(address, Provider.Amazon)

                    when {
                        match == null -> failures.add("$range does not match $address")
                        !covers(match.matchedRange, address) ->
                            failures.add("$address matched ${match.matchedRange}, which excludes it")
                    }
                }
            }
        }

        assertEquals(emptyList(), failures)
    }

    /*
    * The widest block here is /2, which normalizes to 192.0.0.0/2 and so covers every
    * documentation range above it -- the negative cases have to sit below that.
    */
    @Test
    fun `an address outside every block matches nothing`() {
        useBlocks(v4Blocks)

        assertEquals(null, IPRangeUtil.findMatch("10.0.0.1", Provider.Amazon))
        assertEquals(null, IPRangeUtil.findMatch("0.0.0.0", Provider.Amazon))
    }

    /* Every block contains this address, so only the most specific one may be reported. */
    @Test
    fun `the narrowest block wins where every prefix length overlaps`() {
        useBlocks(v4Blocks)

        assertEquals(
            "203.0.113.0/32",
            IPRangeUtil.findMatch("203.0.113.0", Provider.Amazon)?.matchedRange
        )
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
