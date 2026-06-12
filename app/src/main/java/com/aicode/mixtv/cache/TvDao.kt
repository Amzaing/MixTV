package com.aicode.mixtv.cache

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class ChannelWithSources(
    @Embedded
    val channel: ChannelEntity,
    @Relation(
        parentColumn = "name",
        entityColumn = "channelName"
    )
    val sources: List<SourceEntity>
)

@Dao
interface TvDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    // 💡 保持你的优秀设计：如果 URL 已经存在，坚决不覆盖，完美锁死用户的历史评分
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSourcesIgnore(sources: List<SourceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSources(sources: List<SourceEntity>)

    /**
     * 🔥【核心破局】：使用 REPLACE 写入。
     * 如果 URL 已经存在，它会覆盖掉旧的，并把 syncTime 更新为最新的时间戳！
     * 同时也保证了本地的打分（successCount 等）在业务层组装好或者保留。
     * （为了完全保险，这里直接使用 REPLACE 确保更新 syncTime）
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSourcesReplace(sources: List<SourceEntity>)

    @Query("SELECT * FROM tb_sources WHERE isBlacklisted = 0")
    suspend fun getAllValidSources(): List<SourceEntity>

    @Query("SELECT * FROM tb_channels")
    suspend fun getAllChannels(): List<ChannelEntity>

    @Transaction
    @Query("SELECT * FROM tb_channels")
    fun getFullChannelsFlow(): Flow<List<ChannelWithSources>>

    @Query("SELECT * FROM tb_sources WHERE channelName = :channelName")
    suspend fun getSourcesForChannel(channelName: String): List<SourceEntity>

    @Query("""
        UPDATE tb_sources 
        SET successCount = :success, failureCount = :failure, lastUsedTime = :lastUsed, isBlacklisted = :black 
        WHERE url = :url
    """)
    suspend fun updateSourceMetrics(url: String, success: Int, failure: Int, lastUsed: Long, black: Boolean)

    @Query("SELECT value FROM tb_config WHERE `key` = :key LIMIT 1")
    suspend fun getConfig(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: ConfigEntity)

    /**
     * 🔥 清洗卡关一：删除没在台单里的频道
     */
    @Query("DELETE FROM tb_channels WHERE name NOT IN (:names)")
    suspend fun deleteChannelsNotIn(names: List<String>)

    /**
      🔥 清洗卡关二：删除僵尸频道遗留下来的源
     */
    @Query("DELETE FROM tb_sources WHERE channelName NOT IN (:names)")
    suspend fun deleteSourcesNotIn(names: List<String>)


    /**
     * 🔥 清洗 3【降维打击】：凡是同步时间戳对不上的，说明在最新的 M3U / 网页源里已经彻底消失了，直接一网打尽！
     * 🎯 没有任何 List 参数传递，变量数只有 1 个，SQLite 稳如泰山！
     */
    @Query("DELETE FROM tb_sources WHERE syncTime != :currentSyncTime")
    suspend fun clearZombieSources(currentSyncTime: Long)


    /**
     * 🔥【全新无错替换】：不传全量 URL，而是精细化单条或分批清除无用源
     * 专门用来配合 Repository 里的分批 Chunk 算法
     */
    @Query("DELETE FROM tb_sources WHERE channelName = :channelName AND url NOT IN (:validUrls)")
    suspend fun deleteOldSourcesInChannel(channelName: String, validUrls: List<String>)

    /**
     * 🔥【全新加入：精细化手术刀】：
     * 解决“频道名字没变，但内部移除了某几个 URL，导致死链残留”的 Bug。
     * 凡是 URL 不在最新配置总集里的备用源，通通物理湮灭！
     */
/*    @Query("DELETE FROM tb_sources WHERE url NOT IN (:urls)")
    suspend fun deleteSourcesByUrlsNotIn(urls: List<String>)*/

    @Query("DELETE FROM tb_channels")
    suspend fun clearChannels()

    @Query("DELETE FROM tb_sources")
    suspend fun clearSources()
}