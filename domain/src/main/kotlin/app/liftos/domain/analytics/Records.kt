package app.liftos.domain.analytics

import app.liftos.domain.model.TrackingType
import java.time.LocalDate

enum class RecordKind(val labelHe: String, val unit: String, val priority: Int) {
    MAX_WEIGHT("המשקל הגבוה ביותר", "ק״ג", 1),
    BEST_E1RM("1RM משוער הגבוה ביותר", "ק״ג", 2),
    ACTUAL_1RM("חזרה בודדת (1RM בפועל)", "ק״ג", 3),
    REP_MAX_5("משקל מקסימלי ל-5 חזרות", "ק״ג", 4),
    REP_MAX_10("משקל מקסימלי ל-10 חזרות", "ק״ג", 5),
    REPS_AT_WEIGHT("שיא חזרות במשקל", "חזרות", 6),
    BEST_VOLUME("נפח מקסימלי בתרגיל באימון", "ק״ג-חזרות", 7),
    MAX_REPS("שיא חזרות בלי משקל", "חזרות", 8),
    MAX_DURATION("זמן מקסימלי", "שניות", 9),
    ;

    /** Estimates are labelled as such wherever they are shown. */
    val isEstimate: Boolean get() = this == BEST_E1RM
}

/** The current best for one exercise and kind. */
data class RecordEntry(
    val exerciseKey: String,
    val exerciseName: String,
    val kind: RecordKind,
    val value: Double,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val date: LocalDate,
    val sessionId: String,
    val sessionTitle: String,
)

/** A record that was broken in a specific workout. The first value ever logged is a baseline, not an event. */
data class PrEvent(
    val exerciseKey: String,
    val exerciseName: String,
    val kind: RecordKind,
    val newValue: Double,
    val previousValue: Double,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val date: LocalDate,
    val sessionId: String,
    val sessionTitle: String,
    val startedAt: Long,
)

data class RecordsResult(
    val current: List<RecordEntry>,
    /** Oldest first. */
    val events: List<PrEvent>,
) {
    fun eventsFor(sessionId: String): List<PrEvent> = events.filter { it.sessionId == sessionId }

    fun currentFor(exerciseKey: String): List<RecordEntry> =
        current.filter { it.exerciseKey == exerciseKey }.sortedBy { it.kind.priority }

    fun exercisesWithEventsIn(sessionId: String): Set<String> = eventsFor(sessionId).map { it.exerciseKey }.toSet()
}

object PersonalRecordEngine {
    private const val EPS = 1e-9

    /** Best of each kind inside one workout, for one exercise. */
    private data class Candidate(val value: Double, val weightKg: Double?, val reps: Int?, val durationSec: Int?)

    private class SessionBests(
        val sample: SetFact,
        val byKind: Map<RecordKind, Candidate>,
        val repsAtWeight: Map<Double, Int>,
    )

    private fun round2(v: Double) = Math.round(v * 100) / 100.0

    private fun bestsFor(rows: List<SetFact>): SessionBests {
        val working = rows.filter { it.isWorking }
        val sample = rows.first()
        val out = LinkedHashMap<RecordKind, Candidate>()
        val loaded = working.filter { it.isLoaded && (it.reps ?: 0) > 0 && it.trackingType != TrackingType.DURATION }

        loaded.maxWithOrNull(compareBy<SetFact>({ it.weightKg }, { it.reps }))?.let {
            out[RecordKind.MAX_WEIGHT] = Candidate(it.weightKg!!, it.weightKg, it.reps, null)
        }
        loaded.filter { it.estimatedOneRm != null }.maxByOrNull { it.estimatedOneRm!! }?.let {
            out[RecordKind.BEST_E1RM] = Candidate(it.estimatedOneRm!!, it.weightKg, it.reps, null)
        }
        loaded.filter { it.reps == 1 }.maxByOrNull { it.weightKg!! }?.let {
            out[RecordKind.ACTUAL_1RM] = Candidate(it.weightKg!!, it.weightKg, 1, null)
        }
        for ((kind, n) in listOf(RecordKind.REP_MAX_5 to 5, RecordKind.REP_MAX_10 to 10)) {
            loaded.filter { (it.reps ?: 0) >= n }.maxWithOrNull(compareBy<SetFact>({ it.weightKg }, { it.reps }))?.let {
                out[kind] = Candidate(it.weightKg!!, it.weightKg, it.reps, null)
            }
        }
        val volume = working.sumOf { it.volumeKg }
        if (volume > 0.0) out[RecordKind.BEST_VOLUME] = Candidate(volume, null, null, null)

        working.filter { !it.isLoaded && (it.reps ?: 0) > 0 && it.trackingType != TrackingType.DURATION }
            .maxByOrNull { it.reps!! }?.let {
                out[RecordKind.MAX_REPS] = Candidate(it.reps!!.toDouble(), null, it.reps, null)
            }
        working.filter { (it.durationSec ?: 0) > 0 }.maxByOrNull { it.durationSec!! }?.let {
            out[RecordKind.MAX_DURATION] = Candidate(it.durationSec!!.toDouble(), it.weightKg, null, it.durationSec)
        }
        val repsAt = loaded.groupBy { round2(it.weightKg!!) }.mapValues { (_, r) -> r.maxOf { it.reps!! } }
        return SessionBests(sample, out, repsAt)
    }

