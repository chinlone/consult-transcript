package com.xiaodong.consulttranscript.core.audio

import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom

/**
 * 安全删除：先覆写、再删文件。
 *
 * 为什么不用 File.delete() 就完事？
 *   咨询录音是高度敏感数据，"默认不保留"是产品承诺。
 *   覆写一遍能避免文件被某些恢复工具从闪存残余里捞回来。
 *   注意：现代手机闪存有磨损均衡，覆写不能保证 100% 物理擦除，
 *   所以更根本的一环是——它从来只写在 App 私有目录、从不落外部存储、从不联网。
 */
object SecureDelete {
    private val random = SecureRandom()

    fun overwriteAndDelete(file: File): Boolean {
        if (!file.exists()) return true
        return try {
            val length = file.length()
            RandomAccessFile(file, "rws").use { raf ->
                raf.seek(0)
                val buf = ByteArray(64 * 1024)
                var written = 0L
                while (written < length) {
                    random.nextBytes(buf)
                    val n = minOf(buf.size.toLong(), length - written).toInt()
                    raf.write(buf, 0, n)
                    written += n
                }
                raf.fd.sync()
            }
            file.delete()
        } catch (e: Exception) {
            // 覆写失败也要尽力删掉，不能留着
            file.delete()
        }
    }
}
