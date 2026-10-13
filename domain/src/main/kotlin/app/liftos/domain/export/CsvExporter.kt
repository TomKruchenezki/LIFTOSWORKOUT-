package app.liftos.domain.export

import app.liftos.domain.logic.Formatters
import app.liftos.domain.logic.SetEditing
import app.liftos.domain.logic.TimeUtils
import app.liftos.domain.logic.VolumeCalculator
import app.liftos.domain.model.BodyMeasurement
import app.liftos.domain.model.WorkoutDetail

/** RFC 4180 CSV. The file writer should prepend a UTF-8 BOM so Excel shows Hebrew correctly. */
object CsvExporter {

    const val SESSIONS_HEADER =
        "date,start_time,end_time,duration_min,workout,plan,exercise_he,exercise_en,set_number," +
            "set_type,side,weight_kg,reps,duration_sec,rir,rpe,completed,failure,volume_kg,set_notes,exercise_notes,workout_notes"

    fun escape(field: String): String {
        val needsQuotes = field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + field.replace("\"", "\"\"") + "\"" else field
    }

    fun row(vararg fields: String): String = fields.joinToString(",") { escape(it) }

    /** One row per set across all given workouts, oldest first. */
    fun sessions(workouts: List<WorkoutDetail>): String {
        val sb = StringBuilder(SESSIONS_HEADER).append("\r\n")
        for (w in workouts.sortedBy { it.session.startedAt }) {
            val zone = TimeUtils.zone(w.session.zoneId)
            val s = w.session
            val durMin = s.durationMs?.let { (it / 60_000).toString() }.orEmpty()
            for (ex in w.exercises.sortedBy { it.exercise.orderIndex }) {
                val labels = SetEditing.labels(ex.exercise, ex.sets)
                for (set in ex.sets.sortedBy { it.orderIndex }) {
                    val label = labels[set.id]
                    sb.append(
                        row(
                            s.localDate,
                            Formatters.time(s.startedAt, zone),
                            s.endedAt?.let { Formatters.time(it, zone) }.orEmpty(),
                            durMin,
                            s.title,
                            s.planName,
                            ex.exercise.nameHe,
                            ex.exercise.nameEn,
                            label?.number.orEmpty(),
                            set.setType.name,
                            set.side?.name.orEmpty(),
                            Formatters.number(set.weightKg),
                            set.reps?.toString().orEmpty(),
                            set.durationSec?.toString().orEmpty(),
                            set.rir?.toString().orEmpty(),
                            Formatters.number(set.rpe, 1),
                            if (set.isCompleted) "1" else "0",
                            if (set.isFailure) "1" else "0",
                            Formatters.number(VolumeCalculator.setVolume(set)),
                            set.notes,
                            ex.exercise.notes,
                            s.notes,
                        ),
                    ).append("\r\n")
                }
            }
        }
        return sb.toString()
    }

    const val MEASUREMENTS_HEADER =
        "date,time,weight_kg,waist_cm,chest_cm,arm_cm,thigh_cm,hips_cm,neck_cm,body_fat_pct,notes"

    fun measurements(items: List<BodyMeasurement>): String {
        val sb = StringBuilder(MEASUREMENTS_HEADER).append("\r\n")
        for (m in items.sortedBy { it.measuredAt }) {
            val zone = TimeUtils.zone(m.zoneId)
            sb.append(
                row(
                    m.localDate,
                    Formatters.time(m.measuredAt, zone),
                    Formatters.number(m.weightKg),
                    Formatters.number(m.waistCm),
                    Formatters.number(m.chestCm),
                    Formatters.number(m.armCm),
                    Formatters.number(m.thighCm),
                    Formatters.number(m.hipsCm),
                    Formatters.number(m.neckCm),
                    Formatters.number(m.bodyFatPct),
                    m.notes,
                ),
            ).append("\r\n")
        }
        return sb.toString()
    }
}
