package app.liftos.domain.analytics

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** An inclusive range of local calendar dates. */
data class DateRange(val start: LocalDate, val end: LocalDate) {
    init {
        require(!end.isBefore(start)) { "end before start" }
    }

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    val days: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt()

    /** The range of equal length that ends the day before this one starts. */
    fun previous(): DateRange = DateRange(start.minusDays(days.toLong()), start.minusDays(1))

    fun weeks(): Double = days / 7.0

    companion object {
        fun lastDays(days: Int, today: LocalDate): DateRange = DateRange(today.minusDays((days - 1).toLong()), today)

        fun weekOf(date: LocalDate, firstDay: DayOfWeek = DayOfWeek.SUNDAY): DateRange {
            val start = date.with(TemporalAdjusters.previousOrSame(firstDay))
            return DateRange(start, start.plusDays(6))
        }

        fun monthOf(date: LocalDate): DateRange =
            DateRange(date.withDayOfMonth(1), date.with(TemporalAdjusters.lastDayOfMonth()))
    }
}

/** Period filters offered next to every chart and report. */
enum class PeriodPreset(val labelHe: String, val days: Int?) {
    DAYS_7("7 ימים", 7),
    DAYS_30("30 ימים", 30),
    DAYS_90("90 ימים", 90),
    MONTHS_6("6 חודשים", 182),
    YEAR("שנה", 365),
    ALL("כל התקופה", null),
    CUSTOM("טווח מותאם", null),
}

data class Period(val preset: PeriodPreset = PeriodPreset.DAYS_30, val custom: DateRange? = null) {

    /**
     * Resolves to concrete dates. [earliest] is the first date that has data (for ALL); a CUSTOM
     * period without a range falls back to the last 30 days instead of failing.
     */
    fun resolve(today: LocalDate, earliest: LocalDate?): DateRange = when (preset) {
        PeriodPreset.ALL -> DateRange(minOf(earliest ?: today, today), today)
        PeriodPreset.CUSTOM -> custom ?: DateRange.lastDays(30, today)
        else -> DateRange.lastDays(preset.days!!, today)
    }
}

enum class Granularity { DAY, WEEK, MONTH }

object Buckets {

    /** Start of the bucket containing [date]. Weeks start on Sunday (Israel). */
    fun startOf(date: LocalDate, granularity: Granularity): LocalDate = when (granularity) {
        Granularity.DAY -> date
        Granularity.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        Granularity.MONTH -> date.withDayOfMonth(1)
    }

    fun endOf(start: LocalDate, granularity: Granularity): LocalDate = when (granularity) {
        Granularity.DAY -> start
        Granularity.WEEK -> start.plusDays(6)
        Granularity.MONTH -> start.with(TemporalAdjusters.lastDayOfMonth())
    }

    /** All bucket starts that touch [range], oldest first (empty buckets included so charts show gaps). */
    fun starts(range: DateRange, granularity: Granularity): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var cur = startOf(range.start, granularity)
        while (!cur.isAfter(range.end)) {
            out += cur
            cur = when (granularity) {
                Granularity.DAY -> cur.plusDays(1)
                Granularity.WEEK -> cur.plusWeeks(1)
                Granularity.MONTH -> cur.plusMonths(1)
            }
        }
        return out
    }

    /** A sensible bucket size for charting a range: days for a month, weeks for a year, months beyond. */
    fun granularityFor(range: DateRange): Granularity = when {
        range.days <= 35 -> Granularity.DAY
        range.days <= 400 -> Granularity.WEEK
        else -> Granularity.MONTH
    }
}
