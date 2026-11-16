package com.stopthephone.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.stopthephone.data.InstalledApps
import com.stopthephone.data.Release
import com.stopthephone.data.ReleasePurpose
import com.stopthephone.data.StatsRepository
import com.stopthephone.ui.common.AppIcon
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
                    FilterChip(
                        selected = filter.packageName != null,
                        onClick = { appMenu = true },
                        label = { Text(filter.packageName?.let { labels[it] ?: it } ?: "全部应用") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
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
                val from = filter.from
                val to = filter.to
                FilterChip(
                    selected = from != null,
                    onClick = { datePicker = true },
                    label = { Text(if (from == null || to == null) "全部日期" else "${shortDate(from)}–${shortDate(to)}") },
                    trailingIcon = if (from == null) {
                        null
                    } else {
                        {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "清除日期",
                                modifier = Modifier.size(FilterChipDefaults.IconSize).clickable { viewModel.setDates(null, null) },
                            )
                        }
                    },
                )
            }
            val list = reasons ?: return@Column
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("无记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { release ->
                        ListItem(
                            leadingContent = { AppIcon(release.packageName) },
                            headlineContent = { Text(release.reason) },
                            supportingContent = { Text(meta(release, labels)) },
                        )
                    }
                }
            }
        }
    }
    if (datePicker) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = filter.from?.toUtcMillis(),
            initialSelectedEndDateMillis = filter.to?.toUtcMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { datePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = state.selectedStartDateMillis?.toUtcDate()
                        viewModel.setDates(start, state.selectedEndDateMillis?.toUtcDate() ?: start)
                        datePicker = false
                    },
                    enabled = state.selectedStartDateMillis != null,
                ) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { datePicker = false }) { Text("取消") } },
        ) {
            DateRangePicker(state, Modifier.weight(1f))
        }
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
