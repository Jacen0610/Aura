package si.jacen.aura.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class AskDto(
    val answer: String = "",
    val generatedAt: String? = null,
)
