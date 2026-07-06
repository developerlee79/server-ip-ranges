package com.devlee.ipranges.core.extractor.version

import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

class IPv6RegexConverter {

    companion object {

        private const val SEGMENT_BITS = 16
        private const val SEGMENT_COUNT = 8
        private const val NIBBLE_BITS = 4

        private const val ANY_SEGMENT = "[0-9a-f]{1,4}"

        fun extract(ip: String, cidr: Int?): String {
            if (ip.isBlank()) {
                throw IllegalArgumentException("Empty IP Address")
            }

            val normalized = normalize(ip)

            if (cidr == null || cidr >= SEGMENT_BITS * SEGMENT_COUNT) {
                return normalized
            }

            val segments = normalized.split(':')

            val fullSegmentCount = cidr / SEGMENT_BITS
            val bitsInPartial = cidr % SEGMENT_BITS

            val variablePart = buildVariableSegments(fullSegmentCount, bitsInPartial)
            val regexBuilder = StringBuilder()
                .append(buildFixedSegments(segments, fullSegmentCount))
                .append(buildPartialSegment(segments, fullSegmentCount, bitsInPartial, variablePart.isNotEmpty()))
                .append(variablePart)

            return regexBuilder.toString().trimEnd(':')
        }

        private fun normalize(ip: String): String {
            return try {
                val address = InetAddress.getByName(ip)
                if (address !is Inet6Address) {
                    throw UnknownHostException("Invalid IP Address")
                }
                address.hostAddress
            } catch (e: UnknownHostException) {
                throw e
            } catch (e: Exception) {
                throw UnknownHostException("Invalid IP Address")
            }
        }

        private fun buildFixedSegments(segments: List<String>, count: Int): String {
            if (count == 0) return ""
            return segments.take(count).joinToString(":") + ":"
        }

        private fun buildPartialSegment(
            segments: List<String>,
            fullSegmentIndex: Int,
            bitsInPartial: Int,
            hasVariableSegments: Boolean
        ): String {
            if (bitsInPartial == 0) return ""

            val segmentValue = segments[fullSegmentIndex].toInt(16)
            val freeBits = SEGMENT_BITS - bitsInPartial
            val blockLow = segmentValue and ((1 shl freeBits) - 1).inv()
            val segmentRegex = convertCidrBlockToRegex(blockLow, freeBits)
            return if (hasVariableSegments) "$segmentRegex:" else segmentRegex
        }

        private fun buildVariableSegments(fullSegmentCount: Int, bitsInPartial: Int): String {
            val variableCount = when (bitsInPartial) {
                0 -> SEGMENT_COUNT - fullSegmentCount
                else -> SEGMENT_COUNT - fullSegmentCount - 1
            }
            if (variableCount <= 0) return ""
            return (0 until variableCount).joinToString(":") { ANY_SEGMENT }
        }

        /*
        * Converts the CIDR-aligned block [blockLow, blockLow + 2^freeBits - 1] of one
        * 16-bit segment into a regex matching Java's canonical (unpadded, lowercase)
        * segment representation, e.g. "db8" rather than "0db8".
        */
        private fun convertCidrBlockToRegex(blockLow: Int, freeBits: Int): String {
            val fullNibbles = freeBits / NIBBLE_BITS
            val remBits = freeBits % NIBBLE_BITS

            if (remBits == 0) {
                val fixedValue = blockLow shr freeBits
                if (fixedValue == 0) {
                    return if (fullNibbles == 0) "0" else "[0-9a-f]{1,$fullNibbles}"
                }
                val fixedHex = fixedValue.toString(16)
                return fixedHex + freeNibbles(fullNibbles)
            }

            val lowNibble = (blockLow shr (fullNibbles * NIBBLE_BITS)) and 0xF
            val highNibble = lowNibble + (1 shl remBits) - 1
            val fixedAbove = blockLow shr (fullNibbles * NIBBLE_BITS + NIBBLE_BITS)

            if (fixedAbove != 0) {
                return fixedAbove.toString(16) + nibbleClass(lowNibble, highNibble) + freeNibbles(fullNibbles)
            }

            if (lowNibble > 0 || fullNibbles == 0) {
                return nibbleClass(lowNibble, highNibble) + freeNibbles(fullNibbles)
            }

            /*
            * Leading nibble range starts at zero with free nibbles below, so shorter
            * (unpadded) forms are valid: e.g. block 0x0000-0x1fff matches "1abc" and "abc".
            */
            val fullLengthBranch = nibbleClass(1, highNibble) + freeNibbles(fullNibbles)
            return "($fullLengthBranch|[0-9a-f]{1,$fullNibbles})"
        }

        private fun nibbleClass(low: Int, high: Int): String {
            if (low == high) return low.toString(16)
            return "[${low.toString(16)}-${high.toString(16)}]"
        }

        private fun freeNibbles(count: Int): String = when (count) {
            0 -> ""
            1 -> "[0-9a-f]"
            else -> "[0-9a-f]{$count}"
        }

    }

}
