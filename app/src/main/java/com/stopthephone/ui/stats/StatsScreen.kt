package com.stopthephone.ui.stats

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.stopthephone.data.StatsRepository
import com.stopthephone.domain.Stats
import com.stopthephone.domain.StatsRange
import com.stopthephone.domain.StatsReport
import com.stopthephone.domain.StatsScale
import com.stopthephone.domain.Tally
import com.stopthephone.ui.common.AppIcon
import com.stopthephone.ui.common.GroupCard
import com.stopthephone.ui.common.MetricText
import com.stopthephone.ui.common.SegmentedControl
import com.stopthephone.ui.common.PeriodFormat
import com.stopthephone.usage.UsageSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

data class StatsState(val range: StatsRange, val report: StatsReport)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(private val repository: StatsRepository, usageSync: UsageSync) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val synced = MutableStateFlow(false)
    private val _range = MutableStateFlow(StatsRange(StatsScale.WEEK, LocalDate.now()))
    val range: StateFlow<StatsRange> = _range.asStateFlow()

    init {
        viewModelScope.launch {
            usageSync.run()
            synced.value = true
        }
    }

    val state: StateFlow<StatsState?> = combine(_range, synced) { range, _ -> range }
        .mapLatest { range ->
            withContext(Dispatchers.Default) {
                val from = range.start.atStartOfDay(zone).toInstant().toEpochMilli()
                val to = range.endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
                StatsState(range, Stats.report(repository.input(from, to), range, zone, System.currentTimeMillis()))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val labels: StateFlow<Map<String, String>> = repository.apps
        .map { apps -> apps.associate { it.packageName to it.label } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setScale(scale: StatsScale) {
        val current = _range.value
        val today = LocalDate.now()
        _range.value = StatsRange(scale, if (current.contains(today)) today else current.start)
    }

    fun previous() {
        _range.value = _range.value.previous()
    }

    fun next() {
        _range.value = _range.value.next()
    }
}

private enum class RankMetric(val title: String) { USAGE("使用时长"), INTERCEPTS("拦截"), RELEASED("放行") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit, onOpenReasons: () -> Unit) {
    val range by viewModel.range.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val labels by viewModel.labels.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("统计") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    IconButton(onClick = onOpenReasons) { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = "理由记录") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SegmentedControl(
                    options = StatsScale.entries.map { scaleName(it) },
                    selected = range.scale.ordinal,
                    onSelect = { viewModel.setScale(StatsScale.entries[it]) },
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::previous) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一${scaleName(range.scale)}")
                    }
                    Text(
                        StatsFormat.range(range, today),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    IconButton(onClick = viewModel::next, enabled = !range.endExclusive.isAfter(today)) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一${scaleName(range.scale)}")
                    }
                }
            }
            val current = state?.takeIf { it.range == range }
            if (current == null) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                return@LazyColumn
            }
            val report = current.report
            item { Overview(report.totals) }
            item { UsageTrend(current) }
            item { InterceptTrend(current) }
            if (report.totals.released + report.totals.gaveUp > 0) {
                item {
                    Section("结局") { RatioBar(report.totals.gaveUp, report.totals.released, "放弃", "放行") }
                }
            }
            if (range.scale != StatsScale.DAY) {
                item { InterceptHeatmap(report) }
            }
            item { Ranking(report, labels) }
        }
    }
}

