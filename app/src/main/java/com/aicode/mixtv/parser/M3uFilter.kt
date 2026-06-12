package com.aicode.mixtv.parser

import android.util.Log
import com.aicode.mixtv.model.LiveSource

/**
 * 🛡️ MixTV 工业级黑名单清洗引擎（全规则批量懒人完全体）
 */
object M3uFilter {

    private const val TAG = "MixTV_Filter"

    fun filterTrashSources(preFilteredSources: List<LiveSource>, blacklist: List<String>): List<LiveSource> {
        if (preFilteredSources.isEmpty()) return emptyList()
        val distinctSources = preFilteredSources.distinctBy { it.url }
        if (blacklist.isEmpty()) return distinctSources

        return distinctSources.filter { src ->
            // 🔒 状态机：每一个源开始对撞时，默认是 "url" 状态（如果第一行没写标签，默认当普通 URL 模糊匹配）
            var currentLabel = "url"

            val isBad = blacklist.any { rawLine ->
                val line = rawLine.trim()

                // 过滤空行和注释
                if (line.isBlank() || line.startsWith("#") || line.startsWith("//")) return@any false

                val lowerUrl = src.url.lowercase()
                val lowerName = src.channelName.lowercase()

                // 1. 🔍 核心升级：三路状态机解析器
                val (label, content) = when {
                    line.startsWith("name:", ignoreCase = true) -> { currentLabel = "name"; "name" to line.substringAfter(":") }
                    line.startsWith("ext:", ignoreCase = true)  -> { currentLabel = "ext"; "ext" to line.substringAfter(":") }
                    line.startsWith("ip:", ignoreCase = true)   -> { currentLabel = "ip"; "ip" to line.substringAfter(":") }

                    // 🚀【多行继承神技】：如果这一行开头没有这三个标签，直接闭眼继承上一行的标签（不管是 name、ext 还是 ip）！
                    !line.contains("name:") && !line.contains("ext:") && !line.contains("ip:") -> {
                        currentLabel to line
                    }
                    else -> {
                        currentLabel = "url"
                        "url" to line
                    }
                }

                // 2. 🎯 多维漏斗批量精准打击
                when (label) {
                    "name" -> {
                        content.split(Regex("[,|，]")).map { it.trim().lowercase() }.filter { it.isNotBlank() }
                            .any { lowerName.contains(it) }
                    }
                    "ext" -> {
                        val exts = content.split(Regex("[,|，]")).map { it.trim().lowercase() }.filter { it.isNotBlank() }
                        val urlWithoutParams = lowerUrl.substringBefore("?")
                        exts.any { urlWithoutParams.endsWith(it) }
                    }
                    "ip" -> {
                        // 🎯【新增坏 IP 批量过滤器】：同样支持逗号、竖线切分，支持多行换行续杯！
                        val badIps = content.split(Regex("[,|，]")).map { it.trim().lowercase() }.filter { it.isNotBlank() }
                        badIps.any { lowerUrl.contains(it) }
                    }
                    else -> {
                        // 兜底的普通单行匹配
                        val keyword = content.lowercase()
                        if (keyword.length < 4 && !keyword.contains(".")) return@any false
                        lowerUrl.contains(keyword)
                    }
                }
            }

            if (isBad) {
                Log.e(TAG, "🛡️ [黑名单击杀] [${src.channelName}] -> ${src.url}")
            }
            !isBad
        }
    }
}