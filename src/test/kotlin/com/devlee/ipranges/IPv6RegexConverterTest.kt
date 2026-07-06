package com.devlee.ipranges

import com.devlee.ipranges.core.extractor.version.IPv6RegexConverter
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IPv6RegexConverterTest {

    @Test
    fun extractThrowsIllegalArgumentExceptionWhenIpIsBlank() {
        assertFailsWith<IllegalArgumentException> {
            IPv6RegexConverter.extract("", null)
        }
        assertFailsWith<IllegalArgumentException> {
            IPv6RegexConverter.extract("   ", 64)
        }
    }

    @Test
    fun extractThrowsUnknownHostExceptionWhenIpIsInvalid() {
        assertFailsWith<UnknownHostException> {
            IPv6RegexConverter.extract("not-an-ip", null)
        }
        assertFailsWith<UnknownHostException> {
            IPv6RegexConverter.extract("256.1.1.1", null)
        }
    }

    @Test
    fun extractReturnsNormalizedIpWhenCidrIsNull() {
        val result = IPv6RegexConverter.extract("::1", null)
        assertEquals("0:0:0:0:0:0:0:1", result)
    }

    @Test
    fun extractReturnsNormalizedIpWhenCidrIs128() {
        val result = IPv6RegexConverter.extract("2001:db8::1", 128)
        assertEquals("2001:db8:0:0:0:0:0:1", result)
    }

    @Test
    fun extractReturnsRegexMatchingPrefixRangeForCidr32() {
        val regexStr = IPv6RegexConverter.extract("2001:db8::", 32)
        val regex = Regex(regexStr)
        val prefixHostAddress = InetAddress.getByName("2001:db8::").hostAddress
        val inRangeHostAddress = InetAddress.getByName("2001:db8::1").hostAddress
        assertEquals(true, regex.matches(prefixHostAddress))
        assertEquals(true, regex.matches(inRangeHostAddress))
    }

    @Test
    fun extractReturnsRegexMatchingPrefixRangeForCidr48() {
        val regexStr = IPv6RegexConverter.extract("2001:db8:85a3::", 48)
        val regex = Regex(regexStr)
        val inRangeHostAddress = InetAddress.getByName("2001:db8:85a3:0:0:0:0:1").hostAddress
        assertEquals(true, regex.matches(inRangeHostAddress))
    }

    @Test
    fun extractReturnsRegexMatchingPrefixRangeForCidr64() {
        val regexStr = IPv6RegexConverter.extract("2001:db8:85a3:0::", 64)
        val regex = Regex(regexStr)
        val inRangeHostAddress = InetAddress.getByName("2001:db8:85a3:0:0:0:0:1").hostAddress
        assertEquals(true, regex.matches(inRangeHostAddress))
    }

    @Test
    fun extractAcceptsCompressedNotation() {
        val result = IPv6RegexConverter.extract("2001:db8::1", 128)
        assertEquals("2001:db8:0:0:0:0:0:1", result)
    }

    @Test
    fun extractWithCidrZeroProducesRegexMatchingAnyIpv6() {
        val regexStr = IPv6RegexConverter.extract("::", 0)
        val regex = Regex(regexStr)
        val anyHostAddress = InetAddress.getByName("2001:db8::1").hostAddress
        assertEquals(true, regex.matches(anyHostAddress))
    }

    @Test
    fun extractWithCidr36MatchesPartialSegmentBlock() {
        val regex = Regex(IPv6RegexConverter.extract("2001:db8:1000::", 36))
        assertEquals(true, regex.matches(hostAddress("2001:db8:1000::")))
        assertEquals(true, regex.matches(hostAddress("2001:db8:1fff:ffff::1")))
        assertEquals(false, regex.matches(hostAddress("2001:db8:2000::")))
        assertEquals(false, regex.matches(hostAddress("2001:db8:fff::")))
    }

    @Test
    fun extractWithCidr36MatchesUnpaddedZeroLeadingSegments() {
        val regex = Regex(IPv6RegexConverter.extract("2001:db8::", 36))
        assertEquals(true, regex.matches(hostAddress("2001:db8:0:1::")))
        assertEquals(true, regex.matches(hostAddress("2001:db8:fff::1")))
        assertEquals(false, regex.matches(hostAddress("2001:db8:1000::")))
    }

    @Test
    fun extractWithCidr33WrapsAlternationSafely() {
        val regex = Regex(IPv6RegexConverter.extract("2001:db8::", 33))
        assertEquals(true, regex.matches(hostAddress("2001:db8:7fff::1")))
        assertEquals(true, regex.matches(hostAddress("2001:db8:12::")))
        assertEquals(false, regex.matches(hostAddress("2001:db8:8000::")))
        assertEquals(false, regex.matches(hostAddress("2001:db9::")))
    }

    @Test
    fun extractWithCidr33MatchesUpperHalfBlock() {
        val regex = Regex(IPv6RegexConverter.extract("2001:db8:8000::", 33))
        assertEquals(true, regex.matches(hostAddress("2001:db8:8000::")))
        assertEquals(true, regex.matches(hostAddress("2001:db8:ffff::1")))
        assertEquals(false, regex.matches(hostAddress("2001:db8:7fff::")))
    }

    @Test
    fun extractWithCidr20MatchesPartialSecondSegment() {
        val regex = Regex(IPv6RegexConverter.extract("2001::", 20))
        assertEquals(true, regex.matches(hostAddress("2001:abc::1")))
        assertEquals(true, regex.matches(hostAddress("2001:0:1::")))
        assertEquals(false, regex.matches(hostAddress("2001:1000::")))
        assertEquals(false, regex.matches(hostAddress("2002::")))
    }

    private fun hostAddress(ip: String): String =
        InetAddress.getByName(ip).hostAddress
}