private fun scaleName(scale: StatsScale): String = when (scale) {
    StatsScale.DAY -> "日"
    StatsScale.WEEK -> "周"
    StatsScale.MONTH -> "月"
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    GroupCard {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun Overview(totals: Tally) {
    val tiles = listOf(
        "时段内使用" to StatsFormat.duration(totals.inPeriodMillis),
        "时段外使用" to StatsFormat.duration(totals.outPeriodMillis),
        "拦截" to "${totals.intercepts}次",
        "放行时长" to StatsFormat.duration(totals.releaseMillis),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (name, value) ->
                    GroupCard(Modifier.weight(1f)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            MetricText(value, MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageTrend(state: StatsState) {
    TrendSection(
        title = "使用时长",
        state = state,
        values = state.report.buckets.map { it.inPeriodMillis.toFloat() to it.outPeriodMillis.toFloat() },
        firstName = "时段内",
        secondName = "时段外",
        format = { StatsFormat.duration(it.toLong()) },
    )
}

@Composable
private fun InterceptTrend(state: StatsState) {
    TrendSection(
        title = "拦截",
        state = state,
        values = state.report.buckets.map { it.gaveUp.toFloat() to it.released.toFloat() },
        firstName = "放弃",
        secondName = "放行",
        format = { "${it.toInt()}次" },
    )
}

/**
 * 两段堆叠的趋势图，点中柱子时图例显示该柱的数值。
 */
@Composable
private fun TrendSection(
    title: String,
    state: StatsState,
    values: List<Pair<Float, Float>>,
    firstName: String,
    secondName: String,
    format: (Float) -> String,
) {
    var selected by remember(state.range) { mutableStateOf<Int?>(null) }
    val colors = chartColors()
    val starts = remember(state.range) { state.range.bucketStarts() }
    Section(title) {
        val index = selected
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (index == null) {
                LegendItem(colors.first, "$firstName ${format(values.sumOf { it.first.toDouble() }.toFloat())}")
                LegendItem(colors.second, "$secondName ${format(values.sumOf { it.second.toDouble() }.toFloat())}")
            } else {
                Text(StatsFormat.bucket(state.range, starts[index]), style = MaterialTheme.typography.bodySmall)
                LegendItem(colors.first, "$firstName ${format(values[index].first)}")
                LegendItem(colors.second, "$secondName ${format(values[index].second)}")
            }
        }
        Spacer(Modifier.height(12.dp))
        val description = values.mapIndexedNotNull { i, (first, second) ->
            if (first + second == 0f) null else "${StatsFormat.bucket(state.range, starts[i])} $firstName ${format(first)} $secondName ${format(second)}"
        }.joinToString("，").ifEmpty { "无数据" }
        StackedBarChart(
            values = values,
            xLabels = StatsFormat.axisLabels(state.range),
            selected = index,
            onSelect = { selected = it },
            description = "$title：$description",
        )
    }
}

@Composable
private fun InterceptHeatmap(report: StatsReport) {
    var selected by remember(report) { mutableStateOf<Pair<Int, Int>?>(null) }
    Section("拦截时段分布") {
        val cell = selected
        Text(
            if (cell == null) "" else "周${PeriodFormat.DAY_NAMES[cell.first]} ${cell.second}点 · ${report.heatmap[cell.first][cell.second]}次",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        val description = report.heatmap.flatMapIndexed { day, row ->
            row.mapIndexedNotNull { hour, count -> if (count == 0) null else "周${PeriodFormat.DAY_NAMES[day]} ${hour}点 ${count}次" }
        }.joinToString("，").ifEmpty { "无拦截" }
        Heatmap(
            values = report.heatmap,
            dayNames = PeriodFormat.DAY_NAMES,
            selected = cell,
            onSelect = { selected = it },
            description = "拦截时段分布：$description",
        )
    }
}

@Composable
private fun Ranking(report: StatsReport, labels: Map<String, String>) {
    var metric by rememberSaveable { mutableStateOf(RankMetric.USAGE) }
    Section("应用排行") {
        SegmentedControl(
            options = RankMetric.entries.map { it.title },
            selected = metric.ordinal,
            onSelect = { metric = RankMetric.entries[it] },
        )
        Spacer(Modifier.height(12.dp))
        val value: (Tally) -> Long = when (metric) {
            RankMetric.USAGE -> { tally -> tally.usageMillis }
            RankMetric.INTERCEPTS -> { tally -> tally.intercepts.toLong() }
            RankMetric.RELEASED -> { tally -> tally.released.toLong() }
        }
        val rows = report.apps.entries.sortedByDescending { value(it.value) }
        val max = rows.maxOfOrNull { value(it.value) }?.takeIf { it > 0 } ?: 1L
        val bar = chartColors().first
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { (packageName, tally) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(packageName, size = 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(
                                labels[packageName] ?: packageName,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val amount = value(tally)
                            Text(if (metric == RankMetric.USAGE) StatsFormat.duration(amount) else "${amount}次")
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { value(tally).toFloat() / max },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = bar,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            drawStopIndicator = {},
                            gapSize = 0.dp,
                        )
                    }
                }
            }
        }
    }
}
