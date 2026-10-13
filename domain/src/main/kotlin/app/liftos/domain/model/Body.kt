package app.liftos.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class BodyMeasurement(
    val id: String,
    /** Epoch millis, UTC. */
    val measuredAt: Long,
    /** Local calendar date (ISO yyyy-MM-dd) in [zoneId]. */
    val localDate: String,
    val zoneId: String,
    val weightKg: Double? = null,
    val waistCm: Double? = null,
    val chestCm: Double? = null,
    val armCm: Double? = null,
    val thighCm: Double? = null,
    val hipsCm: Double? = null,
    val neckCm: Double? = null,
    val shouldersCm: Double? = null,
    val bodyFatPct: Double? = null,
    /** Circumstances that may explain the reading (see [MeasurementCondition]). */
    val conditions: List<MeasurementCondition> = emptyList(),
    /** Values of the user's own metrics, keyed by [CustomMetric.id]. */
    val customValues: Map<String, Double> = emptyMap(),
    val notes: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    /** True when at least one numeric metric was entered. */
    val hasAnyValue: Boolean
        get() = listOf(weightKg, waistCm, chestCm, armCm, thighCm, hipsCm, neckCm, shouldersCm, bodyFatPct).any { it != null } ||
            customValues.isNotEmpty()
}

/** A user-defined measurement field (for example "היקף ברך" or "קפל עור"). */
@Serializable
data class CustomMetric(
    val id: String,
    val name: String,
    val unit: String = "",
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val createdAt: Long = 0L,
)

/**
 * A calendar entry: something done, planned or missed that is not a recorded gym workout
 * (recorded workouts are [WorkoutSession]s and appear in the calendar by themselves).
 */
@Serializable
data class ActivityEvent(
    val id: String,
    val type: ActivityType = ActivityType.OTHER,
    val title: String = "",
    val notes: String = "",
    /** Epoch millis, UTC. */
    val startsAt: Long,
    val localDate: String,
    val zoneId: String,
    val durationMin: Int? = null,
    val status: EventStatus = EventStatus.DONE,
    val allDay: Boolean = false,
    /** For planned strength events: the plan day this event stands for. */
    val plannedWorkoutId: String? = null,
    /** The recorded workout this event was linked to (soft reference). */
    val linkedSessionId: String? = null,
    /** Repeat rule, see [app.liftos.domain.calendar.Recurrence]; empty = does not repeat. */
    val recurrence: String = "",
    /** Dates (ISO) of occurrences of a repeating event that were moved, cancelled or edited on their own. */
    val recurrenceExceptions: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/**
 * A change to ONE planned occurrence of a plan day. The plan and the workout history are never
 * modified: the calendar just shows this occurrence differently.
 */
@Serializable
data class PlannedOverride(
    val id: String,
    val plannedWorkoutId: String,
    /** The date the plan scheduled the workout on (ISO). */
    val originalDate: String,
    val action: OverrideAction,
    /** For [OverrideAction.MOVED]: the date it was moved to (ISO). */
    val newDate: String? = null,
    /** Optional start time as minutes after midnight, for a moved workout. */
    val newStartMinutes: Int? = null,
    val note: String = "",
    val linkedSessionId: String? = null,
    val createdAt: Long = 0L,
)
