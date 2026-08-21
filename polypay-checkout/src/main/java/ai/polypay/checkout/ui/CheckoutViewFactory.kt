package ai.polypay.checkout.ui

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import ai.polypay.checkout.R
import ai.polypay.checkout.model.CheckoutOrder
import ai.polypay.checkout.model.PaymentMethodGroup
import ai.polypay.checkout.model.PaymentSelection
import ai.polypay.checkout.model.PaymentSelectionPolicy

/** Creates the SDK's native Android views without requiring a host UI framework. */
internal class CheckoutViewFactory(private val context: Context) {
    private val density = context.resources.displayMetrics.density
    private val textPrimary = color(light = 0xFF111827.toInt(), dark = 0xFFF9FAFB.toInt())
    private val textSecondary = color(light = 0xFF64748B.toInt(), dark = 0xFFCBD5E1.toInt())
    private val surface = color(light = 0xFFFFFFFF.toInt(), dark = 0xFF111827.toInt())

    /** Builds a full-screen loading state. */
    fun loading(): View = page().apply {
        gravity = Gravity.CENTER_HORIZONTAL
        addView(ProgressBar(context), margins(top = 72, bottom = 20))
        addView(label(context.getString(R.string.polypay_loading), 17, bold = true))
    }

    /** Builds a retryable error state with a stable diagnostic message. */
    fun error(message: String, retry: () -> Unit, close: () -> Unit): View = page().apply {
        gravity = Gravity.CENTER_HORIZONTAL
        addView(title(context.getString(R.string.polypay_error)), margins(top = 64, bottom = 12))
        addView(label(message, 14), margins(bottom = 28))
        addView(primaryButton(context.getString(R.string.polypay_retry), retry), matchMargins(bottom = 12))
        addView(secondaryButton(context.getString(R.string.polypay_close), close), matchMargins())
    }

