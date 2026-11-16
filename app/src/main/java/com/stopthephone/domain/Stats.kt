package com.stopthephone.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class StatsScale { DAY, WEEK, MONTH }

/**
 * 统计的时间范围，日刻度按小时分桶，周和月刻度按天分桶。
 */
data class StatsRange(val scale: StatsScale, val anchor: LocalDate) {

    val start: LocalDate = when (scale) {
        StatsScale.DAY -> anchor
        StatsScale.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        StatsScale.MONTH -> anchor.withDayOfMonth(1)
    }

    val endExclusive: LocalDate = when (scale) {
        StatsScale.DAY -> start.plusDays(1)
        StatsScale.WEEK -> start.plusWeeks(1)
        StatsScale.MONTH -> start.plusMonths(1)
    }

    fun previous(): StatsRange = copy(anchor = shift(-1))

    fun next(): StatsRange = copy(anchor = shift(1))

    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && date.isBefore(endExclusive)

    fun bucketStarts(): List<LocalDateTime> = when (scale) {
        StatsScale.DAY -> (0 until HOURS_PER_DAY).map { start.atStartOfDay().plusHours(it.toLong()) }
        StatsScale.WEEK, StatsScale.MONTH -> generateSequence(start) { it.plusDays(1) }
            .takeWhile { it.isBefore(endExclusive) }
            .map { it.atStartOfDay() }
            .toList()
    }

    private fun shift(amount: Long): LocalDate = when (scale) {
        StatsScale.DAY -> start.plusDays(amount)
        StatsScale.WEEK -> start.plusWeeks(amount)
        StatsScale.MONTH -> start.plusMonths(amount)
    }

    companion object {
        const val HOURS_PER_DAY = 24
    }
}

data class SessionLite(val packageName: String, val startAt: Long, val endAt: Long)

/**
 * 拦截事件，released 为 null 表示还没有结局。
 */
data class InterceptLite(val packageName: String, val shownAt: Long, val closedAt: Long?, val released: Boolean?)

data class ReleaseLite(val packageName: String, val createdAt: Long, val endAt: Long)

data class Tally(
    val intercepts: Int = 0,
    val released: Int = 0,
    val gaveUp: Int = 0,
    val releaseMillis: Long = 0,
    val inPeriodMillis: Long = 0,
    val outPeriodMillis: Long = 0,
) {

    val usageMillis: Long get() = inPeriodMillis + outPeriodMillis

    operator fun plus(other: Tally) = Tally(
        intercepts + other.intercepts,
        released + other.released,
        gaveUp + other.gaveUp,
        releaseMillis + other.releaseMillis,
        inPeriodMillis + other.inPeriodMillis,
        outPeriodMillis + other.outPeriodMillis,
    )
}

/**
 * 统计结果，heatmap 第一维是周一到周日，第二维是 0 到 23 点，值是拦截次数。
 */
data class StatsReport(
    val totals: Tally,
    val buckets: List<Tally>,
    val apps: Map<String, Tally>,
    val heatmap: List<List<Int>>,
)

data class StatsInput(
    val periods: Map<String, List<PeriodRule>>,
    val sessions: List<SessionLite>,
    val intercepts: List<InterceptLite>,
    val releases: List<ReleaseLite>,
)

object Stats {

    private data class Span(val start: Long, val end: Long)

