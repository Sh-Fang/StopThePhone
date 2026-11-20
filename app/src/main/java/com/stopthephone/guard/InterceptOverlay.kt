package com.stopthephone.guard

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.stopthephone.data.SettingsStore
import com.stopthephone.domain.ReasonRule
import com.stopthephone.ui.common.AppIcon
import com.stopthephone.ui.theme.BlockTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class InterceptRequest(
    val packageName: String,
    val label: String,
    val windowEnd: LocalDateTime,
    val defaultMinutes: Int,
)

private data class Draft(val reason: String, val minutes: Int, val expiresAt: LocalDateTime)

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
    private val reason = mutableStateOf("")
    private val minutes = mutableIntStateOf(0)
    private val drafts = mutableMapOf<String, Draft>()
    private val audioManager = service.getSystemService(AudioManager::class.java)
    private val audioFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
        .setOnAudioFocusChangeListener { }
        .build()

    val showingPackage: String? get() = if (root != null) request.value?.packageName else null

    fun show(newRequest: InterceptRequest) {
        if (request.value?.packageName != newRequest.packageName) {
            saveDraft()
            restoreDraft(newRequest)
        }
        request.value = newRequest
        if (root != null) {
            return
        }
        val owner = OverlayLifecycleOwner().also { it.start() }
        val composeView = ComposeView(service).apply {
            setContent { BlockTheme { request.value?.let { InterceptContent(it) } } }
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
        // 先用纯黑底挡住应用，不等 Compose 画出第一帧
        container.setBackgroundColor(android.graphics.Color.BLACK)
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
            // 铺到状态栏和导航栏下面
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            }
        }
        windowManager.addView(container, params)
        // 黑底上用浅色状态栏图标
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            container.windowInsetsController?.setSystemBarsAppearance(0, light)
        }
        root = container
        lifecycleOwner = owner
        pauseMedia()
    }

    fun updateWindowEnd(windowEnd: LocalDateTime) {
        request.value = request.value?.copy(windowEnd = windowEnd)
    }

    fun hide() {
        val view = root ?: return
        saveDraft()
        windowManager.removeView(view)
        audioManager.abandonAudioFocusRequest(audioFocus)
        lifecycleOwner?.destroy()
        root = null
        lifecycleOwner = null
        request.value = null
    }

    /**
     * 把当前应用没提交的理由和时长存成草稿，有效到本次管控时段结束。
     */
    private fun saveDraft() {
        val req = request.value ?: return
        if (reason.value.isBlank()) {
            drafts.remove(req.packageName)
        } else {
            drafts[req.packageName] = Draft(reason.value, minutes.intValue, req.windowEnd)
        }
    }

    /**
     * 填回该应用还没过期的草稿，没有就从空白开始。
     */
    private fun restoreDraft(req: InterceptRequest) {
        val draft = drafts.remove(req.packageName)?.takeIf { LocalDateTime.now().isBefore(it.expiresAt) }
        reason.value = draft?.reason.orEmpty()
        minutes.intValue = draft?.minutes ?: req.defaultMinutes
    }

    /**
     * 抢占媒体音频焦点并发送暂停键，让后台的视频和音乐停下来。
     */
    private fun pauseMedia() {
        audioManager.requestAudioFocus(audioFocus)
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
    }

    @Composable
    private fun InterceptContent(req: InterceptRequest) {
        val ready = ReasonRule.effectiveLength(reason.value) >= ReasonRule.MIN_LENGTH
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                if (maxWidth > maxHeight) {
                    LandscapeContent(req, ready)
                } else {
                    PortraitContent(req, ready)
                }
            }
        }
    }

    @Composable
    private fun PortraitContent(req: InterceptRequest, ready: Boolean) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Header(req, Modifier.padding(bottom = 40.dp))
            ReasonField()
            MinutesStepper()
            LeaveButton(Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            ReleaseButton(ready, Modifier.fillMaxWidth())
            Spacer(Modifier.weight(1f))
        }
    }

    /**
     * 横屏分左右两栏：左边应用信息，右边输入和操作。
     */
    @Composable
    private fun LandscapeContent(req: InterceptRequest, ready: Boolean) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Header(req, Modifier.weight(1f))
            // 右栏放不下时可以滚动
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    ReasonField()
                    MinutesStepper()
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LeaveButton(Modifier.weight(1f))
                        ReleaseButton(ready, Modifier.weight(1f))
                    }
                }
            }
        }
    }

    @Composable
    private fun Header(req: InterceptRequest, modifier: Modifier) {
        val colors = MaterialTheme.colorScheme
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier,
        ) {
            AppIcon(req.packageName, size = 72.dp)
            Text(req.label, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            Text(
                buildAnnotatedString {
                    append("管控中 · 至 ")
                    withStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.SemiBold)) { append(formatEnd(req.windowEnd)) }
                },
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
        }
    }

    @Composable
    private fun ReasonField() {
        val effective = ReasonRule.effectiveLength(reason.value)
        val colors = MaterialTheme.colorScheme
        TextField(
            value = reason.value,
            onValueChange = { reason.value = it },
            label = { Text("使用理由") },
            supportingText = {
                Text(
                    "$effective / ${ReasonRule.MIN_LENGTH}",
                    color = if (effective >= ReasonRule.MIN_LENGTH) colors.primary else colors.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            minLines = 2,
            shape = MaterialTheme.shapes.medium,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceVariant,
                unfocusedContainerColor = colors.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    @Composable
    private fun MinutesStepper() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            FilledTonalIconButton(
                onClick = { minutes.intValue -= SettingsStore.RELEASE_MINUTES_STEP },
                enabled = minutes.intValue > SettingsStore.MIN_RELEASE_MINUTES,
            ) { Icon(Icons.Default.Remove, contentDescription = "减少放行时长") }
            Text(
                "${minutes.intValue} 分钟",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 80.dp),
            )
            FilledTonalIconButton(
                onClick = { minutes.intValue += SettingsStore.RELEASE_MINUTES_STEP },
                enabled = minutes.intValue < SettingsStore.MAX_RELEASE_MINUTES,
            ) { Icon(Icons.Default.Add, contentDescription = "增加放行时长") }
        }
    }

    @Composable
    private fun LeaveButton(modifier: Modifier) {
        Button(onClick = onLeave, modifier = modifier.height(52.dp), shape = MaterialTheme.shapes.medium) {
            Text("离开", style = MaterialTheme.typography.titleMedium)
        }
    }

    @Composable
    private fun ReleaseButton(ready: Boolean, modifier: Modifier) {
        val colors = MaterialTheme.colorScheme
        OutlinedButton(
            onClick = {
                val text = reason.value
                // 放行后不留草稿
                reason.value = ""
                onRelease(text, minutes.intValue)
            },
            enabled = ready,
            border = BorderStroke(1.dp, if (ready) colors.onSurface else colors.outlineVariant),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
            shape = MaterialTheme.shapes.medium,
            modifier = modifier.height(52.dp),
        ) { Text("放行", style = MaterialTheme.typography.titleMedium) }
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
