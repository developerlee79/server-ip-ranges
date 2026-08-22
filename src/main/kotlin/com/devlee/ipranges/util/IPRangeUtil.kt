package com.devlee.ipranges.util

import com.devlee.ipranges.core.io.RangeFileUtil
import com.devlee.ipranges.core.io.model.MatchResult
import com.devlee.ipranges.core.net.IpAddress
import com.devlee.ipranges.core.provider.Provider

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
        fun isServerIP(ip: String?): Boolean {
            val target = IpAddress.parse(ip) ?: return false

            return Provider.entries.any { containsInternal(target, it) { true } }
        }

        /** Same contract as [isServerIP], restricted to a single [provider]. */
        fun isServerIP(ip: String?, provider: Provider): Boolean {
            val target = IpAddress.parse(ip) ?: return false

            return containsInternal(target, provider) { true }
        }

        /** Same contract as [isServerIP], restricted to a [provider] and exact [region] name. */
        fun isServerIP(ip: String?, provider: Provider, region: String): Boolean {
            val target = IpAddress.parse(ip) ?: return false

            return containsInternal(target, provider) { it == region }
        }

        /**
         * Returns the matching provider, region, and published CIDR block for [ip], or null
         * when the input is null, blank, not an IP literal, or falls in no known range.
         *
         * Providers are searched in [Provider] declaration order; where a provider publishes
         * overlapping blocks, the most specific one wins.
         */
        fun findMatch(ip: String?): MatchResult? {
            val target = IpAddress.parse(ip) ?: return null

            return Provider.entries.firstNotNullOfOrNull { provider ->
                findMatchInternal(target, provider) { true }
            }
        }

        fun findMatch(ip: String?, provider: Provider): MatchResult? {
            val target = IpAddress.parse(ip) ?: return null
            return findMatchInternal(target, provider) { true }
        }

        fun findMatch(ip: String?, provider: Provider, region: String): MatchResult? {
            val target = IpAddress.parse(ip) ?: return null
            return findMatchInternal(target, provider) { it == region }
        }

        private fun findMatchInternal(
            target: IpAddress,
            provider: Provider,
            regionFilter: (String) -> Boolean
        ): MatchResult? {
            return RangeFileUtil.getIndex(provider).find(target, regionFilter)?.let {
                MatchResult(provider, it.region, it.cidr)
            }
        }

        private fun containsInternal(
            target: IpAddress,
            provider: Provider,
            regionFilter: (String) -> Boolean
        ): Boolean = RangeFileUtil.getIndex(provider).contains(target, regionFilter)

    }

}
