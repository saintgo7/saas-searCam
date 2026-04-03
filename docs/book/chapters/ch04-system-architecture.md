# Ch04: 시스템 아키텍처 설계 — 층을 나누고 방향을 정한다

> **이 장에서 배울 것**: 아키텍처는 설계도입니다. SearCam의 Clean Architecture 3계층 구조, 탐지 3중 레이어(Wi-Fi 50% + 렌즈 35% + EMF 15%), 의존성 역전 원칙, Hilt DI 설계, 그리고 실제 패키지 구조를 다이어그램과 함께 설명합니다.

---

## 도입

도시를 설계할 때 주거지역, 상업지역, 공업지역을 나눕니다. 공장이 주택가 한가운데 있으면 소음과 오염이 생깁니다. 가게가 없는 주거지역은 불편합니다. 구역을 나누고 도로로 연결하는 것, 그것이 도시 설계의 핵심입니다.

소프트웨어 아키텍처도 같습니다. "어떤 코드가 어디에 있어야 하는가"를 정하는 것이 아키텍처입니다. SearCam은 Clean Architecture를 채택하여 UI, 비즈니스 로직, 데이터를 엄격하게 분리했습니다.

---

## 4.1 전체 아키텍처 개요

### 3계층: Presentation → Domain → Data

```
┌──────────────────────────────────────────────────────────────┐
│                     Presentation Layer                        │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │HomeScreen│  │ScanScreen│  │LensScreen│  │ReportScr │    │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘    │
│       │              │              │              │          │
│  ┌────▼─────┐  ┌────▼─────┐  ┌────▼─────┐  ┌────▼─────┐    │
│  │HomeVM    │  │ScanVM    │  │LensVM    │  │ReportVM  │    │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘    │
├───────┼──────────────┼──────────────┼──────────────┼──────────┤
│       │         Domain Layer        │              │          │
│  ┌────▼──────────────▼──────────────▼──────────────▼─────┐   │
│  │                    UseCases                            │   │
│  │  RunQuickScan / RunFullScan / CalculateRisk / Export  │   │
│  └────────────────────────┬──────────────────────────────┘   │
│                           │                                   │
│  ┌────────────────────────▼──────────────────────────────┐   │
│  │               Repository Interfaces                    │   │
│  │  WifiScanRepo / MagneticRepo / LensDetectionRepo / ..│   │
│  └────────────────────────┬──────────────────────────────┘   │
├───────────────────────────┼───────────────────────────────────┤
│                      Data Layer                               │
│  ┌────────────────────────▼──────────────────────────────┐   │
│  │              Repository Implementations                │   │
│  └───┬───────────┬───────────┬───────────┬───────────────┘   │
│      │           │           │           │                    │
│  ┌───▼───┐  ┌───▼───┐  ┌───▼───┐  ┌───▼───┐                │
│  │Sensor │  │Analysis│  │ Local │  │  PDF  │                │
│  │Module │  │Module  │  │  DB   │  │Module │                │
│  └───────┘  └────────┘  └───────┘  └───────┘                │
└──────────────────────────────────────────────────────────────┘
```

**핵심 규칙: 의존성은 항상 아래 방향**

```
Presentation → Domain ← Data
        (의존성 방향)
```

Presentation은 Domain을 알지만, Domain은 Presentation을 모릅니다. Data는 Domain의 인터페이스를 구현하지만, Domain은 Data의 구체적인 구현을 모릅니다. 이것이 의존성 역전 원칙(DIP)입니다.

---

## 4.2 Presentation 계층 — UI와 상태 관리

### 단방향 데이터 흐름

Presentation 계층의 규칙은 하나입니다: Screen은 ViewModel만 알고, ViewModel은 UseCase만 압니다.

```
사용자 이벤트 (버튼 탭)
    │ Event
    ▼
ViewModel
    │ suspend fun / Flow
    ▼
UseCase (Domain)
    │ StateFlow
    ▼
ViewModel
    │ UiState
    ▼
Screen (Composable)
    │ 재구성
    ▼
사용자 화면 업데이트
```

**ScanViewModel 예시:**

