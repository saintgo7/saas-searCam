# Ch03: 기술 요구사항과 아키텍처 결정 — 도구를 고를 때 이유가 있어야 한다

> **이 장에서 배울 것**: 기술 스택은 유행을 따르는 것이 아닙니다. SearCam이 Kotlin, Jetpack Compose, Hilt, Room, CameraX를 선택한 이유, Android API 26+를 최소 버전으로 정한 이유, Clean Architecture를 선택한 이유를 하나씩 논증합니다.

---

## 도입

목수는 못을 박기 위해 드라이버를 쓰지 않습니다. 당연한 말이지만, 소프트웨어 세계에서는 이 실수가 자주 일어납니다. "요즘 다들 쓰니까"라는 이유로 기술을 선택하고, 나중에 그 선택이 프로젝트 내내 발목을 잡습니다.

SearCam의 기술 스택을 결정할 때 모든 선택에 "왜?"를 물었습니다. 이 장에서는 그 대화를 재현합니다.

---

## 3.1 개발 언어: Kotlin을 선택한 이유

### Java 대신 Kotlin — 2024년 기준 논쟁의 여지가 없다

결론부터: Android 앱에서 Kotlin은 사실상 표준입니다. Google이 2017년 Android 공식 언어로 채택한 이후, 새 Jetpack 라이브러리는 Kotlin 우선으로 설계됩니다.

하지만 "다들 쓰니까"가 아니라 기술적 이유를 살펴봅니다.

**Null Safety**

SearCam에서 가장 자주 발생할 수 있는 버그는 무엇일까요? 센서 데이터입니다. 자력계가 없는 기기, 플래시가 없는 기기, Wi-Fi가 꺼진 기기 — 언제나 null이 가능합니다.

```kotlin
// Java 방식: NullPointerException 지뢰밭
SensorManager sensorManager = context.getSystemService(Context.SENSOR_SERVICE);
Sensor magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
// magnetometer가 null이면? 바로 크래시

// Kotlin 방식: 컴파일 타임에 null 처리 강제
val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    ?: run {
        // null인 경우 명시적으로 처리
        notifyLayerUnavailable(DetectionLayer.EMF)
        return@run null
    }
```

**Coroutines + Flow**

SearCam의 핵심은 비동기 처리입니다. Wi-Fi 스캔, 카메라 프레임 분석, 자기장 측정 — 모두 동시에 일어납니다.

```kotlin
// Kotlin Coroutines로 비동기를 동기처럼 작성
suspend fun runQuickScan(): ScanResult {
    return coroutineScope {
        val networkResult = async { scanNetwork() }
        val emfResult = async { measureEMF() }

        ScanResult(
            network = networkResult.await(),
            emf = emfResult.await()
        )
    }
}

// Kotlin Flow로 실시간 데이터 스트리밍
fun observeMagneticField(): Flow<MagneticReading> = flow {
    sensorManager.registerListener(...)
    // 20Hz로 지속 방출
}
```

Kotlin 없이 이 코드를 Java로 작성하면 스레드 관리, 콜백 지옥, 메모리 누수 위험이 몇 배로 늘어납니다.

**Data Class**

```kotlin
// 보일러플레이트 없이 불변 데이터 모델
data class MagneticReading(
    val x: Float,
    val y: Float,
    val z: Float,
    val magnitude: Float,
    val timestamp: Long
) {
    fun delta(baseline: MagneticReading): Float =
        abs(magnitude - baseline.magnitude)
}
```

Java라면 getter, setter, equals(), hashCode(), toString()을 직접 작성해야 합니다.

---

## 3.2 UI 프레임워크: Jetpack Compose를 선택한 이유

### XML vs Compose — 레거시냐 미래냐

2024년 기준, 신규 Android 프로젝트에서 XML View System을 선택하는 것은 새 건물을 지으면서 1980년대 설계도를 쓰는 것과 같습니다.

**Compose가 SearCam에 적합한 이유:**

