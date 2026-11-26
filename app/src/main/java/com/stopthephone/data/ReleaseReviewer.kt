package com.stopthephone.data

import android.util.Log
import com.stopthephone.domain.PastRelease
import com.stopthephone.domain.RejectedAttempt
import com.stopthephone.domain.ReleaseReview
import com.stopthephone.domain.ReviewContext
import com.stopthephone.domain.TimeWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

sealed interface ReviewResult {
    // 没开 AI 审核或没填 API Key
    data object Skipped : ReviewResult
    data class Approved(val minutes: Int, val comment: String) : ReviewResult
    data class Rejected(val comment: String) : ReviewResult
    // 断网、接口报错或回复解析不出
    data object Failed : ReviewResult
}

/**
 * 收集本次管控时段内的放行情况，交给大模型判断这次放行请求。
 */
class ReleaseReviewer(
    private val recordDao: RecordDao,
    private val settingsStore: SettingsStore,
    private val client: DeepSeekClient,
) {

    suspend fun review(
        packageName: String,
        label: String,
        window: TimeWindow,
        minutes: Int,
        reason: String,
        rejected: List<RejectedAttempt>,
    ): ReviewResult {
        val apiKey = settingsStore.deepSeekApiKey.first()
        if (!settingsStore.aiReviewEnabled.first() || apiKey.isBlank()) {
            return ReviewResult.Skipped
        }
        val criteria = settingsStore.releaseCriteria.first()
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now()
        // 时段开始时刻和 24 小时前取较晚的
        val from = maxOf(window.start, now.minusHours(HISTORY_HOURS)).atZone(zone).toInstant().toEpochMilli()
        val releases = recordDao.releasesOfSince(packageName, from).map {
            PastRelease(LocalDateTime.ofInstant(Instant.ofEpochMilli(it.createdAt), zone), it.durationMinutes, it.reason)
        }
        val context = ReviewContext(
            now = now,
            appLabel = label,
            windowEnd = window.end,
            requestedMinutes = minutes,
            releases = releases,
            gaveUpCount = recordDao.countGaveUpOfSince(packageName, from),
            rejected = rejected,
            reason = reason,
        )
        return try {
            val content = client.complete(apiKey, ReleaseReview.systemPrompt(criteria), ReleaseReview.userPrompt(context))
            val verdict = ReleaseReview.parseVerdict(content, minutes)
            when {
                verdict == null -> {
                    Log.e(TAG, "unparseable verdict: $content")
                    ReviewResult.Failed
                }
                verdict.approve -> ReviewResult.Approved(verdict.minutes, verdict.comment)
                else -> ReviewResult.Rejected(verdict.comment)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "review failed", e)
            ReviewResult.Failed
        }
    }

    companion object {
        private const val TAG = "StpReview"
        private const val HISTORY_HOURS = 24L
    }
}
