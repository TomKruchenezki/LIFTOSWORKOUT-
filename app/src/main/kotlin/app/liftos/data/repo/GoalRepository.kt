package app.liftos.data.repo

import androidx.room.withTransaction
import app.liftos.data.TimeSource
import app.liftos.data.db.AppDatabase
import app.liftos.data.db.toDomain
import app.liftos.data.db.toEntity
import app.liftos.domain.goals.GoalContext
import app.liftos.domain.goals.GoalEngine
import app.liftos.domain.logic.Formatters
import app.liftos.domain.logic.IdGenerator
import app.liftos.domain.model.Goal
import app.liftos.domain.model.GoalChange
import app.liftos.domain.model.GoalChangeKind
import app.liftos.domain.model.GoalStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GoalRepository(
    private val db: AppDatabase,
    private val time: TimeSource = TimeSource.Default,
    private val ids: IdGenerator = IdGenerator.Random,
) {
    private val dao = db.goalDao()

    fun observeAll(): Flow<List<Goal>> = dao.observeAll().map { l -> l.map { it.toDomain() } }

    fun observe(id: String): Flow<Goal?> = dao.observe(id).map { it?.toDomain() }

    fun observeChanges(goalId: String): Flow<List<GoalChange>> = dao.observeChanges(goalId).map { l -> l.map { it.toDomain() } }

    suspend fun getAll(): List<Goal> = dao.getAll().map { it.toDomain() }

    suspend fun create(goal: Goal): String = db.withTransaction {
        val now = time.nowMs()
        val id = ids.newId()
        dao.upsert(goal.copy(id = id, title = goal.title.trim(), status = GoalStatus.ACTIVE, achievedAt = null, createdAt = now, updatedAt = now).toEntity())
        log(id, GoalChangeKind.CREATED, null, "${Formatters.number(goal.startValue, 2)} ← ${Formatters.number(goal.targetValue, 2)}", goal.title)
        id
    }

    /** Saves edits and writes one history entry for every field that really changed. */
    suspend fun update(goal: Goal) {
        db.withTransaction {
            val old = dao.get(goal.id)?.toDomain() ?: return@withTransaction
            val now = time.nowMs()
            dao.upsert(goal.copy(title = goal.title.trim(), createdAt = old.createdAt, updatedAt = now).toEntity())
            if (old.targetValue != goal.targetValue) {
                log(goal.id, GoalChangeKind.TARGET_CHANGED, Formatters.number(old.targetValue, 2), Formatters.number(goal.targetValue, 2))
            }
            if (old.targetDate != goal.targetDate) {
                log(goal.id, GoalChangeKind.DATE_CHANGED, old.targetDate, goal.targetDate)
            }
            if (old.currentValue != goal.currentValue) {
                log(goal.id, GoalChangeKind.VALUE_UPDATED, old.currentValue?.let { Formatters.number(it, 2) }, goal.currentValue?.let { Formatters.number(it, 2) })
            }
            if (old.status != goal.status) {
                log(goal.id, GoalChangeKind.STATUS_CHANGED, old.status.labelHe, goal.status.labelHe)
            }
            if (old.title != goal.title.trim() || old.notes != goal.notes || old.startValue != goal.startValue || old.metricKey != goal.metricKey) {
                log(goal.id, GoalChangeKind.EDITED, null, null, goal.title.trim())
            }
        }
    }

    /** For custom goals: records the value the user typed. */
    suspend fun updateCurrentValue(id: String, value: Double?) {
        val g = dao.get(id)?.toDomain() ?: return
        update(g.copy(currentValue = value))
    }

    suspend fun setStatus(id: String, status: GoalStatus) {
        val g = dao.get(id)?.toDomain() ?: return
        update(g.copy(status = status, achievedAt = if (status == GoalStatus.ACHIEVED) time.nowMs() else null))
    }

    suspend fun delete(id: String) = dao.delete(id)

    /**
     * Marks goals whose target is reached according to the user's data. Returns the goals that were just
     * achieved (so the caller can celebrate). Safe to call often: only ACTIVE goals are considered.
     */
    suspend fun markNewlyAchieved(ctx: GoalContext): List<Goal> {
        val active = dao.getAll().map { it.toDomain() }.filter { it.status == GoalStatus.ACTIVE }
        val achieved = GoalEngine.newlyAchieved(active, ctx)
        val result = mutableListOf<Goal>()
        for ((goal, progress) in achieved) {
            db.withTransaction {
                val latest = dao.get(goal.id)?.toDomain() ?: return@withTransaction
                if (latest.status != GoalStatus.ACTIVE) return@withTransaction
                val now = time.nowMs()
                val done = latest.copy(status = GoalStatus.ACHIEVED, achievedAt = now, currentValue = progress.current ?: latest.currentValue, updatedAt = now)
                dao.upsert(done.toEntity())
                log(goal.id, GoalChangeKind.STATUS_CHANGED, GoalStatus.ACTIVE.labelHe, GoalStatus.ACHIEVED.labelHe, "הושג אוטומטית לפי הנתונים")
                result += done
            }
        }
        return result
    }

    private suspend fun log(goalId: String, kind: GoalChangeKind, old: String?, new: String?, note: String = "") {
        dao.upsertChange(GoalChange(ids.newId(), goalId, time.nowMs(), kind, old, new, note).toEntity())
    }
}
