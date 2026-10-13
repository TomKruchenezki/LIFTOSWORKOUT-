package app.liftos.domain.model

import kotlinx.serialization.Serializable

/**
 * A personal goal. For data-backed types ([GoalType] other than CUSTOM) the current value is
 * computed live from the user's own logs; [currentValue] only holds the value the user typed for
 * CUSTOM goals (or the last stored value when no data exists).
 */
@Serializable
data class Goal(
    val id: String,
    val title: String,
    val type: GoalType,
    /**
     * What is measured: a body field ("weight", "waist", "chest", "arm", "thigh", "hips", "neck",
     * "shoulders", "bodyfat"), an exercise key, or a custom metric id. Empty when not applicable.
     */
    val metricKey: String = "",
    val unit: String = "",
    val startValue: Double,
    val targetValue: Double,
    val currentValue: Double? = null,
    /** ISO date. */
    val startDate: String,
    /** ISO date or null for an open-ended goal. */
    val targetDate: String? = null,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val achievedAt: Long? = null,
    val notes: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val category: GoalCategory get() = type.category

    /** True when the goal is about getting a number down (weight loss, smaller waist). */
    val isDecreasing: Boolean get() = targetValue < startValue
}

@Serializable
enum class GoalChangeKind(val labelHe: String) {
    CREATED("נוצר"),
    VALUE_UPDATED("עודכן ערך נוכחי"),
    TARGET_CHANGED("שונה ערך היעד"),
    DATE_CHANGED("שונה תאריך היעד"),
    STATUS_CHANGED("שונה סטטוס"),
    EDITED("נערך"),
}

@Serializable
data class GoalChange(
    val id: String,
    val goalId: String,
    val changedAt: Long,
    val kind: GoalChangeKind,
    val oldValue: String? = null,
    val newValue: String? = null,
    val note: String = "",
)

@Serializable
data class ProgressPhoto(
    val id: String,
    val takenAt: Long,
    val localDate: String,
    val zoneId: String,
    val angle: PhotoAngle = PhotoAngle.FRONT,
    /** File name inside the app's private photo folder (never a gallery URI). */
    val fileName: String,
    val weightKg: Double? = null,
    val notes: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/** Per-exercise user preference for the progression engine. */
@Serializable
data class ExercisePrefs(
    val exerciseKey: String,
    /** Smallest weight jump the user wants for this exercise; null = automatic by equipment. */
    val incrementKg: Double? = null,
    val updatedAt: Long = 0L,
)
