package com.stopthephone.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stopthephone.data.ControlPeriod
import com.stopthephone.data.ControlledApp
import com.stopthephone.ui.common.AppIcon
import com.stopthephone.domain.PeriodRule
import com.stopthephone.ui.common.PeriodFormat
import com.stopthephone.ui.common.WheelPicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodEditorSheet(initial: ControlPeriod, onDismiss: () -> Unit, onConfirm: (ControlPeriod) -> Unit) {
    var start by remember { mutableIntStateOf(initial.startMinute) }
    var end by remember { mutableIntStateOf(initial.endMinute) }
    var daysMask by remember { mutableIntStateOf(initial.daysMask) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                TimeWheels("开始", start, onChange = { start = it })
                Text("–", style = MaterialTheme.typography.headlineSmall)
                TimeWheels(if (end <= start) "结束（次日）" else "结束", end, onChange = { end = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("每天" to PeriodRule.ALL_DAYS, "工作日" to PeriodFormat.WEEKDAYS, "周末" to PeriodFormat.WEEKEND).forEach { (name, mask) ->
                    FilterChip(selected = daysMask == mask, onClick = { daysMask = mask }, label = { Text(name) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                PeriodFormat.DAY_NAMES.forEachIndexed { i, name ->
                    val bit = 1 shl i
                    FilterChip(
                        selected = daysMask and bit != 0,
                        onClick = { daysMask = daysMask xor bit },
                        label = { Text(name, Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                        modifier = Modifier.weight(1f).semantics { contentDescription = "周$name" },
                    )
                }
            }
            Button(
                onClick = { onConfirm(initial.copy(startMinute = start, endMinute = end, daysMask = daysMask)) },
                enabled = daysMask != 0,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("保存") }
        }
    }
}

@Composable
private fun TimeWheels(name: String, minuteOfDay: Int, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            WheelPicker(
                count = HOURS,
                value = minuteOfDay / 60,
                onValueChange = { onChange(it * 60 + minuteOfDay % 60) },
                label = { "%02d".format(it) },
                name = "$name 时",
                width = 56.dp,
            )
            Text(":", style = MaterialTheme.typography.headlineSmall)
            WheelPicker(
                count = MINUTES,
                value = minuteOfDay % 60,
                onValueChange = { onChange(minuteOfDay / 60 * 60 + it) },
                label = { "%02d".format(it) },
                name = "$name 分",
                width = 56.dp,
            )
        }
    }
}

private const val HOURS = 24
private const val MINUTES = 60

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
