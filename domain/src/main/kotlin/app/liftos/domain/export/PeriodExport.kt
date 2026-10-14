package app.liftos.domain.export

import app.liftos.domain.backup.BackupCodec
import app.liftos.domain.model.BodyMeasurement
import app.liftos.domain.model.WorkoutDetail
import kotlinx.serialization.Serializable

/** The file formats the user can export a report or data set in. */
enum class ExportFormat(val labelHe: String, val extension: String, val mime: String) {
    TXT("טקסט", "txt", "text/plain"),
    JSON("JSON", "json", "application/json"),
    CSV("CSV", "csv", "text/csv"),
    PDF("PDF", "pdf", "application/pdf"),
}

/**
 * Export of the workouts in a period. Body measurements are only present when the user chose to include
 * them, and notes are blanked unless the user chose to include those.
 */
@Serializable
data class PeriodExportFile(
    val format: String = FORMAT,
    val schemaVersion: Int = 1,
    val exportedAt: Long,
    val scope: String,
    val rangeStart: String,
    val rangeEnd: String,
    /** The request text that goes with the data (see [TextReport.AI_INSTRUCTION]). */
    val instruction: String,
    val workouts: List<WorkoutDetail>,
    val measurements: List<BodyMeasurement> = emptyList(),
) {
    companion object {
        const val FORMAT = "liftos-period"
    }
}

object PeriodJson {
    fun encode(file: PeriodExportFile): String = BackupCodec.json.encodeToString(PeriodExportFile.serializer(), file)

    fun decode(text: String): PeriodExportFile = BackupCodec.json.decodeFromString(PeriodExportFile.serializer(), text)
}

/** Free text the user wrote (workout, exercise, set notes and skip reasons) removed, for exports without notes. */
fun WorkoutDetail.withoutNotes(): WorkoutDetail = copy(
    session = session.copy(notes = ""),
    exercises = exercises.map { ex ->
        ex.copy(
            exercise = ex.exercise.copy(notes = "", skipReason = ""),
            sets = ex.sets.map { it.copy(notes = "") },
        )
    },
)

fun BodyMeasurement.withoutNotes(): BodyMeasurement = copy(notes = "")
