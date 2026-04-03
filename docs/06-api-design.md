# SearCam API 설계 명세서

> 버전: v1.0
> 작성일: 2026-04-03
> 기반: project-plan.md v3.1, 04-system-architecture.md

---

## 1. API 설계 개요

SearCam Phase 1은 서버 없이 온디바이스로 동작하므로 **외부 REST API가 존재하지 않는다**.
본 문서에서 정의하는 API는 Clean Architecture의 내부 레이어 간 인터페이스이다.

```
┌──────────────────────────────────────────────┐
│  API 구조                                     │
│                                               │
│  ViewModel ─── UseCase Interface ──── (domain)│
│                     │                          │
│              Repository Interface ──── (domain)│
│                     │                          │
│              Repository Impl ──────── (data)   │
│                     │                          │
│              Sensor / DB / PDF ─────── (data)  │
└──────────────────────────────────────────────┘
```

---

## 2. UseCase 인터페이스

### 2.1 RunQuickScanUseCase

| 항목 | 상세 |
|------|------|
| 패키지 | `com.searcam.domain.usecase` |
| 설명 | Wi-Fi 전용 빠른 스캔 (30초 이내) |
| 입력 | 없음 |
| 출력 | `Flow<QuickScanState>` |
| 에러 | WifiNotConnectedException, PermissionDeniedException, ScanTimeoutException |

```kotlin
interface RunQuickScanUseCase {
    operator fun invoke(): Flow<QuickScanState>
}

sealed interface QuickScanState {
    data object Preparing : QuickScanState
    data class Scanning(
        val step: ScanStep,
        val progress: Float,          // 0.0 ~ 1.0
        val devicesFound: Int
    ) : QuickScanState
    data class Completed(
        val result: ScanResult
    ) : QuickScanState
    data class Error(
        val error: ScanError
    ) : QuickScanState
}

enum class ScanStep {
    ARP_SCAN,
    MDNS_DISCOVERY,
    OUI_MATCHING,
    PORT_SCAN,
    RISK_CALCULATION
}
```

### 2.2 RunFullScanUseCase

| 항목 | 상세 |
|------|------|
| 패키지 | `com.searcam.domain.usecase` |
| 설명 | 3-Layer 통합 스캔 (Wi-Fi + 렌즈 + EMF) |
| 입력 | `FullScanConfig` |
| 출력 | `Flow<FullScanState>` |
| 에러 | 각 레이어별 에러 (graceful degradation) |

```kotlin
interface RunFullScanUseCase {
    operator fun invoke(config: FullScanConfig): Flow<FullScanState>
}

data class FullScanConfig(
    val enableWifi: Boolean = true,
    val enableLens: Boolean = true,
    val enableIr: Boolean = true,
    val enableEmf: Boolean = true,
    val emfSensitivity: EmfSensitivity = EmfSensitivity.NORMAL,
    val maxDurationSeconds: Int = 180
)

sealed interface FullScanState {
    data object Preparing : FullScanState
    data class Layer1InProgress(
        val step: ScanStep,
        val progress: Float,
        val devicesFound: Int
    ) : FullScanState
    data class Layer2InProgress(
        val lensPointsFound: Int,
        val irPointsFound: Int,
        val elapsed: Long
    ) : FullScanState
    data class Layer3InProgress(
        val isCalibrated: Boolean,
        val currentDelta: Float,
        val maxDelta: Float,
        val elapsed: Long
    ) : FullScanState
    data class CrossValidating(
        val layer1Result: Layer1Result?,
        val layer2Result: Layer2Result?,
        val layer3Result: Layer3Result?
    ) : FullScanState
    data class Completed(
        val result: ScanResult
    ) : FullScanState
    data class Error(
        val error: ScanError,
        val partialResult: ScanResult?
    ) : FullScanState
}
```

### 2.3 RunLensFinderUseCase

| 항목 | 상세 |
|------|------|
| 패키지 | `com.searcam.domain.usecase` |
| 설명 | 수동 렌즈 찾기 모드 (플래시 Retroreflection) |
| 입력 | 없음 |
| 출력 | `Flow<LensFinderState>` |
| 에러 | CameraNotAvailableException, FlashNotAvailableException |

```kotlin
interface RunLensFinderUseCase {
    fun start(): Flow<LensFinderState>
    fun stop()
}

sealed interface LensFinderState {
    data object Initializing : LensFinderState
    data class Scanning(
        val suspects: List<LensPoint>,
        val frameCount: Long,
        val elapsedSeconds: Int
    ) : LensFinderState
    data class PointDetected(
        val point: LensPoint,
        val allPoints: List<LensPoint>
    ) : LensFinderState
    data class Stopped(
        val totalPoints: List<LensPoint>,
        val duration: Long
    ) : LensFinderState
}
```

