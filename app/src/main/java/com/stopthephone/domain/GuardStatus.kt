package com.stopthephone.domain

import java.time.LocalDateTime

/**
 * 常驻通知的一行状态、显示的放行应用，以及状态下一次可能变化的时刻。
 */
data class GuardStatusLine(val text: String, val nextChange: LocalDateTime?, val releasedPackage: String? = null)

object GuardStatus {

    private const val MAX_NAMES = 2

    /**
     * 按放行中、管控中、下次开始的优先级描述当前状态，多个应用放行时显示最近放行的那个。
     */
    fun describe(
        labels: Map<String, String>,
        rulesByApp: Map<String, List<PeriodRule>>,
        releaseEnds: Map<String, LocalDateTime>,
        now: LocalDateTime,
        releaseStarts: Map<String, LocalDateTime> = emptyMap(),
    ): GuardStatusLine {
        val nextChange = labels.keys
            .mapNotNull { GuardRules.nextCheck(rulesByApp[it].orEmpty(), releaseEnds[it], now) }
            .minOrNull()

        val released = labels.keys
            .mapNotNull { packageName -> releaseEnds[packageName]?.takeIf { it.isAfter(now) }?.let { packageName to it } }
            .sortedByDescending { releaseStarts[it.first] ?: LocalDateTime.MIN }
        if (released.isNotEmpty()) {
            val (packageName, end) = released.first()
            val more = if (released.size > 1) " 等 ${released.size} 个应用" else ""
            return GuardStatusLine("${labels.getValue(packageName)}$more 放行至 ${clock(end)}", nextChange, packageName)
        }

        val controlled = labels.keys
            .mapNotNull { packageName -> Schedule.currentWindow(rulesByApp[packageName].orEmpty(), now)?.let { packageName to it } }
            .sortedBy { it.second.end }
        if (controlled.size == 1) {
            val (packageName, window) = controlled.first()
            return GuardStatusLine("${labels.getValue(packageName)} 管控中 · 至 ${clock(window.end)}", nextChange)
        } else if (controlled.size > 1) {
            val names = controlled.take(MAX_NAMES).joinToString("、") { labels.getValue(it.first) }
            val more = if (controlled.size > MAX_NAMES) " 等 ${controlled.size} 个应用" else ""
            return GuardStatusLine("$names$more 管控中", nextChange)
        } else {
        }

        val nextStart = labels.keys.mapNotNull { Schedule.nextStart(rulesByApp[it].orEmpty(), now) }.minOrNull()
            ?: return GuardStatusLine("没有管控时段", nextChange)
        val day = when (nextStart.toLocalDate()) {
            now.toLocalDate() -> ""
            now.toLocalDate().plusDays(1) -> "明天 "
            else -> "${nextStart.monthValue}月${nextStart.dayOfMonth}日 "
        }
        return GuardStatusLine("$day${clock(nextStart)} 开始管控", nextChange)
    }

    private fun clock(time: LocalDateTime): String = "%02d:%02d".format(time.hour, time.minute)
}
