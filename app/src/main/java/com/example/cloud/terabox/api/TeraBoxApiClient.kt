package com.example.cloud.terabox.api

import com.example.cloud.core.CloudErrorCode
import com.example.cloud.core.CloudException
import com.example.cloud.core.CloudFile
import com.example.cloud.core.CloudFolderPage
import com.example.cloud.core.CloudMediaType
import com.example.cloud.terabox.api.awaitResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class TeraBoxApiClient(
    private val http: OkHttpClient = defaultHttpClient()
) {
    companion object {
        const val DEFAULT_API_DOMAIN = "https://www.terabox.com"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(40, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    suspend fun listFolder(
        accountId: String,
        accessToken: String,
        apiDomain: String,
        path: String,
        page: Int,
        pageSize: Int,
        order: String = "name",
        descending: Boolean = false
    ): CloudFolderPage = withContext(Dispatchers.IO) {
        val url = apiUrl(apiDomain, "openapi/api/list")
            .addQueryParameter("access_tokens", accessToken)
            .addQueryParameter("dir", path)
            .addQueryParameter("num", pageSize.toString())
            .addQueryParameter("page", page.toString())
            .addQueryParameter("order", order)
            .addQueryParameter("desc", if (descending) "1" else "0")
            .addQueryParameter("web", "1")
            .build()
        val json = getJson(url)
        val array = json.optJSONArray("list") ?: json.optJSONArray("info")
            ?: throw CloudException(CloudErrorCode.PARSE_ERROR, "TeraBox returned no file list")
        val items = parseFiles(array, accountId)
        CloudFolderPage(
            items = items,
            page = page,
            hasMore = items.size >= pageSize
        )
    }

    suspend fun search(
        accountId: String,
        accessToken: String,
        apiDomain: String,
        query: String,
        page: Int,
        pageSize: Int
    ): CloudFolderPage = withContext(Dispatchers.IO) {
        val url = apiUrl(apiDomain, "openapi/api/search")
            .addQueryParameter("access_tokens", accessToken)
            .addQueryParameter("key", query)
            .addQueryParameter("order", "time")
            .addQueryParameter("desc", "1")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("num", pageSize.toString())
            .addQueryParameter("recursion", "1")
            .build()
        val json = getJson(url)
        val array = json.optJSONArray("list") ?: json.optJSONArray("info")
            ?: throw CloudException(CloudErrorCode.PARSE_ERROR, "TeraBox returned no search results")
        val items = parseFiles(array, accountId)
        val hasMore = json.optInt("has_more", if (items.size >= pageSize) 1 else 0) == 1
        CloudFolderPage(items = items, page = page, hasMore = hasMore)
    }

    suspend fun resolveDownloadUrl(
        accessToken: String,
        apiDomain: String,
        fsId: String
    ): String = withContext(Dispatchers.IO) {
        val url = apiUrl(apiDomain, "openapi/api/download")
            .addQueryParameter("access_tokens", accessToken)
            .addQueryParameter("fidlist", "[$fsId]")
            .addQueryParameter("type", "dlink")
            .build()
        val json = getJson(url)
        val dlinkArray = json.optJSONArray("dlink")
            ?: throw CloudException(CloudErrorCode.PERMISSION_DENIED, "TeraBox did not return a download link")
        if (dlinkArray.length() == 0) {
            throw CloudException(CloudErrorCode.PERMISSION_DENIED, "TeraBox did not return a download link")
        }
        val raw = dlinkArray.optJSONObject(0)?.optString("dlink").orEmpty()
        if (raw.isBlank()) {
            throw CloudException(CloudErrorCode.PERMISSION_DENIED, "TeraBox did not return a download link")
        }
        appendAccessToken(raw, accessToken)
    }

    fun streamingUrl(
        accessToken: String,
        apiDomain: String,
        path: String,
        quality: String = "M3U8_AUTO_720"
    ): String {
        return apiUrl(apiDomain, "openapi/api/streaming")
            .addQueryParameter("access_tokens", accessToken)
            .addQueryParameter("path", path)
            .addQueryParameter("type", quality)
            .build()
            .toString()
    }

    suspend fun loadBytes(url: String, maxBytes: Long): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        val response = try {
            http.newCall(request).awaitResponse()
        } catch (e: IOException) {
            throw CloudException(CloudErrorCode.NETWORK, "Cannot download this cloud file", e)
        }
        response.use {
            if (!it.isSuccessful) {
                throw mapHttpError(it.code, it.body?.string().orEmpty())
            }
            val length = it.body?.contentLength() ?: -1L
            if (length > maxBytes) {
                throw CloudException(CloudErrorCode.UNSUPPORTED, "Cloud file is too large to open in memory")
            }
            val body = it.body ?: throw CloudException(CloudErrorCode.API_ERROR, "Cloud file is empty")
            val source = body.source()
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = source.read(buffer)
                if (read == -1) break
                total += read
                if (total > maxBytes) {
                    throw CloudException(CloudErrorCode.UNSUPPORTED, "Cloud file is too large to open in memory")
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    suspend fun downloadTo(
        url: String,
        sink: java.io.OutputStream
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        val response = try {
            http.newCall(request).awaitResponse()
        } catch (e: IOException) {
            throw CloudException(CloudErrorCode.NETWORK, "Cannot download this cloud file", e)
        }
        response.use {
            if (!it.isSuccessful) {
                throw mapHttpError(it.code, it.body?.string().orEmpty())
            }
            val body = it.body ?: throw CloudException(CloudErrorCode.API_ERROR, "Cloud file is empty")
            body.byteStream().use { input ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    sink.write(buffer, 0, read)
                }
                sink.flush()
            }
        }
    }

    suspend fun rename(
        accessToken: String,
        apiDomain: String,
        file: CloudFile,
        newName: String
    ): Boolean = withContext(Dispatchers.IO) {
        val fileItem = JSONObject()
            .put("path", file.path)
            .put("newname", newName)
        val body = JSONObject().put("filelist", JSONArray().put(fileItem))
        fileManager(accessToken, apiDomain, "rename", body)
    }

    suspend fun delete(
        accessToken: String,
        apiDomain: String,
        files: List<CloudFile>
    ): Boolean = withContext(Dispatchers.IO) {
        val list = JSONArray()
        files.forEach { list.put(it.path) }
        val body = JSONObject().put("filelist", list)
        fileManager(accessToken, apiDomain, "delete", body)
    }

    private suspend fun fileManager(
        accessToken: String,
        apiDomain: String,
        opera: String,
        body: JSONObject
    ): Boolean {
        val url = apiUrl(apiDomain, "openapi/api/filemanager")
            .addQueryParameter("access_tokens", accessToken)
            .addQueryParameter("opera", opera)
            .addQueryParameter("async", "0")
            .build()
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val response = try {
            http.newCall(request).awaitResponse()
        } catch (e: IOException) {
            throw CloudException(CloudErrorCode.NETWORK, "Cannot update the cloud file", e)
        }
        val json = response.use {
            if (!it.isSuccessful) throw mapHttpError(it.code, it.body?.string().orEmpty())
            parseJson(it.body?.string().orEmpty())
        }
        checkErrno(json)
        val info = json.optJSONArray("info")
        if (info != null) {
            for (index in 0 until info.length()) {
                val item = info.optJSONObject(index) ?: continue
                val itemErrno = item.optInt("errno", 0)
                if (itemErrno != 0) {
                    throw CloudException(CloudErrorCode.API_ERROR, "TeraBox rejected the cloud file operation")
                }
            }
        }
        return true
    }

    private suspend fun getJson(url: HttpUrl): JSONObject {
        val request = Request.Builder().url(url).get().build()
        val response = try {
            http.newCall(request).awaitResponse()
        } catch (e: IOException) {
            throw CloudException(CloudErrorCode.NETWORK, "TeraBox is unreachable", e)
        }
        return response.use {
            if (!it.isSuccessful) throw mapHttpError(it.code, it.body?.string().orEmpty())
            val json = try {
                parseJson(it.body?.string().orEmpty())
            } catch (e: JSONException) {
                throw CloudException(CloudErrorCode.PARSE_ERROR, "TeraBox returned invalid JSON", e)
            }
            checkErrno(json)
            json
        }
    }

    private fun parseJson(raw: String): JSONObject {
        if (raw.isBlank()) throw JSONException("empty JSON")
        return JSONObject(raw)
    }

    private fun checkErrno(json: JSONObject) {
        val errno = json.optInt("errno", 0)
        if (errno == 0) return
        val code = when (errno) {
            200002, 200003, 200004, 200005, -6 -> CloudErrorCode.UNAUTHORIZED
            300001 -> CloudErrorCode.RATE_LIMITED
            -9, 105 -> CloudErrorCode.PERMISSION_DENIED
            -7, -8, 2 -> CloudErrorCode.API_ERROR
            else -> CloudErrorCode.API_ERROR
        }
        val message = json.optString("show_msg")
            .ifBlank { json.optString("errmsg") }
            .ifBlank { "TeraBox rejected the request (errno $errno)" }
        throw CloudException(code, message)
    }

    private fun parseFiles(array: JSONArray, accountId: String): List<CloudFile> {
        val files = ArrayList<CloudFile>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val path = item.optString("path").ifBlank { continue }
            val name = item.optString("server_filename")
                .ifBlank { item.optString("filename") }
                .ifBlank { path.substringAfterLast('/') }
            val isDirectory = item.optInt("isdir", 0) == 1
            val category = item.optInt("category", 0)
            val modifiedSeconds = item.optLong("server_mtime", 0L)
                .takeIf { it > 0L } ?: item.optLong("local_mtime", 0L)
            val thumbs = item.optJSONObject("thumbs")
            val size = item.optLong("size", 0L)
            val parentPath = if (path == "/" || path.isBlank()) {
                "/"
            } else {
                path.substringBeforeLast('/', "").ifBlank { "/" }
            }
            files.add(
                CloudFile(
                    accountId = accountId,
                    id = item.optString("fs_id").ifBlank { path },
                    name = name,
                    path = path,
                    parentPath = parentPath,
                    isDirectory = isDirectory,
                    size = size,
                    modifiedAt = modifiedSeconds * 1000L,
                    category = category,
                    mediaType = if (isDirectory) CloudMediaType.OTHER else mediaTypeFor(name, category),
                    durationMs = item.optLong("duration", 0L) * 1000L,
                    width = item.optInt("width", 0),
                    height = item.optInt("height", 0),
                    thumbnailUrl = thumbs?.optString("url2")?.takeIf { it.isNotBlank() },
                    thumbnailUrlSmall = thumbs?.optString("url1")?.takeIf { it.isNotBlank() },
                    thumbnailUrlLarge = thumbs?.optString("url3")?.takeIf { it.isNotBlank() },
                    md5 = item.optString("md5").takeIf { it.isNotBlank() },
                    cachedAt = System.currentTimeMillis()
                )
            )
        }
        return files
    }

    private fun mediaTypeFor(name: String, category: Int): CloudMediaType {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".gif") -> CloudMediaType.GIF
            category == 1 -> CloudMediaType.VIDEO
            category == 2 -> CloudMediaType.AUDIO
            category == 3 -> CloudMediaType.PHOTO
            lower.matches(Regex(".*\\.(mp4|mkv|webm|mov|avi|m3u8|flv|wmv|ts)$")) -> CloudMediaType.VIDEO
            lower.matches(Regex(".*\\.(gif)$")) -> CloudMediaType.GIF
            lower.matches(Regex(".*\\.(jpg|jpeg|png|webp|bmp|heic|heif)$")) -> CloudMediaType.PHOTO
            lower.matches(Regex(".*\\.(mp3|m4a|aac|wav|ogg|flac|opus)$")) -> CloudMediaType.AUDIO
            else -> CloudMediaType.OTHER
        }
    }

    private fun appendAccessToken(rawUrl: String, accessToken: String): String {
        val parsed = rawUrl.toHttpUrlOrNull() ?: return rawUrl
        if (parsed.queryParameter("access_tokens") != null) return parsed.toString()
        return parsed.newBuilder().addQueryParameter("access_tokens", accessToken).build().toString()
    }

    private fun apiUrl(apiDomain: String, path: String): HttpUrl.Builder {
        val base = when {
            apiDomain.isBlank() -> DEFAULT_API_DOMAIN
            apiDomain.startsWith("http://") || apiDomain.startsWith("https://") -> apiDomain.trimEnd('/')
            else -> "https://${apiDomain.trimEnd('/')}"
        }
        val baseUrl = base.toHttpUrlOrNull()
            ?: throw CloudException(CloudErrorCode.PARSE_ERROR, "TeraBox returned an invalid API domain")
        return baseUrl.newBuilder().addPathSegments(path)
    }

    private fun mapHttpError(status: Int, body: String): CloudException {
        val mapped = when {
            status == 401 || status == 403 -> CloudErrorCode.UNAUTHORIZED
            status == 429 -> CloudErrorCode.RATE_LIMITED
            status == 404 -> CloudErrorCode.NOT_FOUND
            else -> CloudErrorCode.API_ERROR
        }
        val message = try {
            val json = JSONObject(body)
            json.optString("show_msg").ifBlank { json.optString("errmsg") }
        } catch (e: Exception) {
            ""
        }.ifBlank { "TeraBox request failed (HTTP $status)" }
        return CloudException(mapped, message)
    }
}
