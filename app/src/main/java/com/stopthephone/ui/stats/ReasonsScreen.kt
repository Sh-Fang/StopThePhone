package com.stopthephone.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.stopthephone.data.InstalledApps
import com.stopthephone.data.Release
import com.stopthephone.data.ReleasePurpose
import com.stopthephone.data.StatsRepository
import com.stopthephone.ui.common.AppIcon
import com.stopthephone.ui.common.GroupDivider
import com.stopthephone.ui.common.GroupItem
import com.stopthephone.ui.common.GroupRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 理由记录的筛选条件，日期为 null 表示不限。
 */
data class ReasonFilter(val packageName: String? = null, val from: LocalDate? = null, val to: LocalDate? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class ReasonsViewModel(repository: StatsRepository, installedApps: InstalledApps) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val _filter = MutableStateFlow(ReasonFilter())
    val filter: StateFlow<ReasonFilter> = _filter.asStateFlow()

    val reasons: StateFlow<List<Release>?> = _filter
        .flatMapLatest { filter ->
            val from = filter.from?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: 0L
            val to = filter.to?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE
            repository.reasons(filter.packageName, from, to)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // 已移出管控的应用从系统取名称，卸载了就显示包名
    val labels: StateFlow<Map<String, String>> = combine(repository.apps, repository.reasonApps) { apps, packageNames ->
        val known = apps.associate { it.packageName to it.label }
        packageNames.associateWith { known[it] ?: installedApps.label(it) ?: it }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setApp(packageName: String?) {
        _filter.value = _filter.value.copy(packageName = packageName)
    }

    fun setDates(from: LocalDate?, to: LocalDate?) {
        _filter.value = _filter.value.copy(from = from, to = to)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReasonsScreen(viewModel: ReasonsViewModel, onBack: () -> Unit) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val reasons by viewModel.reasons.collectAsStateWithLifecycle()
    val labels by viewModel.labels.collectAsStateWithLifecycle()
    var appMenu by remember { mutableStateOf(false) }
    var dateMenu by remember { mutableStateOf(false) }
    var datePicker by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("理由记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    FilterPill(
                        text = filter.packageName?.let { labels[it] ?: it } ?: "全部应用",
                        active = filter.packageName != null,
                        onClick = { appMenu = true },
                    )
                    DropdownMenu(expanded = appMenu, onDismissRequest = { appMenu = false }) {
                        DropdownMenuItem(text = { Text("全部应用") }, onClick = {
                            viewModel.setApp(null)
                            appMenu = false
                        })
                        labels.entries.sortedBy { it.value }.forEach { (packageName, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                viewModel.setApp(packageName)
                                appMenu = false
                            })
                        }
                    }
                }
                Box {
                    FilterPill(text = dateLabel(filter), active = filter.from != null, onClick = { dateMenu = true })
                    DropdownMenu(expanded = dateMenu, onDismissRequest = { dateMenu = false }) {
                        DropdownMenuItem(text = { Text("全部日期") }, onClick = {
                            viewModel.setDates(null, null)
                            dateMenu = false
                        })
                        PRESET_DAYS.forEach { days ->
                            DropdownMenuItem(text = { Text(presetName(days)) }, onClick = {
                                val today = LocalDate.now()
                                viewModel.setDates(today.minusDays(days - 1L), today)
                                dateMenu = false
                            })
                        }
                        DropdownMenuItem(text = { Text("自定义") }, onClick = {
                            datePicker = true
                            dateMenu = false
                        })
                    }
                }
            }
            val list = reasons ?: return@Column
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("无记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {
                    itemsIndexed(list, key = { _, release -> release.id }) { index, release ->
                        GroupItem(index, list.size) {
                            if (index > 0) {
                                GroupDivider(inset = true)
                            }
                            GroupRow(
                                title = release.reason,
                                subtitle = meta(release, labels),
                                leading = { AppIcon(release.packageName) },
                            )
                        }
                    }
                }
            }
        }
    }
    if (datePicker) {
        DateRangeSheet(
            filter = filter,
            onDismiss = { datePicker = false },
            onConfirm = { from, to ->
                viewModel.setDates(from, to)
                datePicker = false
            },
        )
    }
}

private val PRESET_DAYS = listOf(1, 7, 30)

private fun presetName(days: Int): String = if (days == 1) "今天" else "近 $days 天"

private fun dateLabel(filter: ReasonFilter): String {
    val from = filter.from
    val to = filter.to
    if (from == null || to == null) {
        return "全部日期"
    }
    val today = LocalDate.now()
    val preset = PRESET_DAYS.firstOrNull { to == today && from == today.minusDays(it - 1L) }
    return if (preset != null) presetName(preset) else "${shortDate(from)}–${shortDate(to)}"
}

/**
 * 筛选用的胶囊按钮。
 */
@Composable
private fun FilterPill(text: String, active: Boolean, onClick: () -> Unit) {
    val container = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    val content = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .clip(CircleShape)
            .background(container)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = content)
        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = content)
    }
}

/**
 * 自定义日期范围，只能选今天及以前。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeSheet(filter: ReasonFilter, onDismiss: () -> Unit, onConfirm: (LocalDate, LocalDate) -> Unit) {
    val todayMillis = LocalDate.now().toUtcMillis()
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = filter.from?.toUtcMillis(),
        initialSelectedEndDateMillis = filter.to?.toUtcMillis(),
        yearRange = 2024..LocalDate.now().year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis
        },
    )
    val sheetColor = MaterialTheme.colorScheme.surfaceContainerLow
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetColor,
    ) {
        val start = state.selectedStartDateMillis?.toUtcDate()
        val end = state.selectedEndDateMillis?.toUtcDate()
        Text(
            when {
                start == null -> "选择开始日期"
                end == null -> "${shortDate(start)} – 选择结束日期"
                else -> "${shortDate(start)} – ${shortDate(end)}"
            },
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        DateRangePicker(
            state = state,
            modifier = Modifier.fillMaxWidth().height(420.dp),
            title = null,
            headline = null,
            showModeToggle = false,
            colors = DatePickerDefaults.colors(containerColor = sheetColor),
        )
        Button(
            onClick = {
                if (start != null) {
                    onConfirm(start, end ?: start)
                }
            },
            enabled = start != null,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(52.dp),
        ) { Text("确定") }
    }
}

private fun meta(release: Release, labels: Map<String, String>): String {
    val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(release.createdAt), ZoneId.systemDefault())
    val label = labels[release.packageName] ?: release.packageName
    val purpose = when (release.purpose) {
        ReleasePurpose.RELEASE -> "放行 ${release.durationMinutes} 分钟"
        ReleasePurpose.MODIFY_SETTINGS -> "修改设置"
    }
    return "$label · ${shortDate(time.toLocalDate())} ${"%02d:%02d".format(time.hour, time.minute)} · $purpose"
}

private fun shortDate(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"

// 日期选择器按 UTC 零点表示日期
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
