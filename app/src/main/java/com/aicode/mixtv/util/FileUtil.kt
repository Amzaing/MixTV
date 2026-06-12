package com.aicode.mixtv.util

import android.content.Context
import java.io.File

object FileUtil {

    /**
     * 🔥 智能读取：优先读沙盒可写文件（供网页在线编辑），没有再退化读 Assets
     */
    fun getEditableFileLines(context: Context, fileName: String): List<String> {
        val targetFile = File(context.filesDir, fileName)
        if (!targetFile.exists()) {
            // 第一次启动，把 assets 里的模版拷出来
            val content = readAssetFile(context, fileName)
            targetFile.writeText(content)
        }
        return targetFile.readLines().filter { it.isNotBlank() }
    }

    fun saveEditableFile(context: Context, fileName: String, content: String) {
        val targetFile = File(context.filesDir, fileName)
        targetFile.writeText(content)
    }

    fun readAssetFile(context: Context, fileName: String): String {
        return context.assets.open(fileName).bufferedReader().use { it.readText() }
    }
}