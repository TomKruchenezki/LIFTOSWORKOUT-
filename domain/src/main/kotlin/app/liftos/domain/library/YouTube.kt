package app.liftos.domain.library

import java.net.URI
import java.net.URLEncoder

/**
 * LiftOS never invents video links. If the user did not provide a direct link for an exercise,
 * we only offer a *search* on YouTube, and the UI labels it clearly as a search.
 */
object YouTube {
    fun searchUrl(exerciseNameEn: String): String {
        val query = URLEncoder.encode("$exerciseNameEn exercise proper form", Charsets.UTF_8)
        return "https://www.youtube.com/results?search_query=$query"
    }

    /** Accepts only well-formed http(s) links, so user input can't produce odd intents. */
    fun isValidWebUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val uri = URI(url.trim())
            (uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }
}
