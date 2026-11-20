package fr.penpenic.babyphone.feature.pairing

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64

/**
 * Contenu du QR code d'appairage affiché par un appareil déjà de confiance.
 *
 * Format : `babyphone://pair?v=1&family=…&device=…&key=…&server=wss://…&token=…&exp=…`
 * - `key` : clé publique X.509 de l'appareil émetteur, en base64url sans padding ;
 * - `token` : secret à usage unique (au moins 128 bits) que le serveur invalidera après usage ;
 * - `exp` : expiration en secondes Unix (2 minutes après l'affichage).
 */
data class PairingPayload(
    val familyId: String,
    val deviceId: String,
    val publicKey: ByteArray,
    val serverUrl: String,
    val token: String,
    val expiresAtEpochSec: Long,
) {
    fun toUri(): String = buildString {
        append("$SCHEME://$HOST?v=$VERSION")
        append("&family=").append(enc(familyId))
        append("&device=").append(enc(deviceId))
        append("&key=").append(B64.encodeToString(publicKey))
        append("&server=").append(enc(serverUrl))
        append("&token=").append(enc(token))
        append("&exp=").append(expiresAtEpochSec)
    }

    override fun equals(other: Any?): Boolean = other is PairingPayload &&
        familyId == other.familyId && deviceId == other.deviceId &&
        publicKey.contentEquals(other.publicKey) && serverUrl == other.serverUrl &&
        token == other.token && expiresAtEpochSec == other.expiresAtEpochSec

    override fun hashCode(): Int = token.hashCode()

    // Ne jamais journaliser le jeton.
    override fun toString(): String = "PairingPayload(family=$familyId, device=$deviceId, server=$serverUrl)"

    sealed interface ParseResult {
        data class Valid(val payload: PairingPayload) : ParseResult
        data class Invalid(val reason: String) : ParseResult
    }

    companion object {
        const val SCHEME = "babyphone"
        const val HOST = "pair"
        const val VERSION = 1
        const val VALIDITY_SECONDS = 120L
        private const val MIN_TOKEN_LENGTH = 22 // 128 bits en base64url
        private val ID_PATTERN = Regex("^[A-Za-z0-9_-]{8,64}$")
        private val B64 = Base64.getUrlEncoder().withoutPadding()
        private val B64_DECODER = Base64.getUrlDecoder()

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

        fun parse(text: String, nowEpochSec: Long): ParseResult {
            val uri = runCatching { URI(text) }.getOrNull()
                ?: return ParseResult.Invalid("QR code illisible")
            if (uri.scheme != SCHEME || uri.host != HOST) return ParseResult.Invalid("Ce n'est pas un QR code Babyphone")

            val params = uri.rawQuery.orEmpty().split("&")
                .mapNotNull { part ->
                    val i = part.indexOf('=')
                    if (i <= 0) null else part.substring(0, i) to URLDecoder.decode(part.substring(i + 1), "UTF-8")
                }.toMap()

            if (params["v"] != VERSION.toString()) return ParseResult.Invalid("Version de l'application incompatible")
            val family = params["family"]?.takeIf { ID_PATTERN.matches(it) }
                ?: return ParseResult.Invalid("Identifiant de famille invalide")
            val device = params["device"]?.takeIf { ID_PATTERN.matches(it) }
                ?: return ParseResult.Invalid("Identifiant d'appareil invalide")
            val key = params["key"]?.let { runCatching { B64_DECODER.decode(it) }.getOrNull() }
                ?.takeIf { it.isNotEmpty() }
                ?: return ParseResult.Invalid("Clé publique invalide")
            val server = params["server"]?.takeIf { it.startsWith("wss://") }
                ?: return ParseResult.Invalid("Le serveur doit utiliser une connexion chiffrée (wss://)")
            val token = params["token"]?.takeIf { it.length >= MIN_TOKEN_LENGTH }
                ?: return ParseResult.Invalid("Jeton d'appairage invalide")
            val exp = params["exp"]?.toLongOrNull()
                ?: return ParseResult.Invalid("Date d'expiration absente")
            if (exp < nowEpochSec) return ParseResult.Invalid("QR code expiré, affichez-en un nouveau")
            if (exp > nowEpochSec + VALIDITY_SECONDS + 30) return ParseResult.Invalid("Durée de validité anormale")

            return ParseResult.Valid(PairingPayload(family, device, key, server, token, exp))
        }
    }
}
