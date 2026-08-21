package ai.polypay.checkout.internal

import ai.polypay.checkout.model.CheckoutOrder
import ai.polypay.checkout.model.CheckoutStatus
import ai.polypay.checkout.model.NetworkFeeQuote
import ai.polypay.checkout.model.PaymentMethodGroup
import ai.polypay.checkout.model.WalletPaymentRequest
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Minimal HTTPS client for PolyPay's public, checkout-scoped API. */
internal class PolyPayApi(apiBaseUrl: String) {
    private val baseUrl = apiBaseUrl.trimEnd('/')

    init {
        val uri = URI(baseUrl)
        require(uri.scheme == "https" && uri.userInfo == null && !uri.host.isNullOrBlank()) {
            "apiBaseUrl must be a credential-free HTTPS URL"
        }
    }

    /** Loads the current checkout order without merchant credentials. */
    fun getCheckout(tradeId: String): CheckoutOrder {
        val json = request("GET", "/public/checkout-counter?trade_id=${encode(tradeId)}")
        return parseOrder(json)
    }

    /** Loads merchant-enabled currencies and networks for this checkout. */
    fun getPaymentMethods(tradeId: String): List<PaymentMethodGroup> {
        val data = request("GET", "/public/order/payment-methods?trade_id=${encode(tradeId)}")
        val methods = data.optJSONArray("methods") ?: JSONArray()
        return (0 until methods.length()).map { index -> parsePaymentMethod(methods.getJSONObject(index)) }
    }

    /** Replaces the checkout placeholder with a payable order for the selected method. */
    fun selectPaymentMethod(tradeId: String, network: String, currency: String): CheckoutOrder {
        request(
            "POST",
            "/public/order/select-payment-method",
            JSONObject().put("trade_id", tradeId).put("network", network).put("currency", currency),
        )
        return getCheckout(tradeId)
    }

    /** Polls the public payment observation state for UX updates only. */
    fun checkStatus(tradeId: String): CheckoutStatus {
        val data = request("POST", "/public/check-status", JSONObject().put("trade_id", tradeId))
        return CheckoutStatus(
            status = data.getInt("status"),
            confirmations = data.optInt("confirmations"),
            requiredConfirmations = data.optInt("required_confirmations"),
        )
    }

    /** Loads server-locked wallet transaction parameters for an eligible order. */
    fun prepareWalletPayment(tradeId: String): WalletPaymentRequest {
        val data = request(
            "POST",
            "/public/order/wallet-payment/prepare",
            JSONObject().put("trade_id", tradeId),
        )
        return WalletPaymentRequest(
            network = data.getString("network"),
            chainId = data.getString("chain_id"),
            assetType = data.getString("asset_type"),
            tokenContract = data.optString("token_contract"),
            currency = data.getString("currency"),
            recipient = data.getString("recipient"),
            displayAmount = data.getString("display_amount"),
            amountBaseUnits = data.getString("amount_base_units"),
        )
    }

    /** Executes one bounded HTTPS request and unwraps PolyPay's response envelope. */
    private fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        val connection = URL(baseUrl + path).openConnection() as? HttpsURLConnection
            ?: throw PolyPayApiException("transport", "Only HTTPS connections are allowed")
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Content-Type", "application/json")
        if (body != null) {
            connection.doOutput = true
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
        }

        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.let { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                    val value = reader.readText()
                    if (value.length > MAX_RESPONSE_CHARS) {
                        throw PolyPayApiException("response_too_large", "API response is too large")
                    }
                    value
                }
            }.orEmpty()
            val envelope = runCatching { JSONObject(raw) }.getOrElse {
                throw PolyPayApiException("invalid_response", "API returned invalid JSON")
            }
            val apiCode = envelope.optInt("code", if (status in 200..299) 0 else status)
            if (status !in 200..299 || apiCode != 0) {
                throw PolyPayApiException(
                    code = "api_${if (apiCode == 0) status else apiCode}",
                    message = envelope.optString("message").ifBlank { "Checkout request failed" },
                )
            }
            return envelope.optJSONObject("data")
                ?: throw PolyPayApiException("invalid_response", "API response data is missing")
        } finally {
            connection.disconnect()
        }
    }

    /** Maps a checkout order from the public API. */
    private fun parseOrder(json: JSONObject): CheckoutOrder = CheckoutOrder(
        tradeId = json.getString("trade_id"),
        merchantOrderId = json.optionalString("mch_order_id"),
        currency = json.optString("currency"),
        network = json.optString("network"),
        amount = json.decimalString("amount"),
        actualAmount = json.optionalString("display_amount") ?: json.decimalString("actual_amount"),
        address = json.optString("address"),
        status = json.getInt("status"),
        expirationTime = json.optLong("expiration_time"),
        merchantName = json.optionalString("merchant_name"),
    )

    /** Maps one network group and its fee quotes. */
    private fun parsePaymentMethod(json: JSONObject): PaymentMethodGroup {
        val currenciesJson = json.optJSONArray("currencies") ?: JSONArray()
        val currencies = (0 until currenciesJson.length()).map(currenciesJson::getString)
        val feeQuotesJson = json.optJSONObject("fee_quotes")
        val quotes = currencies.mapNotNull { currency ->
            val quote = feeQuotesJson?.optJSONObject(currency) ?: return@mapNotNull null
            currency to NetworkFeeQuote(
                standardFee = quote.optionalString("standard_fee"),
                feeCurrency = quote.optionalString("fee_currency"),
                status = quote.optString("status", "unavailable"),
            )
        }.toMap()
        return PaymentMethodGroup(
            network = json.getString("network"),
            currencies = currencies,
            defaultCurrency = json.optionalString("default_currency"),
            feeQuotes = quotes,
        )
    }

    /** URL-encodes an opaque trade identifier without changing its content. */
    private fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    /** Returns a nullable string while treating JSON null and blank as absent. */
    private fun JSONObject.optionalString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

    /** Preserves API decimal text when possible and avoids locale-dependent formatting. */
    private fun JSONObject.decimalString(key: String): String = get(key).toString()

    private companion object {
        const val MAX_RESPONSE_CHARS = 1_000_000
    }
}

/** Stable API failure surfaced to the checkout UI. */
internal class PolyPayApiException(val code: String, message: String) : Exception(message)
