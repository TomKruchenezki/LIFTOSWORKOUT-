package app.liftos.domain.insights

import app.liftos.domain.analytics.Fx
import app.liftos.domain.analytics.PersonalRecordEngine
import app.liftos.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InsightEngineTest {
    private val today = LocalDate.parse("2026-10-14")

    private fun run(facts: List<app.liftos.domain.analytics.SetFact>, sessions: List<app.liftos.domain.analytics.SessionFact>, target: Int = 4) =
        InsightEngine.generate(facts, sessions, PersonalRecordEngine.compute(facts), today, target)

    @Test
    fun `with too little history it says so and invents no trend`() {
        val facts = Fx.sets("s1", "2026-10-12", listOf(60.0), listOf(10))
        val out = run(facts, listOf(Fx.session("s1", "2026-10-12")))
        assertTrue(out.any { it.text == InsightEngine.NOT_ENOUGH })
        assertFalse(out.any { it.id.startsWith("up-") || it.id.startsWith("vol-") })
        assertTrue(out.any { it.text.startsWith("ביצעת 1 אימון השבוע") })
    }

    @Test
    fun `a heavier weight and a record are celebrated with real numbers`() {
        val facts = Fx.sets("a", "2026-10-01", listOf(60.0, 60.0), listOf(8, 8)) +
            Fx.sets("b", "2026-10-06", listOf(60.0, 60.0), listOf(8, 8)) +
            Fx.sets("c", "2026-10-12", listOf(62.5, 62.5), listOf(8, 8))
        val sessions = listOf("a" to "2026-10-01", "b" to "2026-10-06", "c" to "2026-10-12").map { (id, d) -> Fx.session(id, d) }
        val out = run(facts, sessions)
        val up = out.first { it.id == "up-bench" }
        assertEquals("העלית את משקל העבודה בלחיצת חזה ב-2.5 ק״ג.", up.text)
        assertTrue(out.any { it.id == "pr-bench" && it.text.contains("שיא אישי חדש") })
        assertTrue(out.any { it.id.startsWith("vol-") && it.text.contains("גדל") })
    }

    @Test
    fun `a muscle group that was not trained for a week is called out`() {
        val legs = Fx.sets("l", "2026-10-02", listOf(80.0), listOf(8), exercise = "squat", name = "סקוואט", muscle = MuscleGroup.QUADS)
        val upper = Fx.sets("u1", "2026-10-06", listOf(60.0), listOf(8)) + Fx.sets("u2", "2026-10-12", listOf(60.0), listOf(8))
        val sessions = listOf("l" to "2026-10-02", "u1" to "2026-10-06", "u2" to "2026-10-12").map { (id, d) -> Fx.session(id, d) }
        val out = run(legs + upper, sessions)
        val n = out.first { it.id == "neglect-רגליים" }
        assertEquals("לא בוצע אימון רגליים במשך 12 ימים.", n.text)
        assertEquals(InsightTone.ATTENTION, n.tone)
    }

    @Test
    fun `a decline across workouts is reported`() {
        val facts = Fx.sets("a", "2026-09-28", listOf(60.0, 60.0, 60.0), listOf(12, 11, 11)) +
            Fx.sets("b", "2026-10-04", listOf(60.0, 60.0, 60.0), listOf(11, 10, 10)) +
            Fx.sets("c", "2026-10-11", listOf(60.0, 60.0, 60.0), listOf(10, 9, 9))
        val sessions = listOf("a" to "2026-09-28", "b" to "2026-10-04", "c" to "2026-10-11").map { (id, d) -> Fx.session(id, d) }
        val out = run(facts, sessions)
        assertTrue(out.any { it.id == "dip-bench" && it.text.contains("ירידה בביצועים") })
    }

    @Test
    fun `weekly target streak needs consecutive weeks`() {
        // target 2 per week; weeks Sep 27-Oct 3 and Oct 4-10 each have 2 workouts
        val dates = listOf("2026-09-28", "2026-09-30", "2026-10-05", "2026-10-07", "2026-10-12")
        val facts = dates.flatMap { Fx.sets("s$it", it, listOf(50.0), listOf(10)) }
        val out = run(facts, dates.map { Fx.session("s$it", it) }, target = 2)
        assertTrue(out.any { it.id == "streak" && it.text.contains("2 שבועות ברצף") })
    }

    @Test
    fun `no insight mentions artificial intelligence`() {
        val facts = Fx.sets("a", "2026-10-01", listOf(60.0), listOf(8)) + Fx.sets("b", "2026-10-12", listOf(65.0), listOf(8))
        val out = run(facts, listOf(Fx.session("a", "2026-10-01"), Fx.session("b", "2026-10-12")))
        assertTrue(out.none { it.text.contains("AI") || it.text.contains("בינה מלאכותית") })
    }
}