    /**
     * 按范围汇总管控应用的拦截、放行和使用时长；使用时长扣掉拦截页盖着的部分，再按管控时段拆成时段内和时段外。
     */
    fun report(input: StatsInput, range: StatsRange, zone: ZoneId, now: Long): StatsReport {
        val bounds = range.bucketStarts().map { it.toMillis(zone) } + range.endExclusive.atStartOfDay().toMillis(zone)
        val whole = Span(bounds.first(), bounds.last())
        val buckets = MutableList(bounds.size - 1) { Tally() }
        val apps = input.periods.keys.associateWith { Tally() }.toMutableMap()
        val heatmap = List(DayOfWeek.entries.size) { IntArray(StatsRange.HOURS_PER_DAY) }

        fun add(packageName: String, time: Long, tally: Tally) {
            val index = bounds.indexOfLast { it <= time }.coerceAtMost(buckets.lastIndex)
            buckets[index] = buckets[index] + tally
            apps[packageName] = apps.getValue(packageName) + tally
        }

        for (event in input.intercepts) {
            if (event.packageName !in apps || !whole.containsTime(event.shownAt)) {
                continue
            }
            val tally = when (event.released) {
                true -> Tally(intercepts = 1, released = 1)
                false -> Tally(intercepts = 1, gaveUp = 1)
                null -> Tally(intercepts = 1)
            }
            add(event.packageName, event.shownAt, tally)
            val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.shownAt), zone)
            heatmap[time.dayOfWeek.value - 1][time.hour]++
        }

        for (release in input.releases) {
            if (release.packageName in apps && whole.containsTime(release.createdAt)) {
                add(release.packageName, release.createdAt, Tally(releaseMillis = release.endAt - release.createdAt))
            }
        }

        val covered = input.intercepts.groupBy({ it.packageName }) { Span(it.shownAt, it.closedAt ?: now) }
        for ((packageName, sessions) in input.sessions.groupBy { it.packageName }) {
            val rules = input.periods[packageName] ?: continue
            val windows = controlWindows(rules, range, zone)
            val pieces = sessions.mapNotNull { Span(it.startAt, it.endAt).intersect(whole) }
                .flatMap { it.subtract(covered[packageName].orEmpty()) }
            for (piece in pieces) {
                for (inside in windows.mapNotNull { piece.intersect(it) }) {
                    distribute(inside, bounds) { time, millis -> add(packageName, time, Tally(inPeriodMillis = millis)) }
                }
                for (outside in piece.subtract(windows)) {
                    distribute(outside, bounds) { time, millis -> add(packageName, time, Tally(outPeriodMillis = millis)) }
                }
            }
        }

        return StatsReport(
            totals = buckets.fold(Tally()) { sum, it -> sum + it },
            buckets = buckets,
            apps = apps,
            heatmap = heatmap.map { it.toList() },
        )
    }

    // 范围内的管控时段，重叠或首尾相接的合并成一段
    private fun controlWindows(rules: List<PeriodRule>, range: StatsRange, zone: ZoneId): List<Span> {
        val spans = generateSequence(range.start.minusDays(1)) { it.plusDays(1) }
            .takeWhile { it.isBefore(range.endExclusive) }
            .flatMap { day -> rules.mapNotNull { it.windowStartingOn(day) } }
            .map { Span(it.start.toMillis(zone), it.end.toMillis(zone)) }
            .sortedBy { it.start }
        val merged = mutableListOf<Span>()
        for (span in spans) {
            val last = merged.lastOrNull()
            if (last != null && span.start <= last.end) {
                merged[merged.lastIndex] = Span(last.start, maxOf(last.end, span.end))
            } else {
                merged.add(span)
            }
        }
        return merged
    }

    private fun distribute(span: Span, bounds: List<Long>, onPart: (Long, Long) -> Unit) {
        for (i in 0 until bounds.size - 1) {
            val part = span.intersect(Span(bounds[i], bounds[i + 1])) ?: continue
            onPart(part.start, part.end - part.start)
        }
    }

    private fun Span.containsTime(time: Long): Boolean = time in start until end

    private fun Span.intersect(other: Span): Span? {
        val from = maxOf(start, other.start)
        val to = minOf(end, other.end)
        return if (from < to) Span(from, to) else null
    }

    private fun Span.subtract(cuts: List<Span>): List<Span> {
        val result = mutableListOf<Span>()
        var cursor = start
        for (cut in cuts.sortedBy { it.start }) {
            if (cut.end <= cursor || cut.start >= end) {
                continue
            }
            if (cut.start > cursor) {
                result.add(Span(cursor, cut.start))
            }
            cursor = maxOf(cursor, cut.end)
        }
        if (cursor < end) {
            result.add(Span(cursor, end))
        }
        return result
    }

    private fun LocalDateTime.toMillis(zone: ZoneId): Long = atZone(zone).toInstant().toEpochMilli()
}
