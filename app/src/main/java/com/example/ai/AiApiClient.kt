package com.example.ai

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class AiApiClient(
    private val keyManager: AiKeyManager
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val modelsAdapter = moshi.adapter(ModelsListResponse::class.java)
    private val requestAdapter = moshi.adapter(GenerateContentRequest::class.java)
    private val responseAdapter = moshi.adapter(GenerateContentResponse::class.java)
    private val errorAdapter = moshi.adapter(ApiErrorWrapper::class.java)

    // Cached model lists for session
    @Volatile
    private var cachedDiscoveredModels: DiscoveredAiModels? = null

    /**
     * Clears cached models so the next call performs a fresh lookup.
     */
    fun clearCachedModels() {
        cachedDiscoveredModels = null
    }

    /**
     * Fetches models from the official Google AI Studio Gemini API models endpoint.
     */
    suspend fun discoverModels(forceRefresh: Boolean = false): Result<DiscoveredAiModels> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedDiscoveredModels != null) {
            return@withContext Result.success(cachedDiscoveredModels!!)
        }

        val rawKey = keyManager.getApiKey()?.trim()
        if (rawKey.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("No Google AI Studio API key configured."))
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$rawKey"
        val request = Request.Builder().url(url).get().build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errMsg = parseErrorBody(response.code, bodyStr)
                    return@withContext Result.failure(Exception(errMsg))
                }

                val listResp = modelsAdapter.fromJson(bodyStr)
                val allModels = listResp?.models.orEmpty()

                val gemmaCandidates = mutableListOf<String>()
                val geminiCandidates = mutableListOf<String>()

                for (m in allModels) {
                    val cleanName = m.name.removePrefix("models/")
                    val methods = m.supportedGenerationMethods ?: emptyList()
                    val supportsGenerate = methods.contains("generateContent")

                    if (!supportsGenerate) continue

                    // Exclude non-text/specialized types like embedding, vision-only, or audio-only
                    val isEmbedding = cleanName.contains("embedding", ignoreCase = true)
                    val isAudio = cleanName.contains("audio", ignoreCase = true)
                    val isVideo = cleanName.contains("veo", ignoreCase = true) || cleanName.contains("video", ignoreCase = true)

                    if (isEmbedding || isAudio || isVideo) continue

                    if (cleanName.startsWith("gemma-", ignoreCase = true)) {
                        gemmaCandidates.add(cleanName)
                    } else if (cleanName.startsWith("gemini-", ignoreCase = true)) {
                        geminiCandidates.add(cleanName)
                    }
                }

                val discovered = DiscoveredAiModels(
                    gemmaModels = gemmaCandidates.distinct(),
                    geminiModels = geminiCandidates.distinct()
                )
                cachedDiscoveredModels = discovered
                Result.success(discovered)
            }
        } catch (e: UnknownHostException) {
            Result.failure(Exception("No internet connection. Please check your network and try again."))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("Model discovery timed out. Please retry."))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to discover AI models: ${e.localizedMessage ?: "Unknown error"}"))
        }
    }

    /**
     * Executes generateContent following the strict Gemma-First workflow:
     * 1. Check/discover available models.
     * 2. Try user-selected Gemma model or candidate Gemma models.
     * 3. Retries on 500/503 (wait ~2s, retry once, wait ~4s, retry once, then try next Gemma model).
     * 4. On 404: drop that model candidate and try next Gemma model.
     * 5. If all Gemma models fail:
     *    - If [allowGeminiFallback] is true: attempt approved Gemini fallback model.
     *    - Else: return NeedsGeminiApproval (if fallback preference is ASK) or Error (if NEVER).
     */
    suspend fun executeGenerateContent(
        prompt: String,
        allowGeminiFallback: Boolean = false
    ): AiCallResult = withContext(Dispatchers.IO) {
        val apiKey = keyManager.getApiKey()?.trim()
        if (apiKey.isNullOrBlank()) {
            return@withContext AiCallResult.Error("Google AI Studio API key is missing. Please set it up in AI Settings.")
        }

        // 1. Discover models if not yet cached
        var models = cachedDiscoveredModels
        if (models == null) {
            val discoveryResult = discoverModels(forceRefresh = false)
            if (discoveryResult.isFailure) {
                val err = discoveryResult.exceptionOrNull()?.message ?: "Failed to reach AI models endpoint."
                return@withContext AiCallResult.Error(err)
            }
            models = discoveryResult.getOrNull()
        }

        val gemmaList = models?.gemmaModels?.toMutableList() ?: mutableListOf()
        val geminiList = models?.geminiModels?.toMutableList() ?: mutableListOf()

        // Build priority list: gemini-flash first (follows instructions, no chain-of-thought),
        // then Gemma models, then other Gemini models.
        val flashModels = geminiList.filter { it.contains("flash", ignoreCase = true) }
        val otherGemini = geminiList.filter { !it.contains("flash", ignoreCase = true) }
        val priorityList = (flashModels + gemmaList + otherGemini).distinct().toMutableList()

        // User's preferred Gemma model takes top spot if set
        val preferredGemma = keyManager.getSelectedGemmaModel()
        if (!preferredGemma.isNullOrBlank() && priorityList.contains(preferredGemma)) {
            priorityList.remove(preferredGemma)
            priorityList.add(0, preferredGemma)
        }

        // 2. Try models in priority order (flash → Gemma → other Gemini)
        var lastGemmaError: String? = null
        val unusableGemma = mutableListOf<String>()

        for (gemmaModel in priorityList.toList()) {
            if (unusableGemma.contains(gemmaModel)) continue

            val result = tryModelWithRetries(model = gemmaModel, apiKey = apiKey, prompt = prompt)
            when (result) {
                is ModelCallOutcome.Success -> {
                    return@withContext AiCallResult.Success(
                        rawText = result.text,
                        modelUsed = gemmaModel,
                        isGeminiFallback = gemmaModel.startsWith("gemini-", ignoreCase = true)
                    )
                }
                is ModelCallOutcome.NotFound404 -> {
                    unusableGemma.add(gemmaModel)
                    lastGemmaError = "Model $gemmaModel returned 404 Not Found."
                    // Continue to next Gemma model
                }
                is ModelCallOutcome.FatalClientError -> {
                    // 400, 401, 403, 429 - do NOT switch models
                    return@withContext AiCallResult.Error(
                        message = result.message,
                        httpCode = result.code
                    )
                }
                is ModelCallOutcome.ServerError5xx -> {
                    lastGemmaError = result.message
                    // Continue to next Gemma model
                }
                is ModelCallOutcome.NetworkError -> {
                    return@withContext AiCallResult.Error(result.message)
                }
            }
        }

        // If we reach here, all Gemma models failed or none were available
        val fallbackPref = keyManager.getFallbackPreference()

        if (allowGeminiFallback && geminiList.isNotEmpty()) {
            // User has explicitly approved Gemini fallback for this request
            // Pick the best available Gemini model from returned list
            val fallbackModel = pickBestGeminiModel(geminiList)
            if (fallbackModel != null) {
                val geminiResult = tryModelWithRetries(model = fallbackModel, apiKey = apiKey, prompt = prompt)
                when (geminiResult) {
                    is ModelCallOutcome.Success -> {
                        return@withContext AiCallResult.Success(
                            rawText = geminiResult.text,
                            modelUsed = fallbackModel,
                            isGeminiFallback = true
                        )
                    }
                    is ModelCallOutcome.FatalClientError -> {
                        return@withContext AiCallResult.Error(geminiResult.message, geminiResult.code)
                    }
                    is ModelCallOutcome.ServerError5xx,
                    is ModelCallOutcome.NotFound404 -> {
                        return@withContext AiCallResult.Error("Gemini fallback ($fallbackModel) failed: ${geminiResult}")
                    }
                    is ModelCallOutcome.NetworkError -> {
                        return@withContext AiCallResult.Error(geminiResult.message)
                    }
                }
            }
        }

        if (fallbackPref == AiKeyManager.FALLBACK_NEVER) {
            return@withContext AiCallResult.Error(
                "No hosted Gemma model could complete the request right now. You can retry Gemma or continue using the original app."
            )
        }

        // Return NeedsGeminiApproval so UI can prompt user explicitly
        val message = "None of the available hosted Gemma models could complete your request. You can retry Gemma, continue without AI, or temporarily use an available Gemini model with the same Google AI Studio API key.\n\nGemini may be free within your current Google AI Studio limits. Charges may apply if billing is enabled or if a paid-only model is selected."
        AiCallResult.NeedsGeminiApproval(
            message = message,
            availableGeminiModels = geminiList
        )
    }

    private fun pickBestGeminiModel(models: List<String>): String? {
        // Preferred priority for Gemini models returned by endpoint:
        val priority = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-3.5-flash",
            "gemini-3.1-pro-preview",
            "gemini-flash-latest"
        )
        for (pref in priority) {
            val match = models.firstOrNull { it.equals(pref, ignoreCase = true) }
            if (match != null) return match
        }
        return models.firstOrNull()
    }

    private suspend fun tryModelWithRetries(
        model: String,
        apiKey: String,
        prompt: String
    ): ModelCallOutcome {
        var attempts = 0
        while (attempts < 3) {
            attempts++
            val outcome = callGenerateContentSingle(model, apiKey, prompt)
            when (outcome) {
                is ModelCallOutcome.ServerError5xx -> {
                    if (attempts == 1) {
                        delay(2000)
                    } else if (attempts == 2) {
                        delay(4000)
                    } else {
                        return outcome
                    }
                }
                else -> return outcome
            }
        }
        return ModelCallOutcome.ServerError5xx(500, "Server error on $model after retries.")
    }

    private fun callGenerateContentSingle(
        model: String,
        apiKey: String,
        prompt: String
    ): ModelCallOutcome {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val requestBodyObj = GenerateContentRequest(
            contents = listOf(
                ContentDto(
                    role = "user",
                    parts = listOf(PartDto(text = prompt))
                )
            )
        )
        val jsonString = requestAdapter.toJson(requestBodyObj)
        val body = jsonString.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        return try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                val code = response.code

                if (code == 404) {
                    return ModelCallOutcome.NotFound404
                }

                if (code in 500..599) {
                    return ModelCallOutcome.ServerError5xx(code, "Selected model $model is temporarily unavailable (HTTP $code).")
                }

                if (!response.isSuccessful) {
                    val parsedMsg = parseErrorBody(code, bodyStr)
                    return ModelCallOutcome.FatalClientError(code, parsedMsg)
                }

                val genResponse = responseAdapter.fromJson(bodyStr)
                val candidates = genResponse?.candidates
                if (candidates.isNullOrEmpty()) {
                    return ModelCallOutcome.FatalClientError(200, "Model returned an empty response.")
                }

                val parts = candidates[0].content?.parts.orEmpty()
                val joinedText = parts.joinToString("\n") { it.text }
                if (joinedText.isBlank()) {
                    return ModelCallOutcome.FatalClientError(200, "Model generated no text content.")
                }

                ModelCallOutcome.Success(joinedText)
            }
        } catch (e: UnknownHostException) {
            ModelCallOutcome.NetworkError("No internet connection. Please check your network and retry.")
        } catch (e: SocketTimeoutException) {
            ModelCallOutcome.NetworkError("The AI request timed out. Please retry.")
        } catch (e: IOException) {
            ModelCallOutcome.NetworkError("Network error communicating with AI service: ${e.localizedMessage ?: "Unknown error"}")
        } catch (e: Exception) {
            ModelCallOutcome.FatalClientError(0, "Unexpected error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private fun parseErrorBody(code: Int, bodyStr: String): String {
        val parsedError = try {
            errorAdapter.fromJson(bodyStr)?.error
        } catch (e: Exception) {
            null
        }

        val apiMsg = parsedError?.message?.trim()

        return when (code) {
            400 -> apiMsg ?: "The request could not be processed. Please try a simpler request."
            401, 403 -> apiMsg ?: "The API key may be invalid, restricted or unauthorized. Replace it in Settings."
            404 -> "The selected model is unavailable."
            429 -> apiMsg ?: "The API quota or rate limit has been reached. Please try again later."
            500, 503 -> "The selected model is temporarily unavailable."
            else -> apiMsg ?: "AI service error (HTTP $code)."
        }
    }
}

sealed class ModelCallOutcome {
    data class Success(val text: String) : ModelCallOutcome()
    object NotFound404 : ModelCallOutcome()
    data class ServerError5xx(val code: Int, val message: String) : ModelCallOutcome()
    data class FatalClientError(val code: Int, val message: String) : ModelCallOutcome()
    data class NetworkError(val message: String) : ModelCallOutcome()
}
