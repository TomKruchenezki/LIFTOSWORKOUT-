package app.liftos.domain.export

import app.liftos.domain.analytics.DateRange
import app.liftos.domain.analytics.Fx
import app.liftos.domain.analytics.PersonalRecordEngine
import app.liftos.domain.analytics.SessionFact
import app.liftos.domain.analytics.SetFact
import app.liftos.domain.model.BodyMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PeriodReportTest {
    private val today = LocalDate.parse("2026-10-14")

    private fun input(
        facts: List<SetFact>,
        sessions: List<SessionFact>,
        range: DateRange = DateRange.lastDays(30, today),
        body: List<BodyMeasurement> = emptyList(),
    ) = ReportInput(
        range = range, scopeLabel = "חודש אחרון", today = today, facts = facts, sessions = sessions,
        records = PersonalRecordEngine.compute(facts), measurements = body, targetPerWeek = 3,
    )

    private val facts = Fx.sets("a", "2026-09-20", listOf(60.0, 60.0), listOf(10, 8)) +
        Fx.sets("b", "2026-10-05", listOf(62.5, 62.5), listOf(8, 8)) +
        Fx.sets("c", "2026-10-12", listOf(65.0), listOf(6))
    private val sessions = listOf("a" to "2026-09-20", "b" to "2026-10-05", "c" to "2026-10-12")
        .map { (id, d) -> Fx.session(id, d, energy = 4) }

    @Test
    fun `the report ends with the exact analysis request`() {
        val text = PeriodReport.build(input(facts, sessions), ReportOptions())
        assertTrue(text.trimEnd().endsWith(TextReport.AI_INSTRUCTION))
        assertTrue(text.contains("## סיכום התקופה"))
        assertTrue(text.contains("## האימונים"))
        assertTrue(text.contains("## שיאים אישיים חדשים בתקופה"))
    }

    @Test
    fun `summary numbers come from the data`() {
        val text = PeriodReport.build(input(facts, sessions), ReportOptions())
        assertTrue(text.contains("אימונים: 3"))
        assertTrue(text.contains("סטי עבודה: 5"))
        assertTrue(text.contains("נפח כולל: 2,470 ק״ג-חזרות")) // 60*18 + 62.5*16 + 65*6 = 1080+1000+390
        assertTrue(text.contains("62.5×8"))
    }

    @Test
    fun `an empty period says there is nothing to analyse`() {
        val text = PeriodReport.build(input(facts, sessions, DateRange(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"))), ReportOptions())
        assertTrue(text.contains("לא תועדו אימונים בטווח הזה"))
        assertTrue(text.trimEnd().endsWith(TextReport.AI_INSTRUCTION))
    }

    @Test
    fun `body data and notes only appear when chosen`() {
        val body = listOf(BodyMeasurement("m", Fx.startOf("2026-10-10"), "2026-10-10", "UTC", weightKg = 81.5))
        val noteSessions = sessions.map { it.copy(notes = "הרגשתי עייף במיוחד") }
        val without = PeriodReport.build(input(facts, noteSessions, body = body), ReportOptions(includeBody = false, includeNotes = false))
        assertFalse(without.contains("מדדי גוף"))
        assertFalse(without.contains("81.5"))
        assertFalse(without.contains("הרגשתי עייף במיוחד"))
        val with = PeriodReport.build(input(facts, noteSessions, body = body), ReportOptions(includeBody = true, includeNotes = true))
        assertTrue(with.contains("## מדדי גוף"))
        assertTrue(with.contains("81.5"))
        assertTrue(with.contains("הרגשתי עייף במיוחד"))
    }

    @Test
    fun `long histories list only the latest workouts in full`() {
        val many = (1..20).map { i -> "w$i" to LocalDate.parse("2026-09-01").plusDays(i.toLong()).toString() }
        val f = many.flatMap { (id, d) -> Fx.sets(id, d, listOf(50.0), listOf(10)) }
        val s = many.map { (id, d) -> Fx.session(id, d) }
        val text = PeriodReport.build(input(f, s, DateRange(LocalDate.parse("2026-09-01"), today)), ReportOptions())
        assertTrue(text.contains("מוצגת שורת סיכום בלבד"))
        assertEquals(PeriodReport.RECENT_DETAIL, text.lines().count { it.startsWith("### ") })
    }

    @Test
    fun `scopes resolve to date ranges`() {
        assertEquals(DateRange.weekOf(today), ReportScope.THIS_WEEK.range(today, null, null))
        assertEquals(84, ReportScope.WEEKS_12.range(today, null, null).days)
        assertEquals(LocalDate.parse("2026-01-05"), ReportScope.ALL.range(today, LocalDate.parse("2026-01-05"), null).start)
        val custom = DateRange(LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-10"))
        assertEquals(custom, ReportScope.CUSTOM.range(today, null, custom))
    }
}