첫째, **실시간 데이터 표시**에 강합니다. SearCam의 핵심 화면은 자기장 실시간 그래프, 렌즈 감지 포인트 오버레이, 스캔 진행률 — 모두 매 프레임 변하는 데이터입니다.

```kotlin
// Compose: 상태가 바뀌면 UI가 자동으로 재구성됨
@Composable
fun RiskGauge(riskScore: Int) {
    val animatedScore by animateIntAsState(
        targetValue = riskScore,
        animationSpec = tween(durationMillis = 500)
    )

    Canvas(modifier = Modifier.size(200.dp)) {
        // 위험도에 따라 색상과 각도가 자동으로 변함
        drawArc(
            color = riskColor(animatedScore),
            startAngle = 135f,
            sweepAngle = (animatedScore / 100f) * 270f,
            useCenter = false
        )
    }
}
```

XML로 같은 것을 구현하면 Canvas 커스텀 뷰, 애니메이션 처리, 상태 관리를 모두 수동으로 연결해야 합니다.

둘째, **선언형 패러다임**이 상태 관리를 단순화합니다.

```kotlin
// ViewModel의 상태가 변하면 Compose가 알아서 재구성
@Composable
fun ScanScreen(viewModel: ScanViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (uiState) {
        is ScanUiState.Idle -> IdleContent(onStartScan = viewModel::startScan)
        is ScanUiState.Scanning -> ScanningContent(progress = uiState.progress)
        is ScanUiState.Complete -> ResultContent(result = uiState.result)
        is ScanUiState.Error -> ErrorContent(message = uiState.message)
    }
}
```

XML 방식이라면 각 상태마다 View의 visibility를 수동으로 토글해야 합니다. 상태가 4개면 분기가 기하급수로 늘어납니다.

셋째, **Google의 공식 지원 방향**입니다. 새 Jetpack 컴포넌트는 Compose 우선으로 설계되고, Material3도 Compose에서 가장 잘 동작합니다.

---

## 3.3 의존성 주입: Hilt를 선택한 이유

### DI가 필요한 이유를 먼저 이해한다

의존성 주입(Dependency Injection)은 커피 자판기와 바리스타의 차이와 같습니다. 자판기는 내부에 모든 재료가 고정되어 있어 교체가 불가능합니다. 바리스타는 외부에서 재료를 받아 음료를 만들기 때문에 원두를 바꿔도 됩니다.

SearCam에서 DI가 필요한 이유:

```kotlin
// DI 없이: WifiScanViewModel이 직접 의존성을 생성
class WifiScanViewModel : ViewModel() {
    private val scanner = WifiScanner(context) // context를 어떻게 가져옴?
    private val repository = WifiScanRepositoryImpl(scanner, OuiDatabase())
    // 테스트 시 실제 Wi-Fi 스캐너가 동작함 → 테스트 불가
}

// DI 있이: 외부에서 주입받음
@HiltViewModel
class WifiScanViewModel @Inject constructor(
    private val runQuickScanUseCase: RunQuickScanUseCase
) : ViewModel() {
    // 테스트 시 FakeQuickScanUseCase를 주입하면 됨 → 테스트 가능
}
```

**Hilt vs Koin vs Dagger**

| 항목 | Hilt | Koin | Dagger |
|------|------|------|--------|
| 컴파일 타임 검증 | O | X | O |
| 학습 난이도 | 중간 | 낮음 | 높음 |
| Android 통합 | 최상 (Google 공식) | 보통 | 좋음 |
| 보일러플레이트 | 낮음 | 매우 낮음 | 높음 |
| 성능 | 최상 | 런타임 오버헤드 | 최상 |

Koin이 편하지만, 런타임 의존성 해결은 컴파일 타임에 잡을 수 있는 오류를 앱 크래시로 바꿉니다. SearCam 같은 보안 앱은 배포 후 크래시가 용납되지 않습니다. Hilt가 정답입니다.

