package app.liftos.ui.nav

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val PLANS = "plans"
    const val LIBRARY = "library"
    const val HISTORY = "history"
    const val BODY = "body"
    const val SETTINGS = "settings"

    const val PLAN = "plan/{planId}"
    const val PLAN_WORKOUT = "planWorkout/{workoutId}"
    const val WORKOUT = "workout/{sessionId}"
    const val SUMMARY = "summary/{sessionId}"
    const val EXERCISE = "exercise/{exerciseId}"
    const val EXERCISE_EDIT = "exerciseEdit/{exerciseId}"

    const val NEW = "new"

    fun plan(id: String) = "plan/${Uri.encode(id)}"
    fun planWorkout(id: String) = "planWorkout/${Uri.encode(id)}"
    fun workout(id: String) = "workout/${Uri.encode(id)}"
    fun summary(id: String) = "summary/${Uri.encode(id)}"
    fun exercise(id: String) = "exercise/${Uri.encode(id)}"
    fun exerciseEdit(id: String) = "exerciseEdit/${Uri.encode(id)}"

    val topLevel = setOf(HOME, PLANS, LIBRARY, HISTORY, BODY)
}