### 2.4 CalculateRiskUseCase

| 항목 | 상세 |
|------|------|
| 패키지 | `com.searcam.domain.usecase` |
| 설명 | 교차 검증 + 종합 위험도 산출 |
| 입력 | `Layer1Result?`, `Layer2Result?`, `Layer3Result?` |
| 출력 | `ScanResult` |
| 에러 | 없음 (모든 레이어 null이면 SAFE 반환) |

```kotlin
interface CalculateRiskUseCase {
    suspend operator fun invoke(
        layer1: Layer1Result?,
        layer2: Layer2Result?,
        layer3: Layer3Result?
    ): ScanResult
}
```

### 2.5 ExportReportUseCase

| 항목 | 상세 |
|------|------|
| 패키지 | `com.searcam.domain.usecase` |
| 설명 | 스캔 결과를 리포트로 저장/내보내기 |
| 입력 | `ScanResult`, `ExportFormat` |
| 출력 | `ExportResult` |
| 에러 | StorageException, PdfGenerationException |

```kotlin
interface ExportReportUseCase {
    suspend fun save(result: ScanResult): Long  // reportId 반환
    suspend fun exportPdf(reportId: Long): ExportResult
    suspend fun getReport(reportId: Long): ScanReport?
    suspend fun getAllReports(): List<ScanReport>
    suspend fun deleteReport(reportId: Long)
}

sealed interface ExportResult {
    data class Success(val filePath: String) : ExportResult
    data class Error(val reason: String) : ExportResult
    data class PremiumRequired(val message: String) : ExportResult
}
```

---

## 3. Repository 인터페이스

### 3.1 WifiScanRepository

```kotlin
interface WifiScanRepository {

    /** 현재 Wi-Fi 연결 상태 확인 */
    suspend fun isWifiConnected(): Boolean

    /** ARP 테이블에서 네트워크 기기 목록 조회 */
    suspend fun getArpDevices(): List<ArpEntry>

    /** mDNS/SSDP 서비스 탐색 (타임아웃 포함) */
    fun discoverServices(timeoutMs: Long = 10_000): Flow<DiscoveredService>

    /** 특정 IP의 포트 개방 여부 확인 */
    suspend fun scanPorts(
        ip: String,
        ports: List<Int> = DEFAULT_CAMERA_PORTS,
        timeoutPerPort: Long = 2_000
    ): List<PortResult>

    /** MAC OUI 매칭 */
    suspend fun matchOui(mac: String): OuiResult?

    /** Wi-Fi SSID 목록 조회 (자체 AP 카메라 탐지) */
    suspend fun scanWifiNetworks(): List<WifiNetwork>

    companion object {
        val DEFAULT_CAMERA_PORTS = listOf(554, 80, 8080, 8888, 3702, 1935)
    }
}

data class ArpEntry(
    val ip: String,
    val mac: String,
    val device: String,
    val flags: String
)

data class DiscoveredService(
    val name: String,
    val type: String,        // "_rtsp._tcp", "_http._tcp" 등
    val host: String,
    val port: Int
)

data class PortResult(
    val port: Int,
    val isOpen: Boolean,
    val serviceName: String  // "RTSP", "HTTP", "ONVIF" 등
)

data class OuiResult(
    val vendor: String,
    val type: String,        // "ip_camera", "smart_camera", "consumer"
    val risk: Float          // 0.0 ~ 1.0
)
```

### 3.2 LensDetectionRepository

```kotlin
interface LensDetectionRepository {

    /** 후면 카메라 사용 가능 여부 */
    suspend fun isRearCameraAvailable(): Boolean

    /** 플래시 사용 가능 여부 */
    suspend fun isFlashAvailable(): Boolean

    /** 플래시 Retroreflection 렌즈 감지 시작 */
    fun startRetroReflectionScan(): Flow<LensDetectionEvent>

    /** 렌즈 감지 중단 */
    fun stopScan()

    /** 플래시 수동 토글 (동적 검증) */
    suspend fun toggleFlash(): Boolean
}

sealed interface LensDetectionEvent {
    data class FrameAnalyzed(
        val frameNumber: Long,
        val brightPoints: Int,
        val candidates: Int
    ) : LensDetectionEvent
    data class SuspectFound(
        val point: LensPoint
    ) : LensDetectionEvent
    data class PointVerified(
        val point: LensPoint,
        val flashOffDisappeared: Boolean
    ) : LensDetectionEvent
    data class ScanSummary(
        val totalFrames: Long,
        val confirmedPoints: List<LensPoint>,
        val score: Int
    ) : LensDetectionEvent
}
```

