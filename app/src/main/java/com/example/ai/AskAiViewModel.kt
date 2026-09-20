package com.example.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AskAiState {
    object KeySetupRequired : AskAiState()
    object Idle : AskAiState()
    data class Processing(val message: String) : AskAiState()
    data class Answer(
        val question: String,
        val text: String,
        val model: String,
        val geminiFallback: Boolean
    ) : AskAiState()
    data class NeedsApproval(val message: String) : AskAiState()
    data class Error(val message: String) : AskAiState()
}

/**
 * Each member enters their OWN Google AI Studio API key on their own device.
 * The key is encrypted with the Android Keystore and never leaves the device
 * except in requests to Google's Gemini API.
 */
class AskAiViewModel(application: Application) : AndroidViewModel(application) {

    val keyManager = AiKeyManager(application)
    private val apiClient = AiApiClient(keyManager)

    private val _state = MutableStateFlow<AskAiState>(
        if (keyManager.hasApiKey()) AskAiState.Idle else AskAiState.KeySetupRequired
    )
    val state: StateFlow<AskAiState> = _state.asStateFlow()

    private var lastQuestion: String = ""
    private var lastPrompt: String = ""

    fun saveKey(input: String): SaveKeyResult {
        val result = keyManager.saveApiKey(input)
        if (result is SaveKeyResult.Success) {
            apiClient.clearCachedModels()
            _state.value = AskAiState.Idle
        }
        return result
    }

    fun removeKey() {
        keyManager.removeApiKey()
        apiClient.clearCachedModels()
        _state.value = AskAiState.KeySetupRequired
    }

    fun reset() {
        _state.value = if (keyManager.hasApiKey()) AskAiState.Idle else AskAiState.KeySetupRequired
    }

    fun ask(question: String, data: String) {
        val q = question.trim()
        if (q.isBlank()) return
        if (!keyManager.hasApiKey()) {
            _state.value = AskAiState.KeySetupRequired
            return
        }
        lastQuestion = q
        lastPrompt = AiContextBuilder.wrapPrompt(q, data)
        run(allowGemini = false)
    }

    fun approveGeminiFallback() {
        if (lastPrompt.isBlank()) return
        run(allowGemini = true)
    }

    private fun run(allowGemini: Boolean) {
        val prompt = lastPrompt
        val question = lastQuestion
        viewModelScope.launch {
            _state.value = AskAiState.Processing(
                if (allowGemini) "Asking Gemini..." else "Asking the AI..."
            )
            when (val r = apiClient.executeGenerateContent(prompt, allowGemini)) {
                is AiCallResult.Success -> _state.value = AskAiState.Answer(
                    question = question,
                    text = r.rawText,
                    model = r.modelUsed,
                    geminiFallback = r.isGeminiFallback
                )
                is AiCallResult.NeedsGeminiApproval -> _state.value = AskAiState.NeedsApproval(r.message)
                is AiCallResult.Error -> _state.value = AskAiState.Error(r.message)
            }
        }
    }
}
