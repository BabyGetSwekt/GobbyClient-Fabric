package gobby.utils

import gobby.utils.session.PinnedTrust
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.zip.GZIPInputStream

class HttpText(val body: String?, val etag: String?, val signature: String?, val notModified: Boolean)

class HttpResult(val status: Int, val body: String)

object HttpUtils {

    private const val BACKEND_BASE_URL = "https://194.164.96.84:20099"
    private const val STATUS_OK = 200
    private const val STATUS_NOT_MODIFIED = 304
    private const val STATUS_NO_CONTENT = 204

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val backendClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .sslContext(PinnedTrust.context)
        .build()

    private fun clientFor(url: String): HttpClient =
        if (url.startsWith(BACKEND_BASE_URL)) backendClient else client

    fun backendUrl(path: String): String = "$BACKEND_BASE_URL$path"

    fun getString(url: String): String? {
        return try {
            val request = baseRequest(url).GET().build()
            val response = clientFor(url).send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == STATUS_OK) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    fun getConditional(url: String, etag: String?, token: String? = null): HttpText? = try {
        val request = baseRequest(url)
            .header("Accept-Encoding", "gzip")
            .apply {
                if (!etag.isNullOrEmpty()) header("If-None-Match", etag)
                if (!token.isNullOrEmpty()) header("Authorization", "Bearer $token")
            }
            .GET()
            .build()
        val response = clientFor(url).send(request, HttpResponse.BodyHandlers.ofByteArray())
        when (response.statusCode()) {
            STATUS_NOT_MODIFIED -> HttpText(null, etag, response.headerOrNull("x-signature"), true)
            STATUS_OK -> HttpText(decodeBody(response), response.headerOrNull("etag"), response.headerOrNull("x-signature"), false)
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    fun postJson(url: String, json: String): HttpResult? = try {
        val request = baseRequest(url)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()
        val response = clientFor(url).send(request, HttpResponse.BodyHandlers.ofString())
        HttpResult(response.statusCode(), response.body().orEmpty())
    } catch (e: Exception) {
        null
    }

    fun HttpResult.isSuccess(): Boolean = status == STATUS_OK || status == STATUS_NO_CONTENT

    private fun baseRequest(url: String): HttpRequest.Builder = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Accept", "application/json")
        .header("User-Agent", "GobbyClient")
        .timeout(Duration.ofSeconds(20))

    private fun decodeBody(response: HttpResponse<ByteArray>): String =
        if (response.headerOrNull("content-encoding")?.contains("gzip") == true)
            GZIPInputStream(response.body().inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }
        else response.body().toString(Charsets.UTF_8)

    private fun HttpResponse<*>.headerOrNull(name: String): String? = headers().firstValue(name).orElse(null)
}
