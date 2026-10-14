package app.liftos.service

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import app.liftos.LiftOsApp
import app.liftos.data.repo.MeasurementInput
import app.liftos.di.GoalTracker
import app.liftos.domain.model.Goal
import app.liftos.domain.model.GoalStatus
import app.liftos.domain.model.GoalType
import app.liftos.domain.reminders.ReminderType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate

/**
 * The background parts of the app on the Robolectric Android framework: the rest-finished alert, the ongoing
 * workout notification, the backup alarm and the reminder alarms. What this cannot show is how a real Samsung
 * device treats them under battery optimisation - that stays "NOT VERIFIED" in the audit.
 */
@RunWith(RobolectricTestRunner::class)
class ServiceBehaviorTest {
    private lateinit var app: LiftOsApp
    private val c get() = app.container

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<Application>() as LiftOsApp
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking { c.seeder.seedIfNeeded() }
    }

    private fun notifications() = shadowOf(app.getSystemService(NotificationManager::class.java))
    private fun alarms() = shadowOf(app.getSystemService(AlarmManager::class.java))

    /** Starts the first workout of the default plan and a 90 second rest; returns the session id and the rest end. */
    private fun workoutWithRest(): Pair<String, Long> = runBlocking {
        val plan = c.plans.getPlanDetail(c.plans.observePlans().first().first().id)!!
        val id = c.workouts.startFromPlan(plan.workouts.first().workout.id)
        c.workouts.startRest(id, 90, "סקוואט")
        id to c.workouts.getDetail(id)!!.session.rest!!.endsAt!!
    }

    private fun title(n: Notification) = n.extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    // ---- rest finished ------------------------------------------------------------------------------

    @Test
    fun finishing_a_rest_alerts_exactly_once_even_when_the_service_and_the_alarm_both_fire() {
        val (id, endsAt) = workoutWithRest()
        runBlocking {
            RestFinisher.finish(app, c, id, endsAt)
            RestFinisher.finish(app, c, id, endsAt) // the backup alarm arriving second
        }
        assertEquals(1, notifications().size())
        assertEquals("המנוחה הסתיימה", title(notifications().getNotification(Notifications.ID_REST_DONE)))
        assertNull("the rest is cleared", runBlocking { c.workouts.getDetail(id) }!!.session.rest)
    }

    @Test
    fun a_stale_end_time_does_not_end_a_rest_that_was_extended() {
        val (id, endsAt) = workoutWithRest()
        runBlocking { RestFinisher.finish(app, c, id, endsAt - 30_000) }
        assertEquals(0, notifications().size())
        assertNotNull("the real rest keeps running", runBlocking { c.workouts.getDetail(id) }!!.session.rest)
    }

    @Test
    fun the_rest_alert_respects_the_notification_setting_but_still_ends_the_rest() {
        val (id, endsAt) = workoutWithRest()
        runBlocking { c.settings.update { it.copy(restNotificationEnabled = false) } }
        runBlocking { RestFinisher.finish(app, c, id, endsAt) }
        assertEquals(0, notifications().size())
        assertNull(runBlocking { c.workouts.getDetail(id) }!!.session.rest)
    }

    @Test
    fun without_the_notification_permission_nothing_is_posted_and_nothing_crashes() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val (id, endsAt) = workoutWithRest()
        runBlocking { RestFinisher.finish(app, c, id, endsAt) }
        assertEquals(0, notifications().size())
        assertNull("the timer still ends in the app", runBlocking { c.workouts.getDetail(id) }!!.session.rest)
    }

    // ---- the ongoing notification -------------------------------------------------------------------

    @Test
    fun the_ongoing_notification_shows_the_rest_countdown_then_the_workout() {
        val (id, endsAt) = workoutWithRest()
        val session = runBlocking { c.workouts.getDetail(id) }!!.session

        val resting = Notifications.workoutOngoing(app, session, endsAt - 60_000)
        assertTrue(title(resting).startsWith("מנוחה"))
        assertTrue("it cannot be swiped away", resting.flags and Notification.FLAG_ONGOING_EVENT != 0)

        val afterRest = Notifications.workoutOngoing(app, session, endsAt + 1_000)
        assertEquals(session.title, title(afterRest))

        val noRest = Notifications.workoutOngoing(app, session.copy(rest = null), endsAt)
        assertEquals(session.title, title(noRest))
    }

    // ---- alarms ---------------------------------------------------------------------------------------

    @Test
    fun the_backup_rest_alarm_is_scheduled_for_the_end_time_and_can_be_cancelled() {
        val at = System.currentTimeMillis() + 90_000
        RestAlarmReceiver.schedule(app, "s1", at)
        assertEquals(1, alarms().scheduledAlarms.size)
        val alarm = alarms().scheduledAlarms[0]
        assertEquals(at, alarm.triggerAtTime)
        assertTrue("fires even in Doze", alarm.allowWhileIdle)

        RestAlarmReceiver.cancel(app, "s1")
        assertEquals(0, alarms().scheduledAlarms.size)
    }

    @Test
    fun no_reminder_alarms_exist_until_a_reminder_is_switched_on() {
        runBlocking { ReminderScheduler.rescheduleAll(app, c) }
        assertEquals(0, alarms().scheduledAlarms.size)
    }

    @Test
    fun switching_a_reminder_on_schedules_its_next_occurrence_and_off_cancels_it() {
        runBlocking { c.settings.update { it.copy(reminders = it.reminders.copy(weighInEnabled = true)) } }
        runBlocking { ReminderScheduler.rescheduleAll(app, c) }
        assertEquals(1, alarms().scheduledAlarms.size)
        val alarm = alarms().scheduledAlarms[0]
        assertTrue("in the future", alarm.triggerAtTime > System.currentTimeMillis())
        assertTrue(alarm.allowWhileIdle)

        runBlocking { c.settings.update { it.copy(reminders = it.reminders.copy(weighInEnabled = false)) } }
        runBlocking { ReminderScheduler.rescheduleAll(app, c) }
        assertEquals(0, alarms().scheduledAlarms.size)
    }

    @Test
    fun several_reminders_get_one_alarm_each() {
        runBlocking {
            c.settings.update {
                it.copy(reminders = it.reminders.copy(weighInEnabled = true, weeklySummaryEnabled = true, backupEnabled = true))
            }
            ReminderScheduler.rescheduleAll(app, c)
        }
        assertEquals(3, alarms().scheduledAlarms.size)
    }

    // ---- reminder content -------------------------------------------------------------------------------

    @Test
    fun the_weigh_in_reminder_posts_a_notification_when_nothing_was_weighed() {
        runBlocking {
            c.settings.update { it.copy(reminders = it.reminders.copy(weighInEnabled = true, weighInWeekdays = (1..7).toList())) }
            ReminderReceiver().post(app, c, ReminderType.WEIGH_IN)
        }
        assertEquals(1, notifications().size())
        assertEquals("שקילה", title(notifications().allNotifications[0]))
    }

    @Test
    fun a_reminder_that_is_switched_off_posts_nothing() {
        runBlocking { ReminderReceiver().post(app, c, ReminderType.BACKUP) }
        assertEquals(0, notifications().size())
    }

    @Test
    fun the_weekly_summary_text_reports_the_real_week() {
        runBlocking {
            c.settings.update { it.copy(reminders = it.reminders.copy(weeklySummaryEnabled = true)) }
            ReminderReceiver().post(app, c, ReminderType.WEEKLY_SUMMARY)
        }
        val n = notifications().allNotifications.singleOrNull()
        assertNotNull(n)
        assertEquals("הסיכום השבועי", title(n!!))
        assertEquals("השבוע לא תועדו אימונים.", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test
    fun the_notification_channels_respect_do_not_disturb() {
        Notifications.ensureChannels(app)
        val channels = app.getSystemService(NotificationManager::class.java).notificationChannels
        assertTrue("workout, rest alert, reminders and achievements: ${channels.map { it.id }}", channels.size >= 4)
        assertTrue("no channel overrides Do Not Disturb", channels.none { it.canBypassDnd() })
    }

    // ---- goal reached / new record ------------------------------------------------------------------------

    private fun weightGoal() = Goal(
        id = "", title = "ירידה ל-80", type = GoalType.BODY_WEIGHT, metricKey = "weight", unit = "ק״ג",
        startValue = 85.0, targetValue = 80.0, startDate = LocalDate.now().minusDays(30).toString(),
    )

    @Test
    fun reaching_a_goal_posts_one_notification_and_marks_it_achieved() {
        runBlocking {
            c.goals.create(weightGoal())
            c.measurements.add(MeasurementInput(weightKg = 79.5))
            val first = GoalTracker(c).check()
            val second = GoalTracker(c).check()
            assertEquals(1, first.size)
            assertTrue("a goal is celebrated once", second.isEmpty())
            assertEquals(GoalStatus.ACHIEVED, c.goals.getAll().single().status)
        }
        assertEquals(1, notifications().size())
        assertEquals("יעד הושג!", title(notifications().allNotifications[0]))
    }

    @Test
    fun the_goal_notification_has_its_own_switch_but_the_goal_is_still_marked() {
        runBlocking {
            c.settings.update { it.copy(reminders = it.reminders.copy(goalReachedEnabled = false)) }
            // The container keeps a cached copy of the settings that follows the database asynchronously.
            val deadline = System.currentTimeMillis() + 5_000
            while (c.settingsSnapshot().reminders.goalReachedEnabled && System.currentTimeMillis() < deadline) Thread.sleep(20)
            assertFalse(c.settingsSnapshot().reminders.goalReachedEnabled)
            c.goals.create(weightGoal())
            c.measurements.add(MeasurementInput(weightKg = 79.5))
            assertEquals(1, GoalTracker(c).check().size)
            assertEquals(GoalStatus.ACHIEVED, c.goals.getAll().single().status)
        }
        assertEquals(0, notifications().size())
    }

    @Test
    fun a_new_record_notification_uses_the_achievements_text() {
        Notifications.newRecord(app, "לחיצת חזה: 62.5 ק״ג")
        assertEquals(1, notifications().size())
        val n = notifications().getNotification(Notifications.ID_RECORD)
        assertEquals("שיא אישי חדש!", title(n))
        assertEquals("לחיצת חזה: 62.5 ק״ג", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }
}
