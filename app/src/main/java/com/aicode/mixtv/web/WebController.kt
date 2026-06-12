package com.aicode.mixtv.web

import android.content.Context
import com.aicode.mixtv.repository.TvRepository // 💡 记得引入你的 Repository
import com.aicode.mixtv.util.FileUtil
import java.io.File

/**
 * 💡 完美的工业级 WebController
 * 扩展注入 TvRepository，让网页端可以直接驱动持久层执行“即时清洗聚合”
 */
class WebController(
    private val context: Context,
    private val repository: TvRepository // 🔥【核心改动】：在这里直接注入单例 repository
) {

    // 负责渲染主网页
    fun handleIndex(): String {
        // 防御：如果文件不存在，先给个空字符串，防止冷启动崩溃
        val channelsFile = File(context.filesDir, "channels.txt")
        val urlsFile = File(context.filesDir, "urls.txt")
        val blacklistFile = File(context.filesDir, "blacklist.txt")

        val channels = if (channelsFile.exists()) channelsFile.readText() else ""
        val urls = if (urlsFile.exists()) urlsFile.readText() else ""
        val blacklist = if (blacklistFile.exists()) blacklistFile.readText() else ""

        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="utf-8"><title>MixTV 配置管理</title></head>
            <body style="background:#121212; color:#fff; padding:20px; font-family: sans-serif;">
                <h2>📺 MixTV 局域网在线配置后台</h2>
                <p style="color:#aaa;">修改后点击保存，电视机端将无感零延迟即时刷新，无需重启！</p>
                <form action="/save" method="post">
                    <h3>1. channels.txt</h3><textarea name="channels" style="width:100%;height:150px;background:#222;color:#0ff;padding:10px;border:1px solid #333;">$channels</textarea>
                    <h3>2. urls.txt</h3><textarea name="urls" style="width:100%;height:150px;background:#222;color:#0ff;padding:10px;border:1px solid #333;">$urls</textarea>
                    <h3>3. blacklist.txt (支持输入特定 IP:端口 或 域名特征进行封杀)</h3><textarea name="blacklist" style="width:100%;height:150px;background:#222;color:#0ff;padding:10px;border:1px solid #333;">$blacklist</textarea>
                    <br><br><input type="submit" value="💾 保存配置并即时生效" style="background:#0ff;color:#000;padding:12px 30px;border:none;font-weight:bold;cursor:pointer;font-size:16px;border-radius:4px;">
                </form>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * 负责接收保存
     */
    fun handleSave(channels: String, urls: String, blacklist: String): String {
        // 1. 稳稳地将网页内容覆写覆盖到本地沙盒文件
        FileUtil.saveEditableFile(context, "channels.txt", channels)
        FileUtil.saveEditableFile(context, "urls.txt", urls)
        FileUtil.saveEditableFile(context, "blacklist.txt", blacklist)

        android.util.Log.i("MixTV_Web", "💾 网页端文本已成功覆写写入本地磁盘沙盒！")

        // 💡 顺便把网页上的文案改掉，提示用户不需要重启了！
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="utf-8"><title>保存成功</title></head>
            <body style="background:#121212; color:#fff; text-align:center; padding-top:100px; font-family: sans-serif;">
                <h1 style="color:#0ff;">🟢 💾 配置保存成功！</h1>
                <p style="color:#ccc; font-size:18px;">后台清洗程序已启动，新台单和黑名单机制已【即时同步】到电视屏幕！</p>
                <br>
                <a href="/" style="color:#0ff; text-decoration:none; border:1px solid #0ff; padding:10px 20px; border-radius:4px;">返回主页</a>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * 🔥【核心破局大招】：供 TvWebServer 调用的冲刷手术刀
     * 网页保存后，立刻强行让 Repository 去读刚刚写进沙盒的文件，重新跑一遍清洗聚合（包括你的黑名单过滤和电台过滤！）
     */
    suspend fun getLatestSanitizedChannels(): List<com.aicode.mixtv.model.Channel> {
        return try {
            android.util.Log.w("MixTV_Web", "🔄 [核心冲刷] 正在驱动 Repository 重新透支磁盘文本进行全量数据清洗...")
            // 直接调用数据层的核心聚合方法，让它把最新写的 urls.txt 和 blacklist.txt 洗成纯净的内存对象
            repository.fetchAndAggregate(context)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}