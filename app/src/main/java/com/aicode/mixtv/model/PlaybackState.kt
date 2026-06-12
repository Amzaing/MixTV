package com.aicode.mixtv.model

sealed class PlaybackState {

    // 1. 空闲/初始状态
    object Idle : PlaybackState()

    // 2. 🔥【精准修复】：将 Loading 从 object 改为 data class，并赋予接收频道名和 URL 的构造参数
    data class Loading(
        val channelName: String,
        val sourceUrl: String
    ) : PlaybackState()

    // 3. 播放中状态：携带当前成功播放的频道名和 URL
    data class Playing(
        val channelName: String,
        val sourceUrl: String
    ) : PlaybackState()

    // 4. 报错状态：携带错误提示信息
    data class Error(
        val message: String
    ) : PlaybackState()
}