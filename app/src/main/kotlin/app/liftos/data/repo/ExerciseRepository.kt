package app.liftos.data.repo

import app.liftos.data.TimeSource
import app.liftos.data.db.AppDatabase
import app.liftos.data.db.toDomain
import app.liftos.data.db.toEntity
import app.liftos.domain.library.YouTube
import app.liftos.domain.logic.IdGenerator
import app.liftos.domain.model.Exercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class DeleteOutcome { DELETED, ARCHIVED, NOT_ALLOWED }

class ExerciseRepository(
    private val db: AppDatabase,
    private val time: TimeSource = TimeSource.Default,
    private val ids: IdGenerator = IdGenerator.Random,
) {
    private val dao = db.exerciseDao()

    fun observeAll(): Flow<List<Exercise>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observe(id: String): Flow<Exercise?> = dao.observe(id).map { it?.toDomain() }

    suspend fun get(id: String): Exercise? = dao.get(id)?.toDomain()

    suspend fun getAll(): List<Exercise> = dao.getAll().map { it.toDomain() }

    /** Creates a user exercise. A video link is stored only if it is a well-formed http(s) URL. */
    suspend fun createCustom(draft: Exercise): String {
        val now = time.nowMs()
        val id = ids.newId()
        dao.upsert(
            draft.copy(
                id = id,
                isBuiltIn = false,
                isArchived = false,
                videoUrl = draft.videoUrl?.trim()?.takeIf { YouTube.isValidWebUrl(it) },
                createdAt = now,
                updatedAt = now,
            ).toEntity(),
        )
        return id
    }

    /** Built-in exercises are read-only; use [duplicateAsCustom] to get an editable copy. */
    suspend fun updateCustom(exercise: Exercise): Boolean {
        val existing = dao.get(exercise.id)?.toDomain() ?: return false
        if (existing.isBuiltIn) return false
        dao.upsert(
            exercise.copy(
                isBuiltIn = false,
                videoUrl = exercise.videoUrl?.trim()?.takeIf { YouTube.isValidWebUrl(it) },
                createdAt = existing.createdAt,
                updatedAt = time.nowMs(),
            ).toEntity(),
        )
        return true
    }

    suspend fun duplicateAsCustom(id: String): String? {
        val src = get(id) ?: return null
        return createCustom(src.copy(nameHe = src.nameHe + " (עותק)", nameEn = src.nameEn + " (copy)", alternativeIds = emptyList()))
    }

    /**
     * Unused custom exercises are deleted. Exercises that appear in a plan or in history are
     * archived (hidden from pickers) so that nothing referencing them breaks.
     */
    suspend fun delete(id: String): DeleteOutcome {
        val ex = get(id) ?: return DeleteOutcome.NOT_ALLOWED
        if (ex.isBuiltIn) return DeleteOutcome.NOT_ALLOWED
        return if (dao.sessionUsage(id) > 0 || dao.planUsage(id) > 0) {
            dao.upsert(ex.copy(isArchived = true, updatedAt = time.nowMs()).toEntity())
            DeleteOutcome.ARCHIVED
        } else {
            dao.delete(id)
            DeleteOutcome.DELETED
        }
    }

    suspend fun restore(id: String) {
        val ex = get(id) ?: return
        dao.upsert(ex.copy(isArchived = false, updatedAt = time.nowMs()).toEntity())
    }
}