    /** Builds currency and network selectors backed by server-provided options. */
    fun methodSelection(
        order: CheckoutOrder,
        methods: List<PaymentMethodGroup>,
        busy: Boolean,
        onSelect: (PaymentSelection) -> Unit,
        onClose: () -> Unit,
    ): View {
        val page = page()
        page.addView(header(onClose))
        page.addView(title(context.getString(R.string.polypay_choose_method)), margins(top = 28, bottom = 8))
        order.merchantName?.let { page.addView(label(it, 14), margins(bottom = 20)) }
        page.addView(summary("${order.amount} USD", order.merchantOrderId), matchMargins(bottom = 24))

        val currencies = PaymentSelectionPolicy.currencies(methods)
        var selection = PaymentSelectionPolicy.preferred(methods)
        val networkGroup = RadioGroup(context).apply { orientation = RadioGroup.VERTICAL }
        val currencyRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(10))
        }

        /** Rebuilds network choices whenever a currency changes. */
        fun showNetworks(currency: String) {
            networkGroup.removeAllViews()
            val available = methods.filter { currency in it.currencies }
            if (available.none { it.network == selection?.network }) {
                selection = PaymentSelection(currency, available.firstOrNull()?.network.orEmpty())
            } else {
                selection = PaymentSelection(currency, selection?.network.orEmpty())
            }
            available.forEach { method ->
                val quote = method.feeQuotes[currency]
                val fee = if (quote?.status == "available" && quote.standardFee != null) {
                    "\n${context.getString(R.string.polypay_fee, quote.standardFee, quote.feeCurrency.orEmpty())}"
                } else ""
                networkGroup.addView(RadioButton(context).apply {
                    text = method.network + fee
                    setTextColor(textPrimary)
                    textSize = 15f
                    setPadding(dp(8), dp(10), dp(8), dp(10))
                    isChecked = method.network == selection?.network
                    setOnClickListener { selection = PaymentSelection(currency, method.network) }
                }, matchMargins(bottom = 6))
            }
        }

        currencies.forEach { currency ->
            currencyRow.addView(secondaryButton(currency) {
                selection = PaymentSelection(currency, "")
                showNetworks(currency)
            }, LinearLayout.LayoutParams(dp(108), dp(46)).apply { marginEnd = dp(8) })
        }
        page.addView(sectionLabel(context.getString(R.string.polypay_choose_currency)))
        page.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(currencyRow)
        }, matchMargins(bottom = 16))
        page.addView(sectionLabel(context.getString(R.string.polypay_choose_network)))
        page.addView(networkGroup, matchMargins(bottom = 20))
        showNetworks(selection?.currency ?: currencies.firstOrNull().orEmpty())
        page.addView(primaryButton(context.getString(R.string.polypay_continue)) {
            selection?.takeIf { it.currency.isNotBlank() && it.network.isNotBlank() }?.let(onSelect)
        }.apply { isEnabled = !busy }, matchMargins(bottom = 12))
        page.addView(securityNote(), matchMargins(bottom = 24))
        return page
    }

    /** Builds the wallet-first exact payment and observed-state page. */
    fun payment(
        order: CheckoutOrder,
        statusMessage: String,
        onOpenWallet: (() -> Unit)?,
        onChangeMethod: () -> Unit,
        onClose: () -> Unit,
    ): View = page().apply {
        gravity = Gravity.CENTER_HORIZONTAL
        addView(header(onClose))
        addView(title(order.merchantName ?: context.getString(R.string.polypay_title)), margins(top = 24, bottom = 8))
        addView(label(statusMessage, 14), margins(bottom = 20))
        addView(sectionLabel(context.getString(R.string.polypay_amount_due)), matchMargins())
        addView(label("${order.actualAmount} ${order.currency}", 28, bold = true).apply {
            gravity = Gravity.CENTER
        }, matchMargins(bottom = 8))
        onOpenWallet?.let { openWallet ->
            addView(primaryButton(context.getString(R.string.polypay_open_wallet), openWallet), matchMargins(bottom = 12))
        }
        addView(secondaryButton(context.getString(R.string.polypay_copy_amount)) {
            copy(order.actualAmount)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { bottomMargin = dp(20) })
        if (order.address.isNotBlank()) {
            addView(sectionLabel(context.getString(R.string.polypay_receiving_address)), matchMargins())
            addView(label(order.address, 14).apply {
                gravity = Gravity.CENTER
                setTextIsSelectable(true)
            }, matchMargins(bottom = 10))
            addView(secondaryButton(context.getString(R.string.polypay_copy_address)) {
                copy(order.address)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { bottomMargin = dp(16) })
        }
        if (onOpenWallet == null) {
            addView(label(context.getString(R.string.polypay_manual_payment), 13).apply {
                gravity = Gravity.CENTER
            }, matchMargins(bottom = 16))
        }
        addView(label("${order.currency} · ${order.network}", 15, bold = true), margins(bottom = 12))
        addView(secondaryButton(context.getString(R.string.polypay_change_method), onChangeMethod), matchMargins(bottom = 12))
        addView(securityNote(), matchMargins(bottom = 28))
    }

    /** Copies a payment value into the system clipboard. */
    private fun copy(value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("PolyPay", value))
        Toast.makeText(context, R.string.polypay_copied, Toast.LENGTH_SHORT).show()
    }

    /** Creates the shared scrollable content container. */
    private fun page(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(12), dp(20), dp(20))
        setBackgroundColor(surface)
    }

    /** Creates a lightweight native checkout header. */
    private fun header(onClose: () -> Unit): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(label(context.getString(R.string.polypay_title), 18, bold = true), LinearLayout.LayoutParams(0, dp(52), 1f).apply {
            gravity = Gravity.CENTER_VERTICAL
        })
        addView(secondaryButton("×", onClose), LinearLayout.LayoutParams(dp(52), dp(44)))
    }

    /** Creates an order summary card. */
    private fun summary(amount: String, orderId: String?): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        setBackgroundColor(color(light = 0xFFF1F5F9.toInt(), dark = 0xFF1F2937.toInt()))
        addView(label(amount, 24, bold = true))
        orderId?.let { addView(label(it, 12), margins(top = 6)) }
    }

    /** Creates a security-boundary note shown on payment screens. */
    private fun securityNote(): TextView = label(context.getString(R.string.polypay_security_note), 12).apply {
        gravity = Gravity.CENTER
    }

    /** Creates a prominent page title. */
    private fun title(text: String): TextView = label(text, 24, bold = true)

    /** Creates a section label. */
    private fun sectionLabel(text: String): TextView = label(text, 13, bold = true).apply {
        setTextColor(textSecondary)
    }

    /** Creates a text view using checkout typography colors. */
    private fun label(text: String, size: Int, bold: Boolean = false): TextView = TextView(context).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(if (bold) textPrimary else textSecondary)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    /** Creates a brand-colored primary action. */
    private fun primaryButton(text: String, action: () -> Unit): Button = Button(context).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.WHITE)
        setBackgroundColor(0xFF6D5EF5.toInt())
        setOnClickListener { action() }
    }

    /** Creates a neutral secondary action. */
    private fun secondaryButton(text: String, action: () -> Unit): Button = Button(context).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { action() }
    }

    /** Creates match-width layout params with optional margins. */
    private fun matchMargins(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }

    /** Creates wrap-content layout params with optional margins. */
    private fun margins(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }

    /** Converts density-independent pixels to physical pixels. */
    private fun dp(value: Int): Int = (value * density).toInt()

    /** Chooses a light or dark color using the current system night mode. */
    private fun color(light: Int, dark: Int): Int {
        val mask = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return if (mask == android.content.res.Configuration.UI_MODE_NIGHT_YES) dark else light
    }

    /** Wraps a content view in a vertical scroll container. */
    fun scroll(content: View): View = ScrollView(context).apply {
        isFillViewport = true
        addView(content)
    }
}