```kotlin
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val runQuickScanUseCase: RunQuickScanUseCase,
    private val runFullScanUseCase: RunFullScanUseCase
) : ViewModel() {

    // UI 상태: sealed class로 모든 가능한 상태를 명시
    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    fun startQuickScan() {
        viewModelScope.launch {
            _uiState.value = ScanUiState.Scanning(progress = 0f)
            try {
                val result = runQuickScanUseCase()
                _uiState.value = ScanUiState.Complete(result)
            } catch (e: ScanException) {
                _uiState.value = ScanUiState.Error(e.message ?: "스캔 실패")
            }
        }
    }
}

// UI 상태 정의: 모든 화면 상태를 열거
sealed class ScanUiState {
    object Idle : ScanUiState()
    data class Scanning(val progress: Float, val currentLayer: String = "") : ScanUiState()
    data class Complete(val result: ScanResult) : ScanUiState()
    data class Error(val message: String) : ScanUiState()
}
```

**Screen 예시:**

```kotlin
@Composable
fun QuickScanScreen(
    viewModel: ScanViewModel = hiltViewModel(),
    onNavigateToResult: (ScanResult) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 화면은 상태에 따라 다른 UI를 보여줌 — 분기가 명확하고 단순
    when (val state = uiState) {
        is ScanUiState.Idle ->
            IdleContent(onScanClick = viewModel::startQuickScan)

        is ScanUiState.Scanning ->
            ScanningContent(
                progress = state.progress,
                currentLayer = state.currentLayer
            )

        is ScanUiState.Complete -> {
            LaunchedEffect(state.result) {
                onNavigateToResult(state.result)
            }
        }

        is ScanUiState.Error ->
            ErrorContent(message = state.message, onRetry = viewModel::startQuickScan)
    }
}
```

### Presentation 계층 구성요소

| 구성요소 | 책임 | 의존 대상 |
|---------|------|----------|
| Screen (Composable) | UI 렌더링, 사용자 입력 수신 | ViewModel |
| ViewModel | UI 상태 관리, UseCase 호출, Lifecycle 처리 | UseCase (Domain) |
| Navigation | 화면 전환 로직 | NavHost |
| Components | 재사용 UI 컴포넌트 (RiskGauge, RiskBadge) | 없음 |

---

## 4.3 Domain 계층 — 비즈니스 로직의 집

### Domain은 Android를 모른다

Domain 계층의 가장 중요한 규칙: Android 프레임워크 import가 없어야 합니다.

```kotlin
// 올바른 Domain 모델: 순수 Kotlin
data class ScanResult(
    val id: String,
    val scanTime: Long,
    val networkLayer: NetworkLayerResult?,
    val lensLayer: LensLayerResult?,
    val emfLayer: EmfLayerResult?,
    val overallRisk: RiskLevel,
    val riskScore: Int,             // 0~100
    val crossValidationNote: String
) {
    // 비즈니스 로직을 모델에 캡슐화
    fun isHighRisk(): Boolean = riskScore >= 70
    fun hasMultipleLayerHits(): Boolean {
        var hits = 0
        if (networkLayer?.hasThreats == true) hits++
        if (lensLayer?.hasThreats == true) hits++
        if (emfLayer?.hasThreats == true) hits++
        return hits >= 2
    }
}

// Android import가 없음 — 순수 Kotlin/Java 환경에서도 동작
enum class RiskLevel(val score: Int, val label: String) {
    SAFE(0, "안전"),
    LOW(20, "낮음"),
    MEDIUM(50, "주의"),
    HIGH(70, "위험"),
    CRITICAL(90, "강력 의심")
}
```

**Repository 인터페이스:**

```kotlin
// Domain이 정의한 계약 — Data 계층이 이것을 구현해야 함
interface WifiScanRepository {
    suspend fun scan(): NetworkLayerResult
    fun isConnectedToWifi(): Boolean
}

interface MagneticRepository {
    fun isAvailable(): Boolean
    suspend fun calibrate(): MagneticBaseline
    fun observeField(): Flow<MagneticReading>
}

interface LensDetectionRepository {
    suspend fun startDetection(): Flow<List<LensPoint>>
    suspend fun stopDetection()
}

interface ReportRepository {
    suspend fun save(result: ScanResult): Long
    fun getRecent(limit: Int): Flow<List<ScanResult>>
    fun getByRiskLevel(level: RiskLevel): Flow<List<ScanResult>>
    suspend fun delete(id: String)
}
```

