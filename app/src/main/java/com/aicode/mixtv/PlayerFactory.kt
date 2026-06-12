package com.aicode.mixtv

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer


object PlayerFactory {

    @OptIn(UnstableApi::class)
    fun createDecoderPriorityPlayer(context: Context): ExoPlayer {
        // 1. 创建默认的渲染器工厂
        val renderersFactory = DefaultRenderersFactory(context.applicationContext).apply {
            // 2. 🔥 核心设置：设置扩展解码器（如FFmpeg）的优先级
            // EXTENSION_RENDERER_MODE_PREFER: 优先使用扩展软件解码器
            // 这样一旦遇到复杂的音频流，FFmpeg 会直接接管，确保 100% 吐出声音
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        }

        // 3. 构造带有高级软解内核的 ExoPlayer 实例
        return ExoPlayer.Builder(context.applicationContext, renderersFactory)
            .build()
    }
}

/*object PlayerFactory {
    @OptIn(UnstableApi::class)
    fun createDecoderPriorityPlayer(context: Context): ExoPlayer {
        val appContext = context.applicationContext

        // 1. 创建默认的渲染器工厂（硬核音频解码优先级设置）
        val renderersFactory = DefaultRenderersFactory(appContext).apply {
            // EXTENSION_RENDERER_MODE_PREFER: 优先使用扩展软件解码器（FFmpeg接管复杂音频流）
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        }

        // 2. 🛡️【核心注入】：打造高可用 HTTP 主管道，并允许自动分流到 UDP/RTP、RTSP 子管道
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(5000)
            .setReadTimeoutMs(5000)
            .setAllowCrossProtocolRedirects(true) // 允许跨协议重定向

        // 绑定到协议调度中心（DefaultDataSource 内部自带对 rtp://, udp://, rtsp:// 的智能路由）
        val baseDataSourceFactory = DefaultDataSource.Factory(appContext, httpDataSourceFactory)

        // 包装成 MediaSource 专属工厂
        val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
            .setDataSourceFactory(baseDataSourceFactory)

        // 3. 构造同时具备【高级软解内核】和【全协议路由管道】的 ExoPlayer 实例
        return ExoPlayer.Builder(appContext, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory) // 🎯 在这里完成无缝合体！
            .build()
    }
}*/

/*object PlayerFactory {

    @OptIn(UnstableApi::class)
    fun createDecoderPriorityPlayer(context: Context): ExoPlayer {
        val appContext = context.applicationContext

        // 🎯 启动我们在第一关写好的 Android 底层组播网络锁
        MulticastHelper.acquireMulticastLock(appContext)

        // 1. FFmpeg 音频解码优先级设置
        val renderersFactory = DefaultRenderersFactory(appContext).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        }

        // 2. 打造智能全协议路由管道
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(3000)
            .setReadTimeoutMs(3000)
            .setAllowCrossProtocolRedirects(true)

        val baseDataSourceFactory = DefaultDataSource.Factory(appContext, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
            .setDataSourceFactory(baseDataSourceFactory)

        // 3. 🛠️【针对 RTP 组播的硬核调优】：重写加载控制器
        // 组播流不需要普通网络视频那种动辄 50 秒的预加载，我们把缓存死死卡在 1.5秒 - 3秒 之间
        // 这样既能完美过滤内网丢包带来的花屏，又能实现换台时毫秒级的响应速度！
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1500,  // minBufferMs: 最小缓冲 1.5 秒就开始播
                3000,  // maxBufferMs: 最大缓冲 3 秒，防止占满盒子内存
                500,   // bufferForPlaybackMs: 换台后有 0.5 秒的数据就立刻开画
                1000   // bufferForPlaybackAfterRebufferMs: 卡顿后缓冲 1 秒立马恢复
            )
            .build()

        // 4. 合体封箱
        return ExoPlayer.Builder(appContext, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl) // 🎯 注入抗抖动缓冲区
            .build()
    }
}*/





