package app.liftos.domain.export

enum class BlockKind { HEADING, LINE, SPACER }

data class ReportBlock(val kind: BlockKind, val text: String = "")

/**
 * Splits a plain-text report into the pieces a document renderer (the PDF writer) draws: "## " headings,
 * ordinary lines and blank lines. Kept in the domain module so the logic is unit-tested on the JVM.
 */
object ReportBlocks {
    fun parse(text: String): List<ReportBlock> = text.lines().map { line ->
        when {
            line.startsWith("## ") -> ReportBlock(BlockKind.HEADING, line.removePrefix("## ").trim())
            line.isBlank() -> ReportBlock(BlockKind.SPACER)
            else -> ReportBlock(BlockKind.LINE, line)
        }
    }.dropWhile { it.kind == BlockKind.SPACER }.dropLastWhile { it.kind == BlockKind.SPACER }
}
