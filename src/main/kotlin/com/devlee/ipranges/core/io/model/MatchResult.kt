package com.devlee.ipranges.core.io.model

import com.devlee.ipranges.core.provider.Provider

data class MatchResult(
    val provider: Provider,
    val region: String,
    val matchedPattern: String
)
