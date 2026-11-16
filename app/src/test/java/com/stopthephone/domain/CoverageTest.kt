package com.stopthephone.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CoverageTest {

    private val weekdaysEvening = PeriodRule(21 * 60, 23 * 60, 0b0011111)

    @Test
    fun removingOrShrinkingIsLoosening() {
        assertTrue(Coverage.isLoosening(listOf(weekdaysEvening), emptyList()))
        assertTrue(Coverage.isLoosening(listOf(weekdaysEvening), listOf(weekdaysEvening.copy(endMinute = 22 * 60))))
        assertTrue(Coverage.isLoosening(listOf(weekdaysEvening), listOf(weekdaysEvening.copy(daysMask = 0b0001111))))
    }

    @Test
    fun extendingOrAddingIsNotLoosening() {
        assertFalse(Coverage.isLoosening(listOf(weekdaysEvening), listOf(weekdaysEvening.copy(startMinute = 20 * 60))))
        assertFalse(Coverage.isLoosening(listOf(weekdaysEvening), listOf(weekdaysEvening.copy(daysMask = PeriodRule.ALL_DAYS))))
        assertFalse(Coverage.isLoosening(emptyList(), listOf(weekdaysEvening)))
        assertFalse(Coverage.isLoosening(listOf(weekdaysEvening), listOf(weekdaysEvening)))
    }

    @Test
    fun splittingWithoutGapIsNotLoosening() {
        val split = listOf(PeriodRule(21 * 60, 22 * 60, 0b0011111), PeriodRule(22 * 60, 23 * 60, 0b0011111))
        assertFalse(Coverage.isLoosening(listOf(weekdaysEvening), split))
    }

    @Test
    fun sundayCrossMidnightWrapsToMonday() {
        val sundayNight = PeriodRule(23 * 60, 1 * 60, 0b1000000)
        val mondayEarly = PeriodRule(0, 1 * 60, 0b0000001)
        val sundayLate = PeriodRule(23 * 60, 0, 0b1000000)
        assertFalse(Coverage.isLoosening(listOf(sundayNight), listOf(sundayLate, mondayEarly)))
        assertTrue(Coverage.isLoosening(listOf(sundayNight), listOf(sundayLate)))
    }

    @Test
    fun reasonOnlyNeededInsideWindow() {
        // 2026-11-16 是周一
        val inside = LocalDateTime.of(2026, 11, 16, 21, 30)
        val outside = LocalDateTime.of(2026, 11, 16, 20, 0)
        assertTrue(Coverage.needsReason(listOf(weekdaysEvening), emptyList(), inside))
        assertFalse(Coverage.needsReason(listOf(weekdaysEvening), emptyList(), outside))
        assertFalse(Coverage.needsReason(listOf(weekdaysEvening), listOf(weekdaysEvening.copy(startMinute = 20 * 60)), inside))
    }
}