### 3.3 IrDetectionRepository

```kotlin
interface IrDetectionRepository {

    /** 전면 카메라 사용 가능 여부 */
    suspend fun isFrontCameraAvailable(): Boolean

    /** 현재 조도 확인 (IR 감지 조건: < 10 lux) */
    suspend fun getAmbientLight(): Float

    /** IR 감지 시작 (암실 전용) */
    fun startIrScan(): Flow<IrDetectionEvent>

    /** IR 감지 중단 */
    fun stopScan()
}

sealed interface IrDetectionEvent {
    data class FrameAnalyzed(
        val frameNumber: Long,
        val irPointsCount: Int
    ) : IrDetectionEvent
    data class IrPointDetected(
        val point: IrPoint
    ) : IrDetectionEvent
    data class ScanSummary(
        val confirmedPoints: List<IrPoint>,
        val score: Int
    ) : IrDetectionEvent
}
```

### 3.4 MagneticRepository

```kotlin
interface MagneticRepository {

    /** 자력계 센서 사용 가능 여부 */
    suspend fun isMagnetometerAvailable(): Boolean

    /** 캘리브레이션 수행 (3초) */
    suspend fun calibrate(): CalibrationResult

    /** 실시간 자기장 측정 시작 (20Hz) */
    fun startMeasuring(): Flow<MagneticReading>

    /** 측정 중단 */
    fun stopMeasuring()

    /** 감도 설정 변경 */
    fun setSensitivity(sensitivity: EmfSensitivity)
}

data class CalibrationResult(
    val baseline: Float,       // uT
    val noiseFloor: Float,     // uT
    val isReliable: Boolean    // noise > 30uT면 false
)

data class MagneticReading(
    val magnitude: Float,      // uT
    val x: Float,
    val y: Float,
    val z: Float,
    val delta: Float,          // |magnitude - baseline|
    val level: MagneticLevel,
    val score: Int,            // 0 ~ 95
    val timestamp: Long
)

enum class MagneticLevel {
    NORMAL,         // < 5 uT
    INTEREST,       // 5 ~ 15 uT
    CAUTION,        // 15 ~ 30 uT
    SUSPECT,        // 30 ~ 50 uT
    HIGH_SUSPECT    // > 50 uT
}

enum class EmfSensitivity(val thresholdUt: Float) {
    SENSITIVE(3f),
    NORMAL(8f),
    STABLE(20f)
}
```

### 3.5 ReportRepository

```kotlin
interface ReportRepository {

    /** 리포트 저장 */
    suspend fun saveReport(result: ScanResult): Long

    /** 리포트 조회 (단건) */
    suspend fun getReport(id: Long): ScanReport?

    /** 리포트 목록 조회 */
    fun getAllReports(): Flow<List<ScanReport>>

    /** 리포트 삭제 */
    suspend fun deleteReport(id: Long)

    /** 오래된 리포트 정리 (무료: 10건 초과분 삭제) */
    suspend fun cleanupOldReports(keepCount: Int = 10)

    /** 리포트 수 조회 */
    suspend fun getReportCount(): Int

    /** PDF 내보내기 */
    suspend fun exportToPdf(reportId: Long): String  // 파일 경로 반환
}
```

---

## 4. ViewModel -> UseCase -> Repository 호출 체인

### 4.1 Quick Scan 호출 체인

```
HomeViewModel
  │
  │ onQuickScanClick()
  ▼
ScanViewModel
  │
  │ startQuickScan()
  │ viewModelScope.launch {
  │   runQuickScan()
  │     .onStart { _state.value = Preparing }
  │     .collect { state -> _state.value = state }
  │ }
  ▼
RunQuickScanUseCase
  │
  │ invoke(): Flow<QuickScanState>
  │
  │ 1. wifiScanRepo.isWifiConnected()
  │    -> false: emit(Error(WifiNotConnected))
  │
  │ 2. emit(Scanning(ARP_SCAN, 0.1))
  │    wifiScanRepo.getArpDevices()
  │
  │ 3. emit(Scanning(MDNS_DISCOVERY, 0.3))
  │    wifiScanRepo.discoverServices()
  │
  │ 4. emit(Scanning(OUI_MATCHING, 0.5))
  │    devices.forEach { wifiScanRepo.matchOui(it.mac) }
  │
  │ 5. emit(Scanning(PORT_SCAN, 0.7))
  │    suspiciousDevices.forEach { wifiScanRepo.scanPorts(it.ip) }
  │
  │ 6. emit(Scanning(RISK_CALCULATION, 0.9))
  │    calculateRisk(layer1 = result, layer2 = null, layer3 = null)
  │
  │ 7. emit(Completed(scanResult))
  ▼
WifiScanRepositoryImpl
  │
  ├── WifiScanner.getArpTable()
  ├── WifiScanner.discoverMdns()
  ├── OuiDatabase.lookup(mac)
  └── PortScanner.scan(ip, ports)
```

