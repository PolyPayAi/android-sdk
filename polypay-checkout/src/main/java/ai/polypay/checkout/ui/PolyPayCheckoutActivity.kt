package ai.polypay.checkout.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import ai.polypay.checkout.CheckoutUrlParser
import ai.polypay.checkout.PolyPayCheckoutOptions
import ai.polypay.checkout.PolyPayCheckoutResult
import ai.polypay.checkout.R
import ai.polypay.checkout.internal.PolyPayApi
import ai.polypay.checkout.internal.PolyPayApiException
import ai.polypay.checkout.internal.WalletPaymentUri
import ai.polypay.checkout.model.CheckoutOrder
import ai.polypay.checkout.model.PaymentMethodGroup
import ai.polypay.checkout.model.PaymentSelection
import java.util.concurrent.Executors

/** Native Android payment-method selection and payment activity. */
class PolyPayCheckoutActivity : AppCompatActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var api: PolyPayApi
    private lateinit var viewFactory: CheckoutViewFactory
    private lateinit var tradeId: String
    private var pollIntervalMillis: Long = 5_000
    private var currentOrder: CheckoutOrder? = null
    private var methods: List<PaymentMethodGroup> = emptyList()
    private var errorCode: String? = null
    private var walletPaymentUri: String? = null
    private var walletPreparationComplete = false

    /** Initializes the secure API boundary and loads the server-created checkout. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewFactory = CheckoutViewFactory(this)
        val checkoutUrl = intent.getStringExtra(EXTRA_CHECKOUT_URL).orEmpty()
        val allowedHosts = intent.getStringArrayListExtra(EXTRA_ALLOWED_HOSTS)?.toSet().orEmpty()
        val apiBaseUrl = intent.getStringExtra(EXTRA_API_BASE_URL).orEmpty()
        pollIntervalMillis = intent.getLongExtra(EXTRA_POLL_INTERVAL, 5_000)
        try {
            tradeId = CheckoutUrlParser.parse(checkoutUrl, allowedHosts).tradeId
            api = PolyPayApi(apiBaseUrl)
        } catch (error: IllegalArgumentException) {
            finishWith(PolyPayCheckoutResult.Outcome.ERROR, "invalid_configuration")
            return
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            /** Returns a closed outcome instead of inferring payment from navigation. */
            override fun handleOnBackPressed() {
                finishWith(PolyPayCheckoutResult.Outcome.CLOSED)
            }
        })
        loadCheckout()
    }

    /** Stops polling and background work when the checkout is destroyed. */
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        super.onDestroy()
    }

    /** Loads the current order and decides which native page to show. */
    private fun loadCheckout() {
        show(viewFactory.loading())
        background(
            work = { api.getCheckout(tradeId) },
            success = { order ->
                currentOrder = order
                when (order.status) {
                    STATUS_CHECKOUT_PENDING -> loadMethods(order)
                    STATUS_EXPIRED -> showTerminal(PolyPayCheckoutResult.Outcome.EXPIRED)
                    STATUS_CANCELLED -> showTerminal(PolyPayCheckoutResult.Outcome.CANCELLED)
                    else -> showPayment(order)
                }
            },
        )
    }

    /** Loads merchant-enabled methods for the selection page. */
    private fun loadMethods(order: CheckoutOrder) {
        show(viewFactory.loading())
        background(
            work = { api.getPaymentMethods(tradeId) },
            success = { loaded ->
                methods = loaded
                if (loaded.isEmpty()) showError("No payment methods are available", "no_payment_methods")
                else showSelection(order, busy = false)
            },
        )
    }

    /** Shows the native selection page and submits the chosen pair. */
    private fun showSelection(order: CheckoutOrder, busy: Boolean) {
        show(viewFactory.methodSelection(
            order = order,
            methods = methods,
            busy = busy,
            onSelect = ::selectMethod,
            onClose = { finishWith(PolyPayCheckoutResult.Outcome.CLOSED) },
        ))
    }

    /** Converts the placeholder into a payable order using the public checkout API. */
    private fun selectMethod(selection: PaymentSelection) {
        val order = currentOrder ?: return
        showSelection(order, busy = true)
        walletPaymentUri = null
        walletPreparationComplete = false
        background(
            work = { api.selectPaymentMethod(tradeId, selection.network, selection.currency) },
            success = { updated ->
                currentOrder = updated
                showPayment(updated)
            },
        )
    }

    /** Prepares a compatible wallet request, renders payment, and starts polling. */
    private fun showPayment(order: CheckoutOrder) {
        if (!walletPreparationComplete && order.status == STATUS_WAIT_PAY && WalletPaymentUri.isSupported(order)) {
            prepareWalletPayment(order)
            return
        }
        walletPreparationComplete = true
        val message = when (order.status) {
            STATUS_CONFIRMING -> getString(R.string.polypay_confirming)
            STATUS_SUCCESS, STATUS_ADMIN_MARKED_PAID -> getString(R.string.polypay_submitted)
            else -> getString(R.string.polypay_waiting)
        }
        show(viewFactory.payment(
            order = order,
            statusMessage = message,
            onOpenWallet = walletPaymentUri?.let { uri -> { openWallet(uri) } },
            onChangeMethod = { loadMethods(order) },
            onClose = {
                val outcome = if (order.status in setOf(STATUS_CONFIRMING, STATUS_SUCCESS, STATUS_ADMIN_MARKED_PAID)) {
                    PolyPayCheckoutResult.Outcome.PAYMENT_DETECTED
                } else PolyPayCheckoutResult.Outcome.CLOSED
                finishWith(outcome)
            },
        ))
        handler.removeCallbacksAndMessages(null)
        if (order.status !in TERMINAL_STATUSES) handler.postDelayed(::pollStatus, pollIntervalMillis)
    }

    /** Loads and validates server-authoritative wallet parameters before exposing the wallet action. */
    private fun prepareWalletPayment(order: CheckoutOrder) {
        show(viewFactory.loading())
        background(
            work = {
                val request = api.prepareWalletPayment(tradeId)
                runCatching { WalletPaymentUri.build(request, order) }.getOrElse { error ->
                    throw PolyPayApiException(
                        "invalid_wallet_payment",
                        error.message ?: "Wallet payment parameters are invalid",
                    )
                }
            },
            success = { uri ->
                walletPaymentUri = uri
                walletPreparationComplete = true
                showPayment(order)
            },
        )
    }

    /** Opens a compatible wallet through Android's standard URI dispatch. */
    private fun openWallet(paymentUri: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(paymentUri))
        if (intent.resolveActivity(packageManager) == null) {
            android.widget.Toast.makeText(this, R.string.polypay_wallet_unavailable, android.widget.Toast.LENGTH_LONG).show()
            return
        }
        startActivity(Intent.createChooser(intent, getString(R.string.polypay_open_wallet)))
    }

    /** Polls for display updates; merchant fulfillment must still reconcile server-side. */
    private fun pollStatus() {
        background(
            work = { api.checkStatus(tradeId) },
            success = { status ->
                val order = currentOrder ?: return@background
                val updated = order.copy(status = status.status)
                currentOrder = updated
                when (status.status) {
                    STATUS_EXPIRED -> showTerminal(PolyPayCheckoutResult.Outcome.EXPIRED)
                    STATUS_CANCELLED -> showTerminal(PolyPayCheckoutResult.Outcome.CANCELLED)
                    else -> showPayment(updated)
                }
            },
            failure = {
                handler.postDelayed(::pollStatus, pollIntervalMillis.coerceAtLeast(8_000))
            },
        )
    }

    /** Shows an expired or cancelled terminal state without a paid assertion. */
    private fun showTerminal(outcome: PolyPayCheckoutResult.Outcome) {
        val message = if (outcome == PolyPayCheckoutResult.Outcome.EXPIRED) {
            getString(R.string.polypay_expired)
        } else getString(R.string.polypay_cancelled)
        show(viewFactory.error(message, retry = ::loadCheckout, close = { finishWith(outcome) }))
    }

    /** Executes one API call off the main thread and maps stable failures. */
    private fun <T> background(
        work: () -> T,
        success: (T) -> Unit,
        failure: ((Throwable) -> Unit)? = null,
    ) {
        executor.execute {
            runCatching(work).fold(
                onSuccess = { value -> handler.post { if (!isFinishing) success(value) } },
                onFailure = { error -> handler.post {
                    if (isFinishing) return@post
                    if (failure != null) failure(error) else {
                        val code = (error as? PolyPayApiException)?.code ?: "network_error"
                        showError(error.message ?: getString(R.string.polypay_error), code)
                    }
                } },
            )
        }
    }

    /** Presents a retryable error and stores only a stable error code. */
    private fun showError(message: String, code: String) {
        errorCode = code
        show(viewFactory.error(message, retry = ::loadCheckout, close = {
            finishWith(PolyPayCheckoutResult.Outcome.ERROR, code)
        }))
    }

    /** Sets one scrollable checkout page as the activity content. */
    private fun show(content: android.view.View) {
        setContentView(viewFactory.scroll(content))
    }

    /** Finishes with a deliberately non-authoritative client outcome. */
    private fun finishWith(outcome: PolyPayCheckoutResult.Outcome, code: String? = errorCode) {
        val result = Intent()
            .putExtra(EXTRA_OUTCOME, outcome.name)
            .putExtra(EXTRA_TRADE_ID, if (::tradeId.isInitialized) tradeId else null)
            .putExtra(EXTRA_ERROR_CODE, code)
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    companion object {
        const val EXTRA_OUTCOME = "ai.polypay.checkout.OUTCOME"
        const val EXTRA_TRADE_ID = "ai.polypay.checkout.TRADE_ID"
        const val EXTRA_ERROR_CODE = "ai.polypay.checkout.ERROR_CODE"
        private const val EXTRA_CHECKOUT_URL = "ai.polypay.checkout.CHECKOUT_URL"
        private const val EXTRA_ALLOWED_HOSTS = "ai.polypay.checkout.ALLOWED_HOSTS"
        private const val EXTRA_API_BASE_URL = "ai.polypay.checkout.API_BASE_URL"
        private const val EXTRA_POLL_INTERVAL = "ai.polypay.checkout.POLL_INTERVAL"
        private const val STATUS_CHECKOUT_PENDING = 0
        private const val STATUS_WAIT_PAY = 1
        private const val STATUS_SUCCESS = 2
        private const val STATUS_EXPIRED = 3
        private const val STATUS_CANCELLED = 4
        private const val STATUS_CONFIRMING = 6
        private const val STATUS_ADMIN_MARKED_PAID = 7
        private val TERMINAL_STATUSES = setOf(STATUS_SUCCESS, STATUS_EXPIRED, STATUS_CANCELLED, STATUS_ADMIN_MARKED_PAID)

        /** Creates an explicit intent containing no merchant secret. */
        fun createIntent(context: Context, options: PolyPayCheckoutOptions): Intent =
            Intent(context, PolyPayCheckoutActivity::class.java)
                .putExtra(EXTRA_CHECKOUT_URL, options.checkoutUrl)
                .putStringArrayListExtra(EXTRA_ALLOWED_HOSTS, ArrayList(options.allowedCheckoutHosts))
                .putExtra(EXTRA_API_BASE_URL, options.apiBaseUrl)
                .putExtra(EXTRA_POLL_INTERVAL, options.pollIntervalMillis)
    }
}
