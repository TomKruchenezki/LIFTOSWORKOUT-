package app.liftos.ui.nav

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val PLANS = "plans"
    const val LIBRARY = "library"
    const val HISTORY = "history"
    const val BODY = "body"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"
    const val RECORDS = "records"
    const val GOALS = "goals"
    const val RECOVERY = "recovery"
    const val PHOTOS = "photos"
    const val AI_EXPORT = "aiExport"

    const val PLAN = "plan/{planId}"
    const val PLAN_WORKOUT = "planWorkout/{workoutId}"
    const val WORKOUT = "workout/{sessionId}"
    const val SUMMARY = "summary/{sessionId}"
    const val EXERCISE = "exercise/{exerciseId}"
    const val EXERCISE_EDIT = "exerciseEdit/{exerciseId}"
    const val EXERCISE_ANALYTICS = "exerciseAnalytics/{key}"
    const val PHOTO_COMPARE = "photoCompare/{a}/{b}"
    const val AI_EXPORT_SESSION = "aiExport/{sessionId}"

    const val NEW = "new"

    fun plan(id: String) = "plan/${Uri.encode(id)}"
    fun planWorkout(id: String) = "planWorkout/${Uri.encode(id)}"
    fun workout(id: String) = "workout/${Uri.encode(id)}"
    fun summary(id: String) = "summary/${Uri.encode(id)}"
    fun exercise(id: String) = "exercise/${Uri.encode(id)}"
    fun exerciseEdit(id: String) = "exerciseEdit/${Uri.encode(id)}"
    fun exerciseAnalytics(key: String) = "exerciseAnalytics/${Uri.encode(key)}"
    fun aiExport(sessionId: String) = "aiExport/${Uri.encode(sessionId)}"
    fun photoCompare(a: String, b: String) = "photoCompare/${Uri.encode(a)}/${Uri.encode(b)}"

    val topLevel = setOf(HOME, PLANS, PROGRESS, HISTORY, BODY)
}
