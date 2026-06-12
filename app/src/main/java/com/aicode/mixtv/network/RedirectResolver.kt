package com.aicode.mixtv.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

object RedirectResolver {

    // 🎯 优化 A：全局唯一单例客户端，强制开启连接池复用、开启 HTTP/2 支持
    private val client = NetworkModule.redirectClient

    private val AD_KEYWORDS = listOf("adv", "adsystem", "popunder", "notice", "gg.mp4")

    suspend fun resolveRealUrl(originalUrl: String): String = withContext(Dispatchers.IO) {
        if (!originalUrl.startsWith("http", ignoreCase = true)) return@withContext originalUrl

        var currentUrl = originalUrl
        var depth = 0
        val maxDepth = 4 // 🔒 4层足够应付全网所有套娃

        while (depth < maxDepth) {
            try {
                // 🎯 优化 C：终极核心优化！把 .get() 斩杀，全面换成 .head() 请求！
                // 只向服务器要 Header 报头，字节量趋近于0，盒子CPU和内存毫无波澜！
                val request = Request.Builder()
                    .url(currentUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; MiPad 6)") // 🤫 伪装成你的小米 Pad 6 环境
                    .head() // 🚀 关键：看这里！用 HEAD 代替 GET
                    .build()

                val response = client.newCall(request).execute()
                val code = response.code

                if (code in 300..308) {
                    val nextUrl = response.header("Location")
                    response.close() // 🔒 必须立刻手动释放连接放回池子！防止内存泄漏

                    if (nextUrl.isNullOrBlank()) break

                    // 广告拦截
                    if (AD_KEYWORDS.any { nextUrl.lowercase().contains(it) }) {
                        currentUrl = originalUrl
                        depth++
                        continue
                    }

                    currentUrl = if (nextUrl.startsWith("http", ignoreCase = true)) {
                        nextUrl
                    } else {
                        val base = currentUrl.substringBefore("/live")
                        "$base$nextUrl"
                    }

                    depth++
                    continue
                }

                // 200 或者 403/404 等直接放行，让播放器内核去处理具体的报错业务
                response.close()
                break
            } catch (e: Exception) {
                Log.e("MixTV_Engine", "💥 盒子底层追踪跳闸 (第 $depth 层): ${e.message}")
                break
            }
        }

        return@withContext currentUrl
    }
}