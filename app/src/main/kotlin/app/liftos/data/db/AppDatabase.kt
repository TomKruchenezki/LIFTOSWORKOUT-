package app.liftos.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutPlanEntity::class,
        PlannedWorkoutEntity::class,
        PlannedExerciseEntity::class,
        WorkoutSessionEntity::class,
        SessionExerciseEntity::class,
        ExerciseSetEntity::class,
        BodyMeasurementEntity::class,
        ActivityEventEntity::class,
        AppSettingsEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun planDao(): PlanDao
    abstract fun sessionDao(): SessionDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun activityDao(): ActivityDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "liftos.db"

        /**
         * Every schema change gets a Migration here. We deliberately never call
         * fallbackToDestructiveMigration(): upgrading the app must never erase workout history.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun build(context: Context, name: String? = FILE_NAME): AppDatabase {
            val builder = if (name == null) {
                Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            } else {
                Room.databaseBuilder(context, AppDatabase::class.java, name)
            }
            return builder.addMigrations(*MIGRATIONS).build()
        }
    }
}
