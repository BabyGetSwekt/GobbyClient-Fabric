package gobby.utils.session

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object PayloadSignature {

    private const val ALGORITHM = "Ed25519"
    private const val PUBLIC_KEY = "MCowBQYDK2VwAyEAY56VkzDZABDUzLfZw092uZFkkW+AYR50z246kqbm40g="

    private val publicKey: PublicKey? by lazy {
        runCatching {
            KeyFactory.getInstance(ALGORITHM)
                .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY)))
        }.getOrNull()
    }

    fun isTrusted(payload: String, signature: String?): Boolean {
        if (signature.isNullOrEmpty()) return false
        val key = publicKey ?: return false
        return runCatching {
            Signature.getInstance(ALGORITHM).apply {
                initVerify(key)
                update(payload.toByteArray(Charsets.UTF_8))
            }.verify(Base64.getDecoder().decode(signature))
        }.getOrDefault(false)
    }
}
