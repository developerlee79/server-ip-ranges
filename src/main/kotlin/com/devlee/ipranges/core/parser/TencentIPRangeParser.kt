package com.devlee.ipranges.core.parser

import com.devlee.ipranges.core.io.ProviderFileUtil
import com.devlee.ipranges.core.io.RequestClient
import com.devlee.ipranges.core.io.model.IPRanges
import com.devlee.ipranges.core.provider.Provider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

data object TencentIPRangeParser : IPRangeParser {

    override val provider: Provider
        get() = Provider.Tencent

    /*
    * Based on Tencent Cloud's 'Getting Server IP Addresses' Document
    * https://www.tencentcloud.com/document/product/1047/36742
    * Those API only support obtaining the IP addresses or IP ranges of all Chat service integration methods in the Chinese mainland.
    */
    private val REGION_URL_LIST = arrayOf(
        // China
        "console.tim.qq.com",
        // Singapore
        "adminapisgp.im.qcloud.com",
        // Seoul, South Korea
        "adminapikr.im.qcloud.com",
        // Frankfurt, Germany
        "adminapiger.im.qcloud.com",
        // Mumbai, India
        "adminapiind.im.qcloud.com",
        // Silicon Valley, USA
        "adminapiusa.im.qcloud.com",
    )

    override fun parse(): List<IPRanges> {
        val regexMap = HashMap<String, MutableList<String>>()

        runBlocking {
            val providerInfo = ProviderFileUtil.findProvider(provider)

            for (regionURL in REGION_URL_LIST) {
                val responseJson = RequestClient.getResponse<JsonObject>(
                    String.format(providerInfo.url, regionURL)
                )

                val prefixes = responseJson["IPList"]!!.jsonArray

                for (prefix in prefixes) {
                    val ipAddress = prefix.jsonPrimitive.content
                    regexMap.computeIfAbsent(regionURL) { mutableListOf() }.add(ipAddress)
                }
            }

        }

        return regexMap.map { entry -> IPRanges(entry.key, entry.value) }.toList()
    }

}
