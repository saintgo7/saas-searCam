package com.searcam.data.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.searcam.domain.model.NetworkDevice
import com.searcam.domain.model.RiskLevel
import com.searcam.domain.model.ScanReport
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * ScanReport를 PDF 문서로 변환하는 생성기
 *
 * Android 내장 PdfDocument API를 사용한다 (iText 의존성 없음).
 * 총 3페이지 구조:
 *   - 페이지 1: 헤더(앱명/날짜) + 종합 위험도 점수 + 위험 등급
 *   - 페이지 2: 발견된 기기 목록 (IP, MAC, 제조사, 위험도)
 *   - 페이지 3: 체크리스트 결과 (발견 사항 목록)
 *
 * 보안 원칙:
 *   - 생성된 PDF에 카메라 프레임 원본 이미지는 포함하지 않는다
 *   - 출력 경로는 앱 외부 저장소 접근을 허용하지 않는다 (앱 전용 디렉토리)
 *
 * @param context ApplicationContext (파일 경로 접근용)
 */
class PdfGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // ─────────────────────────────────────────────────────────
    // 페이지 레이아웃 상수
    // ─────────────────────────────────────────────────────────

    /** A4 너비 (포인트, 72dpi 기준) */
    private val PAGE_WIDTH = 595

    /** A4 높이 (포인트, 72dpi 기준) */
    private val PAGE_HEIGHT = 842

    /** 페이지 좌우 여백 */
    private val MARGIN_H = 40f

    /** 페이지 상하 여백 */
    private val MARGIN_V = 50f

    /** 줄 간격 */
    private val LINE_HEIGHT = 20f

    /** 섹션 간격 */
    private val SECTION_GAP = 30f

    // ─────────────────────────────────────────────────────────
    // 공개 API
    // ─────────────────────────────────────────────────────────

    /**
     * ScanReport를 PDF 파일로 생성한다.
     *
     * 생성된 파일 경로를 Result.success()로 반환한다.
     * 오류 발생 시 Result.failure()를 반환하며 임시 파일은 정리된다.
     *
     * @param report 변환할 스캔 리포트
     * @param outputPath 저장할 파일 경로 (앱 전용 디렉토리 권장)
     * @return 성공 시 Result.success(outputPath), 실패 시 Result.failure
     */
    fun generate(report: ScanReport, outputPath: String): Result<String> {
        // 보안: Path Traversal 방지 — 앱 전용 디렉토리만 허용
        val outputFile = File(outputPath)
        val allowedDir = context.filesDir.canonicalPath
        val externalDir = context.getExternalFilesDir(null)?.canonicalPath
        val canonicalOut = outputFile.canonicalPath
        if (!canonicalOut.startsWith(allowedDir) &&
            (externalDir == null || !canonicalOut.startsWith(externalDir))
        ) {
            Timber.e("PDF 출력 경로 차단 (앱 전용 디렉토리 외부): $canonicalOut")
            return Result.failure(SecurityException("PDF 출력 경로가 허용 범위를 벗어났습니다."))
        }

        val document = PdfDocument()

        return try {
            // 출력 디렉토리 생성
            outputFile.parentFile?.mkdirs()

            // 페이지 1: 개요 및 종합 위험도
            val page1 = createPage(document, pageNumber = 1)
            drawOverviewPage(page1.canvas, report)
            document.finishPage(page1)

            // 페이지 2: 발견된 기기 목록
            val page2 = createPage(document, pageNumber = 2)
            drawDevicePage(page2.canvas, report)
            document.finishPage(page2)

            // 페이지 3: 발견 사항 체크리스트
            val page3 = createPage(document, pageNumber = 3)
            drawFindingsPage(page3.canvas, report)
            document.finishPage(page3)

            // 파일 저장
            FileOutputStream(outputFile).use { stream ->
                document.writeTo(stream)
            }

            Timber.d("PDF 생성 완료 — 경로: $outputPath, 리포트 ID: ${report.id}")
            Result.success(outputPath)
        } catch (e: Exception) {
            Timber.e(e, "PDF 생성 실패 — 리포트 ID: ${report.id}")
            // 실패 시 임시 파일 정리
            if (outputFile.exists()) outputFile.delete()
            Result.failure(e)
        } finally {
            document.close()
        }
    }

    // ─────────────────────────────────────────────────────────
    // 페이지 생성 헬퍼
    // ─────────────────────────────────────────────────────────

    /**
     * PdfDocument.Page를 A4 크기로 생성한다.
     *
     * @param document 대상 PDF 문서
     * @param pageNumber 1부터 시작하는 페이지 번호
     * @return 생성된 PdfDocument.Page
     */
    private fun createPage(document: PdfDocument, pageNumber: Int): PdfDocument.Page {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        return document.startPage(pageInfo)
    }

    // ─────────────────────────────────────────────────────────
    // 페이지 1: 개요
    // ─────────────────────────────────────────────────────────

    /**
     * 개요 페이지를 그린다.
     *
     * 구성:
     *   - 앱 이름 헤더 + 생성 날짜
     *   - 구분선
     *   - 종합 위험도 점수 (대형 텍스트)
     *   - 위험 등급 (색상 표시)
     *   - 스캔 모드 / 소요 시간 / 위치 메모
     *   - 탐지 레이어별 결과 요약
     *   - 페이지 번호 푸터
     */
    private fun drawOverviewPage(canvas: Canvas, report: ScanReport) {
        var y = MARGIN_V

        // 헤더
        y = drawHeader(canvas, y, report)

        // 구분선
        y = drawDivider(canvas, y)

        // 종합 위험도 점수
        y += SECTION_GAP
        y = drawRiskScore(canvas, y, report.riskScore, report.riskLevel)

        // 스캔 정보
        y += SECTION_GAP
        y = drawSectionTitle(canvas, y, "스캔 정보")
        y = drawKeyValue(canvas, y, "스캔 모드", report.mode.labelKo)
        y = drawKeyValue(canvas, y, "소요 시간", formatDuration(report.durationMs))
        y = drawKeyValue(canvas, y, "위치 메모", report.locationNote.ifBlank { "(없음)" })
        y = drawKeyValue(canvas, y, "발견 기기 수", "${report.devices.size}대 (의심 ${report.suspiciousDeviceCount}대)")
        y = drawKeyValue(canvas, y, "렌즈 의심 포인트", "${report.retroPoints.size}개")
        y = drawKeyValue(canvas, y, "IR 의심 포인트", "${report.irPoints.size}개")

        // 레이어별 요약
        y += SECTION_GAP
        y = drawSectionTitle(canvas, y, "탐지 레이어 결과")
        for ((layerType, layerResult) in report.layerResults) {
            val statusText = "${layerResult.score}점 — ${layerResult.status.name}"
            y = drawKeyValue(canvas, y, layerType.labelKo, statusText)
        }

        // 페이지 번호
        drawPageNumber(canvas, 1, 3)
    }

    // ─────────────────────────────────────────────────────────
    // 페이지 2: 기기 목록
    // ─────────────────────────────────────────────────────────

    /**
     * 발견된 기기 목록 페이지를 그린다.
     *
     * 구성:
     *   - 섹션 제목
     *   - 기기당 한 행: IP, MAC, 제조사, 위험도 점수, 카메라 여부
     *   - 기기가 없을 경우 "발견된 기기 없음" 메시지
     *   - 페이지 번호 푸터
     */
    private fun drawDevicePage(canvas: Canvas, report: ScanReport) {
        var y = MARGIN_V

        // 소제목 헤더
        drawSmallHeader(canvas, y, "발견된 네트워크 기기")
        y += LINE_HEIGHT * 2

        y = drawDivider(canvas, y)
        y += LINE_HEIGHT

        if (report.devices.isEmpty()) {
            y = drawBodyText(canvas, y, "발견된 네트워크 기기가 없습니다.")
        } else {
            // 테이블 헤더
            y = drawTableHeader(canvas, y)

            // 기기 목록
            for (device in report.devices.sortedByDescending { it.riskScore }) {
                if (y > PAGE_HEIGHT - MARGIN_V - LINE_HEIGHT) break // 페이지 넘침 방지
                y = drawDeviceRow(canvas, y, device)
            }

            // 의심 기기 요약
            y += SECTION_GAP
            y = drawSectionTitle(canvas, y, "의심 기기 상세")
            val suspiciousDevices = report.devices.filter { it.isCamera }
            if (suspiciousDevices.isEmpty()) {
                y = drawBodyText(canvas, y, "카메라로 의심되는 기기가 없습니다.")
            } else {
                for (device in suspiciousDevices) {
                    if (y > PAGE_HEIGHT - MARGIN_V - LINE_HEIGHT * 3) break
                    y = drawSuspiciousDeviceDetail(canvas, y, device)
                }
            }
        }

        drawPageNumber(canvas, 2, 3)
    }

    // ─────────────────────────────────────────────────────────
    // 페이지 3: 발견 사항 체크리스트
    // ─────────────────────────────────────────────────────────

    /**
     * 발견 사항 체크리스트 페이지를 그린다.
     *
     * 구성:
     *   - 발견 사항(Finding) 목록 — 심각도 순
     *   - 권고 사항
     *   - 보고서 메타 정보 (생성 시각, 보고서 ID)
     *   - 페이지 번호 푸터
     */
    private fun drawFindingsPage(canvas: Canvas, report: ScanReport) {
        var y = MARGIN_V

        drawSmallHeader(canvas, y, "발견 사항 및 권고")
        y += LINE_HEIGHT * 2

        y = drawDivider(canvas, y)
        y += LINE_HEIGHT

        if (report.findings.isEmpty()) {
            y = drawBodyText(canvas, y, "특이 사항이 발견되지 않았습니다.")
        } else {
            y = drawSectionTitle(canvas, y, "발견 사항 목록")

            for (finding in report.findings.sortedByDescending { it.severity.ordinal }) {
                if (y > PAGE_HEIGHT - MARGIN_V - LINE_HEIGHT * 3) break

                val severityText = "[${finding.severity.labelKo}] ${finding.type.labelKo}"
                y = drawBodyText(canvas, y, severityText, bold = true)
                y = drawBodyText(canvas, y, "  ${finding.description}")
                y = drawBodyText(canvas, y, "  근거: ${finding.evidence}")
                y += LINE_HEIGHT * 0.5f
            }
        }

        // 권고 사항
        y += SECTION_GAP
        y = drawSectionTitle(canvas, y, "권고 사항")
        val recommendation = getRecommendation(report.riskLevel)
        y = drawBodyText(canvas, y, recommendation)

        // 보고서 메타
        y += SECTION_GAP
        y = drawDivider(canvas, y)
        y += LINE_HEIGHT
        val dateStr = SimpleDateFormat("yyyy년 MM월 dd일 HH:mm", Locale.KOREA)
            .format(Date(report.completedAt))
        y = drawBodyText(canvas, y, "생성 일시: $dateStr", small = true)
        y = drawBodyText(canvas, y, "리포트 ID: ${report.id}", small = true)
        drawBodyText(canvas, y, "SearCam — 몰래카메라 탐지 앱", small = true)

        drawPageNumber(canvas, 3, 3)
    }

    // ─────────────────────────────────────────────────────────
    // 드로잉 유틸리티
    // ─────────────────────────────────────────────────────────

    /**
     * 앱 이름과 날짜를 포함하는 헤더를 그린다.
     *
     * @return 헤더 하단 Y 좌표
     */
    private fun drawHeader(canvas: Canvas, startY: Float, report: ScanReport): Float {
        var y = startY

        // 앱 타이틀
        val titlePaint = buildPaint(size = 22f, bold = true, color = Color.BLACK)
        canvas.drawText("SearCam 스캔 리포트", MARGIN_H, y, titlePaint)
        y += LINE_HEIGHT * 1.8f

        // 부제 — 날짜
        val datePaint = buildPaint(size = 11f, color = Color.GRAY)
        val dateStr = SimpleDateFormat("yyyy년 MM월 dd일 HH:mm", Locale.KOREA)
            .format(Date(report.completedAt))
        canvas.drawText("생성일: $dateStr", MARGIN_H, y, datePaint)
        y += LINE_HEIGHT

        return y
    }

    /**
     * 작은 헤더 텍스트를 그린다 (페이지 2, 3용).
     */
    private fun drawSmallHeader(canvas: Canvas, y: Float, title: String) {
        val paint = buildPaint(size = 16f, bold = true, color = Color.BLACK)
        canvas.drawText(title, MARGIN_H, y + LINE_HEIGHT, paint)
    }

    /**
     * 가로 구분선을 그린다.
     *
     * @return 구분선 하단 Y 좌표
     */
    private fun drawDivider(canvas: Canvas, y: Float): Float {
        val paint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }
        canvas.drawLine(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y, paint)
        return y + LINE_HEIGHT * 0.5f
    }

    /**
     * 종합 위험도 점수와 등급을 대형 텍스트로 그린다.
     *
     * @return 하단 Y 좌표
     */
    private fun drawRiskScore(canvas: Canvas, y: Float, score: Int, level: RiskLevel): Float {
        var currentY = y

        val labelPaint = buildPaint(size = 12f, color = Color.GRAY)
        canvas.drawText("종합 위험도", MARGIN_H, currentY, labelPaint)
        currentY += LINE_HEIGHT

        // 점수 (대형)
        val scorePaint = buildPaint(size = 48f, bold = true, color = parseColor(level.colorHex))
        canvas.drawText("$score 점", MARGIN_H, currentY + 30f, scorePaint)
        currentY += 50f

        // 위험 등급 배지
        val badgePaint = buildPaint(size = 14f, bold = true, color = parseColor(level.colorHex))
        canvas.drawText("${level.labelKo} — ${level.description}", MARGIN_H, currentY, badgePaint)
        currentY += LINE_HEIGHT * 1.5f

        return currentY
    }

    /**
     * 섹션 제목을 그린다.
     *
     * @return 하단 Y 좌표
     */
    private fun drawSectionTitle(canvas: Canvas, y: Float, title: String): Float {
        val paint = buildPaint(size = 13f, bold = true, color = Color.DKGRAY)
        canvas.drawText(title, MARGIN_H, y, paint)
        return y + LINE_HEIGHT * 1.3f
    }

    /**
     * Key-Value 쌍을 한 행에 그린다.
     *
     * @return 하단 Y 좌표
     */
    private fun drawKeyValue(canvas: Canvas, y: Float, key: String, value: String): Float {
        val keyPaint = buildPaint(size = 11f, bold = true, color = Color.DKGRAY)
        val valuePaint = buildPaint(size = 11f, color = Color.BLACK)

        canvas.drawText("$key:", MARGIN_H, y, keyPaint)
        canvas.drawText(value, MARGIN_H + 130f, y, valuePaint)
        return y + LINE_HEIGHT
    }

    /**
     * 본문 텍스트를 그린다.
     *
     * @param bold true이면 굵은 텍스트
     * @param small true이면 작은 텍스트 (9pt)
     * @return 하단 Y 좌표
     */
    private fun drawBodyText(
        canvas: Canvas,
        y: Float,
        text: String,
        bold: Boolean = false,
        small: Boolean = false,
    ): Float {
        val size = if (small) 9f else 11f
        val paint = buildPaint(size = size, bold = bold, color = Color.BLACK)
        canvas.drawText(text, MARGIN_H, y, paint)
        return y + LINE_HEIGHT
    }

    /**
     * 기기 테이블 헤더를 그린다.
     *
     * @return 하단 Y 좌표
     */
    private fun drawTableHeader(canvas: Canvas, y: Float): Float {
        val paint = buildPaint(size = 10f, bold = true, color = Color.WHITE)
        val bgPaint = Paint().apply { color = Color.DKGRAY }

        canvas.drawRect(MARGIN_H, y - LINE_HEIGHT + 4f, PAGE_WIDTH - MARGIN_H, y + 4f, bgPaint)
        canvas.drawText("IP 주소", MARGIN_H + 5f, y, paint)
        canvas.drawText("MAC 주소", MARGIN_H + 110f, y, paint)
        canvas.drawText("제조사", MARGIN_H + 240f, y, paint)
        canvas.drawText("위험도", MARGIN_H + 360f, y, paint)
        canvas.drawText("카메라", MARGIN_H + 420f, y, paint)
        return y + LINE_HEIGHT
    }

    /**
     * 기기 목록 단일 행을 그린다.
     *
     * @return 하단 Y 좌표
     */
    private fun drawDeviceRow(canvas: Canvas, y: Float, device: NetworkDevice): Float {
        val textColor = if (device.isCamera) Color.RED else Color.BLACK
        val paint = buildPaint(size = 10f, color = textColor)

        canvas.drawText(device.ip, MARGIN_H + 5f, y, paint)
        canvas.drawText(device.mac, MARGIN_H + 110f, y, paint)
        canvas.drawText(device.vendor?.take(12) ?: "알 수 없음", MARGIN_H + 240f, y, paint)
        canvas.drawText("${device.riskScore}점", MARGIN_H + 360f, y, paint)
        canvas.drawText(if (device.isCamera) "의심" else "-", MARGIN_H + 420f, y, paint)

        return y + LINE_HEIGHT
    }

    /**
     * 의심 기기 상세 정보를 그린다.
     *
     * @return 하단 Y 좌표
     */
    private fun drawSuspiciousDeviceDetail(canvas: Canvas, y: Float, device: NetworkDevice): Float {
        var currentY = y

        val headerPaint = buildPaint(size = 11f, bold = true, color = Color.RED)
        canvas.drawText("★ ${device.ip} — ${device.vendor ?: "알 수 없는 제조사"}", MARGIN_H, currentY, headerPaint)
        currentY += LINE_HEIGHT

        currentY = drawKeyValue(canvas, currentY, "  MAC", device.mac)
        currentY = drawKeyValue(canvas, currentY, "  유형", device.deviceType.name)

        if (device.openPorts.isNotEmpty()) {
            currentY = drawKeyValue(canvas, currentY, "  개방 포트", device.openPorts.joinToString(", "))
        }
        if (device.services.isNotEmpty()) {
            currentY = drawKeyValue(canvas, currentY, "  서비스", device.services.take(3).joinToString(", "))
        }

        currentY += LINE_HEIGHT * 0.5f
        return currentY
    }

    /**
     * 페이지 번호를 하단 중앙에 그린다.
     */
    private fun drawPageNumber(canvas: Canvas, current: Int, total: Int) {
        val paint = buildPaint(size = 10f, color = Color.GRAY)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("$current / $total", PAGE_WIDTH / 2f, PAGE_HEIGHT - MARGIN_V / 2f, paint)
    }

    // ─────────────────────────────────────────────────────────
    // 권고 사항 텍스트 생성
    // ─────────────────────────────────────────────────────────

    /**
     * 위험 등급에 따른 권고 사항 텍스트를 반환한다.
     *
     * @param level 위험 등급
     * @return 한국어 권고 사항 텍스트
     */
    private fun getRecommendation(level: RiskLevel): String {
        return when (level) {
            RiskLevel.SAFE ->
                "탐지된 위협이 없습니다. 안전한 환경으로 판단됩니다."
            RiskLevel.INTEREST ->
                "경미한 의심 징후가 발견되었습니다. 추가 점검을 권장합니다."
            RiskLevel.CAUTION ->
                "복수의 의심 징후가 감지되었습니다. 육안 점검과 함께 주의 깊게 확인하세요."
            RiskLevel.DANGER ->
                "강한 의심 징후가 감지되었습니다. 즉각적인 육안 점검과 관리자 신고를 권장합니다."
            RiskLevel.CRITICAL ->
                "몰래카메라 의심이 강합니다. 즉시 경찰에 신고하고 해당 공간 사용을 중단하세요."
        }
    }

    // ─────────────────────────────────────────────────────────
    // 드로잉 헬퍼
    // ─────────────────────────────────────────────────────────

    /**
     * Paint 객체를 생성한다.
     *
     * @param size 폰트 크기 (pt)
     * @param bold true이면 굵은 텍스트
     * @param color 텍스트 색상 (Color.* 상수)
     * @return 설정된 Paint 객체
     */
    private fun buildPaint(size: Float, bold: Boolean = false, color: Int = Color.BLACK): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    /**
     * Hex 색상 문자열을 Android Color Int로 변환한다.
     *
     * 파싱 실패 시 Color.BLACK을 반환한다.
     *
     * @param hex "#RRGGBB" 형식 색상 문자열
     * @return Android Color Int
     */
    private fun parseColor(hex: String): Int {
        return try {
            Color.parseColor(hex)
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "색상 파싱 실패 — hex=$hex, 기본값 BLACK 반환")
            Color.BLACK
        }
    }

    /**
     * 밀리초 지속 시간을 읽기 쉬운 형식으로 변환한다.
     *
     * 예: 65_000L → "1분 5초"
     *
     * @param durationMs 지속 시간 (ms)
     * @return 한국어 포맷 문자열
     */
    private fun formatDuration(durationMs: Long): String {
        val seconds = durationMs / 1_000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return if (minutes > 0) "${minutes}분 ${remainingSeconds}초" else "${remainingSeconds}초"
    }
}
