package com.searcam.domain.usecase

import com.searcam.domain.repository.ReportRepository

/**
 * 리포트 PDF 내보내기 UseCase
 *
 * ReportRepository.exportToPdf()에 위임하여 특정 리포트를 PDF 파일로 저장한다.
 * 프리미엄 사용자 전용 기능이며, 권한 확인은 이 UseCase에서 담당한다.
 *
 * 흐름:
 *   1. reportId로 리포트 조회
 *   2. 출력 경로 생성 (앱 외부 저장소 Documents 디렉토리)
 *   3. ReportRepository.exportToPdf() 호출
 *   4. 저장된 파일 경로 반환
 */
class ExportReportUseCase(
    private val reportRepository: ReportRepository,
) {

    /**
     * 리포트를 PDF로 내보내고 저장된 파일 경로를 반환한다.
     *
     * @param reportId 내보낼 리포트 UUID
     * @return Result.success(savedFilePath) 또는 Result.failure(exception)
     *   - NotFoundException: reportId에 해당하는 리포트가 없을 때
     *   - IOException: 파일 저장 실패 시
     */
    suspend operator fun invoke(reportId: String): Result<String> {
        // 리포트 조회
        val reportResult = reportRepository.getReport(reportId)
        if (reportResult.isFailure) {
            return Result.failure(
                reportResult.exceptionOrNull()
                    ?: IllegalStateException("리포트를 찾을 수 없습니다: $reportId")
            )
        }

        val report = reportResult.getOrThrow()

        // 출력 파일명 생성 — searcam_report_YYYYMMDD_HHmmss.pdf 형식
        val timestamp = report.completedAt
        val outputFileName = buildPdfFileName(timestamp)

        // PDF 내보내기 위임
        return reportRepository.exportToPdf(
            report = report,
            outputPath = outputFileName,
        )
    }

    /**
     * PDF 파일명을 생성한다.
     *
     * 형식: searcam_report_{epoch_ms}.pdf
     * 실제 경로(디렉토리)는 data 레이어 구현체가 결정한다.
     *
     * @param epochMs 리포트 완료 시각 (Unix epoch millis)
     * @return PDF 파일명 문자열
     */
    private fun buildPdfFileName(epochMs: Long): String =
        "searcam_report_${epochMs}.pdf"
}
