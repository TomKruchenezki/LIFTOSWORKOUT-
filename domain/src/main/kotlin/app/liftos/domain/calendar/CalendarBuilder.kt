package app.liftos.domain.calendar

import app.liftos.domain.analytics.DateRange
import app.liftos.domain.analytics.SessionFact
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.model.ActivityEvent
import app.liftos.domain.model.ActivityType
import app.liftos.domain.model.EventStatus
import app.liftos.domain.model.OverrideAction
import app.liftos.domain.model.PlannedOverride
import java.time.Instant
import java.time.LocalDate

enum class CalendarItemKind {
    /** A recorded gym workout. */
    SESSION_DONE,

    /** A strength workout scheduled by the plan (or moved there) that has not happened yet. */
    STRENGTH_PLANNED,

    /** A scheduled workout that did not happen. */
    STRENGTH_MISSED,

    /** A scheduled workout the user intentionally skipped. */
    STRENGTH_SKIPPED,

    /** A rest day entry. */
    REST_DAY,

    /** Any other event (cardio, coach meeting, measurement, personal...). */
    EVENT,
}

data class CalendarItem(
    val key: String,
    val kind: CalendarItemKind,
    val date: LocalDate,
    val title: String,
    val subtitle: String = "",
    /** Minutes after midnight, null for untimed items. */
    val startMinutes: Int? = null,
    val durationMin: Int? = null,
    val eventType: ActivityType? = null,
    val eventStatus: EventStatus? = null,
    val sessionId: String? = null,
    val eventId: String? = null,
    val plannedWorkoutId: String? = null,
    /** For moved workouts: the date the plan had scheduled it. */
    val originalDate: LocalDate? = null,
    val moved: Boolean = false,
    val recurring: Boolean = false,
    /** True when the item was produced automatically (a past planned day with no workout). */
    val automatic: Boolean = false,
    val linkedSessionId: String? = null,
)

/** One workout day of the active plan. */
data class ScheduledWorkout(
    val plannedWorkoutId: String,
    val name: String,
    /** ISO weekday. */
    val weekday: Int,
)

object CalendarBuilder {
    /** Past planned days without a workout are only flagged as missed this far back (see docs). */
    const val AUTO_MISSED_DAYS = 28

