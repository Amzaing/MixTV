package com.aicode.mixtv.network

import android.net.wifi.WifiManager
import android.content.Context

object MulticastHelper {
    private var multicastLock: WifiManager.MulticastLock? = null

    /**
     * 🟢 在播放器初始化或 Activity.onCreate 中调用
     */
    fun acquireMulticastLock(context: Context) {
        if (multicastLock == null) {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            // 创建一个组播锁，Tag 保持和我们项目对齐
            multicastLock = wifiManager.createMulticastLock("MixTV:RtpLock").apply {
                setReferenceCounted(false)
                acquire() // 🎯 强行解锁 Android 底层的 UDP 组播拦截
            }
            android.util.Log.i("MixTV_Network", "🔓 [组播硬件锁] Android 底层 UDP 组播网闸已成功强制开启！")
        }
    }

    /**
     * 🔴 在 App 退出时释放，还盒子一个清静
     */
    fun releaseMulticastLock() {
        multicastLock?.let {
            if (it.isHeld) it.release()
        }
        multicastLock = null
    }
}