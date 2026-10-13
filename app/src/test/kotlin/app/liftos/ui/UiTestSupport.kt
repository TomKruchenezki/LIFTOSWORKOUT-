package app.liftos.ui

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo

/**
 * Screen size of a modern phone (the target device is a Galaxy S26 Ultra), so the tests do not
 * depend on Robolectric's tiny default screen where buttons fall below the fold.
 */
const val PHONE_QUALIFIERS = "w411dp-h891dp-xxhdpi"

/** A readable one-line dump of the texts / content descriptions on screen, used in failure messages. */
fun ComposeContentTestRule.screenDump(maxItems: Int = 60): String = try {
    val nodes: List<SemanticsNode> = onAllNodes(SemanticsMatcher("any node") { true })
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
    val items = nodes.mapNotNull { node ->
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("|") { it.text }
        val editable = node.config.getOrNull(SemanticsProperties.EditableText)?.text
        val desc = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("|")
        val parts = listOfNotNull(
            text?.takeIf { it.isNotBlank() }?.let { "text=[$it]" },
            editable?.let { "editable=[$it]" },
            desc?.takeIf { it.isNotBlank() }?.let { "desc=[$it]" },
        )
        parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }.distinct().take(maxItems)
    "${nodes.size} nodes; " + items.joinToString(" ⏎ ").ifBlank { "(no text on screen)" }
} catch (e: Throwable) {
    "(could not dump the screen: ${e.message?.lineSequence()?.firstOrNull()})"
}

fun ComposeContentTestRule.waitUntilOrDump(what: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
    try {
        waitUntil(timeoutMs, condition)
    } catch (e: ComposeTimeoutException) {
        throw AssertionError("Timed out waiting for: $what | SCREEN: ${screenDump()}", e)
    }
}

fun ComposeContentTestRule.waitForText(text: String, substring: Boolean = false, timeoutMs: Long = 10_000) {
    waitUntilOrDump("text \"$text\"", timeoutMs) {
        onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }
}

fun ComposeContentTestRule.containsText(text: String, substring: Boolean = false): Boolean =
    onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

/** Clicks a node; on failure reports why (first lines of the reason) and what was on screen. */
fun ComposeContentTestRule.tap(what: String, scrollFirst: Boolean = false, node: () -> SemanticsNodeInteraction) {
    try {
        val n = node()
        if (scrollFirst) n.performScrollTo()
        n.performClick()
    } catch (e: AssertionError) {
        val reason = e.message?.lineSequence()?.take(4)?.joinToString(" / ").orEmpty()
        throw AssertionError("Could not tap $what: $reason | SCREEN: ${screenDump()}", e)
    }
}
