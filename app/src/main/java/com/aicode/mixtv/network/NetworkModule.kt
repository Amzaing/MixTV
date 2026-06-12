package com.aicode.mixtv.network

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkModule {

    /**
     * 🌍 全局唯一的网络巨兽底座（共享所有线程池和连接池）
     */
    val baseClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // 允许跨运营商和高频切台时复用 Socket，保持 8 个活跃长连接
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * 📺 专供 RedirectResolver 切台探测的客户端（轻量、硬熔断、禁自动跟随）
     */
    val redirectClient: OkHttpClient by lazy {
        baseClient.newBuilder() // 🎯 核心：基于底座衍生，共享资源！
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(1500, TimeUnit.MILLISECONDS) // 1.5秒硬熔断
            .readTimeout(1500, TimeUnit.MILLISECONDS)
            .build()
    }

    /**
     * 📂 专供 TvRepository 下载大列表/黑名单的客户端（高容错、长等待）
     */
    val downloadClient: OkHttpClient by lazy {
        baseClient.newBuilder() // 🎯 核心：基于底座衍生，共享资源！
            .followRedirects(true) // 下载大列表必须允许正常重定向
            .followSslRedirects(true)
            .connectTimeout(6000, TimeUnit.MILLISECONDS) // 给足 6 秒，防止大 M3U 下载超时
            .readTimeout(10000, TimeUnit.MILLISECONDS)   // 给足 10 秒读取时间
            .build()
    }
}