package ai.polypay.checkout.sample

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import ai.polypay.checkout.PolyPayCheckoutContract
import ai.polypay.checkout.PolyPayCheckoutOptions

/** Minimal merchant app demonstrating server-created native checkout. */
class MainActivity : AppCompatActivity() {
    private lateinit var checkoutUrl: EditText
    private lateinit var resultLabel: TextView

    private val checkout = registerForActivityResult(PolyPayCheckoutContract()) { result ->
        resultLabel.text = "Outcome: ${result.outcome}; reconcile ${result.tradeId} on your server"
    }

    /** Builds a small test harness without embedding a merchant API Key. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkoutUrl = EditText(this).apply {
            hint = "Paste the checkoutUrl returned by your server"
            setSingleLine()
        }
        resultLabel = TextView(this)
        val button = Button(this).apply {
            text = "Open native PolyPay checkout"
            isAllCaps = false
            setOnClickListener { openCheckout() }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            addView(checkoutUrl)
            addView(button)
            addView(resultLabel)
        })
    }

    /** Validates and opens the server-created checkout URL. */
    private fun openCheckout() {
        runCatching {
            checkout.launch(PolyPayCheckoutOptions(checkoutUrl = checkoutUrl.text.toString()))
        }.onFailure { resultLabel.text = it.message }
    }
}
