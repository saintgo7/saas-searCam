# Ch17: PDF 리포트 생성 — 증거를 문서로

> **이 장에서 배울 것**: 스캔 결과를 법적 증거로 활용할 수 있는 PDF 문서로 변환하는 방법을 배웁니다. Android 내장 `PdfDocument` API로 3페이지 PDF를 생성하고, 경로 순회(Path Traversal) 공격을 `canonicalPath`로 방어하고, `ExportReportUseCase`가 도메인 레이어에서 이 흐름을 조율하는 방식, 위험도별 색상 코드 시각화, 그리고 `FileProvider`로 다른 앱과 PDF를 안전하게 공유하는 방법까지 — 증거 문서 생성의 전 과정을 다룹니다.

---

## 도입

병원에서 검사를 받으면 결과지가 나옵니다. 의사의 서명, 날짜, 측정값, 해석까지 담긴 공식 문서입니다. 구두로 "혈당이 좀 높아요"라고 말하는 것과 "2026년 4월 4일 공복 혈당 126mg/dL" 이 적힌 문서는 법적 효력이 다릅니다.

SearCam의 PDF 리포트도 같은 목적입니다. 탐지 앱이 "위험합니다"라고 말하는 것과, 날짜, 장소, 발견된 기기 목록, 위험도 점수가 담긴 PDF 문서를 경찰에 제출하는 것은 차원이 다른 이야기입니다. 이 장에서는 `PdfGenerator`가 어떻게 스캔 데이터를 구조화된 문서로 변환하는지 낱낱이 살펴봅니다.

---

## 17.1 Android PdfDocument API 개요

### 외부 라이브러리 없이 PDF 만들기

iText, Apache PDFBox — PDF 생성 라이브러리는 많습니다. 하지만 SearCam은 외부 라이브러리를 쓰지 않습니다. Android API 19(4.4)부터 내장된 `android.graphics.pdf.PdfDocument`만으로 충분합니다.

외부 라이브러리를 배제한 이유는 두 가지입니다. 첫째, APK 용량입니다. iText 라이브러리는 수 MB 규모인데, SearCam처럼 PDF가 부가 기능인 앱에 과도합니다. 둘째, 보안입니다. 외부 라이브러리의 취약점이 앱에 그대로 전파됩니다. 내장 API는 Android 보안 업데이트와 함께 패치됩니다.

`PdfDocument`의 작동 방식은 캔버스 그림 그리기와 같습니다.

```
PdfDocument 생성
    → 페이지 시작 (startPage)
        → Canvas에 텍스트/선/도형 그리기
    → 페이지 완료 (finishPage)
    → 다음 페이지 반복
→ 파일에 쓰기 (writeTo)
→ 문서 닫기 (close)
```

---

## 17.2 PdfGenerator 설계 — 3페이지 구조

### 페이지별 책임 분리

SearCam의 PDF는 3페이지로 구성됩니다. 각 페이지는 독립적인 함수가 담당합니다.

```
페이지 1: drawOverviewPage()   — 종합 위험도 + 스캔 정보 + 레이어별 결과
페이지 2: drawDevicePage()     — 발견된 네트워크 기기 목록 + 의심 기기 상세
페이지 3: drawFindingsPage()   — 발견 사항 체크리스트 + 권고 사항 + 메타 정보
```

페이지 레이아웃 상수는 파일 상단에 모아두었습니다.

```kotlin
// data/pdf/PdfGenerator.kt

class PdfGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
) {
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
```

A4 크기가 595 × 842 포인트인 이유: PDF의 기본 단위는 포인트(point)입니다. 1포인트 = 1/72 인치입니다. A4 용지는 210 × 297mm = 8.27 × 11.69인치 = 595.3 × 841.9 포인트입니다.

### generate() — 메인 진입점과 Path Traversal 방어

