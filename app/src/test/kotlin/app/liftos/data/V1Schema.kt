package app.liftos.data

/**
 * The exact SQL Room generated for database version 1 (copied from the exported schema of the 0.1.0
 * build). Migration tests create a version 1 database from it, fill it with data, and then open it with
 * the current code to prove that upgrading keeps everything.
 */
object V1Schema {
    val statements: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `exercises` (`id` TEXT NOT NULL, `nameHe` TEXT NOT NULL, `nameEn` TEXT NOT NULL, `primaryMuscle` TEXT NOT NULL, `secondaryMuscles` TEXT NOT NULL, `equipment` TEXT NOT NULL, `difficulty` TEXT NOT NULL, `type` TEXT NOT NULL, `trackingType` TEXT NOT NULL, `isUnilateral` INTEGER NOT NULL, `description` TEXT NOT NULL, `instructions` TEXT NOT NULL, `mistakes` TEXT NOT NULL, `safety` TEXT NOT NULL, `alternativeIds` TEXT NOT NULL, `videoUrl` TEXT, `isBuiltIn` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_exercises_primaryMuscle` ON `exercises` (`primaryMuscle`)",
        "CREATE INDEX IF NOT EXISTS `index_exercises_isArchived` ON `exercises` (`isArchived`)",
        "CREATE INDEX IF NOT EXISTS `index_exercises_nameEn` ON `exercises` (`nameEn`)",
        "CREATE TABLE IF NOT EXISTS `workout_plans` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `goal` TEXT NOT NULL, `workoutsPerWeek` INTEGER NOT NULL, `isTemplate` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_workout_plans_isTemplate` ON `workout_plans` (`isTemplate`)",
        "CREATE INDEX IF NOT EXISTS `index_workout_plans_sortOrder` ON `workout_plans` (`sortOrder`)",
        "CREATE TABLE IF NOT EXISTS `planned_workouts` (`id` TEXT NOT NULL, `planId` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `weekday` INTEGER, `notes` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`planId`) REFERENCES `workout_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_planned_workouts_planId` ON `planned_workouts` (`planId`)",
        "CREATE INDEX IF NOT EXISTS `index_planned_workouts_weekday` ON `planned_workouts` (`weekday`)",
        "CREATE TABLE IF NOT EXISTS `planned_exercises` (`id` TEXT NOT NULL, `plannedWorkoutId` TEXT NOT NULL, `exerciseId` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `targetSets` INTEGER NOT NULL, `repsMin` INTEGER NOT NULL, `repsMax` INTEGER NOT NULL, `targetWeightKg` REAL, `targetRir` INTEGER, `targetRpe` REAL, `restSeconds` INTEGER NOT NULL, `notes` TEXT NOT NULL, `warmupSets` INTEGER NOT NULL, `supersetGroup` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`plannedWorkoutId`) REFERENCES `planned_workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        "CREATE INDEX IF NOT EXISTS `index_planned_exercises_plannedWorkoutId` ON `planned_exercises` (`plannedWorkoutId`)",
        "CREATE INDEX IF NOT EXISTS `index_planned_exercises_exerciseId` ON `planned_exercises` (`exerciseId`)",
        "CREATE TABLE IF NOT EXISTS `workout_sessions` (`id` TEXT NOT NULL, `planId` TEXT, `plannedWorkoutId` TEXT, `planName` TEXT NOT NULL, `title` TEXT NOT NULL, `status` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `localDate` TEXT NOT NULL, `zoneId` TEXT NOT NULL, `notes` TEXT NOT NULL, `energyRating` INTEGER, `moodRating` INTEGER, `restTotalMs` INTEGER, `restEndsAt` INTEGER, `restPausedRemainingMs` INTEGER, `restLabel` TEXT NOT NULL, `source` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_workout_sessions_status` ON `workout_sessions` (`status`)",
        "CREATE INDEX IF NOT EXISTS `index_workout_sessions_startedAt` ON `workout_sessions` (`startedAt`)",
        "CREATE INDEX IF NOT EXISTS `index_workout_sessions_localDate` ON `workout_sessions` (`localDate`)",
        "CREATE INDEX IF NOT EXISTS `index_workout_sessions_plannedWorkoutId` ON `workout_sessions` (`plannedWorkoutId`)",
        "CREATE TABLE IF NOT EXISTS `session_exercises` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `exerciseId` TEXT, `plannedExerciseId` TEXT, `nameHe` TEXT NOT NULL, `nameEn` TEXT NOT NULL, `primaryMuscle` TEXT NOT NULL, `trackingType` TEXT NOT NULL, `isUnilateral` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL, `plannedSets` INTEGER NOT NULL, `repsMin` INTEGER, `repsMax` INTEGER, `targetWeightKg` REAL, `targetRir` INTEGER, `targetRpe` REAL, `restSeconds` INTEGER NOT NULL, `notes` TEXT NOT NULL, `skipped` INTEGER NOT NULL, `skipReason` TEXT NOT NULL, `supersetGroup` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`sessionId`) REFERENCES `workout_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        "CREATE INDEX IF NOT EXISTS `index_session_exercises_sessionId` ON `session_exercises` (`sessionId`)",
        "CREATE INDEX IF NOT EXISTS `index_session_exercises_exerciseId` ON `session_exercises` (`exerciseId`)",
        "CREATE TABLE IF NOT EXISTS `exercise_sets` (`id` TEXT NOT NULL, `sessionExerciseId` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `setType` TEXT NOT NULL, `side` TEXT, `weightKg` REAL, `reps` INTEGER, `durationSec` INTEGER, `rir` INTEGER, `rpe` REAL, `isCompleted` INTEGER NOT NULL, `isFailure` INTEGER NOT NULL, `completedAt` INTEGER, `notes` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`sessionExerciseId`) REFERENCES `session_exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_exercise_sets_sessionExerciseId` ON `exercise_sets` (`sessionExerciseId`)",
        "CREATE INDEX IF NOT EXISTS `index_exercise_sets_isCompleted` ON `exercise_sets` (`isCompleted`)",
        "CREATE TABLE IF NOT EXISTS `body_measurements` (`id` TEXT NOT NULL, `measuredAt` INTEGER NOT NULL, `localDate` TEXT NOT NULL, `zoneId` TEXT NOT NULL, `weightKg` REAL, `waistCm` REAL, `chestCm` REAL, `armCm` REAL, `thighCm` REAL, `hipsCm` REAL, `neckCm` REAL, `bodyFatPct` REAL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_body_measurements_measuredAt` ON `body_measurements` (`measuredAt`)",
        "CREATE INDEX IF NOT EXISTS `index_body_measurements_localDate` ON `body_measurements` (`localDate`)",
        "CREATE TABLE IF NOT EXISTS `activity_events` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL, `notes` TEXT NOT NULL, `startsAt` INTEGER NOT NULL, `localDate` TEXT NOT NULL, `zoneId` TEXT NOT NULL, `durationMin` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_activity_events_startsAt` ON `activity_events` (`startsAt`)",
        "CREATE INDEX IF NOT EXISTS `index_activity_events_localDate` ON `activity_events` (`localDate`)",
        "CREATE TABLE IF NOT EXISTS `app_settings` (`id` INTEGER NOT NULL, `themeMode` TEXT NOT NULL, `effortMode` TEXT NOT NULL, `vibrateOnSetComplete` INTEGER NOT NULL, `vibrateOnRestEnd` INTEGER NOT NULL, `restNotificationEnabled` INTEGER NOT NULL, `autoStartRest` INTEGER NOT NULL, `keepScreenOn` INTEGER NOT NULL, `defaultRestCompoundSec` INTEGER NOT NULL, `defaultRestIsolationSec` INTEGER NOT NULL, `activePlanId` TEXT, `weeklyGoalWorkouts` INTEGER NOT NULL, `language` TEXT NOT NULL, PRIMARY KEY(`id`))",
    )

