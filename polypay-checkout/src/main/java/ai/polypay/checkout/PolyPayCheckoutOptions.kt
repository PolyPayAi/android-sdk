package ai.polypay.checkout

import java.net.URI

/** Presentation mode for the checkout session. */
enum class PolyPayCheckoutMode {
    /** Native Activity rendering. Recommended for physical goods, offline services, or direct APK distribution. */
    NATIVE,
    /** Hosted web checkout via Chrome Custom Tabs or external browser. Recommended for Google Play compliance with digital goods. */
    CUSTOM_TABS
}

/** Configuration for a PolyPay checkout session. */
data class PolyPayCheckoutOptions(
    val checkoutUrl: String,
    val allowedCheckoutHosts: Set<String> = setOf("checkout.polypay.ai"),
    val apiBaseUrl: String = "https://api.polypay.ai/api/v1/pay",
    val pollIntervalMillis: Long = 5_000,
    val mode: PolyPayCheckoutMode = PolyPayCheckoutMode.NATIVE,
) {
    /** Validates all network boundaries before the checkout activity starts. */
    fun validate() {
        require(pollIntervalMillis >= 2_000) { "pollIntervalMillis must be at least 2000" }
        require(allowedCheckoutHosts.isNotEmpty()) { "allowedCheckoutHosts must not be empty" }
        require(allowedCheckoutHosts.all(::isValidHost)) { "allowedCheckoutHosts contains an invalid host" }
        require(URI(apiBaseUrl).let { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null }) {
            "apiBaseUrl must be an HTTPS URL without credentials"
        }
        CheckoutUrlParser.parse(checkoutUrl, allowedCheckoutHosts)
    }

    /** Returns true when a configured hostname is an exact, normalized DNS name. */
    private fun isValidHost(host: String): Boolean =
        host.isNotBlank() && host == host.lowercase() && !host.contains('/') && !host.contains(':')
}