```kotlin
fun generate(report: ScanReport, outputPath: String): Result<String> {
    // 보안: Path Traversal 방지
    val outputFile = File(outputPath)
    val allowedDir = context.filesDir.canonicalPath
    val externalDir = context.getExternalFilesDir(null)?.canonicalPath
    val canonicalOut = outputFile.canonicalPath

    if (!canonicalOut.startsWith(allowedDir) &&
        (externalDir == null || !canonicalOut.startsWith(externalDir))
    ) {
        Timber.e("PDF 출력 경로 차단: $canonicalOut")
        return Result.failure(
            SecurityException("PDF 출력 경로가 허용 범위를 벗어났습니다.")
        )
    }

    val document = PdfDocument()

    return try {
        outputFile.parentFile?.mkdirs()

        val page1 = createPage(document, pageNumber = 1)
        drawOverviewPage(page1.canvas, report)
        document.finishPage(page1)

        val page2 = createPage(document, pageNumber = 2)
        drawDevicePage(page2.canvas, report)
        document.finishPage(page2)

        val page3 = createPage(document, pageNumber = 3)
        drawFindingsPage(page3.canvas, report)
        document.finishPage(page3)

        FileOutputStream(outputFile).use { stream ->
            document.writeTo(stream)
        }

        Timber.d("PDF 생성 완료: $outputPath")
        Result.success(outputPath)
    } catch (e: Exception) {
        Timber.e(e, "PDF 생성 실패")
        if (outputFile.exists()) outputFile.delete()  // 실패 시 부분 파일 정리
        Result.failure(e)
    } finally {
        document.close()  // 반드시 닫아야 메모리 해제
    }
}
```

`document.close()`는 `finally`에서 호출합니다. 예외가 발생해도 `PdfDocument`가 점유한 네이티브 메모리가 해제됩니다. `PdfDocument`는 페이지당 비트맵 버퍼를 내부적으로 관리하므로 Close 없이 버려지면 메모리 누수로 이어집니다.

---

## 17.3 Path Traversal 방어 — canonicalPath 검증

### 경로 조작 공격이란

Path Traversal은 `../`를 이용해서 허용된 디렉토리 밖에 파일을 쓰는 공격입니다. 예를 들어 `outputPath`로 `/data/data/com.searcam/databases/../../../etc/hosts`가 들어오면, 일반 경로 비교(`startsWith`)는 통과하지만 실제로는 시스템 파일을 덮어씁니다.

`canonicalPath`는 `.`, `..`, 심볼릭 링크를 모두 해석한 절대 경로를 반환합니다. 경로 조작의 여지를 없앱니다.

```kotlin
// 취약한 코드 (절대 사용 금지)
if (outputPath.startsWith(allowedDir)) {  // "../.."로 우회 가능
    // 파일 쓰기
}

// 안전한 코드 (SearCam 구현)
val canonicalOut = File(outputPath).canonicalPath
val allowedDir = context.filesDir.canonicalPath
if (!canonicalOut.startsWith(allowedDir)) {
    return Result.failure(SecurityException("경로 차단"))
}
```

`File("/data/data/com.searcam/files/../databases/secret").canonicalPath`를 실행하면 `/data/data/com.searcam/databases/secret`이 반환됩니다. 이 경로는 `files/`로 시작하지 않으므로 차단됩니다.

앱 내부 저장소(`context.filesDir`)와 외부 저장소(`context.getExternalFilesDir()`) 두 곳을 허용 목록으로 관리합니다. 두 곳 모두 앱 전용 디렉토리로, 다른 앱이 직접 접근할 수 없습니다.

---

## 17.4 페이지 1 — 종합 위험도 시각화

### drawOverviewPage — 첫인상이 전부다

