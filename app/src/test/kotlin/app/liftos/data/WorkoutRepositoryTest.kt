package app.liftos.data

import app.liftos.data.repo.AnalyticsRepository
import app.liftos.domain.analytics.RecordKind
import app.liftos.data.repo.ToggleResult
import app.liftos.domain.logic.RestTimer
import app.liftos.domain.logic.VolumeCalculator
import app.liftos.domain.model.SessionSource
import app.liftos.domain.model.SessionStatus
import app.liftos.domain.model.SetType
import app.liftos.domain.model.Side
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkoutRepositoryTest {
    private lateinit var env: TestEnv

    @Before
    fun setUp() = runBlocking {
        env = TestEnv()
        env.seeder.seedIfNeeded()
    }

    @After
    fun tearDown() = env.close()

    private suspend fun planWorkout(name: String): String {
        val plan = env.plans.getPlanDetail(env.plans.observePlans().first().single().id)!!
        return plan.workouts.first { it.workout.name == name }.workout.id
    }

    private suspend fun start(name: String = "Upper A"): String = env.workouts.startFromPlan(planWorkout(name))

    private suspend fun detail(id: String) = env.workouts.getDetail(id)!!

    private suspend fun fillAndComplete(id: String, weight: Double, reps: Int) {
        detail(id).exercises.flatMap { it.sets }.forEach { s ->
            env.workouts.setWeight(s.id, weight)
            env.workouts.setReps(s.id, reps)
            env.workouts.toggleSetCompleted(s.id)
        }
    }

    // ---- starting -----------------------------------------------------------------------------------

    @Test
    fun `starting a workout creates an active session with a snapshot of the plan`() = runBlocking {
        val id = start()
        val d = detail(id)
        assertEquals(SessionStatus.ACTIVE, d.session.status)
        assertEquals("Upper A", d.session.title)
        assertEquals(7, d.exercises.size)
        assertEquals("Barbell Bench Press", d.exercises[0].exercise.nameEn)
        assertEquals(3, d.exercises[0].sets.size)
        assertTrue(d.exercises.flatMap { it.sets }.none { it.isCompleted })
        assertEquals(id, env.workouts.observeActive().first()?.id)
    }

    @Test
    fun `only one workout can be active - starting again returns the same one`() = runBlocking {
        val first = start("Upper A")
        val second = start("Lower A")
        assertEquals(first, second)
    }

    @Test
    fun `changing the plan afterwards does not alter an existing session`() = runBlocking {
        val id = start()
        val plan = env.plans.getPlanDetail(env.plans.observePlans().first().single().id)!!
        val pe = plan.workouts[0].exercises[0]
        env.plans.updatePlannedExercise(pe.copy(targetSets = 8, repsMin = 1, repsMax = 2))
        env.plans.swapExercise(pe.id, env.exercises.get("push-up")!!)
        val d = detail(id)
        assertEquals(3, d.exercises[0].exercise.plannedSets)
        assertEquals(5, d.exercises[0].exercise.repsMin)
        assertEquals("barbell-bench-press", d.exercises[0].exercise.exerciseId)
        assertEquals(3, d.exercises[0].sets.size)
    }

    // ---- sets ------------------------------------------------------------------------------------------

    @Test
    fun `updating weight reps and rir is saved immediately`() = runBlocking {
        val id = start()
        val set = detail(id).exercises[0].sets[0]
        env.workouts.setWeight(set.id, 62.5)
        env.workouts.setReps(set.id, 8)
        env.workouts.setRir(set.id, 2)
        env.workouts.setSetNotes(set.id, "קל")
        val saved = detail(id).exercises[0].sets[0]
        assertEquals(62.5, saved.weightKg!!, 0.0); assertEquals(8, saved.reps); assertEquals(2, saved.rir); assertEquals("קל", saved.notes)
    }

    @Test
    fun `out of range values are ignored`() = runBlocking {
        val id = start()
        val set = detail(id).exercises[0].sets[0]
        env.workouts.setRir(set.id, 99)
        env.workouts.setWeight(set.id, -5.0)
        val s = detail(id).exercises[0].sets[0]
        assertNull(s.rir); assertNull(s.weightKg)
    }

    @Test
    fun `adding and deleting sets keeps order consistent`() = runBlocking {
        val id = start()
        val ex = detail(id).exercises[0].exercise
        env.workouts.addSet(ex.id)
        assertEquals(4, detail(id).exercises[0].sets.size)
        env.workouts.addSet(ex.id, SetType.WARMUP)
        var sets = detail(id).exercises[0].sets
        assertEquals(5, sets.size)
        assertEquals(SetType.WARMUP, sets.first().setType)
        assertEquals(listOf(0, 1, 2, 3, 4), sets.map { it.orderIndex })

        env.workouts.deleteSet(sets[2].id)
        sets = detail(id).exercises[0].sets
        assertEquals(4, sets.size)
        assertEquals(listOf(0, 1, 2, 3), sets.map { it.orderIndex })
    }

    @Test
    fun `a new set copies the previous set values but is not completed`() = runBlocking {
        val id = start()
        val ex = detail(id).exercises[0]
        env.workouts.setWeight(ex.sets.last().id, 70.0); env.workouts.setReps(ex.sets.last().id, 6)
        env.workouts.addSet(ex.exercise.id)
        val added = detail(id).exercises[0].sets.last()
        assertEquals(70.0, added.weightKg!!, 0.0); assertEquals(6, added.reps); assertFalse(added.isCompleted)
    }

    @Test
    fun `a set without reps cannot be completed`() = runBlocking {
        val id = start()
        val set = detail(id).exercises[0].sets[0]
        assertEquals(ToggleResult.Rejected, env.workouts.toggleSetCompleted(set.id))
        env.workouts.setReps(set.id, 5)
        assertTrue(env.workouts.toggleSetCompleted(set.id) is ToggleResult.Completed)
        assertEquals(ToggleResult.Uncompleted, env.workouts.toggleSetCompleted(set.id))
        assertFalse(detail(id).exercises[0].sets[0].isCompleted)
    }

    @Test
    fun `warm-up sets are separate from working sets and never trigger the rest timer`() = runBlocking {
        val id = start()
        val ex = detail(id).exercises[0].exercise
        env.workouts.addSet(ex.id, SetType.WARMUP)
        val warm = detail(id).exercises[0].sets.first()
        env.workouts.setWeight(warm.id, 20.0); env.workouts.setReps(warm.id, 12)
        val r = env.workouts.toggleSetCompleted(warm.id) as ToggleResult.Completed
        assertFalse(r.restStarted)
        assertNull(env.workouts.getDetail(id)!!.session.rest)
        assertEquals(0.0, VolumeCalculator.sessionVolume(detail(id)), 0.0)
    }

    @Test
    fun `changing a set type keeps warm-ups first`() = runBlocking {
        val id = start()
        val sets = detail(id).exercises[0].sets
        env.workouts.changeSetType(sets[2].id, SetType.WARMUP)
        val after = detail(id).exercises[0].sets
        assertEquals(sets[2].id, after.first().id)
        assertEquals(SetType.WARMUP, after.first().setType)
        assertEquals(listOf(0, 1, 2), after.map { it.orderIndex })
    }

    @Test
    fun `unilateral exercise rows come in right-left pairs and rest starts after the left side`() = runBlocking {
        val id = start("Lower B")
        val split = detail(id).exercises.first { it.exercise.exerciseId == "bulgarian-split-squat" }
        assertEquals(listOf(Side.RIGHT, Side.LEFT, Side.RIGHT, Side.LEFT, Side.RIGHT, Side.LEFT), split.sets.map { it.side })
        split.sets.forEach { env.workouts.setReps(it.id, 10); env.workouts.setWeight(it.id, 20.0) }

        val right = env.workouts.toggleSetCompleted(split.sets[0].id) as ToggleResult.Completed
        assertFalse(right.restStarted)
        val left = env.workouts.toggleSetCompleted(split.sets[1].id) as ToggleResult.Completed
        assertTrue(left.restStarted)

        env.workouts.addSet(split.exercise.id)
        assertEquals(8, detail(id).exercises.first { it.exercise.id == split.exercise.id }.sets.size)
        env.workouts.deleteSet(split.sets[0].id) // removes the whole pair
        assertEquals(6, detail(id).exercises.first { it.exercise.id == split.exercise.id }.sets.size)
    }

    @Test
    fun `plank is tracked in seconds`() = runBlocking {
        val id = start("Lower B")
        val plank = detail(id).exercises.first { it.exercise.exerciseId == "plank" }
        val set = plank.sets[0]
        assertEquals(ToggleResult.Rejected, env.workouts.toggleSetCompleted(set.id))
        env.workouts.setDuration(set.id, 45)
        assertTrue(env.workouts.toggleSetCompleted(set.id) is ToggleResult.Completed)
        assertEquals(0.0, VolumeCalculator.sessionVolume(detail(id)), 0.0)
    }

    // ---- previous data and prefill -----------------------------------------------------------------------

    @Test
    fun `the next workout of the same type is pre-filled from the last one but not completed`() = runBlocking {
        val first = start()
        fillAndComplete(first, 60.0, 10)
        env.advance(3600)
        env.workouts.finishWorkout(first)

        env.advance(7 * 24 * 3600)
        val second = start()
        val d = detail(second)
        assertTrue(d.exercises.flatMap { it.sets }.all { it.weightKg == 60.0 && it.reps == 10 })
        assertTrue(d.exercises.flatMap { it.sets }.none { it.isCompleted })

        val prev = env.workouts.previousPerformance(second)
        assertEquals(7, prev.size)
        assertEquals("Upper A", prev["barbell-bench-press"]!!.sessionTitle)
        assertEquals(3, prev["barbell-bench-press"]!!.sets.size)
    }

    @Test
    fun `an exercise never done before has no previous data`() = runBlocking {
        val id = start()
        assertTrue(env.workouts.previousPerformance(id).isEmpty())
    }

    // ---- completing sets, rest timer, finishing -----------------------------------------------------------

    @Test
    fun `completing a working set starts the rest timer with the exercise's rest time`() = runBlocking {
        val id = start()
        val set = detail(id).exercises[0].sets[0]
        env.workouts.setReps(set.id, 8); env.workouts.setWeight(set.id, 80.0)
        val r = env.workouts.toggleSetCompleted(set.id) as ToggleResult.Completed
        assertTrue(r.restStarted)
        val rest = detail(id).session.rest!!
        assertEquals(180_000L, rest.totalMs)
        assertEquals(env.now + 180_000L, rest.endsAt)
        assertEquals("לחיצת חזה במוט", rest.label)
        assertEquals(180_000L, RestTimer.remainingMs(rest, env.now))
    }

    @Test
    fun `auto rest can be switched off in settings`() = runBlocking {
        env.settings.update { it.copy(autoStartRest = false) }
        val id = start()
        val set = detail(id).exercises[0].sets[0]
        env.workouts.setReps(set.id, 8)
        val r = env.workouts.toggleSetCompleted(set.id) as ToggleResult.Completed
        assertFalse(r.restStarted)
        assertNull(detail(id).session.rest)
    }

    @Test
    fun `rest timer can pause resume add time and skip`() = runBlocking {
        val id = start()
        env.workouts.startRest(id, 120, "x")
        env.advance(20)
        env.workouts.pauseRest(id)
        env.advance(500)
        var rest = detail(id).session.rest!!
        assertTrue(rest.isPaused)
        assertEquals(100_000L, RestTimer.remainingMs(rest, env.now))
        env.workouts.addRestSeconds(id, 30)
        rest = detail(id).session.rest!!
        assertEquals(130_000L, RestTimer.remainingMs(rest, env.now))
        env.workouts.resumeRest(id)
        env.advance(10)
        rest = detail(id).session.rest!!
        assertEquals(120_000L, RestTimer.remainingMs(rest, env.now))
        env.workouts.skipRest(id)
        assertNull(detail(id).session.rest)
    }

    @Test
    fun `exactly one finisher wins the end of a rest period`() = runBlocking {
        val id = start()
        env.workouts.startRest(id, 60)
        val endsAt = detail(id).session.rest!!.endsAt!!
        assertTrue(env.workouts.claimRestFinish(id, endsAt))
        assertFalse(env.workouts.claimRestFinish(id, endsAt))
        assertNull(detail(id).session.rest)
    }

    @Test
    fun `a stale finisher cannot clear a newer rest`() = runBlocking {
        val id = start()
        env.workouts.startRest(id, 60)
        val oldEnd = detail(id).session.rest!!.endsAt!!
        env.advance(10)
        env.workouts.startRest(id, 90) // user completed another set
        assertFalse(env.workouts.claimRestFinish(id, oldEnd))
        assertNotNull(detail(id).session.rest)
    }

    @Test
    fun `finishing removes unfinished sets and empty exercises but keeps skipped ones`() = runBlocking {
        val id = start()
        val d = detail(id)
        val bench = d.exercises[0]
        env.workouts.setReps(bench.sets[0].id, 8); env.workouts.setWeight(bench.sets[0].id, 80.0)
        env.workouts.toggleSetCompleted(bench.sets[0].id)
        env.workouts.setSkipped(d.exercises[1].exercise.id, true, "כאב בכתף")

        env.advance(1800)
        val summary = env.workouts.finishWorkout(id)
        // 7 exercises: 3+3+2+3+3+2+2 = 18 sets, one of them completed -> 17 removed; 5 whole exercises removed.
        assertEquals(17, summary.removedSets)
        assertEquals(5, summary.removedExercises)
        val after = detail(id)
        assertEquals(SessionStatus.COMPLETED, after.session.status)
        assertEquals(env.now, after.session.endedAt)
        assertEquals(2, after.exercises.size) // bench + the skipped row
        assertEquals(1, after.exercises[0].sets.size)
        assertTrue(after.exercises[1].exercise.skipped)
        assertEquals("כאב בכתף", after.exercises[1].exercise.skipReason)
        assertEquals(listOf(0, 1), after.exercises.map { it.exercise.orderIndex })
        assertNull(env.workouts.observeActive().first())
    }

    @Test
    fun `finishing clears the rest timer`() = runBlocking {
        val id = start()
        env.workouts.startRest(id, 60)
        env.workouts.finishWorkout(id)
        assertNull(detail(id).session.rest)
    }

    @Test
    fun `discarding removes the whole workout`() = runBlocking {
        val id = start()
        env.workouts.discardWorkout(id)
        assertNull(env.workouts.getDetail(id))
        assertNull(env.workouts.observeActive().first())
    }

    // ---- editing exercises during a workout -----------------------------------------------------------------

    @Test
    fun `an exercise can be added during the workout`() = runBlocking {
        val id = start()
        val newId = env.workouts.addExercise(id, env.exercises.get("hammer-curl")!!)
        val d = detail(id)
        assertEquals(8, d.exercises.size)
        assertEquals(newId, d.exercises.last().exercise.id)
        assertEquals("Hammer Curl", d.exercises.last().exercise.nameEn)
        assertEquals(3, d.exercises.last().sets.size)
        assertNull(d.exercises.last().exercise.plannedExerciseId)
    }

    @Test
    fun `an exercise can be replaced and keeps its position`() = runBlocking {
        val id = start()
        val d = detail(id)
        val slot = d.exercises[0].exercise
        env.workouts.replaceExercise(slot.id, env.exercises.get("dumbbell-bench-press")!!)
        val after = detail(id)
        assertEquals(7, after.exercises.size)
        assertEquals(slot.id, after.exercises[0].exercise.id)
        assertEquals("dumbbell-bench-press", after.exercises[0].exercise.exerciseId)
        assertEquals("Dumbbell Bench Press", after.exercises[0].exercise.nameEn)
        assertEquals(3, after.exercises[0].sets.size)
    }

    @Test
    fun `replacing keeps sets that were already completed`() = runBlocking {
        val id = start()
        val slot = detail(id).exercises[0]
        env.workouts.setReps(slot.sets[0].id, 8); env.workouts.setWeight(slot.sets[0].id, 80.0)
        env.workouts.toggleSetCompleted(slot.sets[0].id)
        env.workouts.replaceExercise(slot.exercise.id, env.exercises.get("machine-chest-press")!!)
        val sets = detail(id).exercises[0].sets
        assertEquals(3, sets.size)
        assertTrue(sets[0].isCompleted)
        assertEquals(80.0, sets[0].weightKg!!, 0.0)
        assertTrue(sets.drop(1).none { it.isCompleted })
    }

    @Test
    fun `exercises can be reordered during the workout`() = runBlocking {
        val id = start()
        val ids = detail(id).exercises.map { it.exercise.id }
        val reordered = listOf(ids[6], ids[0], ids[1], ids[2], ids[3], ids[4], ids[5])
        env.workouts.reorderExercises(id, reordered)
        assertEquals(reordered, detail(id).exercises.map { it.exercise.id })
        env.workouts.removeExercise(ids[6])
        assertEquals(6, detail(id).exercises.size)
        assertEquals((0..5).toList(), detail(id).exercises.map { it.exercise.orderIndex })
    }

    @Test
    fun `notes ratings and skip reason are stored`() = runBlocking {
        val id = start()
        val ex = detail(id).exercises[0].exercise
        env.workouts.setExerciseNotes(ex.id, "כתף ימין")
        env.workouts.setSessionNotes(id, "אימון חזק")
        env.workouts.setRatings(id, energy = 4, mood = 9)
        env.workouts.setSkipped(ex.id, true, "  אין ציוד ")
        val d = detail(id)
        assertEquals("כתף ימין", d.exercises[0].exercise.notes)
        assertEquals("אימון חזק", d.session.notes)
        assertEquals(4, d.session.energyRating)
        assertEquals(5, d.session.moodRating) // clamped to the 1..5 scale
        assertEquals("אין ציוד", d.exercises[0].exercise.skipReason)
        env.workouts.setSkipped(ex.id, false)
        assertEquals("", detail(id).exercises[0].exercise.skipReason)
    }

    // ---- crash safety --------------------------------------------------------------------------------------

    @Test
    fun `an active workout survives the process being killed and is fully restorable`() = runBlocking {
        val dbName = "restore-test.db"
        val first = TestEnv(dbName = dbName, idPrefix = "a")
        first.seeder.seedIfNeeded()
        val plan = first.plans.getPlanDetail(first.plans.observePlans().first().single().id)!!
        val sessionId = first.workouts.startFromPlan(plan.workouts[0].workout.id)
        val d = first.workouts.getDetail(sessionId)!!
        val bench = d.exercises[0]
        first.workouts.setWeight(bench.sets[0].id, 82.5); first.workouts.setReps(bench.sets[0].id, 6)
        first.workouts.toggleSetCompleted(bench.sets[0].id) // also starts the rest timer
        first.workouts.setSessionNotes(sessionId, "באמצע אימון")
        val restBefore = first.workouts.getDetail(sessionId)!!.session.rest!!
        first.close() // "process killed"

        // New process, same database file; the clock is 40 s later.
        val second = TestEnv(dbName = dbName, startMs = first.now + 40_000, idPrefix = "b")
        second.seeder.seedIfNeeded() // must not disturb anything
        val active = second.workouts.observeActive().first()
        assertEquals(sessionId, active?.id)
        val restored = second.workouts.getDetail(sessionId)!!
        assertEquals("באמצע אימון", restored.session.notes)
        assertEquals(82.5, restored.exercises[0].sets[0].weightKg!!, 0.0)
        assertTrue(restored.exercises[0].sets[0].isCompleted)
        assertEquals(7, restored.exercises.size)
        // The rest timer is based on absolute time, so it kept counting while the app was dead.
        assertEquals(restBefore, restored.session.rest)
        assertEquals(restBefore.totalMs - 40_000, RestTimer.remainingMs(restored.session.rest!!, second.now))
        // And the user can keep working.
        second.workouts.setReps(restored.exercises[0].sets[1].id, 5)
        second.workouts.toggleSetCompleted(restored.exercises[0].sets[1].id)
        assertEquals(2, second.workouts.getDetail(sessionId)!!.exercises[0].sets.count { it.isCompleted })
        second.close()
    }

    // ---- history editing ---------------------------------------------------------------------------------------

    @Test
    fun `a wrong weight can be corrected after the workout and volume follows`() = runBlocking {
        val id = start()
        fillAndComplete(id, 70.0, 10)
        env.workouts.finishWorkout(id)
        val before = VolumeCalculator.sessionVolume(detail(id))

        val bench = detail(id).exercises[0].sets
        bench.forEach { env.workouts.setWeight(it.id, 60.0) }
        val after = VolumeCalculator.sessionVolume(detail(id))
        assertEquals(before - 3 * 10 * 10.0, after, 0.0001)

        val history = env.workouts.observeHistory().first().single()
        assertEquals(after, history.volumeKg, 0.0001)
    }

    @Test
    fun `history list totals match the domain volume calculation`() = runBlocking {
        val id = start()
        fillAndComplete(id, 55.0, 8)
        env.workouts.finishWorkout(id)
        val h = env.workouts.observeHistory().first().single()
        val d = detail(id)
        val totals = VolumeCalculator.totals(d)
        assertEquals(totals.volumeKg, h.volumeKg, 0.0001)
        assertEquals(totals.workingSets, h.workingSets)
        assertEquals(totals.exercisesPerformed, h.exercises)
    }

    @Test
    fun `a past workout can be entered with a historical date and time`() = runBlocking {
        val pastStart = java.time.ZonedDateTime.of(2026, 9, 1, 18, 30, 0, 0, java.time.ZoneId.of("Asia/Jerusalem")).toInstant().toEpochMilli()
        val id = env.workouts.createPastWorkout(planWorkout("Lower A"), "Lower A", pastStart, 65)
        val d = detail(id)
        assertEquals(SessionStatus.COMPLETED, d.session.status)
        assertEquals(SessionSource.MANUAL, d.session.source)
        assertEquals("2026-09-01", d.session.localDate)
        assertEquals(pastStart + 65 * 60_000L, d.session.endedAt)
        assertEquals(6, d.exercises.size)
        assertNull(env.workouts.observeActive().first()) // it does not become the active workout

        // Fill one set in - volume counts after editing.
        val set = d.exercises[0].sets[0]
        env.workouts.setWeight(set.id, 100.0); env.workouts.setReps(set.id, 5); env.workouts.toggleSetCompleted(set.id)
        assertEquals(500.0, VolumeCalculator.sessionVolume(detail(id)), 0.0001)
        assertNull(detail(id).session.rest) // no rest timer for finished workouts
    }

    @Test
    fun `start and end time can be edited and the date follows`() = runBlocking {
        val id = start()
        env.workouts.finishWorkout(id)
        val newStart = java.time.ZonedDateTime.of(2026, 10, 1, 7, 0, 0, 0, java.time.ZoneId.of("Asia/Jerusalem")).toInstant().toEpochMilli()
        env.workouts.setTimes(id, newStart, newStart + 3_000_000)
        val s = detail(id).session
        assertEquals("2026-10-01", s.localDate)
        assertEquals(3_000_000L, s.durationMs)
    }

    @Test
    fun `the previous workout of the same type is found for comparison`() = runBlocking {
        val first = start()
        fillAndComplete(first, 60.0, 10)
        env.workouts.finishWorkout(first)
        env.advance(86_400)
        val second = start()
        fillAndComplete(second, 62.5, 10)
        env.workouts.finishWorkout(second)
        assertEquals(first, env.workouts.previousSameType(second)!!.session.id)
        assertNull(env.workouts.previousSameType(first))

        val records = AnalyticsRepository(env.db).getData().records
        val prs = records.eventsFor(second)
        assertTrue(prs.any { it.exerciseName == "לחיצת חזה במוט" && it.kind == RecordKind.MAX_WEIGHT && it.newValue == 62.5 && it.previousValue == 60.0 })
        assertTrue(records.eventsFor(first).isEmpty())
    }

    @Test
    fun `deleting a workout removes its exercises and sets`() = runBlocking {
        val id = start()
        fillAndComplete(id, 50.0, 10)
        env.workouts.finishWorkout(id)
        env.workouts.deleteSession(id)
        assertNull(env.workouts.getDetail(id))
        assertEquals(0, env.db.sessionDao().getAllSets().size)
        assertEquals(0, env.db.sessionDao().getAllSessionExercises().size)
    }
}