```kotlin
// Hilt 모듈 예시: 센서 바인딩
@Module
@InstallIn(SingletonComponent::class)
object SensorModule {

    @Provides
    @Singleton
    fun provideWifiScanner(
        @ApplicationContext context: Context
    ): WifiScanner = WifiScanner(context)

    @Provides
    @Singleton
    fun provideMagneticSensor(
        @ApplicationContext context: Context
    ): MagneticSensor = MagneticSensor(context)
}

// Repository 인터페이스와 구현체 바인딩
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindWifiScanRepository(
        impl: WifiScanRepositoryImpl
    ): WifiScanRepository
}
```

---

## 3.4 로컬 DB: Room을 선택한 이유

### SQLite를 직접 쓰지 않는 이유

SQLite 위에 Room을 사용하는 것은 원자재보다 가공품을 쓰는 것과 같습니다. Room이 제공하는 것:

- SQL 쿼리를 Kotlin 메서드로 변환 (컴파일 타임 검증)
- Flow 반환 지원 (실시간 데이터 변화 감지)
- Migration 지원 (버전 업그레이드 관리)

```kotlin
// Room DAO: 스캔 리포트 저장 및 조회
@Dao
interface ReportDao {

    @Insert
    suspend fun insert(report: ScanReportEntity): Long

    @Query("SELECT * FROM scan_reports ORDER BY scan_time DESC LIMIT :limit")
    fun getRecentReports(limit: Int = 10): Flow<List<ScanReportEntity>>

    @Query("SELECT * FROM scan_reports WHERE risk_level = :level ORDER BY scan_time DESC")
    fun getReportsByRiskLevel(level: String): Flow<List<ScanReportEntity>>

    @Delete
    suspend fun delete(report: ScanReportEntity)
}
```

Flow 반환형은 Compose와 완벽하게 연동됩니다. DB에 새 리포트가 저장되면 UI가 자동으로 업데이트됩니다.

**SearCam의 데이터 모델**

| 테이블 | 역할 | 관계 |
|--------|------|------|
| scan_reports | 스캔 결과 헤더 (날짜, 위험도, 위치) | 1:N |
| network_devices | 발견된 네트워크 기기 목록 | N:1 → scan_reports |
| risk_points | 렌즈/IR 의심 포인트 | N:1 → scan_reports |
| checklist_items | 육안 점검 체크리스트 항목 | N:1 → scan_reports |

---

## 3.5 카메라: CameraX를 선택한 이유

### Camera2 API vs CameraX

Camera2 API는 강력하지만 복잡합니다. CameraX는 Camera2를 추상화하여 개발자 친화적 API를 제공합니다.

SearCam에서 카메라가 하는 일:
1. **렌즈 감지**: 실시간 프레임을 분석하여 밝은 반사 포인트 추출
2. **IR 감지**: 전면 카메라로 IR LED 발광 분석

```kotlin
// CameraX ImageAnalysis: 실시간 프레임 처리
class LensDetector @Inject constructor(
    private val retroreflectionAnalyzer: RetroreflectionAnalyzer
) {
    fun startAnalysis(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ): Flow<List<LensPoint>> = callbackFlow {

        val imageAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(Dispatchers.Default.asExecutor()) { imageProxy ->
                    val points = retroreflectionAnalyzer.analyze(imageProxy)
                    trySend(points)
                    imageProxy.close()
                }
            }

        // Preview + ImageAnalysis 동시 실행
        val cameraProvider = ProcessCameraProvider.getInstance(context).await()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) },
            imageAnalyzer
        )

        awaitClose { cameraProvider.unbindAll() }
    }
}
```

CameraX의 핵심 장점: Lifecycle과 자동 연동됩니다. Activity/Fragment가 파괴되면 카메라가 자동으로 해제됩니다. Camera2로 직접 구현하면 이것을 수동으로 관리해야 합니다.

---

## 3.6 Android 버전 전략: API 26+를 선택한 이유

### 최소 버전 결정은 트레이드오프다

