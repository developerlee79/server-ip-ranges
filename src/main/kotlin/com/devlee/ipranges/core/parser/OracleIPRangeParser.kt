package com.devlee.ipranges.core.parser

import com.devlee.ipranges.core.io.ProviderFileUtil
import com.devlee.ipranges.core.io.RequestClient
import com.devlee.ipranges.core.io.model.IPRanges
import com.devlee.ipranges.core.provider.Provider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data object OracleIPRangeParser : IPRangeParser {

    override val provider: Provider
        get() = Provider.Oracle

    override fun parse(): List<IPRanges> {
        val ipRangeMap = HashMap<String, MutableList<String>>()

        runBlocking {
            val providerInfo = ProviderFileUtil.findProvider(provider)

            val responseJson = RequestClient.getResponse<JsonObject>(providerInfo.url)

            val lastUpdateTime = responseJson.jsonObject["last_updated_timestamp"]?.jsonPrimitive?.content

            if (lastUpdateTime != null && providerInfo.refreshToken != lastUpdateTime) {
                ProviderFileUtil.updateRefreshToken(providerInfo, lastUpdateTime)
            }

            val regions = responseJson.jsonObject["regions"]!!.jsonArray

            for (region in regions) {
                val regionObject = region.jsonObject

                val regionName = regionObject["region"]!!.jsonPrimitive.content
                val ipAddressArray = regionObject["cidrs"]!!.jsonArray

                for (ipAddressObject in ipAddressArray) {
                    val ipAddress = ipAddressObject.jsonObject["cidr"]!!.jsonPrimitive.content
                    ipRangeMap.computeIfAbsent(regionName) { mutableListOf() }.add(ipAddress)
                }
            }
        }

        return ipRangeMap.map { entry -> IPRanges(entry.key, entry.value) }.toList()
    }

}
