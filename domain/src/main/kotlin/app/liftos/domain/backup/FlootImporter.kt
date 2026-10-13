package app.liftos.domain.backup

import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.model.ActivityEvent
import app.liftos.domain.model.ActivityType
import app.liftos.domain.model.BodyMeasurement
import app.liftos.domain.model.Exercise
import app.liftos.domain.model.ExerciseSet
import app.liftos.domain.model.MuscleGroup
import app.liftos.domain.model.PlannedExercise
import app.liftos.domain.model.PlannedWorkout
import app.liftos.domain.model.SessionExercise
import app.liftos.domain.model.SessionSource
import app.liftos.domain.model.SessionStatus
import app.liftos.domain.model.SetType
import app.liftos.domain.model.WorkoutPlan
import app.liftos.domain.model.WorkoutSession
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Best-effort adapter for backups exported by the earlier prototype (built with Floot).
 *
 * The prototype's top-level shape is: version, plans, customExercises, sessions, measurements,
 * events, active, name. A session has id, planId, title, start, end, notes, energy and
 * exercises[{exerciseId, sets[{id, weight, reps, rir, done}]}]. Field types and the plan layout
 * were described but not verified against a real export, so every access here is tolerant:
 * unknown/missing fields are skipped and reported as warnings instead of failing the import.
 *
 * Imported ids are prefixed with "floot:" so importing the same file twice is idempotent.
 */
object FlootImporter {

    const val ID_PREFIX = "floot:"

    fun looksLikeFloot(root: JsonObject): Boolean =
        root["sessions"] is JsonArray && (root["plans"] is JsonArray || root["customExercises"] is JsonArray)