```kotlin
private fun drawOverviewPage(canvas: Canvas, report: ScanReport) {
    var y = MARGIN_V

    y = drawHeader(canvas, y, report)
    y = drawDivider(canvas, y)

    y += SECTION_GAP
    y = drawRiskScore(canvas, y, report.riskScore, report.riskLevel)

    y += SECTION_GAP
    y = drawSectionTitle(canvas, y, "스캔 정보")
    y = drawKeyValue(canvas, y, "스캔 모드", report.mode.labelKo)
    y = drawKeyValue(canvas, y, "소요 시간", formatDuration(report.durationMs))
    y = drawKeyValue(canvas, y, "위치 메모", report.locationNote.ifBlank { "(없음)" })
    y = drawKeyValue(canvas, y, "발견 기기 수", "${report.devices.size}대 (의심 ${report.suspiciousDeviceCount}대)")
    y = drawKeyValue(canvas, y, "렌즈 의심 포인트", "${report.retroPoints.size}개")
    y = drawKeyValue(canvas, y, "IR 의심 포인트", "${report.irPoints.size}개")

    y += SECTION_GAP
    y = drawSectionTitle(canvas, y, "탐지 레이어 결과")
    for ((layerType, layerResult) in report.layerResults) {
        val statusText = "${layerResult.score}점 — ${layerResult.status.name}"
        y = drawKeyValue(canvas, y, layerType.labelKo, statusText)
    }

    drawPageNumber(canvas, 1, 3)
}
```

`var y`를 통해 세로 방향 커서를 관리합니다. 각 드로잉 함수는 그린 영역의 마지막 Y 좌표를 반환하고, 다음 함수는 이 값을 시작점으로 받습니다. 간단하지만 효과적인 플로우 레이아웃 패턴입니다.

### 위험도 점수 대형 표시와 색상 코드

위험도는 PDF에서 한눈에 들어와야 합니다. 48pt 폰트로 점수를 크게 표시하고, 위험 등급에 따라 색상을 바꿉니다.

```kotlin
private fun drawRiskScore(canvas: Canvas, y: Float, score: Int, level: RiskLevel): Float {
    var currentY = y

    // "종합 위험도" 라벨 (작은 회색 텍스트)
    val labelPaint = buildPaint(size = 12f, color = Color.GRAY)
    canvas.drawText("종합 위험도", MARGIN_H, currentY, labelPaint)
    currentY += LINE_HEIGHT

    // 점수 숫자 (48pt, 위험도 색상)
    val scorePaint = buildPaint(size = 48f, bold = true, color = parseColor(level.colorHex))
    canvas.drawText("$score 점", MARGIN_H, currentY + 30f, scorePaint)
    currentY += 50f

    // 위험 등급 설명
    val badgePaint = buildPaint(size = 14f, bold = true, color = parseColor(level.colorHex))
    canvas.drawText("${level.labelKo} — ${level.description}", MARGIN_H, currentY, badgePaint)
    currentY += LINE_HEIGHT * 1.5f

    return currentY
}
```

위험 등급별 색상 체계는 SearCam 전체에서 일관되게 사용됩니다.

| 위험 등급 | 색상 | Hex 코드 |
|----------|------|---------|
| SAFE (안전) | 초록 | #22C55E |
| INTEREST (관심) | 연두 | #84CC16 |
| CAUTION (주의) | 노랑 | #EAB308 |
| DANGER (위험) | 주황 | #F97316 |
| CRITICAL (매우 위험) | 빨강 | #EF4444 |

이 색상 코드는 `RiskLevel.colorHex` 프로퍼티로 도메인 모델에 정의되어 있습니다. PDF, Compose UI, 아이콘 모두 같은 값을 참조하므로 색상이 어디서나 일치합니다.

---

## 17.5 페이지 2 — 기기 목록 테이블

### 테이블 헤더와 행 렌더링

PDF에는 HTML처럼 자동 레이아웃이 없습니다. 모든 위치를 픽셀 단위로 직접 지정해야 합니다.

