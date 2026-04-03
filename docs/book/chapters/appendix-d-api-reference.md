# Appendix D: 주요 API 레퍼런스

> **이 부록에서 배울 것**: SearCam의 주요 UseCase 시그니처, Repository 인터페이스 목록, Constants 주요 값을 빠르게 참조합니다.

---

## D.1 UseCase 레이어

UseCase는 Domain 레이어의 핵심입니다. 하나의 UseCase는 하나의 비즈니스 액션을 담당합니다.

### 스캔 관련 UseCase

| UseCase | 반환 타입 | 설명 |
|---------|---------|------|
| `StartQuickScanUseCase` | `Flow<ScanProgress>` | Quick Scan(Wi-Fi 스캔) 시작. 진행 상황을 Flow로 방출 |
| `StartFullScanUseCase` | `Flow<ScanProgress>` | Full Scan(3레이어 통합) 시작 |
| `StopScanUseCase` | `Unit` | 진행 중인 스캔 중단 |
| `GetScanResultUseCase` | `ScanResult` | 완료된 스캔 결과 조회 |

```kotlin
// StartQuickScanUseCase 시그니처
class StartQuickScanUseCase(
    private val wifiScanRepository: WifiScanRepository,
    private val ouiDatabase: OuiDatabase
) {
    operator fun invoke(): Flow<ScanProgress> = flow {
        emit(ScanProgress.Started)
        val devices = wifiScanRepository.scanDevices()
        devices.forEach { device ->
            val ouiEntry = ouiDatabase.lookup(device.macAddress)
            val riskScore = calculateRisk(device, ouiEntry)
            emit(ScanProgress.DeviceFound(device.copy(riskScore = riskScore)))
        }
        emit(ScanProgress.Completed)
    }.catch { e ->
        emit(ScanProgress.Error(e.toScanError()))
    }
}
```

### 렌즈 감지 UseCase

| UseCase | 반환 타입 | 설명 |
|---------|---------|------|
| `StartLensDetectionUseCase` | `Flow<LensDetectionState>` | Retroreflection 기반 렌즈 감지 시작 |
| `StartIrDetectionUseCase` | `Flow<IrDetectionState>` | IR 야간 카메라 감지 시작 |
| `StopCameraDetectionUseCase` | `Unit` | 카메라 기반 감지 중단 |

```kotlin
// StartLensDetectionUseCase 시그니처
class StartLensDetectionUseCase(
    private val cameraRepository: CameraRepository
) {
    operator fun invoke(): Flow<LensDetectionState> =
        cameraRepository.analyzeFrames()
            .map { frame -> LensAnalyzer.analyze(frame) }
            .distinctUntilChanged()
}
```

### 자기장 UseCase

| UseCase | 반환 타입 | 설명 |
|---------|---------|------|
| `StartEmfMonitoringUseCase` | `Flow<EmfReading>` | 자기장 실시간 모니터링 시작 |
| `CalibrateEmfUseCase` | `EmfBaseline` | 베이스라인 측정 (30샘플 평균) |
| `StopEmfMonitoringUseCase` | `Unit` | 자기장 모니터링 중단 |

### 리포트 UseCase

| UseCase | 반환 타입 | 설명 |
|---------|---------|------|
| `SaveReportUseCase` | `ReportId` | 스캔 결과를 로컬 DB에 저장 |
| `GetReportsUseCase` | `Flow<List<ScanReport>>` | 저장된 리포트 목록 조회 |
| `DeleteReportUseCase` | `Unit` | 리포트 삭제 |
| `ExportReportPdfUseCase` | `File` | 리포트를 PDF로 내보내기 (프리미엄) |

---

## D.2 Repository 인터페이스

Repository 인터페이스는 Domain 레이어에 정의되며, Data 레이어에서 구현합니다.

### WifiScanRepository

