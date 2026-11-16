package com.stopthephone.ui.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.stopthephone.data.ControlRepository
import com.stopthephone.data.InstalledApp
import com.stopthephone.data.InstalledApps
import com.stopthephone.ui.common.AppIcon
import com.stopthephone.ui.common.GuardedViewModel
import com.stopthephone.ui.common.ReasonDialog
import com.stopthephone.ui.stats.StatsFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PickerState(
    val apps: List<InstalledApp>,
    val usage: Map<String, Long>,
    val original: Set<String>,
    val selected: Set<String>,
    val query: String = "",
) {
    val visibleApps: List<InstalledApp>
        get() = query.trim().let { q ->
            if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
        }
}

class AppPickerViewModel(repository: ControlRepository, installedApps: InstalledApps) : GuardedViewModel(repository) {

    private val _state = MutableStateFlow<PickerState?>(null)
    val state: StateFlow<PickerState?> = _state

    init {
        viewModelScope.launch {
            val controlled = repository.apps.first().map { it.packageName }.toSet()
            val usage = installedApps.recentUsage(USAGE_DAYS, System.currentTimeMillis())
            // 按近 7 天使用时长排序
            val apps = installedApps.list().sortedByDescending { usage[it.packageName] ?: 0L }
            _state.value = PickerState(apps, usage, controlled, controlled)
        }
    }

    fun setQuery(query: String) {
        _state.update { it?.copy(query = query) }
    }

    fun toggle(packageName: String) {
        _state.update { s ->
            s?.copy(selected = if (packageName in s.selected) s.selected - packageName else s.selected + packageName)
        }
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value ?: return
        val added = s.apps.filter { it.packageName in s.selected && it.packageName !in s.original }
        val removed = (s.original - s.selected).toList()
        // 取消勾选会连同时段一起删
        guarded(
            change = { removed.associateWith { emptyList() } },
            apply = {
                repository.applySelection(added, removed, System.currentTimeMillis())
                onDone()
            },
        )
    }

    companion object {
        private const val USAGE_DAYS = 7L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(viewModel: AppPickerViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    if (pending != null) {
        ReasonDialog(onDismiss = viewModel::cancelPending, onConfirm = viewModel::confirmPending)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择管控应用") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save(onBack) }, enabled = state != null) { Text("保存") }
                },
            )
        },
    ) { padding ->
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = s.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (s.query.isEmpty()) {
                    null
                } else {
                    { IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Default.Close, contentDescription = "清除搜索") } }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(s.visibleApps, key = { it.packageName }) { app ->
                    val usage = s.usage[app.packageName] ?: 0L
                    ListItem(
                        modifier = Modifier.clickable { viewModel.toggle(app.packageName) },
                        leadingContent = { AppIcon(app.packageName) },
                        headlineContent = { Text(app.label) },
                        supportingContent = if (usage > 0L) {
                            { Text(StatsFormat.duration(usage)) }
                        } else {
                            null
                        },
                        trailingContent = { Checkbox(checked = app.packageName in s.selected, onCheckedChange = { viewModel.toggle(app.packageName) }) },
                    )
                }
            }
        }
    }
}
