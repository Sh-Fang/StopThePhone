package com.stopthephone.domain

import java.time.LocalDateTime

/**
 * 拦截判断：是否拦截、放行到何时、下次何时重新判断。
 */
object GuardRules {

    /**
     * 返回需要拦截时所在的管控时段；不在时段内或放行未到期时返回 null。
     */
    fun interceptWindow(rules: List<PeriodRule>, releaseEnd: LocalDateTime?, now: LocalDateTime): TimeWindow? {
        val window = Schedule.currentWindow(rules, now) ?: return null
        if (releaseEnd != null && now.isBefore(releaseEnd)) {
            return null
        }
        return window
    }

    /**
     * 放行结束时刻取「现在 + 放行时长」和「时段结束」中较早的那个。
     */
    fun releaseEnd(now: LocalDateTime, minutes: Int, window: TimeWindow): LocalDateTime =
        minOf(now.plusMinutes(minutes.toLong()), window.end)

    /**
     * 返回下一次需要重新判断的时刻：最近的时段边界或放行到期，取较早的。
     */
    fun nextCheck(rules: List<PeriodRule>, releaseEnd: LocalDateTime?, now: LocalDateTime): LocalDateTime? =
        listOfNotNull(Schedule.nextBoundary(rules, now), releaseEnd?.takeIf { it.isAfter(now) }).minOrNull()
}
