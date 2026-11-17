package com.xiaodong.consulttranscript.data.db

import android.content.Context
import androidx.room.Room

/**
 * 明文数据库工厂（本版按使用者要求改为不加密）。
 *
 * 数据以明文 SQLite 落在 App 私有目录 /data/data/<包名>/databases/ 下，
 * 其他 App 读不到（系统沙箱隔离），但 root / 备份可读取——
 * 数据安全由使用者自行负责，App 不做任何加密，也不联网。
 *
 * 文件名沿用 consult.db；若旧版残留了 consult_encrypted.db（SQLCipher 格式），
 * 新代码不会再去打开它，可忽略或 uninstall 时一并清除。
 */
object PlainDbFactory {
    fun build(context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "consult.db"
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }
}
