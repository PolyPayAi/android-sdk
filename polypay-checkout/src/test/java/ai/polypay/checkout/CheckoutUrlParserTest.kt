package ai.polypay.checkout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Security-boundary tests for native checkout URL parsing. */
class CheckoutUrlParserTest {
    /** Accepts an exact allowlisted HTTPS host and `/pay` route. */
    @Test
    fun parsesAllowedPaymentUrl() {
        val parsed = CheckoutUrlParser.parse(
            "https://checkout.polypay.ai/en/pay/trade_12345678",
            setOf("checkout.polypay.ai"),
        )
        assertEquals("trade_12345678", parsed.tradeId)
        assertEquals("checkout.polypay.ai", parsed.host)
    }

    /** Rejects host suffix attacks, credentials, fragments, and non-HTTPS URLs. */
    @Test
    fun rejectsUnsafeUrls() {
        val hosts = setOf("checkout.polypay.ai")
        listOf(
            "http://checkout.polypay.ai/pay/trade_12345678",
            "https://checkout.polypay.ai.evil.test/pay/trade_12345678",
            "https://user@checkout.polypay.ai/pay/trade_12345678",
            "https://checkout.polypay.ai/pay/trade_12345678#secret",
            "https://checkout.polypay.ai/checkout/session_12345678",
        ).forEach { url ->
            assertThrows(IllegalArgumentException::class.java) {
                CheckoutUrlParser.parse(url, hosts)
            }
        }
    }
}
