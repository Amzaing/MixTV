package com.aicode.mixtv

import android.app.Application
import com.aicode.mixtv.cache.AppDatabase
import com.aicode.mixtv.repository.TvRepository

class MixTvApp : Application() {

    // 💡 全局唯一的数据库和数据源实例，开机只初始化一次
    val database by lazy { AppDatabase.getDatabase(this) }
    val tvRepository by lazy { TvRepository(this, database.tvDao()) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: MixTvApp
            private set
    }
}