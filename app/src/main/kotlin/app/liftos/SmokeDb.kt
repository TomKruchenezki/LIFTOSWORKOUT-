package app.liftos

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "smoke")
data class SmokeEntity(@PrimaryKey val id: String, val value: Int)

@Dao
interface SmokeDao {
    @Query("SELECT * FROM smoke")
    fun all(): Flow<List<SmokeEntity>>
}

@Database(entities = [SmokeEntity::class], version = 1, exportSchema = true)
abstract class SmokeDb : RoomDatabase() {
    abstract fun dao(): SmokeDao
}
