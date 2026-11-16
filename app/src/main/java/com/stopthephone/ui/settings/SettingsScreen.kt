package com.stopthephone.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.stopthephone.data.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settingsStore: SettingsStore) : ViewModel() {

    val defaultReleaseMinutes: StateFlow<Int?> = settingsStore.defaultReleaseMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun changeDefaultReleaseMinutes(delta: Int) {
        val current = defaultReleaseMinutes.value ?: return
        val next = (current + delta).coerceIn(SettingsStore.MIN_RELEASE_MINUTES, SettingsStore.MAX_RELEASE_MINUTES)
        viewModelScope.launch { settingsStore.setDefaultReleaseMinutes(next) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val minutes by viewModel.defaultReleaseMinutes.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        val value = minutes ?: return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text("默认放行时长") },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.changeDefaultReleaseMinutes(-SettingsStore.RELEASE_MINUTES_STEP) },
                                enabled = value > SettingsStore.MIN_RELEASE_MINUTES,
                            ) { Icon(Icons.Default.Remove, contentDescription = "减少") }
                            Text("$value 分钟")
                            IconButton(
                                onClick = { viewModel.changeDefaultReleaseMinutes(SettingsStore.RELEASE_MINUTES_STEP) },
                                enabled = value < SettingsStore.MAX_RELEASE_MINUTES,
                            ) { Icon(Icons.Default.Add, contentDescription = "增加") }
                        }
                    },
                )
            }
        }
    }
}
