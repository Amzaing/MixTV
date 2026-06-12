package com.aicode.mixtv.model

/**
 * 标准频道（由用户 channels.txt 定义）
 */
data class Channel(
    val name: String,
    var score: Int = 100, // 频道的综合评分，默认100
    //val sources: MutableList<LiveSource> = mutableListOf()
    val sources: List<LiveSource>
)

/**
 * 具体的直播源（从外部 urls.txt 解析并模糊匹配出来的）
 */
data class LiveSource(
    val channelName: String, // 匹配到的标准频道名
    val originalName: String, // 原始源中的名称
    val url: String,
    var successCount: Int = 0,
    var failureCount: Int = 0,
    var lastUsedTime: Long = 0L,
    var isBlacklisted: Boolean = false
) {
    // 智能选源评分机制：成功率高、失败少、最近刚成功过的分值高
    val calculatedScore: Int
        get() {
            if (isBlacklisted) return -1
            val total = successCount + failureCount
            val successRate = if (total == 0) 1.0 else successCount.toDouble() / total
            val penalty = failureCount * 10 // 失败一次扣10分
            val recencyBonus = if (System.currentTimeMillis() - lastUsedTime < 300000) 20 else 0 // 5分钟内用过加20分
            return ((successRate * 100).toInt() - penalty + recencyBonus).coerceAtLeast(0)
        }
}