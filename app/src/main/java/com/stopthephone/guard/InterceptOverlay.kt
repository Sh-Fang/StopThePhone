package com.stopthephone.guard

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.stopthephone.data.SettingsStore
import com.stopthephone.domain.ReasonRule
import com.stopthephone.ui.theme.StpTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class InterceptRequest(
    val packageName: String,
    val label: String,
    val windowEnd: LocalDateTime,
    val defaultMinutes: Int,
)

/**
 * 用无障碍窗口在管控应用上盖一层全屏拦截页。
 */
class InterceptOverlay(
    private val service: AccessibilityService,
    private val onRelease: (reason: String, minutes: Int) -> Unit,
    private val onLeave: () -> Unit,
) {

    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: FrameLayout? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private val request = mutableStateOf<InterceptRequest?>(null)

    val showingPackage: String? get() = if (root != null) request.value?.packageName else null

    fun show(newRequest: InterceptRequest) {
        request.value = newRequest
        if (root != null) {
            return
        }
        val owner = OverlayLifecycleOwner().also { it.start() }
        val composeView = ComposeView(service).apply {
            setContent { StpTheme { request.value?.let { InterceptContent(it) } } }
        }
        // 截获返回键，当作离开
        val container = object : FrameLayout(service) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) {
                        onLeave()
                    }
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        container.setViewTreeLifecycleOwner(owner)
        container.setViewTreeSavedStateRegistryOwner(owner)
        container.addView(composeView)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        ).apply {
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        windowManager.addView(container, params)
        root = container
        lifecycleOwner = owner
    }

    fun updateWindowEnd(windowEnd: LocalDateTime) {
        request.value = request.value?.copy(windowEnd = windowEnd)
    }

    fun hide() {
        val view = root ?: return
        windowManager.removeView(view)
        lifecycleOwner?.destroy()
        root = null
        lifecycleOwner = null
        request.value = null
    }

    @Composable
    private fun InterceptContent(req: InterceptRequest) {
        // 换了一个应用就重置输入
        key(req.packageName) {
            var reason by remember { mutableStateOf("") }
            var minutes by remember { mutableIntStateOf(req.defaultMinutes) }
            val effective = ReasonRule.effectiveLength(reason)
            Surface(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(req.label, style = MaterialTheme.typography.headlineMedium)
                    Text("至 ${formatEnd(req.windowEnd)}", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("使用理由") },
                        supportingText = {
                            if (effective < ReasonRule.MIN_LENGTH) {
                                Text("$effective / ${ReasonRule.MIN_LENGTH}")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { minutes -= SettingsStore.RELEASE_MINUTES_STEP },
                            enabled = minutes > SettingsStore.MIN_RELEASE_MINUTES,
                        ) { Icon(Icons.Default.Remove, contentDescription = "减少放行时长") }
                        Text("$minutes 分钟")
                        IconButton(
                            onClick = { minutes += SettingsStore.RELEASE_MINUTES_STEP },
                            enabled = minutes < SettingsStore.MAX_RELEASE_MINUTES,
                        ) { Icon(Icons.Default.Add, contentDescription = "增加放行时长") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(onClick = { onRelease(reason, minutes) }, enabled = effective >= ReasonRule.MIN_LENGTH) {
                            Text("放行")
                        }
                        Button(onClick = onLeave) {
                            Text("离开")
                        }
                    }
                }
            }
        }
    }

    private fun formatEnd(end: LocalDateTime): String {
        val today = LocalDateTime.now().toLocalDate()
        val time = end.format(TIME_FORMAT)
        return when (end.toLocalDate()) {
            today -> time
            today.plusDays(1) -> "明天 $time"
            else -> end.format(DATE_TIME_FORMAT)
        }
    }

    companion object {
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
        private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("M月d日 HH:mm")
    }
}
