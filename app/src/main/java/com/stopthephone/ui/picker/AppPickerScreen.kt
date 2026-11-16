package com.stopthephone.ui.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.stopthephone.data.ControlRepository
import com.stopthephone.data.InstalledApp
import com.stopthephone.data.InstalledApps
import com.stopthephone.ui.common.AppIcon
import com.stopthephone.ui.common.GuardedViewModel
import com.stopthephone.ui.common.ReasonDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PickerState(val apps: List<InstalledApp>, val original: Set<String>, val selected: Set<String>)

class AppPickerViewModel(repository: ControlRepository, installedApps: InstalledApps) : GuardedViewModel(repository) {

    private val _state = MutableStateFlow<PickerState?>(null)
    val state: StateFlow<PickerState?> = _state

    init {
        viewModelScope.launch {
            val controlled = repository.apps.first().map { it.packageName }.toSet()
            _state.value = PickerState(installedApps.list(), controlled, controlled)
        }
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
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(s.apps, key = { it.packageName }) { app ->
                ListItem(
                    modifier = Modifier.clickable { viewModel.toggle(app.packageName) },
                    leadingContent = { AppIcon(app.packageName) },
                    headlineContent = { Text(app.label) },
                    trailingContent = { Checkbox(checked = app.packageName in s.selected, onCheckedChange = { viewModel.toggle(app.packageName) }) },
                )
            }
        }
    }
}
