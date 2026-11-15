package com.example.cloud.terabox.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.cloud.core.CloudTokens
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureTokenStore(private val context: Context) {

    companion object {
        private const val PREFERENCES = "cloud_secure_tokens"
        private const val KEY_ALIAS = "proxxquepus_cloud_token_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }

    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun save(tokens: CloudTokens) {
        val payload = JSONObject()
            .put("access_token", tokens.accessToken)
            .put("refresh_token", tokens.refreshToken)
            .put("expires_at", tokens.expiresAtEpochSeconds)
            .put("api_domain", tokens.apiDomain)
            .put("account_id", tokens.accountId)
            .toString()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(tokens.accountId.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
        val stored = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        preferences.edit().putString(keyFor(tokens.accountId), stored).apply()
    }

    fun load(accountId: String): CloudTokens? {
        val stored = preferences.getString(keyFor(accountId), null) ?: return null
        return try {
            val parts = stored.split(":", limit = 2)
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            if (iv.size != IV_BYTES) return null
            val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(TAG_BITS, iv)
            )
            cipher.updateAAD(accountId.toByteArray(Charsets.UTF_8))
            val json = JSONObject(String(cipher.doFinal(encrypted), Charsets.UTF_8))
            CloudTokens(
                accessToken = json.getString("access_token"),
                refreshToken = json.getString("refresh_token"),
                expiresAtEpochSeconds = json.getLong("expires_at"),
                apiDomain = json.getString("api_domain"),
                accountId = json.optString("account_id", accountId)
            )
        } catch (e: Exception) {
            preferences.edit().remove(keyFor(accountId)).apply()
            null
        }
    }

    fun delete(accountId: String) {
        preferences.edit().remove(keyFor(accountId)).apply()
    }

    fun clearAll() {
        preferences.edit().clear().apply()
    }

    private fun keyFor(accountId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(accountId.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
