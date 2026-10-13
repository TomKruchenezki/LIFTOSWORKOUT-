package app.liftos.domain.body

import app.liftos.domain.analytics.DateRange
import app.liftos.domain.goals.BodyField
import app.liftos.domain.logic.BodyTrends
import app.liftos.domain.logic.Formatters
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.model.BodyMeasurement
import app.liftos.domain.model.MeasurementCondition
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.sqrt

data class BodyPoint(
    val date: LocalDate,
    val value: Double,
    val measurementId: String,
    val conditions: List<MeasurementCondition> = emptyList(),
)

/** Difference between two values. [percent] is null when the base is zero. */
data class ValueChange(val from: Double, val to: Double) {
    val delta: Double get() = to - from
    val percent: Double? get() = if (from != 0.0) delta / from * 100.0 else null
}

data class FieldComparison(val field: BodyField, val first: Double?, val second: Double?) {
    val change: ValueChange? get() = if (first != null && second != null) ValueChange(first, second) else null
}

data class PeriodAverage(val range: DateRange, val average: Double?, val count: Int)

data class PeriodComparisonResult(val field: BodyField, val a: PeriodAverage, val b: PeriodAverage) {
    val change: ValueChange? get() = if (a.average != null && b.average != null) ValueChange(a.average, b.average) else null
}

enum class TrendDirection { RISING, FALLING, STABLE, UNCLEAR }

/**
 * Separates a real trend from day-to-day noise. [slopePerWeek] comes from a least-squares line through
 * the last [windowDays] days; [noise] is the spread of the readings around that line.
 */
data class BodyTrend(
    val field: BodyField,
    val direction: TrendDirection,
    val slopePerWeek: Double?,
    val noise: Double?,
    val points: Int,
    val message: String,
)

object BodyAnalytics {
    const val CAVEAT = "המדידות לא מבדילות בין שומן, שריר ונוזלים – שינוי במספר לבדו לא אומר מה השתנה בגוף."

    /** Smallest change (in the field's unit) that can matter, below which a trend is called stable. */
    private fun minMeaningful(field: BodyField) = when (field) {
        BodyField.WEIGHT -> 0.3
        BodyField.BODY_FAT -> 0.5
        else -> 0.4
    }

    fun series(measurements: List<BodyMeasurement>, field: BodyField, range: DateRange? = null): List<BodyPoint> =
        measurements.mapNotNull { m ->
            val v = field.valueOf(m) ?: return@mapNotNull null
            val d = TimeUtils.parseDate(m.localDate) ?: return@mapNotNull null
            if (range != null && d !in range) null else BodyPoint(d, v, m.id, m.conditions)
        }.sortedBy { it.date }

    fun customSeries(measurements: List<BodyMeasurement>, metricId: String, range: DateRange? = null): List<BodyPoint> =
        measurements.mapNotNull { m ->
            val v = m.customValues[metricId] ?: return@mapNotNull null
            val d = TimeUtils.parseDate(m.localDate) ?: return@mapNotNull null
            if (range != null && d !in range) null else BodyPoint(d, v, m.id, m.conditions)
        }.sortedBy { it.date }

    /** Trailing moving average over [windowDays] calendar days. */
    fun movingAverage(points: List<BodyPoint>, windowDays: Int = 7): List<BodyTrends.Point> =
        BodyTrends.movingAverage(points.map { BodyTrends.Point(it.date, it.value) }, windowDays)

    /** Field-by-field comparison of two measurements (absolute and percent change). */
    fun compare(a: BodyMeasurement, b: BodyMeasurement): List<FieldComparison> =
        BodyField.entries.map { FieldComparison(it, it.valueOf(a), it.valueOf(b)) }
            .filter { it.first != null || it.second != null }

    /** Average of a field in two date ranges, e.g. this month versus last month. */
    fun comparePeriods(measurements: List<BodyMeasurement>, field: BodyField, a: DateRange, b: DateRange): PeriodComparisonResult {
        fun avg(r: DateRange): PeriodAverage {
            val values = series(measurements, field, r).map { it.value }
            return PeriodAverage(r, values.takeIf { it.isNotEmpty() }?.average(), values.size)
        }
        return PeriodComparisonResult(field, avg(a), avg(b))
    }