```kotlin
interface WifiScanRepository {
    // ARP 테이블에서 연결된 기기 목록 조회
    suspend fun scanDevices(): List<NetworkDevice>

    // 특정 기기의 카메라 포트 스캔
    suspend fun scanPorts(
        ipAddress: String,
        ports: List<Int> = Constants.CAMERA_PORTS
    ): PortScanResult

    // mDNS 서비스 탐색
    fun discoverServices(
        timeoutMs: Long = Constants.MDNS_DISCOVERY_TIMEOUT_MS
    ): Flow<MdnsService>

    // 현재 Wi-Fi 연결 상태 확인
    fun isConnected(): Boolean
}
```

### CameraRepository

```kotlin
interface CameraRepository {
    // 카메라 프레임을 Flow로 제공 (저장 없음)
    fun analyzeFrames(): Flow<ImageFrame>

    // 플래시 ON/OFF
    suspend fun setFlashEnabled(enabled: Boolean)

    // 전면/후면 카메라 전환
    suspend fun switchCamera(facing: CameraFacing)

    // 카메라 해제
    suspend fun release()
}
```

### EmfRepository

```kotlin
interface EmfRepository {
    // 자기장 센서 실시간 데이터 스트림
    fun getMagneticFieldFlow(): Flow<EmfReading>

    // 베이스라인 측정 (정지 상태에서 N샘플 평균)
    suspend fun measureBaseline(
        samples: Int = Constants.EMF_BASELINE_SAMPLES
    ): EmfBaseline

    // 센서 지원 여부 확인
    fun isSensorAvailable(): Boolean

    // 센서 정확도 조회
    fun getSensorAccuracy(): SensorAccuracy
}
```

### ReportRepository

```kotlin
interface ReportRepository {
    // 리포트 저장 (무료: 최근 10건 유지)
    suspend fun save(report: ScanReport): ReportId

    // 전체 리포트 목록 (최신순)
    fun getAll(): Flow<List<ScanReport>>

    // 리포트 상세 조회
    suspend fun getById(id: ReportId): ScanReport?

    // 리포트 삭제
    suspend fun delete(id: ReportId)

    // 저장된 리포트 수
    suspend fun count(): Int

    // 오래된 리포트 자동 삭제 (무료 사용자: 10건 초과 시)
    suspend fun pruneIfNeeded(maxCount: Int = 10)
}
```

### OuiRepository

```kotlin
interface OuiRepository {
    // MAC 주소로 OUI 엔트리 조회
    fun lookup(macAddress: String): OuiEntry?

    // 카메라 제조사 여부 (risk_weight >= 0.7)
    fun isCameraManufacturer(macAddress: String): Boolean

    // 안전 기기 여부 (risk_weight <= 0.1)
    fun isSafeDevice(macAddress: String): Boolean

    // DB 버전 조회
    fun getDatabaseVersion(): String

    // OTA 업데이트 (프리미엄)
    suspend fun updateFromRemote(): UpdateResult
}
```

---

## D.3 주요 Domain 모델

### ScanResult

```kotlin
data class ScanResult(
    val id: ReportId,
    val startedAt: Instant,
    val completedAt: Instant,
    val devices: List<DetectedDevice>,
    val lensDetections: List<LensDetection>,
    val emfReadings: EmfSummary,
    val riskScore: Int,           // 0~100
    val riskLevel: RiskLevel,     // SAFE / INTEREST / CAUTION / DANGER / CRITICAL
    val crossValidation: CrossValidationResult
)
```

### RiskLevel

```kotlin
enum class RiskLevel(val range: IntRange, val label: String) {
    SAFE(0..20, "안전"),
    INTEREST(21..40, "주의 관찰"),
    CAUTION(41..60, "주의"),
    DANGER(61..80, "위험"),
    CRITICAL(81..100, "매우 위험")
}
```

### DetectedDevice

```kotlin
data class DetectedDevice(
    val ipAddress: String,
    val macHash: String,          // SHA-256 해시 (원본 MAC 아님)
    val manufacturer: String?,    // OUI 조회 결과
    val openPorts: List<Int>,     // 열린 카메라 포트
    val mdnsName: String?,        // mDNS 서비스명
    val riskScore: Int,           // 0~100
    val riskWeight: Float         // OUI risk_weight
)
```

