package app.liftos.domain.analytics

import app.liftos.domain.calendar.CalendarBuilder
import app.liftos.domain.calendar.ScheduledWorkout
import app.liftos.domain.export.PeriodReport
import app.liftos.domain.export.ReportInput
import app.liftos.domain.export.ReportOptions
import app.liftos.domain.insights.InsightEngine
import app.liftos.domain.model.MuscleGroup
import app.liftos.domain.progression.ProgressionEngine
import app.liftos.domain.recovery.RecoveryAnalytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * About five years of training (about 750 workouts and 17,000 sets). The calculations are pure and recomputed
 * from the facts on every screen, so they must stay fast with a long history. The limits are generous (a slow CI
 * machine must not fail them); they exist to catch an accidental quadratic loop.
 */
class LargeHistoryTest {
    private val today = LocalDate.parse("2026-10-14")
    private val exercises = listOf(
        Triple("bench", "לחיצת חזה", MuscleGroup.CHEST),
        Triple("row", "חתירה", MuscleGroup.BACK),
        Triple("squat", "סקוואט", MuscleGroup.QUADS),
        Triple("press", "לחיצת כתפיים", MuscleGroup.SHOULDERS),
        Triple("curl", "כפיפת מרפק", MuscleGroup.BICEPS),
    )

    private fun build(): Pair<List<SetFact>, List<SessionFact>> {
        val facts = mutableListOf<SetFact>()
        val sessions = mutableListOf<SessionFact>()
        var day = today.minusDays(5L * 365)
        var n = 0
        while (day.isBefore(today)) {
            // Three workouts a week: Sunday, Tuesday, Thursday.
            if (day.dayOfWeek.value in setOf(7, 2, 4)) {
                val id = "s${++n}"
                val date = day.toString()
                sessions += Fx.session(id, date, minutes = 55, title = "Upper")
                exercises.forEachIndexed { i, (key, name, muscle) ->
                    val base = 40.0 + i * 10 + n * 0.02
                    facts += Fx.sets(id, date, List(4) { base }, List(4) { 8 + (it % 3) }, exercise = key, name = name, muscle = muscle)
                }
            }
            day = day.plusDays(1)
        }
        return facts to sessions
    }

    private inline fun <T> timed(limitMs: Long, what: String, block: () -> T): T {
        val start = System.nanoTime()
        val result = block()
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("$what took $ms ms (limit $limitMs ms)", ms <= limitMs)
        return result
    }

    @Test
    fun `history of five years is analysed quickly`() {
        val (facts, sessions) = build()
        assertTrue("expected about 750 workouts, got ${sessions.size}", sessions.size > 700)
        assertTrue("expected well over 10,000 sets, got ${facts.size}", facts.size > 10_000)

        val records = timed(6_000, "personal records") { PersonalRecordEngine.compute(facts) }
        assertTrue(records.events.isNotEmpty())

        val cards = timed(3_000, "dashboard cards") { DashboardAnalytics.cards(facts, sessions, records, today, targetPerWeek = 3) }
        assertTrue(cards.hasAnyWorkout)

        timed(3_000, "muscle loads over the whole history") {
            VolumeAnalytics.muscleLoads(facts, DateRange(today.minusDays(5L * 365), today))
        }
        timed(3_000, "weekly buckets over the whole history") {
            VolumeAnalytics.buckets(facts, sessions, DateRange(today.minusDays(5L * 365), today), Granularity.WEEK)
        }
        val suggestion = timed(3_000, "progression suggestion") {
            ProgressionEngine.suggest("bench", "לחיצת חזה", facts, sessions, today, null, null)
        }
        assertTrue(suggestion.headline.isNotBlank())
        timed(3_000, "recovery report") { RecoveryAnalytics.report(facts, sessions, today) }
        timed(3_000, "insights") { InsightEngine.generate(facts, sessions, records, today, 3) }

        val report = timed(6_000, "a twelve-week report") {
            PeriodReport.build(
                ReportInput(
                    range = DateRange.lastDays(84, today), scopeLabel = "12 שבועות", today = today, facts = facts,
                    sessions = sessions, records = records, targetPerWeek = 3,
                ),
                ReportOptions(),
            )
        }
        assertTrue("a long period must not produce an enormous report (${report.length} chars)", report.length < 60_000)
    }

    @Test
    fun `calendar of a month is built quickly from a long history`() {
        val (_, sessions) = build()
        val schedule = listOf(ScheduledWorkout("w1", "Upper", 7), ScheduledWorkout("w2", "Lower", 2), ScheduledWorkout("w3", "Upper B", 4))
        val range = DateRange(today.minusDays(20), today.plusDays(21))
        val items = timed(2_000, "calendar month") {
            CalendarBuilder.build(range, today, schedule, today.minusDays(5L * 365), sessions, emptyList(), emptyList())
        }
        assertTrue(items.isNotEmpty())
        assertEquals(items.size, items.map { it.key }.toSet().size)
    }
}
