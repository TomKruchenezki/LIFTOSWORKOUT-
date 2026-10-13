package app.liftos.data.db

import app.liftos.domain.logic.RestTimerState
import app.liftos.domain.model.ActivityEvent
import app.liftos.domain.model.AppSettings
import app.liftos.domain.analytics.SessionFact
import app.liftos.domain.analytics.SetFact
import app.liftos.domain.model.BodyMeasurement
import app.liftos.domain.model.CustomMetric
import app.liftos.domain.model.Exercise
import app.liftos.domain.model.ExercisePrefs
import app.liftos.domain.model.ExerciseSet
import app.liftos.domain.model.Goal
import app.liftos.domain.model.GoalChange
import app.liftos.domain.model.MeasurementCondition
import app.liftos.domain.model.MuscleGroup
import app.liftos.domain.model.PlannedExercise
import app.liftos.domain.model.PlannedOverride
import app.liftos.domain.model.PlannedWorkout
import app.liftos.domain.model.ProgressPhoto
import app.liftos.domain.model.ReminderSettings
import app.liftos.domain.model.SessionExercise
import app.liftos.domain.model.WorkoutPlan
import app.liftos.domain.model.WorkoutSession
import app.liftos.domain.progression.ProgressionDecision
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
        notes = notes, energyRating = energyRating, moodRating = moodRating,
        difficultyRating = difficultyRating, qualityRating = qualityRating, sorenessRating = sorenessRating,
        fatigueRating = fatigueRating, sleepHours = sleepHours,
        rest = rest, source = source, createdAt = createdAt, updatedAt = updatedAt,
    )
}

