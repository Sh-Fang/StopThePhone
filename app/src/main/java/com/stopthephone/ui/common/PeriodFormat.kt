package com.stopthephone.ui.common

import com.stopthephone.domain.PeriodRule

object PeriodFormat {

    val DAY_NAMES = listOf("一", "二", "三", "四", "五", "六", "日")

    const val WEEKDAYS = 0b0011111
    const val WEEKEND = 0b1100000

    fun time(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    fun range(rule: PeriodRule): String = "${time(rule.startMinute)}–${time(rule.endMinute)}"

    fun days(daysMask: Int): String = when (daysMask) {
        PeriodRule.ALL_DAYS -> "每天"
        WEEKDAYS -> "工作日"
        WEEKEND -> "周末"
        else -> "周" + DAY_NAMES.filterIndexed { i, _ -> daysMask and (1 shl i) != 0 }.joinToString("、")
    }
}