### 4.2 Full Scan 호출 체인

```
ScanViewModel
  │
  │ startFullScan(config)
  │ viewModelScope.launch {
  │   runFullScan(config).collect { ... }
  │ }
  ▼
RunFullScanUseCase
  │
  │ Phase 1: Wi-Fi (위와 동일)
  │   └─ Layer1Result
  │
  │ Phase 2: 병렬 실행 (coroutineScope)
  │   ├─ async { lensDetectionRepo.startRetroReflectionScan() }
  │   │   └─ Stage A + Stage B -> Layer2Result
  │   └─ async { magneticRepo.calibrate() + startMeasuring() }
  │       └─ Layer3Result
  │
  │ Phase 3: 교차 검증
  │   └─ calculateRisk(layer1, layer2, layer3) -> ScanResult
  │
  ▼
  emit(Completed(scanResult))
```

---

## 5. 데이터 전달 객체(DTO) 정의

### 5.1 도메인 모델 (core DTO)

| 클래스 | 필드 | 타입 | 설명 |
|--------|------|------|------|
| **ScanResult** | mode | ScanMode | QUICK, FULL, LENS, IR, EMF |
| | overallScore | Int | 0~100 |
| | riskLevel | RiskLevel | SAFE, ATTENTION, CAUTION, DANGER, CRITICAL |
| | layer1 | Layer1Result? | Wi-Fi 결과 |
| | layer2 | Layer2Result? | 렌즈 결과 |
| | layer3 | Layer3Result? | EMF 결과 |
| | evidences | List<Evidence> | 근거 목록 |
| | timestamp | Long | 스캔 시각 (epoch ms) |
| | duration | Long | 소요 시간 (ms) |
| **NetworkDevice** | ip | String | 192.168.1.x |
| | mac | String | AA:BB:CC:DD:EE:FF |
| | vendor | String? | 제조사명 |
| | deviceType | DeviceType | IP_CAMERA, SMART_CAMERA, CONSUMER, UNKNOWN |
| | riskScore | Int | 0~100 |
| | openPorts | List<Int> | 개방 포트 |
| | evidence | List<String> | 위험 근거 |
| **LensPoint** | x | Float | 화면 좌표 X (0.0~1.0) |
| | y | Float | 화면 좌표 Y (0.0~1.0) |
| | size | Int | 포인트 크기 (pixel) |
| | circularity | Float | 원형도 (0.0~1.0) |
| | brightness | Float | 상대 밝기 |
| | duration | Long | 지속 시간 (ms) |
| | flashVerified | Boolean | 플래시 OFF 소실 확인 |
| | score | Int | 0~100 |
| **IrPoint** | x | Float | 화면 좌표 X |
| | y | Float | 화면 좌표 Y |
| | color | IrColor | PURPLE, WHITE |
| | duration | Long | 지속 시간 (ms) |
| | score | Int | 0~100 |
| **MagneticReading** | magnitude | Float | 총 자기장 강도 (uT) |
| | delta | Float | baseline 대비 변화량 |
| | level | MagneticLevel | NORMAL ~ HIGH_SUSPECT |
| | score | Int | 0~95 |

### 5.2 결과 래퍼 DTO

```kotlin
data class Layer1Result(
    val score: Int,                       // 0~100
    val devices: List<NetworkDevice>,
    val totalDevicesFound: Int,
    val suspiciousDevices: Int,
    val scanDuration: Long                // ms
)

data class Layer2Result(
    val score: Int,                       // 0~100
    val lensPoints: List<LensPoint>,      // Stage A
    val irPoints: List<IrPoint>,          // Stage B
    val stageAScore: Int,
    val stageBScore: Int,
    val framesAnalyzed: Long
)

data class Layer3Result(
    val score: Int,                       // 0~95
    val maxDelta: Float,                  // uT
    val avgDelta: Float,                  // uT
    val level: MagneticLevel,
    val calibration: CalibrationResult,
    val readings: List<MagneticReading>   // 최근 200개 (그래프용)
)

data class Evidence(
    val layer: Int,                       // 1, 2, 3
    val severity: EvidenceSeverity,       // LOW, MEDIUM, HIGH, CRITICAL
    val description: String,              // 사용자 표시용 문장
    val technicalDetail: String           // 기술 상세 (리포트용)
)
```

