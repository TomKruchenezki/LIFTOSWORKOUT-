package app.liftos.service

import android.content.Context
import app.liftos.di.AppContainer
import kotlinx.coroutines.flow.first

/** Ends a rest period: exactly one caller wins the claim and alerts the user. */
object RestFinisher {
    suspend fun finish(context: Context, container: AppContainer, sessionId: String, endsAt: Long) {
        val session = container.workouts.getDetail(sessionId)?.session ?: return
        val label = session.rest?.takeIf { it.endsAt == endsAt }?.label.orEmpty()
        if (!container.workouts.claimRestFinish(sessionId, endsAt)) return
        val settings = container.settings.observe().first()
        if (settings.restNotificationEnabled) Notifications.postRestFinished(context, label)
        if (settings.vibrateOnRestEnd) Haptics.restFinished(context)
    }
}
