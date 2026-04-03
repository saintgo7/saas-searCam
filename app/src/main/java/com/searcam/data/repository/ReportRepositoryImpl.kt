package com.searcam.data.repository

import com.searcam.data.local.dao.ReportDao
import com.searcam.data.local.entity.ScanReportEntity
import com.searcam.domain.model.ScanReport
import com.searcam.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

/**
 * ReportRepository 구현체
 *
 * Room DB를 통해 스캔 리포트를 CRUD 처리한다.
 *
 * Phase 1: 기본 골격 구현. Entity ↔ Domain 매핑은 Phase 2에서 완성.
 */
class ReportRepositoryImpl @Inject constructor(
    private val reportDao: ReportDao
) : ReportRepository {

    override suspend fun saveReport(report: ScanReport): Result<Unit> {
        return try {
            val entity = report.toEntity()
            reportDao.insert(entity)
            Timber.d("리포트 저장 완료: id=${report.id}, score=${report.riskScore}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "리포트 저장 실패: id=${report.id}")
            Result.failure(e)
        }
    }

    override suspend fun getReport(id: String): Result<ScanReport> {
        return try {
            val entity = reportDao.findById(id)
                ?: return Result.failure(NoSuchElementException("리포트를 찾을 수 없음: id=$id"))
            Result.success(entity.toDomain())
        } catch (e: Exception) {
            Timber.e(e, "리포트 조회 실패: id=$id")
            Result.failure(e)
        }
    }

    override fun observeReports(): Flow<List<ScanReport>> =
        reportDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun deleteReport(id: String): Result<Unit> {
        return try {
            val entity = reportDao.findById(id)
                ?: return Result.failure(NoSuchElementException("리포트를 찾을 수 없음: id=$id"))
            reportDao.delete(entity)
            Timber.d("리포트 삭제 완료: id=$id")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "리포트 삭제 실패: id=$id")
            Result.failure(e)
        }
    }

    override suspend fun exportToPdf(report: ScanReport, outputPath: String): Result<String> {
        // TODO Phase 2: PDF 생성 로직 구현
        Timber.d("PDF 내보내기 (Phase 1 스텁): outputPath=$outputPath")
        return Result.failure(NotImplementedError("PDF 내보내기는 Phase 2에서 구현 예정"))
    }

    // ── Entity ↔ Domain 매핑 ──

    /**
     * ScanReport(Domain) → ScanReportEntity(Room) 변환
     * TODO Phase 2: devices/findings JSON 직렬화 구현
     */
    private fun ScanReport.toEntity(): ScanReportEntity = ScanReportEntity(
        id = id,
        mode = mode.name,
        startedAt = startedAt,
        completedAt = completedAt,
        riskScore = riskScore,
        riskLevel = riskLevel.name,
        locationNote = locationNote
    )

    /**
     * ScanReportEntity(Room) → ScanReport(Domain) 변환
     * TODO Phase 2: JSON 역직렬화로 devices/findings 복원 구현
     */
    private fun ScanReportEntity.toDomain(): ScanReport = ScanReport(
        id = id,
        mode = com.searcam.domain.model.ScanMode.valueOf(mode),
        startedAt = startedAt,
        completedAt = completedAt,
        riskScore = riskScore,
        riskLevel = com.searcam.domain.model.RiskLevel.valueOf(riskLevel),
        devices = emptyList(),
        findings = emptyList(),
        layerResults = emptyMap(),
        correctionFactor = 1.0f,
        locationNote = locationNote,
        retroPoints = emptyList(),
        irPoints = emptyList(),
        magneticReadings = emptyList()
    )
}
