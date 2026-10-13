package app.liftos.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTimerTest {
    private val t0 = 1_000_000L

    @Test
    fun `counts down from absolute end time`() {
        val s = RestTimer.start(t0, 120)
        assertEquals(120_000L, RestTimer.remainingMs(s, t0))
        assertEquals(60_000L, RestTimer.remainingMs(s, t0 + 60_000))
        assertFalse(RestTimer.isFinished(s, t0 + 119_999))
        assertTrue(RestTimer.isFinished(s, t0 + 120_000))
    }

    @Test
    fun `survives process death - state is just timestamps`() {
        val s = RestTimer.start(t0, 90)
        // App killed; restored 70 seconds later from the persisted state.
        val restored = s.copy()
        assertEquals(20_000L, RestTimer.remainingMs(restored, t0 + 70_000))
    }

    @Test
    fun `pause freezes remaining time and resume continues`() {
        val s = RestTimer.start(t0, 100)
        val paused = RestTimer.pause(s, t0 + 40_000)
        assertTrue(paused.isPaused)
        assertEquals(60_000L, RestTimer.remainingMs(paused, t0 + 500_000))
        assertFalse(RestTimer.isFinished(paused, t0 + 500_000))
        val resumed = RestTimer.resume(paused, t0 + 100_000)
        assertEquals(60_000L, RestTimer.remainingMs(resumed, t0 + 100_000))
        assertEquals(10_000L, RestTimer.remainingMs(resumed, t0 + 150_000))
    }

    @Test
    fun `adding time works while running and while paused`() {
        val s = RestTimer.start(t0, 60)
        val plus15 = RestTimer.addSeconds(s, t0 + 10_000, 15)
        assertEquals(65_000L, RestTimer.remainingMs(plus15, t0 + 10_000))
        assertEquals(75_000L, plus15.totalMs)

        val paused = RestTimer.pause(s, t0 + 10_000)
        val plus30 = RestTimer.addSeconds(paused, t0 + 20_000, 30)
        assertEquals(80_000L, RestTimer.remainingMs(plus30, t0 + 20_000))
    }

    @Test
    fun `backwards clock jump never exceeds total`() {
        val s = RestTimer.start(t0, 60)
        assertEquals(60_000L, RestTimer.remainingMs(s, t0 - 3_600_000))
    }

    @Test
    fun `progress goes from 0 to 1`() {
        val s = RestTimer.start(t0, 100)
        assertEquals(0f, RestTimer.progress(s, t0), 0.0001f)
        assertEquals(0.5f, RestTimer.progress(s, t0 + 50_000), 0.0001f)
        assertEquals(1f, RestTimer.progress(s, t0 + 200_000), 0.0001f)
    }

    @Test
    fun `duration is clamped to sane bounds`() {
        assertEquals(5_000L, RestTimer.start(t0, 0).totalMs)
        assertEquals(1_800_000L, RestTimer.start(t0, 999_999).totalMs)
    }
}
