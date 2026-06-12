package com.aicode.mixtv.repository

import android.content.Context
import android.util.Log
import com.aicode.mixtv.cache.ChannelEntity
import com.aicode.mixtv.cache.ConfigEntity
import com.aicode.mixtv.cache.SourceEntity
import com.aicode.mixtv.cache.TvDao
import com.aicode.mixtv.event.AppEventBus
import com.aicode.mixtv.model.Channel
import com.aicode.mixtv.model.LiveSource
import com.aicode.mixtv.network.NetworkModule
import com.aicode.mixtv.parser.ChannelMatcher
import com.aicode.mixtv.parser.M3uFilter
import com.aicode.mixtv.parser.SourceParser
import com.aicode.mixtv.util.FileUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest

class TvRepository(context: Context, private val tvDao: TvDao) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // 💡【对齐修复】：保持你的不可变业务模型流不变
    val allChannelsFlow: Flow<List<Channel>> = tvDao.getFullChannelsFlow().map { listWithSources ->
        listWithSources.map { item ->
            Channel(
                name = item.channel.name,
                score = item.channel.score,
                sources = item.sources.map { sourceEntity ->
                    LiveSource(
                        channelName = sourceEntity.channelName,
                        originalName = sourceEntity.originalName,
                        url = sourceEntity.url,
                        successCount = sourceEntity.successCount,
                        failureCount = sourceEntity.failureCount,
                        lastUsedTime = sourceEntity.lastUsedTime,
                        isBlacklisted = sourceEntity.isBlacklisted
                    )
                }
            )
        }
    }

    init {
        // 使用 Dispatchers.IO 开启常驻后台事件监听
        CoroutineScope(Dispatchers.IO).launch {
            val initTime = System.currentTimeMillis()

            // 🎯【核心修复二】：collectLatest 本身就是一个挂起接收器
            AppEventBus.refreshEvent.collectLatest { freshWebChannels ->
                // 🛡️ 边缘防御：防止旧粘性事件倒灌
                if (System.currentTimeMillis() - initTime < 500) {
                    Log.w(
                        "MixTV_Repo",
                        "⛔ [边缘防御] 拦截了冷启动或重建时倒灌的旧刷新信号，不予执行。"
                    )
                    return@collectLatest
                }

                Log.w(
                    "MixTV_Repo",
                    "🚀 [大礼包同步机制启动] 真正收到网页端的实时强推数据，开始秒级洗盘..."
                )
                try {
                    // 🎯【核心修复三】：因为 collectLatest 内部的 Lambda 块没有隐式传递 suspend 上下文
                    // 咱们直接显式指定在当前 IO 协程环境中同步挂起执行，完美消灭编译器报错！
                    withContext(Dispatchers.IO) {
                        if (freshWebChannels.isNotEmpty()) {
                            saveToCache(freshWebChannels)
                        } else {
                            // 保险措施：如果大礼包没带肉（老接口兼容），则强行去读磁盘文件清洗
                            fetchAndAggregate(context)
                        }
                    }
                    Log.d("MixTV_Repo", "✨ [大礼包清洗完毕] 数据库已完全对齐最新网页配置！")
                } catch (e: Exception) {
                    Log.e("MixTV_Repo", "❌ 大礼包洗盘发生异常: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun getLastWatchedChannel(): String? = tvDao.getConfig("last_watched")
    suspend fun saveLastWatchedChannel(channelName: String) =
        tvDao.saveConfig(ConfigEntity("last_watched", channelName))

    suspend fun loadFromCache(): List<Channel> {
        val channelEntities = tvDao.getAllChannels()
        return channelEntities.map { chEntity ->
            val srcEntities = tvDao.getSourcesForChannel(chEntity.name)
            Channel(
                name = chEntity.name,
                score = chEntity.score,
                sources = srcEntities.map {
                    LiveSource(
                        channelName = it.channelName,
                        originalName = it.originalName,
                        url = it.url,
                        successCount = it.successCount,
                        failureCount = it.failureCount,
                        lastUsedTime = it.lastUsedTime,
                        isBlacklisted = it.isBlacklisted
                    )
                }
            )
        }
    }

    suspend fun shouldFetchFromNetwork(): Boolean {
        val lastFetchStr = tvDao.getConfig("last_fetch_time") ?: return true
        val lastFetchTime = lastFetchStr.toLongOrNull() ?: return true
        val cacheCount = tvDao.getAllChannels().size
        if (cacheCount == 0) return true

        val twelveHours = 12 * 60 * 60 * 1000L
        return (System.currentTimeMillis() - lastFetchTime) > twelveHours
    }

    /**
     * 🔥【核心大清洗】：修复了原本不处理 URL 下载的大漏洞
     */
    suspend fun fetchAndAggregate(context: Context): List<Channel> = withContext(Dispatchers.IO) {
        Log.d("MixTV_Repo", "🚀 开始全链路数据核算与大清洗...")

        val standardChannels = FileUtil.getEditableFileLines(context, "channels.txt")
        val blacklist = FileUtil.getEditableFileLines(context, "blacklist.txt")
        val urlSubscriptions = FileUtil.getEditableFileLines(context, "urls.txt")

        val rawSources = mutableListOf<LiveSource>()

        // 🛠️【核心修正】：遍历 URL，先用 OkHttp 拿下真正的文本内容，再交付解析
        urlSubscriptions.forEach { urlLine ->
            val url = urlLine.trim()
            if (url.isEmpty() || url.startsWith("#")) return@forEach

            // 🌐 通过 OkHttp 发起同步网络抓取
            val m3uContent = try {
                val request = Request.Builder().url(url).build()
                NetworkModule.downloadClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() ?: "" else ""
                }
            } catch (e: Exception) {
                Log.e("MixTV_Repo", "❌ 订阅链接网络下载失败 [URL: $url], 异常: ${e.message}")
                ""
            }

            // 🎯 只有拿到真实大文本内容后，才喂给你的原版 SourceParser
            if (m3uContent.isNotBlank()) {
                val parsed = SourceParser.parse(m3uContent)
                Log.d("MixTV_Repo", "📥 链接 [$url] 成功解析出原始源 ${parsed.size} 条")
                rawSources.addAll(parsed)
            }
        }

        // =====================================================================
        // 🚀【老哥指定核心破局 1】：第一步，先挑出我们要的频道（战略初筛）
        // =====================================================================
        val targetNames = standardChannels.map { it.lowercase() }.toSet()
        val sanitizedSources = rawSources.map { source ->
            val matchedStandardName =
                ChannelMatcher.findStandardName(source.channelName, standardChannels)
            if (matchedStandardName != null) {
                source.copy(channelName = matchedStandardName) // 纠偏成功，打上正规军标签
            } else {
                source // 找不到就保留原样
            }
        }

        val preFilteredSources = sanitizedSources.filter { src ->
            // 只要野生源的名字，不包含在我们标准的台单模板里，直接原地枪毙，不配进入清洗管线
            targetNames.any { src.channelName.lowercase().contains(it) }
        }


        // 🎯 降维打击：直接调用解析器包里的清洗引擎，Repository 瞬间瘦身！
        val distinctSources = M3uFilter.filterTrashSources(preFilteredSources, blacklist)


        // 5. 归类收拢
        val finalChannels = ChannelMatcher.match(standardChannels, distinctSources)

        Log.d(
            "MixTV_Repo",
            "🧼 原始源总数: ${rawSources.size} -> 按需取台后的源: ${preFilteredSources.size} -> 去重后纯净源: ${distinctSources.size}"
        )

        // 🛡️【自愈铁闸】：如果是第一次开机或断网，防止把本地数据清空导致台单变 0
        if (finalChannels.all { it.sources.isEmpty() }) {
            Log.w(
                "MixTV_Repo",
                "🛡️ [熔断保护] 洗盘后有效源为 0（极可能是网络未连通），拒绝覆盖老账本。"
            )
            return@withContext loadFromCache()
        }

        // 6. 落盘
        saveToCache(finalChannels)

        return@withContext finalChannels
    }

    /**
     * 💾【标记清除】
     */
    private suspend fun saveToCache(newChannels: List<Channel>) {
        val newChannelNames = newChannels.map { it.name }
        if (newChannelNames.isEmpty()) return

        val currentSyncTime = System.currentTimeMillis()

        tvDao.deleteChannelsNotIn(newChannelNames)
        tvDao.deleteSourcesNotIn(newChannelNames)
        tvDao.insertChannels(newChannels.map { ChannelEntity(it.name, it.score) })

        val localSourcesMap = tvDao.getAllValidSources().associateBy { it.url }

        val allNewSources = newChannels.flatMap { ch ->
            ch.sources.map { src ->
                val oldEntity = localSourcesMap[src.url]
                SourceEntity(
                    url = src.url,
                    channelName = src.channelName,
                    originalName = src.originalName,
                    successCount = oldEntity?.successCount ?: 0,
                    failureCount = oldEntity?.failureCount ?: 0,
                    lastUsedTime = oldEntity?.lastUsedTime ?: 0L,
                    isBlacklisted = oldEntity?.isBlacklisted ?: false,
                    syncTime = currentSyncTime
                )
            }
        }

        tvDao.insertSourcesReplace(allNewSources)
        tvDao.clearZombieSources(currentSyncTime)
        tvDao.saveConfig(ConfigEntity("last_fetch_time", currentSyncTime.toString())) // 更新同步时间锁

        Log.i("MixTV_Repo", "✅ [数据清洗与持久化完美闭环] 内存脱水率 100%，SQLite 压力为 0！")
    }

    suspend fun updateSourceStatus(source: LiveSource) {
        tvDao.updateSourceMetrics(
            source.url,
            source.successCount,
            source.failureCount,
            source.lastUsedTime,
            source.isBlacklisted
        )
        //printScoreDashboard(source.channelName)
    }

    private suspend fun printScoreDashboard(channelName: String) {
        val sources = tvDao.getSourcesForChannel(channelName)
        Log.d(
            "MixTV_Score",
            "========================================================================="
        )
        Log.d(
            "MixTV_Score",
            "📊 [MixTV 实时评分监控矩阵] 频道: $channelName (当前共缓存 ${sources.size} 个源)"
        )
        Log.d(
            "MixTV_Score",
            "-------------------------------------------------------------------------"
        )
        sources.forEachIndexed { idx, src ->
            val score = src.successCount - (src.failureCount * 2)
            val status = if (src.isBlacklisted) "❌已拉黑" else "🟢正常可用"
            Log.d(
                "MixTV_Score",
                " 排行 #${idx + 1} | 成功: ${src.successCount}次 | 失败: ${src.failureCount}次 | 动态综合得分: ${score}分 | 状态: $status | 地址: ${src.url}"
            )
        }
        Log.d(
            "MixTV_Score",
            "========================================================================="
        )
    }

    suspend fun getStaticChannels(): List<Channel> {
        return allChannelsFlow.first()
    }
}