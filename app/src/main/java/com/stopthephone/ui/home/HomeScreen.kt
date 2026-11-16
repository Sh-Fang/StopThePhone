package com.stopthephone.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.stopthephone.data.ControlRepository
import com.stopthephone.data.StatsRepository
import com.stopthephone.data.toRule
import com.stopthephone.domain.PeriodRule
import com.stopthephone.domain.Schedule
import com.stopthephone.domain.Stats
import com.stopthephone.domain.StatsRange
import com.stopthephone.domain.StatsScale
import com.stopthephone.domain.Tally
import com.stopthephone.system.Permissions
import com.stopthephone.ui.common.AppIcon
import com.stopthephone.ui.common.CircleIconButton
import com.stopthephone.ui.common.GroupCard
import com.stopthephone.ui.common.GroupDivider
import com.stopthephone.ui.common.GroupRow
import com.stopthephone.ui.common.MetricText
import com.stopthephone.ui.common.PeriodFormat
import com.stopthephone.ui.common.SectionLabel
import com.stopthephone.ui.common.StatusPill
import com.stopthephone.ui.settings.startSafely
import com.stopthephone.ui.stats.StatsFormat
import com.stopthephone.usage.UsageSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class AppSummary(val packageName: String, val label: String, val periods: List<PeriodRule>)

class HomeViewModel(
    repository: ControlRepository,
    private val statsRepository: StatsRepository,
    private val usageSync: UsageSync,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val _today = MutableStateFlow<Tally?>(null)
    val today: StateFlow<Tally?> = _today.asStateFlow()

    val apps: StateFlow<List<AppSummary>?> = combine(repository.apps, repository.periods) { apps, periods ->
        val byApp = periods.groupBy { it.packageName }
        apps.map { app -> AppSummary(app.packageName, app.label, byApp[app.packageName].orEmpty().map { it.toRule() }) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 同步使用时长后重算今日数据。
     */
    fun refresh() {
        viewModelScope.launch {
            usageSync.run()
            val range = StatsRange(StatsScale.DAY, LocalDate.now())
            val from = range.start.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = range.endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
            val input = statsRepository.input(from, to)
            _today.value = withContext(Dispatchers.Default) { Stats.report(input, range, zone, System.currentTimeMillis()).totals }
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onPickApps: () -> Unit,
    onOpenApp: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LifecycleResumeEffect(Unit) {
        now = LocalDateTime.now()
        viewModel.refresh()
        onPauseOrDispose { }
    }
    Scaffold(
        floatingActionButton = {
            if (!apps.isNullOrEmpty()) {
                FloatingActionButton(
                    onClick = onPickApps,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "选择管控应用")
                }
            }
        },
    ) { padding ->
        val list = apps ?: return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "StopThePhone",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    CircleIconButton(Icons.Default.BarChart, "统计", onOpenStats)
                    Spacer(Modifier.width(8.dp))
                    CircleIconButton(Icons.Default.Settings, "设置", onOpenSettings)
                }
            }
            item { ServiceBanner() }
            if (list.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 160.dp), contentAlignment = Alignment.Center) {
                        Button(onClick = onPickApps, modifier = Modifier.height(52.dp)) { Text("选择管控应用") }
                    }
                }
                return@LazyColumn
            }
            item { TodayCard(today) }
            item { SectionLabel("管控中的应用") }
            item {
                GroupCard {
                    list.forEachIndexed { index, app ->
                        if (index > 0) {
                            GroupDivider(inset = true)
                        }
                        GroupRow(
                            title = app.label,
                            subtitle = summary(app.periods),
                            onClick = { onOpenApp(app.packageName) },
                            leading = { AppIcon(app.packageName, size = 44.dp) },
                            trailing = {
                                if (Schedule.currentWindow(app.periods, now) != null) {
                                    StatusPill("管控中")
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayCard(today: Tally?) {
    GroupCard(Modifier.padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            TodayNumber("今日拦截", today?.intercepts?.toString() ?: "–", MaterialTheme.colorScheme.primary)
            TodayNumber("放行", today?.released?.toString() ?: "–", MaterialTheme.colorScheme.onSurface)
            TodayNumber("时段内使用", today?.let { StatsFormat.duration(it.inPeriodMillis) } ?: "–", MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun TodayNumber(name: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics(mergeDescendants = true) { }) {
        MetricText(value, MaterialTheme.typography.headlineMedium, color)
        Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 管控服务没开时在首页顶部提示。
 */
@Composable
private fun ServiceBanner() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        enabled = Permissions.isAccessibilityEnabled(context)
        onPauseOrDispose { }
    }
    if (enabled) {
        return
    }
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(12.dp))
            Text(
                "管控服务未开启",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { context.startSafely(Permissions.accessibilitySettings()) }) { Text("去开启") }
        }
    }
}

private fun summary(periods: List<PeriodRule>): String {
    val first = periods.firstOrNull() ?: return "未设时段"
    val text = "${PeriodFormat.range(first)} · ${PeriodFormat.days(first.daysMask)}"
    return if (periods.size > 1) "$text  +${periods.size - 1}" else text
}
