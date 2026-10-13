package app.liftos.domain.library

import app.liftos.domain.model.Difficulty
import app.liftos.domain.model.Equipment
import app.liftos.domain.model.Exercise
import app.liftos.domain.model.ExerciseType
import app.liftos.domain.model.MuscleGroup
import app.liftos.domain.model.TrackingType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
internal data class LibraryFile(val exercises: List<LibraryEntry> = emptyList())

/** Compact on-disk representation of a built-in exercise (see the library JSON files in app assets). */
@Serializable
internal data class LibraryEntry(
    val id: String,
    val he: String,
    val en: String,
    val primary: MuscleGroup,
    val secondary: List<MuscleGroup> = emptyList(),
    val equipment: Equipment,
    val difficulty: Difficulty = Difficulty.INTERMEDIATE,
    val type: ExerciseType = ExerciseType.COMPOUND,
    val tracking: TrackingType = TrackingType.WEIGHT_REPS,
    val unilateral: Boolean = false,
    val desc: String = "",
    val steps: List<String> = emptyList(),
    val mistakes: List<String> = emptyList(),
    val safety: List<String> = emptyList(),
    val alternatives: List<String> = emptyList(),
)

data class Library(
    val exercises: List<Exercise>,
    /** Hash of the raw content; the app re-seeds built-in exercises whenever it changes. */
    val contentHash: String,
)

object ExerciseLibrary {
    private val json = Json { ignoreUnknownKeys = true }

    /** Parses one or more library JSON documents (one per muscle group). */
    fun parse(jsonTexts: List<String>): Library {
        val digest = MessageDigest.getInstance("SHA-256")
        val all = jsonTexts.flatMap { text ->
            digest.update(text.toByteArray(Charsets.UTF_8))
            json.decodeFromString<LibraryFile>(text).exercises
        }
        val exercises = all.map { e ->
            Exercise(
                id = e.id,
                nameHe = e.he,
                nameEn = e.en,
                primaryMuscle = e.primary,
                secondaryMuscles = e.secondary,
                equipment = e.equipment,
                difficulty = e.difficulty,
                type = e.type,
                trackingType = e.tracking,
                isUnilateral = e.unilateral,
                description = e.desc,
                instructions = e.steps,
                mistakes = e.mistakes,
                safety = e.safety,
                alternativeIds = e.alternatives,
                videoUrl = null,
                isBuiltIn = true,
            )
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        return Library(exercises, hash)
    }

    private val idPattern = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

    /** Returns a list of human-readable problems; empty means the library is consistent. */
    fun validate(exercises: List<Exercise>): List<String> {
        val problems = mutableListOf<String>()
        val ids = exercises.map { it.id }
        ids.groupBy { it }.filter { it.value.size > 1 }.keys.forEach { problems += "מזהה כפול: $it" }
        exercises.map { it.nameEn.trim().lowercase() }.groupBy { it }.filter { it.value.size > 1 }.keys
            .forEach { problems += "שם באנגלית כפול: $it" }
        val idSet = ids.toSet()
        for (e in exercises) {
            val tag = e.id
            if (!idPattern.matches(e.id)) problems += "$tag: מזהה לא תקין"
            if (e.nameHe.isBlank()) problems += "$tag: חסר שם בעברית"
            if (e.nameEn.isBlank()) problems += "$tag: חסר שם באנגלית"
            if (e.primaryMuscle == MuscleGroup.OTHER) problems += "$tag: חסרה קבוצת שרירים ראשית"
            if (e.description.isBlank()) problems += "$tag: חסר תיאור"
            if (e.instructions.size < 3) problems += "$tag: פחות מ-3 שלבי ביצוע"
            if (e.mistakes.size < 2) problems += "$tag: פחות מ-2 טעויות נפוצות"
            if (e.safety.isEmpty()) problems += "$tag: חסרים דגשי בטיחות"
            if (e.alternativeIds.isEmpty()) problems += "$tag: חסרות חלופות"
            e.alternativeIds.forEach { alt ->
                if (alt == e.id) problems += "$tag: חלופה לעצמו"
                if (alt !in idSet) problems += "$tag: חלופה לא קיימת '$alt'"
            }
            if (e.secondaryMuscles.contains(e.primaryMuscle)) problems += "$tag: שריר ראשי מופיע גם כמשני"
            if (e.videoUrl != null) problems += "$tag: תרגיל מובנה לא יכול להכיל קישור וידאו מומצא"
        }
        return problems
    }
}
