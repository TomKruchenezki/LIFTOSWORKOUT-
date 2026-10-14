package app.liftos.data

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import app.liftos.domain.export.BlockKind
import app.liftos.domain.export.ReportBlocks
import java.io.ByteArrayOutputStream

/**
 * Renders a plain-text report as an A4 PDF using only the platform's own classes (no library, no network).
 * Lines starting with "## " become headings. Hebrew lines are laid out right-to-left by the text engine.
 */
object PdfExport {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40

    class Result(val bytes: ByteArray, val pages: Int)

    fun render(title: String, text: String): Result {
        val doc = PdfDocument()
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.BLACK }
        val heading = TextPaint(body).apply { textSize = 14f; isFakeBoldText = true }
        val titlePaint = TextPaint(body).apply { textSize = 18f; isFakeBoldText = true }
        val footer = TextPaint(body).apply { textSize = 9f; color = Color.DKGRAY }
        val width = PAGE_W - 2 * MARGIN

        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
        var y = MARGIN.toFloat()

        fun finishPage() {
            page.canvas.drawText("LiftOS · $pageNo", (PAGE_W / 2f) - 20f, PAGE_H - 18f, footer)
            doc.finishPage(page)
        }

        fun draw(line: String, paint: TextPaint, before: Float, after: Float) {
            val layout = StaticLayout.Builder.obtain(line, 0, line.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
                .setLineSpacing(2f, 1f)
                .build()
            y += before
            if (y + layout.height > PAGE_H - MARGIN - 12 && y > MARGIN + 1) {
                finishPage()
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
                y = MARGIN.toFloat()
            }
            val canvas = page.canvas
            canvas.save()
            canvas.translate(MARGIN.toFloat(), y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + after
        }

        draw(title, titlePaint, 0f, 10f)
        for (block in ReportBlocks.parse(text)) {
            when (block.kind) {
                BlockKind.HEADING -> draw(block.text, heading, 10f, 4f)
                BlockKind.SPACER -> y += 6f
                BlockKind.LINE -> draw(block.text, body, 0f, 1f)
            }
        }
        finishPage()
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return Result(out.toByteArray(), pageNo)
    }
}
