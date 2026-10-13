package app.liftos.ui

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot

/**
 * Screen size of a modern phone (the target device is a Galaxy S26 Ultra), so the tests do not
 * depend on Robolectric's tiny default screen where buttons fall below the fold.
 */
const val PHONE_QUALIFIERS = "w411dp-h891dp-xxhdpi"

/** A readable dump of the visible texts / content descriptions, printed when a wait times out. */
fun ComposeContentTestRule.screenDump(maxLines: Int = 70): String {
    val lines = mutableListOf<String>()
    fun walk(node: SemanticsNode, depth: Int) {
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" | ") { it.text }
        val editable = node.config.getOrNull(SemanticsProperties.EditableText)?.text
        val desc = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" | ")
        val parts = listOfNotNull(
            text?.takeIf { it.isNotBlank() }?.let { "text=[$it]" },
            editable?.let { "editable=[$it]" },
            desc?.takeIf { it.isNotBlank() }?.let { "desc=[$it]" },
        )
        if (parts.isNotEmpty() && lines.size < maxLines) lines += "  ".repeat(minOf(depth, 6)) + parts.joinToString(" ")
        node.children.forEach { walk(it, depth + 1) }
    }
    return try {
        walk(onRoot(useUnmergedTree = false).fetchSemanticsNode(), 0)
        lines.joinToString("\n").ifBlank { "(no text on screen)" }
    } catch (e: Throwable) {
        "(could not dump the screen: ${e.message})"
    }
}

fun ComposeContentTestRule.waitUntilOrDump(what: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
    try {
        waitUntil(timeoutMs, condition)
    } catch (e: ComposeTimeoutException) {
        throw AssertionError("Timed out waiting for: $what\n--- screen ---\n${screenDump()}", e)
    }
}

fun ComposeContentTestRule.waitForText(text: String, substring: Boolean = false, timeoutMs: Long = 10_000) {
    waitUntilOrDump("text \"$text\"", timeoutMs) {
        onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }
}

fun ComposeContentTestRule.containsText(text: String, substring: Boolean = false): Boolean =
    onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
