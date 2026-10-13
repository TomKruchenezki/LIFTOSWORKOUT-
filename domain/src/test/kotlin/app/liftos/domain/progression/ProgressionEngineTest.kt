package app.liftos.domain.progression

import app.liftos.domain.analytics.Fx
import app.liftos.domain.analytics.SessionFact
import app.liftos.domain.analytics.SetFact
import app.liftos.domain.model.Equipment
import app.liftos.domain.model.SetType
import app.liftos.domain.model.TrackingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressionEngineTest {
    private val today = LocalDate.parse("2026-10-13")

    private fun ex(
        session: String,
        date: String,
        weights: List<Double?>,
        reps: List<Int?>,
        rir: Int? = null,
        equipment: Equipment = Equipment.BARBELL,
        exercise: String = "bench",
        tracking: TrackingType = TrackingType.WEIGHT_REPS,
    ): List<SetFact> = weights.indices.map { i ->
        Fx.fact(session, date, weights[i], reps[i], exercise = exercise, order = i, tracking = tracking)
            .copy(equipment = equipment, targetRepsMin = 8, targetRepsMax = 12, plannedSets = 3, rir = rir)
    }

    private fun suggest(
        facts: List<SetFact>,
        sessions: List<SessionFact> = emptyList(),
        exercise: String = "bench",
        override: Double? = null,
    ) = ProgressionEngine.suggest(exercise, "לחיצת חזה", facts, sessions, today, incrementOverrideKg = override)

    private val userExample =
        ex("s1", "2026-09-22", listOf(60.0, 60.0, 60.0), listOf(10, 9, 8)) +
            ex("s2", "2026-09-29", listOf(60.0, 60.0, 60.0), listOf(11, 10, 9)) +
            ex("s3", "2026-10-06", listOf(60.0, 60.0, 60.0), listOf(12, 12, 12))

    @Test
    fun `the double progression example from the brief suggests 62_5 kg`() {
        val s = suggest(userExample)
        assertEquals(SuggestionKind.INCREASE_WEIGHT, s.kind)
        assertEquals(62.5, s.suggestedWeightKg!!, 0.0)
        assertEquals(60.0, s.currentWeightKg!!, 0.0)
        assertEquals(8, s.targetRepsMin)
        assertEquals(Confidence.HIGH, s.confidence)
        assertEquals(3, s.evidence.size)
        assertTrue(s.explanation.contains("62.5"))
        assertTrue(s.changesWeight)
        assertEquals("s3", s.basedOnSessionId)
    }

    @Test
    fun `before the top of the range the advice is to add reps at the same weight`() {
        val s = suggest(userExample.filter { it.sessionId == "s2" })
        assertEquals(SuggestionKind.ADD_REPS, s.kind)
        assertEquals(60.0, s.suggestedWeightKg!!, 0.0)
        assertFalse(s.changesWeight)
        assertEquals(10, s.targetRepsMin) // weakest set was 9 -> aim for 10
        assertEquals(12, s.targetRepsMax)
    }

    @Test
    fun `a single workout is a low-confidence suggestion that says so`() {
        val s = suggest(ex("s1", "2026-10-06", listOf(60.0, 60.0, 60.0), listOf(12, 12, 12)))
        assertEquals(Confidence.LOW, s.confidence)
        assertTrue(s.cautions.any { it.contains("אימון אחד") })
    }

    @Test
    fun `no history gives no recommendation`() {
        val s = suggest(emptyList())
        assertEquals(SuggestionKind.INSUFFICIENT_DATA, s.kind)
        assertNull(s.suggestedWeightKg)
        assertTrue(s.explanation.isNotBlank())
    }

    @Test
    fun `one bad workout never lowers the target`() {
        val facts = ex("s1", "2026-09-29", listOf(60.0, 60.0, 60.0), listOf(12, 12, 11)) +
            ex("s2", "2026-10-06", listOf(60.0, 60.0, 60.0), listOf(8, 7, 6))
        val s = suggest(facts)
        assertEquals(SuggestionKind.HOLD, s.kind)
        assertEquals(60.0, s.suggestedWeightKg!!, 0.0)
        assertTrue(s.explanation.contains("אימון בודד"))
    }

    @Test
    fun `falling below the range twice at the same weight suggests a small reduction`() {
        val facts = ex("s1", "2026-09-29", listOf(70.0, 70.0, 70.0), listOf(7, 6, 6)) +
            ex("s2", "2026-10-06", listOf(70.0, 70.0, 70.0), listOf(7, 6, 5))
        val s = suggest(facts)
        assertEquals(SuggestionKind.REDUCE_WEIGHT, s.kind)
        assertEquals(67.5, s.suggestedWeightKg!!, 0.0)
    }

    @Test
    fun `two consecutive declines ask to check recovery instead of raising weight`() {
        val facts = ex("s1", "2026-09-22", listOf(60.0, 60.0, 60.0), listOf(12, 11, 11)) +
            ex("s2", "2026-09-29", listOf(60.0, 60.0, 60.0), listOf(11, 10, 10)) +
            ex("s3", "2026-10-06", listOf(60.0, 60.0, 60.0), listOf(10, 9, 9))
        val s = suggest(facts)
        assertEquals(SuggestionKind.REVIEW, s.kind)
        assertEquals(60.0, s.suggestedWeightKg!!, 0.0)
        assertTrue(s.headline.contains("ירידה בביצועים"))
        assertTrue(s.explanation.contains("אין זה אומר שהכוח ירד"))
    }

    @Test
    fun `a deliberate weight reduction is not treated as a decline`() {
        val facts = ex("s1", "2026-09-22", listOf(70.0, 70.0, 70.0), listOf(10, 9, 8)) +
            ex("s2", "2026-09-29", listOf(70.0, 70.0, 70.0), listOf(9, 8, 8)) +
            ex("s3", "2026-10-06", listOf(60.0, 60.0, 60.0), listOf(10, 10, 9))
        assertTrue(suggest(facts).kind != SuggestionKind.REVIEW)
    }

    @Test
    fun `the top of the range at failure waits one more workout`() {
        val facts = ex("s1", "2026-09-29", listOf(60.0, 60.0, 60.0), listOf(11, 10, 10)) +
            ex("s2", "2026-10-06", listOf(60.0, 60.0, 60.0), listOf(12, 12, 12), rir = 0)
        val s = suggest(facts)
        assertEquals(SuggestionKind.HOLD, s.kind)
        assertTrue(s.explanation.contains("כישלון"))
        val withReserve = suggest(
            ex("s1", "2026-09-29", listOf(60.0, 60.0, 60.0), listOf(11, 10, 10)) +
                ex("s2", "2026-10-06", listOf(60.0, 60.0, 60.0), listOf(12, 12, 12), rir = 2),
        )
        assertEquals(SuggestionKind.INCREASE_WEIGHT, withReserve.kind)
    }

    @Test
    fun `after a long break the weight is not raised`() {
        val old = ex("s1", "2026-08-01", listOf(60.0, 60.0, 60.0), listOf(12, 12, 12))
        val s = suggest(old)
        assertEquals(SuggestionKind.HOLD, s.kind)
        assertEquals(Confidence.LOW, s.confidence)
        assertEquals(60.0, s.suggestedWeightKg!!, 0.0)
    }

    @Test
    fun `low energy is mentioned and lowers confidence of an increase`() {
        val sessions = listOf(Fx.session("s3", "2026-10-06", energy = 2))
        val s = suggest(userExample, sessions)
        assertEquals(SuggestionKind.INCREASE_WEIGHT, s.kind)
        assertEquals(Confidence.MEDIUM, s.confidence)
        assertTrue(s.cautions.any { it.contains("אנרגיה") })
    }

    @Test
    fun `increments follow the equipment and can be overridden`() {
        val dumbbells = ex("s1", "2026-10-06", listOf(20.0, 20.0, 20.0), listOf(12, 12, 12), equipment = Equipment.DUMBBELL)
        assertEquals(22.0, suggest(dumbbells).suggestedWeightKg!!, 0.0)
        val small = ex("s1", "2026-10-06", listOf(8.0, 8.0, 8.0), listOf(12, 12, 12), equipment = Equipment.DUMBBELL)
        assertEquals(9.0, suggest(small).suggestedWeightKg!!, 0.0)
        assertEquals(65.0, suggest(userExample, override = 5.0).suggestedWeightKg!!, 0.0)
        val bands = ex("s1", "2026-10-06", listOf(null, null, null), listOf(12, 12, 12), equipment = Equipment.BANDS)
        val b = suggest(bands)
        assertEquals(SuggestionKind.HOLD, b.kind)
        assertNull(b.suggestedWeightKg)
    }

    @Test
    fun `bodyweight movements suggest adding external load only at the top of the range`() {
        val pull = ex("s1", "2026-10-06", listOf(null, null, null), listOf(12, 12, 12), equipment = Equipment.BODYWEIGHT,
            exercise = "pullup", tracking = TrackingType.BODYWEIGHT_REPS)
        val s = suggest(pull, exercise = "pullup")
        assertEquals(SuggestionKind.INCREASE_WEIGHT, s.kind)
        assertEquals(2.5, s.suggestedWeightKg!!, 0.0)
        assertNull(s.currentWeightKg)
        val low = ex("s1", "2026-10-06", listOf(null, null, null), listOf(8, 7, 7), equipment = Equipment.BODYWEIGHT,
            exercise = "pullup", tracking = TrackingType.BODYWEIGHT_REPS)
        assertTrue(suggest(low, exercise = "pullup").kind in setOf(SuggestionKind.HOLD, SuggestionKind.ADD_REPS))
    }

    @Test
    fun `timed holds progress by seconds`() {
        val plank = listOf(40, 40, 35).mapIndexed { i, sec ->
            Fx.fact("s1", "2026-10-06", null, null, exercise = "plank", name = "פלאנק", tracking = TrackingType.DURATION, duration = sec, order = i)
                .copy(targetRepsMin = 30, targetRepsMax = 45, plannedSets = 3)
        }
        val s = suggest(plank, exercise = "plank")
        assertEquals(SuggestionKind.ADD_REPS, s.kind)
        assertEquals(40, s.targetRepsMin)
        assertNull(s.suggestedWeightKg)
    }

    @Test
    fun `missing sets are called out and warm-ups are ignored`() {
        val facts = ex("s1", "2026-10-06", listOf(60.0, 60.0), listOf(12, 12)) +
            listOf(Fx.fact("s1", "2026-10-06", 20.0, 5, type = SetType.WARMUP, order = 9).copy(targetRepsMin = 8, targetRepsMax = 12, plannedSets = 3))
        val s = suggest(facts)
        assertEquals(SuggestionKind.HOLD, s.kind)
        assertTrue(s.explanation.contains("2 מתוך 3"))
    }

    @Test
    fun `pyramids are not judged by double progression`() {
        val facts = ex("s1", "2026-10-06", listOf(50.0, 60.0, 70.0), listOf(12, 10, 8))
        val s = suggest(facts)
        assertEquals(SuggestionKind.HOLD, s.kind)
        assertEquals(Confidence.LOW, s.confidence)
    }

    @Test
    fun `every kind of suggestion carries an explanation`() {
        val scenarios = listOf(
            emptyList(),
            userExample,
            userExample.filter { it.sessionId == "s1" },
            ex("a", "2026-08-01", listOf(60.0), listOf(12)),
            ex("a", "2026-10-06", listOf(60.0, 60.0, 60.0), listOf(5, 5, 5)),
        )
        for (facts in scenarios) {
            val s = suggest(facts)
            assertTrue(s.headline.isNotBlank())
            assertTrue(s.explanation.isNotBlank())
        }
    }

    @Test
    fun `an ignored suggestion stays hidden only until the next workout`() {
        val s = suggest(userExample)
        val ignore = ProgressionDecision("d1", "bench", "s3", SuggestionKind.INCREASE_WEIGHT, 62.5, null, DecisionType.IGNORED, 10L)
        assertEquals(ProgressionEngine.Visibility.IGNORED, ProgressionEngine.visibility(s, listOf(ignore)))
        assertEquals(ProgressionEngine.Visibility.SHOW, ProgressionEngine.visibility(s, emptyList()))
        // After another workout the suggestion is based on a different session, so it shows again.
        val next = suggest(userExample + ex("s4", "2026-10-12", listOf(60.0, 60.0, 60.0), listOf(12, 12, 12)))
        assertEquals(ProgressionEngine.Visibility.SHOW, ProgressionEngine.visibility(next, listOf(ignore)))
        val applied = ProgressionDecision("d2", "bench", "s3", SuggestionKind.INCREASE_WEIGHT, 62.5, 62.5, DecisionType.APPLIED, 20L)
        assertEquals(ProgressionEngine.Visibility.APPLIED, ProgressionEngine.visibility(s, listOf(ignore, applied)))
    }

    @Test
    fun `plan targets override the snapshotted ones`() {
        val facts = ex("s1", "2026-10-06", listOf(60.0, 60.0, 60.0), listOf(10, 10, 10))
        val s = ProgressionEngine.suggest("bench", "x", facts, emptyList(), today, target = RepTarget(6, 10, 3))
        assertEquals(SuggestionKind.INCREASE_WEIGHT, s.kind)
    }
}
