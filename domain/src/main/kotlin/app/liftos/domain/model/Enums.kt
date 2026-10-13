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

    // Added in version 2 of the calendar (names are stored in the database, never renumbered).
    STRENGTH("אימון כוח"),
    COACH("פגישה עם מאמן"),
    MEASUREMENT("מדידת גוף"),
    SPORT("פעילות ספורטיבית"),
    CUSTOM("אירוע אישי"),
    ;

    /** True for events that are cardio / other sports rather than the gym itself. */
    val isExternalActivity: Boolean
        get() = this == WALK || this == RUN || this == SWIM || this == SOCCER || this == CYCLING || this == CARDIO || this == SPORT
}

@Serializable
enum class EventStatus(val labelHe: String) {
    PLANNED("מתוכנן"),
    DONE("בוצע"),
    MISSED("לא בוצע"),
    CANCELLED("בוטל"),
}

/** What happened to one planned occurrence of a plan day (the plan itself is never touched). */
@Serializable
enum class OverrideAction(val labelHe: String) {
    MOVED("הועבר ליום אחר"),
    SKIPPED("דולג בכוונה"),
    MISSED("סומן כלא בוצע"),
}

/** Circumstances that explain a measurement (a weigh-in after a salty meal is not a trend). */
@Serializable
enum class MeasurementCondition(val labelHe: String) {
    MORNING("בוקר"),
    FASTED("בצום"),
    AFTER_MEAL("אחרי ארוחה"),
    AFTER_WORKOUT("אחרי אימון"),
    EVENING("ערב"),
    SALTY_MEAL("ארוחה מלוחה"),
    ALCOHOL("אלכוהול יום קודם"),
    POOR_SLEEP("שינה לא טובה"),
    ILLNESS("מחלה"),
    CYCLE("מחזור"),
    SUPPLEMENT("שינוי תוספים"),
    OTHER("אחר"),
}

@Serializable
enum class GoalType(val labelHe: String, val category: GoalCategory) {
    BODY_WEIGHT("משקל גוף", GoalCategory.BODY),
    BODY_MEASURE("מדד גוף (היקף / אחוז שומן)", GoalCategory.BODY),
    EXERCISE_WEIGHT("משקל מקסימלי בתרגיל", GoalCategory.STRENGTH),
    EXERCISE_REPS("חזרות מקסימליות בתרגיל", GoalCategory.STRENGTH),
    WORKOUTS_PER_MONTH("אימונים בחודש", GoalCategory.FREQUENCY),
    WORKOUTS_PER_WEEK("אימונים בשבוע", GoalCategory.FREQUENCY),
    PROGRAM_WEEKS("השלמת תוכנית אימונים (שבועות)", GoalCategory.PROGRAM),
    CUSTOM("יעד מותאם אישית", GoalCategory.CUSTOM),
}

@Serializable
enum class GoalCategory(val labelHe: String) {
    BODY("גוף"),
    STRENGTH("כוח"),
    FREQUENCY("תדירות"),
    PROGRAM("תוכנית"),
    CUSTOM("אישי"),
}

@Serializable
enum class GoalStatus(val labelHe: String) {
    ACTIVE("פעיל"),
    ACHIEVED("הושג"),
    PAUSED("בהשהיה"),
    ABANDONED("ננטש"),
}

@Serializable
enum class PhotoAngle(val labelHe: String) {
    FRONT("חזית"),
    SIDE("צד"),
    BACK("גב"),
}
