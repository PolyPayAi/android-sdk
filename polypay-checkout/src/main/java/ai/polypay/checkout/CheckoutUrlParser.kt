package ai.polypay.checkout

import java.net.URI

/** Parsed, non-secret identifier carried by a PolyPay checkout URL. */
internal data class ParsedCheckout(val tradeId: String, val host: String)

/** Strict parser for server-created PolyPay payment URLs. */
internal object CheckoutUrlParser {
    private val tradeIdPattern = Regex("^[A-Za-z0-9_-]{8,128}$")

    /** Extracts the trade ID from an allowlisted HTTPS `/pay/{tradeId}` URL. */
    fun parse(rawUrl: String, allowedHosts: Set<String>): ParsedCheckout {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrElse {
            throw IllegalArgumentException("checkoutUrl is invalid", it)
        }
        require(uri.scheme == "https") { "checkoutUrl must use HTTPS" }
        require(uri.userInfo == null) { "checkoutUrl must not contain credentials" }
        require(uri.fragment == null) { "checkoutUrl must not contain a fragment" }
        val host = uri.host?.lowercase() ?: throw IllegalArgumentException("checkoutUrl host is missing")
        require(host in allowedHosts.map(String::lowercase)) { "checkoutUrl host is not allowlisted" }
        val segments = uri.path.split('/').filter(String::isNotBlank)
        val payIndex = segments.indexOfLast { it == "pay" }
        require(payIndex >= 0 && payIndex == segments.lastIndex - 1) {
            "checkoutUrl must point to /pay/{tradeId}"
        }
        val tradeId = segments.last()
        require(tradeIdPattern.matches(tradeId)) { "checkoutUrl trade ID is invalid" }
        return ParsedCheckout(tradeId, host)
    }
}
