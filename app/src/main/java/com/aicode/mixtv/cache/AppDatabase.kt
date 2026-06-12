package com.aicode.mixtv.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ChannelEntity::class, SourceEntity::class, ConfigEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tvDao(): TvDao

    companion object {
        // 🎯【修复点】：彻底抛弃 @Transient，改用符合 Java/Kotlin DCL 规范的 @Volatile，锁死指令重排，拒绝开机并发闪退！
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mixtv_database"
                )
                    // 💡 打开破坏性迁移。这样以后我们加字段、改表结构，Room 自动洗表，不会报 Migration 闪退
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}