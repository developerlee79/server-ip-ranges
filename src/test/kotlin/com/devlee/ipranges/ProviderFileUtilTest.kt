package com.devlee.ipranges

import com.devlee.ipranges.core.io.DataDirectory
import com.devlee.ipranges.core.io.ProviderFileUtil
import com.devlee.ipranges.core.io.model.ProviderInfo
import com.devlee.ipranges.core.provider.Provider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The parsers read their endpoints from this file, so it has to resolve without a data
 * directory too — otherwise they only work inside a repo checkout.
 */
class ProviderFileUtilTest {

    @AfterTest
    fun resetDataSource() {
        System.clearProperty(DataDirectory.PROPERTY)
    }

    private fun dataDirectoryWith(providerInfoJson: String): File {
        val directory = File.createTempFile("ipranges-provider", "").let {
            it.delete()
            it.mkdirs()
            it.deleteOnExit()
            it
        }

        File(directory, "provider-info.json").writeText(providerInfoJson)
        System.setProperty(DataDirectory.PROPERTY, directory.absolutePath)

        return directory
    }

    @Test
    fun `every provider resolves from the bundled file when no data directory is set`() {
        System.clearProperty(DataDirectory.PROPERTY)

        for (provider in Provider.entries) {
            val info = ProviderFileUtil.findProvider(provider)

            assertEquals(provider.name, info.name)
            assertEquals(true, info.url.startsWith("https://"), "${provider.name} url: ${info.url}")
        }
    }

    @Test
    fun `a configured data directory replaces the bundled file`() {
        dataDirectoryWith(
            Json.encodeToString(listOf(ProviderInfo("Amazon", "https://override.invalid/ranges.json")))
        )

        assertEquals(
            "https://override.invalid/ranges.json",
            ProviderFileUtil.findProvider(Provider.Amazon).url
        )
    }

    /* Copies written before refreshToken was dropped must still load. */
    @Test
    fun `a file still carrying refreshToken is accepted`() {
        dataDirectoryWith(
            """[{"name":"Amazon","url":"https://override.invalid/ranges.json","refreshToken":"0000000000"}]"""
        )

        assertEquals("Amazon", ProviderFileUtil.findProvider(Provider.Amazon).name)
    }

}
