package com.devlee.ipranges.core.parser

import com.devlee.ipranges.core.io.ProviderFileUtil
import com.devlee.ipranges.core.io.RequestClient
import com.devlee.ipranges.core.io.model.IPRanges
import com.devlee.ipranges.core.io.model.ProviderInfo
import com.devlee.ipranges.core.provider.Provider
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.charset.Charset

data object MicrosoftIPRangeParser: IPRangeParser {

    override val provider: Provider
        get() = Provider.Microsoft

    override fun parse(): List<IPRanges> {
        val ipRangeMap = HashMap<String, MutableList<String>>()

        runBlocking {
            val providerInfo = ProviderFileUtil.findProvider(provider)

            val rangeFileURL = findRangeFileURL(providerInfo)

            val rangeFile = File("./range/microsoft/" + rangeFileURL.split("/").last())
            rangeFile.deleteOnExit()

            val rangeFileChannel = RequestClient.getResponse<ByteReadChannel>(rangeFileURL)
            rangeFileChannel.copyAndClose(rangeFile.writeChannel())

            val rangeJson = Json.parseToJsonElement(rangeFile.readText()).jsonObject

            val syncToken = rangeJson["changeNumber"]?.jsonPrimitive?.content

            if (syncToken != null && providerInfo.refreshToken != syncToken) {
                ProviderFileUtil.updateRefreshToken(providerInfo, syncToken)
            }

            val platformArray = rangeJson["values"]?.jsonArray

            if (platformArray.isNullOrEmpty()) {
                throw RuntimeException("Test")
            }

            for (platform in platformArray) {
                val platformObject = platform.jsonObject

                val serviceName = platformObject["name"]!!.jsonPrimitive.content

                /*
                * Microsoft's IP range document includes the IP of management and other services.
                * In this project, we only parse the IP addresses of Azure Cloud Computing instances.
                */
                if (isNotServerInstance(serviceName)) {
                    continue
                }

                val prefixes = platformObject["properties"]!!.jsonObject["addressPrefixes"]!!.jsonArray

                for (prefix in prefixes) {
                    val ipAddress = prefix.jsonPrimitive.content
                    ipRangeMap.computeIfAbsent(serviceName) { mutableListOf() }.add(ipAddress)
                }
            }
        }

        return ipRangeMap.map { entry -> IPRanges(entry.key, entry.value) }.toList()
    }

    private suspend fun findRangeFileURL(provider: ProviderInfo): String {
        val rangeDocumentResponse = RequestClient.getClient()
            .get(provider.url)
            .bodyAsText(Charset.defaultCharset())

        val rangeFileURLRegex = "https://download.microsoft.com/download/(.*?)/ServiceTags_(.*?).json".toRegex()

        val rangeFileURL = rangeFileURLRegex.find(rangeDocumentResponse)
            ?: throw NoSuchElementException("Cannot find range document file url")

        return rangeFileURL.value
    }

    private fun isNotServerInstance(serviceName: String) = !serviceName.startsWith("AzureCloud")

}
