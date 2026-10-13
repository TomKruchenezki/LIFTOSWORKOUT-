package app.liftos.domain.analytics

import app.liftos.domain.model.MuscleGroup
import app.liftos.domain.model.SetType
import app.liftos.domain.model.Side
import app.liftos.domain.model.TrackingType
import java.time.LocalDate
import java.time.ZoneOffset

/** Small builders for analytics tests. */
object Fx {
    fun startOf(date: String, hour: Int = 18): Long =
        LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + hour * 3_600_000L

    private var counter = 0

    fun fact(
        session: String,
        date: String,
        weight: Double?,
        reps: Int?,
        exercise: String = "bench",
        name: String = "לחיצת חזה",
        muscle: MuscleGroup = MuscleGroup.CHEST,
        secondary: List<MuscleGroup> = emptyList(),
        order: Int = 0,
        type: SetType = SetType.WORKING,
        tracking: TrackingType = TrackingType.WEIGHT_REPS,
        unilateral: Boolean = false,
        side: Side? = null,
        duration: Int? = null,
        title: String = "Upper A",
        seId: String = "$session-$exercise",
    ) = SetFact(
        sessionId = session, sessionTitle = title, startedAt = startOf(date), localDate = date,
        sessionExerciseId = seId, exerciseId = exercise, exerciseName = name, exerciseNameEn = name,
        primaryMuscle = muscle, secondaryMuscles = secondary, trackingType = tracking, isUnilateral = unilateral,
        setId = "set-${++counter}", setOrder = order, setType = type, side = side,
        weightKg = weight, reps = reps, durationSec = duration, completedAt = startOf(date),
    )

    /** A full exercise in one session: parallel lists of weights and reps. */
    fun sets(
        session: String,
        date: String,
        weights: List<Double?>,
        reps: List<Int?>,
        exercise: String = "bench",
        name: String = "לחיצת חזה",
        muscle: MuscleGroup = MuscleGroup.CHEST,
        secondary: List<MuscleGroup> = emptyList(),
    ): List<SetFact> = weights.indices.map { i ->
        fact(session, date, weights[i], reps[i], exercise, name, muscle, secondary, order = i)
    }

    fun session(id: String, date: String, minutes: Int? = 60, title: String = "Upper A", energy: Int? = null) = SessionFact(
        id = id, title = title, startedAt = startOf(date),
        endedAt = minutes?.let { startOf(date) + it * 60_000L }, localDate = date, energyRating = energy,
    )
}
