package com.stopthephone.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class StatsTest {

    private val zone = ZoneOffset.UTC
    private val monday = LocalDate.of(2026, 11, 16)
    private val minute = 60_000L

    private fun at(day: LocalDate, hour: Int, min: Int = 0): Long =
        LocalDateTime.of(day, LocalTime.of(hour, min)).toInstant(zone).toEpochMilli()

    // 每天 21:00 到次日 07:00
    private val night = PeriodRule(21 * 60, 7 * 60, PeriodRule.ALL_DAYS)

    private fun input(
        sessions: List<SessionLite> = emptyList(),
        intercepts: List<InterceptLite> = emptyList(),
        releases: List<ReleaseLite> = emptyList(),
    ) = StatsInput(mapOf("a" to listOf(night)), sessions, intercepts, releases)

    @Test
    fun weekRangeStartsOnMonday() {
        val range = StatsRange(StatsScale.WEEK, monday.plusDays(3))
        assertEquals(monday, range.start)
        assertEquals(7, range.bucketStarts().size)
        assertEquals(monday.minusWeeks(1), range.previous().start)
    }

    @Test
    fun monthRangeHasOneBucketPerDay() {
        val range = StatsRange(StatsScale.MONTH, LocalDate.of(2026, 2, 10))
        assertEquals(28, range.bucketStarts().size)
    }

    @Test
    fun usageIsSplitByControlPeriod() {
        val sessions = listOf(SessionLite("a", at(monday, 20, 30), at(monday, 21, 30)))
        val report = Stats.report(input(sessions), StatsRange(StatsScale.DAY, monday), zone, now = at(monday, 23))
        assertEquals(30 * minute, report.totals.inPeriodMillis)
        assertEquals(30 * minute, report.totals.outPeriodMillis)
        assertEquals(30 * minute, report.buckets[20].outPeriodMillis)
        assertEquals(30 * minute, report.buckets[21].inPeriodMillis)
    }

    @Test
    fun interceptOverlapIsDeducted() {
        val sessions = listOf(SessionLite("a", at(monday, 22), at(monday, 22, 30)))
        val intercepts = listOf(InterceptLite("a", at(monday, 22), at(monday, 22, 10), released = true))
        val report = Stats.report(input(sessions, intercepts), StatsRange(StatsScale.DAY, monday), zone, now = at(monday, 23))
        assertEquals(20 * minute, report.totals.inPeriodMillis)
        assertEquals(1, report.totals.released)
    }

    @Test
    fun openInterceptIsDeductedUntilNow() {
        val sessions = listOf(SessionLite("a", at(monday, 22), at(monday, 22, 30)))
        val intercepts = listOf(InterceptLite("a", at(monday, 22, 5), null, released = null))
        val report = Stats.report(input(sessions, intercepts), StatsRange(StatsScale.DAY, monday), zone, now = at(monday, 22, 15))
        assertEquals(20 * minute, report.totals.inPeriodMillis)
        assertEquals(1, report.totals.intercepts)
        assertEquals(0, report.totals.released + report.totals.gaveUp)
    }

    @Test
    fun overnightWindowFromPreviousDayCounts() {
        val sessions = listOf(SessionLite("a", at(monday, 6, 30), at(monday, 7, 30)))
        val report = Stats.report(input(sessions), StatsRange(StatsScale.DAY, monday), zone, now = at(monday, 23))
        assertEquals(30 * minute, report.totals.inPeriodMillis)
        assertEquals(30 * minute, report.totals.outPeriodMillis)
    }

    @Test
    fun sessionIsClippedToRange() {
        val sessions = listOf(SessionLite("a", at(monday.minusDays(1), 23), at(monday, 1)))
        val report = Stats.report(input(sessions), StatsRange(StatsScale.DAY, monday), zone, now = at(monday, 23))
        assertEquals(60 * minute, report.totals.inPeriodMillis)
    }

    @Test
    fun uncontrolledAppsAreIgnored() {
        val sessions = listOf(SessionLite("b", at(monday, 22), at(monday, 23)))
        val intercepts = listOf(InterceptLite("b", at(monday, 22), at(monday, 22, 1), released = false))
        val report = Stats.report(input(sessions, intercepts), StatsRange(StatsScale.DAY, monday), zone, now = at(monday, 23))
        assertEquals(Tally(), report.totals)
        assertEquals(setOf("a"), report.apps.keys)
    }

    @Test
    fun heatmapAndOutcomesByShownAt() {
        val tuesday = monday.plusDays(1)
        val intercepts = listOf(
            InterceptLite("a", at(tuesday, 22, 5), at(tuesday, 22, 6), released = false),
            InterceptLite("a", at(tuesday, 22, 40), at(tuesday, 22, 41), released = true),
        )
        val releases = listOf(ReleaseLite("a", at(tuesday, 22, 41), at(tuesday, 22, 56)))
        val report = Stats.report(input(intercepts = intercepts, releases = releases), StatsRange(StatsScale.WEEK, monday), zone, now = at(tuesday, 23))
        assertEquals(2, report.heatmap[1][22])
        assertEquals(2, report.buckets[1].intercepts)
        assertEquals(1, report.totals.gaveUp)
        assertEquals(15 * minute, report.apps.getValue("a").releaseMillis)
    }
}
