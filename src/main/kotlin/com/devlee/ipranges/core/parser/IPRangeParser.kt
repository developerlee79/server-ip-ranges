package com.devlee.ipranges.core.parser

import com.devlee.ipranges.core.io.model.IPRanges
import com.devlee.ipranges.core.provider.Provider

sealed interface IPRangeParser {

    val provider: Provider

    fun parse(): List<IPRanges>

    companion object {

        fun getProvider(provider: Provider): IPRangeParser {
            return when (provider) {
                Provider.Tencent -> TencentIPRangeParser
                Provider.Amazon -> AmazonIPRangeParser
                Provider.Oracle -> OracleIPRangeParser
                Provider.Microsoft -> MicrosoftIPRangeParser
                Provider.Google -> GoogleIPRangeParser
                Provider.CloudFlare -> CloudFlareIPRangeParser
                Provider.DigitalOcean -> DigitalOceanIPRangeParser
            }
        }

    }

}