최소 지원 버전을 낮추면 더 많은 사용자를 커버하지만, 새 API를 쓰기 어려워집니다. 높이면 최신 기능을 쓸 수 있지만 일부 사용자를 잃습니다.

```
Android 버전별 시장 점유율 (2024 기준):
  API 26+ (Android 8.0+): ~95%의 기기 커버
  API 29+ (Android 10+): ~88%의 기기 커버
  API 33+ (Android 13+): ~60%의 기기 커버
```

SearCam이 API 26을 선택한 이유:

| 이유 | 설명 |
|------|------|
| 시장 커버리지 | 95%+ 기기 지원 (보급형 포함) |
| Wi-Fi 스캔 API | ACCESS_FINE_LOCATION 필수 (API 26+부터 강제) |
| CameraX 지원 | API 21+부터 지원, API 26에서 안정적 |
| Foreground Service | API 26부터 의무화 — 백그라운드 스캔 정책 |
| 타겟 사용자 | 보급형 기기 사용자 포함 (사회적 취약계층 포함) |

**보안 앱으로서의 의미**

몰래카메라 피해자는 비싼 최신 기기를 쓰지 않을 수도 있습니다. SearCam이 보안 도구로서 의미를 갖기 위해서는 보급형 기기에서도 동작해야 합니다. API 26 선택은 기술적 결정인 동시에 제품 철학의 반영입니다.

**API 버전별 조건부 코드 처리**

```kotlin
// API 레벨에 따른 조건부 처리
fun requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Android 13+ (API 33): POST_NOTIFICATIONS 런타임 권한 필요
        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    // Android 12 이하: 권한 불필요 (알림 자동 허용)
}

fun enableHighSamplingRate() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12+ (API 31): HIGH_SAMPLING_RATE_SENSORS 권한 선언 필요
        // 20Hz+ 자력계 샘플링을 위해 필요
    }
}
```

---

## 3.7 아키텍처: Clean Architecture를 선택한 이유

### 아키텍처는 "어떻게"가 아니라 "왜"다

Clean Architecture를 선택한 이유를 한 문장으로: **변화에 유연하게 대응하기 위해서.**

SearCam은 Phase 1에서 Android만 지원하지만, Phase 2에서 iOS로 확장합니다. Phase 3에서 클라우드 백엔드가 추가됩니다. 이 변화가 올 때 코드 전체를 다시 짜지 않으려면 처음부터 계층을 분리해야 합니다.

**레이어별 변화 시나리오:**

| 변화 | 영향 레이어 | Clean Architecture 덕분에 |
|------|-----------|--------------------------|
| Room → SQLite 직접 사용 | Data만 | Domain, UI 변경 없음 |
| Wi-Fi 스캔 → Bluetooth 추가 | Data만 | 새 Repository 구현 추가 |
| Compose → XML (가정) | UI만 | Domain, Data 변경 없음 |
| 오프라인 → 클라우드 동기화 | Data만 | Domain 인터페이스 유지 |
| 탐지 알고리즘 개선 | Domain + Data | UI 변경 없음 |

**Clean Architecture가 없다면:**

```kotlin
// 나쁜 예: UI가 DB에 직접 접근
@Composable
fun ScanResultScreen() {
    val database = AppDatabase.getInstance(LocalContext.current)
    val reports = database.reportDao().getAll() // UI가 Data 레이어를 직접 알고 있음
    // 나중에 DB를 바꾸면 화면 코드도 바꿔야 함
    // 테스트 시 실제 DB가 필요함
}

// 좋은 예: UI는 ViewModel만 알고, ViewModel은 UseCase만 알고
@Composable
fun ScanResultScreen(viewModel: ScanViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // DB가 바뀌어도 이 화면은 전혀 변하지 않음
}
```

---

## 3.8 비동기 처리: Coroutines + Flow를 선택한 이유

### 스마트폰의 메인 스레드는 UI 전용이다

