package app.liftos.domain

import app.liftos.domain.library.ExerciseLibrary
import app.liftos.domain.library.Library
import app.liftos.domain.logic.IdGenerator
import app.liftos.domain.model.ExerciseSet
import app.liftos.domain.model.ExerciseWithSets
import app.liftos.domain.model.MuscleGroup
import app.liftos.domain.model.SessionExercise
import app.liftos.domain.model.SessionStatus
import app.liftos.domain.model.SetType
import app.liftos.domain.model.TrackingType
import app.liftos.domain.model.WorkoutDetail
import app.liftos.domain.model.WorkoutSession
import java.io.File

/** Deterministic id generator: id-1, id-2, ... */
class SeqIds(private val prefix: String = "id") : IdGenerator {
    private var n = 0
    override fun newId(): String = "$prefix-${++n}"
}

object TestData {
    /** The real library JSON shipped in the app's assets (tests run with the module dir as cwd). */
    fun libraryDir(): File {
        val candidates = listOf("../app/src/main/assets/library", "app/src/main/assets/library")
        return candidates.map(::File).first { it.isDirectory }
    }

    val library: Library by lazy {
        val files = libraryDir().listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
        ExerciseLibrary.parse(files.map { it.readText(Charsets.UTF_8) })
    }

    fun set(
        id: String,
        seId: String = "se",
        order: Int = 0,
        weight: Double? = 60.0,
        reps: Int? = 10,
        type: SetType = SetType.WORKING,
        done: Boolean = true,
        rir: Int? = null,
    ) = ExerciseSet(
        id = id, sessionExerciseId = seId, orderIndex = order, setType = type,
        weightKg = weight, reps = reps, rir = rir, isCompleted = done,
    )

    fun sessionExercise(
        id: String = "se",
        sessionId: String = "s",
        exerciseId: String? = "barbell-bench-press",
        name: String = "לחיצת חזה",
        order: Int = 0,
        unilateral: Boolean = false,
        tracking: TrackingType = TrackingType.WEIGHT_REPS,
    ) = SessionExercise(
        id = id, sessionId = sessionId, exerciseId = exerciseId, nameHe = name, nameEn = "Bench Press",
        primaryMuscle = MuscleGroup.CHEST, orderIndex = order, isUnilateral = unilateral, trackingType = tracking,
        plannedSets = 3, repsMin = 5, repsMax = 8, restSeconds = 180,
    )

    fun session(
        id: String = "s",
        date: String = "2026-10-11",
        start: Long = 1_760_000_000_000L,
        end: Long? = 1_760_003_600_000L,
        title: String = "Upper A",
        plannedWorkoutId: String? = "pw-upper-a",
    ) = WorkoutSession(
        id = id, planId = "p", plannedWorkoutId = plannedWorkoutId, planName = "תוכנית", title = title,
        status = if (end == null) SessionStatus.ACTIVE else SessionStatus.COMPLETED,
        startedAt = start, endedAt = end, localDate = date, zoneId = "Asia/Jerusalem",
    )

    fun detail(
        session: WorkoutSession = session(),
        vararg exercises: ExerciseWithSets,
    ) = WorkoutDetail(session, exercises.toList())

    fun benchDetail(
        sessionId: String = "s",
        date: String = "2026-10-11",
        weights: List<Double> = listOf(60.0, 60.0, 60.0),
        reps: List<Int> = listOf(10, 9, 8),
        start: Long = 1_760_000_000_000L,
    ): WorkoutDetail {
        val se = sessionExercise(id = "$sessionId-se", sessionId = sessionId)
        val sets = weights.indices.map { i ->
            set("$sessionId-set$i", seId = se.id, order = i, weight = weights[i], reps = reps[i])
        }
        return WorkoutDetail(
            session(id = sessionId, date = date, start = start, end = start + 3_600_000L),
            listOf(ExerciseWithSets(se, sets)),
        )
    }
}
