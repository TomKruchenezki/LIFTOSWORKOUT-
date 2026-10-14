package app.liftos.domain.export

import org.junit.Assert.assertEquals
import org.junit.Test

class ReportBlocksTest {
    @Test
    fun `headings lines and blank lines are told apart`() {
        val blocks = ReportBlocks.parse("דוח\n\n## סיכום\nשורה 1\n\nשורה 2")
        assertEquals(
            listOf(
                ReportBlock(BlockKind.LINE, "דוח"),
                ReportBlock(BlockKind.SPACER),
                ReportBlock(BlockKind.HEADING, "סיכום"),
                ReportBlock(BlockKind.LINE, "שורה 1"),
                ReportBlock(BlockKind.SPACER),
                ReportBlock(BlockKind.LINE, "שורה 2"),
            ),
            blocks,
        )
    }

    @Test
    fun `leading and trailing blank lines are dropped and an empty report has no blocks`() {
        assertEquals(listOf(ReportBlock(BlockKind.LINE, "x")), ReportBlocks.parse("\n\nx\n\n"))
        assertEquals(emptyList<ReportBlock>(), ReportBlocks.parse(""))
    }

    @Test
    fun `a real period report is parsed into headings and lines`() {
        val sample = "## בקשה\n" + app.liftos.domain.export.TextReport.AI_INSTRUCTION
        val blocks = ReportBlocks.parse(sample)
        assertEquals(BlockKind.HEADING, blocks[0].kind)
        assertEquals("בקשה", blocks[0].text)
        assertEquals(BlockKind.LINE, blocks[1].kind)
    }
}
