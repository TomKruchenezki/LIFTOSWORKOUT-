package app.liftos.domain.logic

import app.liftos.domain.model.PlannedWorkout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class NextWorkoutResolverTest {
    // Israeli split: Sun Upper A, Mon Lower A, Wed Upper B, Thu Lower B.
    private val upperA = PlannedWorkout("ua", "p", "Upper A", orderIndex = 0, weekday = 7)
    private val lowerA = PlannedWorkout("la", "p", "Lower A", orderIndex = 1, weekday = 1)
    private val upperB = PlannedWorkout("ub", "p", "Upper B", orderIndex = 2, weekday = 3)
    private val lowerB = PlannedWorkout("lb", "p", "Lower B", orderIndex = 3, weekday = 4)
    private val all = listOf(upperA, lowerA, upperB, lowerB)

    private fun d(day: Int) = LocalDate.of(2026, 10, day) // 11 = Sunday, 12 = Monday, 13 = Tuesday ...

    @Test
    fun `today's scheduled workout is suggested`() {
        val r = NextWorkoutResolver.resolve(all, emptyList(), d(11))
        assertEquals("ua", r.workout?.id)
        assertEquals(NextReason.TODAY, r.reason)
    }

    @Test
    fun `after finishing today's workout the next scheduled day is suggested`() {
        val r = NextWorkoutResolver.resolve(all, listOf(CompletedRef("ua", d(11))), d(11))
        assertEquals("la", r.workout?.id)
        assertEquals(NextReason.UPCOMING, r.reason)
        assertEquals(d(12), r.date)
    }

    @Test
    fun `on the rest day tuesday the next workout is wednesday's upper b`() {
        val r = NextWorkoutResolver.resolve(all, emptyList(), d(13))
        assertEquals("ub", r.workout?.id)
        assertEquals(NextReason.UPCOMING, r.reason)
        assertEquals(d(14), r.date)
    }

    @Test
    fun `on the weekend the next workout wraps to sunday`() {
        val r = NextWorkoutResolver.resolve(all, emptyList(), d(17)) // Saturday
        assertEquals("ua", r.workout?.id)
        assertEquals(d(18), r.date)
    }

    @Test
    fun `a missed workout does not block today's workout`() {
        // Sunday's Upper A was never done; on Monday we still suggest Lower A.
        val r = NextWorkoutResolver.resolve(all, emptyList(), d(12))
        assertEquals("la", r.workout?.id)
        assertEquals(NextReason.TODAY, r.reason)
    }

    @Test
    fun `unscheduled plans rotate after the last performed workout`() {
        val free = all.map { it.copy(weekday = null) }
        val none = NextWorkoutResolver.resolve(free, emptyList(), d(13))
        assertEquals("ua", none.workout?.id)
        assertEquals(NextReason.ROTATION, none.reason)

        val after = NextWorkoutResolver.resolve(free, listOf(CompletedRef("ub", d(10))), d(13))
        assertEquals("lb", after.workout?.id)

        val wrap = NextWorkoutResolver.resolve(free, listOf(CompletedRef("lb", d(10))), d(13))
        assertEquals("ua", wrap.workout?.id)
    }

    @Test
    fun `empty plan has no next workout`() {
        val r = NextWorkoutResolver.resolve(emptyList(), emptyList(), d(11))
        assertNull(r.workout)
        assertEquals(NextReason.NONE, r.reason)
    }
}
