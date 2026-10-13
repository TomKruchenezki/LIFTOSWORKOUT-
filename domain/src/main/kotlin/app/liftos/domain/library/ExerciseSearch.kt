package app.liftos.domain.library

import app.liftos.domain.model.Difficulty
import app.liftos.domain.model.Equipment
import app.liftos.domain.model.Exercise
import app.liftos.domain.model.ExerciseType
import app.liftos.domain.model.MuscleGroup

data class ExerciseFilter(
    val query: String = "",
    val muscle: MuscleGroup? = null,
    val equipment: Equipment? = null,
    val type: ExerciseType? = null,
    val difficulty: Difficulty? = null,
    val includeArchived: Boolean = false,
    val onlyCustom: Boolean = false,
) {
    val isActive: Boolean
        get() = query.isNotBlank() || muscle != null || equipment != null || type != null || difficulty != null || onlyCustom
}

object ExerciseSearch {

    /** Search + filter. A muscle filter matches the primary muscle or any secondary muscle. */
    fun apply(all: List<Exercise>, f: ExerciseFilter): List<Exercise> {
        val q = normalize(f.query)
        val terms = if (q.isEmpty()) emptyList() else q.split(' ').filter { it.isNotEmpty() }
        return all.asSequence()
            .filter { f.includeArchived || !it.isArchived }
            .filter { !f.onlyCustom || !it.isBuiltIn }
            .filter { f.muscle == null || it.primaryMuscle == f.muscle || f.muscle in it.secondaryMuscles }
            .filter { f.equipment == null || it.equipment == f.equipment }
            .filter { f.type == null || it.type == f.type }
            .filter { f.difficulty == null || it.difficulty == f.difficulty }
            .filter { ex ->
                if (terms.isEmpty()) true else {
                    val haystack = normalize("${ex.nameHe} ${ex.nameEn} ${ex.primaryMuscle.labelHe} ${ex.equipment.labelHe}")
                    terms.all { haystack.contains(it) }
                }
            }
            .sortedWith(compareByDescending<Exercise> { relevance(it, q) }.thenBy { it.nameHe })
            .toList()
    }

    private fun relevance(ex: Exercise, q: String): Int {
        if (q.isEmpty()) return 0
        val he = normalize(ex.nameHe)
        val en = normalize(ex.nameEn)
        return when {
            he == q || en == q -> 3
            he.startsWith(q) || en.startsWith(q) -> 2
            he.contains(q) || en.contains(q) -> 1
            else -> 0
        }
    }

    /** Lower-cases, strips Hebrew niqqud/punctuation and collapses whitespace. */
    fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("[\\u0591-\\u05C7]"), "")
            .replace(Regex("[\"'׳״`.,()-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
