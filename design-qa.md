# Android Checkout Design QA

## Validation context

- Reference: `/tmp/ai-chat-custom-attachment-temp-file-5f567782-41da-4985-8804-7f535eb9c1c1-7160473628677778977.png` (600 × 1039)
- Implementation: `/tmp/polypay-android-redesign-sticky-final.png` (1080 × 2340)
- Side-by-side comparison: `/tmp/polypay-design-comparison.png`
- Device: realme RMX1901, Android 11, 1080 × 2340
- State: pending checkout, USDT and Ethereum (ERC20) selected, dark theme
- Locale: device uses Simplified Chinese; the English reference copy therefore differs intentionally

## Comparison history

1. Initial implementation had clipped square asset icons, excessive vertical density, and no persistent primary action.
2. Asset sizing, card proportions, typography, selection states, and section spacing were aligned with the mobile web reference.
3. The primary action was moved into a fixed bottom safe-area container; scroll content received matching bottom clearance.
4. Merchant avatar loading was verified. The connected device resolves `cloud-oss.nextcli.com` to an unreachable address, so the intentional PolyPay placeholder remains visible on this device.
5. Final full-view comparison and focused scroll-state inspection found no P0, P1, or P2 visual defects.

## Interaction checks

- Switching USDT to USDC updates available networks to Ethereum and Base.
- Switching back to USDT restores Ethereum, Base, and TRON.
- Scrolling exposes all network choices while the primary action remains visible.
- No payment action was triggered during validation.

## Result

passed
