package app.liftos.data.repo

import app.liftos.data.TimeSource
import app.liftos.data.ZoneSource
import app.liftos.data.db.AppDatabase
import app.liftos.data.db.toDomain
import app.liftos.data.db.toEntity
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import app.liftos.domain.logic.IdGenerator
import app.liftos.domain.logic.TimeUtils
import androidx.room.withTransaction
import app.liftos.data.db.CustomMetricEntity
import app.liftos.data.db.MeasurementCustomValueEntity
import app.liftos.domain.calendar.Exceptions
import app.liftos.domain.model.ActivityEvent
import app.liftos.domain.model.BodyMeasurement
import app.liftos.domain.model.CustomMetric
import app.liftos.domain.model.EventStatus
import app.liftos.domain.model.MeasurementCondition
import app.liftos.domain.model.OverrideAction
import app.liftos.domain.model.PlannedOverride
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Values the user typed for one measurement; every metric is optional. */
data class MeasurementInput(
    val weightKg: Double? = null,
    val waistCm: Double? = null,
    val chestCm: Double? = null,
    val armCm: Double? = null,
    val thighCm: Double? = null,
    val hipsCm: Double? = null,
    val neckCm: Double? = null,
    val shouldersCm: Double? = null,
    val bodyFatPct: Double? = null,
    val conditions: List<MeasurementCondition> = emptyList(),
    /** Values of custom metrics, by [CustomMetric.id]. */
    val customValues: Map<String, Double> = emptyMap(),
    val notes: String = "",
) {
    val hasAnyValue: Boolean
        get() = listOf(weightKg, waistCm, chestCm, armCm, thighCm, hipsCm, neckCm, shouldersCm, bodyFatPct).any { it != null } ||
            customValues.isNotEmpty()
}

