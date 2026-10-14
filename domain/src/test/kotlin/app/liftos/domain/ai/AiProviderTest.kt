package app.liftos.domain.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class AiProviderTest {
    @Test
    fun `no provider is connected, so the app never claims to send data`() {
        assertFalse(AiProviders.isDirectAnalysisAvailable)
    }

    @Test
    fun `a request cannot be built without a recorded consent`() {
        assertThrows(IllegalArgumentException::class.java) {
            AiRequest("x", "y", AiConsent(providerId = "", grantedAtEpochMs = 1L, includesBodyData = false, includesNotes = false))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiRequest("x", "y", AiConsent(providerId = "openai", grantedAtEpochMs = 0L, includesBodyData = false, includesNotes = false))
        }
        AiRequest("x", "y", AiConsent(providerId = "openai", grantedAtEpochMs = 1L, includesBodyData = false, includesNotes = false))
    }
}