**UseCase:**

```kotlin
// 단일 책임: Quick Scan 오케스트레이션만 담당
class RunQuickScanUseCase @Inject constructor(
    private val wifiRepo: WifiScanRepository,
    private val emfRepo: MagneticRepository,
    private val calculateRisk: CalculateRiskUseCase,
    private val reportRepo: ReportRepository
) {
    suspend operator fun invoke(): ScanResult {
        // Wi-Fi 연결 여부에 따라 레이어 동적 활성화
        val networkResult = if (wifiRepo.isConnectedToWifi()) {
            wifiRepo.scan()
        } else {
            null  // Layer 1 비활성화
        }

        val emfResult = if (emfRepo.isAvailable()) {
            val baseline = emfRepo.calibrate()
            emfRepo.observeField()
                .take(60)            // 3초간 (20Hz × 3초 = 60 샘플)
                .toList()
                .let { readings -> EmfLayerResult(readings, baseline) }
        } else {
            null  // Layer 3 비활성화
        }

        val result = calculateRisk(networkResult, null, emfResult)
        reportRepo.save(result)
        return result
    }
}
```

---

## 4.4 탐지 3중 레이어 — 핵심 알고리즘 설계

### 단일 센서의 한계: 왜 3중 레이어인가

의사의 진단과 비슷합니다. 체온만 재서 진단하는 의사와, 체온 + 혈압 + 혈액 검사를 종합하는 의사 중 어느 쪽을 신뢰하겠습니까?

SearCam은 3가지 독립적인 방법으로 몰래카메라를 탐지하고, 그 결과를 교차 검증합니다.

```
탐지 3중 레이어 구조:

  [Layer 1: Wi-Fi 스캔]          가중치: 50%
      ARP 조회 → mDNS → SSDP → OUI 매칭 → 포트 스캔
      탐지 대상: 동일 네트워크에 연결된 카메라

  [Layer 2: 렌즈 감지]           가중치: 35%
      플래시 ON → 프레임 캡처 → 고휘도 추출 → 원형도 검사
      → 플래시 토글 동적 검증
      탐지 대상: 렌즈가 노출된 카메라 (전원 무관)

  [Layer 3: EMF 감지]            가중치: 15%
      캘리브레이션 → 3축 자기장 측정 → 노이즈 필터
      → baseline 대비 변화량 계산
      탐지 대상: 전원 공급 중인 전자기기

       │              │              │
       ▼              ▼              ▼
  ┌─────────────────────────────────────────┐
  │         교차 검증 엔진 (CrossValidator)  │
  │                                         │
  │  단일 양성: 개별 가중치 적용             │
  │  복수 양성: 보정 계수 상향 적용          │
  │  EMF 단독: "가전제품 가능성" 안내        │
  └──────────────────┬──────────────────────┘
                     │
                     ▼
              종합 위험도 0~100
```

### 가중치 설계 이유

| 레이어 | 가중치 | 이유 |
|--------|--------|------|
| Layer 1 (Wi-Fi) | 50% | 가장 정확. MAC OUI + 포트로 카메라 제조사 확인 가능 |
| Layer 2 (렌즈) | 35% | 전원 OFF 카메라도 탐지. 단, 환경광/거울에 오탐 가능 |
| Layer 3 (EMF) | 15% | 보조 역할. 가전제품과 구별 어려움 — 단독 신뢰도 낮음 |

**오프라인 모드의 가중치 재배분:**

