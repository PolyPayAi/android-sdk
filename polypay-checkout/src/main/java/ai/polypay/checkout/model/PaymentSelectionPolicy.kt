package ai.polypay.checkout.model

/** Shared deterministic policy for the initial native payment-method selection. */
internal object PaymentSelectionPolicy {
    /** Selects a merchant default, then USDT on Tron, then the first available pair. */
    fun preferred(methods: List<PaymentMethodGroup>): PaymentSelection? {
        methods.firstOrNull { method ->
            method.defaultCurrency != null && method.defaultCurrency in method.currencies
        }?.let { return PaymentSelection(it.defaultCurrency!!, it.network) }

        methods.firstOrNull { it.network.equals("Tron", ignoreCase = true) && "USDT" in it.currencies }
            ?.let { return PaymentSelection("USDT", it.network) }

        methods.firstOrNull { "USDT" in it.currencies }
            ?.let { return PaymentSelection("USDT", it.network) }

        val first = methods.firstOrNull { it.currencies.isNotEmpty() } ?: return null
        return PaymentSelection(first.currencies.first(), first.network)
    }

    /** Returns every currency in stable display order without duplicates. */
    fun currencies(methods: List<PaymentMethodGroup>): List<String> {
        val preferredOrder = listOf("USDT", "USDC", "BUSD", "DAI", "ETH", "BNB", "TRX", "TON")
        return methods.flatMap { it.currencies }.distinct().sortedWith(
            compareBy<String> { preferredOrder.indexOf(it).let { index -> if (index < 0) Int.MAX_VALUE else index } }
                .thenBy { it },
        )
    }
}
