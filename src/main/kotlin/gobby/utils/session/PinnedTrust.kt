package gobby.utils.session

import java.net.Socket
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager

object PinnedTrust {

    private const val BACKEND_PUBLIC_KEY_PIN = "hjn3JNJYL/89OklnmnX1NSHVdgbe53GWOIz6CILDKes="
    private const val TLS_PROTOCOL = "TLSv1.3"
    private const val DIGEST = "SHA-256"

    val context: SSLContext by lazy {
        SSLContext.getInstance(TLS_PROTOCOL).apply { init(null, arrayOf(PinnedTrustManager), null) }
    }

    private fun pinOf(certificate: X509Certificate): String =
        Base64.getEncoder().encodeToString(MessageDigest.getInstance(DIGEST).digest(certificate.publicKey.encoded))

    private object PinnedTrustManager : X509ExtendedTrustManager() {

        override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull() ?: throw CertificateException("No certificate presented")
            leaf.checkValidity()
            if (pinOf(leaf) != BACKEND_PUBLIC_KEY_PIN) throw CertificateException("Certificate is not the pinned backend key")
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?, socket: Socket?) =
            checkServerTrusted(chain, authType)

        override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?, engine: SSLEngine?) =
            checkServerTrusted(chain, authType)

        override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) =
            throw CertificateException("Client authentication is not supported")

        override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?, socket: Socket?) =
            checkClientTrusted(chain, authType)

        override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?, engine: SSLEngine?) =
            checkClientTrusted(chain, authType)

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
