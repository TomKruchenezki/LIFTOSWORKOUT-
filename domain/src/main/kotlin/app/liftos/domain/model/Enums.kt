package app.liftos.domain.model

import kotlinx.serialization.Serializable

/** Muscle groups used for exercise classification and volume distribution. */
@Serializable
enum class MuscleGroup(val labelHe: String) {
    CHEST("חזה"),
    BACK("גב"),
    SHOULDERS("כתפיים"),
    BICEPS("יד קדמית"),
    TRICEPS("יד אחורית"),
    FOREARMS("אמות"),
    TRAPS("טרפז"),
    LOWER_BACK("גב תחתון"),
    ABS("בטן"),
    QUADS("ארבע ראשי"),
    HAMSTRINGS("ירך אחורית"),
    GLUTES("ישבן"),
    ABDUCTORS("מרחיקי ירך"),
    ADDUCTORS("מקרבי ירך"),
    CALVES("תאומים"),
    FULL_BODY("כל הגוף"),
    OTHER("אחר"),
}

@Serializable
enum class Equipment(val labelHe: String) {
    BARBELL("מוט"),
    DUMBBELL("משקולות יד"),
    EZ_BAR("מוט EZ"),
    MACHINE("מכונה"),
    CABLE("כבלים"),
    SMITH_MACHINE("מכונת סמית'"),
    BODYWEIGHT("משקל גוף"),
    KETTLEBELL("קטלבל"),
    BANDS("גומיות"),
    OTHER("אחר"),
}

@Serializable
enum class Difficulty(val labelHe: String) {
    BEGINNER("מתחיל"),
    INTERMEDIATE("בינוני"),
    ADVANCED("מתקדם"),
}

@Serializable
enum class ExerciseType(val labelHe: String) {
    COMPOUND("מורכב"),
    ISOLATION("בידוד"),
}

/** What the "reps" column of a set means for an exercise. */
@Serializable
enum class TrackingType(val labelHe: String) {
    /** Weight x reps (barbell, machines, dumbbells...). */
    WEIGHT_REPS("משקל וחזרות"),

    /** Reps with optional added weight (pull-up, dip, push-up). */
    BODYWEIGHT_REPS("משקל גוף וחזרות"),

    /** A hold measured in seconds (plank). Stored in [ExerciseSet.durationSec]. */
    DURATION("זמן בשניות"),
}

@Serializable
enum class SetType(val labelHe: String, val shortHe: String) {
    WARMUP("חימום", "ח"),
    WORKING("עבודה", ""),
    DROP("דרופ סט", "D"),
}

/** For unilateral exercises each side is its own row. */
@Serializable
enum class Side(val labelHe: String, val shortHe: String) {
    RIGHT("ימין", "ימין"),
    LEFT("שמאל", "שמאל"),
}

@Serializable
enum class SessionStatus { ACTIVE, COMPLETED }

@Serializable
enum class SessionSource { LIVE, MANUAL, IMPORT }

@Serializable
enum class EffortMode(val labelHe: String) {
    RIR("RIR - חזרות שנשארו במאגר"),
    RPE("RPE - מדד מאמץ 1-10"),
}

@Serializable
enum class ThemeMode(val labelHe: String) {
    DARK("כהה"),
    LIGHT("בהיר"),
    SYSTEM("לפי המערכת"),
}

@Serializable
enum class ActivityType(val labelHe: String) {
    WALK("הליכה"),
    RUN("ריצה"),
    SWIM("שחייה"),
    SOCCER("כדורגל"),
    CYCLING("רכיבה"),
    CARDIO("אירובי"),
    REST("יום מנוחה"),
    NOTE("הערה"),
    OTHER("אחר"),
}
