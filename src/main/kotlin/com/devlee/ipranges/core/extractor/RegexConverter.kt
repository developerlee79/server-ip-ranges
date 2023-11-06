package com.devlee.ipranges.core.extractor

import com.devlee.ipranges.core.extractor.version.IPv4RegexExtractor
import java.net.UnknownHostException

class RegexConverter {

    companion object {

        private val IPv4_REGEX = Regex("(\\d{1,2}|1\\d{2}|2[0-4]\\d|25[0-5]).".repeat(4).dropLast(1))

        fun extract(ip: String): String {
            val splitIPAddress = ip.split('/')

            if (splitIPAddress.size != 2) {
                throw UnknownHostException("Invalid IP Address")
            }

            return extract(splitIPAddress[0], splitIPAddress[1].toInt())
        }

        fun extract(ip: String, cidr: Int?): String {
            if (IPv4_REGEX.matches(ip)) {
                return IPv4RegexExtractor.extract(ip, cidr)
            }

            throw UnknownHostException("Invalid IP Address")
        }

    }

}