    fun trend(measurements: List<BodyMeasurement>, field: BodyField, today: LocalDate, windowDays: Int = 28): BodyTrend {
        val window = DateRange.lastDays(windowDays, today)
        val pts = series(measurements, field, window)
        val spanDays = if (pts.size >= 2) ChronoUnit.DAYS.between(pts.first().date, pts.last().date) else 0
        if (pts.size < 4 || spanDays < 14) {
            return BodyTrend(field, TrendDirection.UNCLEAR, null, null, pts.size, "נדרשות לפחות 4 מדידות על פני שבועיים כדי להעריך מגמה. $CAVEAT")
        }
        val x = pts.map { ChronoUnit.DAYS.between(pts.first().date, it.date).toDouble() }
        val y = pts.map { it.value }
        val xm = x.average()
        val ym = y.average()
        val sxx = x.sumOf { (it - xm) * (it - xm) }
        val slope = if (sxx == 0.0) 0.0 else x.indices.sumOf { (x[it] - xm) * (y[it] - ym) } / sxx
        val intercept = ym - slope * xm
        val residuals = x.indices.map { y[it] - (intercept + slope * x[it]) }
        val noise = sqrt(residuals.sumOf { it * it } / (pts.size - 2).coerceAtLeast(1))
        val change = slope * spanDays
        val significant = abs(change) >= minMeaningful(field) && abs(change) > 1.5 * noise
        val perWeek = slope * 7
        val direction = when {
            !significant -> TrendDirection.STABLE
            slope > 0 -> TrendDirection.RISING
            else -> TrendDirection.FALLING
        }
        val unit = field.unit
        val message = when (direction) {
            TrendDirection.STABLE ->
                "אין מגמה ברורה מעבר לתנודות הרגילות (פיזור סביב ${Formatters.number(noise, 1)} $unit). $CAVEAT"
            TrendDirection.RISING ->
                "מגמת עלייה של כ-${Formatters.number(abs(perWeek), 2)} $unit בשבוע על פני ${spanDays} ימים. $CAVEAT"
            TrendDirection.FALLING ->
                "מגמת ירידה של כ-${Formatters.number(abs(perWeek), 2)} $unit בשבוע על פני ${spanDays} ימים. $CAVEAT"
            TrendDirection.UNCLEAR -> CAVEAT
        }
        return BodyTrend(field, direction, perWeek, noise, pts.size, message)
    }

    /** Typical size of a plausible jump between readings, used to ask "is this value right?". */
    private fun outlierTolerance(field: BodyField) = when (field) {
        BodyField.WEIGHT -> 5.0
        BodyField.BODY_FAT -> 5.0
        else -> 8.0
    }

    /**
     * Returns a warning when a newly typed value is far from the recent readings (probably a typo),
     * otherwise null. It never blocks saving: the user decides.
     */
    fun outlierWarning(measurements: List<BodyMeasurement>, field: BodyField, value: Double, onDate: LocalDate): String? {
        if (value <= 0.0) return "הערך ${Formatters.number(value)} אינו סביר."
        val recent = series(measurements, field)
            .filter { !it.date.isAfter(onDate) }
            .takeLast(5)
            .map { it.value }
        if (recent.isEmpty()) {
            val absurd = when (field) {
                BodyField.WEIGHT -> value < 25 || value > 300
                BodyField.BODY_FAT -> value < 2 || value > 60
                else -> value < 10 || value > 250
            }
            return if (absurd) "הערך ${Formatters.number(value)} ${field.unit} נראה חריג – כדאי לוודא." else null
        }
        val median = recent.sorted()[recent.size / 2]
        return if (abs(value - median) > outlierTolerance(field)) {
            "הערך ${Formatters.number(value)} ${field.unit} שונה מאוד מהמדידות האחרונות (סביב ${Formatters.number(median)}). כדאי לוודא שאין טעות הקלדה."
        } else null
    }
}