### 5.3 리포트 DTO

```kotlin
data class ScanReport(
    val id: Long,
    val scanResult: ScanResult,
    val location: String?,                // 사용자 입력 (선택)
    val memo: String?,                    // 사용자 메모 (선택)
    val createdAt: Long,
    val checklist: ChecklistResult?       // 체크리스트 결과 (선택)
)
```

---

## 6. Phase 2 외부 API 고려사항

### 6.1 Firebase 연동 (Phase 2~3)

| API | 용도 | 엔드포인트 패턴 |
|-----|------|---------------|
| OUI 업데이트 | 카메라 제조사 DB 갱신 | Firebase Remote Config |
| 오탐 패턴 수집 | 사용자 피드백 (익명) | Firestore: `false_positives/{id}` |
| 커뮤니티 맵 | 위험 장소 공유 | Firestore: `risk_locations/{id}` |
| 크래시 리포트 | 앱 안정성 모니터링 | Firebase Crashlytics |
| 앱 분석 | 사용 패턴 분석 | Firebase Analytics |

### 6.2 커뮤니티 맵 API (Phase 3)

```
POST /api/v1/reports
  Body: { location, riskLevel, scanSummary, anonymousId }
  Response: { reportId, status }

GET /api/v1/map?lat={lat}&lng={lng}&radius={km}
  Response: { reports: [{ location, riskLevel, count, lastReport }] }

POST /api/v1/reports/{id}/verify
  Body: { verified: Boolean, reason }
  Response: { verificationCount }
```

---

## 7. 에러 응답 형식

### 7.1 내부 에러 타입 계층

```kotlin
sealed class ScanError(
    val code: String,
    val userMessage: String,
    val technicalMessage: String
) {
    // 센서 에러 (E1xxx)
    class WifiNotConnected : ScanError(
        "E1001", "Wi-Fi에 연결되지 않았습니다", "WiFi state: DISCONNECTED"
    )
    class MagnetometerUnavailable : ScanError(
        "E1101", "자기장 센서를 사용할 수 없습니다", "TYPE_MAGNETIC_FIELD not found"
    )
    class CameraUnavailable : ScanError(
        "E1201", "카메라를 사용할 수 없습니다", "CameraX init failed"
    )

    // 권한 에러 (E3xxx)
    class LocationPermissionDenied : ScanError(
        "E3001", "위치 권한이 필요합니다", "ACCESS_FINE_LOCATION denied"
    )
    class CameraPermissionDenied : ScanError(
        "E3002", "카메라 권한이 필요합니다", "CAMERA permission denied"
    )

    // 시스템 에러 (E4xxx)
    class ScanTimeout(layer: String) : ScanError(
        "E4001", "스캔 시간이 초과되었습니다", "Timeout on layer: $layer"
    )
    class DatabaseError(cause: Throwable) : ScanError(
        "E4101", "데이터 저장에 실패했습니다", "Room error: ${cause.message}"
    )
}
```

### 7.2 에러 -> 사용자 메시지 매핑

| 에러 코드 | 기술 메시지 | 사용자 메시지 | 복구 행동 |
|----------|-----------|-------------|----------|
| E1001 | WiFi DISCONNECTED | "Wi-Fi에 연결해주세요" | Wi-Fi 설정 이동 버튼 |
| E1101 | Magnetometer not found | "이 기기는 자기장 센서를 지원하지 않습니다" | EMF 레이어 스킵 안내 |
| E1201 | CameraX init failed | "카메라 초기화에 실패했습니다. 다시 시도해주세요" | 재시도 버튼 |
| E3001 | Location denied | "Wi-Fi 스캔에 위치 권한이 필요합니다" | 권한 설정 이동 |
| E3002 | Camera denied | "렌즈 감지에 카메라 권한이 필요합니다" | 권한 설정 이동 |
| E4001 | Scan timeout | "스캔 시간이 초과되었습니다" | 부분 결과 표시 |
| E4101 | Room error | "결과 저장에 실패했습니다" | 재시도 버튼 |

> 상세 에러 처리 전략은 `08-error-handling.md`를 참조한다.

---

*본 문서는 project-plan.md v3.1 기반으로 작성되었으며, Phase 1 (Android MVP) 범위의 내부 API를 정의합니다.*
*Phase 2~3의 외부 API는 해당 시점에 별도 API 명세서로 작성합니다.*
