package com.devlee.ipranges.core.io

import com.devlee.ipranges.core.io.model.ProviderInfo
import com.devlee.ipranges.core.provider.Provider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ProviderFileUtil {

    companion object {

        private const val PROVIDER_FILE_PATH = "./provider-info.json"

        private val jsonFormat = Json { prettyPrint = true }

        fun findProvider(provider: Provider): ProviderInfo {
            val providerFileString = File(PROVIDER_FILE_PATH).readText()
            val providerInfo = jsonFormat.decodeFromString<List<ProviderInfo>>(providerFileString)

            return providerInfo.find {
                it.name == provider.name
            } ?: throw NoSuchElementException("No such provider")
        }

        fun updateRefreshToken(provider: ProviderInfo, token: String) {
            val providerFile = File(PROVIDER_FILE_PATH)
            val providerInfo = jsonFormat.decodeFromString<List<ProviderInfo>>(providerFile.readText())

            providerInfo.find { it.name == provider.name }?.refreshToken = token

            providerFile.writeText(jsonFormat.encodeToString(providerInfo))
        }

    }

}
