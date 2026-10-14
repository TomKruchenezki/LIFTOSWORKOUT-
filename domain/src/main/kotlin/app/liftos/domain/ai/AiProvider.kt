package app.liftos.domain.ai

/**
 * Extension point for a future DIRECT connection to an AI service (OpenAI, Anthropic, ...).
 *
 * Nothing implements it today and none is wired in: the app has no INTERNET permission, never sends
 * workout data anywhere by itself, and the "analyze with AI" screen only prepares a report that the
 * user copies or shares. If a provider is ever added, these rules apply (see ARCHITECTURE.md):
 *  - an explicit, per-provider [AiConsent] saved by the user, which can be revoked at any time;
 *  - the user's own API key, kept in the Android Keystore (never in the APK, backups or exports);
 *  - the exact text to be sent is shown before sending, and the response is shown as "AI output, may be wrong".
 */
interface AiProvider {
    val id: String
    val displayName: String

    suspend fun analyze(request: AiRequest): AiResponse
}

/** What the user agreed to send to one provider. */
data class AiConsent(
    val providerId: String,
    val grantedAtEpochMs: Long,
    val includesBodyData: Boolean,
    val includesNotes: Boolean,
)

data class AiRequest(
    val instruction: String,
    val report: String,
    val consent: AiConsent,
) {
    init {
        require(consent.providerId.isNotBlank()) { "consent must name the provider" }
        require(consent.grantedAtEpochMs > 0) { "consent must have been given" }
    }
}

sealed interface AiResponse {
    data class Text(val text: String) : AiResponse
    data class Failure(val message: String) : AiResponse
}

/** The providers that are actually connected. Empty on purpose: see [AiProvider]. */
object AiProviders {
    val connected: List<AiProvider> = emptyList()

    val isDirectAnalysisAvailable: Boolean get() = connected.isNotEmpty()
}
