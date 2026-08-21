package ai.polypay.checkout.model

/** A payment network and the currencies enabled by the merchant. */
internal data class PaymentMethodGroup(
    val network: String,
    val currencies: List<String>,
    val defaultCurrency: String?,
    val feeQuotes: Map<String, NetworkFeeQuote>,
)

/** A display-only network fee quote. */
internal data class NetworkFeeQuote(
    val standardFee: String?,
    val feeCurrency: String?,
    val status: String,
)

/** Public checkout data needed by the native views. */
internal data class CheckoutOrder(
    val tradeId: String,
    val merchantOrderId: String?,
    val currency: String,
    val network: String,
    val amount: String,
    val actualAmount: String,
    val address: String,
    val status: Int,
    val expirationTime: Long,
    val merchantName: String?,
)

/** Status polling response from the public checkout API. */
internal data class CheckoutStatus(
    val status: Int,
    val confirmations: Int,
    val requiredConfirmations: Int,
)

/** Server-authoritative parameters used to request a wallet transaction. */
internal data class WalletPaymentRequest(
    val network: String,
    val chainId: String,
    val assetType: String,
    val tokenContract: String,
    val currency: String,
    val recipient: String,
    val displayAmount: String,
    val amountBaseUnits: String,
)

/** A user-selected currency and network pair. */
internal data class PaymentSelection(val currency: String, val network: String)
