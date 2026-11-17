package com.xiaodong.consulttranscript.core.asr

import android.content.Context
import java.io.File

/** 把 assets 里的模型拷到 App 私有目录：native 层需要真实文件路径，不能直接读 assets。 */
object AssetCopier {
    fun copyAssets(context: Context, assetDir: String): File {
        val dest = File(context.filesDir, assetDir.replace('/', '_'))
        dest.mkdirs()
        val names = context.assets.list(assetDir).orEmpty()
        for (name in names) {
            val out = File(dest, name)
            if (out.exists() && out.length() > 0) continue // 已拷过，别每次启动都复制 60MB
            context.assets.open("$assetDir/$name").use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
        }
        return dest
    }
}