```kotlin
private fun drawTableHeader(canvas: Canvas, y: Float): Float {
    // 배경 사각형 (어두운 회색)
    val bgPaint = Paint().apply { color = Color.DKGRAY }
    canvas.drawRect(MARGIN_H, y - LINE_HEIGHT + 4f, PAGE_WIDTH - MARGIN_H, y + 4f, bgPaint)

    // 흰 텍스트로 헤더 항목
    val paint = buildPaint(size = 10f, bold = true, color = Color.WHITE)
    canvas.drawText("IP 주소",  MARGIN_H + 5f,   y, paint)
    canvas.drawText("MAC 주소", MARGIN_H + 110f,  y, paint)
    canvas.drawText("제조사",   MARGIN_H + 240f,  y, paint)
    canvas.drawText("위험도",   MARGIN_H + 360f,  y, paint)
    canvas.drawText("카메라",   MARGIN_H + 420f,  y, paint)
    return y + LINE_HEIGHT
}

private fun drawDeviceRow(canvas: Canvas, y: Float, device: NetworkDevice): Float {
    // 의심 기기는 빨간색으로 강조
    val textColor = if (device.isCamera) Color.RED else Color.BLACK
    val paint = buildPaint(size = 10f, color = textColor)

    canvas.drawText(device.ip,                           MARGIN_H + 5f,   y, paint)
    canvas.drawText(device.mac,                          MARGIN_H + 110f,  y, paint)
    canvas.drawText(device.vendor?.take(12) ?: "알 수 없음", MARGIN_H + 240f,  y, paint)
    canvas.drawText("${device.riskScore}점",              MARGIN_H + 360f,  y, paint)
    canvas.drawText(if (device.isCamera) "의심" else "-",  MARGIN_H + 420f,  y, paint)

    return y + LINE_HEIGHT
}
```

`device.vendor?.take(12)`는 제조사 이름이 너무 길어서 다음 컬럼을 침범하는 것을 방지합니다. PDF Canvas는 텍스트가 영역을 벗어나도 자동 줄바꿈이 없습니다 — 직접 잘라야 합니다.

### 페이지 오버플로우 방지

기기가 많으면 페이지를 넘칠 수 있습니다. 현재 Y 위치를 체크해서 페이지 끝에 가까워지면 렌더링을 중단합니다.

```kotlin
for (device in report.devices.sortedByDescending { it.riskScore }) {
    // 페이지 하단 여백에 한 줄이 들어갈 공간이 없으면 중단
    if (y > PAGE_HEIGHT - MARGIN_V - LINE_HEIGHT) break
    y = drawDeviceRow(canvas, y, device)
}
```

프로덕션 품질 PDF라면 다음 페이지로 자동 넘김(pagination)을 구현해야 합니다. SearCam 1.0에서는 기기가 많을 경우 위험도 높은 순으로 정렬하고 넘치는 부분은 생략합니다. 기기 목록 전체보다 가장 위험한 기기를 먼저 보여주는 것이 사용자에게 더 유용하기 때문입니다.

---

## 17.6 페이지 3 — 발견 사항과 권고 사항

### 심각도 순 정렬과 구조화된 출력

```kotlin
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

        // severity.ordinal: enum 선언 순서 (CRITICAL > DANGER > ...)
        for (finding in report.findings.sortedByDescending { it.severity.ordinal }) {
            if (y > PAGE_HEIGHT - MARGIN_V - LINE_HEIGHT * 3) break

            // 심각도 배지와 발견 유형 (굵은 텍스트)
            val severityText = "[${finding.severity.labelKo}] ${finding.type.labelKo}"
            y = drawBodyText(canvas, y, severityText, bold = true)
            y = drawBodyText(canvas, y, "  ${finding.description}")
            y = drawBodyText(canvas, y, "  근거: ${finding.evidence}")
            y += LINE_HEIGHT * 0.5f  // 항목 간 여백
        }
    }

    // 권고 사항 — 위험 등급에 따라 다른 텍스트
    y += SECTION_GAP
    y = drawSectionTitle(canvas, y, "권고 사항")
    val recommendation = getRecommendation(report.riskLevel)
    y = drawBodyText(canvas, y, recommendation)

    // 보고서 메타 정보
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
```

### 위험 등급별 권고 사항 텍스트