    /** A small but complete data set: one of everything the 0.1.0 app could have stored. */
    val sampleData: List<String> = listOf(
        "INSERT INTO exercises (id,nameHe,nameEn,primaryMuscle,secondaryMuscles,equipment,difficulty,type,trackingType,isUnilateral,description,instructions,mistakes,safety,alternativeIds,videoUrl,isBuiltIn,isArchived,createdAt,updatedAt) VALUES ('bench','לחיצת חזה במוט','Barbell Bench Press','CHEST','TRICEPS,SHOULDERS','BARBELL','INTERMEDIATE','COMPOUND','WEIGHT_REPS',0,'','[]','[]','[]','[]',NULL,1,0,1,1)",
        "INSERT INTO workout_plans (id,name,description,goal,workoutsPerWeek,isTemplate,sortOrder,createdAt,updatedAt) VALUES ('plan','פלג עליון','','',4,0,0,1,1)",
        "INSERT INTO planned_workouts (id,planId,name,description,orderIndex,weekday,notes) VALUES ('pw','plan','Upper A','',0,7,'')",
        "INSERT INTO planned_exercises (id,plannedWorkoutId,exerciseId,orderIndex,targetSets,repsMin,repsMax,targetWeightKg,targetRir,targetRpe,restSeconds,notes,warmupSets,supersetGroup) VALUES ('pe','pw','bench',0,3,8,12,60.0,2,NULL,120,'',0,NULL)",
        "INSERT INTO workout_sessions (id,planId,plannedWorkoutId,planName,title,status,startedAt,endedAt,localDate,zoneId,notes,energyRating,moodRating,restTotalMs,restEndsAt,restPausedRemainingMs,restLabel,source,createdAt,updatedAt) VALUES ('s1','plan','pw','פלג עליון','Upper A','COMPLETED',1760000000000,1760003600000,'2025-10-09','Asia/Jerusalem','הערה ישנה',4,3,NULL,NULL,NULL,'','LIVE',1,1)",
        "INSERT INTO session_exercises (id,sessionId,exerciseId,plannedExerciseId,nameHe,nameEn,primaryMuscle,trackingType,isUnilateral,orderIndex,plannedSets,repsMin,repsMax,targetWeightKg,targetRir,targetRpe,restSeconds,notes,skipped,skipReason,supersetGroup) VALUES ('se1','s1','bench','pe','לחיצת חזה במוט','Barbell Bench Press','CHEST','WEIGHT_REPS',0,0,3,8,12,60.0,2,NULL,120,'',0,'',NULL)",
        "INSERT INTO exercise_sets (id,sessionExerciseId,orderIndex,setType,side,weightKg,reps,durationSec,rir,rpe,isCompleted,isFailure,completedAt,notes) VALUES ('st1','se1',0,'WORKING',NULL,60.0,10,NULL,2,NULL,1,0,1760000100000,'')",
        "INSERT INTO exercise_sets (id,sessionExerciseId,orderIndex,setType,side,weightKg,reps,durationSec,rir,rpe,isCompleted,isFailure,completedAt,notes) VALUES ('st2','se1',1,'WORKING',NULL,60.0,9,NULL,1,NULL,1,0,1760000200000,'')",
        "INSERT INTO body_measurements (id,measuredAt,localDate,zoneId,weightKg,waistCm,chestCm,armCm,thighCm,hipsCm,neckCm,bodyFatPct,notes,createdAt,updatedAt) VALUES ('m1',1759900000000,'2025-10-08','Asia/Jerusalem',84.5,92.0,NULL,NULL,NULL,NULL,NULL,NULL,'בוקר',1,1)",
        "INSERT INTO activity_events (id,type,title,notes,startsAt,localDate,zoneId,durationMin,createdAt,updatedAt) VALUES ('ev1','WALK','הליכה','',1759950000000,'2025-10-08','Asia/Jerusalem',40,1,1)",
        "INSERT INTO app_settings (id,themeMode,effortMode,vibrateOnSetComplete,vibrateOnRestEnd,restNotificationEnabled,autoStartRest,keepScreenOn,defaultRestCompoundSec,defaultRestIsolationSec,activePlanId,weeklyGoalWorkouts,language) VALUES (1,'LIGHT','RPE',0,1,1,1,0,200,100,'plan',3,'he')",
    )
}
