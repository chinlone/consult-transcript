package com.xiaodong.consulttranscript.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CaseDao {
    @Query("SELECT * FROM cases ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CaseEntity>>

    @Query("SELECT * FROM cases WHERE id = :id")
    suspend fun get(id: String): CaseEntity?

    @Query("SELECT * FROM cases WHERE id = :id")
    fun observe(id: String): Flow<CaseEntity?>

    @Upsert suspend fun upsert(entity: CaseEntity)

    @Query("DELETE FROM cases WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM cases")
    suspend fun deleteAll()

    @Query("SELECT id FROM cases WHERE status = 'DONE'")
    suspend fun finishedIds(): List<String>
}

@Dao
interface SegmentDao {
    @Query("SELECT * FROM segments WHERE caseId = :caseId ORDER BY startMs ASC")
    fun observe(caseId: String): Flow<List<SegmentEntity>>

    @Query("SELECT * FROM segments WHERE caseId = :caseId ORDER BY startMs ASC")
    suspend fun once(caseId: String): List<SegmentEntity>

    @Upsert suspend fun upsert(segment: SegmentEntity)

    @Upsert suspend fun upsertAll(segments: List<SegmentEntity>)

    @Query("DELETE FROM segments WHERE caseId = :caseId")
    suspend fun deleteByCase(caseId: String)

    @Query("DELETE FROM segments")
    suspend fun deleteAll()
}
