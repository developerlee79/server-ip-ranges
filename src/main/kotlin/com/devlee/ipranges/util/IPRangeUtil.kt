package com.devlee.ipranges.util

import com.devlee.ipranges.core.io.RangeFileUtil
import com.devlee.ipranges.core.io.model.MatchResult
import com.devlee.ipranges.core.provider.Provider
import java.net.InetAddress
import java.net.UnknownHostException

class IPRangeUtil {

    companion object {

        fun isServerIP(ip: String?): Boolean {
            if (ip.isNullOrBlank()) {
                throw UnknownHostException("Invalid IP Address")
            }

            val regexes = RangeFileUtil.getAllRegex()

            return regexes.any { regexList ->
                run {
                    regexList.regex.any { it.matches(ip) }
                }
            }
        }

        fun isServerIP(ip: String?, provider: Provider): Boolean =
            findMatch(ip, provider) != null

        fun isServerIP(ip: String?, provider: Provider, region: String): Boolean =
            findMatch(ip, provider) { it == region } != null

        private fun findMatch(
            ip: String?,
            provider: Provider,
            regionFilter: (String) -> Boolean = { true }
        ): MatchResult? {
            if (ip.isNullOrBlank()) {
                return null
            }

            val target = InetAddress.getByName(ip).hostAddress

            return RangeFileUtil.getRegex(provider) { regionFilter(it.name) }
                .firstNotNullOfOrNull { regexGroup ->
                    regexGroup.regex.firstOrNull { it.matches(target) }?.let {
                        MatchResult(provider, regexGroup.name, it.pattern)
                    }
                }
        }

    }

}