```kotlin
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
```

권고 사항 텍스트는 법적 주의 사항입니다. "경찰에 신고하세요"는 명확한 행동 지침이지만, "몰래카메라가 있습니다"는 단정 짓지 않습니다. SearCam은 탐지 도우미이지 법적 판단 주체가 아닙니다. 문구 선택이 중요합니다.

---

## 17.7 드로잉 유틸리티 — buildPaint와 parseColor

### Paint 팩토리 함수

PDF Canvas에서 텍스트를 그릴 때마다 `Paint` 객체를 생성합니다. `buildPaint()`는 반복되는 설정을 한 곳에 모읍니다.

```kotlin
private fun buildPaint(
    size: Float,
    bold: Boolean = false,
    color: Int = Color.BLACK,
): Paint {
    return Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
}
```

`Paint.ANTI_ALIAS_FLAG`는 텍스트 가장자리를 부드럽게 렌더링합니다. 없으면 계단 현상(aliasing)이 생겨서 PDF를 확대했을 때 텍스트가 거칠어 보입니다.

### 안전한 색상 파싱

```kotlin
private fun parseColor(hex: String): Int {
    return try {
        Color.parseColor(hex)
    } catch (e: IllegalArgumentException) {
        Timber.e(e, "색상 파싱 실패 — hex=$hex, 기본값 BLACK 반환")
        Color.BLACK  // 파싱 실패 시 기본값으로 계속 진행
    }
}
```

`Color.parseColor()`는 유효하지 않은 Hex 문자열이 들어오면 `IllegalArgumentException`을 던집니다. 잡지 않으면 PDF 생성 전체가 실패합니다. 잘못된 색상 코드 때문에 증거 PDF가 생성되지 않는 것보다, 색상만 검정으로 대체하고 문서를 완성하는 것이 낫습니다.

---

## 17.8 ExportReportUseCase — 도메인 레이어의 흐름 조율

### UseCase는 오케스트라 지휘자

`ExportReportUseCase`는 실제로 PDF를 그리거나 파일을 저장하지 않습니다. 그 일은 `PdfGenerator`와 `ReportRepository`가 합니다. UseCase는 이 흐름을 조율하는 지휘자 역할입니다.

```kotlin
// domain/usecase/ExportReportUseCase.kt

class ExportReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * 리포트를 PDF로 내보내고 저장된 파일 경로를 반환한다.
     *
     * 흐름:
     *   1. reportId로 리포트 조회
     *   2. 출력 파일명 생성
     *   3. ReportRepository.exportToPdf() 호출
     *   4. 저장된 파일 경로 반환
     */
    suspend operator fun invoke(reportId: String): Result<String> {
        // 1단계: 리포트 조회
        val reportResult = reportRepository.getReport(reportId)
        if (reportResult.isFailure) {
            return Result.failure(
                reportResult.exceptionOrNull()
                    ?: IllegalStateException("리포트를 찾을 수 없습니다: $reportId")
            )
        }

        val report = reportResult.getOrThrow()

        // 2단계: 파일명 생성 (경로는 data 레이어가 결정)
        val outputFileName = buildPdfFileName(report.completedAt)

        // 3단계: 위임
        return reportRepository.exportToPdf(
            report = report,
            outputPath = outputFileName,
        )
    }

    /**
     * PDF 파일명을 생성한다.
     *
     * 경로(디렉토리)는 data 레이어 구현체에서 결정한다.
     * UseCase는 파일명만 알고 있어야 한다.
     */
    private fun buildPdfFileName(epochMs: Long): String =
        "searcam_report_${epochMs}.pdf"
}
```

UseCase에 `@ApplicationContext`가 없습니다. 파일 경로 결정은 data 레이어(`ReportRepositoryImpl`)의 책임입니다. UseCase는 "어떤 파일에 저장할지"가 아니라 "리포트를 PDF로 변환해달라"는 의도만 표현합니다. 이 덕분에 UseCase를 순수 JVM 환경에서 테스트할 수 있습니다.

