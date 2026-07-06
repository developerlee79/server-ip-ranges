package com.devlee.ipranges

import com.devlee.ipranges.core.io.model.IPRegex
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class IPRegexSerializationTest {

    @Test
    fun encodeAndDecodeRoundTripPreservesPatterns() {
        val original = listOf(
            IPRegex(
                name = "test-region",
                regex = listOf(
                    Regex("10\\.0\\.0\\.(\\d{1,2}|1\\d{2}|2[0-4]\\d|25[0-5])"),
                    Regex("2001:db8:[0-9a-f]{1,4}")
                )
            )
        )

        val encoded = Json.encodeToString(original)
        val decoded = Json.decodeFromString<List<IPRegex>>(encoded)

        assertEquals(original.size, decoded.size)
        assertEquals(original[0].name, decoded[0].name)
        assertEquals(
            original[0].regex.map { it.pattern },
            decoded[0].regex.map { it.pattern }
        )
    }

    @Test
    fun decodeReadsExistingFileFormat() {
        val json = """[{"name":"rg-1","regex":["10\\.0\\.0\\.1"]}]"""
        val decoded = Json.decodeFromString<List<IPRegex>>(json)
        assertEquals("rg-1", decoded[0].name)
        assertEquals(true, decoded[0].regex[0].matches("10.0.0.1"))
    }

}
