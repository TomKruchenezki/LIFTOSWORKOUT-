package app.liftos.ui

import app.liftos.ui.settings.isDeleteConfirmed
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The confirmation of "delete all my data" accepts exactly the word "מחק". */
class DeleteConfirmationTest {
    @Test
    fun only_the_exact_word_confirms() {
        assertTrue(isDeleteConfirmed("מחק"))
        assertTrue(isDeleteConfirmed("  מחק "))
        assertFalse(isDeleteConfirmed(""))
        assertFalse(isDeleteConfirmed("מחק הכול"))
        assertFalse(isDeleteConfirmed("מח"))
        assertFalse(isDeleteConfirmed("delete"))
    }
}
