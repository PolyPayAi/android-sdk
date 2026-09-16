package ai.polypay.checkout.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import ai.polypay.checkout.R
import ai.polypay.checkout.model.CheckoutOrder
import ai.polypay.checkout.model.PaymentMethodGroup
import ai.polypay.checkout.model.PaymentSelection
import ai.polypay.checkout.model.PaymentSelectionPolicy
import java.math.RoundingMode

/** Creates the SDK's native Android views without requiring a host UI framework. */
internal class CheckoutViewFactory(private val context: Context) {
    private val density = context.resources.displayMetrics.density
    private val backgroundColor = 0xFF070910.toInt()
    private val panel = 0xFF121826.toInt()
    private val panelRaised = 0xFF1B2333.toInt()
    private val textPrimary = 0xFFEAEFF6.toInt()
    private val textSecondary = 0xFF9BA6B8.toInt()
    private val textFaint = 0xFF78849A.toInt()
    private val line = 0xFF30394D.toInt()
    private val brand = 0xFF8B92FF.toInt()
    private val brandSoft = 0xFF202846.toInt()
    private val brandLine = 0xFF424A74.toInt()
    private val success = 0xFF34D399.toInt()

    /** Builds a full-screen loading state. */
    fun loading(onBack: () -> Unit): View = page().apply {
        gravity = Gravity.CENTER_HORIZONTAL
        addView(header(onBack), matchMargins(bottom = 60))
        addView(ProgressBar(context).apply {
            indeterminateTintList = ColorStateList.valueOf(brand)
        }, margins(bottom = 20))
        addView(label(context.getString(R.string.polypay_loading), 16, bold = true))
    }

    /** Builds a retryable error state with a stable diagnostic message. */
    fun error(message: String, retry: () -> Unit, close: () -> Unit): View = page().apply {
        gravity = Gravity.CENTER_HORIZONTAL
        addView(header(close), matchMargins(bottom = 48))
        addView(title(context.getString(R.string.polypay_error)), margins(bottom = 12))
        addView(label(message, 14).apply { gravity = Gravity.CENTER }, matchMargins(bottom = 28))
        addView(primaryButton(context.getString(R.string.polypay_retry), retry), fixedHeight(50, bottom = 12))
        addView(secondaryButton(context.getString(R.string.polypay_close), close), fixedHeight(48))
    }

