package app.liftos.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Device-local flags that are not part of the user's data and are not included in backups
 * (seeding progress, one-time prompts, last export time). User-facing settings live in Room
 * (AppSettings) so that they are backed up and restored together with everything else.
 */
class DevicePrefs(private val store: DataStore<Preferences>) {

    private object Keys {
        val libraryHash = stringPreferencesKey("library_hash")
        val defaultPlanSeeded = booleanPreferencesKey("default_plan_seeded")
        val lastBackupAt = longPreferencesKey("last_backup_at")
        val notificationPermissionAsked = booleanPreferencesKey("notification_permission_asked")
    }

    suspend fun libraryHash(): String? = store.data.first()[Keys.libraryHash]

    suspend fun setLibraryHash(hash: String?) {
        store.edit { if (hash == null) it.remove(Keys.libraryHash) else it[Keys.libraryHash] = hash }
    }

    suspend fun defaultPlanSeeded(): Boolean = store.data.first()[Keys.defaultPlanSeeded] ?: false

    suspend fun setDefaultPlanSeeded(value: Boolean) {
        store.edit { it[Keys.defaultPlanSeeded] = value }
    }

    val lastBackupAt: Flow<Long?> = store.data.map { it[Keys.lastBackupAt] }

    suspend fun setLastBackupAt(ms: Long) {
        store.edit { it[Keys.lastBackupAt] = ms }
    }

    suspend fun clearBackupTime() {
        store.edit { it.remove(Keys.lastBackupAt) }
    }

    suspend fun notificationPermissionAsked(): Boolean = store.data.first()[Keys.notificationPermissionAsked] ?: false

    suspend fun setNotificationPermissionAsked(value: Boolean) {
        store.edit { it[Keys.notificationPermissionAsked] = value }
    }
}
