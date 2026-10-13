package app.liftos.domain.progression

import app.liftos.domain.model.Equipment
import kotlinx.serialization.Serializable

@Serializable
enum class SuggestionKind(val labelHe: String) {
    INCREASE_WEIGHT("אפשר לשקול העלאת משקל"),
    ADD_REPS("להוסיף חזרות באותו משקל"),
    HOLD("להישאר באותו משקל"),
    REVIEW("כדאי לבדוק התאוששות וטכניקה"),
    REDUCE_WEIGHT("לשקול הפחתת משקל"),
    INSUFFICIENT_DATA("אין מספיק נתונים"),
}

enum class Confidence(val labelHe: String) {
    LOW("ביטחון נמוך"),
    MEDIUM("ביטחון בינוני"),
    HIGH("ביטחון גבוה"),
}

/**
 * A recommendation for the NEXT workout of one exercise. It is only a suggestion: nothing is changed
 * until the user applies it, and every suggestion explains itself ([explanation] + [evidence]).
 */
data class ProgressionSuggestion(
    val exerciseKey: String,
    val exerciseName: String,
    val kind: SuggestionKind,
    val headline: String,
    val explanation: String,
    val evidence: List<String> = emptyList(),
    val cautions: List<String> = emptyList(),
    val confidence: Confidence = Confidence.LOW,
    val currentWeightKg: Double? = null,
    val suggestedWeightKg: Double? = null,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val basedOnSessionId: String? = null,
    val sessionsConsidered: Int = 0,
) {
    /** True when applying the suggestion would change the working weight. */
    val changesWeight: Boolean
        get() = suggestedWeightKg != null && suggestedWeightKg != currentWeightKg
}

/** What the user did with a suggestion. */
@Serializable
enum class DecisionType { APPLIED, IGNORED, MANUAL }

@Serializable
data class ProgressionDecision(
    val id: String,
    val exerciseKey: String,
    val basedOnSessionId: String,
    val kind: SuggestionKind,
    val suggestedWeightKg: Double?,
    val chosenWeightKg: Double?,
    val decision: DecisionType,
    val decidedAt: Long,
)

/** Plan targets for an exercise; falls back to what the last workout was snapshotted with. */
data class RepTarget(val repsMin: Int, val repsMax: Int, val sets: Int) {
    init {
        require(repsMin in 1..repsMax) { "bad rep range" }
    }

    companion object {
        val DEFAULT = RepTarget(8, 12, 3)
    }
}

object IncrementPolicy {
    /**
     * Smallest sensible jump for the equipment, in kg. Null means the load cannot be increased in kg
     * (resistance bands), so only repetitions are progressed. A user-set override always wins.
     */
    fun stepKg(equipment: Equipment?, currentKg: Double?, overrideKg: Double? = null): Double? {
        if (overrideKg != null && overrideKg > 0.0) return overrideKg
        val w = currentKg ?: 0.0
        return when (equipment) {
            Equipment.BANDS -> null
            Equipment.DUMBBELL -> when {
                w < 10.0 -> 1.0
                w < 30.0 -> 2.0
                else -> 2.5
            }
            Equipment.KETTLEBELL -> 4.0
            else -> 2.5
        }
    }

    fun round(kg: Double): Double = Math.round(kg * 4.0) / 4.0
}
