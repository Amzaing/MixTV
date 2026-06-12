package com.aicode.mixtv.parser

import com.aicode.mixtv.model.Channel
import com.aicode.mixtv.model.LiveSource
import java.util.regex.Pattern

object ChannelMatcher {

    private val numberPattern = Pattern.compile("\\d+")

    /**
     * 🎯 终极分类归拢器
     */
    fun match(standardChannels: List<String>, rawSources: List<LiveSource>): List<Channel> {

        // 1. 遍历所有进场的源，利用原始名字进行标准对齐
        val sanitizedSources = rawSources.map { source ->
            // 拿 source.channelName（已被 Parser 填入原始名）去大纲里找亲家
            val matchedStandardName = findStandardName(source.channelName, standardChannels)
            if (matchedStandardName != null) {
                source.copy(channelName = matchedStandardName) // 纠偏成功，打上正规军标签
            } else {
                source // 找不到就保留原样
            }
        }

        // 2. 此时再按纠偏后的标准台名进行内存分组
        val groupedSources: Map<String, List<LiveSource>> = sanitizedSources
            .filter { it.channelName in standardChannels } // 只有对上暗号的才留下
            .groupBy { it.channelName }

        // 3. 对照 channels.txt 的物理顺序，一次性打包封箱吐给上游
        return standardChannels.map { standardName ->
            val currentChannelSources = groupedSources[standardName] ?: emptyList()

            // 顺便在内存中把优先级排好
            val sortedSources = currentChannelSources.sortedByDescending {
                it.successCount - (it.failureCount * 2)
            }

            Channel(
                name = standardName,
                score = 0,
                sources = sortedSources
            )
        }
    }

      fun findStandardName(rawName: String, standardChannels: List<String>): String? {
        if (rawName.isBlank()) return null

        val cleanedRaw = cleanName(rawName)
        // 🎯【核心修正二】：防御闸门。如果清洗完只剩下一个字（比如“视”、“台”），说明信息严重缺失，
        // 绝不参与模糊包含比对，直接走精准匹配或放弃，防止全网乱认亲！
        if (cleanedRaw.length <= 1) {
            return standardChannels.find { it == rawName } // 降级为极其严苛的完全相等判定
        }

        val rawNumbers = extractNumbers(cleanedRaw)

        return standardChannels.find { standard ->
            val cleanedStd = cleanName(standard)
            val stdNumbers = extractNumbers(cleanedStd)

            if (rawNumbers.isNotEmpty() && stdNumbers.isNotEmpty() && rawNumbers != stdNumbers) {
                return@find false
            }

            cleanedRaw.contains(cleanedStd) || cleanedStd.contains(cleanedRaw)
        }
    }

     private fun cleanName(name: String): String {
        return name.replace(" ", "")
            .replace("-", "")
            .replace("_", "")
            .replace("高清", "")
            .replace("超清", "")
            .replace("中央", "")
            .replace("频道", "")
            // 🎯【核心修正一】：把带有噪音性质的“电视台”或后缀“台”精准干掉
            // 但如果仅仅是一个孤零零的“台”字开头（如台视），或者只有两个字（如台视、港台），绝对不切！
            .let { current ->
                if (current.endsWith("电视台")) {
                    current.replace("电视台", "")
                } else if (current.length > 2 && current.endsWith("台")) {
                    // 只有当总长度大于 2 且以台结尾时（如: 浙江台），才切掉末尾的“台”
                    current.substring(0, current.length - 1)
                } else {
                    current
                }
            }
            .uppercase()
    }

    /**
     * 🎯【全版本兼容】：从名字中提取出连续的数字列表
     * 放弃 API 34 要求的 .results()，回退到最稳固的 while 循环，完美兼容 API 26+
     */
    private fun extractNumbers(text: String): List<String> {
        val numbers = mutableListOf<String>()
        val matcher = numberPattern.matcher(text)
        while (matcher.find()) {
            numbers.add(matcher.group())
        }
        return numbers
    }
}