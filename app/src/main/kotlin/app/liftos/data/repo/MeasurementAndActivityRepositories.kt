package app.liftos.data.repo

import app.liftos.data.TimeSource
import app.liftos.data.ZoneSource
import app.liftos.data.db.AppDatabase
import app.liftos.data.db.toDomain
import app.liftos.data.db.toEntity
import app.liftos.domain.logic.IdGenerator
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.model.ActivityEvent
import app.liftos.domain.model.BodyMeasurement
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
    val bodyFatPct: Double? = null,
    val notes: String = "",
) {
    val hasAnyValue: Boolean
        get() = listOf(weightKg, waistCm, chestCm, armCm, thighCm, hipsCm, neckCm, bodyFatPct).any { it != null }
}

class MeasurementRepository(
    db: AppDatabase,
    private val time: TimeSource = TimeSource.Default,
    private val zones: ZoneSource = ZoneSource.Device,
    private val ids: IdGenerator = IdGenerator.Random,
) {
    private val dao = db.measurementDao()

    fun observeAll(): Flow<List<BodyMeasurement>> = dao.observeAll().map { l -> l.map { it.toDomain() } }

    fun observeLatestWeight(): Flow<BodyMeasurement?> = dao.observeLatestWeight().map { it?.toDomain() }

    suspend fun get(id: String): BodyMeasurement? = dao.get(id)?.toDomain()

    /** Snapshot of all measurements, newest first. */
    suspend fun observeAllOnce(): List<BodyMeasurement> = dao.getAll().map { it.toDomain() }

    /** Creates a measurement at [measuredAt] (epoch millis); a note-only entry is allowed. */
    suspend fun add(input: MeasurementInput, measuredAt: Long = time.nowMs()): String {
        val zone = zones.zone()
        val now = time.nowMs()
        val id = ids.newId()
        dao.upsert(
            BodyMeasurement(
                id = id, measuredAt = measuredAt, localDate = TimeUtils.localDateString(measuredAt, zone), zoneId = zone.id,
                weightKg = input.weightKg, waistCm = input.waistCm, chestCm = input.chestCm, armCm = input.armCm,
                thighCm = input.thighCm, hipsCm = input.hipsCm, neckCm = input.neckCm, bodyFatPct = input.bodyFatPct,
                notes = input.notes, createdAt = now, updatedAt = now,
            ).toEntity(),
        )
        return id
    }

    suspend fun update(id: String, input: MeasurementInput, measuredAt: Long) {
        val existing = dao.get(id)?.toDomain() ?: return
        val zone = zones.zone()
        dao.upsert(
            existing.copy(
                measuredAt = measuredAt, localDate = TimeUtils.localDateString(measuredAt, zone), zoneId = zone.id,
                weightKg = input.weightKg, waistCm = input.waistCm, chestCm = input.chestCm, armCm = input.armCm,
                thighCm = input.thighCm, hipsCm = input.hipsCm, neckCm = input.neckCm, bodyFatPct = input.bodyFatPct,
                notes = input.notes, updatedAt = time.nowMs(),
            ).toEntity(),
        )
    }

    suspend fun delete(id: String) = dao.delete(id)
}

class ActivityRepository(
    db: AppDatabase,
    private val time: TimeSource = TimeSource.Default,
    private val zones: ZoneSource = ZoneSource.Device,
    private val ids: IdGenerator = IdGenerator.Random,
) {
    private val dao = db.activityDao()

    fun observeAll(): Flow<List<ActivityEvent>> = dao.observeAll().map { l -> l.map { it.toDomain() } }

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
}
