package com.searcam.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.searcam.data.local.entity.ChecklistEntity
import kotlinx.coroutines.flow.Flow

/**
 * 체크리스트 DAO
 */
@Dao
interface ChecklistDao {

    /**
     * 체크리스트 수행 기록을 저장한다.
     *
     * @param checklist 저장할 ChecklistEntity
     * @return 자동 생성된 기본키(PK)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(checklist: ChecklistEntity): Long

    /**
     * 모든 체크리스트 기록을 최신순으로 반환한다.
     *
     * @return 체크리스트 목록을 실시간으로 emit하는 Flow
     */
    @Query("SELECT * FROM checklists ORDER BY performed_at DESC")
    fun observeAll(): Flow<List<ChecklistEntity>>

    /**
     * 특정 ID의 체크리스트를 반환한다.
     *
     * @param id 조회할 체크리스트 ID
     * @return ChecklistEntity 또는 null
     */
    @Query("SELECT * FROM checklists WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ChecklistEntity?

    /**
     * 특정 체크리스트 기록을 삭제한다.
     *
     * @param id 삭제할 체크리스트 ID
     */
    @Query("DELETE FROM checklists WHERE id = :id")
    suspend fun deleteById(id: Long)
}
