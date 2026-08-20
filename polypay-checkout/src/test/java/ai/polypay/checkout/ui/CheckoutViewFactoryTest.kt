package ai.polypay.checkout.ui

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import ai.polypay.checkout.model.CheckoutOrder
import ai.polypay.checkout.model.PaymentMethodGroup
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Rendering tests for both required native checkout pages. */
@RunWith(RobolectricTestRunner::class)
class CheckoutViewFactoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val factory = CheckoutViewFactory(context)
    private val order = CheckoutOrder(
        tradeId = "trade_12345678",
        merchantOrderId = "ORDER-42",
        currency = "USDT",
        network = "Tron",
        amount = "10.00",
        actualAmount = "10.01",
        address = "TXAddress123456789",
        status = 1,
        expirationTime = 2_000_000_000,
        merchantName = "Example Merchant",
    )

    /** Proves the native method page renders server-provided currency and network choices. */
    @Test
    fun rendersPaymentMethodSelectionPage() {
        val view = factory.methodSelection(
            order = order.copy(currency = "", network = "", address = "", status = 0),
            methods = listOf(
                PaymentMethodGroup("Tron", listOf("USDT", "TRX"), "USDT", emptyMap()),
                PaymentMethodGroup("Ethereum", listOf("USDC"), null, emptyMap()),
            ),
            busy = false,
            onSelect = {},
            onClose = {},
        )
        val text = allText(view)
        assertTrue(text.contains("USDT"))
        assertTrue(text.contains("USDC"))
        assertTrue(text.contains("Tron"))
        findTextView(view, "USDC")?.performClick()
        assertTrue(allText(view).contains("Ethereum"))
    }

    /** Proves the native payment page renders exact amount, network, and address. */
    @Test
    fun rendersPaymentPage() {
        val view = factory.payment(order, "Waiting for payment", onChangeMethod = {}, onClose = {})
        val text = allText(view)
        assertTrue(text.contains("10.01 USDT"))
        assertTrue(text.contains("TXAddress123456789"))
        assertTrue(text.contains("USDT · Tron"))
    }

    /** Collects text recursively from a native view hierarchy. */
    private fun allText(view: View): String = buildString {
        if (view is TextView) append(view.text).append('\n')
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) append(allText(view.getChildAt(index)))
        }
    }

    /** Finds the first clickable text view with an exact label. */
    private fun findTextView(view: View, text: String): TextView? {
        if (view is TextView && view.text.toString() == text) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findTextView(view.getChildAt(index), text)?.let { return it }
            }
        }
        return null
    }
}
