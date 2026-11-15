package com.example.cloud.terabox.auth

import com.example.cloud.core.CloudAccount
import com.example.cloud.core.CloudClaimResult
import com.example.cloud.core.CloudErrorCode
import com.example.cloud.core.CloudException
import com.example.cloud.core.CloudOAuthStart
import com.example.cloud.core.CloudTokens
import com.example.cloud.terabox.api.awaitResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class CloudAuthApi(
    private val backendBaseUrl: String,
    private val http: OkHttpClient = defaultHttpClient()
) {
    companion object {
        const val PLACEHOLDER_BACKEND = "https://YOUR-SERVICE.onrender.com"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val baseUrl = backendBaseUrl.trimEnd('/')

    fun isConfigurationPresent(): Boolean {
        if (baseUrl.isBlank() || baseUrl == PLACEHOLDER_BACKEND.trimEnd('/')) return false
        val parsed = baseUrl.toHttpUrlOrNull() ?: return false
        return parsed.isHttps
    }

    suspend fun start(): CloudOAuthStart = withContext(Dispatchers.IO) {
        requireConfigured()
        val response = execute(
            Request.Builder()
                .url("$baseUrl/v1/auth/start")
                .get()
                .build()
        )
        val body = readJson(response)
        CloudOAuthStart(
            authorizationUrl = body.getString("authorization_url"),
            state = body.getString("state"),
            expiresIn = body.optInt("expires_in", 600)
        )
    }

    suspend fun exchange(code: String, state: String): CloudClaimResult = withContext(Dispatchers.IO) {
        requireConfigured()
        val payload = JSONObject()
            .put("code", code)
            .put("state", state)
        val response = execute(
            Request.Builder()
                .url("$baseUrl/v1/auth/exchange")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
        )
        parseTokenPayload(readJson(response))
    }

    suspend fun claim(claimCode: String): CloudClaimResult = withContext(Dispatchers.IO) {
        requireConfigured()
        val payload = JSONObject().put("claim_code", claimCode)
        val response = execute(
            Request.Builder()
                .url("$baseUrl/v1/auth/claim")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
        )
        parseTokenPayload(readJson(response))
    }

    suspend fun refresh(refreshToken: String): CloudClaimResult = withContext(Dispatchers.IO) {
        requireConfigured()
        val payload = JSONObject().put("refresh_token", refreshToken)
        val response = execute(
            Request.Builder()
                .url("$baseUrl/v1/auth/refresh")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
        )
        parseTokenPayload(readJson(response))
    }

    suspend fun checkConfiguration(): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigurationPresent()) return@withContext false
        try {
            val response = execute(
                Request.Builder().url("$baseUrl/health").get().build()
            )
            response.use {
                if (!it.isSuccessful) return@withContext false
                parseJson(it.body?.string().orEmpty()).optBoolean("configured", false)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun requireConfigured() {
        if (!isConfigurationPresent()) {
            throw CloudException(
                CloudErrorCode.NOT_CONFIGURED,
                "The Cloud backend URL is not configured. Set TERABOX_BACKEND_BASE_URL before building the app."
            )
        }
    }

    private suspend fun execute(request: Request): okhttp3.Response {
        val response = try {
            http.newCall(request).awaitResponse()
        } catch (e: IOException) {
            throw CloudException(CloudErrorCode.NETWORK, "Cannot reach the Cloud backend", e)
        }
        if (!response.isSuccessful) {
            val body = response.body?.string().orEmpty()
            response.close()
            throw mapError(response.code, body)
        }
        return response
    }

    private fun readJson(response: okhttp3.Response): JSONObject = response.use {
        try {
            parseJson(it.body?.string().orEmpty())
        } catch (e: JSONException) {
            throw CloudException(CloudErrorCode.PARSE_ERROR, "Cloud backend returned an invalid response", e)
        }
    }

    private fun parseJson(raw: String): JSONObject {
        if (raw.isBlank()) throw JSONException("empty JSON")
        return JSONObject(raw)
    }

    private fun parseTokenPayload(payload: JSONObject): CloudClaimResult {
        val accountJson = payload.optJSONObject("account")
            ?: throw CloudException(CloudErrorCode.PARSE_ERROR, "Cloud backend did not return an account")
        val account = CloudAccount(
            id = accountJson.optString("id"),
            displayName = accountJson.optString("display_name", "TeraBox account"),
            avatarUrl = accountJson.optString("avatar_url").takeIf { it.isNotBlank() },
            vipType = accountJson.optInt("vip_type", 0),
            userId = accountJson.optString("user_id").takeIf { it.isNotBlank() }
        )
        if (account.id.isBlank()) {
            throw CloudException(CloudErrorCode.PARSE_ERROR, "Cloud backend returned an empty account id")
        }
        val expiresIn = payload.optLong("expires_in", 172800L)
        val tokens = CloudTokens(
            accessToken = payload.getString("access_token"),
            refreshToken = payload.getString("refresh_token"),
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000L + expiresIn,
            apiDomain = payload.getString("api_domain"),
            accountId = account.id
        )
        return CloudClaimResult(account = account, tokens = tokens)
    }

    private fun mapError(status: Int, body: String): CloudException {
        val code = try {
            JSONObject(body).optJSONObject("error")?.optString("code").orEmpty()
        } catch (e: Exception) {
            ""
        }
        val mapped = when {
            status == 401 -> CloudErrorCode.UNAUTHORIZED
            status == 429 -> CloudErrorCode.RATE_LIMITED
            code == "SERVER_NOT_CONFIGURED" -> CloudErrorCode.NOT_CONFIGURED
            code == "CLAIM_NOT_FOUND" -> CloudErrorCode.NOT_FOUND
            code == "REAUTH_REQUIRED" -> CloudErrorCode.UNAUTHORIZED
            code == "RATE_LIMITED" -> CloudErrorCode.RATE_LIMITED
            status >= 500 -> CloudErrorCode.API_ERROR
            else -> CloudErrorCode.API_ERROR
        }
        val message = try {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        } catch (e: Exception) {
            ""
        }.ifBlank { "Cloud authorization request failed" }
        return CloudException(mapped, message)
    }

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
}