    fun convert(
        root: JsonObject,
        library: List<Exercise>,
        zone: ZoneId,
        nowMs: Long,
    ): ParseResult {
        val warnings = mutableListOf<String>()
        val libraryById = library.associateBy { it.id }
        val libraryBySlug = library.associateBy { slug(it.nameEn) }

        // --- custom exercises -------------------------------------------------------------
        val exercises = mutableListOf<Exercise>()
        val exerciseIdMap = HashMap<String, String>() // foreign id -> LiftOS id
        root.arr("customExercises").forEach { el ->
            val o = el as? JsonObject ?: return@forEach
            val fid = o.str("id") ?: return@forEach
            val name = o.str("name", "title", "nameHe") ?: fid
            val newId = ID_PREFIX + fid
            exerciseIdMap[fid] = newId
            exercises += Exercise(
                id = newId,
                nameHe = name,
                nameEn = o.str("nameEn") ?: name,
                primaryMuscle = muscleFrom(o.str("muscle", "primaryMuscle", "group")),
                description = o.str("description", "notes").orEmpty(),
                videoUrl = o.str("video", "videoUrl", "url")?.takeIf { it.startsWith("http") },
                isBuiltIn = false,
                createdAt = nowMs,
                updatedAt = nowMs,
            )
        }

        fun resolveExercise(foreignId: String?, fallbackName: String? = null): String? {
            if (foreignId == null) return null
            exerciseIdMap[foreignId]?.let { return it }
            libraryById[foreignId]?.let { exerciseIdMap[foreignId] = it.id; return it.id }
            libraryBySlug[slug(foreignId)]?.let { exerciseIdMap[foreignId] = it.id; return it.id }
            fallbackName?.let { name -> libraryBySlug[slug(name)]?.let { exerciseIdMap[foreignId] = it.id; return it.id } }
            // Unknown exercise: keep the history by creating a custom exercise for it.
            val newId = ID_PREFIX + foreignId
            val name = humanize(foreignId)
            exercises += Exercise(
                id = newId, nameHe = name, nameEn = name, primaryMuscle = MuscleGroup.OTHER,
                isBuiltIn = false, createdAt = nowMs, updatedAt = nowMs,
            )
            exerciseIdMap[foreignId] = newId
            warnings += "התרגיל '$foreignId' לא נמצא בספרייה ונוצר כתרגיל אישי."
            return newId
        }

        // --- plans ----------------------------------------------------------------------------
        val plans = mutableListOf<WorkoutPlan>()
        val plannedWorkouts = mutableListOf<PlannedWorkout>()
        val plannedExercises = mutableListOf<PlannedExercise>()
        val planIdMap = HashMap<String, String>()
        root.arr("plans").forEachIndexed { pi, el ->
            val o = el as? JsonObject ?: return@forEachIndexed
            val fid = o.str("id") ?: "plan$pi"
            val planId = ID_PREFIX + fid
            planIdMap[fid] = planId
            plans += WorkoutPlan(
                id = planId,
                name = o.str("name", "title") ?: "תוכנית מיובאת ${pi + 1}",
                description = o.str("description", "notes").orEmpty(),
                sortOrder = pi,
                createdAt = nowMs,
                updatedAt = nowMs,
            )
            val dayArray = listOf("days", "workouts", "templates", "routines", "sessions")
                .firstNotNullOfOrNull { k -> (o[k] as? JsonArray)?.takeIf { it.isNotEmpty() } }
            if (dayArray == null) {
                warnings += "מבנה התוכנית '${plans.last().name}' לא זוהה; יובאה ללא ימי אימון."
                return@forEachIndexed
            }
            dayArray.forEachIndexed { di, dayEl ->
                val d = dayEl as? JsonObject ?: return@forEachIndexed
                val workoutId = ID_PREFIX + (d.str("id") ?: "${fid}-day$di")
                plannedWorkouts += PlannedWorkout(
                    id = workoutId,
                    planId = planId,
                    name = d.str("name", "title") ?: "יום ${di + 1}",
                    orderIndex = di,
                    weekday = d.int("weekday", "day", "dow")?.let(::isoWeekday),
                )
                d.arr("exercises").forEachIndexed { ei, exEl ->
                    val e = exEl as? JsonObject ?: return@forEachIndexed
                    val exId = resolveExercise(e.str("exerciseId", "id"), e.str("name")) ?: return@forEachIndexed
                    val setsCount = e.int("sets", "targetSets") ?: (e["sets"] as? JsonArray)?.size ?: 3
                    plannedExercises += PlannedExercise(
                        id = ID_PREFIX + "${workoutId.removePrefix(ID_PREFIX)}-ex$ei",
                        plannedWorkoutId = workoutId,
                        exerciseId = exId,
                        orderIndex = ei,
                        targetSets = setsCount.coerceIn(1, 20),
                        repsMin = e.int("repsMin", "repMin", "minReps", "min") ?: 8,
                        repsMax = e.int("repsMax", "repMax", "maxReps", "max") ?: 12,
                        restSeconds = e.int("rest", "restSeconds", "restSec") ?: 90,
                        notes = e.str("notes", "note").orEmpty(),
                    )
                }
            }
        }

        // --- sessions -----------------------------------------------------------------------
        val sessions = mutableListOf<WorkoutSession>()
        val sessionExercises = mutableListOf<SessionExercise>()
        val sets = mutableListOf<ExerciseSet>()
        var skippedSessions = 0
        root.arr("sessions").forEachIndexed { si, el ->
            val o = el as? JsonObject
            if (o == null) { skippedSessions++; return@forEachIndexed }
            val start = o.time("start", "startedAt", "date", zone = zone)
            if (start == null) { skippedSessions++; return@forEachIndexed }
            val end = o.time("end", "endedAt", zone = zone)
            val fid = o.str("id") ?: "session$si"
            val sessionId = ID_PREFIX + fid
            val title = o.str("title", "name") ?: "אימון"
            val planFid = o.str("planId")
            sessions += WorkoutSession(
                id = sessionId,
                planId = planFid?.let { planIdMap[it] },
                planName = planFid?.let { pid -> plans.firstOrNull { it.id == planIdMap[pid] }?.name }.orEmpty(),
                title = title,
                status = if (end != null) SessionStatus.COMPLETED else SessionStatus.COMPLETED,
                startedAt = start,
                endedAt = end ?: start,
                localDate = TimeUtils.localDateString(start, zone),
                zoneId = zone.id,
                notes = o.str("notes", "note").orEmpty(),
                energyRating = o.int("energy")?.takeIf { it in 1..5 },
                source = SessionSource.IMPORT,
                createdAt = nowMs,
                updatedAt = nowMs,
            )
            o.arr("exercises").forEachIndexed { ei, exEl ->
                val e = exEl as? JsonObject ?: return@forEachIndexed
                val foreignExId = e.str("exerciseId", "id")
                val exId = resolveExercise(foreignExId, e.str("name"))
                val libEx = exId?.let { id -> libraryById[id] ?: exercises.firstOrNull { it.id == id } }
                val seId = ID_PREFIX + "$fid-ex$ei"
                sessionExercises += SessionExercise(
                    id = seId,
                    sessionId = sessionId,
                    exerciseId = exId,
                    nameHe = libEx?.nameHe ?: e.str("name") ?: humanize(foreignExId ?: "תרגיל"),
                    nameEn = libEx?.nameEn ?: "",
                    primaryMuscle = libEx?.primaryMuscle ?: MuscleGroup.OTHER,
                    trackingType = libEx?.trackingType ?: app.liftos.domain.model.TrackingType.WEIGHT_REPS,
                    isUnilateral = libEx?.isUnilateral ?: false,
                    orderIndex = ei,
                    notes = e.str("notes", "note").orEmpty(),
                )
                e.arr("sets").forEachIndexed { k, setEl ->
                    val s = setEl as? JsonObject ?: return@forEachIndexed
                    val done = s.bool("done", "completed") ?: true
                    sets += ExerciseSet(
                        id = ID_PREFIX + (s.str("id") ?: "$fid-ex$ei-s$k"),
                        sessionExerciseId = seId,
                        orderIndex = k,
                        setType = SetType.WORKING,
                        weightKg = s.dbl("weight"),
                        reps = s.int("reps"),
                        rir = s.int("rir"),
                        isCompleted = done,
                        completedAt = if (done) (end ?: start) else null,
                    )
                }
            }
        }
        if (skippedSessions > 0) warnings += "$skippedSessions אימונים דולגו (תאריך התחלה חסר או לא תקין)."

        // --- measurements and events ------------------------------------------------------
        val measurements = root.arr("measurements").mapIndexedNotNull { i, el ->
            val o = el as? JsonObject ?: return@mapIndexedNotNull null
            val at = o.time("date", "ts", "time", "at", "measuredAt", zone = zone) ?: return@mapIndexedNotNull null
            BodyMeasurement(
                id = ID_PREFIX + (o.str("id") ?: "m$i-$at"),
                measuredAt = at,
                localDate = TimeUtils.localDateString(at, zone),
                zoneId = zone.id,
                weightKg = o.dbl("weight", "weightKg", "bodyWeight"),
                waistCm = o.dbl("waist"),
                chestCm = o.dbl("chest"),
                armCm = o.dbl("arm"),
                thighCm = o.dbl("thigh"),
                hipsCm = o.dbl("hips", "pelvis"),
                neckCm = o.dbl("neck"),
                bodyFatPct = o.dbl("bodyFat", "fat", "bodyFatPct"),
                notes = o.str("notes", "note").orEmpty(),
                createdAt = nowMs,
                updatedAt = nowMs,
            )
        }.filter { it.hasAnyValue || it.notes.isNotBlank() }

        val events = root.arr("events").mapIndexedNotNull { i, el ->
            val o = el as? JsonObject ?: return@mapIndexedNotNull null
            val at = o.time("date", "start", "time", "at", zone = zone) ?: return@mapIndexedNotNull null
            ActivityEvent(
                id = ID_PREFIX + (o.str("id") ?: "e$i-$at"),
                type = activityFrom(o.str("type", "kind")),
                title = o.str("title", "name", "text").orEmpty(),
                notes = o.str("notes", "note").orEmpty(),
                startsAt = at,
                localDate = TimeUtils.localDateString(at, zone),
                zoneId = zone.id,
                durationMin = o.int("duration", "durationMin"),
                createdAt = nowMs,
                updatedAt = nowMs,
            )
        }

        if (root["active"] != null && root["active"] !is JsonNull) {
            warnings += "אימון פעיל מהאפליקציה הקודמת לא יובא (ניתן להזין אותו ידנית)."
        }

        val file = BackupFile(
            exportedAt = nowMs,
            appVersion = "floot-import",
            data = BackupData(
                exercises = exercises.distinctBy { it.id },
                plans = plans,
                plannedWorkouts = plannedWorkouts,
                plannedExercises = plannedExercises,
                sessions = sessions,
                sessionExercises = sessionExercises,
                sets = sets,
                measurements = measurements,
                events = events,
            ),
        )
        return ParseResult.Success(file, BackupSource.FLOOT, warnings)
    }

