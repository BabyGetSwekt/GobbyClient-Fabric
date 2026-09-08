package gobby.utils.session

import com.google.gson.JsonObject
import gobby.Gobbyclient.Companion.mc
import gobby.utils.HttpUtils
import gobby.utils.HttpUtils.isSuccess
import gobby.utils.parseJsonObject
import gobby.utils.stringOrNull
import gobby.utils.timer.Cooldown

object SessionAuth {

    private const val CHALLENGE_PATH = "/v1/auth/challenge"
    private const val VERIFY_PATH = "/v1/auth/verify"

    private val validity = Cooldown()

    @Volatile
    private var token: String? = null

    val isAuthenticated: Boolean get() = token != null && validity.isActive

    fun token(): String? {
        if (isAuthenticated) return token
        return authenticate()
    }

    private fun authenticate(): String? {
        val user = mc.user ?: return null
        val serverId = requestChallenge() ?: return null
        if (!joinMojangSession(user.profileId, user.accessToken, serverId)) return null
        return exchangeForToken(user.name, serverId)
    }

    private fun requestChallenge(): String? =
        HttpUtils.getString(HttpUtils.backendUrl(CHALLENGE_PATH))
            ?.let { parseJsonObject(it)?.stringOrNull("serverId") }

    private fun joinMojangSession(profileId: java.util.UUID, accessToken: String, serverId: String): Boolean {
        val body = JsonObject().apply {
            addProperty("accessToken", accessToken)
            addProperty("selectedProfile", profileId.toString().replace("-", ""))
            addProperty("serverId", serverId)
        }
        return HttpUtils.postJson("https://sessionserver.mojang.com/session/minecraft/join", body.toString())?.isSuccess() == true
    }

    private fun exchangeForToken(username: String, serverId: String): String? {
        val body = JsonObject().apply {
            addProperty("username", username)
            addProperty("serverId", serverId)
        }
        val result = HttpUtils.postJson(HttpUtils.backendUrl(VERIFY_PATH), body.toString()) ?: return null
        if (!result.isSuccess()) return null
        val json = parseJsonObject(result.body) ?: return null
        val issued = json.stringOrNull("token") ?: return null
        val ttl = json.get("ttlSeconds")?.takeUnless { it.isJsonNull }?.asInt ?: return null
        token = issued
        validity.start((ttl - 60).coerceAtLeast(1))
        return issued
    }
}