### ViewModel에서 UseCase 호출

```kotlin
// ui/report/ReportViewModel.kt

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val exportReportUseCase: ExportReportUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val reportId = savedStateHandle.get<String>("reportId")!!

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    fun exportToPdf() {
        if (_exportState.value is ExportState.Loading) return

        viewModelScope.launch {
            _exportState.value = ExportState.Loading

            val result = exportReportUseCase(reportId)

            _exportState.value = result.fold(
                onSuccess = { filePath -> ExportState.Success(filePath) },
                onFailure = { error -> ExportState.Error(error.message ?: "알 수 없는 오류") },
            )
        }
    }
}

sealed class ExportState {
    data object Idle : ExportState()
    data object Loading : ExportState()
    data class Success(val filePath: String) : ExportState()
    data class Error(val message: String) : ExportState()
}
```

`result.fold()`는 `Result<T>`의 성공/실패를 깔끔하게 처리합니다. `if (result.isSuccess)` 분기와 달리, `fold`는 두 케이스 모두 값을 반환해야 하므로 누락 없이 처리를 강제합니다.

---

## 17.9 FileProvider로 PDF 안전하게 공유

### 다른 앱이 파일을 읽게 허용하기

PDF 파일이 앱 내부 저장소에 있으면 다른 앱이 직접 접근할 수 없습니다. 이메일, 카카오톡 같은 앱으로 PDF를 공유하려면 임시 읽기 권한을 부여해야 합니다. `FileProvider`가 이 역할을 합니다.

```xml
<!-- AndroidManifest.xml -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_provider_paths" />
</provider>
```

```xml
<!-- res/xml/file_provider_paths.xml -->
<paths>
    <!-- 앱 내부 저장소 -->
    <files-path name="files" path="." />
    <!-- 앱 외부 저장소 (Documents 디렉토리) -->
    <external-files-path name="external_files" path="Documents/" />
</paths>
```

```kotlin
// PDF 공유 함수

fun sharePdf(context: Context, filePath: String) {
    val file = File(filePath)

    // FileProvider URI 생성 — 직접 파일 경로 대신 content:// URI 사용
    val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "SearCam 스캔 리포트")
        // 받는 앱에 임시 읽기 권한 부여
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(
        Intent.createChooser(shareIntent, "리포트 공유")
    )
}
```

`FLAG_GRANT_READ_URI_PERMISSION`이 핵심입니다. 이 플래그가 있어야 공유 대상 앱이 `content://` URI로 파일을 읽을 수 있습니다. 공유가 끝나면 권한이 자동으로 취소됩니다. 파일 경로를 직접 노출하지 않으므로 다른 앱이 앱 내부 디렉토리 구조를 알 수 없습니다.

### Compose에서 공유 버튼 연결

```kotlin
// ui/report/ReportScreen.kt

@Composable
fun ReportScreen(
    reportId: String,
    viewModel: ReportViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()

    // 내보내기 성공 시 공유 인텐트 실행
    LaunchedEffect(exportState) {
        if (exportState is ExportState.Success) {
            sharePdf(context, (exportState as ExportState.Success).filePath)
        }
    }

    // 내보내기 버튼
    Button(
        onClick = viewModel::exportToPdf,
        enabled = exportState !is ExportState.Loading,
    ) {
        if (exportState is ExportState.Loading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp))
        } else {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("PDF로 공유")
        }
    }
}
```

`LaunchedEffect(exportState)`는 `exportState`가 바뀔 때마다 재실행됩니다. `ExportState.Success`일 때만 `sharePdf()`를 호출하므로, 사용자가 화면을 회전해도(Recomposition) 공유 인텐트가 중복 실행되지 않습니다 — `exportState`가 바뀌지 않았으므로 `LaunchedEffect`가 재실행되지 않습니다.

---

## 17.10 전체 흐름 요약