    // ---- tolerant JSON helpers -------------------------------------------------------------

    private fun JsonObject.arr(key: String): List<JsonElement> = (this[key] as? JsonArray) ?: emptyList()

    private fun JsonElement?.primitive(): JsonPrimitive? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }

    private fun JsonObject.str(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { k -> this[k].primitive()?.content?.takeIf { it.isNotBlank() } }

    private fun JsonObject.dbl(vararg keys: String): Double? =
        keys.firstNotNullOfOrNull { k -> this[k].primitive()?.content?.replace(',', '.')?.toDoubleOrNull() }
            ?.takeIf { it.isFinite() }

    private fun JsonObject.int(vararg keys: String): Int? = dbl(*keys)?.toInt()

    private fun JsonObject.bool(vararg keys: String): Boolean? =
        keys.firstNotNullOfOrNull { k ->
            this[k].primitive()?.content?.lowercase()?.let { c ->
                when (c) { "true", "1", "yes" -> true; "false", "0", "no" -> false; else -> null }
            }
        }

    /** Accepts epoch millis, epoch seconds, ISO instants, ISO local date-times or plain dates. */
    private fun JsonObject.time(vararg keys: String, zone: ZoneId): Long? {
        for (k in keys) {
            val p = this[k].primitive() ?: continue
            parseTime(p.content, zone)?.let { return it }
        }
        return null
    }

    internal fun parseTime(text: String, zone: ZoneId): Long? {
        val t = text.trim()
        t.toDoubleOrNull()?.let { n ->
            if (!n.isFinite() || n <= 0) return null
            return if (n > 1e11) n.toLong() else (n * 1000).toLong()
        }
        return try { Instant.parse(t).toEpochMilli() } catch (_: Exception) {
            try { LocalDateTime.parse(t).atZone(zone).toInstant().toEpochMilli() } catch (_: Exception) {
                try { LocalDate.parse(t).atStartOfDay(zone).toInstant().toEpochMilli() } catch (_: Exception) { null }
            }
        }
    }

    /** Foreign weekday: 0..6 (Sunday = 0) or 1..7; mapped to ISO 1..7 (Monday = 1). */
    private fun isoWeekday(n: Int): Int? = when (n) {
        0 -> 7
        in 1..6 -> n
        7 -> 7
        else -> null
    }

    private fun slug(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun humanize(id: String): String =
        id.replace(Regex("[-_]+"), " ").trim().replaceFirstChar { it.uppercase() }

    private fun muscleFrom(text: String?): MuscleGroup {
        val t = text?.trim()?.lowercase() ?: return MuscleGroup.OTHER
        return MuscleGroup.entries.firstOrNull { it.name.lowercase() == t || it.labelHe == text.trim() } ?: MuscleGroup.OTHER
    }

    private fun activityFrom(text: String?): ActivityType {
        val t = text?.trim()?.lowercase() ?: return ActivityType.OTHER
        return ActivityType.entries.firstOrNull { it.name.lowercase() == t || it.labelHe == text.trim() } ?: ActivityType.OTHER
    }
}