    fun build(
        range: DateRange,
        today: LocalDate,
        schedule: List<ScheduledWorkout>,
        scheduleStart: LocalDate?,
        sessions: List<SessionFact>,
        events: List<ActivityEvent>,
        overrides: List<PlannedOverride>,
    ): List<CalendarItem> {
        val items = mutableListOf<CalendarItem>()
        val sessionsByDate = sessions.groupBy { it.date }

        // Recorded workouts.
        for (s in sessions) {
            if (s.date !in range) continue
            val zone = TimeUtils.DEFAULT_ZONE
            items += CalendarItem(
                key = "session:${s.id}",
                kind = CalendarItemKind.SESSION_DONE,
                date = s.date,
                title = s.title,
                subtitle = s.planName,
                startMinutes = minutesOfDay(s.startedAt, zone),
                durationMin = s.durationMs?.let { (it / 60_000).toInt() },
                sessionId = s.id,
                plannedWorkoutId = s.plannedWorkoutId,
            )
        }

        // Events, repeating ones expanded.
        for (e in events) {
            val rule = RecurrenceRule.parse(e.recurrence)
            val start = TimeUtils.parseDate(e.localDate) ?: continue
            val zone = TimeUtils.zone(e.zoneId)
            val minutes = if (e.allDay) null else minutesOfDay(e.startsAt, zone)
            val dates = if (rule == null) {
                listOf(start).filter { it in range }
            } else {
                rule.occurrences(start, range, Exceptions.parse(e.recurrenceExceptions))
            }
            for (d in dates) {
                items += CalendarItem(
                    key = "event:${e.id}:$d",
                    kind = if (e.type == ActivityType.REST) CalendarItemKind.REST_DAY else CalendarItemKind.EVENT,
                    date = d,
                    title = e.title.ifBlank { e.type.labelHe },
                    subtitle = if (e.title.isBlank()) "" else e.type.labelHe,
                    startMinutes = minutes,
                    durationMin = e.durationMin,
                    eventType = e.type,
                    eventStatus = e.status,
                    eventId = e.id,
                    plannedWorkoutId = e.plannedWorkoutId,
                    recurring = rule != null,
                    linkedSessionId = e.linkedSessionId,
                )
            }
        }

        // Workouts scheduled by the plan.
        val overrideByOccurrence = overrides.associateBy { it.plannedWorkoutId to it.originalDate }
        val movedIn = overrides.filter { it.action == OverrideAction.MOVED && it.newDate != null }
        var day = range.start
        while (!day.isAfter(range.end)) {
            for (w in schedule.filter { it.weekday == day.dayOfWeek.value }) {
                if (scheduleStart != null && day.isBefore(scheduleStart)) continue
                val ov = overrideByOccurrence[w.plannedWorkoutId to day.toString()]
                when (ov?.action) {
                    OverrideAction.MOVED -> Unit // shown on the new date below
                    OverrideAction.SKIPPED -> items += occurrence(w, day, CalendarItemKind.STRENGTH_SKIPPED, ov)
                    OverrideAction.MISSED -> items += occurrence(w, day, CalendarItemKind.STRENGTH_MISSED, ov)
                    null -> {
                        val doneThatDay = sessionsByDate[day].orEmpty().isNotEmpty()
                        when {
                            doneThatDay -> Unit // the recorded workout represents it
                            day >= today -> items += occurrence(w, day, CalendarItemKind.STRENGTH_PLANNED, null)
                            day >= today.minusDays(AUTO_MISSED_DAYS.toLong()) ->
                                items += occurrence(w, day, CalendarItemKind.STRENGTH_MISSED, null, automatic = true)
                        }
                    }
                }
            }
            day = day.plusDays(1)
        }
        // Occurrences moved onto a date inside the range.
        for (ov in movedIn) {
            val target = TimeUtils.parseDate(ov.newDate!!) ?: continue
            val original = TimeUtils.parseDate(ov.originalDate) ?: continue
            if (target !in range) continue
            val w = schedule.firstOrNull { it.plannedWorkoutId == ov.plannedWorkoutId } ?: continue
            val doneThatDay = sessionsByDate[target].orEmpty().isNotEmpty()
            if (doneThatDay) continue
            val kind = if (target >= today) CalendarItemKind.STRENGTH_PLANNED else CalendarItemKind.STRENGTH_MISSED
            items += occurrence(w, target, kind, ov, moved = true, original = original, automatic = kind == CalendarItemKind.STRENGTH_MISSED)
        }

        return items.sortedWith(compareBy({ it.date }, { it.startMinutes ?: -1 }, { it.kind.ordinal }, { it.title }))
    }

    private fun occurrence(
        w: ScheduledWorkout,
        date: LocalDate,
        kind: CalendarItemKind,
        ov: PlannedOverride?,
        moved: Boolean = false,
        original: LocalDate? = null,
        automatic: Boolean = false,
    ) = CalendarItem(
        key = "plan:${w.plannedWorkoutId}:${original ?: date}",
        kind = kind,
        date = date,
        title = w.name,
        subtitle = ov?.note.orEmpty(),
        startMinutes = ov?.newStartMinutes,
        plannedWorkoutId = w.plannedWorkoutId,
        originalDate = original ?: date,
        moved = moved,
        automatic = automatic,
        linkedSessionId = ov?.linkedSessionId,
    )

    fun minutesOfDay(epochMs: Long, zone: java.time.ZoneId): Int {
        val t = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalTime()
        return t.hour * 60 + t.minute
    }

    /** Items of one day, in display order. */
    fun forDay(items: List<CalendarItem>, date: LocalDate): List<CalendarItem> = items.filter { it.date == date }

    /** Number of items per date, for the month grid. */
    fun markers(items: List<CalendarItem>): Map<LocalDate, List<CalendarItemKind>> =
        items.groupBy { it.date }.mapValues { (_, l) -> l.map { it.kind }.distinct() }
}
