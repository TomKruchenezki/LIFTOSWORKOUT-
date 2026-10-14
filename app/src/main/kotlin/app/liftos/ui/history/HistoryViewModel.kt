package app.liftos.ui.history

import app.liftos.data.repo.HistoryItem
import app.liftos.domain.model.ActivityEvent

/** One line of the history list: a recorded workout or a logged activity. */
sealed interface HistoryEntry {
    val sortKey: Long
    val localDate: String

    data class Workout(val item: HistoryItem) : HistoryEntry {
        override val sortKey get() = item.session.startedAt
        override val localDate get() = item.session.localDate
    }

    data class Activity(val event: ActivityEvent) : HistoryEntry {
        override val sortKey get() = event.startsAt
        override val localDate get() = event.localDate
    }
}
