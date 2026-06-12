package com.aicode.mixtv.player

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.aicode.mixtv.manager.PlaybackStrategyManager

@OptIn(UnstableApi::class)
class MixPlayerListener(
    private val player: ExoPlayer,
    private val strategyManager: PlaybackStrategyManager,
    private val onDatabaseUpdateRequired: (com.aicode.mixtv.model.LiveSource) -> Unit,
    private val onSwitchToNextSource: (String) -> Unit
) : Player.Listener {

    private val TAG = "MixTV_Player"

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_READY -> {
                Log.d(TAG, "🟢 Media3 吐出画面，成功渲染第一帧。")
                val winner = strategyManager.reportSuccess()
                if (winner != null) {
                    onDatabaseUpdateRequired(winner)
                }
            }
            Player.STATE_BUFFERING -> {
                Log.d(TAG, "⏳ 正在缓冲...")
            }
            Player.STATE_ENDED -> {
                triggerFallback()
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        Log.e(TAG, "❌ 播放器异常: ${error.errorCodeName} (${error.errorCode}) -> ${error.localizedMessage}")
        triggerFallback()
    }

    private fun triggerFallback() {
        // 🔥【核心重构】：监听器只抓取当前失败的那个源，用来打分回写数据库
        val failedSource = strategyManager.getActiveSource()
        failedSource?.let { onDatabaseUpdateRequired(it) }

        // 🚀【拒绝越权】：绝对不在这里执行 reportFailureAndFallback()！
        // 直接把当前失败的 URL 原样抛回给 ViewModel，当作一个单纯的“失败警报信号”
        val currentUrl = failedSource?.url ?: ""
        Log.w(TAG, "📣 [信号发射] 播放失败，向 ViewModel 发送切源警报信号...")
        onSwitchToNextSource(currentUrl)
    }
}