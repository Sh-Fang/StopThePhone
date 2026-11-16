package com.stopthephone.guard

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.stopthephone.StopThePhoneApp
import com.stopthephone.data.ControlledApp
import com.stopthephone.data.InterceptEvent
import com.stopthephone.data.InterceptOutcome
import com.stopthephone.data.RecordDao
import com.stopthephone.data.Release
import com.stopthephone.data.ReleasePurpose
import com.stopthephone.data.SettingsStore
import com.stopthephone.data.toRule
import com.stopthephone.domain.GuardRules
import com.stopthephone.domain.GuardStatus
import com.stopthephone.domain.PeriodRule
import com.stopthephone.domain.TimeWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 检测前台应用，在管控时段内盖上拦截页。
 */
class GuardService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var overlay: InterceptOverlay
    private lateinit var recordDao: RecordDao

    private var apps: Map<String, ControlledApp> = emptyMap()
    private var rulesByApp: Map<String, List<PeriodRule>> = emptyMap()
    // 每个应用生效中的放行结束时刻（毫秒）
    private val releaseEnds = mutableMapOf<String, Long>()
    // 每个应用最近一次放行写的理由
    private val releaseReasons = mutableMapOf<String, String>()
    private val releaseStarts = mutableMapOf<String, Long>()
    private var defaultMinutes = SettingsStore.DEFAULT_RELEASE_MINUTES_VALUE
    private var foreground: String? = null
    private var currentWindow: TimeWindow? = null
    // 当前拦截事件的写库结果，拿到 id 后才能更新结局
    private var currentIntercept: Deferred<Long>? = null
    private var timerJob: Job? = null
    private var statusJob: Job? = null
    private var showNotification = false
    private var foregroundStarted = false

    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            evaluate()
            refreshStatus()
        }
    }

    override fun onServiceConnected() {
        val app = application as StopThePhoneApp
        recordDao = app.database.recordDao()
        overlay = InterceptOverlay(this, onRelease = ::release, onLeave = ::leave)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, clockReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // 服务重新连上时用户可能正停在管控应用里，主动读一次当前前台
        foreground = rootInActiveWindow?.packageName?.toString()?.takeUnless { isIgnored(it) }

        // 先收尾遗留事件、载入放行，再开始按配置拦截
        scope.launch {
            recordDao.closeOpenIntercepts()
            recordDao.activeReleases(System.currentTimeMillis()).forEach { release ->
                val endAt = release.endAt ?: return@forEach
                if (endAt > (releaseEnds[release.packageName] ?: 0L)) {
                    releaseEnds[release.packageName] = endAt
                    releaseReasons[release.packageName] = release.reason
                    releaseStarts[release.packageName] = release.createdAt
                }
            }
            combine(app.controlRepository.apps, app.controlRepository.periods) { appList, periods ->
                appList.associateBy { it.packageName } to periods.groupBy({ it.packageName }, { it.toRule() })
            }.collect { (appMap, rules) ->
                apps = appMap
                rulesByApp = rules
                evaluate()
                refreshStatus()
            }
        }
        scope.launch {
            app.settingsStore.defaultReleaseMinutes.collect { defaultMinutes = it }
        }
        scope.launch {
            app.settingsStore.showNotification.collect { show ->
                showNotification = show
                if (show) {
                    refreshStatus()
                } else {
                    statusJob?.cancel()
                    GuardNotification.stop(this@GuardService)
                    foregroundStarted = false
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }
        val packageName = event.packageName?.toString() ?: return
        if (isIgnored(packageName)) {
            return
        }
        foreground = packageName
        evaluate()
    }

    /**
     * 按当前前台应用和时间决定盖上、保留还是撤掉拦截页，并安排下一次判断。
     */
    private fun evaluate() {
        if (!::overlay.isInitialized) {
            return
        }
        val now = LocalDateTime.now()
        val app = foreground?.let { apps[it] }
        val rules = app?.let { rulesByApp[it.packageName] }.orEmpty()
        val releaseEnd = app?.let { releaseEndOf(it.packageName) }
        val window = app?.let { GuardRules.interceptWindow(rules, releaseEnd, now) }

        if (app != null && window != null) {
            currentWindow = window
            if (overlay.showingPackage == app.packageName) {
                overlay.updateWindowEnd(window.end)
            } else {
                // 从一个被拦截的应用直接切到另一个，前一次算放弃
                closeIntercept(InterceptOutcome.GAVE_UP, null)
                showIntercept(app, window, now)
            }
        } else if (overlay.showingPackage != null) {
            closeIntercept(InterceptOutcome.GAVE_UP, null)
            overlay.hide()
        } else {
        }
        scheduleNext(if (app != null) GuardRules.nextCheck(rules, releaseEnd, now) else null, now)
    }

    private fun showIntercept(app: ControlledApp, window: TimeWindow, now: LocalDateTime) {
        overlay.show(InterceptRequest(app.packageName, app.label, window.end, defaultMinutes))
        val shownAt = now.toEpochMillis()
        currentIntercept = scope.async {
            recordDao.insertIntercept(InterceptEvent(packageName = app.packageName, shownAt = shownAt, closedAt = null, outcome = null, releaseId = null))
        }
    }

    private fun closeIntercept(outcome: InterceptOutcome, release: Release?) {
        val pending = currentIntercept ?: return
        currentIntercept = null
        val closedAt = System.currentTimeMillis()
        scope.launch {
            val releaseId = release?.let { recordDao.insertRelease(it) }
            recordDao.closeIntercept(pending.await(), closedAt, outcome, releaseId)
            refreshStatus()
        }
    }

    private fun release(reason: String, minutes: Int) {
        val packageName = overlay.showingPackage ?: return
        val window = currentWindow ?: return
        val now = LocalDateTime.now()
        val endAt = GuardRules.releaseEnd(now, minutes, window).toEpochMillis()
        releaseEnds[packageName] = endAt
        releaseReasons[packageName] = reason
        releaseStarts[packageName] = now.toEpochMillis()
        val release = Release(
            packageName = packageName,
            reason = reason,
            purpose = ReleasePurpose.RELEASE,
            createdAt = now.toEpochMillis(),
            durationMinutes = minutes,
            endAt = endAt,
        )
        closeIntercept(InterceptOutcome.RELEASED, release)
        overlay.hide()
        evaluate()
    }

    private fun leave() {
        closeIntercept(InterceptOutcome.GAVE_UP, null)
        overlay.hide()
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun scheduleNext(next: LocalDateTime?, now: LocalDateTime) {
        timerJob?.cancel()
        if (next == null) {
            return
        }
        val delayMillis = Duration.between(now, next).toMillis() + TIMER_SLACK_MILLIS
        timerJob = scope.launch {
            delay(delayMillis)
            evaluate()
        }
    }

    /**
     * 刷新常驻通知，并在状态下次变化或跨天时再刷新。
     */
    private fun refreshStatus() {
        statusJob?.cancel()
        if (!showNotification) {
            return
        }
        statusJob = scope.launch {
            val now = LocalDateTime.now()
            val zone = ZoneId.systemDefault()
            val releases = releaseEnds.mapValues { LocalDateTime.ofInstant(Instant.ofEpochMilli(it.value), zone) }
            val starts = releaseStarts.mapValues { LocalDateTime.ofInstant(Instant.ofEpochMilli(it.value), zone) }
            val status = GuardStatus.describe(apps.mapValues { it.value.label }, rulesByApp, releases, now, starts)
            val todayStart = now.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            val reason = status.releasedPackage?.let { releaseReasons[it] }
            val text = if (reason != null) {
                "你说：$reason"
            } else {
                "今日拦截 ${recordDao.countInterceptsSince(todayStart)} 次 · 放行 ${recordDao.countReleasesSince(todayStart)} 次"
            }
            // 首次直接带内容进前台，避免占位通知晚到覆盖内容
            runCatching {
                if (foregroundStarted) {
                    GuardNotification.update(this@GuardService, status.text, text)
                } else {
                    GuardNotification.startForeground(this@GuardService, status.text, text)
                    foregroundStarted = true
                }
            }.onFailure { Log.e(TAG, "notify failed", it) }

            val tomorrow = now.toLocalDate().plusDays(1).atStartOfDay()
            val next = listOfNotNull(status.nextChange, tomorrow).min()
            delay(Duration.between(now, next).toMillis() + TIMER_SLACK_MILLIS)
            refreshStatus()
        }
    }

    private fun releaseEndOf(packageName: String): LocalDateTime? =
        releaseEnds[packageName]?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }

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
            closeIntercept(InterceptOutcome.GAVE_UP, null)
            overlay.hide()
            unregisterReceiver(clockReceiver)
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun LocalDateTime.toEpochMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    companion object {
        private const val TAG = "StpGuard"
        private const val SYSTEM_UI = "com.android.systemui"
        // 到点后多等一点再判断，避免落在边界前一毫秒
        private const val TIMER_SLACK_MILLIS = 50L
    }
}
