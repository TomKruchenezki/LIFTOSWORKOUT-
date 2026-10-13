package app.liftos.domain.model

import kotlinx.serialization.Serializable

/**
 * Which reminders are on. Everything defaults to OFF except the event-driven celebrations; the
 * user turns each reminder on separately and nothing is scheduled until they do.
 */
@Serializable
data class ReminderSettings(
    val workoutEnabled: Boolean = false,
    /** Time of day ("HH:mm") for the reminder on days a workout is planned. */
    val workoutTime: String = "17:30",
    val weighInEnabled: Boolean = false,
    /** ISO weekdays (1 = Monday ... 7 = Sunday) on which to remind about weighing in. */
    val weighInWeekdays: List<Int> = listOf(7),
    val weighInTime: String = "07:30",
    val measureEnabled: Boolean = false,
    /** Remind about circumference measurements every N days. */
    val measureEveryDays: Int = 14,
    val measureTime: String = "08:00",
    val weeklySummaryEnabled: Boolean = false,
    /** ISO weekday for the weekly summary (default Saturday evening). */
    val weeklySummaryWeekday: Int = 6,
    val weeklySummaryTime: String = "20:00",
    val backupEnabled: Boolean = false,
    val backupEveryDays: Int = 14,
    val goalReachedEnabled: Boolean = true,
    val newRecordEnabled: Boolean = true,
) {
    val anyScheduled: Boolean
        get() = workoutEnabled || weighInEnabled || measureEnabled || weeklySummaryEnabled || backupEnabled
}

@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val effortMode: EffortMode = EffortMode.RIR,
    val vibrateOnSetComplete: Boolean = true,
    val vibrateOnRestEnd: Boolean = true,
    val restNotificationEnabled: Boolean = true,
    val autoStartRest: Boolean = true,
    val keepScreenOn: Boolean = true,
    val defaultRestCompoundSec: Int = 150,
    val defaultRestIsolationSec: Int = 90,
    val activePlanId: String? = null,
    /** Overrides the plan's own weekly target on the dashboard when > 0. */
    val weeklyGoalWorkouts: Int = 0,
    val language: String = "he",
    val reminders: ReminderSettings = ReminderSettings(),
) {
    fun defaultRestFor(type: ExerciseType): Int =
        if (type == ExerciseType.COMPOUND) defaultRestCompoundSec else defaultRestIsolationSec
}