```kotlin
// 사용 불가 레이어는 가중치를 다른 레이어로 재배분
data class LayerWeights(
    val wifi: Float,
    val lens: Float,
    val emf: Float
) {
    companion object {
        // 풀 기능
        val FULL = LayerWeights(0.50f, 0.35f, 0.15f)
        // Wi-Fi 없음
        val NO_WIFI = LayerWeights(0f, 0.75f, 0.25f)
        // Wi-Fi + EMF 없음 (렌즈만)
        val LENS_ONLY = LayerWeights(0f, 1.0f, 0f)
        // Wi-Fi만 (렌즈/EMF 없음)
        val WIFI_ONLY = LayerWeights(1.0f, 0f, 0f)

        fun calculate(
            hasWifi: Boolean,
            hasLens: Boolean,
            hasEmf: Boolean
        ): LayerWeights {
            val base = listOf(
                hasWifi to 0.50f,
                hasLens to 0.35f,
                hasEmf to 0.15f
            )
            val total = base.filter { it.first }.sumOf { it.second.toDouble() }.toFloat()
            return LayerWeights(
                wifi  = if (hasWifi)  0.50f / total else 0f,
                lens  = if (hasLens)  0.35f / total else 0f,
                emf   = if (hasEmf)   0.15f / total else 0f
            )
        }
    }
}
```

### 교차 검증 알고리즘

```kotlin
class CrossValidator @Inject constructor() {

    fun validate(
        networkResult: NetworkLayerResult?,
        lensResult: LensLayerResult?,
        emfResult: EmfLayerResult?,
        weights: LayerWeights
    ): CrossValidationResult {

        // 각 레이어의 위험 점수 계산 (0~100)
        val wifiScore  = networkResult?.riskScore ?: 0
        val lensScore  = lensResult?.riskScore ?: 0
        val emfScore   = emfResult?.riskScore ?: 0

        // 가중치 적용 기본 점수
        val baseScore = (wifiScore * weights.wifi +
                        lensScore * weights.lens +
                        emfScore  * weights.emf).toInt()

        // 교차 검증 보정: 복수 레이어 양성 시 신뢰도 상향
        val activeHits = listOf(
            networkResult?.hasThreats == true,
            lensResult?.hasThreats == true,
            emfResult?.hasThreats == true
        ).count { it }

        val correctedScore = when (activeHits) {
            0 -> baseScore
            1 -> baseScore                          // 단일: 보정 없음
            2 -> minOf(100, (baseScore * 1.3f).toInt())  // 복수: 30% 상향
            else -> minOf(100, (baseScore * 1.5f).toInt()) // 전체: 50% 상향
        }

        // EMF 단독 양성: 낮은 신뢰도 안내
        val note = when {
            activeHits == 1 && emfResult?.hasThreats == true ->
                "자기장 변화가 감지되었습니다. 주변 가전제품이나 금속으로 인한 반응일 가능성이 있습니다."
            activeHits >= 2 ->
                "복수의 탐지 방식에서 이상이 확인되어 신뢰도가 높습니다."
            else -> ""
        }

        return CrossValidationResult(
            finalScore = correctedScore,
            riskLevel = RiskLevel.fromScore(correctedScore),
            activeLayerCount = activeHits,
            note = note
        )
    }
}
```

---

## 4.5 의존성 역전 원칙 — Domain이 Data를 모르는 이유

### 인터페이스가 방향을 역전시킨다

일반적인 코드에서 상위 레이어가 하위 레이어를 직접 사용합니다. 그러면 상위 레이어가 하위 레이어의 변화에 종속됩니다.

```
일반적인 방향:
  ScanViewModel → WifiScanRepositoryImpl → WifiScanner → Android WifiManager

문제: WifiScanner를 바꾸면 RepositoryImpl도 바꿔야 함.
     테스트할 때 실제 Wi-Fi 하드웨어가 필요함.
```

의존성 역전(DIP)은 인터페이스로 이 방향을 뒤집습니다.

```
DIP 적용 후:
  ScanViewModel → RunQuickScanUseCase (Domain)
                        ↓ (인터페이스 사용)
                  WifiScanRepository (interface, Domain)
                        ↑ (구현체 제공)
               WifiScanRepositoryImpl (Data)
                        ↓
                  WifiScanner → Android WifiManager

Domain은 WifiScanner의 존재조차 모름.
테스트 시 FakeWifiScanRepository를 주입하면 됨.
```

**테스트에서의 효과:**

