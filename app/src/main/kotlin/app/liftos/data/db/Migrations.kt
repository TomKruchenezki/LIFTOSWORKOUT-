package app.liftos.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 1 -> 2 (LiftOS 0.2.0): recovery ratings on workouts, richer body measurements, a real
 * calendar (status, repeats, links), reminder settings, and the new tables for progression decisions,
 * plan overrides, goals, progress photos, custom measurement fields and exercise preferences.
 *
 * Everything is additive: existing rows keep all their data, nothing is dropped or rewritten.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ---- columns added to existing tables ------------------------------------------------
        db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `difficultyRating` INTEGER")
        db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `qualityRating` INTEGER")
        db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `sorenessRating` INTEGER")
        db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `fatigueRating` INTEGER")
        db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `sleepHours` REAL")

        db.execSQL("ALTER TABLE `body_measurements` ADD COLUMN `shouldersCm` REAL")
        db.execSQL("ALTER TABLE `body_measurements` ADD COLUMN `conditions` TEXT NOT NULL DEFAULT ''")

        // Events that already exist were things the user logged as done.
        db.execSQL("ALTER TABLE `activity_events` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'DONE'")
        db.execSQL("ALTER TABLE `activity_events` ADD COLUMN `allDay` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `activity_events` ADD COLUMN `plannedWorkoutId` TEXT")
        db.execSQL("ALTER TABLE `activity_events` ADD COLUMN `linkedSessionId` TEXT")
        db.execSQL("ALTER TABLE `activity_events` ADD COLUMN `recurrence` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `activity_events` ADD COLUMN `recurrenceExceptions` TEXT NOT NULL DEFAULT ''")

        db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `remindersJson` TEXT NOT NULL DEFAULT ''")

        // ---- new tables --------------------------------------------------------------------------
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `progression_decisions` (`id` TEXT NOT NULL, `exerciseKey` TEXT NOT NULL, " +
                "`basedOnSessionId` TEXT NOT NULL, `kind` TEXT NOT NULL, `suggestedWeightKg` REAL, `chosenWeightKg` REAL, " +
                "`decision` TEXT NOT NULL, `decidedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_progression_decisions_exerciseKey` ON `progression_decisions` (`exerciseKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_progression_decisions_basedOnSessionId` ON `progression_decisions` (`basedOnSessionId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `exercise_prefs` (`exerciseKey` TEXT NOT NULL, `incrementKg` REAL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`exerciseKey`))",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `planned_overrides` (`id` TEXT NOT NULL, `plannedWorkoutId` TEXT NOT NULL, " +
                "`originalDate` TEXT NOT NULL, `action` TEXT NOT NULL, `newDate` TEXT, `newStartMinutes` INTEGER, " +
                "`note` TEXT NOT NULL, `linkedSessionId` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_planned_overrides_plannedWorkoutId_originalDate` " +
                "ON `planned_overrides` (`plannedWorkoutId`, `originalDate`)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_planned_overrides_newDate` ON `planned_overrides` (`newDate`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `goals` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                "`metricKey` TEXT NOT NULL, `unit` TEXT NOT NULL, `startValue` REAL NOT NULL, `targetValue` REAL NOT NULL, " +
                "`currentValue` REAL, `startDate` TEXT NOT NULL, `targetDate` TEXT, `status` TEXT NOT NULL, " +
                "`achievedAt` INTEGER, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_goals_status` ON `goals` (`status`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `goal_changes` (`id` TEXT NOT NULL, `goalId` TEXT NOT NULL, `changedAt` INTEGER NOT NULL, " +
                "`kind` TEXT NOT NULL, `oldValue` TEXT, `newValue` TEXT, `note` TEXT NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`goalId`) REFERENCES `goals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_changes_goalId` ON `goal_changes` (`goalId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `progress_photos` (`id` TEXT NOT NULL, `takenAt` INTEGER NOT NULL, `localDate` TEXT NOT NULL, " +
                "`zoneId` TEXT NOT NULL, `angle` TEXT NOT NULL, `fileName` TEXT NOT NULL, `weightKg` REAL, `notes` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_progress_photos_takenAt` ON `progress_photos` (`takenAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_progress_photos_angle` ON `progress_photos` (`angle`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `custom_metrics` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `unit` TEXT NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `measurement_custom_values` (`measurementId` TEXT NOT NULL, `metricId` TEXT NOT NULL, " +
                "`value` REAL NOT NULL, PRIMARY KEY(`measurementId`, `metricId`), " +
                "FOREIGN KEY(`measurementId`) REFERENCES `body_measurements`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`metricId`) REFERENCES `custom_metrics`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_measurement_custom_values_metricId` ON `measurement_custom_values` (`metricId`)")
    }
}
