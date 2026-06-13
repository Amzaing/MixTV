package com.aicode.mixtv.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.aicode.mixtv.event.AppEventBus
import com.aicode.mixtv.manager.PlaybackStrategyManager
import com.aicode.mixtv.model.Channel
import com.aicode.mixtv.model.PlaybackState
import com.aicode.mixtv.network.RedirectResolver
import com.aicode.mixtv.player.MixPlayerListener
import com.aicode.mixtv.repository.TvRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap

/**
 * 📺 完美的工业级纯净版 TvPlaybackViewModel（最终对齐定稿）
 */
class TvPlaybackViewModel(private val repository: TvRepository) : ViewModel() {

    val strategyManager = PlaybackStrategyManager()

    val channels = MutableStateFlow<List<Channel>>(emptyList())
    val playbackState: StateFlow<PlaybackState> = strategyManager.playbackState
    val currentChannelIndex = MutableStateFlow(-1)

    var player: ExoPlayer? = null
    val isConfigInitialized = MutableStateFlow(false)

    private var isSwitching = false
    private var cutSourceJob: kotlinx.coroutines.Job? = null

    // 🎯 账本1：记录洗白后的直链 (原始URL -> 纯净直链)
    private val washedUrlCache = ConcurrentHashMap<String, String>()

    init {
        // 💡 1. 开机冷启动：快照独立注入，防范持久层自动震荡
        viewModelScope.launch {
            repository.allChannelsFlow.first().let { freshChannels ->
                val validChannels = freshChannels.filter { it.sources.isNotEmpty() }
                Log.i("MixTV_Observer", "🚀 [冷启动独立注入] 成功捕获静态台单，合法台数: ${validChannels.size}")

                channels.value = validChannels

                if (validChannels.isNotEmpty()) {
                    restoreLastWatched(validChannels)
                    isConfigInitialized.value = true
                } else {
                    Log.w("MixTV_Observer", "⚠️ 本地数据库空空如也，静候首次网络拉取...")
                    isConfigInitialized.value = true
                }
            }
        }

        // 💡 2. 【核心破局点】：死守网页端大喇叭，直接吞下清洗完毕的大礼包
        viewModelScope.launch {
            AppEventBus.refreshEvent.collect { freshWebChannels ->
                Log.w("MixTV_Observer", "🔔 [网页端强推] 捕获到网页端送达的黄金大礼包！开始无缝对齐 UI...")

                val validChannels = freshWebChannels.filter { it.sources.isNotEmpty() }
                val oldList = channels.value

                // 啪！直接喂给 Compose UI，列表立马在电视上刷新，完全不需要重启！
                channels.value = validChannels
                Log.i("MixTV_Observer", "✨ [即时生效] 新台单已直接同步给 UI 渲染层，合法台数: ${validChannels.size}")

                if (validChannels.isNotEmpty() && currentChannelIndex.value != -1) {
                    handleCurrentChannelDeleted(oldList, validChannels)
                }
                isConfigInitialized.value = true
            }
        }
    }

    fun initPlayer(exoPlayer: ExoPlayer) {
        this.player = exoPlayer
        exoPlayer.addListener(MixPlayerListener(exoPlayer, strategyManager, { source ->
            viewModelScope.launch(Dispatchers.IO) {
                repository.updateSourceStatus(source)
            }
        }) { _ ->
            // 🛡️【布尔闸门】
            if (isSwitching) {
                Log.w("MixTV_Gate", "⛔ [布尔锁] 拦截了底层的激进报错连击，当前切源正在严格隔离中...")
                return@MixPlayerListener
            }
            isSwitching = true
            cutSourceJob = viewModelScope.launch(Dispatchers.Main) {
                try {
                    Log.i("MixTV_Gate", "🚨 [串行控制器] 捕获到有效的切源请求，开始执行安全降级...")
                    // 1. 🛑【内核断电】
                    player?.stop()
                    player?.clearMediaItems()

                    // 2. 📝【安全记账】
                    val nextSource = strategyManager.reportFailureAndFallback()
                    if (nextSource == null) {
                        Log.e("MixTV_Gate", "🚨 备用源已全军覆没")
                        isSwitching = false
                        return@launch
                    }
                    // 3. 🔊 驱动加载状态
                    strategyManager.playbackState.value = PlaybackState.Loading(
                        strategyManager.currentChannel?.name ?: "",
                        nextSource.url
                    )
                    Log.i("MixTV_Gate", "⏳ 数字已变更，开始进行 1200 毫秒物理隔离期...")
                    // 4. ⏳【物理呼吸期】
                    kotlinx.coroutines.delay(1200)
                    isSwitching = false
                    // 5. 正式点火播放
                    playMediaUrl(nextSource.url)
                } catch (e: Exception) {
                    e.printStackTrace()
                    isSwitching = false
                }
            }
        })
    }

