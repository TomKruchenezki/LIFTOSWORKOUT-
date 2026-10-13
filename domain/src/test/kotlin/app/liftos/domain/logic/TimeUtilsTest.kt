package app.liftos.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class TimeUtilsTest {
    private val jerusalem = ZoneId.of("Asia/Jerusalem")

    @Test
    fun `local date uses the recorded zone, not the device zone`() {
        // 2026-10-13 22:30 UTC is already 2026-10-14 in Jerusalem (UTC+3 in October) but still the 13th in UTC.
        val ms = java.time.Instant.parse("2026-10-13T22:30:00Z").toEpochMilli()
        assertEquals("2026-10-14", TimeUtils.localDateString(ms, jerusalem))
        assertEquals("2026-10-13", TimeUtils.localDateString(ms, ZoneId.of("UTC")))
    }

    @Test
    fun `stored instant and date are stable across the autumn daylight saving change`() {
        // Israel leaves summer time on Sunday 2026-10-25 at 02:00 (clocks go back to 01:00).
        val evening = TimeUtils.toEpochMs(LocalDate.of(2026, 10, 24), 23, 30, jerusalem)
        assertEquals("2026-10-24", TimeUtils.localDateString(evening, jerusalem))

        // 00:30 -> 03:30 on the 25th is 3 wall-clock hours but 4 real hours (one hour repeats).
        val start = TimeUtils.toEpochMs(LocalDate.of(2026, 10, 25), 0, 30, jerusalem)
        val end = TimeUtils.toEpochMs(LocalDate.of(2026, 10, 25), 3, 30, jerusalem)
        assertEquals(4 * 3_600_000L, end - start)
        // Both instants keep their own calendar date.
        assertEquals("2026-10-25", TimeUtils.localDateString(start, jerusalem))
        assertEquals("2026-10-25", TimeUtils.localDateString(end, jerusalem))
    }

    @Test
    fun `spring gap time is resolved forward instead of failing`() {
        // Israel enters summer time on Friday 2026-03-27 at 02:00 (02:00 -> 03:00): 02:30 does not exist.
        val ms = TimeUtils.toEpochMs(LocalDate.of(2026, 3, 27), 2, 30, jerusalem)
        assertEquals("2026-03-27", TimeUtils.localDateString(ms, jerusalem))
        assertEquals("03:30", Formatters.time(ms, jerusalem))
    }

    @Test
    fun `week starts on sunday in israel`() {
        val tue = LocalDate.of(2026, 10, 13)
        assertEquals(LocalDate.of(2026, 10, 11), TimeUtils.weekStart(tue))
        assertEquals(LocalDate.of(2026, 10, 17), TimeUtils.weekRange(tue).endInclusive)
        assertEquals(DayOfWeek.SUNDAY, TimeUtils.weekStart(tue).dayOfWeek)
    }

    @Test
    fun `month range covers the whole month`() {
        val r = TimeUtils.monthRange(LocalDate.of(2026, 2, 10))
        assertEquals(LocalDate.of(2026, 2, 1), r.start)
        assertEquals(LocalDate.of(2026, 2, 28), r.endInclusive)
    }

    @Test
    fun `unknown zone falls back to jerusalem`() {
        assertEquals(TimeUtils.DEFAULT_ZONE, TimeUtils.zone("Not/AZone"))
        assertEquals(TimeUtils.DEFAULT_ZONE, TimeUtils.zone(null))
    }
}
