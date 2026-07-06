package com.devlee.ipranges

import com.devlee.ipranges.core.extractor.version.IPv4RegexExtractor
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IPv4RegexExtractorTest {

    @Test
    fun extractThrowsIllegalArgumentExceptionWhenIpIsBlank() {
        assertFailsWith<IllegalArgumentException> {
            IPv4RegexExtractor.extract("", 24)
        }
    }

    @Test
    fun extractThrowsUnknownHostExceptionWhenIpIsInvalid() {
        assertFailsWith<UnknownHostException> {
            IPv4RegexExtractor.extract("256.1.1.1", 24)
        }
        assertFailsWith<UnknownHostException> {
            IPv4RegexExtractor.extract("1.2.3", 24)
        }
    }

    @Test
    fun extractWithFullCidrMatchesOnlyExactAddress() {
        val regex = Regex(IPv4RegexExtractor.extract("1.2.3.4", 32))
        assertEquals(true, regex.matches("1.2.3.4"))
        assertEquals(false, regex.matches("1.2.3.5"))
        assertEquals(false, regex.matches("1a2b3c4"))
    }

    @Test
    fun extractEscapesDotsInFixedQuartets() {
        val regex = Regex(IPv4RegexExtractor.extract("10.0.0.0", 8))
        assertEquals(true, regex.matches("10.1.2.3"))
        assertEquals(false, regex.matches("10x1x2x3"))
    }

    @Test
    fun extractWithCidr8MatchesWholeClassA() {
        val regex = Regex(IPv4RegexExtractor.extract("10.0.0.0", 8))
        assertEquals(true, regex.matches("10.0.0.0"))
        assertEquals(true, regex.matches("10.255.255.255"))
        assertEquals(false, regex.matches("11.0.0.0"))
        assertEquals(false, regex.matches("9.255.255.255"))
    }

    @Test
    fun extractWithCidr27MatchesAlignedBlockBoundaries() {
        val regex = Regex(IPv4RegexExtractor.extract("13.34.3.160", 27))
        assertEquals(true, regex.matches("13.34.3.160"))
        assertEquals(true, regex.matches("13.34.3.191"))
        assertEquals(false, regex.matches("13.34.3.159"))
        assertEquals(false, regex.matches("13.34.3.192"))
    }

    @Test
    fun extractWithCidr22MatchesAlignedBlockBoundaries() {
        val regex = Regex(IPv4RegexExtractor.extract("3.5.140.0", 22))
        assertEquals(true, regex.matches("3.5.140.0"))
        assertEquals(true, regex.matches("3.5.143.255"))
        assertEquals(false, regex.matches("3.5.139.255"))
        assertEquals(false, regex.matches("3.5.144.0"))
    }

    @Test
    fun extractNormalizesUnalignedHostBitsToCidrBlock() {
        val regex = Regex(IPv4RegexExtractor.extract("13.34.3.170", 27))
        assertEquals(true, regex.matches("13.34.3.160"))
        assertEquals(true, regex.matches("13.34.3.191"))
        assertEquals(false, regex.matches("13.34.3.192"))
    }

    /*
    * Exhaustive check over every block of every last-octet CIDR split: each of the
    * 256 octet values must match exactly when it lies inside the block. Interior
    * values are covered, not just block boundaries.
    */
    @Test
    fun extractMatchesExactlyTheCidrBlockForAllLastOctetSplits() {
        for (cidr in 25..31) {
            val blockSize = 1 shl (32 - cidr)

            for (blockStart in 0..255 step blockSize) {
                val regex = Regex(IPv4RegexExtractor.extract("10.0.0.$blockStart", cidr))

                for (value in 0..255) {
                    val expected = value in blockStart until blockStart + blockSize
                    assertEquals(
                        expected,
                        regex.matches("10.0.0.$value"),
                        "cidr=$cidr block=$blockStart value=$value"
                    )
                }
            }
        }
    }

    @Test
    fun extractMatchesExactlyTheCidrBlockForAllSecondOctetSplits() {
        for (cidr in 9..15) {
            val blockSize = 1 shl (16 - cidr)

            for (blockStart in 0..255 step blockSize) {
                val regex = Regex(IPv4RegexExtractor.extract("10.$blockStart.0.0", cidr))

                for (value in 0..255) {
                    val expected = value in blockStart until blockStart + blockSize
                    assertEquals(
                        expected,
                        regex.matches("10.$value.7.7"),
                        "cidr=$cidr block=$blockStart value=$value"
                    )
                }
            }
        }
    }

}
