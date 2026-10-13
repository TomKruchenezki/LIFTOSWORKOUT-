package app.liftos.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class BodyMeasurement(
    val id: String,
    /** Epoch millis, UTC. */
    val measuredAt: Long,
    /** Local calendar date (ISO yyyy-MM-dd) in [zoneId]. */
    val localDate: String,
    val zoneId: String,
    val weightKg: Double? = null,
    val waistCm: Double? = null,
    val chestCm: Double? = null,
    val armCm: Double? = null,
    val thighCm: Double? = null,
    val hipsCm: Double? = null,
    val neckCm: Double? = null,
    val bodyFatPct: Double? = null,
    val notes: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    /** True when at least one numeric metric was entered. */
    val hasAnyValue: Boolean
        get() = listOf(weightKg, waistCm, chestCm, armCm, thighCm, hipsCm, neckCm, bodyFatPct).any { it != null }
}

@Serializable
data class ActivityEvent(
    val id: String,
    val type: ActivityType = ActivityType.OTHER,
    val title: String = "",
    val notes: String = "",
    /** Epoch millis, UTC. */
    val startsAt: Long,
    val localDate: String,
    val zoneId: String,
    val durationMin: Int? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
