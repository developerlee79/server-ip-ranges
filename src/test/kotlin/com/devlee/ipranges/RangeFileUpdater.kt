package com.devlee.ipranges

import com.devlee.ipranges.core.io.RangeFileUtil
import com.devlee.ipranges.core.parser.IPRangeParser
import com.devlee.ipranges.core.provider.Provider
import kotlin.test.Test

class RangeFileUpdater {

    @Test
    fun updateIPRanges() {
        for (provider in Provider.entries) {
            val parser = IPRangeParser.getParser(provider)
            RangeFileUtil.updateRangeFile(parser.provider) { parser.parse() }
        }
    }

}
