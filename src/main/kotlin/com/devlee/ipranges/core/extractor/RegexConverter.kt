package com.devlee.ipranges.core.extractor

import com.devlee.ipranges.core.extractor.version.IPv4RegexExtractor
import com.devlee.ipranges.core.extractor.version.IPv6RegexConverter
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

class RegexConverter {

    companion object {

        fun extract(ip: String): String {
            val splitIPAddress = ip.split('/')

            if (splitIPAddress.size != 2) {
                throw UnknownHostException("Invalid IP Address")
            }

            val cidr = splitIPAddress[1].trim().toIntOrNull()
                ?: throw UnknownHostException("Invalid CIDR notation")

            return extract(splitIPAddress[0].trim(), cidr)
        }

        fun extract(ip: String, cidr: Int?): String {
            if (IPv4RegexExtractor.IPv4_LITERAL_REGEX.matches(ip)) {
                return IPv4RegexExtractor.extract(ip, cidr)
            }

            /*
            * Colon-less input can never be an IPv6 literal; rejecting it here keeps
            * InetAddress.getByName from falling back to a DNS hostname lookup.
            */
            if (':' !in ip) {
                throw UnknownHostException("Invalid IP Address")
            }

            return try {
                val address = InetAddress.getByName(ip)
                if (address is Inet6Address) {
                    IPv6RegexConverter.extract(address.hostAddress, cidr)
                } else {
                    throw UnknownHostException("Invalid IP Address")
                }
            } catch (e: UnknownHostException) {
                throw e
            } catch (e: Exception) {
                throw UnknownHostException("Invalid IP Address")
            }
        }

    }

}
