package app.liftos.domain.export

import app.liftos.domain.backup.BackupCodec
import app.liftos.domain.model.WorkoutDetail
import kotlinx.serialization.Serializable

/** Export of a single workout (optionally with the previous workout of the same type). */
@Serializable
data class SessionExportFile(
    val format: String = FORMAT,
    val schemaVersion: Int = 1,
    val exportedAt: Long,
    val workout: WorkoutDetail,
    val previous: WorkoutDetail? = null,
) {
    companion object {
        const val FORMAT = "liftos-session"
    }
}

object SessionJson {
    fun encode(file: SessionExportFile): String =
        BackupCodec.json.encodeToString(SessionExportFile.serializer(), file)

    fun decode(text: String): SessionExportFile =
        BackupCodec.json.decodeFromString(SessionExportFile.serializer(), text)
}