fun WorkoutSession.toEntity() = WorkoutSessionEntity(
    id = id, planId = planId, plannedWorkoutId = plannedWorkoutId, planName = planName, title = title,
    status = status, startedAt = startedAt, endedAt = endedAt, localDate = localDate, zoneId = zoneId,
    notes = notes, energyRating = energyRating, moodRating = moodRating,
    difficultyRating = difficultyRating, qualityRating = qualityRating, sorenessRating = sorenessRating,
    fatigueRating = fatigueRating, sleepHours = sleepHours,
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

internal fun List<MeasurementCondition>.toConditionCsv(): String = joinToString(",") { it.name }

internal fun String.toConditionList(): List<MeasurementCondition> =
    if (isBlank()) emptyList() else split(",").mapNotNull { n -> MeasurementCondition.entries.firstOrNull { it.name == n.trim() } }

/** Custom values live in their own table; the repository fills [BodyMeasurement.customValues]. */
fun BodyMeasurementEntity.toDomain(customValues: Map<String, Double> = emptyMap()) = BodyMeasurement(
    id = id, measuredAt = measuredAt, localDate = localDate, zoneId = zoneId, weightKg = weightKg,
    waistCm = waistCm, chestCm = chestCm, armCm = armCm, thighCm = thighCm, hipsCm = hipsCm,
    neckCm = neckCm, shouldersCm = shouldersCm, bodyFatPct = bodyFatPct, conditions = conditions.toConditionList(),
    customValues = customValues, notes = notes, createdAt = createdAt, updatedAt = updatedAt,
)

fun BodyMeasurement.toEntity() = BodyMeasurementEntity(
    id = id, measuredAt = measuredAt, localDate = localDate, zoneId = zoneId, weightKg = weightKg,
    waistCm = waistCm, chestCm = chestCm, armCm = armCm, thighCm = thighCm, hipsCm = hipsCm,
    neckCm = neckCm, bodyFatPct = bodyFatPct, notes = notes, createdAt = createdAt, updatedAt = updatedAt,
    shouldersCm = shouldersCm, conditions = conditions.toConditionCsv(),
)

fun ActivityEventEntity.toDomain() = ActivityEvent(
    id = id, type = type, title = title, notes = notes, startsAt = startsAt, localDate = localDate,
    zoneId = zoneId, durationMin = durationMin, status = status, allDay = allDay,
    plannedWorkoutId = plannedWorkoutId, linkedSessionId = linkedSessionId, recurrence = recurrence,
    recurrenceExceptions = recurrenceExceptions, createdAt = createdAt, updatedAt = updatedAt,
)

fun ActivityEvent.toEntity() = ActivityEventEntity(
    id = id, type = type, title = title, notes = notes, startsAt = startsAt, localDate = localDate,
    zoneId = zoneId, durationMin = durationMin, createdAt = createdAt, updatedAt = updatedAt,
    status = status, allDay = allDay, plannedWorkoutId = plannedWorkoutId, linkedSessionId = linkedSessionId,
    recurrence = recurrence, recurrenceExceptions = recurrenceExceptions,
)

private val settingsJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

internal fun ReminderSettings.toJson(): String = settingsJson.encodeToString(ReminderSettings.serializer(), this)

internal fun String.toReminderSettings(): ReminderSettings =
    if (isBlank()) ReminderSettings() else try {
        settingsJson.decodeFromString(ReminderSettings.serializer(), this)
    } catch (_: Exception) {
        ReminderSettings()
    }

fun AppSettingsEntity.toDomain() = AppSettings(
    themeMode = themeMode, effortMode = effortMode, vibrateOnSetComplete = vibrateOnSetComplete,
    vibrateOnRestEnd = vibrateOnRestEnd, restNotificationEnabled = restNotificationEnabled,
    autoStartRest = autoStartRest, keepScreenOn = keepScreenOn,
    defaultRestCompoundSec = defaultRestCompoundSec, defaultRestIsolationSec = defaultRestIsolationSec,
    activePlanId = activePlanId, weeklyGoalWorkouts = weeklyGoalWorkouts, language = language,
    reminders = remindersJson.toReminderSettings(),
)

fun AppSettings.toEntity() = AppSettingsEntity(
    id = 1, themeMode = themeMode, effortMode = effortMode, vibrateOnSetComplete = vibrateOnSetComplete,
    vibrateOnRestEnd = vibrateOnRestEnd, restNotificationEnabled = restNotificationEnabled,
    autoStartRest = autoStartRest, keepScreenOn = keepScreenOn,
    defaultRestCompoundSec = defaultRestCompoundSec, defaultRestIsolationSec = defaultRestIsolationSec,
    activePlanId = activePlanId, weeklyGoalWorkouts = weeklyGoalWorkouts, language = language,
    remindersJson = reminders.toJson(),
)

// ---- version 2 ---------------------------------------------------------------------------------

fun ProgressionDecisionEntity.toDomain() = ProgressionDecision(
    id = id, exerciseKey = exerciseKey, basedOnSessionId = basedOnSessionId, kind = kind,
    suggestedWeightKg = suggestedWeightKg, chosenWeightKg = chosenWeightKg, decision = decision, decidedAt = decidedAt,
)

fun ProgressionDecision.toEntity() = ProgressionDecisionEntity(
    id = id, exerciseKey = exerciseKey, basedOnSessionId = basedOnSessionId, kind = kind,
    suggestedWeightKg = suggestedWeightKg, chosenWeightKg = chosenWeightKg, decision = decision, decidedAt = decidedAt,
)

fun ExercisePrefsEntity.toDomain() = ExercisePrefs(exerciseKey, incrementKg, updatedAt)

fun ExercisePrefs.toEntity() = ExercisePrefsEntity(exerciseKey, incrementKg, updatedAt)

fun PlannedOverrideEntity.toDomain() = PlannedOverride(
    id = id, plannedWorkoutId = plannedWorkoutId, originalDate = originalDate, action = action, newDate = newDate,
    newStartMinutes = newStartMinutes, note = note, linkedSessionId = linkedSessionId, createdAt = createdAt,
)

fun PlannedOverride.toEntity() = PlannedOverrideEntity(
    id = id, plannedWorkoutId = plannedWorkoutId, originalDate = originalDate, action = action, newDate = newDate,
    newStartMinutes = newStartMinutes, note = note, linkedSessionId = linkedSessionId, createdAt = createdAt,
)

fun GoalEntity.toDomain() = Goal(
    id = id, title = title, type = type, metricKey = metricKey, unit = unit, startValue = startValue,
    targetValue = targetValue, currentValue = currentValue, startDate = startDate, targetDate = targetDate,
    status = status, achievedAt = achievedAt, notes = notes, createdAt = createdAt, updatedAt = updatedAt,
)

fun Goal.toEntity() = GoalEntity(
    id = id, title = title, type = type, metricKey = metricKey, unit = unit, startValue = startValue,
    targetValue = targetValue, currentValue = currentValue, startDate = startDate, targetDate = targetDate,
    status = status, achievedAt = achievedAt, notes = notes, createdAt = createdAt, updatedAt = updatedAt,
)

fun GoalChangeEntity.toDomain() = GoalChange(id, goalId, changedAt, kind, oldValue, newValue, note)

fun GoalChange.toEntity() = GoalChangeEntity(id, goalId, changedAt, kind, oldValue, newValue, note)

fun ProgressPhotoEntity.toDomain() = ProgressPhoto(
    id = id, takenAt = takenAt, localDate = localDate, zoneId = zoneId, angle = angle, fileName = fileName,
    weightKg = weightKg, notes = notes, createdAt = createdAt, updatedAt = updatedAt,
)

fun ProgressPhoto.toEntity() = ProgressPhotoEntity(
    id = id, takenAt = takenAt, localDate = localDate, zoneId = zoneId, angle = angle, fileName = fileName,
    weightKg = weightKg, notes = notes, createdAt = createdAt, updatedAt = updatedAt,
)

fun CustomMetricEntity.toDomain() = CustomMetric(id, name, unit, sortOrder, isArchived, createdAt)

fun CustomMetric.toEntity() = CustomMetricEntity(id, name, unit, sortOrder, isArchived, createdAt)

fun SetFactRow.toDomain() = SetFact(
    sessionId = sessionId, sessionTitle = sessionTitle, startedAt = startedAt, localDate = localDate,
    sessionExerciseId = sessionExerciseId, exerciseId = exerciseId, exerciseName = exerciseName,
    exerciseNameEn = exerciseNameEn, primaryMuscle = primaryMuscle,
    secondaryMuscles = secondaryMuscles.orEmpty().toMuscleList(), trackingType = trackingType,
    isUnilateral = isUnilateral, exerciseOrder = exerciseOrder, setId = setId, setOrder = setOrder,
    setType = setType, side = side, weightKg = weightKg, reps = reps, durationSec = durationSec, rir = rir,
    rpe = rpe, isFailure = isFailure, completedAt = completedAt, plannedSets = plannedSets,
    targetRepsMin = repsMin, targetRepsMax = repsMax, equipment = equipment, exerciseType = exerciseType,
)

fun WorkoutSessionEntity.toFact() = SessionFact(
    id = id, title = title, planName = planName, plannedWorkoutId = plannedWorkoutId, startedAt = startedAt,
    endedAt = endedAt, localDate = localDate, energyRating = energyRating, moodRating = moodRating,
    difficultyRating = difficultyRating, qualityRating = qualityRating, sorenessRating = sorenessRating,
    fatigueRating = fatigueRating, sleepHours = sleepHours, notes = notes,
)
