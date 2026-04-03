package com.searcam.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.searcam.data.local.entity.DeviceEntity
import kotlinx.coroutines.flow.Flow

/**
 * 탐지된 네트워크 기기 DAO
 */
@Dao
interface DeviceDao {

    /**
     * 기기 목록을 일괄 저장한다.
     *
     * @param devices 저장할 DeviceEntity 목록
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(devices: List<DeviceEntity>)

    /**
     * 특정 리포트의 기기 목록을 반환한다.
     *
     * @param reportId 조회할 리포트 ID
     * @return 기기 목록을 실시간으로 emit하는 Flow
     */
    @Query("SELECT * FROM devices WHERE report_id = :reportId ORDER BY is_camera DESC")
    fun observeByReport(reportId: String): Flow<List<DeviceEntity>>

    /**
     * 카메라 의심 기기만 조회한다.
     *
     * @param reportId 조회할 리포트 ID
     * @return 카메라 의심 기기 목록
     */
    @Query("SELECT * FROM devices WHERE report_id = :reportId AND is_camera = 1")
    suspend fun findSuspiciousByReport(reportId: String): List<DeviceEntity>

    /**
     * 특정 리포트의 기기를 모두 삭제한다 (CASCADE로 자동 처리되지만 명시적으로도 사용 가능).
     *
     * @param reportId 삭제할 리포트 ID
     */
    @Query("DELETE FROM devices WHERE report_id = :reportId")
    suspend fun deleteByReport(reportId: String)
}
