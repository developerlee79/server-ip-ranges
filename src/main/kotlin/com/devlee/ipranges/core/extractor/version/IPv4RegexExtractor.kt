package com.devlee.ipranges.core.extractor.version

import java.net.UnknownHostException

class IPv4RegexExtractor {

    companion object {

        private const val OCTET_PATTERN = "(\\d{1,2}|1\\d{2}|2[0-4]\\d|25[0-5])"
        private const val OCTET_BITS = 8
        private const val OCTET_COUNT = 4
        private const val ADDRESS_BITS = OCTET_BITS * OCTET_COUNT

        internal val IPv4_LITERAL_REGEX = Regex("$OCTET_PATTERN(\\.$OCTET_PATTERN){3}")

        fun extract(ip: String, cidr: Int?): String {
            if (ip.isBlank()) {
                throw IllegalArgumentException("Empty IP Address")
            }

            if (!ip.matches(IPv4_LITERAL_REGEX)) {
                throw UnknownHostException("Invalid IP Address")
            }

            if (cidr == null || cidr >= ADDRESS_BITS) {
                return ip.replace(".", "\\.")
            }

            val addressArray = ip.split('.')

            val quartet = addressArray[cidr / OCTET_BITS].toInt()
            val quartetRange = findIPRange(quartet, cidr)

            val regexBuilder = StringBuilder()
                .append(extractFixedQuartet(addressArray, cidr))
                .append(convertRangeToRegexString(quartetRange))
                .append(extractLeftQuartet(cidr))

            return regexBuilder.toString()
        }

        private fun findIPRange(quartet: Int, cidr: Int): IntRange {
            val blockSize = 1.shl(OCTET_BITS - (cidr % OCTET_BITS))

            val low = quartet and (blockSize - 1).inv()
            val high = low + blockSize - 1

            return low .. high
        }

        private fun extractFixedQuartet(addressArray: List<String>, cidr: Int): String {
            val quartetStringBuilder = StringBuilder()
            val indexOfUnfixedQuartet = cidr / OCTET_BITS

            for (i in 0 until indexOfUnfixedQuartet) {
                quartetStringBuilder
                    .append(addressArray[i])
                    .append("\\.")
            }

            return quartetStringBuilder.toString()
        }

        private fun extractLeftQuartet(cidr: Int): String {
            val quartetStringBuilder = StringBuilder()
            val indexOfUnfixedQuartet = cidr / OCTET_BITS

            for (i in indexOfUnfixedQuartet + 1 until OCTET_COUNT) {
                quartetStringBuilder
                    .append("\\.")
                    .append(OCTET_PATTERN)
            }

            return quartetStringBuilder.toString()
        }

        private fun convertRangeToRegexString(range: IntRange): String {
            if (range == 0 .. 255) {
                return OCTET_PATTERN
            }

            return "(" + buildRangeBranches(range.first, range.last).joinToString("|") + ")"
        }

        /*
        * Decimal values match unpadded, so [low, high] is split into same-digit-length
        * subranges ([0,9], [10,99], [100,255]) before digit-wise regex generation.
        */
        private fun buildRangeBranches(low: Int, high: Int): List<String> {
            val digitLengthBands = listOf(0..9, 10..99, 100..255)

            return digitLengthBands.flatMap { band ->
                val bandLow = maxOf(low, band.first)
                val bandHigh = minOf(high, band.last)

                if (bandLow > bandHigh) {
                    emptyList()
                } else {
                    sameLengthRangeBranches(bandLow.toString(), bandHigh.toString())
                }
            }
        }

        private fun sameLengthRangeBranches(low: String, high: String): List<String> {
            if (low == high) {
                return listOf(low)
            }

            if (low.length == 1) {
                return listOf(digitClass(low[0], high[0]))
            }

            if (low[0] == high[0]) {
                return sameLengthRangeBranches(low.drop(1), high.drop(1)).map { "${low[0]}$it" }
            }

            val restLength = low.length - 1
            val zeros = "0".repeat(restLength)
            val nines = "9".repeat(restLength)

            val branches = mutableListOf<String>()

            var middleLowDigit = low[0]
            var middleHighDigit = high[0]

            if (low.drop(1) != zeros) {
                middleLowDigit++
                branches += sameLengthRangeBranches(low.drop(1), nines).map { "${low[0]}$it" }
            }

            val highBranches = if (high.drop(1) != nines) {
                middleHighDigit--
                sameLengthRangeBranches(zeros, high.drop(1)).map { "${high[0]}$it" }
            } else {
                emptyList()
            }

            if (middleLowDigit <= middleHighDigit) {
                branches += digitClass(middleLowDigit, middleHighDigit) + "[0-9]".repeat(restLength)
            }

            return branches + highBranches
        }

        private fun digitClass(low: Char, high: Char): String {
            if (low == high) return low.toString()
            return "[$low-$high]"
        }

    }

}
