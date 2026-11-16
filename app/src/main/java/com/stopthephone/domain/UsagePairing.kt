package com.stopthephone.domain

enum class UsageEventKind {
    // 应用切到前台
    RESUMED,

    // 应用离开前台
    PAUSED,

    // 锁屏、息屏、关机，所有应用都离开前台
    ALL_STOPPED,
}

data class UsageEventLite(val packageName: String, val time: Long, val kind: UsageEventKind)

data class ForegroundSession(val packageName: String, val startAt: Long, val endAt: Long)

/**
 * sessions 是已结束的前台片段；nextFrom 是下次同步的起点。
 */
data class PairingResult(val sessions: List<ForegroundSession>, val nextFrom: Long)

/**
 * 把使用情况事件配对成前台片段。
 */
object UsagePairing {

    /**
     * 同一时刻只认一个前台应用；到 until 仍未结束的片段不输出，下次从最早未结束片段的开始时间重读。
     */
    fun pair(events: List<UsageEventLite>, until: Long): PairingResult {
        val open = mutableMapOf<String, Long>()
        val closed = mutableListOf<ForegroundSession>()
        val close = { packageName: String, time: Long ->
            open.remove(packageName)?.let { start ->
                if (time > start) {
                    closed.add(ForegroundSession(packageName, start, time))
                }
            }
        }
        for (event in events.sortedBy { it.time }) {
            when (event.kind) {
                UsageEventKind.RESUMED -> {
                    // 漏了离开事件的其他应用，在这里收尾
                    open.keys.filter { it != event.packageName }.forEach { close(it, event.time) }
                    open.putIfAbsent(event.packageName, event.time)
                }
                UsageEventKind.PAUSED -> close(event.packageName, event.time)
                UsageEventKind.ALL_STOPPED -> open.keys.toList().forEach { close(it, event.time) }
            }
        }
        val nextFrom = open.values.minOrNull() ?: until
        // 开始时间不早于 nextFrom 的片段下次会重新配出来，这次不输出
        return PairingResult(closed.filter { it.startAt < nextFrom }, nextFrom)
    }
}
