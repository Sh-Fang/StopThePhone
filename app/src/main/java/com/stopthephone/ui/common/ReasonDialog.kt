package com.stopthephone.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.stopthephone.domain.ReasonRule

/**
 * 放宽限制前填写使用理由的弹窗。
 */
@Composable
fun ReasonDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    val effective = ReasonRule.effectiveLength(reason)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("使用理由") },
        text = {
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                supportingText = {
                    if (effective < ReasonRule.MIN_LENGTH) {
                        Text("$effective / ${ReasonRule.MIN_LENGTH}")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason) }, enabled = effective >= ReasonRule.MIN_LENGTH) { Text("确认修改") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
