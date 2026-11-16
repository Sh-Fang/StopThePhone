package com.stopthephone.guard

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * 用无障碍窗口在管控应用上盖一层全屏拦截页。
 */
class InterceptOverlay(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: FrameLayout? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    val isShowing: Boolean get() = root != null

    fun show(appLabel: String) {
        if (root != null) {
            return
        }
        val owner = OverlayLifecycleOwner().also { it.start() }
        val composeView = ComposeView(service).apply {
            setContent { InterceptContent(appLabel) }
        }
        // 截获返回键，不让它传到下面的应用
        val container = object : FrameLayout(service) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    Log.d(TAG, "back key captured, action=${event.action}")
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
        Log.d(TAG, "overlay shown for $appLabel")
    }

    fun hide() {
        val view = root ?: return
        windowManager.removeView(view)
        lifecycleOwner?.destroy()
        root = null
        lifecycleOwner = null
        Log.d(TAG, "overlay hidden")
    }

    @androidx.compose.runtime.Composable
    private fun InterceptContent(appLabel: String) {
        var reason by remember { mutableStateOf("") }
        MaterialTheme {
            Surface(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().imePadding().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(appLabel, style = MaterialTheme.typography.headlineMedium)
                    OutlinedTextField(
                        value = reason,
                        onValueChange = {
                            reason = it
                            Log.d(TAG, "reason input length=${it.length}")
                        },
                        label = { Text("使用理由") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = {
                        hide()
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    }) {
                        Text("离开")
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "StpOverlay"
    }
}
