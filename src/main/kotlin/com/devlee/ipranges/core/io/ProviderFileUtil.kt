package com.devlee.ipranges.core.io

import com.devlee.ipranges.core.io.model.ProviderInfo
import com.devlee.ipranges.core.provider.Provider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ProviderFileUtil {

    companion object {

        private const val PROVIDER_FILE_PATH = "./provider-info.json"

        private val jsonFormat = Json { prettyPrint = true }

        fun findProvider(provider: Provider): ProviderInfo =
            runBlocking {
                val providerFileString = File(PROVIDER_FILE_PATH).readText()
                val providerInfo = jsonFormat.decodeFromString<List<ProviderInfo>>(providerFileString)

                providerInfo.find {
                    it.name == provider.name
                } ?: throw NoSuchElementException("No such provider")
            }

        fun updateRefreshToken(provider: ProviderInfo, token: String) {
            val providerFile = File(PROVIDER_FILE_PATH)
            val providerInfo = jsonFormat.decodeFromString<List<ProviderInfo>>(providerFile.readText())

            providerInfo.find { it.name == provider.name }?.refreshToken = token

            providerFile.writeText(jsonFormat.encodeToString(providerInfo))
        }

        fun hasToRefresh(provider: ProviderInfo, token: String): Boolean {
            val providerInfo = jsonFormat.decodeFromString<List<ProviderInfo>>(File(PROVIDER_FILE_PATH).readText())
            return providerInfo.any { it.name == provider.name && it.refreshToken != token }
        }

    }

}
