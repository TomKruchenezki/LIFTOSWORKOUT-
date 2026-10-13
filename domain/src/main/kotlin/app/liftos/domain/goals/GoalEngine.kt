package app.liftos.domain.goals

import app.liftos.domain.analytics.DateRange
import app.liftos.domain.analytics.ExerciseAnalytics
import app.liftos.domain.analytics.SessionFact
import app.liftos.domain.analytics.SetFact
import app.liftos.domain.logic.Formatters
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.model.BodyMeasurement
import app.liftos.domain.model.Goal
import app.liftos.domain.model.GoalStatus
import app.liftos.domain.model.GoalType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Body fields a goal (or a chart) can refer to. [key] is stored in [Goal.metricKey]. */
enum class BodyField(val key: String, val labelHe: String, val unit: String) {
    WEIGHT("weight", "משקל גוף", "ק״ג"),
    WAIST("waist", "היקף מותניים", "ס״מ"),
    CHEST("chest", "היקף חזה", "ס״מ"),
    ARM("arm", "היקף זרוע", "ס״מ"),
    THIGH("thigh", "היקף ירך", "ס״מ"),
    HIPS("hips", "היקף אגן", "ס״מ"),
    NECK("neck", "היקף צוואר", "ס״מ"),
    SHOULDERS("shoulders", "היקף כתפיים", "ס״מ"),
    BODY_FAT("bodyfat", "אחוז שומן", "%"),
    ;

    fun valueOf(m: BodyMeasurement): Double? = when (this) {
        WEIGHT -> m.weightKg
        WAIST -> m.waistCm
        CHEST -> m.chestCm
        ARM -> m.armCm
        THIGH -> m.thighCm
        HIPS -> m.hipsCm
        NECK -> m.neckCm
        SHOULDERS -> m.shouldersCm
        BODY_FAT -> m.bodyFatPct
    }

    companion object {
        fun fromKey(key: String): BodyField? = entries.firstOrNull { it.key == key }
    }
}

/** Everything a goal may be computed from. All of it is the user's own logged data. */
data class GoalContext(
    val today: LocalDate,
    val measurements: List<BodyMeasurement> = emptyList(),
    val facts: List<SetFact> = emptyList(),
    val sessions: List<SessionFact> = emptyList(),
)

data class GoalProgress(
    val goalId: String,
    /** Current value, or null when there is no data to compute one. */
    val current: Double?,
    /** 0..100, or null without data. Never invented. */
    val percent: Double?,
    val achieved: Boolean,
    val remaining: Double?,
    val daysLeft: Long?,
    /** Where the user should be today if progress were even; null without a deadline. */
    val expectedPercent: Double?,
    /** null when it can not be judged (no data / no deadline). */
    val onTrack: Boolean?,
    val message: String,
)

object GoalEngine {
    private const val TOLERANCE = 1e-9

    fun currentValue(goal: Goal, ctx: GoalContext): Double? = when (goal.type) {
        GoalType.BODY_WEIGHT -> latestBody(ctx, BodyField.WEIGHT)
        GoalType.BODY_MEASURE -> {
            val field = BodyField.fromKey(goal.metricKey)
            if (field != null) latestBody(ctx, field) else latestCustom(ctx, goal.metricKey)
        }
        GoalType.EXERCISE_WEIGHT ->
            ExerciseAnalytics.overview(ctx.facts, goal.metricKey, ctx.today)?.maxWeight?.value
        GoalType.EXERCISE_REPS -> {
            val o = ExerciseAnalytics.overview(ctx.facts, goal.metricKey, ctx.today)
            (o?.maxRepsUnloaded?.value ?: o?.sessions?.mapNotNull { it.maxReps }?.maxOrNull()?.toDouble())
        }
        GoalType.WORKOUTS_PER_MONTH ->
            ctx.sessions.count { it.date in DateRange.monthOf(ctx.today) }.toDouble()
        GoalType.WORKOUTS_PER_WEEK ->
            ctx.sessions.count { it.date in DateRange.weekOf(ctx.today) }.toDouble()
        GoalType.PROGRAM_WEEKS -> completedProgramWeeks(goal, ctx).toDouble()
        GoalType.CUSTOM -> goal.currentValue
    }

    private fun latestBody(ctx: GoalContext, field: BodyField): Double? =
        ctx.measurements.filter { field.valueOf(it) != null }.maxByOrNull { it.measuredAt }?.let { field.valueOf(it) }

