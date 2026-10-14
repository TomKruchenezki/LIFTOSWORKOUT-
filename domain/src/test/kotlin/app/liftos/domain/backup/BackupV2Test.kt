package app.liftos.domain.backup

import app.liftos.domain.TestData
import app.liftos.domain.model.BodyMeasurement
import app.liftos.domain.model.CustomMetric
import app.liftos.domain.model.Goal
import app.liftos.domain.model.GoalChange
import app.liftos.domain.model.GoalType
import app.liftos.domain.model.OverrideAction
import app.liftos.domain.model.PhotoAngle
import app.liftos.domain.model.PlannedOverride
import app.liftos.domain.model.ProgressPhoto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class BackupV2Test {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private val library = TestData.library.exercises

    private val metric = CustomMetric(id = "cm1", name = "היקף ברך", unit = "ס״מ")
    private val goal = Goal(id = "g1", title = "לחיצת חזה 100", type = GoalType.EXERCISE_WEIGHT, metricKey = "bench", startValue = 80.0, targetValue = 100.0, startDate = "2026-09-01")
    private val photo = ProgressPhoto(id = "ph1", takenAt = 1_760_000_000_000L, localDate = "2026-10-09", zoneId = "Asia/Jerusalem", angle = PhotoAngle.FRONT, fileName = "photo-ph1.jpg")

    private fun v2Data() = BackupData(
        exercises = library,
        customMetrics = listOf(metric),
        measurements = listOf(BodyMeasurement("m1", 1_760_000_000_000L, "2026-10-09", "Asia/Jerusalem", weightKg = 82.0, customValues = mapOf("cm1" to 38.5))),
        plannedOverrides = listOf(PlannedOverride("o1", "pw1", "2026-10-12", OverrideAction.SKIPPED)),
        goals = listOf(goal),
        goalChanges = listOf(GoalChange(id = "gc1", goalId = "g1", changedAt = 2L, kind = app.liftos.domain.model.GoalChangeKind.CREATED, newValue = "100", note = "")),
        photos = listOf(photo),
    )

    @Test
    fun `a version 1 backup without the new tables still loads and the new lists are empty`() {
        val full = BackupCodec.encode(BackupFile(exportedAt = 1L, data = v2Data()))
        val obj = Json.parseToJsonElement(full).jsonObject
        val data = obj["data"]!!.jsonObject
        val v1Data = JsonObject(data.filterKeys { it !in setOf("customMetrics", "plannedOverrides", "goals", "goalChanges", "photos", "exercisePrefs", "progressionDecisions") })
        val v1 = buildJsonObject {
            obj.forEach { (k, v) ->
                when (k) {
                    "schemaVersion" -> put(k, JsonPrimitive(1))
                    "data" -> put(k, v1Data)
                    "includesPhotos" -> Unit
                    else -> put(k, v)
                }
            }
        }
        val parsed = BackupCodec.parse(v1.toString(), library, zone, 0L)
        assertTrue(parsed is ParseResult.Success)
        val file = (parsed as ParseResult.Success).file
        assertTrue(file.data.goals.isEmpty() && file.data.photos.isEmpty() && file.data.customMetrics.isEmpty() && file.data.plannedOverrides.isEmpty())
        assertEquals(1, file.data.measurements.size)
        assertFalse(file.includesPhotos)
    }

    @Test
    fun `a version 2 backup round trips goals photos custom metrics and overrides`() {
        val file = BackupFile(exportedAt = 1L, includesPhotos = true, data = v2Data())
        val parsed = BackupCodec.parse(BackupCodec.encode(file), library, zone, 0L) as ParseResult.Success
        assertEquals(file.data, parsed.file.data)
        assertTrue(parsed.file.includesPhotos)
        assertEquals(2, parsed.file.schemaVersion)
    }

    @Test
    fun `merge adds only new items of the version 2 tables and keeps local preferences`() {
        val d = v2Data()
        val existing = ExistingIds(goals = setOf("g1"), customMetrics = setOf("cm1"), photos = setOf("ph1"), plannedOverrides = setOf("o1"))
        val delta = BackupMerge.delta(d, existing)
        assertTrue(delta.goals.isEmpty())
        assertTrue(delta.goalChanges.isEmpty())
        assertTrue(delta.photos.isEmpty())
        assertTrue(delta.plannedOverrides.isEmpty())
        assertTrue(delta.customMetrics.isEmpty())
        // The measurement is new and its custom value survives because the metric already exists locally.
        assertEquals(mapOf("cm1" to 38.5), delta.measurements.single().customValues)
    }

    @Test
    fun `merge into an empty database brings goal history with new goals only`() {
        val delta = BackupMerge.delta(v2Data(), ExistingIds())
        assertEquals(listOf("g1"), delta.goals.map { it.id })
        assertEquals(listOf("gc1"), delta.goalChanges.map { it.id })
        assertEquals(listOf("ph1"), delta.photos.map { it.id })
    }

    @Test
    fun `a custom value with no metric anywhere is dropped from a merged measurement`() {
        val d = v2Data().copy(customMetrics = emptyList())
        val delta = BackupMerge.delta(d, ExistingIds())
        assertTrue(delta.measurements.single().customValues.isEmpty())
    }

    @Test
    fun `photo file names that could escape the photo folder are rejected`() {
        val bad = listOf("../secret.jpg", "a/b.jpg", "..\\x.jpg", "dir\\x.jpg", "", "/abs.jpg")
        bad.forEach { name ->
            val report = BackupValidator.validate(BackupFile(data = v2Data().copy(photos = listOf(photo.copy(fileName = name)))), library.map { it.id }.toSet())
            assertFalse("'$name' should be rejected", report.isValid)
        }
        val ok = BackupValidator.validate(BackupFile(data = v2Data()), library.map { it.id }.toSet())
        assertTrue(ok.errors.joinToString(), ok.isValid)
    }

    @Test
    fun `the summary counts the version 2 content`() {
        val s = BackupSummary.of(v2Data())
        assertEquals(1, s.goals)
        assertEquals(1, s.photos)
        assertEquals(1, s.customMetrics)
    }
}
