package ai.polypay.checkout

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import ai.polypay.checkout.ui.PolyPayCheckoutActivity

/** Activity Result API contract for presenting PolyPay's native checkout UI. */
class PolyPayCheckoutContract : ActivityResultContract<PolyPayCheckoutOptions, PolyPayCheckoutResult>() {
    /** Builds the explicit SDK activity intent after validating the configuration. */
    override fun createIntent(context: Context, input: PolyPayCheckoutOptions): Intent {
        input.validate()
        return PolyPayCheckoutActivity.createIntent(context, input)
    }

    /** Converts activity result extras into a non-authoritative checkout outcome. */
    override fun parseResult(resultCode: Int, intent: Intent?): PolyPayCheckoutResult {
        val rawOutcome = intent?.getStringExtra(PolyPayCheckoutActivity.EXTRA_OUTCOME)
        val outcome = runCatching { PolyPayCheckoutResult.Outcome.valueOf(rawOutcome.orEmpty()) }
            .getOrDefault(PolyPayCheckoutResult.Outcome.CLOSED)
        return PolyPayCheckoutResult(
            outcome = outcome,
            tradeId = intent?.getStringExtra(PolyPayCheckoutActivity.EXTRA_TRADE_ID),
            errorCode = intent?.getStringExtra(PolyPayCheckoutActivity.EXTRA_ERROR_CODE),
        )
    }
}
