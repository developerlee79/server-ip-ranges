package com.devlee.ipranges.core.parser

import com.devlee.ipranges.core.io.model.IPRanges
import com.devlee.ipranges.core.provider.Provider

sealed interface IPRangeParser {

    val provider: Provider

    fun parse(): List<IPRanges>

}
