package com.aicode.mixtv.cache

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "tb_channels")
data class ChannelEntity(
    @PrimaryKey val name: String,
    val score: Int = 100
)

@Entity(tableName = "tb_sources")
data class SourceEntity(
    @PrimaryKey val url: String,
    val channelName: String,
    val originalName: String,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val lastUsedTime: Long = 0L,
    val isBlacklisted: Boolean = false,
    // 🔥【核心归位】：引入同步时间戳，用来做标记清除
    val syncTime: Long = 0L
)

@Entity(tableName = "tb_config")
data class ConfigEntity(
    @PrimaryKey val key: String,
    val value: String
)