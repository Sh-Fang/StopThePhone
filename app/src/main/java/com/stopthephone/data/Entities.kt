package com.stopthephone.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "controlled_app")
data class ControlledApp(
    @PrimaryKey val packageName: String,
    val label: String,
    val createdAt: Long,
)

/**
 * 管控时段，endMinute 不大于 startMinute 时表示跨午夜。
 */
@Entity(tableName = "control_period", indices = [Index("packageName")])
data class ControlPeriod(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startMinute: Int,
    val endMinute: Int,
    // 第 0 位是周一，第 6 位是周日
    val daysMask: Int,
)

enum class ReleasePurpose { RELEASE, MODIFY_SETTINGS }

@Entity(tableName = "release", indices = [Index("packageName"), Index("createdAt")])
data class Release(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val reason: String,
    val purpose: ReleasePurpose,
    val createdAt: Long,
    val durationMinutes: Int?,
    val endAt: Long?,
)

enum class InterceptOutcome { RELEASED, GAVE_UP }

@Entity(tableName = "intercept_event", indices = [Index("packageName"), Index("shownAt")])
data class InterceptEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val shownAt: Long,
    val closedAt: Long?,
    val outcome: InterceptOutcome?,
    val releaseId: Long?,
)

@Entity(tableName = "usage_session", indices = [Index("packageName"), Index("startAt")])
data class UsageSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startAt: Long,
    val endAt: Long,
)