class MeasurementRepository(
    private val db: AppDatabase,
    private val time: TimeSource = TimeSource.Default,
    private val zones: ZoneSource = ZoneSource.Device,
    private val ids: IdGenerator = IdGenerator.Random,
) {
    private val dao = db.measurementDao()
    private val metrics = db.customMetricDao()

    fun observeAll(): Flow<List<BodyMeasurement>> =
        combine(dao.observeAll(), metrics.observeValues()) { list, values ->
            val byMeasurement = values.groupBy { it.measurementId }
            list.map { e -> e.toDomain(byMeasurement[e.id].orEmpty().associate { it.metricId to it.value }) }
        }

    fun observeLatestWeight(): Flow<BodyMeasurement?> = dao.observeLatestWeight().map { it?.toDomain() }

    fun observeMetrics(): Flow<List<CustomMetric>> = metrics.observeAll().map { l -> l.map { it.toDomain() } }

    suspend fun get(id: String): BodyMeasurement? {
        val e = dao.get(id) ?: return null
        return e.toDomain(metrics.valuesOf(id).associate { it.metricId to it.value })
    }

    /** Snapshot of all measurements, newest first. */
    suspend fun observeAllOnce(): List<BodyMeasurement> {
        val values = metrics.getValues().groupBy { it.measurementId }
        return dao.getAll().map { e -> e.toDomain(values[e.id].orEmpty().associate { it.metricId to it.value }) }
    }

    /** Creates a measurement at [measuredAt] (epoch millis); a note-only entry is allowed. */
    suspend fun add(input: MeasurementInput, measuredAt: Long = time.nowMs()): String = db.withTransaction {
        val zone = zones.zone()
        val now = time.nowMs()
        val id = ids.newId()
        dao.upsert(
            BodyMeasurement(
                id = id, measuredAt = measuredAt, localDate = TimeUtils.localDateString(measuredAt, zone), zoneId = zone.id,
                weightKg = input.weightKg, waistCm = input.waistCm, chestCm = input.chestCm, armCm = input.armCm,
                thighCm = input.thighCm, hipsCm = input.hipsCm, neckCm = input.neckCm, shouldersCm = input.shouldersCm,
                bodyFatPct = input.bodyFatPct, conditions = input.conditions,
                notes = input.notes, createdAt = now, updatedAt = now,
            ).toEntity(),
        )
        saveCustomValues(id, input.customValues)
        id
    }

    suspend fun update(id: String, input: MeasurementInput, measuredAt: Long) {
        db.withTransaction {
            val existing = dao.get(id)?.toDomain() ?: return@withTransaction
            val zone = zones.zone()
            dao.upsert(
                existing.copy(
                    measuredAt = measuredAt, localDate = TimeUtils.localDateString(measuredAt, zone), zoneId = zone.id,
                    weightKg = input.weightKg, waistCm = input.waistCm, chestCm = input.chestCm, armCm = input.armCm,
                    thighCm = input.thighCm, hipsCm = input.hipsCm, neckCm = input.neckCm, shouldersCm = input.shouldersCm,
                    bodyFatPct = input.bodyFatPct, conditions = input.conditions,
                    notes = input.notes, updatedAt = time.nowMs(),
                ).toEntity(),
            )
            metrics.deleteValuesOf(id)
            saveCustomValues(id, input.customValues)
        }
    }

    private suspend fun saveCustomValues(measurementId: String, values: Map<String, Double>) {
        val known = metrics.ids().toSet()
        val rows = values.filterKeys { it in known }.map { (metricId, v) -> MeasurementCustomValueEntity(measurementId, metricId, v) }
        if (rows.isNotEmpty()) metrics.upsertValues(rows)
    }

    suspend fun delete(id: String) = dao.delete(id)

    // ---- custom metrics --------------------------------------------------------------------------

    suspend fun addMetric(name: String, unit: String): String? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        val id = ids.newId()
        val order = metrics.getAll().size
        metrics.upsert(CustomMetricEntity(id, clean, unit.trim(), order, false, time.nowMs()))
        return id
    }

    suspend fun renameMetric(id: String, name: String, unit: String) {
        val m = metrics.get(id) ?: return
        if (name.isNotBlank()) metrics.upsert(m.copy(name = name.trim(), unit = unit.trim()))
    }

    /** A metric that already has values is archived (kept, hidden); an unused one is deleted. */
    suspend fun removeMetric(id: String): Boolean {
        val m = metrics.get(id) ?: return false
        return if (metrics.usage(id) > 0) {
            metrics.upsert(m.copy(isArchived = true))
            false
        } else {
            metrics.delete(id)
            true
        }
    }

    suspend fun restoreMetric(id: String) {
        val m = metrics.get(id) ?: return
        metrics.upsert(m.copy(isArchived = false))
    }
}

