package com.stopthephone.domain

import com.stopthephone.domain.UsageEventKind.ALL_STOPPED
import com.stopthephone.domain.UsageEventKind.PAUSED
import com.stopthephone.domain.UsageEventKind.RESUMED
import org.junit.Assert.assertEquals
import org.junit.Test

class UsagePairingTest {

    private fun e(packageName: String, time: Long, kind: UsageEventKind) = UsageEventLite(packageName, time, kind)

    @Test
    fun pairsResumeAndPause() {
        val result = UsagePairing.pair(listOf(e("a", 10, RESUMED), e("a", 30, PAUSED), e("b", 40, RESUMED), e("b", 45, PAUSED)), until = 100)
        assertEquals(listOf(ForegroundSession("a", 10, 30), ForegroundSession("b", 40, 45)), result.sessions)
        assertEquals(100, result.nextFrom)
    }

    @Test
    fun missingPauseIsClosedByNextResume() {
        val result = UsagePairing.pair(listOf(e("a", 10, RESUMED), e("b", 20, RESUMED), e("b", 25, PAUSED)), until = 100)
        assertEquals(listOf(ForegroundSession("a", 10, 20), ForegroundSession("b", 20, 25)), result.sessions)
    }

    @Test
    fun screenOffClosesEverything() {
        val result = UsagePairing.pair(listOf(e("a", 10, RESUMED), e("", 50, ALL_STOPPED), e("a", 60, PAUSED)), until = 100)
        assertEquals(listOf(ForegroundSession("a", 10, 50)), result.sessions)
    }

    @Test
    fun repeatedResumeKeepsEarliestStart() {
        val result = UsagePairing.pair(listOf(e("a", 10, RESUMED), e("a", 15, RESUMED), e("a", 30, PAUSED)), until = 100)
        assertEquals(listOf(ForegroundSession("a", 10, 30)), result.sessions)
    }

    @Test
    fun openSessionDefersToNextSync() {
        val events = listOf(e("a", 10, RESUMED), e("a", 20, PAUSED), e("b", 30, RESUMED))
        val first = UsagePairing.pair(events, until = 40)
        assertEquals(listOf(ForegroundSession("a", 10, 20)), first.sessions)
        assertEquals(30, first.nextFrom)
        // 下次从 30 读起，接上 b 的离开事件
        val second = UsagePairing.pair(listOf(e("b", 30, RESUMED), e("b", 70, PAUSED)), until = 100)
        assertEquals(listOf(ForegroundSession("b", 30, 70)), second.sessions)
    }

    @Test
    fun zeroLengthSessionIsDropped() {
        val result = UsagePairing.pair(listOf(e("a", 10, RESUMED), e("a", 10, PAUSED)), until = 100)
        assertEquals(emptyList<ForegroundSession>(), result.sessions)
    }
}
