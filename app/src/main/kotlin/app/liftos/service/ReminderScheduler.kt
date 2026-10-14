package app.liftos.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.liftos.di.AppContainer
import app.liftos.domain.goals.BodyField
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.reminders.ReminderContext
import app.liftos.domain.reminders.ReminderPlanner
import app.liftos.domain.reminders.ReminderType
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime

/**
 * Turns the reminder settings into alarms: one per enabled reminder, always the NEXT occurrence only.
 * When an alarm fires, [ReminderReceiver] posts the notification (if still needed) and calls [rescheduleAll].
 *
 * Alarms are inexact and doze-tolerant (`setAndAllowWhileIdle`), so no exact-alarm permission is needed;
 * they may be a few minutes late when the phone is idle. They are re-created at app start and after a reboot.
 */
object ReminderScheduler {
    const val EXTRA_TYPE = "reminder_type"

    suspend fun rescheduleAll(context: Context, container: AppContainer) {
        val reminders = container.settings.observe().first().reminders
        val ctx = loadContext(container)
        val now = ZonedDateTime.now(container.zones.zone())
        ReminderType.entries.forEach { type ->
            schedule(context, type, ReminderPlanner.nextFire(type, reminders, now, ctx))
        }
    }

    suspend fun loadContext(container: AppContainer): ReminderContext {
        val settings = container.settings.observe().first()
        val plan = settings.activePlanId?.let { container.plans.observePlanDetail(it).first() }
        val measurements = container.measurements.observeAllOnce()
        val zone = container.zones.zone()
        return ReminderContext(
            plannedWeekdays = plan?.workouts?.mapNotNull { it.workout.weekday }?.toSet().orEmpty(),
            lastWeighInDate = measurements.filter { it.weightKg != null }.mapNotNull { TimeUtils.parseDate(it.localDate) }.maxOrNull(),
            lastMeasurementDate = measurements
                .filter { m -> BodyField.entries.any { it != BodyField.WEIGHT && it.valueOf(m) != null } }
                .mapNotNull { TimeUtils.parseDate(it.localDate) }.maxOrNull(),
            lastBackupDate = container.devicePrefs.lastBackupAt.first()?.let { TimeUtils.localDate(it, zone) },
            workoutDates = container.analytics.getData().sessions.map { it.date }.toSet(),
        )
    }

    private fun pendingIntent(context: Context, type: ReminderType): PendingIntent =
        PendingIntent.getBroadcast(
            context, type.requestCode,
            Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_TYPE, type.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun schedule(context: Context, type: ReminderType, at: ZonedDateTime?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, type)
        if (at == null) am.cancel(pi) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pi)
    }
}
