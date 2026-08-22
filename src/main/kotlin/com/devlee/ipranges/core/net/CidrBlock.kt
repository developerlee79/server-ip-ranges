package com.devlee.ipranges.core.net

/**
 * A CIDR block normalized to its network address: host bits present in the source text
 * (`10.0.0.5/24`) are masked off, so [startHigh]/[startLow] always hold the network address
 * and [endHigh]/[endLow] the last address of the block.
 */
class CidrBlock private constructor(
    val version: Int,
    val startHigh: Long,
    val startLow: Long,
    val prefixLength: Int
) {

    val endHigh: Long
    val endLow: Long

    init {
        val hostBits = IpAddress.addressBits(version) - prefixLength
        endHigh = startHigh or highHostMask(hostBits)
        endLow = startLow or lowHostMask(hostBits)
    }

    fun toCanonicalString(): String =
        "${IpAddress(version, startHigh, startLow)}/$prefixLength"

    override fun toString(): String = toCanonicalString()

    companion object {

        fun of(address: IpAddress, prefixLength: Int): CidrBlock {
            require(prefixLength in 0..IpAddress.addressBits(address.version)) {
                "Prefix length $prefixLength out of range for IPv${address.version}"
            }

            val hostBits = IpAddress.addressBits(address.version) - prefixLength

            return CidrBlock(
                version = address.version,
                startHigh = address.high and highHostMask(hostBits).inv(),
                startLow = address.low and lowHostMask(hostBits).inv(),
                prefixLength = prefixLength
            )
        }

        /** Returns null when [text] is not `<ip literal>/<prefix length>`. */
        fun parse(text: String): CidrBlock? {
            val separator = text.indexOf('/')
            if (separator < 0) {
                return null
            }

            val address = IpAddress.parse(text.substring(0, separator)) ?: return null
            val prefixLength = text.substring(separator + 1).trim().toIntOrNull() ?: return null

            if (prefixLength !in 0..IpAddress.addressBits(address.version)) {
                return null
            }

            return of(address, prefixLength)
        }

        /*
        * Kotlin's shl on Long is defined modulo 64, so shift counts at or beyond a word
        * boundary are branched on rather than shifted.
        */
        internal fun lowHostMask(hostBits: Int): Long = when {
            hostBits <= 0 -> 0L
            hostBits >= 64 -> -1L
            else -> (1L shl hostBits) - 1L
        }

        internal fun highHostMask(hostBits: Int): Long = when {
            hostBits <= 64 -> 0L
            hostBits >= 128 -> -1L
            else -> (1L shl (hostBits - 64)) - 1L
        }

    }

}