---

## D.4 Constants 주요 값

`Constants.kt`에 정의된 앱 전역 상수입니다.

### 네트워크 스캔

| 상수 | 값 | 설명 |
|------|-----|------|
| `CAMERA_PORTS` | `[554, 80, 8080, 8888, 3702, 1935, 443, 8443]` | 카메라 스트리밍에 자주 사용되는 포트 |
| `PORT_SCAN_TIMEOUT_MS` | `500L` | 포트 단일 연결 타임아웃 (ms) |
| `ARP_TABLE_PATH` | `"/proc/net/arp"` | ARP 테이블 파일 경로 |
| `MDNS_DISCOVERY_TIMEOUT_MS` | `5_000L` | mDNS 탐색 타임아웃 (ms) |
| `QUICK_SCAN_TIMEOUT_MS` | `30_000L` | Quick Scan 최대 시간 (30초) |
| `FULL_SCAN_TIMEOUT_MS` | `120_000L` | Full Scan 최대 시간 (120초) |

### EMF 센서

| 상수 | 값 | 설명 |
|------|-----|------|
| `EMF_POLLING_HZ` | `20` | 샘플링 주파수 (20Hz = 50ms 간격) |
| `EMF_MOVING_AVG_WINDOW` | `10` | 이동 평균 윈도우 크기 (노이즈 필터) |
| `EMF_ANOMALY_THRESHOLD_UT` | `50.0f` | 이상 감지 임계값 (마이크로테슬라) |
| `EMF_BASELINE_SAMPLES` | `30` | 베이스라인 측정 샘플 수 |

### 위험도 임계값

| 상수 | 값 | 대응 RiskLevel |
|------|-----|--------------|
| `RISK_SAFE_MAX` | `20` | SAFE (0~20) |
| `RISK_INTEREST_MAX` | `40` | INTEREST (21~40) |
| `RISK_CAUTION_MAX` | `60` | CAUTION (41~60) |
| `RISK_DANGER_MAX` | `80` | DANGER (61~80) |
| `RISK_CRITICAL_MIN` | `81` | CRITICAL (81~100) |

### 카메라 설정

| 상수 | 값 | 설명 |
|------|-----|------|
| `CAMERA_ANALYSIS_WIDTH` | `1280` | 분석 해상도 너비 (픽셀) |
| `CAMERA_ANALYSIS_HEIGHT` | `720` | 분석 해상도 높이 (픽셀) |
| `RETROREFLECTION_FRAME_COUNT` | `10` | 역반사 분석 프레임 수 (평균화) |
| `RETROREFLECTION_THRESHOLD` | `200` | 렌즈 의심 반사 강도 임계값 (0~255) |

### OUI 데이터베이스

| 상수 | 값 | 설명 |
|------|-----|------|
| `OUI_JSON_ASSET_PATH` | `"oui.json"` | assets 폴더 내 OUI JSON 파일 경로 |

---

## D.5 탐지 레이어 가중치

교차 검증 엔진에서 각 레이어의 기여도입니다.

| 레이어 | 가중치 | 설명 |
|--------|--------|------|
| Layer 1: Wi-Fi 스캔 | 50% | MAC OUI + 포트 스캔 + mDNS |
| Layer 2: 렌즈 감지 | 35% | Retroreflection + IR 감지 |
| Layer 3: 자기장 | 15% | EMF 이상 감지 |

```kotlin
// LayerType enum (각 레이어의 weight)
enum class LayerType(val weight: Float) {
    WIFI_SCAN(0.50f),
    LENS_DETECTION(0.35f),
    EMF_MONITORING(0.15f)
}

// 교차 검증 점수 계산
fun crossValidate(
    wifiScore: Int,
    lensScore: Int,
    emfScore: Int
): Int {
    return (wifiScore * LayerType.WIFI_SCAN.weight +
            lensScore * LayerType.LENS_DETECTION.weight +
            emfScore * LayerType.EMF_MONITORING.weight).toInt()
        .coerceIn(0, 100)
}
```
