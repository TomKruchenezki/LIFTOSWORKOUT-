package app.liftos.domain.export

import app.liftos.domain.TestData
import app.liftos.domain.logic.BodyTrends
import app.liftos.domain.model.BodyMeasurement
import app.liftos.domain.model.ExerciseWithSets
import app.liftos.domain.model.SetType
import app.liftos.domain.model.Side
import app.liftos.domain.model.WorkoutDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportTest {

    private val prev = TestData.benchDetail("p", "2026-10-04", listOf(60.0, 60.0, 60.0), listOf(10, 9, 8))
    private val cur: WorkoutDetail = TestData.benchDetail("c", "2026-10-11", listOf(60.0, 60.0, 62.5), listOf(11, 10, 8), start = 1_761_000_000_000L).let { d ->
        d.copy(
            session = d.session.copy(notes = "אימון טוב", energyRating = 4, moodRating = 3),
            exercises = d.exercises.map { ex ->
                ex.copy(
                    exercise = ex.exercise.copy(notes = "כתף ימין מרגישה טוב"),
                    sets = listOf(TestData.set("warm", ex.exercise.id, 0, 40.0, 10, SetType.WARMUP)) +
                        ex.sets.mapIndexed { i, s -> s.copy(orderIndex = i + 1, rir = 2) },
                )
            },
        )
    }

    // ---- CSV -------------------------------------------------------------------------------

    @Test
    fun `csv escaping follows rfc 4180`() {
        assertEquals("plain", CsvExporter.escape("plain"))
        assertEquals("\"a,b\"", CsvExporter.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.escape("say \"hi\""))
        assertEquals("\"line1\nline2\"", CsvExporter.escape("line1\nline2"))
    }

    @Test
    fun `csv has a header and one row per set`() {
        val csv = CsvExporter.sessions(listOf(cur, prev))
        val lines = csv.trimEnd().split("\r\n")
        assertEquals(CsvExporter.SESSIONS_HEADER, lines.first())
        assertEquals(1 + 4 + 3, lines.size) // header + (warm-up + 3) + 3
        // Oldest workout first.
        assertTrue(lines[1].startsWith("2026-10-04"))
        assertTrue(lines.last().startsWith("2026-10-11"))
    }

    @Test
    fun `csv row carries weight reps rir volume and hebrew text`() {
        val csv = CsvExporter.sessions(listOf(cur))
        val row = csv.split("\r\n").first { it.contains("62.5") }
        assertTrue(row.contains("לחיצת חזה"))
        assertTrue(row.contains(",62.5,8,"))
        assertTrue(row.contains(",500,")) // 62.5 x 8 volume
        assertTrue(row.contains("אימון טוב"))
    }

    @Test
    fun `csv marks warm-up rows and completion`() {
        val csv = CsvExporter.sessions(listOf(cur)).split("\r\n")
        assertTrue(csv.any { it.contains(",WARMUP,") })
        assertTrue(csv.filter { it.contains(",WORKING,") }.all { it.contains(",1,0,") })
    }

    @Test
    fun `unilateral side is exported`() {
        val se = TestData.sessionExercise(unilateral = true)
        val sets = listOf(
            TestData.set("r", se.id, 0).copy(side = Side.RIGHT),
            TestData.set("l", se.id, 1).copy(side = Side.LEFT),
        )
        val d = WorkoutDetail(TestData.session(), listOf(ExerciseWithSets(se, sets)))
        val csv = CsvExporter.sessions(listOf(d))
        assertTrue(csv.contains(",RIGHT,"))
        assertTrue(csv.contains(",LEFT,"))
    }

    @Test
    fun `measurements csv`() {
        val m = BodyMeasurement("m", 1_760_000_000_000L, "2026-10-09", "Asia/Jerusalem", weightKg = 82.4, waistCm = 91.0, notes = "אחרי \"קריאטין\", בוקר")
        val csv = CsvExporter.measurements(listOf(m)).trimEnd().split("\r\n")
        assertEquals(CsvExporter.MEASUREMENTS_HEADER, csv[0])
        assertTrue(csv[1].startsWith("2026-10-09,"))
        assertTrue(csv[1].contains("82.4,91,"))
        assertTrue(csv[1].endsWith("\"אחרי \"\"קריאטין\"\", בוקר\""))
    }

    // ---- text report -----------------------------------------------------------------------

    @Test
    fun `workout text contains the essentials`() {
        val text = TextReport.workout(cur)
        assertTrue(text.contains("אימון: Upper A"))
        assertTrue(text.contains("יום "))
        assertTrue(text.contains("משך האימון: 1:00:00"))
        assertTrue(text.contains("דירוג אנרגיה: 4/5"))
        assertTrue(text.contains("הרגשה כללית: 3/5"))
        assertTrue(text.contains("הערות לאימון: אימון טוב"))
        assertTrue(text.contains("חימום 1: 40 ק״ג × 10"))
        assertTrue(text.contains("סט 1: 60 ק״ג × 11 · RIR 2"))
        assertTrue(text.contains("סט 3: 62.5 ק״ג × 8 · RIR 2"))
        assertTrue(text.contains("הערות לתרגיל: כתף ימין מרגישה טוב"))
        assertTrue(text.contains("3 סטי עבודה"))
        assertTrue(text.contains("1 סטי חימום"))
    }

    @Test
    fun `volume in the text excludes warm-ups`() {
        // 60*11 + 60*10 + 62.5*8 = 660 + 600 + 500 = 1760
        assertTrue(TextReport.workout(cur).contains("נפח 1,760 ק״ג-חזרות"))
    }

    @Test
    fun `ai report includes previous workout, comparison and the exact request`() {
        val report = TextReport.aiReport(cur, prev, null)
        assertTrue(report.contains("## האימון הנוכחי"))
        assertTrue(report.contains("## האימון הקודם מאותו סוג"))
        assertTrue(report.contains("## השוואה לאימון הקודם מאותו סוג (2026-10-04)"))
        assertTrue(report.contains("סט 1: 60 ק״ג × 10")) // from the previous workout
        assertTrue(report.trimEnd().endsWith(TextReport.AI_INSTRUCTION))
        assertEquals(
            "נתח את האימון שלי מבחינת נפח, עצימות, התקדמות, התאוששות ובחירת התרגילים. " +
                "השווה לנתונים הקודמים כשיש בסיס לכך. הסבר מה עבד, מה כדאי לשפר והצע יעדים ריאליים לאימון הבא. " +
                "אל תמציא מידע שלא מופיע בדוח.",
            TextReport.AI_INSTRUCTION,
        )
    }

    @Test
    fun `ai report says plainly when there is no previous workout`() {
        val report = TextReport.aiReport(cur, null, null)
        assertTrue(report.contains("אין אימון קודם מאותו סוג"))
        assertFalse(report.contains("## השוואה"))
    }

    @Test
    fun `body data appears only when explicitly attached`() {
        assertFalse(TextReport.aiReport(cur, prev, null).contains("מדדי גוף"))
        val m = BodyMeasurement("m", 1L, "2026-10-09", "Asia/Jerusalem", weightKg = 82.4, waistCm = 91.0, notes = "התחלתי קריאטין")
        val withBody = TextReport.aiReport(
            cur, prev,
            BodySnapshot(m, BodyTrends.WeightChange(7, -0.6, java.time.LocalDate.of(2026, 10, 2), java.time.LocalDate.of(2026, 10, 9)), null),
        )
        assertTrue(withBody.contains("## מדדי גוף"))
        assertTrue(withBody.contains("משקל: 82.4 ק״ג"))
        assertTrue(withBody.contains("היקף מותניים: 91 ס״מ"))
        assertTrue(withBody.contains("שינוי משקל ב-7 ימים: -0.6 ק״ג"))
        assertTrue(withBody.contains("התחלתי קריאטין"))
        assertTrue(withBody.contains("אינם מעידים בהכרח על שומן או שריר"))
    }

    @Test
    fun `report never claims improvement when performance is equal`() {
        val same = TestData.benchDetail("c2", "2026-10-11", start = 1_761_000_000_000L)
        val base = TestData.benchDetail("p2", "2026-10-04")
        val text = TextReport.workout(same, base)
        assertTrue(text.contains("ללא שינוי"))
        assertFalse(text.contains("· שיפור"))
    }

    @Test
    fun `skipped and incomplete sets are visible in the report`() {
        val se = TestData.sessionExercise().copy(skipped = true, skipReason = "כאב בכתף")
        val d = WorkoutDetail(TestData.session(), listOf(ExerciseWithSets(se, listOf(TestData.set("x", se.id, 0, done = false)))))
        val text = TextReport.workout(d)
        assertTrue(text.contains("דולג: כאב בכתף"))
        assertTrue(text.contains("לא הושלם"))
    }

    // ---- single workout json ---------------------------------------------------------------

    @Test
    fun `single workout json round trips including the previous workout`() {
        val file = SessionExportFile(exportedAt = 7L, workout = cur, previous = prev)
        val text = SessionJson.encode(file)
        assertTrue(text.contains("liftos-session"))
        assertEquals(file, SessionJson.decode(text))
    }
}
