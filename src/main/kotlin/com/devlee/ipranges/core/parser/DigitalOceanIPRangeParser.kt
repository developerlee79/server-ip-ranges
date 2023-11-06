package com.devlee.ipranges.core.parser

import com.devlee.ipranges.core.io.ProviderFileUtil
import com.devlee.ipranges.core.io.RequestClient
import com.devlee.ipranges.core.io.model.IPRanges
import com.devlee.ipranges.core.provider.Provider
import kotlinx.coroutines.runBlocking

data object DigitalOceanIPRangeParser : IPRangeParser {

    override val provider: Provider
        get() = Provider.DigitalOcean

    override fun parse(): List<IPRanges> {
        val ipRangeMap = HashMap<String, MutableList<String>>()

        runBlocking {
            val providerInfo = ProviderFileUtil.findProvider(provider)

            val regionList = RequestClient
                .getResponse<String>(providerInfo.url)
                .split("\n")

            regionList.forEach { region ->
                if (region.isBlank()) {
                    return@forEach
                }

                val splitRegionInfo = region.split(",")

                val ipAddress = splitRegionInfo[0]
                val regionName = splitRegionInfo[2]

                ipRangeMap.computeIfAbsent(regionName) { mutableListOf() }.add(ipAddress)
            }
        }

        return ipRangeMap.map { entry -> IPRanges(entry.key, entry.value) }.toList()
    }

}
