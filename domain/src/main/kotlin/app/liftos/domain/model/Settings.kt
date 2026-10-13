package app.liftos.domain.model

import kotlinx.serialization.Serializable

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
) {
    fun defaultRestFor(type: ExerciseType): Int =
        if (type == ExerciseType.COMPOUND) defaultRestCompoundSec else defaultRestIsolationSec
}
