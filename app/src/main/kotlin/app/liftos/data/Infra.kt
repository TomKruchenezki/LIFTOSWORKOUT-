package app.liftos.data

import java.time.ZoneId

/** Injectable clock so tests (and DST scenarios) are deterministic. */
fun interface TimeSource {
    fun nowMs(): Long

    companion object {
        val Default = TimeSource { System.currentTimeMillis() }
    }
}

/** Zone used when creating new records; the device zone (Asia/Jerusalem for the owner). */
fun interface ZoneSource {
    fun zone(): ZoneId

    companion object {
        val Device = ZoneSource { ZoneId.systemDefault() }
    }
}
