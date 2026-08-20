package ai.polypay.checkout.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests deterministic method defaults shared by the selection page. */
class PaymentSelectionPolicyTest {
    /** Prefers a merchant-configured default over SDK heuristics. */
    @Test
    fun prefersMerchantDefault() {
        val methods = listOf(
            method("Tron", listOf("USDT")),
            method("Ethereum", listOf("USDC", "ETH"), "USDC"),
        )
        assertEquals(PaymentSelection("USDC", "Ethereum"), PaymentSelectionPolicy.preferred(methods))
    }

    /** Falls back to USDT on Tron when no merchant default exists. */
    @Test
    fun prefersUsdtOnTron() {
        val methods = listOf(
            method("Ethereum", listOf("USDT", "USDC")),
            method("Tron", listOf("USDT")),
        )
        assertEquals(PaymentSelection("USDT", "Tron"), PaymentSelectionPolicy.preferred(methods))
    }

    /** Keeps currencies unique and stable across network groups. */
    @Test
    fun ordersCurrencies() {
        val methods = listOf(
            method("Ethereum", listOf("ETH", "USDC")),
            method("Tron", listOf("TRX", "USDT", "USDC")),
        )
        assertEquals(listOf("USDT", "USDC", "ETH", "TRX"), PaymentSelectionPolicy.currencies(methods))
    }

    /** Creates a method fixture without fee metadata. */
    private fun method(network: String, currencies: List<String>, default: String? = null) =
        PaymentMethodGroup(network, currencies, default, emptyMap())
}
