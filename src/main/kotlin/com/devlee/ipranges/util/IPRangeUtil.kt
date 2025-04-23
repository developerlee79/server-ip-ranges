package com.devlee.ipranges.util

import com.devlee.ipranges.core.io.RangeFileUtil
import com.devlee.ipranges.core.provider.Provider
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

        fun isServerIP(ip: String?, provider: Provider): Boolean {
            if (ip.isNullOrBlank()) {
                throw UnknownHostException("Invalid IP Address")
            }

            val regexes = RangeFileUtil.getRegex(provider)

            return regexes.any { regexList ->
                run {
                    regexList.regex.any { it.matches(ip) }
                }
            }
        }

        fun isServerIP(ip: String?, provider: Provider, region: String): Boolean {
            if (ip.isNullOrBlank()) {
                throw UnknownHostException("Invalid IP Address")
            }

            val regexes = RangeFileUtil.getRegex(provider)

            return regexes.any { regexList ->
                run {
                    (regexList.name == region) and regexList.regex.any { it.matches(ip) }
                }
            }
        }

    }

}
