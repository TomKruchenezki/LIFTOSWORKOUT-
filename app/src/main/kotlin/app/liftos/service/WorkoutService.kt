package app.liftos.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.liftos.LiftOsApp
import app.liftos.domain.model.WorkoutSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that exists while a workout is active. It keeps the process alive when the
 * screen is off or another app is in front, shows the elapsed time / rest countdown in the
 * notification shade, and ends rest periods on time (with a wake lock while a rest is running and
 * an AlarmManager backup in [RestAlarmReceiver]).
 *
 * The rest state itself lives in the database (absolute timestamps), so the service is stateless:
 * if it is killed and restarted it simply reads the workout again.
 */
class WorkoutService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private var restJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastRestEnd: Long = -1L
    private var lastSessionId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val type = if (android.os.Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            ServiceCompat.startForeground(this, Notifications.ID_WORKOUT, Notifications.workoutOngoing(this, null, System.currentTimeMillis()), type)
        } catch (_: Exception) {
            // Starting a foreground service can be refused by the OS in rare background situations.
            stopSelf()
            return START_NOT_STICKY
        }
        if (observeJob == null) {
            val container = (application as LiftOsApp).container
            observeJob = scope.launch {
                container.workouts.observeActive().collect { onSession(container, it) }
            }
        }
        return START_STICKY
    }

    private fun onSession(container: app.liftos.di.AppContainer, session: WorkoutSession?) {
        if (session == null) {
            lastSessionId?.let { RestAlarmReceiver.cancel(this, it) }
            releaseWakeLock()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        lastSessionId = session.id
        val now = System.currentTimeMillis()
        getSystemService(NotificationManager::class.java).notify(Notifications.ID_WORKOUT, Notifications.workoutOngoing(this, session, now))

        val rest = session.rest
        val endsAt = rest?.endsAt
        if (endsAt == null) {
            restJob?.cancel(); restJob = null
            lastRestEnd = -1L
            releaseWakeLock()
            RestAlarmReceiver.cancel(this, session.id)
            Notifications.cancelRestFinished(this)
            return
        }
        if (endsAt == lastRestEnd) return // same rest already being tracked
        lastRestEnd = endsAt
        restJob?.cancel()
        Notifications.cancelRestFinished(this)
        RestAlarmReceiver.schedule(this, session.id, endsAt)
        acquireWakeLockUntil(endsAt)
        restJob = scope.launch {
            val wait = endsAt - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            RestFinisher.finish(applicationContext, container, session.id, endsAt)
            releaseWakeLock()
        }
    }

    private fun acquireWakeLockUntil(endsAt: Long) {
        releaseWakeLock()
        val pm = getSystemService(PowerManager::class.java) ?: return
        val timeout = (endsAt - System.currentTimeMillis()).coerceAtLeast(0L) + 15_000L
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "liftos:rest").apply {
            setReferenceCounted(false)
            acquire(timeout)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, WorkoutService::class.java))
            } catch (_: Exception) {
                // Not allowed to start from the background right now; the app will retry when it is shown.
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WorkoutService::class.java))
        }
    }
}
