package com.stopthephone.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.stopthephone.data.ControlPeriod
import com.stopthephone.data.ControlRepository
import com.stopthephone.data.ControlledApp
import com.stopthephone.data.toRule
import com.stopthephone.ui.common.GuardedViewModel
import com.stopthephone.ui.common.PeriodFormat
import com.stopthephone.ui.common.ReasonDialog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DetailState(val app: ControlledApp?, val periods: List<ControlPeriod>, val otherApps: List<ControlledApp>)

class AppDetailViewModel(repository: ControlRepository, val packageName: String) : GuardedViewModel(repository) {

    val state: StateFlow<DetailState?> = combine(repository.apps, repository.periods) { apps, periods ->
        DetailState(
            app = apps.firstOrNull { it.packageName == packageName },
            periods = periods.filter { it.packageName == packageName },
            otherApps = apps.filter { it.packageName != packageName },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun savePeriod(period: ControlPeriod) {
        guarded(
            change = { current ->
                val periods = current[packageName].orEmpty()
                val updated = if (period.id == 0L) periods + period else periods.map { if (it.id == period.id) period else it }
                mapOf(packageName to updated)
            },
            apply = { repository.savePeriod(period) },
        )
    }

    fun deletePeriod(id: Long) {
        guarded(
            change = { current -> mapOf(packageName to current[packageName].orEmpty().filter { it.id != id }) },
            apply = { repository.deletePeriod(id) },
        )
    }

    fun copyTo(targets: List<String>) {
        // 复制会覆盖目标应用原有时段
        guarded(
            change = { current -> targets.associateWith { target -> current[packageName].orEmpty().map { it.copy(packageName = target) } } },
            apply = { repository.copyPeriods(packageName, targets) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(viewModel: AppDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    // 正在编辑的时段，id 为 0 表示新增
    var editing by remember { mutableStateOf<ControlPeriod?>(null) }
    var copying by remember { mutableStateOf(false) }
    val s = state
    val newPeriod = { ControlPeriod(packageName = viewModel.packageName, startMinute = 22 * 60, endMinute = 7 * 60, daysMask = 0b1111111) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s?.app?.label.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (s != null && s.periods.isNotEmpty() && s.otherApps.isNotEmpty()) {
                        IconButton(onClick = { copying = true }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "复制时段到其他应用")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!s?.periods.isNullOrEmpty()) {
                FloatingActionButton(onClick = { editing = newPeriod() }) {
                    Icon(Icons.Default.Add, contentDescription = "添加时段")
                }
            }
        },
    ) { padding ->
        if (s == null) {
            return@Scaffold
        }
        if (s.periods.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Button(onClick = { editing = newPeriod() }) { Text("添加时段") }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(s.periods, key = { it.id }) { period ->
                    val rule = period.toRule()
                    ListItem(
                        modifier = Modifier.clickable { editing = period },
                        headlineContent = { Text(PeriodFormat.range(rule), style = MaterialTheme.typography.titleLarge) },
                        supportingContent = { Text(PeriodFormat.days(rule.daysMask)) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.deletePeriod(period.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除时段")
                            }
                        },
                    )
                }
            }
        }
    }

    editing?.let { period ->
        PeriodEditorSheet(
            initial = period,
            onDismiss = { editing = null },
            onConfirm = {
                viewModel.savePeriod(it)
                editing = null
            },
        )
    }
    if (pending != null) {
        ReasonDialog(onDismiss = viewModel::cancelPending, onConfirm = viewModel::confirmPending)
    }
    if (copying && s != null) {
        CopyPeriodsDialog(
            candidates = s.otherApps,
            onDismiss = { copying = false },
            onConfirm = {
                viewModel.copyTo(it)
                copying = false
            },
        )
    }
}