```kotlin
// 실제 앱: Hilt가 실제 구현체를 주입
@Binds
abstract fun bindWifiRepo(impl: WifiScanRepositoryImpl): WifiScanRepository

// 테스트: Fake 구현체를 주입 — Wi-Fi 하드웨어 없이 테스트 가능
class FakeWifiScanRepository : WifiScanRepository {
    var shouldReturnHighRisk = false

    override suspend fun scan(): NetworkLayerResult {
        return if (shouldReturnHighRisk) {
            NetworkLayerResult.createHighRisk(listOf(
                NetworkDevice(mac = "28:57:BE:FF:01:02", manufacturer = "Hikvision", riskScore = 85)
            ))
        } else {
            NetworkLayerResult.createSafe()
        }
    }

    override fun isConnectedToWifi(): Boolean = true
}

// UseCase 단위 테스트: 실제 Wi-Fi 없이 로직 검증
@Test
fun `should return high risk when network layer finds camera`() = runTest {
    val fakeWifiRepo = FakeWifiScanRepository().apply { shouldReturnHighRisk = true }
    val fakeEmfRepo = FakeMagneticRepository()

    val useCase = RunQuickScanUseCase(fakeWifiRepo, fakeEmfRepo, CalculateRiskUseCase())

    val result = useCase()

    assert(result.isHighRisk())
    assert(result.riskScore >= 70)
}
```

---

## 4.6 Hilt DI 설계 — 의존성의 지도

### DI 모듈 구조

SearCam의 DI 모듈은 책임별로 분리됩니다.

```
di/
├── AppModule.kt          # 앱 전역 (Context, SharedPreferences)
├── SensorModule.kt       # 센서 제공 (WifiScanner, MagneticSensor, LensDetector)
├── DatabaseModule.kt     # Room DB + DAO
├── AnalysisModule.kt     # CrossValidator, RiskCalculator, OuiDatabase
└── RepositoryModule.kt   # 인터페이스 ↔ 구현체 바인딩
```

**Scope 설계:**

