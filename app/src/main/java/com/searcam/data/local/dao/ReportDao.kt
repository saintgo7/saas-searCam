package com.searcam.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.searcam.data.local.entity.ScanReportEntity
import kotlinx.coroutines.flow.Flow

/**
 * 스캔 리포트 DAO (Data Access Object)
 *
 * Room이 이 인터페이스를 구현해 SQL을 자동 생성한다.
 * 모든 읽기 작업은 Flow로 반환하여 실시간 UI 업데이트를 지원한다.
 */
@Dao
interface ReportDao {

    /**
     * 새 리포트를 저장하거나 기존 리포트를 덮어쓴다.
     *
     * @param report 저장할 ScanReportEntity
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: ScanReportEntity)

    /**
     * 모든 리포트를 최신순으로 반환한다.
     *
     * @return 리포트 목록을 실시간으로 emit하는 Flow
     */
    @Query("SELECT * FROM scan_reports ORDER BY started_at DESC")
    fun observeAll(): Flow<List<ScanReportEntity>>

    /**
     * 특정 ID의 리포트를 반환한다.
     *
     * @param id 리포트 고유 식별자
     * @return ScanReportEntity 또는 null (없는 경우)
     */
    @Query("SELECT * FROM scan_reports WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ScanReportEntity?

    /**
     * 특정 리포트를 삭제한다.
     *
     * @param report 삭제할 ScanReportEntity
     */
    @Delete
    suspend fun delete(report: ScanReportEntity)

    /**
     * 모든 리포트를 삭제한다.
     */
    @Query("DELETE FROM scan_reports")
    suspend fun deleteAll()

    /**
     * 저장된 리포트 수를 반환한다.
     *
     * @return 리포트 총 개수
     */
    @Query("SELECT COUNT(*) FROM scan_reports")
    suspend fun count(): Int
}