```
사용자: "PDF로 공유" 버튼 탭
    ↓
ReportViewModel.exportToPdf()
    ↓
ExportReportUseCase.invoke(reportId)
    ├── ReportRepository.getReport(reportId)
    │       └── ReportDao.findById() → ScanReportEntity
    │               └── Mapper.toDomain() → ScanReport
    └── ReportRepository.exportToPdf(report, outputPath)
            ├── Path Traversal 검증 (canonicalPath)
            ├── PdfDocument 생성
            ├── drawOverviewPage() — 종합 위험도 + 스캔 정보
            ├── drawDevicePage()   — 기기 목록 테이블
            ├── drawFindingsPage() — 발견 사항 + 권고
            └── FileOutputStream.write() → 파일 저장
                    ↓
ExportState.Success(filePath)
    ↓
LaunchedEffect: sharePdf(context, filePath)
    ↓
FileProvider.getUriForFile() → content:// URI
    ↓
Intent.ACTION_SEND → 공유 앱 선택
```

각 계층이 자신의 책임만 담당합니다. UseCase는 흐름만 조율하고, Repository는 데이터 접근을 추상화하고, PdfGenerator는 렌더링에만 집중합니다. 어느 계층도 다른 계층의 구현 세부사항을 알지 못합니다.

---

## 실습

> **실습 17-1**: `PdfGenerator`의 `drawDeviceRow()`를 수정해서 위험도 점수 50 이상인 기기의 행 배경을 연한 빨강(`Color.argb(50, 255, 0, 0)`)으로 채워보세요.

> **실습 17-2**: 기기 목록이 한 페이지를 넘는 경우를 테스트해보세요. 20개 이상의 가짜 `NetworkDevice`를 만들어 `generate()`를 호출하고, 결과 PDF에서 넘친 기기가 어떻게 처리되는지 확인하세요.

> **실습 17-3**: `ExportReportUseCase`의 단위 테스트를 작성해보세요. `ReportRepository`를 Mock으로 대체하고, `reportId`에 없는 ID가 들어왔을 때 `Result.failure`가 올바르게 반환되는지 검증하세요.

---

## 핵심 정리

| 개념 | 핵심 |
|------|------|
| PdfDocument | Android 내장 API, 외부 라이브러리 불필요, A4 = 595×842pt |
| Canvas Y 커서 | 각 드로잉 함수가 마지막 Y를 반환, 누적으로 레이아웃 구성 |
| Path Traversal 방어 | `canonicalPath`로 `..` 해석 후 허용 디렉토리 비교 |
| parseColor 방어 | 파싱 실패 시 `Color.BLACK` 반환, PDF 생성 실패 방지 |
| ExportReportUseCase | 흐름 조율만, Android 의존성 없음, 순수 JVM 테스트 가능 |
| FileProvider | `content://` URI로 파일 노출, 임시 읽기 권한만 부여 |
| FLAG_GRANT_READ_URI_PERMISSION | 공유 앱에 임시 읽기 권한, 공유 종료 후 자동 취소 |

- `PdfDocument.close()`는 `finally`에서 반드시 호출해야 네이티브 메모리가 해제된다
- 텍스트가 긴 경우 `take(n)`으로 잘라야 다음 컬럼을 침범하지 않는다
- `FileProvider` 없이 파일 경로(`file://`)를 직접 공유하면 Android 7.0 이상에서 `FileUriExposedException`이 발생한다
- 권고 사항 문구는 단정이 아닌 권고 형식으로 — 앱은 탐지 도우미이지 법적 판단 주체가 아니다

---

## 다음 장 예고

이제 SearCam의 핵심 기능 구현이 모두 완성되었습니다. Ch18에서는 구현한 코드를 검증하는 테스트 전략 — 도메인 레이어 단위 테스트, Room DB 통합 테스트, Compose UI 테스트, 그리고 실제 하드웨어 없이 센서를 테스트하는 방법까지 — 을 다룹니다.

---
*참고 문서: docs/14-security-design.md, docs/06-api-design.md, docs/18-test-strategy.md*
