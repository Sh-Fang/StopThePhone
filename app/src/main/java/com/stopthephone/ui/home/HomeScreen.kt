package com.stopthephone.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.stopthephone.data.ControlRepository
import com.stopthephone.data.toRule
import com.stopthephone.domain.PeriodRule
import com.stopthephone.ui.common.AppIcon
import com.stopthephone.ui.common.PeriodFormat
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AppSummary(val packageName: String, val label: String, val periods: List<PeriodRule>)

class HomeViewModel(repository: ControlRepository) : ViewModel() {

    val apps: StateFlow<List<AppSummary>?> = combine(repository.apps, repository.periods) { apps, periods ->
        val byApp = periods.groupBy { it.packageName }
        apps.map { app -> AppSummary(app.packageName, app.label, byApp[app.packageName].orEmpty().map { it.toRule() }) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel, onPickApps: () -> Unit, onOpenApp: (String) -> Unit, onOpenSettings: () -> Unit) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("StopThePhone") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!apps.isNullOrEmpty()) {
                FloatingActionButton(onClick = onPickApps) {
                    Icon(Icons.Default.Add, contentDescription = "选择管控应用")
                }
            }
        },
    ) { padding ->
        val list = apps ?: return@Scaffold
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Button(onClick = onPickApps) { Text("选择管控应用") }
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(list, key = { it.packageName }) { app ->
                ListItem(
                    modifier = Modifier.clickable { onOpenApp(app.packageName) },
                    leadingContent = { AppIcon(app.packageName) },
                    headlineContent = { Text(app.label) },
                    supportingContent = { Text(summary(app.periods)) },
                )
            }
        }
    }
}

private fun summary(periods: List<PeriodRule>): String {
    val first = periods.firstOrNull() ?: return "未设时段"
    val text = "${PeriodFormat.range(first)}  ${PeriodFormat.days(first.daysMask)}"
    return if (periods.size > 1) "$text  +${periods.size - 1}" else text
}
