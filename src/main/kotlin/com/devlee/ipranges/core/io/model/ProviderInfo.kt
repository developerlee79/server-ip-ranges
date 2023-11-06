package com.devlee.ipranges.core.io.model

import kotlinx.serialization.Serializable

@Serializable
class ProviderInfo(
    val name: String,
    val url: String,
    var refreshToken: String? = null
)
