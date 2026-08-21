package ai.polypay.checkout.internal

import ai.polypay.checkout.model.CheckoutOrder
import ai.polypay.checkout.model.WalletPaymentRequest

/** Builds standards-based wallet requests from server-authoritative payment parameters. */
internal object WalletPaymentUri {
    private val evmAddress = Regex("^0x[0-9a-fA-F]{40}$")
    private val baseUnits = Regex("^[0-9]+$")

    /** Returns whether this order can use the server's current direct-wallet preparation API. */
    fun isSupported(order: CheckoutOrder): Boolean =
        order.currency in setOf("USDT", "USDC") && order.network in setOf(
            "Ethereum",
            "Polygon",
            "BSC",
            "Base",
            "Arbitrum",
            "Optimism",
        )

    /** Creates an ERC-681 URI after validating every irreversible transaction parameter. */
    fun build(request: WalletPaymentRequest, order: CheckoutOrder): String {
        require(request.network == order.network) { "Wallet network does not match checkout" }
        require(request.currency == order.currency) { "Wallet currency does not match checkout" }
        require(request.recipient == order.address) { "Wallet recipient does not match checkout" }
        require(request.displayAmount == order.actualAmount) { "Wallet amount does not match checkout" }
        require(request.chainId.startsWith("eip155:")) { "Unsupported wallet chain" }
        val chainId = request.chainId.removePrefix("eip155:")
        require(chainId.matches(Regex("^[1-9][0-9]*$"))) { "Invalid EVM chain ID" }
        require(request.recipient.matches(evmAddress)) { "Invalid EVM recipient" }
        require(request.amountBaseUnits.matches(baseUnits) && request.amountBaseUnits.any { it != '0' }) {
            "Invalid wallet payment amount"
        }
        return when (request.assetType) {
            "native" -> "ethereum:${request.recipient}@$chainId?value=${request.amountBaseUnits}"
            "token" -> {
                require(request.tokenContract.matches(evmAddress)) { "Invalid EVM token contract" }
                "ethereum:${request.tokenContract}@$chainId/transfer" +
                    "?address=${request.recipient}&uint256=${request.amountBaseUnits}"
            }
            else -> throw IllegalArgumentException("Unsupported wallet asset type")
        }
    }
}
