package app.liftos.di

import app.liftos.domain.goals.GoalContext
import app.liftos.domain.model.Goal
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first

/**
 * Notices goals that were reached. It is called after the events that can change a goal's number (a
 * finished workout, a saved measurement, an edited goal), compares them with the user's logged data and
 * marks the goal as achieved. Nothing is checked in the background.
 */
class GoalTracker(private val c: AppContainer) {
    private val _achieved = MutableSharedFlow<Goal>(extraBufferCapacity = 8)

    /** Emits each goal once, right after it was marked as achieved. */
    val achieved: SharedFlow<Goal> = _achieved

    suspend fun check(): List<Goal> {
        val goals = c.goals.getAll()
        if (goals.none { it.status == app.liftos.domain.model.GoalStatus.ACTIVE }) return emptyList()
        val data = c.analytics.getData()
        val measurements = c.measurements.observeAllOnce()
        val today = java.time.LocalDate.now(c.zones.zone())
        val ctx = GoalContext(today, measurements, data.facts, data.sessions)
        val done = c.goals.markNewlyAchieved(ctx)
        done.forEach { g ->
            _achieved.tryEmit(g)
            if (c.settingsSnapshot().reminders.goalReachedEnabled) {
                app.liftos.service.Notifications.goalReached(c.app, g.title)
            }
        }
        return done
    }
}
