package com.devlee.ipranges.core.parser

import com.devlee.ipranges.core.io.ProviderFileUtil
import com.devlee.ipranges.core.io.RequestClient
import com.devlee.ipranges.core.io.model.IPRanges
import com.devlee.ipranges.core.provider.Provider
import kotlinx.coroutines.runBlocking

data object CloudFlareIPRangeParser : IPRangeParser {

    override val provider: Provider
        get() = Provider.CloudFlare

    override fun parse(): List<IPRanges> {
        return runBlocking {
            val providerInfo = ProviderFileUtil.findProvider(provider)

            listOf(
                IPRanges(
                    providerInfo.name,
                    RequestClient
                        .getResponse<String>(providerInfo.url)
                        .split("\n")
                )
            )
        }
    }

}
