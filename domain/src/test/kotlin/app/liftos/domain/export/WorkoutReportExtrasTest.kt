package app.liftos.domain.export

import app.liftos.domain.TestData
import app.liftos.domain.analytics.Fx
import app.liftos.domain.analytics.PersonalRecordEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The single-workout AI report also carries the records the workout set, the trends and the recovery ratings. */
class WorkoutReportExtrasTest {
    // Bench press (library id "barbell-bench-press") on three Sundays, getting heavier.
    private fun history() = listOf(
        Triple("a", "2026-09-20", 60.0),
        Triple("b", "2026-09-27", 62.5),
        Triple("c", "2026-10-04", 65.0),
    )

    private val facts = history().flatMap { (id, date, w) ->
        Fx.sets(id, date, List(3) { w }, listOf(8, 8, 8), exercise = "barbell-bench-press", name = "לחיצת חזה")
    }
    private val sessions = history().map { (id, date, _) -> Fx.session(id, date) }
    private val records = PersonalRecordEngine.compute(facts)

    private fun detailOf(id: String, date: String, weight: Double) =
        TestData.benchDetail(id, date, List(3) { weight }, listOf(8, 8, 8))

    @Test
    fun `records set in this workout are listed with their previous value`() {
        val extras = WorkoutReportContext.build(detailOf("c", "2026-10-04", 65.0), facts, sessions, records)
        assertTrue("the third workout broke records", extras.newRecords.isNotEmpty())
        val report = TextReport.aiReport(detailOf("c", "2026-10-04", 65.0), null, null, extras)
        assertTrue(report.contains("## שיאים אישיים שנקבעו באימון הזה"))
        assertTrue(report.contains("המשקל הגבוה ביותר 65 ק״ג (קודם 62.5)"))
        assertTrue("estimates are labelled", report.contains("(הערכה)"))
    }

    @Test
    fun `a workout without records says so`() {
        val extras = WorkoutReportContext.build(detailOf("a", "2026-09-20", 60.0), facts, sessions, records)
        // The very first workout only creates a baseline.
        assertTrue(extras.newRecords.isEmpty())
        val report = TextReport.aiReport(detailOf("a", "2026-09-20", 60.0), null, null, extras)
        assertTrue(report.contains("לא נקבעו שיאים חדשים באימון הזה."))
    }

    @Test
    fun `trend lines use only data up to the workout date and say when there is not enough`() {
        val first = WorkoutReportContext.build(detailOf("a", "2026-09-20", 60.0), facts, sessions, records)
        assertTrue(first.trendLines.single().contains(WorkoutReportContext.NOT_ENOUGH_FOR_TREND))

        val third = WorkoutReportContext.build(detailOf("c", "2026-10-04", 65.0), facts, sessions, records)
        val line = third.trendLines.single()
        assertTrue(line, line.contains("ב-90 הימים עד האימון הזה (3 אימונים)"))
        assertTrue(line, line.contains("←"))
        assertEquals(3, third.workoutsInLast28Days)
    }

    @Test
    fun `a future workout never leaks into an earlier report`() {
        val second = WorkoutReportContext.build(detailOf("b", "2026-09-27", 62.5), facts, sessions, records)
        assertTrue(second.trendLines.single().contains("(2 אימונים)"))
        assertEquals(2, second.workoutsInLast28Days)
    }

    @Test
    fun `recovery ratings appear when they were entered`() {
        val d = detailOf("c", "2026-10-04", 65.0)
        val rated = d.copy(session = d.session.copy(difficultyRating = 4, qualityRating = 5, sorenessRating = 2, fatigueRating = 3, sleepHours = 7.5))
        val report = TextReport.aiReport(rated, null, null)
        assertTrue(report.contains("תחושת קושי: 4/5"))
        assertTrue(report.contains("איכות האימון: 5/5"))
        assertTrue(report.contains("כאבי שרירים: 2/5"))
        assertTrue(report.contains("עייפות כללית: 3/5"))
        assertTrue(report.contains("שעות שינה: 7.5"))
        assertFalse(TextReport.aiReport(d, null, null).contains("תחושת קושי"))
    }

    @Test
    fun `the exact request still ends the report`() {
        val extras = WorkoutReportContext.build(detailOf("c", "2026-10-04", 65.0), facts, sessions, records)
        assertTrue(TextReport.aiReport(detailOf("c", "2026-10-04", 65.0), null, null, extras).trimEnd().endsWith(TextReport.AI_INSTRUCTION))
    }
}
