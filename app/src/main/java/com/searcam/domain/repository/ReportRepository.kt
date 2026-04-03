package com.searcam.domain.repository

import com.searcam.domain.model.ScanReport
import kotlinx.coroutines.flow.Flow

/**
 * 스캔 리포트 저장소 인터페이스
 *
 * Room DB에 스캔 결과를 저장하고 조회한다.
 * 무료 사용자는 최대 10건만 저장되며, 프리미엄은 무제한이다.
 * 구현체는 data 레이어의 ReportRepositoryImpl에 위치한다.
 */
interface ReportRepository {

    /**
     * 스캔 리포트를 저장한다.
     *
     * 무료 사용자는 10건 초과 시 가장 오래된 리포트를 자동 삭제한다.
     * 저장 시 devices, findings, layerResults 모두 직렬화하여 저장한다.
     *
     * @param report 저장할 스캔 리포트
     * @return Result.success(Unit) 또는 Result.failure(exception)
     */
    suspend fun saveReport(report: ScanReport): Result<Unit>

    /**
     * ID로 특정 리포트를 조회한다.
     *
     * @param id 리포트 UUID
     * @return Result.success(report) 또는 Result.failure(NotFoundException 등)
     */
    suspend fun getReport(id: String): Result<ScanReport>

    /**
     * 저장된 리포트 목록 전체를 실시간으로 관찰하는 Flow를 반환한다.
     *
     * 리포트 추가/삭제 시 자동으로 업데이트된 목록을 emit한다.
     * 정렬 순서: 최신순(completedAt DESC)
     *
     * @return ScanReport 목록을 실시간으로 emit하는 Flow
     */
    fun observeReports(): Flow<List<ScanReport>>

    /**
     * 특정 리포트를 삭제한다.
     *
     * 관련 기기 정보, 포인트 데이터 등 연관 데이터도 CASCADE로 함께 삭제된다.
     *
     * @param id 삭제할 리포트 UUID
     * @return Result.success(Unit) 또는 Result.failure(exception)
     */
    suspend fun deleteReport(id: String): Result<Unit>

    /**
     * 리포트를 PDF 파일로 내보낸다.
     *
     * 프리미엄 사용자 전용 기능이다. 권한 확인은 UseCase 레이어에서 담당한다.
     *
     * @param report 내보낼 스캔 리포트
     * @param outputPath 저장할 파일 경로 (절대 경로)
     * @return Result.success(savedFilePath) 또는 Result.failure(exception)
     */
    suspend fun exportToPdf(report: ScanReport, outputPath: String): Result<String>
}
