package com.devlee.ipranges.util

import com.devlee.ipranges.core.extractor.version.IPv4RegexExtractor
import com.devlee.ipranges.core.io.RangeFileUtil
import com.devlee.ipranges.core.io.model.MatchResult
import com.devlee.ipranges.core.provider.Provider
import java.net.InetAddress

class IPRangeUtil {

    companion object {

        /**
         * Returns true when [ip] belongs to a published cloud provider range.
         *
         * Contract: null, blank, and non-IP-literal input (including hostnames) returns
         * false — it never throws for bad input and never performs a DNS lookup.
         * A false result therefore means "not recognized as a cloud IP", which covers
         * both a genuine non-match and unparseable input; use [findMatch] plus your own
         * input validation when those cases must be distinguished.
         */
        fun isServerIP(ip: String?): Boolean =
            findMatch(ip) != null

        /** Same contract as [isServerIP], restricted to a single [provider]. */
        fun isServerIP(ip: String?, provider: Provider): Boolean =
            findMatch(ip, provider) != null

        /** Same contract as [isServerIP], restricted to a [provider] and exact [region] name. */
        fun isServerIP(ip: String?, provider: Provider, region: String): Boolean =
            findMatch(ip, provider, region) != null

        /**
         * Returns the first matching provider/region/pattern for [ip], or null when
         * the input is null, blank, not an IP literal, or matches no known range.
         */
        fun findMatch(ip: String?): MatchResult? {
            val target = normalize(ip) ?: return null

            return Provider.entries.firstNotNullOfOrNull { provider ->
                findMatchInternal(target, provider) { true }
            }
        }

        fun findMatch(ip: String?, provider: Provider): MatchResult? {
            val target = normalize(ip) ?: return null
            return findMatchInternal(target, provider) { true }
        }

        fun findMatch(ip: String?, provider: Provider, region: String): MatchResult? {
            val target = normalize(ip) ?: return null
            return findMatchInternal(target, provider) { it == region }
        }

        /*
        * Only IP literals are accepted. Hostname input is rejected up front so that
        * InetAddress.getByName never falls back to DNS resolution.
        */
        private fun normalize(ip: String?): String? {
            if (ip.isNullOrBlank()) {
                return null
            }

            val trimmed = ip.trim()

            val isLiteral = trimmed.matches(IPv4RegexExtractor.IPv4_LITERAL_REGEX) || ':' in trimmed
            if (!isLiteral) {
                return null
            }

            return runCatching { InetAddress.getByName(trimmed).hostAddress }.getOrNull()
        }

        private fun findMatchInternal(
            target: String,
            provider: Provider,
            regionFilter: (String) -> Boolean
        ): MatchResult? {
            return RangeFileUtil.getRegex(provider) { regionFilter(it.name) }
                .firstNotNullOfOrNull { regexGroup ->
                    regexGroup.regex.firstOrNull { it.matches(target) }?.let {
                        MatchResult(provider, regexGroup.name, it.pattern)
                    }
                }
        }

    }

}