    /**
     * Walks the whole history oldest to newest. Exercises are matched by [SetFact.key]. Only strictly
     * better values count; a tie is not a record.
     */
    fun compute(facts: List<SetFact>): RecordsResult {
        val events = mutableListOf<PrEvent>()
        val current = mutableListOf<RecordEntry>()

        val byExercise = facts.filter { it.isWorking }.groupBy { it.key }
        for ((key, exerciseFacts) in byExercise) {
            val perSession = exerciseFacts.groupBy { it.sessionId }.values
                .map { rows -> rows.sortedBy { it.setOrder } }
                .sortedWith(compareBy({ it.first().startedAt }, { it.first().localDate }))

            val running = HashMap<RecordKind, RecordEntry>()
            val repsAtWeightBest = HashMap<Double, Int>()

            for (rows in perSession) {
                val bests = bestsFor(rows)
                val s = bests.sample
                for ((kind, cand) in bests.byKind) {
                    val prev = running[kind]
                    if (prev == null || cand.value > prev.value + EPS) {
                        if (prev != null) {
                            events += PrEvent(
                                exerciseKey = key, exerciseName = s.exerciseName, kind = kind,
                                newValue = cand.value, previousValue = prev.value,
                                weightKg = cand.weightKg, reps = cand.reps, durationSec = cand.durationSec,
                                date = s.date, sessionId = s.sessionId, sessionTitle = s.sessionTitle, startedAt = s.startedAt,
                            )
                        }
                        running[kind] = RecordEntry(
                            exerciseKey = key, exerciseName = s.exerciseName, kind = kind, value = cand.value,
                            weightKg = cand.weightKg, reps = cand.reps, durationSec = cand.durationSec,
                            date = s.date, sessionId = s.sessionId, sessionTitle = s.sessionTitle,
                        )
                    }
                }
                for ((weight, reps) in bests.repsAtWeight) {
                    val prevReps = repsAtWeightBest[weight]
                    if (prevReps == null || reps > prevReps) {
                        if (prevReps != null) {
                            events += PrEvent(
                                exerciseKey = key, exerciseName = s.exerciseName, kind = RecordKind.REPS_AT_WEIGHT,
                                newValue = reps.toDouble(), previousValue = prevReps.toDouble(),
                                weightKg = weight, reps = reps, durationSec = null,
                                date = s.date, sessionId = s.sessionId, sessionTitle = s.sessionTitle, startedAt = s.startedAt,
                            )
                        }
                        repsAtWeightBest[weight] = reps
                    }
                }
            }
            current += running.values
        }
        return RecordsResult(
            current = current.sortedWith(compareBy({ it.exerciseName }, { it.kind.priority })),
            events = events.sortedWith(compareBy({ it.startedAt }, { it.kind.priority })),
        )
    }

    /**
     * Records broken by one workout, given the history *before* it plus that workout's own sets.
     * Used live during a workout: completed sets of the active session count as soon as they are ticked.
     */
    fun eventsForSession(history: List<SetFact>, sessionFacts: List<SetFact>, sessionId: String): List<PrEvent> {
        val keys = sessionFacts.map { it.key }.toSet()
        val relevant = history.filter { it.key in keys && it.sessionId != sessionId } + sessionFacts
        return compute(relevant).eventsFor(sessionId)
    }

    /** At most [limit] events per exercise, most important kinds first - keeps the summary readable. */
    fun headline(events: List<PrEvent>, limit: Int = 3): List<PrEvent> =
        events.groupBy { it.exerciseKey }.values
            .flatMap { it.sortedBy { e -> e.kind.priority }.take(limit) }
            .sortedWith(compareBy({ it.exerciseName }, { it.kind.priority }))
}
