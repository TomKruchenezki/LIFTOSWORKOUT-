package app.liftos.domain.logic

import app.liftos.domain.model.PlannedWorkout
import java.time.LocalDate

enum class NextReason { TODAY, UPCOMING, ROTATION, NONE }

data class NextWorkout(
    val workout: PlannedWorkout?,
    val reason: NextReason,
    /** Date the workout is scheduled for (null in rotation mode). */
    val date: LocalDate? = null,
) {
    val isToday: Boolean get() = reason == NextReason.TODAY
}

/** A finished session reduced to what the resolver needs. */
data class CompletedRef(val plannedWorkoutId: String?, val localDate: LocalDate)

object NextWorkoutResolver {

    /**
     * Decides which workout of the active plan to suggest.
     *
     * - If workouts are scheduled on weekdays: today's workout if not done yet, otherwise the next
     *   scheduled day (rest days are skipped automatically).
     * - If nothing is scheduled: rotate through the workouts in order after the last one performed.
     */
    fun resolve(
        workouts: List<PlannedWorkout>,
        completed: List<CompletedRef>,
        today: LocalDate,
    ): NextWorkout {
        if (workouts.isEmpty()) return NextWorkout(null, NextReason.NONE)
        val ordered = workouts.sortedBy { it.orderIndex }
        val scheduled = ordered.filter { it.weekday != null }

        if (scheduled.isNotEmpty()) {
            val doneToday = completed.filter { it.localDate == today }.mapNotNull { it.plannedWorkoutId }.toSet()
            scheduled.firstOrNull { it.weekday == today.dayOfWeek.value && it.id !in doneToday }
                ?.let { return NextWorkout(it, NextReason.TODAY, today) }
            for (offset in 1..7) {
                val date = today.plusDays(offset.toLong())
                scheduled.firstOrNull { it.weekday == date.dayOfWeek.value }
                    ?.let { return NextWorkout(it, NextReason.UPCOMING, date) }
            }
        }

        val last = completed
            .filter { c -> ordered.any { it.id == c.plannedWorkoutId } }
            .maxByOrNull { it.localDate }
        val next = if (last == null) {
            ordered.first()
        } else {
            val idx = ordered.indexOfFirst { it.id == last.plannedWorkoutId }
            ordered[(idx + 1) % ordered.size]
        }
        return NextWorkout(next, NextReason.ROTATION)
    }
}
