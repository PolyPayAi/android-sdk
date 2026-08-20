package ai.polypay.checkout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the server-authoritative public result contract. */
class PolyPayCheckoutResultTest {
    /** Ensures no public outcome can be interpreted as a direct paid decision. */
    @Test
    fun publicOutcomesNeverContainPaid() {
        assertFalse(PolyPayCheckoutResult.Outcome.entries.any { it.name == "PAID" })
        PolyPayCheckoutResult.Outcome.entries.forEach { outcome ->
            assertTrue(PolyPayCheckoutResult(outcome, "trade_12345678").requiresServerConfirmation)
        }
    }
}
