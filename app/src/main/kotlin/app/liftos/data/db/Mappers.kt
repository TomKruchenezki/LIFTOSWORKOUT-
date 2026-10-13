package app.liftos.data.db

import app.liftos.domain.logic.RestTimerState
import app.liftos.domain.model.ActivityEvent
import app.liftos.domain.model.AppSettings
import app.liftos.domain.model.BodyMeasurement
import app.liftos.domain.model.Exercise
import app.liftos.domain.model.ExerciseSet
import app.liftos.domain.model.MuscleGroup
import app.liftos.domain.model.PlannedExercise
import app.liftos.domain.model.PlannedWorkout
import app.liftos.domain.model.SessionExercise
import app.liftos.domain.model.WorkoutPlan
import app.liftos.domain.model.WorkoutSession
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val listJson = Json { ignoreUnknownKeys = true }
private val stringList = ListSerializer(String.serializer())

internal fun List<String>.toJsonArray(): String = listJson.encodeToString(stringList, this)

internal fun String.fromJsonArray(): List<String> =
    if (isBlank()) emptyList() else try {
        listJson.decodeFromString(stringList, this)
    } catch (_: Exception) {
        emptyList()
    }

internal fun List<MuscleGroup>.toCsv(): String = joinToString(",") { it.name }

internal fun String.toMuscleList(): List<MuscleGroup> =
    if (isBlank()) emptyList() else split(",").mapNotNull { n -> MuscleGroup.entries.firstOrNull { it.name == n.trim() } }

// ---- exercises ---------------------------------------------------------------------------

fun ExerciseEntity.toDomain() = Exercise(
    id = id, nameHe = nameHe, nameEn = nameEn, primaryMuscle = primaryMuscle,
    secondaryMuscles = secondaryMuscles.toMuscleList(), equipment = equipment, difficulty = difficulty,
    type = type, trackingType = trackingType, isUnilateral = isUnilateral, description = description,
    instructions = instructions.fromJsonArray(), mistakes = mistakes.fromJsonArray(), safety = safety.fromJsonArray(),
    alternativeIds = alternativeIds.fromJsonArray(), videoUrl = videoUrl, isBuiltIn = isBuiltIn,
    isArchived = isArchived, createdAt = createdAt, updatedAt = updatedAt,
)

fun Exercise.toEntity() = ExerciseEntity(
    id = id, nameHe = nameHe, nameEn = nameEn, primaryMuscle = primaryMuscle,
    secondaryMuscles = secondaryMuscles.toCsv(), equipment = equipment, difficulty = difficulty,
    type = type, trackingType = trackingType, isUnilateral = isUnilateral, description = description,
    instructions = instructions.toJsonArray(), mistakes = mistakes.toJsonArray(), safety = safety.toJsonArray(),
    alternativeIds = alternativeIds.toJsonArray(), videoUrl = videoUrl, isBuiltIn = isBuiltIn,
    isArchived = isArchived, createdAt = createdAt, updatedAt = updatedAt,
)

// ---- plans -----------------------------------------------------------------------------------

fun WorkoutPlanEntity.toDomain() = WorkoutPlan(
    id = id, name = name, description = description, goal = goal, workoutsPerWeek = workoutsPerWeek,
    isTemplate = isTemplate, sortOrder = sortOrder, createdAt = createdAt, updatedAt = updatedAt,
)

fun WorkoutPlan.toEntity() = WorkoutPlanEntity(
    id = id, name = name, description = description, goal = goal, workoutsPerWeek = workoutsPerWeek,
    isTemplate = isTemplate, sortOrder = sortOrder, createdAt = createdAt, updatedAt = updatedAt,
)

fun PlannedWorkoutEntity.toDomain() = PlannedWorkout(
    id = id, planId = planId, name = name, description = description, orderIndex = orderIndex,
    weekday = weekday, notes = notes,
)

fun PlannedWorkout.toEntity() = PlannedWorkoutEntity(
    id = id, planId = planId, name = name, description = description, orderIndex = orderIndex,
    weekday = weekday, notes = notes,
)

fun PlannedExerciseEntity.toDomain() = PlannedExercise(
    id = id, plannedWorkoutId = plannedWorkoutId, exerciseId = exerciseId, orderIndex = orderIndex,
    targetSets = targetSets, repsMin = repsMin, repsMax = repsMax, targetWeightKg = targetWeightKg,
    targetRir = targetRir, targetRpe = targetRpe, restSeconds = restSeconds, notes = notes,
    warmupSets = warmupSets, supersetGroup = supersetGroup,
)

fun PlannedExercise.toEntity() = PlannedExerciseEntity(
    id = id, plannedWorkoutId = plannedWorkoutId, exerciseId = exerciseId, orderIndex = orderIndex,
    targetSets = targetSets, repsMin = repsMin, repsMax = repsMax, targetWeightKg = targetWeightKg,
    targetRir = targetRir, targetRpe = targetRpe, restSeconds = restSeconds, notes = notes,
    warmupSets = warmupSets, supersetGroup = supersetGroup,
)

// ---- sessions --------------------------------------------------------------------------------

fun WorkoutSessionEntity.toDomain(): WorkoutSession {
    val rest = if (restTotalMs != null && (restEndsAt != null || restPausedRemainingMs != null)) {
        RestTimerState(
            totalMs = restTotalMs,
            endsAt = restEndsAt,
            pausedRemainingMs = restPausedRemainingMs,
            label = restLabel,
        )
    } else null
    return WorkoutSession(
        id = id, planId = planId, plannedWorkoutId = plannedWorkoutId, planName = planName, title = title,
        status = status, startedAt = startedAt, endedAt = endedAt, localDate = localDate, zoneId = zoneId,
        notes = notes, energyRating = energyRating, moodRating = moodRating, rest = rest, source = source,
        createdAt = createdAt, updatedAt = updatedAt,
    )
}

