package com.devlee.ipranges.core.io.model

import kotlinx.serialization.Serializable

@Serializable
data class IPRanges(
    val name: String,
    val ranges: List<String>
)
