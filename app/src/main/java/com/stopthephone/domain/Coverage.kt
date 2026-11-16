package com.stopthephone.domain

import java.time.LocalDateTime

/**
 * 一周内被管控的分钟集合，用来判断修改是否放宽了限制。
 */
object Coverage {

    private const val MINUTES_PER_DAY = 24 * 60
    private const val MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY

    /**
     * 新规则漏掉了旧规则覆盖的任何一分钟，就算放宽。
     */
    fun isLoosening(oldRules: List<PeriodRule>, newRules: List<PeriodRule>): Boolean {
        val old = weekMinutes(oldRules)
        val new = weekMinutes(newRules)
        return old.indices.any { old[it] && !new[it] }
    }

    /**
     * 应用正处于管控时段、且这次修改放宽了限制时，需要先填使用理由。
     */
    fun needsReason(oldRules: List<PeriodRule>, newRules: List<PeriodRule>, now: LocalDateTime): Boolean =
        Schedule.currentWindow(oldRules, now) != null && isLoosening(oldRules, newRules)

    private fun weekMinutes(rules: List<PeriodRule>): BooleanArray {
        val covered = BooleanArray(MINUTES_PER_WEEK)
        for (rule in rules) {
            // 开始等于结束表示整整 24 小时
            val length = Math.floorMod(rule.endMinute - rule.startMinute - 1, MINUTES_PER_DAY) + 1
            for (day in 0 until 7) {
                if (rule.daysMask and (1 shl day) == 0) {
                    continue
                }
                val start = day * MINUTES_PER_DAY + rule.startMinute
                for (offset in 0 until length) {
                    // 周日跨午夜的部分落到下周一
                    covered[(start + offset) % MINUTES_PER_WEEK] = true
                }
            }
        }
        return covered
    }
}
