# PolyPay Android SDK

Native Android checkout for PolyPay. The SDK renders both payment-method selection and the payment page using Android views; it does not embed the Hosted Checkout website in a WebView.

## Requirements

- Android API 24+
- Kotlin 1.9+
- A server endpoint that creates a PolyPay checkout with `POST /api/v1/pay/order/checkout`

The merchant API Key stays on your server. Create checkout without `currency` and `network` so the returned URL points to `/pay/{tradeId}` and the SDK can display its native method selector.

## Install

GitHub Packages:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/PolyPayAi/android-sdk")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull
            password = providers.gradleProperty("gpr.key").orNull
        }
    }
}

dependencies {
    implementation("ai.polypay:checkout:0.1.0")
}
```

## Present checkout

```kotlin
private val polyPayCheckout = registerForActivityResult(PolyPayCheckoutContract()) { result ->
    // Never fulfill from this result. Ask your server to reconcile result.tradeId.
    viewModel.reconcile(result.tradeId)
}

fun pay() = lifecycleScope.launch {
    val checkoutUrl = merchantApi.createCheckout(orderId)
    polyPayCheckout.launch(
        PolyPayCheckoutOptions(checkoutUrl = checkoutUrl)
    )
}
```

For an approved white-label checkout domain, add its exact lowercase hostname to `allowedCheckoutHosts`. The SDK accepts HTTPS `/pay/{tradeId}` URLs only.

## Native flow

1. Loads the checkout placeholder from PolyPay's public checkout API.
2. Shows merchant-enabled currencies, networks, and server-provided fee estimates.
3. Converts the placeholder to a payable order after the user confirms a method.
4. Shows exact amount, address, scannable QR code, copy actions, network, and observed status.
5. Returns only `CLOSED`, `PAYMENT_DETECTED`, `EXPIRED`, `CANCELLED`, or `ERROR`. There is deliberately no client-side `PAID` outcome.

Final fulfillment must use a verified webhook or an authenticated server-side order query.

## Build

```bash
./gradlew testDebugUnitTest assembleRelease lint
```
