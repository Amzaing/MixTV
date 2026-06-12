package com.aicode.mixtv.parser

import android.util.Log
import com.aicode.mixtv.model.LiveSource
import java.io.BufferedReader
import java.io.StringReader

object SourceParser {

    private fun isValidLiveUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http://") ||
                lower.startsWith("https://") ||
                lower.startsWith("rtsp://")
    }

    /**
     * 📺 MixTV V1.1.0 跨运营商标准公网解析引擎
     */
    fun parse(content: String): List<LiveSource> {
        val sources = mutableListOf<LiveSource>()
        if (content.isBlank()) return sources

        val reader = BufferedReader(StringReader(content))
        var line: String?
        var currentName = ""

        if (content.contains("#EXTM3U", ignoreCase = true)) {
            while (reader.readLine().also { line = it } != null) {
                val trimmed = line!!.trim().replace("\r", "").replace("\n", "")
                if (trimmed.isEmpty()) continue

                if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                    // 严格死守最后一个逗号右边的纯净台名
                    val namePart = trimmed.substringAfterLast(",")
                    if (namePart.isNotBlank()) {
                        currentName = namePart.trim()
                    }
                } else if (isValidLiveUrl(trimmed)) {
                    if (currentName.isNotEmpty()) {
                        sources.add(LiveSource(
                            channelName = currentName,
                            originalName = currentName,
                            url = trimmed
                        ))
                        currentName = ""
                    }
                }
            }
        } else {
            // TXT 键值对解析保持不变
            while (reader.readLine().also { line = it } != null) {
                val trimmed = line!!.trim().replace("\r", "").replace("\n", "")
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

                val separator = when {
                    trimmed.contains(",") -> ","
                    trimmed.contains("=") -> "="
                    else -> null
                }

                if (separator != null) {
                    val parts = trimmed.split(separator, limit = 2)
                    if (parts.size == 2) {
                        val name = parts[0].trim()
                        val url = parts[1].trim()
                        if (isValidLiveUrl(url)) {
                            sources.add(LiveSource(channelName = name, originalName = name, url = url))
                        }
                    }
                }
            }
        }

        Log.d("MixTV_Parser", "🏁 [V1.1.0 跨网引擎] 成功抓取全网有效源: ${sources.size} 条")
        return sources
    }
}