package com.stopthephone.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class GuardStatusTest {

    // 2026-11-16 是周一
    private fun at(hour: Int, minute: Int = 0, day: Int = 16): LocalDateTime = LocalDateTime.of(2026, 11, day, hour, minute)

    private val evening = listOf(PeriodRule(21 * 60, 23 * 60, PeriodRule.ALL_DAYS))
    private val night = listOf(PeriodRule(22 * 60, 7 * 60, PeriodRule.ALL_DAYS))
    private val labels = mapOf("a" to "抖音", "b" to "哔哩哔哩", "c" to "微博")

    @Test
    fun noAppsHasNoPeriod() {
        val status = GuardStatus.describe(emptyMap(), emptyMap(), emptyMap(), at(12))
        assertEquals("没有管控时段", status.text)
        assertNull(status.nextChange)
    }

    @Test
    fun showsNextStartToday() {
        val status = GuardStatus.describe(labels, mapOf("a" to evening, "b" to night), emptyMap(), at(12))
        assertEquals("21:00 开始管控", status.text)
        assertEquals(at(21), status.nextChange)
    }

    @Test
    fun showsNextStartTomorrow() {
        val workdays = listOf(PeriodRule(9 * 60, 12 * 60, 0b0011111))
        assertEquals("明天 09:00 开始管控", GuardStatus.describe(labels, mapOf("a" to workdays), emptyMap(), at(13)).text)
        // 周五之后要到下周一
        assertEquals("11月23日 09:00 开始管控", GuardStatus.describe(labels, mapOf("a" to workdays), emptyMap(), at(13, day = 20)).text)
    }

    @Test
    fun singleControlledAppShowsEnd() {
        val status = GuardStatus.describe(labels, mapOf("a" to night), emptyMap(), at(23))
        assertEquals("抖音 管控中 · 至 07:00", status.text)
        assertEquals(at(7, day = 17), status.nextChange)
    }

    @Test
    fun multipleControlledAppsAreListed() {
        val two = GuardStatus.describe(labels, mapOf("a" to night, "b" to evening), emptyMap(), at(22, 30))
        assertEquals("哔哩哔哩、抖音 管控中", two.text)
        val three = GuardStatus.describe(labels, mapOf("a" to night, "b" to evening, "c" to evening), emptyMap(), at(22, 30))
        assertEquals("哔哩哔哩、微博 等 3 个应用 管控中", three.text)
    }

    @Test
    fun releaseTakesPriority() {
        val status = GuardStatus.describe(labels, mapOf("a" to evening, "b" to evening), mapOf("b" to at(21, 15)), at(21, 5))
        assertEquals("哔哩哔哩 放行至 21:15", status.text)
        assertEquals("b", status.releasedPackage)
        assertEquals(at(21, 15), status.nextChange)
        // 多个放行时显示最近放行的
        val latest = GuardStatus.describe(
            labels,
            mapOf("a" to evening, "b" to evening),
            mapOf("a" to at(21, 30), "b" to at(21, 15)),
            at(21, 10),
            mapOf("a" to at(21, 0), "b" to at(21, 5)),
        )
        assertEquals("哔哩哔哩 等 2 个应用 放行至 21:15", latest.text)
        assertEquals("b", latest.releasedPackage)
        // 到期的放行不再显示
        assertEquals("抖音、哔哩哔哩 管控中", GuardStatus.describe(labels, mapOf("a" to evening, "b" to evening), mapOf("b" to at(21, 15)), at(21, 15)).text)
    }
}
