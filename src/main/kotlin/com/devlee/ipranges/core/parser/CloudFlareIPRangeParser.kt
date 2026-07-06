package com.devlee.ipranges.core.parser

import com.devlee.ipranges.core.io.ProviderFileUtil
import com.devlee.ipranges.core.io.RequestClient
import com.devlee.ipranges.core.io.model.IPRanges
import com.devlee.ipranges.core.provider.Provider
import kotlinx.coroutines.runBlocking

data object CloudFlareIPRangeParser : IPRangeParser {

    override val provider: Provider
        get() = Provider.CloudFlare

    override fun parse(): List<IPRanges> {
        return runBlocking {
            val providerInfo = ProviderFileUtil.findProvider(provider)

            /*
            * CloudFlare publishes IPv4 and IPv6 ranges on separate endpoints
            * (ips-v4 / ips-v6); the configured url points to the IPv4 list.
            */
            val urls = listOf(
                providerInfo.url,
                providerInfo.url.replace("ips-v4", "ips-v6")
            ).distinct()

            val ranges = urls.flatMap { url ->
                RequestClient
                    .getResponse<String>(url)
                    .split("\n")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            }

            listOf(IPRanges(providerInfo.name, ranges))
        }
    }

}
