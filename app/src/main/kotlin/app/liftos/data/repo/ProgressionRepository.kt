package app.liftos.data.repo

import app.liftos.data.TimeSource
import app.liftos.data.db.AppDatabase
import app.liftos.data.db.toDomain
import app.liftos.data.db.toEntity
import app.liftos.domain.logic.IdGenerator
import app.liftos.domain.model.ExercisePrefs
import app.liftos.domain.progression.DecisionType
import app.liftos.domain.progression.ProgressionDecision
import app.liftos.domain.progression.ProgressionSuggestion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What the user decided about suggestions, and per-exercise progression preferences. */
class ProgressionRepository(
    db: AppDatabase,
    private val time: TimeSource = TimeSource.Default,
    private val ids: IdGenerator = IdGenerator.Random,
) {
    private val dao = db.progressionDao()

    fun observeDecisions(): Flow<List<ProgressionDecision>> = dao.observeDecisions().map { l -> l.map { it.toDomain() } }

    fun observePrefs(): Flow<Map<String, ExercisePrefs>> =
        dao.observePrefs().map { l -> l.map { it.toDomain() }.associateBy { it.exerciseKey } }

    suspend fun getPref(exerciseKey: String): ExercisePrefs? = dao.getPref(exerciseKey)?.toDomain()

    /** Records what happened to [suggestion]; nothing is changed in any workout by this call. */
    suspend fun record(suggestion: ProgressionSuggestion, decision: DecisionType, chosenWeightKg: Double? = null) {
        val based = suggestion.basedOnSessionId ?: return
        dao.upsertDecision(
            ProgressionDecision(
                id = ids.newId(), exerciseKey = suggestion.exerciseKey, basedOnSessionId = based, kind = suggestion.kind,
                suggestedWeightKg = suggestion.suggestedWeightKg, chosenWeightKg = chosenWeightKg,
                decision = decision, decidedAt = time.nowMs(),
            ).toEntity(),
        )
    }

    /** Sets (or clears with null) the smallest weight jump the user wants for an exercise. */
    suspend fun setIncrement(exerciseKey: String, incrementKg: Double?) {
        if (incrementKg == null) dao.deletePref(exerciseKey)
        else dao.upsertPref(ExercisePrefs(exerciseKey, incrementKg.coerceIn(0.25, 25.0), time.nowMs()).toEntity())
    }
}
