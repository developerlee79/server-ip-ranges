package com.devlee.ipranges.core.extractor.version

import java.net.UnknownHostException

class IPv4RegexExtractor {

    companion object {

        private val IPv4_REGEX = Regex("(\\d{1,2}|1\\d{2}|2[0-4]\\d|25[0-5]).".repeat(4).dropLast(1))

        fun extract(ip: String, cidr: Int?): String {
            if (ip.isBlank()) {
                throw IllegalArgumentException("Empty IP Address")
            }

            if (!ip.matches(IPv4_REGEX)) {
                throw UnknownHostException("Invalid IP Address")
            }

            if (cidr == null || cidr >= 32) {
                return ip
            }

            val addressArray = ip.split('.')

            val quartet = addressArray[cidr / 8].toInt()
            val quartetRange = findIPRange(quartet, cidr)

            val regexBuilder = StringBuilder()
                .append(extractFixedQuartet(addressArray, cidr))
                .append(convertRangeToRegexString(quartetRange))
                .append(extractLeftQuartet(cidr))

            return regexBuilder.toString()
        }

        private fun findIPRange(quartet: Int, cidr: Int): IntRange {
            val quartetValue = 1.shl(8 - (cidr % 8))

            val low = quartet and quartetValue or quartet
            val high = quartetValue - 1 + low

            return low .. high
        }

        private fun extractFixedQuartet(addressArray: List<String>, cidr: Int): String {
            val quartetStringBuilder = StringBuilder()
            val indexOfUnfixedQuartet = cidr / 8

            for (i in 0 until indexOfUnfixedQuartet) {
                quartetStringBuilder
                    .append(addressArray[i])
                    .append('.')
            }

            return quartetStringBuilder.toString()
        }

        private fun extractLeftQuartet(cidr: Int): String {
            val quartetStringBuilder = StringBuilder()
            val indexOfUnfixedQuartet = cidr / 8

            for (i in indexOfUnfixedQuartet + 1 until 4) {
                quartetStringBuilder
                    .append(".")
                    .append(convertRangeToRegexString(0 .. 255))
            }

            return quartetStringBuilder.toString()
        }

        private fun convertRangeToRegexString(range: IntRange): String {
            if (range == 0 .. 255) {
                return "(\\d{1,2}|1\\d{2}|2[0-4]\\d|25[0-5])"
            }

            val first = range.first
            val last = range.last

            val regexBuilder = StringBuilder("(")

            val diff = last - first

            val firstString = first.toString()
            val lastString = last.toString()

            var minusIndex = 0
            var multiply = 1

            var low = first
            var high = last

            while (diff >= multiply * 10 && minusIndex < firstString.length) {
                low -= appendRegexString(regexBuilder, first, firstString, minusIndex, multiply, true)
                low += multiply * 10
                high -= appendRegexString(regexBuilder, last, lastString, minusIndex, multiply, false)
                high--

                minusIndex++
                multiply *= 10
            }

            if (low < high) {
                val lowString = low.toString()
                val highString = high.toString()

                val currentIndex = lowString.lastIndex - minusIndex

                regexBuilder.append(lowString.substring(0 until currentIndex))

                for (i in currentIndex until lowString.length) {
                    if (lowString[i].digitToInt() > highString[i].digitToInt()) {
                        regexBuilder.append("[${highString[i]}-${lowString[i]}]")
                    } else {
                        regexBuilder.append("[${lowString[i]}-${highString[i]}]")
                    }
                }
            } else {
                regexBuilder.deleteAt(regexBuilder.lastIndex)
            }

            return regexBuilder.append(")").toString()
        }

        private fun appendRegexString(
            regexBuilder: StringBuilder,
            target: Int,
            targetString: String,
            minusIndex: Int,
            multiply: Int,
            isFirst: Boolean
        ): Int {
            val currentIndex = targetString.lastIndex - minusIndex
            val currentChar = targetString[currentIndex]

            regexBuilder.append(targetString.substring(0 until currentIndex))

            if (target < multiply) {
                regexBuilder.append("[0-9]")
            } else {
                if (isFirst && currentChar != '9') {
                    regexBuilder.append("[${currentChar + (if (minusIndex > 0) 1 else 0)}-9]")
                } else if (!isFirst && currentChar != '0') {
                    regexBuilder.append("[0-${currentChar - (if (minusIndex > 0) 1 else 0)}]")
                } else {
                    regexBuilder.append(currentChar)
                }
            }

            regexBuilder
                .append("[0-9]".repeat(minusIndex))
                .append("|")

            return currentChar.digitToInt() * multiply
        }

    }

}