    private fun latestCustom(ctx: GoalContext, metricId: String): Double? =
        ctx.measurements.filter { it.customValues[metricId] != null }.maxByOrNull { it.measuredAt }?.customValues?.get(metricId)

    /** Weeks since the goal started in which at least [Goal.metricKey] (default 1) workouts were done. */
    private fun completedProgramWeeks(goal: Goal, ctx: GoalContext): Int {
        val start = TimeUtils.parseDate(goal.startDate) ?: return 0
        val perWeek = goal.metricKey.toIntOrNull()?.coerceAtLeast(1) ?: 1
        var weekStart = TimeUtils.weekStart(start)
        val currentWeek = TimeUtils.weekStart(ctx.today)
        var done = 0
        while (weekStart.isBefore(currentWeek)) { // only finished weeks count
            val range = DateRange(weekStart, weekStart.plusDays(6))
            if (ctx.sessions.count { it.date in range } >= perWeek) done++
            weekStart = weekStart.plusWeeks(1)
        }
        // The running week counts once it already meets the minimum.
        val running = DateRange(currentWeek, currentWeek.plusDays(6))
        if (!currentWeek.isBefore(TimeUtils.weekStart(start)) && ctx.sessions.count { it.date in running } >= perWeek) done++
        return done
    }

    fun progress(goal: Goal, ctx: GoalContext): GoalProgress {
        val current = currentValue(goal, ctx)
        val start = goal.startValue
        val target = goal.targetValue
        val achieved = current != null && if (goal.isDecreasing) current <= target + TOLERANCE else current >= target - TOLERANCE
        val percent = current?.let { c ->
            val span = target - start
            when {
                achieved -> 100.0
                Math.abs(span) < TOLERANCE -> 0.0
                else -> ((c - start) / span * 100.0).coerceIn(0.0, 100.0)
            }
        }
        val remaining = current?.let { if (goal.isDecreasing) (it - target).coerceAtLeast(0.0) else (target - it).coerceAtLeast(0.0) }

        val startDate = TimeUtils.parseDate(goal.startDate)
        val targetDate = goal.targetDate?.let { TimeUtils.parseDate(it) }
        val daysLeft = targetDate?.let { ChronoUnit.DAYS.between(ctx.today, it) }
        val expected = if (startDate != null && targetDate != null && targetDate.isAfter(startDate)) {
            val total = ChronoUnit.DAYS.between(startDate, targetDate).toDouble()
            val elapsed = ChronoUnit.DAYS.between(startDate, ctx.today).toDouble().coerceIn(0.0, total)
            elapsed / total * 100.0
        } else null
        val onTrack = if (percent != null && expected != null) percent + 10.0 >= expected else null

        val message = when {
            goal.status == GoalStatus.ACHIEVED || achieved -> "היעד הושג!"
            current == null -> when (goal.type) {
                GoalType.CUSTOM -> "עדכן ערך נוכחי כדי לראות התקדמות."
                else -> "אין עדיין נתונים לחישוב. התקדמות תוצג אחרי שיתועדו נתונים רלוונטיים."
            }
            onTrack == false -> "מאחורי הקצב המתוכנן לפי התאריך שנקבע."
            onTrack == true -> "בקצב המתוכנן."
            else -> "נותרו ${Formatters.number(remaining, 1)} ${goal.unit} ליעד."
        }
        return GoalProgress(goal.id, current, percent, achieved, remaining, daysLeft, expected, onTrack, message)
    }

    /** Goals that just reached their target and should be marked achieved (only ACTIVE goals qualify). */
    fun newlyAchieved(goals: List<Goal>, ctx: GoalContext): List<Pair<Goal, GoalProgress>> =
        goals.filter { it.status == GoalStatus.ACTIVE }
            .map { it to progress(it, ctx) }
            .filter { it.second.achieved && it.second.current != null }

    /** A sensible starting value when creating a goal, taken from the user's data (null if there is none). */
    fun suggestedStart(type: GoalType, metricKey: String, ctx: GoalContext): Double? {
        val probe = Goal(id = "", title = "", type = type, metricKey = metricKey, startValue = 0.0, targetValue = 0.0, startDate = ctx.today.toString())
        return when (type) {
            GoalType.WORKOUTS_PER_MONTH, GoalType.WORKOUTS_PER_WEEK, GoalType.PROGRAM_WEEKS -> 0.0
            GoalType.CUSTOM -> null
            else -> currentValue(probe, ctx)
        }
    }
}
