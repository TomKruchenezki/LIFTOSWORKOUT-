package app.liftos.domain.logic

import kotlinx.serialization.Serializable

/**
 * Persistable rest-timer state. It is based on absolute wall-clock timestamps (never on a
 * running counter), so it stays correct when the screen is off, the app is in the background,
 * or the process is killed and later restored.
 */
@Serializable
data class RestTimerState(
    val totalMs: Long,
    /** Absolute epoch millis when the rest ends; null while paused. */
    val endsAt: Long?,
    /** Remaining millis captured at pause time; null while running. */
    val pausedRemainingMs: Long? = null,
    /** Free label shown in the notification, e.g. the exercise name. */
    val label: String = "",
) {
    val isPaused: Boolean get() = endsAt == null
}

object RestTimer {
    const val MIN_SECONDS = 5
    const val MAX_SECONDS = 60 * 30

    fun start(nowMs: Long, durationSec: Int, label: String = ""): RestTimerState {
        val total = durationSec.coerceIn(MIN_SECONDS, MAX_SECONDS) * 1000L
        return RestTimerState(totalMs = total, endsAt = nowMs + total, label = label)
    }

    fun remainingMs(state: RestTimerState, nowMs: Long): Long {
        val raw = state.endsAt?.let { it - nowMs } ?: (state.pausedRemainingMs ?: 0L)
        // Clamp to the total so a backwards wall-clock jump can never produce a huge countdown.
        return raw.coerceIn(0L, state.totalMs)
    }

    fun isFinished(state: RestTimerState, nowMs: Long): Boolean =
        !state.isPaused && remainingMs(state, nowMs) <= 0L

    /** 0f..1f portion of the rest that already elapsed. */
    fun progress(state: RestTimerState, nowMs: Long): Float {
        if (state.totalMs <= 0L) return 1f
        return 1f - remainingMs(state, nowMs).toFloat() / state.totalMs.toFloat()
    }

    fun pause(state: RestTimerState, nowMs: Long): RestTimerState {
        if (state.isPaused) return state
        return state.copy(endsAt = null, pausedRemainingMs = remainingMs(state, nowMs))
    }

    fun resume(state: RestTimerState, nowMs: Long): RestTimerState {
        if (!state.isPaused) return state
        val remaining = state.pausedRemainingMs ?: 0L
        return state.copy(endsAt = nowMs + remaining, pausedRemainingMs = null)
    }

    fun addSeconds(state: RestTimerState, nowMs: Long, seconds: Int): RestTimerState {
        val delta = seconds * 1000L
        val newTotal = (state.totalMs + delta).coerceAtLeast(1000L)
        return if (state.isPaused) {
            val remaining = ((state.pausedRemainingMs ?: 0L) + delta).coerceAtLeast(0L)
            state.copy(totalMs = newTotal, pausedRemainingMs = remaining)
        } else {
            val remaining = (remainingMs(state, nowMs) + delta).coerceAtLeast(0L)
            state.copy(totalMs = newTotal, endsAt = nowMs + remaining)
        }
    }
}
