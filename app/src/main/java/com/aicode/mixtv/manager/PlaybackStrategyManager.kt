package com.aicode.mixtv.manager

import android.util.Log
import com.aicode.mixtv.model.Channel
import com.aicode.mixtv.model.LiveSource
import com.aicode.mixtv.model.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow

class PlaybackStrategyManager {

    private val TAG = "MixTV_Strategy"

    val playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)

    var currentChannel: Channel? = null
    var sortedSources = listOf<LiveSource>()

    var currentSourceIndex = 0
        private set

    fun selectChannel(channel: Channel): LiveSource? {
        currentChannel = channel
        sortedSources = channel.sources.sortedWith(
            compareByDescending<LiveSource> { !it.isBlacklisted }
                .thenByDescending { it.calculatedScore }
                .thenByDescending { it.lastUsedTime }
        )
        currentSourceIndex = 0
        val bestSource = sortedSources.getOrNull(currentSourceIndex)

        if (bestSource != null) {
            Log.d(TAG, "🎯 策略打分路由成功 -> 频道: ${channel.name}，优先挑选分值最高源(第 1 个): ${bestSource.url}")
            playbackState.value = PlaybackState.Loading(channel.name, bestSource.url)
        }
        return bestSource
    }

    fun reportSuccess(): LiveSource? {
        val currentSource = sortedSources.getOrNull(currentSourceIndex)
        currentSource?.apply {
            successCount++
            lastUsedTime = System.currentTimeMillis()
            Log.d(TAG, "📈 [评分系统回写] 播放成功!! 该源成功次数+1, URL: $url")
        }
        currentChannel?.let {
            playbackState.value = PlaybackState.Playing(it.name, currentSource?.url ?: "")
        }
        return currentSource
    }

    fun reportFailureAndFallback(): LiveSource? {
        val failedSource = sortedSources.getOrNull(currentSourceIndex)
        failedSource?.apply {
            failureCount++
            lastUsedTime = System.currentTimeMillis()
            if (failureCount >= 3) {
                isBlacklisted = true
            }
            Log.e(TAG, "📉 [评分系统回写] 播放遭遇致命错误!! 该源失败次数+1, URL: $url")
        }

        currentSourceIndex++ // 👈 在 ViewModel 的硬锁保护下，这里每一次被调用都是神圣且安全的
        if (currentSourceIndex < sortedSources.size) {
            return sortedSources[currentSourceIndex]
        } else {
            currentChannel?.let {
                playbackState.value = PlaybackState.Error("🚨 整个频道下所有备用源已全军覆没")
            }
            return null
        }
    }

    fun getActiveSource(): LiveSource? = sortedSources.getOrNull(currentSourceIndex)
}