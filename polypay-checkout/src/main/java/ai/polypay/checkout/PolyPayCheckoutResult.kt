package ai.polypay.checkout

/** Non-authoritative client outcome returned when the native checkout closes. */
data class PolyPayCheckoutResult(
    val outcome: Outcome,
    val tradeId: String?,
    val requiresServerConfirmation: Boolean = true,
    val errorCode: String? = null,
) {
    /** Outcomes intentionally exclude `paid`; merchant fulfillment stays server-authoritative. */
    enum class Outcome {
        CLOSED,
        PAYMENT_DETECTED,
        EXPIRED,
        CANCELLED,
        ERROR,
    }
}
