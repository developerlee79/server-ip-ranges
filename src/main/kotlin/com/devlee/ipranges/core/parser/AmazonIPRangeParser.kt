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

data object AmazonIPRangeParser : IPRangeParser {

    override val provider: Provider
        get() = Provider.Amazon

    override fun parse(): List<IPRanges> {
        val ipRangeMap = HashMap<String, MutableList<String>>()

        runBlocking {
            val providerInfo = ProviderFileUtil.findProvider(provider)

            val responseJson = RequestClient.getResponse<JsonObject>(providerInfo.url)

            val syncToken = responseJson["syncToken"]?.jsonPrimitive?.content

            if (syncToken != null && providerInfo.refreshToken != syncToken) {
                ProviderFileUtil.updateRefreshToken(providerInfo, syncToken)
            }

            val prefixes = responseJson["prefixes"]!!.jsonArray

            for (prefix in prefixes) {
                val currentIPObject = prefix.jsonObject

                val region = currentIPObject["region"]!!.jsonPrimitive.content
                val ipAddress = currentIPObject["ip_prefix"]!!.jsonPrimitive.content

                ipRangeMap.computeIfAbsent(region) { mutableListOf() }.add(ipAddress)
            }
        }

        return ipRangeMap.map { entry -> IPRanges(entry.key, entry.value) }.toList()
    }

}
