package com.stopthephone.guard

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/**
 * 检测前台应用，在管控时段内盖上拦截页。
 */
class GuardService : AccessibilityService() {

    private lateinit var overlay: InterceptOverlay

    override fun onServiceConnected() {
        overlay = InterceptOverlay(this)
        Log.d(TAG, "service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }
        val packageName = event.packageName?.toString() ?: return
        Log.d(TAG, "window state changed: $packageName")
        if (isIgnored(packageName)) {
            return
        }
        // TODO 原型验证：写死抖音为管控应用，接入管控配置后删除
        if (packageName == PROBE_TARGET) {
            overlay.show("抖音")
        } else if (overlay.isShowing) {
            // 前台已经换成别的应用（按 Home、切后台等），撤掉拦截页
            overlay.hide()
        } else {
        }
    }

    /**
     * 判断事件是否不代表前台应用切换：本应用、系统界面、输入法。
     */
    private fun isIgnored(packageName: String): Boolean {
        if (packageName == this.packageName || packageName == SYSTEM_UI) {
            return true
        }
        val imm = getSystemService(InputMethodManager::class.java)
        return imm.enabledInputMethodList.any { it.packageName == packageName }
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        if (::overlay.isInitialized) {
            overlay.hide()
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "StpGuard"
        private const val SYSTEM_UI = "com.android.systemui"
        private const val PROBE_TARGET = "com.ss.android.ugc.aweme"
    }
}
