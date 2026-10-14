package app.liftos.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.liftos.data.PdfExport
import app.liftos.data.repo.AnalyticsData
import app.liftos.di.AppContainer
import app.liftos.domain.analytics.DateRange
import app.liftos.domain.analytics.SessionFact
import app.liftos.domain.export.BodySnapshot
import app.liftos.domain.export.CsvExporter
import app.liftos.domain.export.ExportFormat
import app.liftos.domain.export.PeriodExportFile
import app.liftos.domain.export.PeriodJson
import app.liftos.domain.export.PeriodReport
import app.liftos.domain.export.ReportInput
import app.liftos.domain.export.ReportOptions
import app.liftos.domain.export.ReportScope
import app.liftos.domain.export.SessionExportFile
import app.liftos.domain.export.SessionJson
import app.liftos.domain.export.TextReport
import app.liftos.domain.export.withoutNotes
import app.liftos.domain.goals.GoalContext
import app.liftos.domain.goals.GoalEngine
import app.liftos.domain.logic.BodyTrends
import app.liftos.domain.logic.Formatters
import app.liftos.domain.model.GoalCategory
import app.liftos.domain.model.GoalStatus
import app.liftos.domain.model.WorkoutDetail
import app.liftos.domain.progression.ProgressionEngine
import app.liftos.domain.recovery.RecoveryAnalytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** What the user picked on the "analyze with AI" screen. */
data class AiSelection(
    val scope: ReportScope,
    val custom: DateRange? = null,
    /** For [ReportScope.SINGLE]: the chosen workout; null = the latest one. */
    val sessionId: String? = null,
    val options: ReportOptions = ReportOptions(),
)

data class BuiltReport(
    val text: String,
    val range: DateRange,
    val scopeLabel: String,
    val workouts: Int,
    val sessionId: String?,
)

data class AiExportUi(
    val loading: Boolean = true,
    val selection: AiSelection = AiSelection(ReportScope.LAST_MONTH),
    val report: BuiltReport? = null,
    val recentSessions: List<SessionFact> = emptyList(),
    val today: LocalDate = LocalDate.now(),
)

/** A file ready to be shared: the bytes are produced only when the user taps a share button. */
class ExportPayload(val fileName: String, val mime: String, val bytes: ByteArray)

@OptIn(ExperimentalCoroutinesApi::class)
class AiExportViewModel(private val c: AppContainer, initialSessionId: String?) : ViewModel() {
    private val selection = MutableStateFlow(
        AiSelection(
            scope = if (initialSessionId != null) ReportScope.SINGLE else ReportScope.LAST_MONTH,
            sessionId = initialSessionId,
        ),
    )

    val ui: StateFlow<AiExportUi> = combine(selection, c.analyticsData) { sel, data -> sel to data }
        .flatMapLatest { (sel, data) ->
            flowOf(Unit).map {
                val report = build(sel, data)
                AiExportUi(
                    loading = false,
                    selection = sel,
                    report = report,
                    recentSessions = data.sessions.sortedByDescending { it.startedAt }.take(12),
                    today = LocalDate.now(c.zones.zone()),
                )
            }
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiExportUi())

    fun setScope(scope: ReportScope) { selection.value = selection.value.copy(scope = scope) }

    fun setCustom(range: DateRange) { selection.value = selection.value.copy(scope = ReportScope.CUSTOM, custom = range) }

    fun setSession(id: String) { selection.value = selection.value.copy(scope = ReportScope.SINGLE, sessionId = id) }

    fun setOptions(options: ReportOptions) { selection.value = selection.value.copy(options = options) }

    // ---- the report ---------------------------------------------------------------------------------------

    private suspend fun bodySnapshot(): BodySnapshot {
        val all = c.measurements.observeAllOnce()
        val latest = all.firstOrNull { it.weightKg != null } ?: all.firstOrNull()
        return BodySnapshot(latest, BodyTrends.weightChange(all, 7), BodyTrends.weightChange(all, 30))
    }

    private suspend fun singleDetail(sel: AiSelection, data: AnalyticsData): WorkoutDetail? {
        val id = sel.sessionId ?: data.sessions.maxByOrNull { it.startedAt }?.id ?: return null
        return c.workouts.getDetail(id)
    }

