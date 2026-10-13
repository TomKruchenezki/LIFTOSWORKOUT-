package app.liftos.domain.logic

/**
 * Estimated one-rep max. These formulas are estimates, not a measured 1RM, and become less
 * reliable as reps increase; we therefore refuse to estimate above [MAX_RELIABLE_REPS].
 */
object OneRepMax {
    const val MAX_RELIABLE_REPS = 12

    const val EXPLANATION =
        "1RM משוער לפי נוסחת Epley: משקל × (1 + חזרות ÷ 30). זו הערכה בלבד ולא מדידה ישירה, " +
            "והיא פחות מדויקת מעל ~10 חזרות."

    fun epley(weightKg: Double, reps: Int): Double? {
        if (weightKg <= 0.0 || reps < 1 || reps > MAX_RELIABLE_REPS) return null
        return if (reps == 1) weightKg else weightKg * (1.0 + reps / 30.0)
    }

    fun brzycki(weightKg: Double, reps: Int): Double? {
        if (weightKg <= 0.0 || reps < 1 || reps > MAX_RELIABLE_REPS) return null
        return if (reps == 1) weightKg else weightKg * 36.0 / (37.0 - reps)
    }
}
