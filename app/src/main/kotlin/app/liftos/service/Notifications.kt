package app.liftos.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.liftos.MainActivity
import app.liftos.R
import app.liftos.domain.logic.RestTimerState
import app.liftos.domain.model.WorkoutSession

/** Notification channels and builders. Texts are Hebrew; the app is RTL. */
object Notifications {
    const val CHANNEL_WORKOUT = "workout_in_progress"
    const val CHANNEL_REST_DONE = "rest_finished"
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_ACHIEVEMENTS = "achievements"
    const val ID_WORKOUT = 1001
    const val ID_REST_DONE = 1002
    const val ID_GOAL = 3001
    const val ID_RECORD = 3002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_WORKOUT, "אימון פעיל", NotificationManager.IMPORTANCE_LOW).apply {
                description = "מציג את האימון הפעיל וטיימר המנוחה"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REST_DONE, "סיום זמן מנוחה", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "התראה כשזמן המנוחה בין הסטים הסתיים"
                enableVibration(false) // vibration is triggered manually so the in-app switch is respected
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "תזכורות", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "תזכורות לאימון, לשקילה, למדידת היקפים, לסיכום שבועי ולגיבוי"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ACHIEVEMENTS, "יעדים ושיאים", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "הודעה כשיעד הושג או נקבע שיא אישי חדש"
            },
        )
    }

    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The ongoing notification of the foreground service: elapsed time, or a rest countdown. */
    fun workoutOngoing(context: Context, session: WorkoutSession?, nowMs: Long): android.app.Notification {
        val b = NotificationCompat.Builder(context, CHANNEL_WORKOUT)
            .setSmallIcon(R.drawable.ic_stat_workout)
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        val rest: RestTimerState? = session?.rest
        when {
            session == null -> b.setContentTitle("LiftOS").setContentText("אימון פעיל")
            rest != null && !rest.isPaused && (rest.endsAt ?: 0L) > nowMs -> {
                b.setContentTitle("מנוחה · ${session.title}")
                    .setContentText(rest.label.ifBlank { "זמן מנוחה" })
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setWhen(rest.endsAt ?: nowMs)
                    .setShowWhen(true)
            }
            rest != null && rest.isPaused -> {
                b.setContentTitle("מנוחה מושהית · ${session.title}").setContentText(rest.label)
            }
            else -> {
                b.setContentTitle(session.title)
                    .setContentText("אימון פעיל")
                    .setUsesChronometer(true)
                    .setWhen(session.startedAt)
                    .setShowWhen(true)
            }
        }
        return b.build()
    }

    fun restFinished(context: Context, label: String): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_REST_DONE)
            .setSmallIcon(R.drawable.ic_stat_workout)
            .setContentTitle("המנוחה הסתיימה")
            .setContentText(label.ifBlank { "אפשר להמשיך לסט הבא" })
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setTimeoutAfter(5 * 60 * 1000L)
            .build()

    fun postRestFinished(context: Context, label: String) {
        if (!canPostNotifications(context)) return
        ensureChannels(context)
        context.getSystemService(NotificationManager::class.java).notify(ID_REST_DONE, restFinished(context, label))
    }

    fun cancelRestFinished(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID_REST_DONE)
    }

    /** A plain, tap-to-open notification. Respects the system notification settings and Do Not Disturb. */
    fun postSimple(context: Context, channel: String, id: Int, title: String, text: String) {
        if (!canPostNotifications(context)) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_workout)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, n)
    }

    fun goalReached(context: Context, goalTitle: String) =
        postSimple(context, CHANNEL_ACHIEVEMENTS, ID_GOAL, "יעד הושג!", goalTitle)

    fun newRecord(context: Context, text: String) =
        postSimple(context, CHANNEL_ACHIEVEMENTS, ID_RECORD, "שיא אישי חדש!", text)
}
