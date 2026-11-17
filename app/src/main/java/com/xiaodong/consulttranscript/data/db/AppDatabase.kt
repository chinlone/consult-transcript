package com.xiaodong.consulttranscript.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CaseEntity::class, SegmentEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun caseDao(): CaseDao
    abstract fun segmentDao(): SegmentDao

    companion object {
        /**
         * v1 → v2：支持多人。cases 表新增两列，均给默认值，旧数据平滑升级不丢稿。
         * 不用 destructive migration（那会清空用户已录的逐字稿）。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cases ADD COLUMN speakerCount INTEGER NOT NULL DEFAULT 2")
                db.execSQL("ALTER TABLE cases ADD COLUMN speakerLabels TEXT")
            }
        }
    }
}
