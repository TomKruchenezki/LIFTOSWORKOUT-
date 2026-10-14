package app.liftos.data

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PdfExportTest {
    @Test
    fun `a hebrew report becomes a non empty pdf with at least one page`() {
        val text = buildString {
            appendLine("דוח אימונים מתוך LiftOS – חודש אחרון")
            appendLine()
            appendLine("## סיכום התקופה")
            repeat(200) { appendLine("אימון $it: לחיצת חזה במוט 60×8, נפח 480 ק״ג-חזרות") }
        }
        val result = PdfExport.render("LiftOS – חודש אחרון", text)
        assertTrue(result.bytes.isNotEmpty())
        assertTrue(result.pages >= 1)
    }

    @Test
    fun `an empty report still produces a valid single page document`() {
        val result = PdfExport.render("LiftOS", "")
        assertTrue(result.bytes.isNotEmpty())
        assertTrue(result.pages == 1)
    }
}
