package gobby.utils.session

import gobby.utils.ConfigUtils
import gobby.utils.HttpUtils

sealed class FetchState {
    class Updated(val body: String) : FetchState()
    object Unchanged : FetchState()
    object Failed : FetchState()
}

class SignedResource(private val path: String, name: String, folder: String) {

    private val file = ConfigUtils.jsonFile(name, folder)
    private val signatureFile = ConfigUtils.file("$name.sig", folder)

    @Volatile
    private var etag: String? = null

    fun cachedBody(): String? {
        if (!file.exists() || !signatureFile.exists()) return null
        val body = runCatching { file.readText() }.getOrNull() ?: return null
        val signature = runCatching { signatureFile.readText() }.getOrNull()
        return body.takeIf { PayloadSignature.isTrusted(it, signature) }
    }

    fun fetch(): FetchState {
        val token = SessionAuth.token() ?: return FetchState.Failed
        val response = HttpUtils.getConditional(HttpUtils.backendUrl(path), etag, token) ?: return FetchState.Failed
        if (response.notModified) return FetchState.Unchanged
        val body = response.body ?: return FetchState.Failed
        if (!PayloadSignature.isTrusted(body, response.signature)) return FetchState.Failed
        etag = response.etag
        persist(body, response.signature)
        return FetchState.Updated(body)
    }

    private fun persist(body: String, signature: String?) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(body)
            signatureFile.writeText(signature.orEmpty())
        }
    }
}
