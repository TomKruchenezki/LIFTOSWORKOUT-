package app.liftos.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.liftos.LiftOsApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Backup finisher for the rest timer. The foreground service normally ends the rest, but if the
 * system killed it (aggressive battery managers) this alarm still fires at the right time.
 * Both paths call WorkoutRepository.claimRestFinish, so the user is alerted exactly once.
 */
class RestAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
        val endsAt = intent.getLongExtra(EXTRA_ENDS_AT, 0L)
        if (endsAt <= 0L) return
        val pending = goAsync()
        val container = (context.applicationContext as LiftOsApp).container
        CoroutineScope(Dispatchers.Default).launch {
            try {
                RestFinisher.finish(context.applicationContext, container, sessionId, endsAt)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_ENDS_AT = "ends_at"

        private fun pendingIntent(context: Context, sessionId: String, endsAt: Long): PendingIntent =
            PendingIntent.getBroadcast(
                context, 7001,
                Intent(context, RestAlarmReceiver::class.java)
                    .putExtra(EXTRA_SESSION_ID, sessionId)
                    .putExtra(EXTRA_ENDS_AT, endsAt),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        fun schedule(context: Context, sessionId: String, endsAt: Long) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            // Inexact-but-doze-tolerant: needs no special permission and is accurate to a few seconds.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, pendingIntent(context, sessionId, endsAt))
        }

        fun cancel(context: Context, sessionId: String) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            am.cancel(pendingIntent(context, sessionId, 0L))
        }
    }
}
