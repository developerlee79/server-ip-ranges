package com.devlee.ipranges.core.io.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class IPRegex(
    val name: String,
    val regex: List<@Serializable(with = RegexPatternSerializer::class) Regex>
)

object RegexPatternSerializer : KSerializer<Regex> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Regex", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Regex) {
        encoder.encodeString(value.pattern)
    }

    override fun deserialize(decoder: Decoder): Regex =
        Regex(decoder.decodeString())

}