    /** Builds the Web-aligned mobile currency and network selector. */
    fun methodSelection(
        order: CheckoutOrder,
        methods: List<PaymentMethodGroup>,
        busy: Boolean,
        onSelect: (PaymentSelection) -> Unit,
        onClose: () -> Unit,
    ): View {
        val content = page().apply { setPadding(dp(12), dp(76), dp(12), dp(88)) }
        content.addView(orderSummary(order), matchMargins())
        val workspace = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(16))
            background = rounded(panel, 18)
        }
        content.addView(workspace, matchMargins(bottom = 8))

        val preferred = PaymentSelectionPolicy.preferred(methods)
        var selection = preferred
        val actionButton = primaryButton(context.getString(R.string.polypay_continue)) {
            selection?.takeIf { it.currency.isNotBlank() && it.network.isNotBlank() }?.let(onSelect)
        }

        /** Re-renders the compact selector when the local choice changes. */
        fun renderSelection() {
            workspace.removeAllViews()
            workspace.addView(selectedMethod(selection), matchMargins(bottom = 12))
            workspace.addView(stepHeader("1", context.getString(R.string.polypay_choose_currency_step)), matchMargins(bottom = 6))

            val currencies = PaymentSelectionPolicy.currencies(methods)
            workspace.addView(optionGrid(currencies.map { currency ->
                val checked = currency == selection?.currency
                optionCard(
                    icon = currency,
                    title = currency,
                    subtitle = currencyFullName(currency),
                    checked = checked,
                    enabled = !busy,
                    recommended = false,
                ) {
                    val networks = methods.filter { currency in it.currencies }
                    val nextNetwork = preferred?.takeIf { it.currency == currency }?.network
                        ?.takeIf { target -> networks.any { it.network == target } }
                        ?: networks.firstOrNull()?.network.orEmpty()
                    selection = PaymentSelection(currency, nextNetwork)
                    renderSelection()
                }
            }), matchMargins(bottom = 10))

            workspace.addView(stepHeader("2", context.getString(R.string.polypay_choose_network_step)), matchMargins(bottom = 6))
            val selectedCurrency = selection?.currency.orEmpty()
            val networks = methods.filter { selectedCurrency in it.currencies }
            workspace.addView(optionGrid(networks.map { method ->
                val checked = method.network == selection?.network
                optionCard(
                    icon = method.network,
                    title = networkName(method.network),
                    subtitle = transferStandard(method.network, selectedCurrency),
                    checked = checked,
                    enabled = !busy,
                    recommended = preferred?.network == method.network && preferred.currency == selectedCurrency,
                ) {
                    selection = PaymentSelection(selectedCurrency, method.network)
                    renderSelection()
                }
            }), matchMargins(bottom = 10))

            val validSelection = selection?.takeIf { it.currency.isNotBlank() && it.network.isNotBlank() }
            val actionLabel = if (busy) {
                context.getString(R.string.polypay_creating_payment)
            } else validSelection?.let(::paymentActionLabel) ?: context.getString(R.string.polypay_continue)
            actionButton.text = actionLabel
            actionButton.isEnabled = validSelection != null && !busy
            actionButton.alpha = if (actionButton.isEnabled) 1f else 0.55f
        }

        renderSelection()
        return FrameLayout(context).apply {
            setBackgroundColor(backgroundColor)
            addView(scroll(content), FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
            addView(header(onClose).apply { setBackgroundColor(backgroundColor) }, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(56),
                Gravity.TOP,
            ).apply {
                leftMargin = dp(12)
                topMargin = dp(8)
                rightMargin = dp(12)
            })
            addView(LinearLayout(context).apply {
                setPadding(dp(12), dp(8), dp(12), dp(10))
                setBackgroundColor(0xF2121826.toInt())
                addView(actionButton, fixedHeight(50))
            }, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ))
        }
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
        addView(header(onClose), matchMargins(bottom = 12))
        addView(orderSummary(order), matchMargins(bottom = 12))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(22), dp(16), dp(20))
            background = rounded(panel, 18)
            addView(label(statusMessage, 14, bold = true), margins(bottom = 18))
            addView(sectionLabel(context.getString(R.string.polypay_amount_due)), matchMargins(bottom = 8))
            addView(label("${order.actualAmount} ${order.currency}", 30, bold = true).apply {
                gravity = Gravity.CENTER
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }, matchMargins(bottom = 18))
            onOpenWallet?.let { openWallet ->
                addView(primaryButton(context.getString(R.string.polypay_open_wallet), openWallet), fixedHeight(52, bottom = 12))
            }
            addView(secondaryButton(context.getString(R.string.polypay_copy_amount)) { copy(order.actualAmount) }, fixedHeight(48, bottom = 20))
            if (order.address.isNotBlank()) {
                addView(sectionLabel(context.getString(R.string.polypay_receiving_address)), matchMargins(bottom = 8))
                addView(label(order.address, 13).apply {
                    gravity = Gravity.CENTER
                    setTextIsSelectable(true)
                }, matchMargins(bottom = 10))
                addView(secondaryButton(context.getString(R.string.polypay_copy_address)) { copy(order.address) }, fixedHeight(48, bottom = 16))
            }
            if (onOpenWallet == null) {
                addView(label(context.getString(R.string.polypay_manual_payment), 13).apply {
                    gravity = Gravity.CENTER
                }, matchMargins(bottom = 16))
            }
            addView(label("${order.currency} · ${networkName(order.network)}", 15, bold = true), margins(bottom = 14))
            addView(secondaryButton(context.getString(R.string.polypay_change_method), onChangeMethod), fixedHeight(48, bottom = 16))
            addView(securityNote(), matchMargins())
        }, matchMargins(bottom = 8))
    }

    /** Creates an Android-style top app bar with leading back navigation. */
    private fun header(onBack: () -> Unit): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(ImageButton(context).apply {
            setImageResource(R.drawable.polypay_ic_arrow_back)
            imageTintList = ColorStateList.valueOf(textPrimary)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            contentDescription = context.getString(R.string.polypay_navigate_back)
            setOnClickListener { onBack() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(4) })
        addView(label(context.getString(R.string.polypay_title), 20, bold = true).apply {
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, dp(56), 1f))
    }

    /** Creates the merchant and amount summary card used by both native checkout stages. */
    private fun orderSummary(order: CheckoutOrder): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF1B1942.toInt(), 0xFF13123A.toInt(), 0xFF0C0C26.toInt()),
        ).apply { cornerRadius = dp(18).toFloat() }

        val merchantName = order.merchantName ?: "PolyPay"
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val avatar = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(R.drawable.polypay_brand)
                background = rounded(0x22FFFFFF, 10, 0x33FFFFFF)
                clipToOutline = true
                contentDescription = merchantName
            }
            addView(avatar, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(11) })
            RemoteImageLoader.load(avatar, order.merchantAvatar)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(merchantName, 15, bold = true).apply { setTextColor(0xFFECEAFF.toInt()) })
                addView(label(context.getString(R.string.polypay_requests_payment), 12).apply {
                    setTextColor(0xFFABA5DC.toInt())
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }, matchMargins(bottom = 18))

        addView(label(context.getString(R.string.polypay_amount_due), 12, bold = true).apply {
            setTextColor(0xFFABA5DC.toInt())
        }, margins(bottom = 5))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            addView(label(formatFiat(order.amount), 35, bold = true).apply {
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                setTextColor(0xFFECEAFF.toInt())
            })
            addView(label("USD", 16, bold = true).apply {
                setTextColor(0xFFABA5DC.toInt())
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(8)
                bottomMargin = dp(4)
            })
        }, matchMargins(bottom = 16))

        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = rounded(0x16FFFFFF, 14, 0x24FFFFFF)
            order.merchantOrderId?.let { orderId ->
                addView(label(context.getString(R.string.polypay_merchant_order_id), 13).apply {
                    setTextColor(0xFFABA5DC.toInt())
                }, margins(bottom = 5))
                addView(label(orderId, 12, bold = true).apply {
                    setTextColor(0xFFECEAFF.toInt())
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    setTextIsSelectable(true)
                    ellipsize = TextUtils.TruncateAt.END
                    maxLines = 1
                }, matchMargins(bottom = 9))
            }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(label(context.getString(R.string.polypay_order_amount), 13).apply {
                    setTextColor(0xFFABA5DC.toInt())
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(label("${formatFiat(order.amount)} USD", 13, bold = true).apply {
                    setTextColor(0xFFECEAFF.toInt())
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                })
            })
        }, matchMargins(bottom = 2))
    }

    /** Creates the selected-method summary strip. */
    private fun selectedMethod(selection: PaymentSelection?): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(10), dp(12), dp(10))
        background = rounded(brandSoft, 13, brandLine)
        addView(label(context.getString(R.string.polypay_selected_method), 11, bold = true).apply {
            setTextColor(brand)
            letterSpacing = 0.04f
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f))
        if (selection != null) {
            addView(tokenNetworkIcon(selection.currency, selection.network), LinearLayout.LayoutParams(dp(32), dp(30)).apply {
                marginEnd = dp(7)
            })
            val standard = transferStandard(selection.network, selection.currency)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(label("${selection.currency} · ${networkName(selection.network)}", 14, bold = true).apply {
                    maxLines = 1
                })
                standard?.takeIf(String::isNotBlank)?.let { value ->
                    addView(label(value, 10).apply { setTextColor(textFaint) })
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.5f))
        }
    }

    /** Creates a numbered selector-section heading. */
    private fun stepHeader(step: String, text: String): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(label(step, 12, bold = true).apply {
            gravity = Gravity.CENTER
            setTextColor(brand)
            background = rounded(brandSoft, 10)
        }, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) })
        addView(label(text, 14, bold = true))
    }

    /** Lays out compact option cards in the Web checkout's two-column grid. */
    private fun optionGrid(cards: List<View>): View = GridLayout(context).apply {
        columnCount = 2
        cards.forEachIndexed { index, card ->
            addView(card, GridLayout.LayoutParams().apply {
                width = 0
                height = dp(50)
                columnSpec = GridLayout.spec(index % 2, 1f)
                setMargins(if (index % 2 == 0) 0 else dp(5), 0, if (index % 2 == 0) dp(5) else 0, dp(6))
            })
        }
    }

    /** Creates one currency or network radio card with a local raster asset. */
    private fun optionCard(
        icon: String,
        title: String,
        subtitle: String?,
        checked: Boolean,
        enabled: Boolean,
        recommended: Boolean,
        onClick: () -> Unit,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(11), dp(7), dp(5), dp(7))
        background = rounded(if (checked) brandSoft else panel, 12, if (checked) brand else line, if (checked) 2 else 1)
        isEnabled = enabled
        isClickable = enabled
        alpha = if (enabled) 1f else 0.6f
        setOnClickListener { onClick() }
        addView(assetIcon(icon, 24), LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(9) })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(title, 14, bold = true).apply {
                maxLines = 1
                isClickable = enabled
                setOnClickListener { onClick() }
            }, matchMargins())
            if (!subtitle.isNullOrBlank() || recommended) {
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    subtitle?.takeIf(String::isNotBlank)?.let { value ->
                        addView(label(value, 11).apply {
                            setTextColor(textFaint)
                            ellipsize = TextUtils.TruncateAt.END
                            maxLines = 1
                        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    }
                    if (recommended) {
                        addView(label(context.getString(R.string.polypay_recommended), 8, bold = true).apply {
                            setTextColor(success)
                            setPadding(dp(4), dp(1), dp(4), dp(1))
                            background = rounded(0xFF10241C.toInt(), 5)
                        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginStart = dp(3)
                        })
                    }
                })
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(Space(context), LinearLayout.LayoutParams(dp(2), 1))
        addView(RadioButton(context).apply {
            isChecked = checked
            isClickable = false
            buttonTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(brand, line),
            )
        }, LinearLayout.LayoutParams(dp(30), dp(42)))
    }

    /** Creates overlapping currency and network icons for the selected-method strip. */
    private fun tokenNetworkIcon(currency: String, network: String): View = FrameLayout(context).apply {
        addView(assetIcon(currency, 24), FrameLayout.LayoutParams(dp(24), dp(24), Gravity.START or Gravity.TOP))
        addView(FrameLayout(context).apply {
            setPadding(dp(1), dp(1), dp(1), dp(1))
            background = rounded(Color.WHITE, 9)
            addView(assetIcon(network, 14), FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER))
        }, FrameLayout.LayoutParams(dp(18), dp(18), Gravity.END or Gravity.BOTTOM))
    }

    /** Resolves a packaged asset icon while retaining a real brand mark for unknown assets. */
    private fun assetIcon(symbol: String, size: Int): ImageView = ImageView(context).apply {
        val normalized = when (symbol.lowercase()) {
            "eth" -> "ethereum"
            "trx" -> "tron"
            "bnb" -> "bsc"
            "gram" -> "ton"
            "pol", "matic" -> "polygon"
            else -> symbol.lowercase()
        }
        val resourceId = context.resources.getIdentifier("polypay_asset_$normalized", "drawable", context.packageName)
        setImageResource(if (resourceId != 0) resourceId else R.drawable.polypay_brand)
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = rounded(Color.WHITE, size / 2)
        clipToOutline = true
        contentDescription = symbol
        layoutParams = ViewGroup.LayoutParams(dp(size), dp(size))
    }

    /** Returns the readable asset name used on the Web mobile cards. */
    private fun currencyFullName(currency: String): String? = when (currency) {
        "USDT" -> "Tether USD"
        "USDC" -> "USD Coin"
        "ETH" -> "Ether"
        "TRX" -> "TRON"
        "BTC" -> "Bitcoin"
        "SOL" -> "Solana"
        "POL", "MATIC" -> "Polygon"
        "BNB" -> "BNB"
        "GRAM" -> "Gram"
        else -> null
    }

    /** Returns the canonical network name used on the Web checkout. */
    private fun networkName(network: String): String = when (network) {
        "Tron" -> "TRON"
        "BSC" -> "BNB Smart Chain"
        "Arbitrum" -> "Arbitrum One"
        else -> network
    }

    /** Returns the token standard, omitting it for a network's native currency. */
    private fun transferStandard(network: String, currency: String): String? {
        val native = when (network) {
            "Ethereum", "Base", "Arbitrum", "Optimism" -> "ETH"
            "Tron" -> "TRX"
            "BSC" -> "BNB"
            "Solana" -> "SOL"
            "Polygon" -> if (currency == "MATIC") "MATIC" else "POL"
            "TON" -> "GRAM"
            "BTC" -> "BTC"
            else -> null
        }
        if (currency == native) return null
        return when (network) {
            "Tron" -> "TRC20"
            "BSC" -> "BEP20"
            "Ethereum", "Base", "Arbitrum", "Optimism", "Polygon" -> "ERC20"
            "Solana" -> "SPL"
            "TON" -> "Jetton"
            else -> null
        }
    }

    /** Formats the call-to-action with the exact selected transfer method. */
    private fun paymentActionLabel(selection: PaymentSelection): String {
        val standard = transferStandard(selection.network, selection.currency)
        return context.getString(
            R.string.polypay_pay_with_method,
            selection.currency,
            networkName(selection.network),
            standard?.let { " ($it)" }.orEmpty(),
        )
    }

    /** Formats the fiat order amount with the Web checkout's two-decimal baseline. */
    private fun formatFiat(value: String): String = value.toBigDecimalOrNull()
        ?.setScale(2, RoundingMode.HALF_UP)
        ?.toPlainString()
        ?: value

    /** Copies a payment value into the system clipboard. */
    private fun copy(value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("PolyPay", value))
        Toast.makeText(context, R.string.polypay_copied, Toast.LENGTH_SHORT).show()
    }

    /** Creates the shared full-width mobile page container. */
    private fun page(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(8), dp(12), dp(22))
        setBackgroundColor(backgroundColor)
    }

    /** Creates a security-boundary note shown on payment screens. */
    private fun securityNote(): TextView = label(context.getString(R.string.polypay_security_note), 11).apply {
        gravity = Gravity.CENTER
        setTextColor(textFaint)
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
        includeFontPadding = false
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    /** Creates a rounded brand-colored primary action. */
    private fun primaryButton(text: String, action: () -> Unit): Button = Button(context).apply {
        this.text = text
        isAllCaps = false
        textSize = 15f
        setTextColor(0xFF11122E.toInt())
        setTypeface(typeface, Typeface.BOLD)
        backgroundTintList = null
        background = rounded(brand, 13)
        compoundDrawableTintList = ColorStateList.valueOf(0xFF11122E.toInt())
        setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.ic_media_next, 0)
        compoundDrawablePadding = dp(8)
        setOnClickListener { action() }
    }

    /** Creates a rounded neutral secondary action. */
    private fun secondaryButton(text: String, action: () -> Unit): Button = Button(context).apply {
        this.text = text
        isAllCaps = false
        textSize = 14f
        setTextColor(textPrimary)
        backgroundTintList = null
        background = rounded(panelRaised, 12, line)
        setOnClickListener { action() }
    }

    /** Creates a rounded fill, optional stroke, and deterministic corner radius. */
    private fun rounded(fill: Int, radius: Int, stroke: Int? = null, strokeWidth: Int = 1): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            stroke?.let { setStroke(dp(strokeWidth), it) }
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

    /** Creates match-width fixed-height layout params with an optional bottom margin. */
    private fun fixedHeight(height: Int, bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(height)).apply {
            bottomMargin = dp(bottom)
        }

    /** Converts density-independent pixels to physical pixels. */
    private fun dp(value: Int): Int = (value * density).toInt()

    /** Wraps a content view in a vertical scroll container with matching system-bar color. */
    fun scroll(content: View): View = ScrollView(context).apply {
        if (content is FrameLayout) return content
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        setBackgroundColor(backgroundColor)
        addView(content)
    }
}
