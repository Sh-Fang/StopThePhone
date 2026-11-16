package com.stopthephone.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopthephone.data.ControlPeriod
import com.stopthephone.data.ControlRepository
import com.stopthephone.data.toRule
import com.stopthephone.domain.Coverage
import com.stopthephone.domain.ReasonRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * 修改管控配置的 ViewModel 基类：放宽正处于管控时段的应用时，先要使用理由。
 */
abstract class GuardedViewModel(protected val repository: ControlRepository) : ViewModel() {

    class PendingChange(val packageNames: List<String>, val apply: suspend () -> Unit)

    private val _pending = MutableStateFlow<PendingChange?>(null)
    val pending: StateFlow<PendingChange?> = _pending

    /**
     * change 根据当前各应用的时段，返回受影响应用修改后的时段。
     */
    protected fun guarded(change: (Map<String, List<ControlPeriod>>) -> Map<String, List<ControlPeriod>>, apply: suspend () -> Unit) {
        viewModelScope.launch {
            val current = repository.periods.first().groupBy { it.packageName }
            val now = LocalDateTime.now()
            val locked = change(current).filter { (packageName, newPeriods) ->
                val oldRules = current[packageName].orEmpty().map { it.toRule() }
                Coverage.needsReason(oldRules, newPeriods.map { it.toRule() }, now)
            }.keys.toList()
            if (locked.isEmpty()) {
                apply()
            } else {
                _pending.value = PendingChange(locked, apply)
            }
        }
    }

    fun confirmPending(reason: String) {
        val change = _pending.value ?: return
        if (!ReasonRule.isValid(reason)) {
            return
        }
        _pending.value = null
        viewModelScope.launch {
            repository.recordSettingsReason(change.packageNames, reason, System.currentTimeMillis())
            change.apply()
        }
    }

    fun cancelPending() {
        _pending.value = null
    }
}
