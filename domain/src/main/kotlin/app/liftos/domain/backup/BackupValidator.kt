package app.liftos.domain.backup

import app.liftos.domain.model.ExerciseSet

data class ValidationReport(
    val errors: List<String>,
    val warnings: List<String>,
    /** Data with soft problems repaired (e.g. dangling optional references cleared). */
    val cleaned: BackupData,
    val summary: BackupSummary,
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/**
 * Validates a backup *before* anything touches the database. Hard problems (errors) block the
 * import; soft problems (warnings) are repaired and reported.
 */
object BackupValidator {

    private const val MAX_WEIGHT = 2000.0
    private const val MAX_REPS = 2000
    private const val MIN_EPOCH = 946_684_800_000L // 2000-01-01
    private const val MAX_EPOCH = 4_102_444_800_000L // 2100-01-01

    fun validate(file: BackupFile, knownExerciseIds: Set<String> = emptySet()): ValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (file.format != BackupFile.FORMAT) errors += "קובץ זה אינו גיבוי של LiftOS (פורמט '${file.format}')."
        if (file.schemaVersion < 1 || file.schemaVersion > BackupFile.CURRENT_SCHEMA) {
            errors += "גרסת הגיבוי ${file.schemaVersion} אינה נתמכת (הגרסה הנתמכת: ${BackupFile.CURRENT_SCHEMA})."
        }
        val d = file.data

        fun <T> checkUnique(name: String, items: List<T>, id: (T) -> String) {
            items.groupBy(id).filter { it.value.size > 1 }.keys.take(3).forEach { errors += "מזהה כפול ב-$name: $it" }
            if (items.any { id(it).isBlank() }) errors += "נמצאה רשומה ללא מזהה ב-$name."
        }
        checkUnique("תרגילים", d.exercises) { it.id }
        checkUnique("תוכניות", d.plans) { it.id }
        checkUnique("ימי אימון בתוכנית", d.plannedWorkouts) { it.id }
        checkUnique("תרגילים בתוכנית", d.plannedExercises) { it.id }
        checkUnique("אימונים", d.sessions) { it.id }
        checkUnique("תרגילי אימון", d.sessionExercises) { it.id }
        checkUnique("סטים", d.sets) { it.id }
        checkUnique("מדידות", d.measurements) { it.id }
        checkUnique("אירועים", d.events) { it.id }
        checkUnique("שדות מדידה מותאמים", d.customMetrics) { it.id }
        checkUnique("שינויים בתוכנית שבועית", d.plannedOverrides) { it.id }
        checkUnique("יעדים", d.goals) { it.id }
        checkUnique("היסטוריית יעדים", d.goalChanges) { it.id }
        checkUnique("תמונות התקדמות", d.photos) { it.id }
        checkUnique("החלטות התקדמות", d.progressionDecisions) { it.id }

        val exerciseIds = d.exercises.map { it.id }.toSet()
        val allKnownExercises = exerciseIds + knownExerciseIds
        val planIds = d.plans.map { it.id }.toSet()
        val workoutIds = d.plannedWorkouts.map { it.id }.toSet()
        val sessionIds = d.sessions.map { it.id }.toSet()
        val sessionExerciseIds = d.sessionExercises.map { it.id }.toSet()

        // Hard referential integrity (the database enforces these as foreign keys).
        d.plannedWorkouts.filter { it.planId !in planIds }.take(3)
            .forEach { errors += "יום אימון '${it.name}' מצביע על תוכנית שאינה קיימת בקובץ." }
        d.plannedExercises.filter { it.plannedWorkoutId !in workoutIds }.take(3)
            .forEach { errors += "תרגיל בתוכנית מצביע על יום אימון שאינו קיים בקובץ." }
        d.plannedExercises.filter { it.exerciseId !in allKnownExercises }.take(3)
            .forEach { errors += "תרגיל בתוכנית מצביע על תרגיל לא מוכר: ${it.exerciseId}" }
        d.sessionExercises.filter { it.sessionId !in sessionIds }.take(3)
            .forEach { errors += "תרגיל באימון '${it.nameHe}' מצביע על אימון שאינו קיים בקובץ." }
        d.sets.filter { it.sessionExerciseId !in sessionExerciseIds }.take(3)
            .forEach { errors += "נמצא סט שאינו משויך לתרגיל באימון." }

        val goalIds = d.goals.map { it.id }.toSet()
        d.goalChanges.filter { it.goalId !in goalIds }.take(3)
            .forEach { errors += "רשומת היסטוריה של יעד מצביעה על יעד שאינו קיים בקובץ." }
        val metricIds = d.customMetrics.map { it.id }.toSet()

        // Value ranges.
        d.sessions.forEach { s ->
            if (s.startedAt !in MIN_EPOCH..MAX_EPOCH) errors += "תאריך התחלה לא סביר באימון '${s.title}'."
            if (s.endedAt != null && s.endedAt < s.startedAt) errors += "אימון '${s.title}' מסתיים לפני שהתחיל."
            if (s.energyRating != null && s.energyRating !in 1..5) warnings += "דירוג אנרגיה מחוץ לטווח באימון '${s.title}'."
        }
        d.measurements.forEach { m ->
            if (m.measuredAt !in MIN_EPOCH..MAX_EPOCH) errors += "תאריך מדידה לא סביר (${m.localDate})."
            (listOf(m.weightKg, m.waistCm, m.chestCm, m.armCm, m.thighCm, m.hipsCm, m.neckCm, m.shouldersCm, m.bodyFatPct) +
                m.customValues.values)
                .forEach { v -> if (v != null && (v < 0.0 || v > 100000.0)) errors += "ערך מדידה לא סביר ($v) בתאריך ${m.localDate}." }
        }
        d.sets.forEach { s -> checkSet(s, errors) }
        d.photos.forEach { p ->
            if (p.takenAt !in MIN_EPOCH..MAX_EPOCH) errors += "תאריך צילום לא סביר (${p.localDate})."
            if (p.fileName.isBlank() || p.fileName.contains("/") || p.fileName.contains("..")) {
                errors += "שם קובץ תמונה לא תקין: '${p.fileName}'."
            }
        }
        d.goals.forEach { g ->
            if (g.title.isBlank()) warnings += "נמצא יעד ללא שם."
        }

        // Soft repairs.
        val cleanedSessionExercises = d.sessionExercises.map { se ->
            val id = se.exerciseId
            if (id != null && id !in allKnownExercises) {
                warnings += "התרגיל '${se.nameHe}' אינו קיים בספרייה; ההיסטוריה נשמרת עם השם בלבד."
                se.copy(exerciseId = null)
            } else se
        }
        // A custom value whose metric is missing from the file can not be restored: drop it, say so.
        val cleanedMeasurements = d.measurements.map { m ->
            val unknown = m.customValues.keys.filter { it !in metricIds && it !in knownExerciseIds }
            if (unknown.isEmpty()) m else {
                warnings += "מדידה מתאריך ${m.localDate} כוללת שדה מותאם שאינו קיים בקובץ; הערך דולג."
                m.copy(customValues = m.customValues.filterKeys { it in metricIds })
            }
        }
        val cleaned = d.copy(sessionExercises = cleanedSessionExercises, measurements = cleanedMeasurements)
        return ValidationReport(errors.distinct(), warnings.distinct(), cleaned, BackupSummary.of(cleaned))
    }

    private fun checkSet(s: ExerciseSet, errors: MutableList<String>) {
        if (s.weightKg != null && (s.weightKg < 0.0 || s.weightKg > MAX_WEIGHT)) errors += "משקל לא סביר בסט: ${s.weightKg}"
        if (s.reps != null && (s.reps < 0 || s.reps > MAX_REPS)) errors += "מספר חזרות לא סביר בסט: ${s.reps}"
        if (s.rir != null && (s.rir < 0 || s.rir > 10)) errors += "ערך RIR לא סביר בסט: ${s.rir}"
        if (s.rpe != null && (s.rpe < 0.0 || s.rpe > 10.0)) errors += "ערך RPE לא סביר בסט: ${s.rpe}"
    }

}