class ActivityRepository(
    private val db: AppDatabase,
    private val time: TimeSource = TimeSource.Default,
    private val zones: ZoneSource = ZoneSource.Device,
    private val ids: IdGenerator = IdGenerator.Random,
) {
    private val dao = db.activityDao()
    private val calendar = db.calendarDao()

    fun observeAll(): Flow<List<ActivityEvent>> = dao.observeAll().map { l -> l.map { it.toDomain() } }

    fun observeOverrides(): Flow<List<PlannedOverride>> = calendar.observeOverrides().map { l -> l.map { it.toDomain() } }

    suspend fun get(id: String): ActivityEvent? = dao.get(id)?.toDomain()

    suspend fun save(event: ActivityEvent): String {
        val zone = zones.zone()
        val now = time.nowMs()
        val id = event.id.ifBlank { ids.newId() }
        dao.upsert(
            event.copy(
                id = id,
                localDate = TimeUtils.localDateString(event.startsAt, zone),
                zoneId = zone.id,
                createdAt = if (event.createdAt == 0L) now else event.createdAt,
                updatedAt = now,
            ).toEntity(),
        )
        return id
    }

    suspend fun delete(id: String) = dao.delete(id)

    /** Deletes one occurrence of a repeating event (or the whole event when it does not repeat). */
    suspend fun deleteOccurrence(id: String, date: LocalDate) {
        val e = get(id) ?: return
        if (e.recurrence.isBlank()) delete(id) else save(Exceptions.withException(e, date))
    }

    /**
     * Moves one occurrence to [newStartsAt]. A repeating event keeps its series: the original date is
     * excluded and a separate single event is created at the new time.
     */
    suspend fun moveOccurrence(id: String, date: LocalDate, newStartsAt: Long): String? =
        changeOccurrence(id, date, newStartsAt) { it }

    /** Sets the status of ONE occurrence; a repeating series is split so the other dates are unaffected. */
    suspend fun setOccurrenceStatus(id: String, date: LocalDate, status: EventStatus): String? =
        changeOccurrence(id, date, null) { it.copy(status = status) }

    /** Links ONE occurrence to a recorded workout (marking it done), or removes the link. */
    suspend fun linkOccurrence(id: String, date: LocalDate, sessionId: String?): String? =
        changeOccurrence(id, date, null) {
            it.copy(linkedSessionId = sessionId, status = if (sessionId != null) EventStatus.DONE else it.status)
        }

    /**
     * Applies [change] to one occurrence. A single event is edited in place; for a repeating series the
     * date is added to the exceptions and a separate single event carries the change (at [newStartsAt],
     * or at the same time of day on [date] when null).
     */
    private suspend fun changeOccurrence(
        id: String,
        date: LocalDate,
        newStartsAt: Long?,
        change: (ActivityEvent) -> ActivityEvent,
    ): String? = db.withTransaction {
        val e = get(id) ?: return@withTransaction null
        if (e.recurrence.isBlank()) {
            save(change(e).let { if (newStartsAt != null) it.copy(startsAt = newStartsAt) else it })
        } else {
            val zone = TimeUtils.zone(e.zoneId)
            val minutes = app.liftos.domain.calendar.CalendarBuilder.minutesOfDay(e.startsAt, zone)
            val at = newStartsAt ?: TimeUtils.toEpochMs(date, minutes / 60, minutes % 60, zone)
            save(Exceptions.withException(e, date))
            save(change(e).copy(id = "", startsAt = at, recurrence = "", recurrenceExceptions = "", createdAt = 0L))
        }
    }

    // ---- planned workouts (overrides) -----------------------------------------------------------------

    /** Moves ONE occurrence of a plan day. The plan and past workouts are untouched. */
    suspend fun moveWorkout(plannedWorkoutId: String, originalDate: LocalDate, newDate: LocalDate, newStartMinutes: Int? = null, note: String = "") {
        putOverride(plannedWorkoutId, originalDate, OverrideAction.MOVED, newDate.toString(), newStartMinutes, note)
    }

    suspend fun markWorkout(plannedWorkoutId: String, originalDate: LocalDate, action: OverrideAction, note: String = "") {
        require(action != OverrideAction.MOVED) { "use moveWorkout" }
        putOverride(plannedWorkoutId, originalDate, action, null, null, note)
    }

    suspend fun clearOverride(plannedWorkoutId: String, originalDate: LocalDate) =
        calendar.deleteOverride(plannedWorkoutId, originalDate.toString())

    private suspend fun putOverride(
        workoutId: String,
        original: LocalDate,
        action: OverrideAction,
        newDate: String?,
        newStart: Int?,
        note: String,
    ) {
        db.withTransaction {
            val existing = calendar.findOverride(workoutId, original.toString())
            calendar.upsertOverride(
                PlannedOverride(
                    id = existing?.id ?: ids.newId(), plannedWorkoutId = workoutId, originalDate = original.toString(),
                    action = action, newDate = newDate, newStartMinutes = newStart, note = note.trim(),
                    linkedSessionId = existing?.linkedSessionId, createdAt = existing?.createdAt ?: time.nowMs(),
                ).toEntity(),
            )
        }
    }
}