fun WorkoutSession.toEntity() = WorkoutSessionEntity(
    id = id, planId = planId, plannedWorkoutId = plannedWorkoutId, planName = planName, title = title,
    status = status, startedAt = startedAt, endedAt = endedAt, localDate = localDate, zoneId = zoneId,
    notes = notes, energyRating = energyRating, moodRating = moodRating,
    restTotalMs = rest?.totalMs, restEndsAt = rest?.endsAt, restPausedRemainingMs = rest?.pausedRemainingMs,
    restLabel = rest?.label.orEmpty(), source = source, createdAt = createdAt, updatedAt = updatedAt,
)

fun SessionExerciseEntity.toDomain() = SessionExercise(
    id = id, sessionId = sessionId, exerciseId = exerciseId, plannedExerciseId = plannedExerciseId,
    nameHe = nameHe, nameEn = nameEn, primaryMuscle = primaryMuscle, trackingType = trackingType,
    isUnilateral = isUnilateral, orderIndex = orderIndex, plannedSets = plannedSets, repsMin = repsMin,
    repsMax = repsMax, targetWeightKg = targetWeightKg, targetRir = targetRir, targetRpe = targetRpe,
    restSeconds = restSeconds, notes = notes, skipped = skipped, skipReason = skipReason,
    supersetGroup = supersetGroup,
)

fun SessionExercise.toEntity() = SessionExerciseEntity(
    id = id, sessionId = sessionId, exerciseId = exerciseId, plannedExerciseId = plannedExerciseId,
    nameHe = nameHe, nameEn = nameEn, primaryMuscle = primaryMuscle, trackingType = trackingType,
    isUnilateral = isUnilateral, orderIndex = orderIndex, plannedSets = plannedSets, repsMin = repsMin,
    repsMax = repsMax, targetWeightKg = targetWeightKg, targetRir = targetRir, targetRpe = targetRpe,
    restSeconds = restSeconds, notes = notes, skipped = skipped, skipReason = skipReason,
    supersetGroup = supersetGroup,
)

fun ExerciseSetEntity.toDomain() = ExerciseSet(
    id = id, sessionExerciseId = sessionExerciseId, orderIndex = orderIndex, setType = setType, side = side,
    weightKg = weightKg, reps = reps, durationSec = durationSec, rir = rir, rpe = rpe,
    isCompleted = isCompleted, isFailure = isFailure, completedAt = completedAt, notes = notes,
)

fun ExerciseSet.toEntity() = ExerciseSetEntity(
    id = id, sessionExerciseId = sessionExerciseId, orderIndex = orderIndex, setType = setType, side = side,
    weightKg = weightKg, reps = reps, durationSec = durationSec, rir = rir, rpe = rpe,
    isCompleted = isCompleted, isFailure = isFailure, completedAt = completedAt, notes = notes,
)

// ---- body, events, settings -------------------------------------------------------------

fun BodyMeasurementEntity.toDomain() = BodyMeasurement(
    id = id, measuredAt = measuredAt, localDate = localDate, zoneId = zoneId, weightKg = weightKg,
    waistCm = waistCm, chestCm = chestCm, armCm = armCm, thighCm = thighCm, hipsCm = hipsCm,
    neckCm = neckCm, bodyFatPct = bodyFatPct, notes = notes, createdAt = createdAt, updatedAt = updatedAt,
)

fun BodyMeasurement.toEntity() = BodyMeasurementEntity(
    id = id, measuredAt = measuredAt, localDate = localDate, zoneId = zoneId, weightKg = weightKg,
    waistCm = waistCm, chestCm = chestCm, armCm = armCm, thighCm = thighCm, hipsCm = hipsCm,
    neckCm = neckCm, bodyFatPct = bodyFatPct, notes = notes, createdAt = createdAt, updatedAt = updatedAt,
)

fun ActivityEventEntity.toDomain() = ActivityEvent(
    id = id, type = type, title = title, notes = notes, startsAt = startsAt, localDate = localDate,
    zoneId = zoneId, durationMin = durationMin, createdAt = createdAt, updatedAt = updatedAt,
)

fun ActivityEvent.toEntity() = ActivityEventEntity(
    id = id, type = type, title = title, notes = notes, startsAt = startsAt, localDate = localDate,
    zoneId = zoneId, durationMin = durationMin, createdAt = createdAt, updatedAt = updatedAt,
)

fun AppSettingsEntity.toDomain() = AppSettings(
    themeMode = themeMode, effortMode = effortMode, vibrateOnSetComplete = vibrateOnSetComplete,
    vibrateOnRestEnd = vibrateOnRestEnd, restNotificationEnabled = restNotificationEnabled,
    autoStartRest = autoStartRest, keepScreenOn = keepScreenOn,
    defaultRestCompoundSec = defaultRestCompoundSec, defaultRestIsolationSec = defaultRestIsolationSec,
    activePlanId = activePlanId, weeklyGoalWorkouts = weeklyGoalWorkouts, language = language,
)

fun AppSettings.toEntity() = AppSettingsEntity(
    id = 1, themeMode = themeMode, effortMode = effortMode, vibrateOnSetComplete = vibrateOnSetComplete,
    vibrateOnRestEnd = vibrateOnRestEnd, restNotificationEnabled = restNotificationEnabled,
    autoStartRest = autoStartRest, keepScreenOn = keepScreenOn,
    defaultRestCompoundSec = defaultRestCompoundSec, defaultRestIsolationSec = defaultRestIsolationSec,
    activePlanId = activePlanId, weeklyGoalWorkouts = weeklyGoalWorkouts, language = language,
)