Android의 황금률: 메인 스레드(UI 스레드)에서 블로킹 작업을 하면 ANR(앱 응답 없음) 다이얼로그가 뜹니다.

SearCam에서 메인 스레드에서 하면 안 되는 작업들:
- Wi-Fi ARP 테이블 읽기 (파일 I/O)
- TCP 포트 스캔 (네트워크)
- Room DB 쿼리 (디스크 I/O)
- 이미지 프레임 분석 (CPU 집약)

**Coroutines가 이것을 우아하게 해결합니다:**

```kotlin
class RunQuickScanUseCase @Inject constructor(
    private val wifiRepo: WifiScanRepository,
    private val magneticRepo: MagneticRepository,
    private val calculateRisk: CalculateRiskUseCase
) {
    suspend operator fun invoke(): ScanResult = withContext(Dispatchers.IO) {
        // IO 스레드에서 병렬 실행
        val networkDeferred = async { wifiRepo.scan() }
        val emfDeferred = async(Dispatchers.Default) {
            magneticRepo.calibrateAndMeasure(durationSeconds = 3)
        }

        val networkResult = networkDeferred.await()
        val emfResult = emfDeferred.await()

        calculateRisk(networkResult, emfResult)
    }
}
```

**Flow가 실시간 데이터를 처리합니다:**

```kotlin
// 자기장 센서 데이터를 20Hz로 Flow 방출
fun observeMagneticField(): Flow<MagneticReading> = callbackFlow {
    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            trySend(MagneticReading(event.values[0], event.values[1], event.values[2]))
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    sensorManager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_GAME)
    awaitClose { sensorManager.unregisterListener(listener) }
}

// ViewModel에서 Flow를 StateFlow로 변환
val magneticState = magneticRepo.observeMagneticField()
    .map { reading -> MagneticUiState(reading, riskLevel(reading)) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MagneticUiState.Loading)
```

---

## 3.9 기술 스택 최종 결정표

| 범주 | 선택 | 버전 | 선택 이유 |
|------|------|------|----------|
| 언어 | Kotlin | 2.0+ | Null Safety, Coroutines, Data Class |
| UI | Jetpack Compose | BOM 2024.x | 선언형, 실시간 상태 관리, 미래 표준 |
| DI | Hilt | 2.51+ | 컴파일 타임 검증, Android 공식 지원 |
| 로컬 DB | Room | 2.6.x | Flow 지원, 타입 안전 쿼리 |
| 카메라 | CameraX | 1.3.x | Lifecycle 자동 관리, 추상화 |
| 비동기 | Coroutines + Flow | 1.8.x | 구조적 동시성, 리액티브 스트림 |
| 아키텍처 | Clean Architecture + MVVM | - | 변화 유연성, 테스트 가능성 |
| 그래프 | MPAndroidChart | 3.1.x | 실시간 자기장 그래프 |
| 로깅 | Timber | 5.x | 릴리즈 빌드 자동 제거 |
| 최소 SDK | API 26 (Android 8.0) | - | 95%+ 기기 커버, 핵심 API 지원 |
| 타겟 SDK | API 34 (Android 14) | - | Play Store 정책, 최신 API |

---

## 마무리

기술 선택에는 정답이 없습니다. 하지만 이유 없는 선택은 있습니다. SearCam의 모든 기술 결정에는 "왜?"가 있었고, 그 이유는 제품의 요구사항에서 출발했습니다.

30초 안에 결과를 보여줘야 하니 비동기 처리가 필수고, 비동기 처리엔 Coroutines가 최선입니다. 실시간 센서 데이터를 표시해야 하니 Compose가 적합합니다. 보안 앱으로서 테스트 가능성이 중요하니 DI와 Clean Architecture가 필요합니다.

다음 장에서는 이 기술 스택으로 실제 시스템 아키텍처를 어떻게 설계했는지 — 패키지 구조, 의존성 방향, 탐지 레이어 설계 — 자세히 살펴봅니다.
