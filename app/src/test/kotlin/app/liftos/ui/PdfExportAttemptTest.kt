package app.liftos.ui

import app.liftos.data.PdfExport
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Tries to really draw the PDF under Robolectric's native graphics. Earlier attempts in the legacy graphics mode
 * failed inside PdfDocument. If the framework cannot do it here, the test is SKIPPED (not passed) and the audit
 * keeps "PDF drawing" as BLOCKED - a skipped test never counts as evidence.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfExportAttemptTest {
    @Test
    fun a_long_hebrew_report_becomes_a_multi_page_pdf() {
        val text = buildString {
            append("## דוח אימון מתוך LiftOS\n")
            repeat(160) { append("סט $it: לחיצת חזה במוט 60 ק\"ג × 8 · RIR 2\n") }
        }
        val result = try {
            PdfExport.render("LiftOS – בדיקה", text)
        } catch (t: Throwable) {
            assumeNoException("PdfDocument is not usable in this Robolectric environment", t)
            return
        }
        assertTrue("starts with the PDF signature", String(result.bytes, 0, 5, Charsets.ISO_8859_1) == "%PDF-")
        assertTrue("more than one page for 160 lines (was ${result.pages})", result.pages >= 2)
        assertTrue("not empty (${result.bytes.size} bytes)", result.bytes.size > 2_000)
    }
}
