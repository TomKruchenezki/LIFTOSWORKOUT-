package app.liftos.data

import app.liftos.data.repo.MeasurementInput
import app.liftos.domain.model.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The numbers on the home screen come straight from the database: this checks the queries behind them. */
@RunWith(RobolectricTestRunner::class)
class HomeDataTest {
    private lateinit var env: TestEnv

    @Before
    fun setUp() = runBlocking {
        env = TestEnv()
        env.seeder.seedIfNeeded()
    }

    @After
    fun tearDown() = env.close()

    private suspend fun planned(): String {
        val plan = env.plans.getPlanDetail(env.plans.observePlans().first().single().id)!!
        return plan.workouts.first().workout.id
    }

    private suspend fun past(daysAgo: Int) = env.workouts.createPastWorkout(planned(), "Upper A", env.now - daysAgo * 86_400_000L, 50)

    @Test
    fun `counts only finished workouts inside the date range`() = runBlocking {
        past(1); past(3); past(20)
        val active = env.workouts.startFromPlan(planned())
        assertNotNull(env.workouts.getDetail(active))

        // env.now is 2025-10-09 in Jerusalem: 1 day ago = 10-08, 3 days ago = 10-06, 20 days ago = 09-19.
        assertEquals(2, env.workouts.observeCountBetween("2025-10-06", "2025-10-08").first())
        assertEquals(1, env.workouts.observeCountBetween("2025-10-07", "2025-10-09").first())
        assertEquals(3, env.workouts.observeCountBetween("2025-09-01", "2025-10-31").first())
        assertEquals("a running workout is not counted", 0, env.workouts.observeCountBetween("2025-10-09", "2025-10-09").first())
    }

    @Test
    fun `the last workout is the newest finished one`() = runBlocking {
        assertNull(env.workouts.observeLastCompleted().first())
        past(10); val newest = past(2); past(5)
        val last = env.workouts.observeLastCompleted().first()!!
        assertEquals(newest, last.id)
        assertEquals(SessionStatus.COMPLETED, last.status)
    }

    @Test
    fun `the latest weight skips measurements without a weight and follows edits`() = runBlocking {
        assertNull(env.measurements.observeLatestWeight().first())
        env.measurements.add(MeasurementInput(weightKg = 84.0), env.now - 5 * 86_400_000L)
        val newest = env.measurements.add(MeasurementInput(weightKg = 83.2), env.now - 2 * 86_400_000L)
        env.measurements.add(MeasurementInput(waistCm = 90.0), env.now - 86_400_000L)
        assertEquals(83.2, env.measurements.observeLatestWeight().first()!!.weightKg!!, 0.0)

        env.measurements.update(newest, MeasurementInput(weightKg = 82.9), env.now - 2 * 86_400_000L)
        assertEquals(82.9, env.measurements.observeLatestWeight().first()!!.weightKg!!, 0.0)
        env.measurements.delete(newest)
        assertEquals(84.0, env.measurements.observeLatestWeight().first()!!.weightKg!!, 0.0)
    }
}
