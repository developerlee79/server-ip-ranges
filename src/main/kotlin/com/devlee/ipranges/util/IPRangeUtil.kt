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

            val regexes = RangeFileUtil.findAllRegex()

            return regexes.any { regexList ->
                run {
                    regexList.regex.any { Regex(it).matches(ip) }
                }
            }
        }

        fun isServerIP(ip: String?, provider: Provider): Boolean {
            if (ip.isNullOrBlank()) {
                throw UnknownHostException("Invalid IP Address")
            }

            val regexes = RangeFileUtil.findRegex(provider)

            return regexes.any { regexList ->
                run {
                    regexList.regex.any { Regex(it).matches(ip) }
                }
            }
        }

        fun isServerIP(ip: String?, provider: Provider, region: String): Boolean {
            if (ip.isNullOrBlank()) {
                throw UnknownHostException("Invalid IP Address")
            }

            val regexes = RangeFileUtil.findRegex(provider)

            return regexes.any { regexList ->
                run {
                    (regexList.name == region) and regexList.regex.any { Regex(it).matches(ip) }
                }
            }
        }

    }

}
