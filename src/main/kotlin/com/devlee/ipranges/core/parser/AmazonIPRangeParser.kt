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

            /*
            * Amazon publishes IPv4 entries under 'prefixes' (key 'ip_prefix') and
            * IPv6 entries under a separate 'ipv6_prefixes' array (key 'ipv6_prefix').
            */
            val prefixArrays = listOfNotNull(
                responseJson["prefixes"]?.jsonArray,
                responseJson["ipv6_prefixes"]?.jsonArray
            )

            for (prefixes in prefixArrays) {
                for (prefix in prefixes) {
                    val currentIPObject = prefix.jsonObject

                    val region = currentIPObject["region"]!!.jsonPrimitive.content
                    val ipAddress = currentIPObject["ip_prefix"]?.jsonPrimitive?.content
                        ?: currentIPObject["ipv6_prefix"]?.jsonPrimitive?.content
                        ?: continue

                    ipRangeMap.computeIfAbsent(region) { mutableListOf() }.add(ipAddress)
                }
            }
        }

        return ipRangeMap.map { entry -> IPRanges(entry.key, entry.value) }.toList()
    }

}
