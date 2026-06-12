package com.aicode.mixtv.event

import kotlinx.coroutines.flow.MutableSharedFlow
import android.util.Log
import kotlinx.coroutines.flow.asSharedFlow
import com.aicode.mixtv.model.Channel

object AppEventBus {
    private const val TAG = "MixTV_EventBus"

    // 💡 确保这里是 List<Channel> 大礼包流
    private val _refreshEvent = MutableSharedFlow<List<Channel>>(replay = 0, extraBufferCapacity = 1)
    val refreshEvent = _refreshEvent.asSharedFlow()

    /**
     * 🚀 接收全量清洗好的台单并喷射出去（带参数！）
     */
    fun triggerRefresh(cleanChannels: List<Channel>) {
        Log.i(TAG, "🔔 [事件总线] 准备弹射全量新台单大礼包，台数: ${cleanChannels.size}")
        val success = _refreshEvent.tryEmit(cleanChannels)
        if (success) {
            Log.d(TAG, "✅ [事件总线] 台单大礼包已滑入缓冲区。")
        } else {
            Log.e(TAG, "❌ [事件总线] 缓冲区拥堵，大礼包丢失！")
        }
    }
}
