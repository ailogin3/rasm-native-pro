package com.example.ai

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore-backed encrypted storage for the user-supplied Google AI Studio API key
 * and AI preferences.
 *
 * Keys are encrypted using AES/GCM/NoPadding with a 256-bit key securely held in the
 * AndroidKeyStore hardware/software enclave.
 */
class AiKeyManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "rasm_ai_secure_prefs"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "rasm_ai_key_alias"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128

        private const val PREF_ENCRYPTED_API_KEY = "encrypted_ai_api_key"
        private const val PREF_IV = "api_key_iv"
        private const val PREF_SELECTED_GEMMA_MODEL = "selected_gemma_model"
        private const val PREF_FALLBACK_PREFERENCE = "fallback_preference"
        private const val PREF_AI_ENABLED = "ai_feature_enabled"

        const val FALLBACK_ASK = "ASK_BEFORE_GEMINI"
        const val FALLBACK_NEVER = "NEVER_GEMINI_GEMMA_ONLY"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // In-memory cache of decrypted key to avoid decrypting on every keystroke
    @Volatile
    private var cachedApiKey: String? = null

    init {
        ensureKeyStoreKey()
    }

    private fun ensureKeyStoreKey() {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    KEYSTORE_PROVIDER
                )
                val keyGenSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()
                keyGenerator.init(keyGenSpec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            // AndroidKeyStore fallback initialization
        }
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Validates and saves the user's Google AI Studio API key.
     * Rejects invalid keys (empty, spaces, newlines, tabs, carriage returns).
     */
    fun saveApiKey(rawInput: String): SaveKeyResult {
        val normalized = rawInput.trim()

        if (normalized.isEmpty()) {
            return SaveKeyResult.Error("API key cannot be empty.")
        }
        if (normalized.contains(" ")) {
            return SaveKeyResult.Error("API key cannot contain spaces.")
        }
        if (normalized.contains("\n") || normalized.contains("\r") || normalized.contains("\t")) {
            return SaveKeyResult.Error("API key contains invalid whitespace or newline characters.")
        }

        try {
            val secretKey = getSecretKey()
                ?: return SaveKeyResult.Error("Could not access secure keystore on this device.")

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(normalized.toByteArray(Charsets.UTF_8))

            val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)

            prefs.edit()
                .putString(PREF_ENCRYPTED_API_KEY, encryptedBase64)
                .putString(PREF_IV, ivBase64)
                .putBoolean(PREF_AI_ENABLED, true)
                .apply()

            cachedApiKey = normalized
            return SaveKeyResult.Success
        } catch (e: Exception) {
            return SaveKeyResult.Error("Failed to securely store API key: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    /**
     * Reads and decrypts the saved API key.
     */
    fun getApiKey(): String? {
        if (!cachedApiKey.isNullOrBlank()) {
            return cachedApiKey
        }

        val encryptedBase64 = prefs.getString(PREF_ENCRYPTED_API_KEY, null) ?: return null
        val ivBase64 = prefs.getString(PREF_IV, null) ?: return null

        return try {
            val secretKey = getSecretKey() ?: return null
            val encryptedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            val key = String(decryptedBytes, Charsets.UTF_8).trim()
            cachedApiKey = key
            key
        } catch (e: Exception) {
            null
        }
    }

    fun hasApiKey(): Boolean {
        return !getApiKey().isNullOrBlank()
    }

    /**
     * Returns a safely masked version of the API key for UI display.
     */
    fun getMaskedApiKey(): String {
        val key = getApiKey() ?: return "No key configured"
        if (key.length <= 8) return "••••••••"
        val start = key.take(4)
        val end = key.takeLast(4)
        return "$start••••••••$end"
    }

    /**
     * Removes the API key and clears in-memory caches.
     */
    fun removeApiKey() {
        cachedApiKey = null
        prefs.edit()
            .remove(PREF_ENCRYPTED_API_KEY)
            .remove(PREF_IV)
            .remove(PREF_SELECTED_GEMMA_MODEL)
            .apply()
    }

    // --- Model and Fallback Settings ---

    fun getSelectedGemmaModel(): String? {
        return prefs.getString(PREF_SELECTED_GEMMA_MODEL, null)
    }

    fun setSelectedGemmaModel(modelName: String?) {
        if (modelName == null) {
            prefs.edit().remove(PREF_SELECTED_GEMMA_MODEL).apply()
        } else {
            prefs.edit().putString(PREF_SELECTED_GEMMA_MODEL, modelName).apply()
        }
    }

    fun getFallbackPreference(): String {
        return prefs.getString(PREF_FALLBACK_PREFERENCE, FALLBACK_ASK) ?: FALLBACK_ASK
    }

    fun setFallbackPreference(pref: String) {
        prefs.edit().putString(PREF_FALLBACK_PREFERENCE, pref).apply()
    }

    fun isAiEnabled(): Boolean {
        return prefs.getBoolean(PREF_AI_ENABLED, true)
    }

    fun setAiEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_AI_ENABLED, enabled).apply()
    }
}

sealed class SaveKeyResult {
    object Success : SaveKeyResult()
    data class Error(val message: String) : SaveKeyResult()
}
