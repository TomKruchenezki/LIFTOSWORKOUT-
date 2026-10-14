package app.liftos.data

import app.liftos.data.repo.AnalyticsRepository
import app.liftos.domain.analytics.RecordKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Scenario B of the acceptance audit at the data level: analytics are derived from the workouts, so changing a past
 * workout (editing, un-ticking or deleting a set) changes the charts' source data, the records and the volume at once.
 */
@RunWith(RobolectricTestRunner::class)
class AnalyticsLiveUpdateTest {
    private lateinit var env: TestEnv
    private lateinit var analytics: AnalyticsRepository

    @Before
    fun setUp() = runBlocking {
        env = TestEnv()
        env.seeder.seedIfNeeded()
        analytics = AnalyticsRepository(env.db)
    }

    @After
    fun tearDown() = env.close()

    private suspend fun upperA(): String {
        val plan = env.plans.getPlanDetail(env.plans.observePlans().first().single().id)!!
        return plan.workouts.first { it.workout.name == "Upper A" }.workout.id
    }

    /** A finished workout [daysAgo] days back where every set is done with [weight] x 8. */
    private suspend fun log(daysAgo: Int, weight: Double): String {
        val id = env.workouts.createPastWorkout(upperA(), "Upper A", env.now - daysAgo * 86_400_000L, 60)
        env.workouts.getDetail(id)!!.exercises.flatMap { it.sets }.forEach { s ->
            env.workouts.setWeight(s.id, weight)
            env.workouts.setReps(s.id, 8)
            env.workouts.toggleSetCompleted(s.id)
        }
        return id
    }

    private suspend fun topWeightEvent(sessionId: String): Double? =
        analytics.getData().records.eventsFor(sessionId).filter { it.kind == RecordKind.MAX_WEIGHT }.maxOfOrNull { it.newValue }

    @Test
    fun `with no workouts there is nothing to analyse`() = runBlocking {
        val d = analytics.getData()
        assertTrue(d.isEmpty)
        assertTrue(d.facts.isEmpty())
        assertTrue(d.records.events.isEmpty())
    }

    @Test
    fun `editing a past set changes the volume and the record that workout set`() = runBlocking {
        log(21, 50.0)
        log(14, 55.0)
        val latest = log(7, 60.0)

        val before = analytics.getData()
        assertEquals(3, before.sessions.size)
        assertEquals(60.0, before.facts.mapNotNull { it.weightKg }.max(), 0.0)
        assertEquals(60.0, topWeightEvent(latest)!!, 0.0)
        val volumeBefore = before.facts.sumOf { it.volumeKg }

        val firstSet = env.workouts.getDetail(latest)!!.exercises.first { it.sets.any { s -> s.isCompleted } }.sets.first { it.isCompleted }
        env.workouts.setWeight(firstSet.id, 100.0)

        val after = analytics.getData()
        assertEquals(100.0, after.facts.mapNotNull { it.weightKg }.max(), 0.0)
        assertEquals("the record of that workout follows the edit", 100.0, topWeightEvent(latest)!!, 0.0)
        assertTrue("volume went up", after.facts.sumOf { it.volumeKg } > volumeBefore)
    }

    @Test
    fun `un-ticking a set removes it from every figure`() = runBlocking {
        val id = log(7, 60.0)
        val before = analytics.getData()
        val sets = env.workouts.getDetail(id)!!.exercises.flatMap { it.sets }.filter { it.isCompleted }
        env.workouts.toggleSetCompleted(sets.first().id)

        val after = analytics.getData()
        assertEquals(before.facts.size - 1, after.facts.size)
        assertTrue(after.facts.sumOf { it.volumeKg } < before.facts.sumOf { it.volumeKg })
    }

    @Test
    fun `deleting a workout removes its sets and the records it created`() = runBlocking {
        log(14, 50.0)
        val middle = log(10, 55.0)
        log(7, 52.5)

        assertTrue("the middle workout broke a record", analytics.getData().records.eventsFor(middle).isNotEmpty())
        env.workouts.deleteSession(middle)

        val after = analytics.getData()
        assertEquals(2, after.sessions.size)
        assertTrue(after.facts.none { it.sessionId == middle })
        assertTrue(after.records.eventsFor(middle).isEmpty())
        assertEquals("the best weight is recomputed from what is left", 52.5, after.facts.mapNotNull { it.weightKg }.max(), 0.0)
    }

    @Test
    fun `the live flow and the one-shot read agree after an edit`() = runBlocking {
        val id = log(5, 40.0)
        val set = env.workouts.getDetail(id)!!.exercises.flatMap { it.sets }.first { it.isCompleted }
        env.workouts.setWeight(set.id, 45.0)
        val live = analytics.observeData().first()
        val once = analytics.getData()
        assertEquals(once.facts.size, live.facts.size)
        assertEquals(once.facts.sumOf { it.volumeKg }, live.facts.sumOf { it.volumeKg }, 0.0)
        assertEquals(once.records.events.size, live.records.events.size)
    }
}
