package com.devlee.ipranges.core.io.model

import kotlinx.serialization.Serializable

@Serializable
data class IPRegex(
    val name: String,
    val regex: List<String>
)
