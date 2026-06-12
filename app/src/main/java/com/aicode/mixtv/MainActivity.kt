package com.aicode.mixtv

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.media3.exoplayer.ExoPlayer
import com.aicode.mixtv.ui.MainTvScreen
import com.aicode.mixtv.ui.TvPlaybackViewModel
import com.aicode.mixtv.util.FileUtil
import com.aicode.mixtv.web.TvWebServer
import com.aicode.mixtv.web.WebController

class MainActivity : ComponentActivity() {

    private lateinit var tvWebServer: TvWebServer
    private var exoPlayer: ExoPlayer? = null

    // 💡【架构对齐】：利用自定义工厂，将 Application 层维护的全局单例 Repository 注入 ViewModel
    private val tvPlaybackViewModel: TvPlaybackViewModel by viewModels {
        TvPlaybackViewModel.Factory((application as MixTvApp).tvRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 🔥【核心守护】：开机检查并释放 assets 模版到沙盒可写目录，为局域网网页编辑打好地基
        FileUtil.getEditableFileLines(this, "channels.txt")
        FileUtil.getEditableFileLines(this, "urls.txt")
        FileUtil.getEditableFileLines(this, "blacklist.txt")

        // 2. 🔥【无缝生态】：实例化局域网独立 Web 服务，并将 Repository 单例注入其中
        val myController = WebController(this, (application as MixTvApp).tvRepository)
        tvWebServer = TvWebServer(myController)
        tvWebServer.start(8080)

        // 3. 开启隐藏状态栏与导航栏的全屏沉浸模式（大屏专用）
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )

        // 保持屏幕常亮（防止看电视时盒子进入睡眠或屏保）
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 4. 🔥【FFmpeg 内核加持】：通过高级工厂实例化带有全量软解优先级内核的播放器
        exoPlayer = PlayerFactory.createDecoderPriorityPlayer(this)

        // 5. 将中央控制器与物理播放器宿主进行无缝绑定
        tvPlaybackViewModel.initPlayer(exoPlayer!!)

        // 6. 触发数据层的核心控流拉取引擎（首次安装全自动无感洗盘）
        tvPlaybackViewModel.loadRealData(this)

        // 7. 渲染终极美化后的 Compose TV 专属大屏 UI
        setContent {
            MainTvScreen(viewModel = tvPlaybackViewModel)
        }
    }

    // 📺【TV 专属优化】：按照 Media3 官方最佳实践，在 onStart 恢复播放，防止系统弹音量面板时画面卡死
    override fun onStart() {
        super.onStart()
        exoPlayer?.play()
    }

    // 📺【TV 专属优化】：在 onStop 拦截暂停，确保切后台、关屏时彻底断流，绝不偷跑客厅网速
    override fun onStop() {
        super.onStop()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()

        // 安全摘除 Web 服务器，释放 8080 端口，防止下次冷启动报端口占用异常
        if (::tvWebServer.isInitialized) {
            tvWebServer.stop()
        }

        // 彻底解绑并销毁 ExoPlayer，物理切断底层 C++ 层的硬解码堆内存，防范严重的硬件内存泄漏
        exoPlayer?.release()
        exoPlayer = null
    }
}