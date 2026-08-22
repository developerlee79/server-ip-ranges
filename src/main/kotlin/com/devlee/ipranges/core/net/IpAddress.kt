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

        private const val OCTET_PATTERN = "(\\d{1,2}|1\\d{2}|2[0-4]\\d|25[0-5])"

        private val IPV4_LITERAL_REGEX = Regex("$OCTET_PATTERN(\\.$OCTET_PATTERN){3}")

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

            if (IPV4_LITERAL_REGEX.matches(trimmed)) {
                var value = 0L
                for (octet in trimmed.split('.')) {
                    value = (value shl 8) or octet.toLong()
                }
                return IpAddress(VERSION_4, 0L, value)
            }

            if (':' !in trimmed) {
                return null
            }

            val bytes = runCatching { InetAddress.getByName(trimmed).address }.getOrNull()
                ?: return null

            return ofBytes(bytes)
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
