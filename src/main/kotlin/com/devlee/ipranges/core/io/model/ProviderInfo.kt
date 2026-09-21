package com.devlee.ipranges.core.io.model

import kotlinx.serialization.Serializable

@Serializable
data class ProviderInfo(
    val name: String,
    val url: String
)
