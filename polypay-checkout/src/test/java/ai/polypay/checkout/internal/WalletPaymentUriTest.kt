package ai.polypay.checkout.internal

import ai.polypay.checkout.model.CheckoutOrder
import ai.polypay.checkout.model.WalletPaymentRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Protocol tests for wallet payment URI construction. */
class WalletPaymentUriTest {
    private val request = WalletPaymentRequest(
        network = "Base",
        chainId = "eip155:8453",
        assetType = "token",
        tokenContract = "0x1111111111111111111111111111111111111111",
        currency = "USDC",
        recipient = "0x2222222222222222222222222222222222222222",
        displayAmount = "10.01",
        amountBaseUnits = "10010000",
    )
    private val order = CheckoutOrder(
        tradeId = "trade_12345678",
        merchantOrderId = null,
        currency = "USDC",
        network = "Base",
        amount = "10",
        actualAmount = "10.01",
        address = request.recipient,
        status = 1,
        expirationTime = 2_000_000_000,
        merchantName = null,
    )

    /** Preserves the exact contract, chain, recipient, and atomic amount in ERC-681 format. */
    @Test
    fun buildsErc20TransferUri() {
        assertEquals(
            "ethereum:0x1111111111111111111111111111111111111111@8453/transfer" +
                "?address=0x2222222222222222222222222222222222222222&uint256=10010000",
            WalletPaymentUri.build(request, order),
        )
    }

    /** Rejects malformed irreversible payment parameters instead of opening a wallet. */
    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedRecipient() {
        WalletPaymentUri.build(request.copy(recipient = "0x1234"), order.copy(address = "0x1234"))
    }

    /** Rejects a valid-looking recipient when it differs from the displayed checkout order. */
    @Test(expected = IllegalArgumentException::class)
    fun rejectsRecipientMismatch() {
        WalletPaymentUri.build(
            request.copy(recipient = "0x3333333333333333333333333333333333333333"),
            order,
        )
    }

    /** Limits direct wallet preparation to the backend's supported stablecoin EVM matrix. */
    @Test
    fun matchesSupportedOrders() {
        assertTrue(WalletPaymentUri.isSupported(order))
        assertFalse(WalletPaymentUri.isSupported(order.copy(network = "Tron")))
        assertFalse(WalletPaymentUri.isSupported(order.copy(currency = "ETH")))
    }
}
