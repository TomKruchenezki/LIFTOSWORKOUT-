package app.liftos.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.liftos.LiftOsApp
import app.liftos.di.AppContainer
import app.liftos.domain.analytics.DateRange
import app.liftos.domain.analytics.VolumeAnalytics
import app.liftos.domain.logic.Formatters
import app.liftos.domain.reminders.ReminderPlanner
import app.liftos.domain.reminders.ReminderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Fires when a reminder alarm goes off: posts the notification if it is still useful, then plans the next one. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(ReminderScheduler.EXTRA_TYPE)?.let { n -> ReminderType.entries.firstOrNull { it.name == n } } ?: return
        val pending = goAsync()
        val app = context.applicationContext
        val container = (app as LiftOsApp).container
        CoroutineScope(Dispatchers.Default).launch {
            try {
                post(app, container, type)
            } finally {
                runCatching { ReminderScheduler.rescheduleAll(app, container) }
                pending.finish()
            }
        }
    }

    private suspend fun post(context: Context, container: AppContainer, type: ReminderType) {
        val settings = container.settings.observe().first()
        val ctx = ReminderScheduler.loadContext(container)
        val today = LocalDate.now(container.zones.zone())
        if (!ReminderPlanner.shouldPost(type, settings.reminders, today, ctx)) return
        val (title, text) = when (type) {
            ReminderType.WORKOUT -> {
                val plan = settings.activePlanId?.let { container.plans.observePlanDetail(it).first() }
                val name = plan?.workouts?.firstOrNull { it.workout.weekday == today.dayOfWeek.value }?.workout?.name
                "זמן לאימון" to (if (name != null) "היום באימון: $name" else "יש היום אימון בתוכנית")
            }
            ReminderType.WEIGH_IN -> "שקילה" to "עוד לא תיעדת משקל היום."
            ReminderType.MEASURE -> "מדידת היקפים" to "הגיע הזמן למדוד היקפים ואחוז שומן, אם אתה עוקב אחריהם."
            ReminderType.WEEKLY_SUMMARY -> {
                val data = container.analytics.getData()
                val stats = VolumeAnalytics.periodStats(data.facts, data.sessions, DateRange.weekOf(today))
                "הסיכום השבועי" to (
                    if (stats.workouts == 0) "השבוע לא תועדו אימונים."
                    else "השבוע: ${stats.workouts} אימונים, ${stats.workingSets} סטי עבודה, נפח ${Formatters.thousands(stats.volumeKg)} ק״ג-חזרות."
                    )
            }
            ReminderType.BACKUP -> "גיבוי נתונים" to "כדאי לשמור גיבוי של האימונים והמדדים שלך (הגדרות ← גיבוי)."
        }
        Notifications.postSimple(context, Notifications.CHANNEL_REMINDERS, 4000 + type.ordinal, title, text)
    }
}

/** Re-creates the reminder alarms after a reboot or an app update (the system forgets alarms then). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val app = context.applicationContext
        val container = (app as LiftOsApp).container
        CoroutineScope(Dispatchers.Default).launch {
            try {
                ReminderScheduler.rescheduleAll(app, container)
            } finally {
                pending.finish()
            }
        }
    }
}
