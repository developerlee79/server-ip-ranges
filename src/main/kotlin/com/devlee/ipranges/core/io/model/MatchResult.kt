package com.devlee.ipranges.core.io.model

import com.devlee.ipranges.core.provider.Provider

/** [matchedRange] is the published CIDR block containing the address, e.g. `192.0.2.0/24`. */
data class MatchResult(
    val provider: Provider,
    val region: String,
    val matchedRange: String
)
