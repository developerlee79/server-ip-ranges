package com.devlee.ipranges.core.io

import com.devlee.ipranges.core.io.model.ProviderInfo
import com.devlee.ipranges.core.provider.Provider
import kotlinx.serialization.json.Json
import java.io.File

class ProviderFileUtil {

    companion object {

        private const val PROVIDER_FILE_NAME = "provider-info.json"

        /* Namespaced so it cannot collide with a consumer resource of the same name. */
        private const val PROVIDER_RESOURCE_NAME = "ipranges/provider-info.json"

        /* Tolerates the refreshToken key that older copies of the file still carry. */
        private val jsonFormat = Json { ignoreUnknownKeys = true }

        fun findProvider(provider: Provider): ProviderInfo {
            val providerInfo = jsonFormat.decodeFromString<List<ProviderInfo>>(readProviderFile())

            return providerInfo.find {
                it.name == provider.name
            } ?: throw NoSuchElementException("No such provider: ${provider.name}")
        }

        /*
        * Same precedence as the range data: the configured data directory when there is one,
        * otherwise the copy bundled in the jar, so the parsers work as a dependency too.
        */
        private fun readProviderFile(): String {
            val providerFile = DataDirectory.resolve()?.let { File(it, PROVIDER_FILE_NAME) }

            if (providerFile != null && providerFile.exists()) {
                return providerFile.readText()
            }

            return ProviderFileUtil::class.java.classLoader
                .getResourceAsStream(PROVIDER_RESOURCE_NAME)
                ?.use { it.readBytes().decodeToString() }
                ?: throw NoSuchFileException(File(PROVIDER_RESOURCE_NAME))
        }

    }

}
