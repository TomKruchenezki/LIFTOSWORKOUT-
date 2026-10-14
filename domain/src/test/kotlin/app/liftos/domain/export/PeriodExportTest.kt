package app.liftos.domain.export

import app.liftos.domain.TestData
import app.liftos.domain.model.BodyMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodExportTest {
    private val workout = TestData.benchDetail("c", "2026-10-11", listOf(60.0, 60.0, 62.5), listOf(11, 10, 8)).let { d ->
        d.copy(
            session = d.session.copy(notes = "הרגשתי טוב"),
            exercises = d.exercises.map { ex ->
                ex.copy(
                    exercise = ex.exercise.copy(notes = "כתף ימין", skipReason = "אין ציוד"),
                    sets = ex.sets.map { it.copy(notes = "סט אחרון קשה") },
                )
            },
        )
    }

    @Test
    fun `period file round trips through json`() {
        val file = PeriodExportFile(
            exportedAt = 1_760_000_000_000L, scope = "חודש אחרון", rangeStart = "2026-09-12", rangeEnd = "2026-10-11",
            instruction = TextReport.AI_INSTRUCTION, workouts = listOf(workout),
            measurements = listOf(BodyMeasurement(id = "m", measuredAt = 1L, localDate = "2026-10-10", zoneId = "Asia/Jerusalem", weightKg = 82.5)),
        )
        val decoded = PeriodJson.decode(PeriodJson.encode(file))
        assertEquals(file, decoded)
        assertEquals(PeriodExportFile.FORMAT, decoded.format)
    }

    @Test
    fun `body measurements are absent unless added`() {
        val file = PeriodExportFile(exportedAt = 1L, scope = "x", rangeStart = "2026-10-01", rangeEnd = "2026-10-11", instruction = "", workouts = listOf(workout))
        assertTrue(PeriodJson.decode(PeriodJson.encode(file)).measurements.isEmpty())
    }

    @Test
    fun `without notes removes every free text the user wrote but keeps the numbers`() {
        val clean = workout.withoutNotes()
        assertEquals("", clean.session.notes)
        assertTrue(clean.exercises.all { it.exercise.notes.isEmpty() && it.exercise.skipReason.isEmpty() })
        assertTrue(clean.exercises.flatMap { it.sets }.all { it.notes.isEmpty() })
        assertEquals(workout.exercises.flatMap { it.sets }.map { it.weightKg to it.reps }, clean.exercises.flatMap { it.sets }.map { it.weightKg to it.reps })
        val json = PeriodJson.encode(PeriodExportFile(exportedAt = 1L, scope = "x", rangeStart = "a", rangeEnd = "b", instruction = "", workouts = listOf(clean)))
        assertFalse(json.contains("הרגשתי טוב"))
        assertFalse(json.contains("כתף ימין"))
        assertFalse(json.contains("סט אחרון קשה"))
        assertFalse(CsvExporter.sessions(listOf(clean)).contains("הרגשתי טוב"))
    }

    @Test
    fun `formats have distinct extensions and mime types`() {
        assertEquals(4, ExportFormat.entries.map { it.extension }.toSet().size)
        assertEquals("application/pdf", ExportFormat.PDF.mime)
        assertEquals("text/csv", ExportFormat.CSV.mime)
    }
}
