package com.stopthephone.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stopthephone.data.ControlPeriod
import com.stopthephone.data.ControlledApp
import com.stopthephone.ui.common.AppIcon
import com.stopthephone.ui.common.PeriodFormat

private enum class TimeField { START, END }

@Composable
fun PeriodEditorDialog(initial: ControlPeriod, onDismiss: () -> Unit, onConfirm: (ControlPeriod) -> Unit) {
    var start by remember { mutableIntStateOf(initial.startMinute) }
    var end by remember { mutableIntStateOf(initial.endMinute) }
    var daysMask by remember { mutableIntStateOf(initial.daysMask) }
    var picking by remember { mutableStateOf<TimeField?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管控时段") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { picking = TimeField.START },
                        modifier = Modifier.weight(1f).semantics { contentDescription = "开始 ${PeriodFormat.time(start)}" },
                    ) { Text(PeriodFormat.time(start)) }
                    OutlinedButton(
                        onClick = { picking = TimeField.END },
                        modifier = Modifier.weight(1f).semantics { contentDescription = "结束 ${PeriodFormat.time(end)}" },
                    ) { Text(PeriodFormat.time(end)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    PeriodFormat.DAY_NAMES.forEachIndexed { i, name ->
                        val bit = 1 shl i
                        FilterChip(
                            selected = daysMask and bit != 0,
                            onClick = { daysMask = daysMask xor bit },
                            label = { Text(name) },
                            modifier = Modifier.weight(1f).semantics { contentDescription = "周$name" },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(initial.copy(startMinute = start, endMinute = end, daysMask = daysMask)) },
                enabled = daysMask != 0,
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )

    picking?.let { field ->
        TimePickerDialog(
            initialMinute = if (field == TimeField.START) start else end,
            onDismiss = { picking = null },
            onConfirm = { minute ->
                if (field == TimeField.START) start = minute else end = minute
                picking = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initialMinute: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinute / 60, initialMinute = initialMinute % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
fun CopyPeriodsDialog(candidates: List<ControlledApp>, onDismiss: () -> Unit, onConfirm: (List<String>) -> Unit) {
    var selected by remember { mutableStateOf(emptySet<String>()) }
    val toggle = { packageName: String -> selected = if (packageName in selected) selected - packageName else selected + packageName }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("覆盖所选应用的时段") },
        text = {
            LazyColumn {
                items(candidates, key = { it.packageName }) { app ->
                    ListItem(
                        modifier = Modifier.clickable { toggle(app.packageName) },
                        leadingContent = { AppIcon(app.packageName, size = 32.dp) },
                        headlineContent = { Text(app.label) },
                        trailingContent = { Checkbox(checked = app.packageName in selected, onCheckedChange = { toggle(app.packageName) }) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected.toList()) }, enabled = selected.isNotEmpty()) { Text("覆盖") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
