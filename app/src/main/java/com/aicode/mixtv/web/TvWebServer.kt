package com.aicode.mixtv.web

import com.aicode.mixtv.event.AppEventBus
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread

class TvWebServer(private val myController: WebController) {

    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    /**
     * 🔥 开启局域网无线管理后台
     */
    fun start(port: Int = 8080) {
        if (isRunning) return
        isRunning = true

        thread(start = true, isDaemon = true, name = "MixTV-WebServer") {
            try {
                serverSocket = ServerSocket(port)
                android.util.Log.i("MixTV_Web", "🟢 零依赖无线控制台已移至专属 Web 包！端口: $port")

                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    thread {
                        handleBrowserRequest(socket)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MixTV_Web", "🔴 服务器运行异常: ${e.message}")
            }
        }
    }

    /**
     * 停止服务器（释放资源）
     */
    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun handleBrowserRequest(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val output: OutputStream = socket.getOutputStream()

            val firstLine = reader.readLine() ?: return
            val tokens = firstLine.split(" ")
            val method = tokens.getOrNull(0) ?: "GET"
            val path = tokens.getOrNull(1) ?: "/"

            var line: String?
            var contentLength = 0
            while (reader.readLine().also { line = it } != null) {
                if (line!!.isEmpty()) break
                if (line!!.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line!!.substring(15).trim().toIntOrNull() ?: 0
                }
            }

            var responseHtml = ""

            if (method == "GET" && (path == "/" || path == "")) {
                responseHtml = myController.handleIndex()
            } else if (method == "POST" && path == "/save") {
                val bodyBuffer = CharArray(contentLength)
                reader.read(bodyBuffer, 0, contentLength)
                val formData = String(bodyBuffer)

                val params = formData.split("&").associate {
                    val parts = it.split("=")
                    val key = parts.getOrNull(0) ?: ""
                    val value = if (parts.size > 1) URLDecoder.decode(parts[1], "UTF-8") else ""
                    key to value
                }

                // 1. 物理保存文件
                responseHtml = myController.handleSave(
                    channels = params["channels"] ?: "",
                    urls = params["urls"] ?: "",
                    blacklist = params["blacklist"] ?: ""
                )

                // 2. 🔥【核心修复】：借用 runBlocking 强行架起桥梁，让普通网络线程合情合法地调用 suspend 清洗函数！
                // 彻底解决 Suspend function should be called only from a coroutine 的铁律报错
                val freshCleanChannels = kotlinx.coroutines.runBlocking {
                    myController.getLatestSanitizedChannels()
                }

                // 3. 🚀 将刚出炉、洗干净的大礼包喷射给大喇叭
                AppEventBus.triggerRefresh(freshCleanChannels)

                android.util.Log.i("MixTV_Web", "⚡ 网页即时生效闭环彻底熔断完成！大礼包已安全送达。")
            } else {
                responseHtml = "<h3>404 Not Found</h3>"
            }

            val htmlBytes = responseHtml.toByteArray(Charsets.UTF_8)
            val httpResponse = ("HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: ${htmlBytes.size}\r\n" +
                    "Connection: close\r\n" +
                    "\r\n").toByteArray(Charsets.UTF_8)

            output.write(httpResponse)
            output.write(htmlBytes)
            output.flush()
            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}