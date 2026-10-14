package app.liftos.data.repo

import app.liftos.data.db.AppDatabase
import app.liftos.data.db.toDomain
import app.liftos.data.db.toFact
import app.liftos.domain.analytics.PersonalRecordEngine
import app.liftos.domain.analytics.RecordsResult
import app.liftos.domain.analytics.SessionFact
import app.liftos.domain.analytics.SetFact
import app.liftos.domain.analytics.VolumeAnalytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Everything the analytics screens need, computed from the database. All of it is derived data:
 * nothing here is stored, so editing or deleting a workout updates every screen at once.
 */
data class AnalyticsData(
    val facts: List<SetFact>,
    val sessions: List<SessionFact>,
    val records: RecordsResult,
) {
    val earliest: LocalDate? get() = VolumeAnalytics.earliestDate(sessions, facts)
    val isEmpty: Boolean get() = sessions.isEmpty()

    companion object {
        val EMPTY = AnalyticsData(emptyList(), emptyList(), RecordsResult(emptyList(), emptyList()))
    }
}

class AnalyticsRepository(db: AppDatabase) {
    private val analytics = db.analyticsDao()
    private val sessions = db.sessionDao()

    fun observeFacts(): Flow<List<SetFact>> =
        analytics.observeFacts().map { rows -> rows.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    fun observeSessions(): Flow<List<SessionFact>> =
        sessions.observeCompleted().map { list -> list.map { it.toFact() } }.flowOn(Dispatchers.Default)

    /** Facts, sessions and the personal-record history, recomputed whenever any workout data changes. */
    fun observeData(): Flow<AnalyticsData> =
        combine(observeFacts(), observeSessions()) { facts, sess ->
            AnalyticsData(facts, sess, PersonalRecordEngine.compute(facts))
        }.conflate().flowOn(Dispatchers.Default)

    suspend fun getData(): AnalyticsData = withContext(Dispatchers.Default) {
        val facts = analytics.getFacts().map { it.toDomain() }
        val sess = sessions.getCompleted().map { it.toFact() }
        AnalyticsData(facts, sess, PersonalRecordEngine.compute(facts))
    }

    /** History of the given library exercises before [excludeSessionId] (ad-hoc exercises are filtered by the caller). */
    suspend fun historyFor(exerciseIds: List<String>, excludeSessionId: String): List<SetFact> =
        withContext(Dispatchers.Default) {
            analytics.getFactsForExercises(exerciseIds, excludeSessionId).map { it.toDomain() }
        }

    /** Headers of the given workouts (used for energy / fatigue notes next to a suggestion). */
    suspend fun sessionFacts(ids: Collection<String>): List<SessionFact> =
        ids.distinct().mapNotNull { sessions.getSession(it)?.toFact() }

    /** Completed sets of one workout, including a workout that is still running. */
    fun observeFactsOfSession(sessionId: String): Flow<List<SetFact>> =
        analytics.observeFactsOfSession(sessionId).map { rows -> rows.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    suspend fun factsOfSession(sessionId: String): List<SetFact> =
        analytics.getFactsOfSession(sessionId).map { it.toDomain() }
}
