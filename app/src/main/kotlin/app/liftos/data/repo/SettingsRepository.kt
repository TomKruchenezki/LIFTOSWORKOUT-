package app.liftos.data.repo

import androidx.room.withTransaction
import app.liftos.data.db.AppDatabase
import app.liftos.data.db.toDomain
import app.liftos.data.db.toEntity
import app.liftos.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val db: AppDatabase) {
    private val dao = db.settingsDao()

    fun observe(): Flow<AppSettings> = dao.observe().map { it?.toDomain() ?: AppSettings() }

    suspend fun get(): AppSettings = dao.get()?.toDomain() ?: AppSettings()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        db.withTransaction {
            dao.upsert(transform(get()).toEntity())
        }
    }

    suspend fun ensureExists() {
        dao.insertIfAbsent(AppSettings().toEntity())
    }
}