    private suspend fun build(sel: AiSelection, data: AnalyticsData): BuiltReport = withContext(Dispatchers.Default) {
        val today = LocalDate.now(c.zones.zone())
        val opts = sel.options
        if (sel.scope == ReportScope.SINGLE) {
            val detail = singleDetail(sel, data)
            if (detail == null) {
                return@withContext BuiltReport(NO_DATA, DateRange(today, today), sel.scope.labelHe, 0, null)
            }
            val previous = c.workouts.previousSameType(detail.session.id)
            val d = if (opts.includeNotes) detail else detail.withoutNotes()
            val p = if (opts.includeNotes) previous else previous?.withoutNotes()
            val body = if (opts.includeBody) bodySnapshot() else null
            val date = app.liftos.domain.logic.TimeUtils.parseDate(detail.session.localDate) ?: today
            return@withContext BuiltReport(
                TextReport.aiReport(d, p, body), DateRange(date, date), "${detail.session.title} (${Formatters.date(date)})", 1, detail.session.id,
            )
        }
        val range = sel.scope.range(today, data.earliest, sel.custom)
        val measurements = if (opts.includeBody) c.measurements.observeAllOnce() else emptyList()
        val goalRows = if (opts.includeGoals) {
            // Body goals show body numbers, so they only go out when the body data is included too.
            val ctx = GoalContext(today, c.measurements.observeAllOnce(), data.facts, data.sessions)
            c.goals.getAll()
                .filter { it.status == GoalStatus.ACTIVE && (opts.includeBody || it.type.category != GoalCategory.BODY) }
                .map { it to GoalEngine.progress(it, ctx) }
        } else emptyList()
        val recovery = if (opts.includeRecovery) RecoveryAnalytics.report(data.facts, data.sessions, today) else null
        val suggestions = if (opts.includeProgressionHints) {
            val prefs = c.progression.observePrefs().first()
            data.facts.filter { it.date in range && it.isWorking }.groupBy { it.key }.entries
                .sortedByDescending { (_, rows) -> rows.sumOf { it.volumeKg } }
                .take(8)
                .map { (key, rows) ->
                    ProgressionEngine.suggest(key, rows.first().exerciseName, data.facts, data.sessions, today, null, prefs[key]?.incrementKg)
                }
        } else emptyList()
        val input = ReportInput(
            range = range, scopeLabel = sel.scope.labelHe, today = today, facts = data.facts, sessions = data.sessions,
            records = data.records, measurements = measurements, goals = goalRows, recovery = recovery,
            targetPerWeek = c.settingsSnapshot().weeklyGoalWorkouts, suggestions = suggestions,
        )
        BuiltReport(
            PeriodReport.build(input, opts), range, sel.scope.labelHe,
            data.sessions.count { it.date in range }, null,
        )
    }

    // ---- files ----------------------------------------------------------------------------------------------

    suspend fun payload(format: ExportFormat): ExportPayload? = withContext(Dispatchers.Default) {
        val state = ui.value
        val report = state.report ?: return@withContext null
        val sel = state.selection
        val opts = sel.options
        val stamp = "${report.range.start}_${report.range.end}"
        val base = "liftos-${if (sel.scope == ReportScope.SINGLE) "workout" else "report"}-$stamp"
        val data = c.analytics.getData()
        val details: List<WorkoutDetail> = if (sel.scope == ReportScope.SINGLE) {
            listOfNotNull(singleDetail(sel, data))
        } else {
            data.sessions.filter { it.date in report.range }.sortedBy { it.startedAt }.mapNotNull { c.workouts.getDetail(it.id) }
        }.map { if (opts.includeNotes) it else it.withoutNotes() }
        val now = System.currentTimeMillis()
        when (format) {
            ExportFormat.TXT -> ExportPayload("$base.txt", format.mime, report.text.toByteArray(Charsets.UTF_8))
            ExportFormat.PDF -> ExportPayload(
                "$base.pdf", format.mime,
                PdfExport.render("LiftOS – ${report.scopeLabel}", report.text).bytes,
            )
            ExportFormat.CSV -> ExportPayload(
                "$base.csv", format.mime,
                byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + CsvExporter.sessions(details).toByteArray(Charsets.UTF_8),
            )
            ExportFormat.JSON -> {
                val json = if (sel.scope == ReportScope.SINGLE && details.isNotEmpty()) {
                    val prev = c.workouts.previousSameType(details[0].session.id)?.let { if (opts.includeNotes) it else it.withoutNotes() }
                    SessionJson.encode(SessionExportFile(exportedAt = now, workout = details[0], previous = prev))
                } else {
                    val body = if (opts.includeBody) c.measurements.observeAllOnce().filter {
                        app.liftos.domain.logic.TimeUtils.parseDate(it.localDate)?.let { d -> d in report.range } == true
                    }.map { if (opts.includeNotes) it else it.withoutNotes() } else emptyList()
                    PeriodJson.encode(
                        PeriodExportFile(
                            exportedAt = now, scope = report.scopeLabel, rangeStart = report.range.start.toString(),
                            rangeEnd = report.range.end.toString(), instruction = TextReport.AI_INSTRUCTION,
                            workouts = details, measurements = body,
                        ),
                    )
                }
                ExportPayload("$base.json", format.mime, json.toByteArray(Charsets.UTF_8))
            }
        }
    }

    companion object {
        const val NO_DATA = "אין אימון מתועד לניתוח. סיים אימון (או הוסף אימון מהעבר) ואז חזור לכאן."
    }
}
