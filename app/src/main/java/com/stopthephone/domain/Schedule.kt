package com.stopthephone.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 一段管控时段的规则，endMinute 不大于 startMinute 时表示跨午夜。
 */
data class PeriodRule(
    val startMinute: Int,
    val endMinute: Int,
    // 第 0 位是周一，第 6 位是周日
    val daysMask: Int,
) {

    fun isEnabledOn(day: DayOfWeek): Boolean = daysMask and dayBit(day) != 0

    /**
     * 返回从指定日期开始的那一次时段，这一天没勾选时返回 null。
     */
    fun windowStartingOn(date: LocalDate): TimeWindow? {
        if (!isEnabledOn(date.dayOfWeek)) {
            return null
        }
        val start = date.atStartOfDay().plusMinutes(startMinute.toLong())
        // 跨午夜的时段结束在第二天
        val endDate = if (endMinute > startMinute) date else date.plusDays(1)
        val end = endDate.atStartOfDay().plusMinutes(endMinute.toLong())
        return TimeWindow(start, end)
    }

    companion object {
        const val ALL_DAYS = 0b1111111

        fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)
    }
}

/**
 * 左闭右开的时间区间。
 */
data class TimeWindow(val start: LocalDateTime, val end: LocalDateTime) {

    fun contains(time: LocalDateTime): Boolean = !time.isBefore(start) && time.isBefore(end)
}

object Schedule {

    // 往后最多看 8 天，覆盖一整周再加跨午夜的一天
    private const val LOOKAHEAD_DAYS = 8L

    /**
     * 返回包含指定时刻的管控时段；首尾相接或重叠的时段合并成一段。
     */
    fun currentWindow(rules: List<PeriodRule>, time: LocalDateTime): TimeWindow? {
        val windows = windowsAround(rules, time.toLocalDate())
        val containing = windows.filter { it.contains(time) }
        if (containing.isEmpty()) {
            return null
        }
        val start = containing.minOf { it.start }
        var end = containing.maxOf { it.end }
        // 后面的时段在当前结束时刻之前或正好在结束时刻开始，就接上
        while (true) {
            val next = windows.filter { !it.start.isAfter(end) && it.end.isAfter(end) }.maxOfOrNull { it.end } ?: break
            end = next
        }
        return TimeWindow(start, end)
    }

    /**
     * 返回指定时刻之后最近的一个时段开始或结束时刻，没有时返回 null。
     */
    fun nextBoundary(rules: List<PeriodRule>, time: LocalDateTime): LocalDateTime? {
        return windowsAround(rules, time.toLocalDate())
            .flatMap { listOf(it.start, it.end) }
            .filter { it.isAfter(time) }
            .minOrNull()
    }

    private fun windowsAround(rules: List<PeriodRule>, date: LocalDate): List<TimeWindow> {
        val windows = mutableListOf<TimeWindow>()
        // 从前一天算起，前一天开始的跨午夜时段可能还没结束
        var day = date.minusDays(1)
        val lastDay = date.plusDays(LOOKAHEAD_DAYS)
        while (!day.isAfter(lastDay)) {
            for (rule in rules) {
                rule.windowStartingOn(day)?.let { windows.add(it) }
            }
            day = day.plusDays(1)
        }
        return windows
    }
}
