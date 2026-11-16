package com.stopthephone.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class GuardRulesTest {

    // 2026-11-16 是周一
    private fun at(hour: Int, minute: Int = 0, day: Int = 16): LocalDateTime = LocalDateTime.of(2026, 11, day, hour, minute)

    private val evening = listOf(PeriodRule(21 * 60, 23 * 60, PeriodRule.ALL_DAYS))
    private val window = TimeWindow(at(21), at(23))

    @Test
    fun interceptsInsideWindowWithoutRelease() {
        assertEquals(window, GuardRules.interceptWindow(evening, null, at(21)))
        assertNull(GuardRules.interceptWindow(evening, null, at(20, 59)))
    }

    @Test
    fun activeReleaseSkipsIntercept() {
        assertNull(GuardRules.interceptWindow(evening, at(21, 30), at(21, 29)))
        // 放行到期那一刻重新拦截
        assertEquals(window, GuardRules.interceptWindow(evening, at(21, 30), at(21, 30)))
    }

    @Test
    fun releaseEndIsCappedByWindowEnd() {
        assertEquals(at(21, 15), GuardRules.releaseEnd(at(21), 15, window))
        assertEquals(at(23), GuardRules.releaseEnd(at(22, 50), 15, window))
    }

    @Test
    fun nextCheckIsEarlierOfBoundaryAndReleaseEnd() {
        assertEquals(at(21), GuardRules.nextCheck(evening, null, at(20)))
        assertEquals(at(21, 15), GuardRules.nextCheck(evening, at(21, 15), at(21, 1)))
        // 已过期的放行不参与
        assertEquals(at(23), GuardRules.nextCheck(evening, at(21, 15), at(21, 20)))
        assertNull(GuardRules.nextCheck(emptyList(), null, at(21)))
    }
}
