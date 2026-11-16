package com.stopthephone.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class ScheduleTest {

    // 2026-11-16 是周一
    private fun at(day: Int, hour: Int, minute: Int = 0): LocalDateTime = LocalDateTime.of(2026, 11, day, hour, minute)

    private fun mask(vararg days: DayOfWeek): Int = days.fold(0) { acc, d -> acc or PeriodRule.dayBit(d) }

    private val lunch = PeriodRule(12 * 60, 14 * 60, mask(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY))

    // 周五 22:00 到周六 07:00
    private val fridayNight = PeriodRule(22 * 60, 7 * 60, mask(DayOfWeek.FRIDAY))

    @Test
    fun startIsInclusiveEndIsExclusive() {
        assertEquals(TimeWindow(at(16, 12), at(16, 14)), Schedule.currentWindow(listOf(lunch), at(16, 12)))
        assertEquals(TimeWindow(at(16, 12), at(16, 14)), Schedule.currentWindow(listOf(lunch), at(16, 13, 59)))
        assertNull(Schedule.currentWindow(listOf(lunch), at(16, 14)))
        assertNull(Schedule.currentWindow(listOf(lunch), at(16, 11, 59)))
    }

    @Test
    fun uncheckedDayIsNotControlled() {
        // 11-21 是周六
        assertNull(Schedule.currentWindow(listOf(lunch), at(21, 13)))
    }

    @Test
    fun crossMidnightBelongsToStartDay() {
        val window = TimeWindow(at(20, 22), at(21, 7))
        assertEquals(window, Schedule.currentWindow(listOf(fridayNight), at(20, 23)))
        // 周六凌晨仍属于周五开始的时段
        assertEquals(window, Schedule.currentWindow(listOf(fridayNight), at(21, 6, 59)))
        assertNull(Schedule.currentWindow(listOf(fridayNight), at(21, 7)))
        // 周六晚上没勾选
        assertNull(Schedule.currentWindow(listOf(fridayNight), at(21, 23)))
        // 周四凌晨不属于任何时段
        assertNull(Schedule.currentWindow(listOf(fridayNight), at(19, 3)))
    }

    @Test
    fun equalStartAndEndMeansWholeDay() {
        val wholeMonday = PeriodRule(8 * 60, 8 * 60, mask(DayOfWeek.MONDAY))
        assertEquals(TimeWindow(at(16, 8), at(17, 8)), Schedule.currentWindow(listOf(wholeMonday), at(17, 7, 59)))
    }

    @Test
    fun adjacentAndOverlappingWindowsMerge() {
        val morning = PeriodRule(9 * 60, 12 * 60, PeriodRule.ALL_DAYS)
        val noon = PeriodRule(12 * 60, 13 * 60, PeriodRule.ALL_DAYS)
        val overlap = PeriodRule(12 * 60 + 30, 15 * 60, PeriodRule.ALL_DAYS)
        assertEquals(TimeWindow(at(16, 9), at(16, 15)), Schedule.currentWindow(listOf(morning, noon, overlap), at(16, 10)))
    }

    @Test
    fun nextBoundaryFindsNearestStartOrEnd() {
        assertEquals(at(16, 12), Schedule.nextBoundary(listOf(lunch), at(16, 9)))
        assertEquals(at(16, 14), Schedule.nextBoundary(listOf(lunch), at(16, 12)))
        // 周五 14:00 之后，下一个是下周一 12:00
        assertEquals(at(23, 12), Schedule.nextBoundary(listOf(lunch), at(20, 14)))
        assertEquals(at(21, 7), Schedule.nextBoundary(listOf(fridayNight), at(20, 22)))
    }

    @Test
    fun noRulesMeansNoWindow() {
        assertNull(Schedule.currentWindow(emptyList(), at(16, 12)))
        assertNull(Schedule.nextBoundary(emptyList(), at(16, 12)))
        assertNull(Schedule.currentWindow(listOf(PeriodRule(0, 60, 0)), at(16, 0, 30)))
    }
}
