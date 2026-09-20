package com.example.ai

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ModelsListResponse(
    val models: List<AiModelDto>? = null
)

@JsonClass(generateAdapter = true)
data class AiModelDto(
    val name: String,
    val displayName: String? = null,
    val description: String? = null,
    val supportedGenerationMethods: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<ContentDto>
)

@JsonClass(generateAdapter = true)
data class ContentDto(
    val role: String = "user",
    val parts: List<PartDto>
)

@JsonClass(generateAdapter = true)
data class PartDto(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<CandidateDto>? = null,
    val error: ApiErrorDto? = null
)

@JsonClass(generateAdapter = true)
data class CandidateDto(
    val content: CandidateContentDto? = null,
    val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class CandidateContentDto(
    val parts: List<PartDto>? = null,
    val role: String? = null
)

@JsonClass(generateAdapter = true)
data class ApiErrorWrapper(
    val error: ApiErrorDto? = null
)

@JsonClass(generateAdapter = true)
data class ApiErrorDto(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

data class DiscoveredAiModels(
    val gemmaModels: List<String>,
    val geminiModels: List<String>
)

sealed class AiCallResult {
    data class Success(
        val rawText: String,
        val modelUsed: String,
        val isGeminiFallback: Boolean
    ) : AiCallResult()

    data class NeedsGeminiApproval(
        val message: String,
        val availableGeminiModels: List<String>
    ) : AiCallResult()

    data class Error(
        val message: String,
        val httpCode: Int? = null
    ) : AiCallResult()
}