| 컴포넌트 | Scope | 이유 |
|---------|-------|------|
| WifiScanner | Singleton | 하나의 인스턴스로 충분 |
| MagneticSensor | Singleton | SensorManager 공유 |
| LensDetector | Singleton | CameraX ProcessCameraProvider 공유 |
| ScanViewModel | ViewModel | Compose의 HiltViewModel 사용 |
| RunQuickScanUseCase | Singleton | 상태 없음, 재사용 가능 |
| AppDatabase | Singleton | DB 연결은 하나여야 함 |

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object SensorModule {

    @Provides
    @Singleton
    fun provideWifiScanner(
        @ApplicationContext context: Context
    ): WifiScanner = WifiScanner(
        context = context,
        wifiManager = context.getSystemService(WifiManager::class.java),
        nsdManager = context.getSystemService(NsdManager::class.java)
    )

    @Provides
    @Singleton
    fun provideMagneticSensor(
        @ApplicationContext context: Context
    ): MagneticSensor {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        // 자력계 없는 기기 처리
        return MagneticSensor(sensorManager, magnetometer)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindWifiScanRepository(
        impl: WifiScanRepositoryImpl
    ): WifiScanRepository

    @Binds
    abstract fun bindMagneticRepository(
        impl: MagneticRepositoryImpl
    ): MagneticRepository

    @Binds
    abstract fun bindLensDetectionRepository(
        impl: LensDetectionRepositoryImpl
    ): LensDetectionRepository

    @Binds
    abstract fun bindReportRepository(
        impl: ReportRepositoryImpl
    ): ReportRepository
}
```

---

## 4.7 패키지 구조 — 파일의 주소

### 기능별 vs 레이어별 패키징

두 가지 패키징 전략이 있습니다.

```
레이어별 패키징 (Layer-first):          기능별 패키징 (Feature-first):
  ui/                                     scan/
    ScanScreen.kt                           ScanScreen.kt
    LensScreen.kt                           ScanViewModel.kt
  domain/                                   ScanUseCase.kt
    ScanUseCase.kt                        lens/
    LensUseCase.kt                          LensScreen.kt
  data/                                     LensViewModel.kt
    WifiScanner.kt                          LensUseCase.kt
    LensDetector.kt
```

SearCam은 **레이어별 패키징**을 선택했습니다. Clean Architecture의 레이어 경계를 패키지로 명확히 표현하기 때문입니다.

**전체 패키지 구조:**

```
com.searcam/
│
├── SearCamApp.kt                    # @HiltAndroidApp
├── MainActivity.kt                  # Single Activity, NavHost
│
├── di/                              # Hilt DI 모듈
│   ├── AppModule.kt
│   ├── SensorModule.kt
│   ├── DatabaseModule.kt
│   ├── AnalysisModule.kt
│   └── RepositoryModule.kt
│
├── domain/                          # 순수 Kotlin (Android 없음)
│   ├── model/
│   │   ├── ScanResult.kt
│   │   ├── RiskLevel.kt
│   │   ├── NetworkDevice.kt
│   │   ├── MagneticReading.kt
│   │   ├── LensPoint.kt
│   │   └── ScanReport.kt
│   ├── usecase/
│   │   ├── RunQuickScanUseCase.kt
│   │   ├── RunFullScanUseCase.kt
│   │   ├── RunLensFinderUseCase.kt
│   │   ├── CalculateRiskUseCase.kt
│   │   └── ExportReportUseCase.kt
│   └── repository/
│       ├── WifiScanRepository.kt       # 인터페이스만
│       ├── MagneticRepository.kt
│       ├── LensDetectionRepository.kt
│       └── ReportRepository.kt
│
├── data/                            # Android SDK 의존
│   ├── sensor/
│   │   ├── WifiScanner.kt
│   │   ├── MagneticSensor.kt
│   │   ├── LensDetector.kt
│   │   └── IrDetector.kt
│   ├── analysis/
│   │   ├── OuiDatabase.kt
│   │   ├── CrossValidator.kt
│   │   ├── RiskCalculator.kt
│   │   └── NoiseFilter.kt
│   ├── repository/
│   │   ├── WifiScanRepositoryImpl.kt
│   │   ├── MagneticRepositoryImpl.kt
│   │   ├── LensDetectionRepositoryImpl.kt
│   │   └── ReportRepositoryImpl.kt
│   ├── local/
│   │   ├── AppDatabase.kt
│   │   ├── dao/
│   │   │   ├── ReportDao.kt
│   │   │   └── DeviceDao.kt
│   │   └── entity/
│   │       ├── ScanReportEntity.kt
│   │       └── DeviceEntity.kt
│   └── pdf/
│       └── PdfGenerator.kt
│
├── ui/                              # Presentation 레이어
│   ├── navigation/
│   │   ├── SearCamNavHost.kt
│   │   └── Screen.kt
│   ├── home/
│   │   ├── HomeScreen.kt
│   │   └── HomeViewModel.kt
│   ├── scan/
│   │   ├── QuickScanScreen.kt
│   │   ├── FullScanScreen.kt
│   │   ├── ScanResultScreen.kt
│   │   └── ScanViewModel.kt
│   ├── lens/
│   │   ├── LensFinderScreen.kt
│   │   └── LensViewModel.kt
│   ├── magnetic/
│   │   ├── MagneticScreen.kt
│   │   └── MagneticViewModel.kt
│   ├── report/
│   │   ├── ReportListScreen.kt
│   │   └── ReportViewModel.kt
│   ├── theme/
│   │   ├── Color.kt
│   │   ├── Type.kt
│   │   └── Theme.kt
│   └── components/
│       ├── RiskGauge.kt
│       ├── RiskBadge.kt
│       └── ScanProgress.kt
│
└── util/
    ├── SoundManager.kt
    ├── VibrationManager.kt
    └── PermissionHelper.kt
```

---

## 4.8 모듈 의존성 그래프

### 어떤 모듈이 누구를 아는가

```
                    ┌─────────┐
                    │  :app   │  (Application)
                    └────┬────┘
                         │
              ┌──────────┼──────────┐
              │          │          │
        ┌─────▼───┐ ┌───▼────┐ ┌──▼──────┐
        │:ui-scan │ │:ui-    │ │:ui-     │
        │         │ │report  │ │settings │
        └────┬────┘ └───┬────┘ └────┬────┘
             │          │           │
             └──────────┬───────────┘
                        │
                  ┌─────▼─────┐
                  │  :domain   │  (순수 Kotlin)
                  └─────┬─────┘
                        │ (인터페이스 구현)
                  ┌─────▼─────┐
                  │   :data    │
                  └──┬──┬──┬──┘
                     │  │  │
          ┌──────────┘  │  └────────────┐
    ┌─────▼──────┐  ┌───▼────┐ ┌───────▼──────┐
    │:sensor     │  │:analysis│ │:local-db     │
    │(WifiScanner│  │(OUI DB, │ │(Room, DAO,   │
    │ CameraX ..)│  │ CrossVal│ │ Entity)      │
    └────────────┘  └─────────┘ └──────────────┘
```

**의존성 규칙:**

| 모듈 | 알아야 하는 것 | 알면 안 되는 것 |
|------|--------------|----------------|
| :domain | 없음 (순수 정책 레이어) | 모든 구현 세부사항 |
| :data | :domain (인터페이스만) | :ui, :app |
| :ui-scan | :domain (UseCase, Model) | :data 구현체 |
| :app | 모든 모듈 (조립자) | - |

---

## 4.9 데이터 흐름: 스캔 시작부터 결과까지

사용자가 "Quick Scan" 버튼을 탭했을 때 무슨 일이 일어나는지 추적합니다.

```
1. 사용자: "Quick Scan" 버튼 탭
           │
2. Screen: viewModel.startQuickScan() 호출
           │
3. ViewModel: viewModelScope.launch {
                _uiState = Scanning
                result = runQuickScanUseCase()
              }
           │
4. UseCase: val networkResult = wifiRepo.scan()   // 병렬
            val emfResult = emfRepo.measure()      // 병렬
           │
5. WifiScanner:
   a. /proc/net/arp 읽기 → IP-MAC 테이블
   b. mDNS NsdManager → 서비스 목록
   c. SSDP M-SEARCH → UPnP 기기
   d. OUI DB 매칭 → 제조사 확인
   e. 의심 기기만 TCP 포트 스캔 (554, 8080, 8888)
   f. NetworkLayerResult 반환
           │
6. MagneticSensor:
   a. 3초 캘리브레이션 (60 샘플)
   b. baseline + noise_floor 계산
   c. 3축 자기장 관찰 (20Hz)
   d. EmfLayerResult 반환
           │
7. CalculateRiskUseCase:
   a. 가중치 계산 (Wi-Fi 있으면 50%, 없으면 0%)
   b. 각 레이어 점수 × 가중치
   c. 교차 검증 보정 (복수 양성 시 상향)
   d. ScanResult 생성
           │
8. ReportRepository:
   a. ScanResult → ScanReportEntity 변환
   b. Room DB에 저장
           │
9. UseCase: ScanResult 반환
           │
10. ViewModel: _uiState = Complete(result)
           │
11. Screen: Result 화면으로 네비게이션
           │
12. 사용자: 위험도, 발견 기기 목록, 근거 확인
```

전체 과정이 30초 이내에 완료됩니다.

---

## 4.10 아키텍처 체크리스트

새 기능을 추가하기 전에 확인합니다.

- [ ] 새 기능이 어느 레이어에 속하는가?
- [ ] Domain 레이어에 Android import를 추가하려 한다면 — 멈추고 재설계
- [ ] UI가 Repository 구현체를 직접 참조한다면 — 멈추고 재설계
- [ ] UseCase가 10개 이상의 의존성을 갖는다면 — 분리 검토
- [ ] 테스트 시 실제 하드웨어(Wi-Fi, 카메라)가 필요하다면 — Fake 인터페이스 도입
- [ ] 새 Repository를 추가했다면 — DI 모듈에 바인딩 추가

---

## 마무리

아키텍처는 완성하는 것이 아니라 지키는 것입니다. SearCam의 Clean Architecture 구조는 코드를 한 번 짜고 끝나는 것이 아니라, 새 기능을 추가할 때마다 "이 코드가 올바른 레이어에 있는가?"를 자문하는 습관입니다.

3계층 분리, 의존성 역전, 탐지 레이어 가중치 설계 — 이 모든 결정이 결국 하나의 목표를 향합니다: **30초 안에 믿을 수 있는 결과를 보여주는 앱**.

다음 장에서는 이 아키텍처 위에 실제 코드를 쌓기 시작합니다. 프로젝트 초기 설정 — Gradle Version Catalog, Hilt Application, 권한 전략 — 부터 시작합니다.
