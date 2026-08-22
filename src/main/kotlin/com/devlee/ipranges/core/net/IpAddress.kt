package com.devlee.ipranges.core.net

import java.net.InetAddress

/**
 * A parsed IP literal held as a fixed-width unsigned integer.
 *
 * IPv4 addresses keep [high] at zero and store the 32-bit value in [low];
 * IPv6 addresses split the unsigned 128-bit value across [high] and [low].
 */
data class IpAddress(
    val version: Int,
    val high: Long,
    val low: Long
) {

    fun toBytes(): ByteArray = when (version) {
        VERSION_4 -> ByteArray(BYTES_V4) { index -> (low ushr ((BYTES_V4 - 1 - index) * 8)).toByte() }
        else -> ByteArray(BYTES_V6) { index ->
            val word = if (index < 8) high else low
            (word ushr ((7 - (index % 8)) * 8)).toByte()
        }
    }

    override fun toString(): String = InetAddress.getByAddress(toBytes()).hostAddress

    companion object {

        const val VERSION_4 = 4
        const val VERSION_6 = 6

        const val BITS_V4 = 32
        const val BITS_V6 = 128

        private const val BYTES_V4 = 4
        private const val BYTES_V6 = 16

        private const val MAX_OCTET_VALUE = 255
        private const val MAX_OCTET_DIGITS = 3

        /**
         * Parses an IP literal, returning null for null, blank, and any input that is not
         * an IPv4 or IPv6 literal.
         *
         * Hostnames are rejected before the [InetAddress] call, so no DNS lookup is ever
         * performed: IPv4 is parsed arithmetically, and only colon-bearing input — which can
         * never be a hostname — reaches [InetAddress.getByName].
         */
        fun parse(text: String?): IpAddress? {
            if (text.isNullOrBlank()) {
                return null
            }

            val trimmed = text.trim()

            parseIpv4(trimmed)?.let { return it }

            if (':' !in trimmed) {
                return null
            }

            val bytes = runCatching { InetAddress.getByName(trimmed).address }.getOrNull()
                ?: return null

            return ofBytes(bytes)
        }

        /*
        * Scans the text once rather than matching a regex and splitting it: IPv4 parsing runs
        * on the hot path of every lookup, and the regex form dominated the cost of one.
        *
        * Leading zeros are rejected rather than accepted, because a reader that treats 010 as
        * octal and one that treats it as decimal disagree on which address was meant.
        */
        private fun parseIpv4(text: String): IpAddress? {
            var value = 0L
            var index = 0

            for (octetIndex in 0 until BYTES_V4) {
                if (index >= text.length || text[index] !in '0'..'9') {
                    return null
                }

                var octet = text[index] - '0'
                var digits = 1
                index++

                while (index < text.length && text[index] in '0'..'9') {
                    if (digits == MAX_OCTET_DIGITS || octet == 0) {
                        return null
                    }
                    octet = octet * 10 + (text[index] - '0')
                    digits++
                    index++
                }

                if (octet > MAX_OCTET_VALUE) {
                    return null
                }

                value = (value shl 8) or octet.toLong()

                if (octetIndex < BYTES_V4 - 1) {
                    if (index >= text.length || text[index] != '.') {
                        return null
                    }
                    index++
                }
            }

            if (index != text.length) {
                return null
            }

            return IpAddress(VERSION_4, 0L, value)
        }

        /*
        * IPv4-mapped IPv6 input such as ::ffff:1.2.3.4 arrives here as four bytes, so it
        * is matched against the IPv4 tables exactly like the plain literal would be.
        */
        fun ofBytes(bytes: ByteArray): IpAddress = when (bytes.size) {
            BYTES_V4 -> IpAddress(VERSION_4, 0L, readUnsigned(bytes, 0, BYTES_V4))
            BYTES_V6 -> IpAddress(VERSION_6, readUnsigned(bytes, 0, 8), readUnsigned(bytes, 8, 8))
            else -> throw IllegalArgumentException("Unsupported address length: ${bytes.size}")
        }

        fun addressBits(version: Int): Int =
            if (version == VERSION_4) BITS_V4 else BITS_V6

        /** Unsigned comparison of two addresses already split into high/low words. */
        fun compare(leftHigh: Long, leftLow: Long, rightHigh: Long, rightLow: Long): Int {
            val high = java.lang.Long.compareUnsigned(leftHigh, rightHigh)
            return if (high != 0) high else java.lang.Long.compareUnsigned(leftLow, rightLow)
        }

        private fun readUnsigned(bytes: ByteArray, offset: Int, length: Int): Long {
            var value = 0L
            for (index in offset until offset + length) {
                value = (value shl 8) or (bytes[index].toLong() and 0xFF)
            }
            return value
        }

    }

}
