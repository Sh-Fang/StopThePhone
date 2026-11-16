package com.stopthephone.ui.stats

import com.stopthephone.domain.StatsRange
import com.stopthephone.domain.StatsScale
import com.stopthephone.ui.common.PeriodFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object StatsFormat {

    private const val MONTH_LABEL_STEP = 7

    fun duration(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        return when {
            millis == 0L -> "0分"
            minutes == 0L -> "<1分"
            minutes < 60 -> "${minutes}分"
            minutes % 60 == 0L -> "${minutes / 60}小时"
            else -> "${minutes / 60}小时${minutes % 60}分"
        }
    }

    fun range(range: StatsRange, today: LocalDate): String = when (range.scale) {
        StatsScale.DAY -> if (range.start == today) "今天" else "${date(range.start)} 周${PeriodFormat.DAY_NAMES[range.start.dayOfWeek.value - 1]}"
        StatsScale.WEEK -> {
            val last = range.endExclusive.minusDays(1)
            val end = if (last.month == range.start.month) "${last.dayOfMonth}日" else date(last)
            "${date(range.start)}–$end"
        }
        StatsScale.MONTH -> "${range.start.year}年${range.start.monthValue}月"
    }

    fun bucket(range: StatsRange, start: LocalDateTime): String = when (range.scale) {
        StatsScale.DAY -> "${start.hour}:00–${start.hour + 1}:00"
        StatsScale.WEEK, StatsScale.MONTH -> "${date(start.toLocalDate())} 周${PeriodFormat.DAY_NAMES[start.dayOfWeek.value - 1]}"
    }

    /**
     * 横轴标签，只在部分柱子下显示，避免挤在一起。
     */
    fun axisLabels(range: StatsRange): List<String?> = range.bucketStarts().mapIndexed { index, start ->
        when (range.scale) {
            StatsScale.DAY -> if (start.hour % 6 == 0) "${start.hour}" else null
            StatsScale.WEEK -> PeriodFormat.DAY_NAMES[index]
            StatsScale.MONTH -> if (index % MONTH_LABEL_STEP == 0) "${start.dayOfMonth}" else null
        }
    }

    private fun date(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"
}