    fun playChannel(index: Int) {
        val list = channels.value
        if (index !in list.indices) return
        currentChannelIndex.value = index
        val targetChannel = list[index]

        // ⚔️ 物理纹丝不动绞杀
        cutSourceJob?.cancel()
        isSwitching = false

        player?.stop()
        player?.clearMediaItems()

        viewModelScope.launch(Dispatchers.IO) {
            repository.saveLastWatchedChannel(targetChannel.name)
        }

        val bestSource = strategyManager.selectChannel(targetChannel)
        if (bestSource != null) {
            cutSourceJob = viewModelScope.launch (Dispatchers.Main){
                playMediaUrl(bestSource.url)
            }

        }
    }

    fun loadRealData(context: Context) {
        viewModelScope.launch {
            if (repository.shouldFetchFromNetwork()) {
                Log.i("MixTV_ViewModel", "⏰ 距离上一次缓存已超过12小时，触发后台更新...")
                val realData = repository.fetchAndAggregate(context)
                if (realData.isNotEmpty()) {
                    channels.value = realData
                    if (currentChannelIndex.value == -1) {
                        restoreLastWatched(realData)
                    }
                }
            } else {
                Log.i("MixTV_ViewModel", "🔒 12小时控流保护生效，完全信任本地高速缓存。")
            }
        }
    }

    private suspend fun restoreLastWatched(list: List<Channel>) {
        val lastWatchedName = repository.getLastWatchedChannel()
        val index = list.indexOfFirst { it.name == lastWatchedName }
        viewModelScope.launch(Dispatchers.Main) {
            if (index != -1) {
                playChannel(index)
            } else if (list.isNotEmpty()) {
                playChannel(0)
            }
        }
    }

    fun changeChannelOffset(offset: Int) {
        val list = channels.value
        if (list.isEmpty()) return
        var nextIndex = (currentChannelIndex.value + offset) % list.size
        if (nextIndex < 0) nextIndex = list.size - 1
        playChannel(nextIndex)
    }


    /**
     * 📺 智能预检点火核心（换台、降级最终汇聚于此，顺道解决重定向）
     */
    private suspend fun playMediaUrl(url: String) {
        if (url.isBlank()) return
        try {
            var finalUrl = url
            if(washedUrlCache.containsKey(url)){
                finalUrl = washedUrlCache[url]?:url
                Log.d("MixTV_Player", "🎬 使用缓存地址 $url")
            }else{
                Log.d("MixTV_Player", "🎬 探路兵出击，正在剥离重定向: $url")
                val realWashedUrl = RedirectResolver.resolveRealUrl(url)
                if (!realWashedUrl.isNullOrBlank()){
                    finalUrl = realWashedUrl
                    washedUrlCache[url] = realWashedUrl
                    Log.i("MixTV_Player", "🟢 预检通关！最终投喂内核: $finalUrl")
                }
            }
            // 🎯【一石二鸟】：换台和切源，都会在这里顺道把 301/302 扒干净！
            // OkHttp HEAD 请求在后台跑，拿回真正能播的 200 OK 直播流
            player?.apply {
                setMediaItem(MediaItem.fromUri(finalUrl))
                prepare()
                play()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.d("MixTV_Player", "🔄 切台速度太快，上一个源的探测被优雅取消: $url")
            throw e // 必须向外抛出，维持协程树的健康
        } catch (e: Exception) {
            Log.e("MixTV_Player", "💥 智能探路跳闸，硬顶兜底: ${e.message}")
            // 🛡️ 异常兜底：网络炸了，用原 URL 强顶
            player?.apply {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                play()
            }
        }
    }

    private fun handleCurrentChannelDeleted(oldList: List<Channel>, newList: List<Channel>) {
        val currentIndex = currentChannelIndex.value
        if (currentIndex !in oldList.indices || newList.isEmpty()) {
            if (newList.isEmpty()) {
                player?.stop()
                player?.clearMediaItems()
                currentChannelIndex.value = -1
            }
            return
        }
        val playingChannelName = oldList[currentIndex].name
        val newIndex = newList.indexOfFirst { it.name == playingChannelName }

        if (newIndex != -1) {
            Log.i("MixTV_Safety", "✅ 当前播放频道 '$playingChannelName' 依然存在，位置矫正为 $newIndex")
            currentChannelIndex.value = newIndex
        } else {
            Log.w("MixTV_Safety", "⚠️ 警告：当前播放频道 '$playingChannelName' 已被删除！执行强制降级...")
            playChannel(0)
        }
    }

    class Factory(private val repository: TvRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TvPlaybackViewModel::class.java)) {
                return TvPlaybackViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}