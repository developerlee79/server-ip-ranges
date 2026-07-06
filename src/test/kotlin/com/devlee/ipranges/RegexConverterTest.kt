package com.devlee.ipranges

import com.devlee.ipranges.core.extractor.RegexConverter
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RegexConverterTest {

    @Test
    fun extractWithIpv4CidrReturnsRegexString() {
        val result = RegexConverter.extract("1.2.3.4/24")
        assert(result.isNotEmpty())
        assertEquals(true, Regex(result).matches("1.2.3.4"))
    }

    @Test
    fun extractWithIpv4AndCidrParametersReturnsRegexString() {
        val result = RegexConverter.extract("10.0.0.0", 8)
        assertEquals(true, result.startsWith("10\\."))
        assertEquals(true, Regex(result).matches("10.1.2.3"))
    }

    @Test
    fun extractWithIpv6CidrReturnsRegexString() {
        val result = RegexConverter.extract("2001:db8::/32")
        assertEquals(true, result.contains("2001"))
        assertEquals(true, Regex(result).matches("2001:db8:0:0:0:0:0:1"))
    }

    @Test
    fun extractWithIpv6CompressedNotationReturnsRegexString() {
        val result = RegexConverter.extract("2001:db8::1", 128)
        assertEquals("2001:db8:0:0:0:0:0:1", result)
    }

    @Test
    fun extractThrowsWhenInvalidIpFormat() {
        assertFailsWith<UnknownHostException> {
            RegexConverter.extract("not-an-ip/24")
        }
    }

    @Test
    fun extractThrowsWhenMissingCidr() {
        assertFailsWith<UnknownHostException> {
            RegexConverter.extract("1.2.3.4")
        }
    }

    @Test
    fun extractThrowsWhenTooManySlashes() {
        assertFailsWith<UnknownHostException> {
            RegexConverter.extract("1.2.3.4/24/extra")
        }
    }
}
