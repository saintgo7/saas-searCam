# Ch09: 데이터 흐름 설계 — 센서에서 리포트까지 단방향

> **이 장에서 배울 것**: 자기장 센서의 원시값이 어떻게 화면의 위험도 게이지로 변환되는지 배웁니다. Kotlin Flow vs LiveData 선택 이유, StateFlow + SharedFlow 사용 패턴, ViewModel → UseCase → Repository → Sensor 체인, 그리고 4개 레이어를 동시에 실행하는 coroutineScope + async 패턴을 실제 코드로 설명합니다.

---

## 도입

강물을 떠올려보세요. 산에서 눈이 녹아 흘러내리면, 개울이 되고, 강이 되고, 바다로 흘러갑니다. 물은 항상 한 방향으로만 흐릅니다. 강물이 거꾸로 거슬러 흘러 산으로 올라가지는 않습니다.

SearCam의 데이터도 강물처럼 한 방향으로만 흐릅니다. 하드웨어 센서에서 출발한 원시 데이터가 분석기를 지나 도메인 모델이 되고, UseCase가 가공하고, ViewModel이 UI 상태로 변환하고, 마지막으로 Compose 화면이 그려냅니다. 화면에서 센서로 거슬러 올라가는 경로는 없습니다.

이것이 **단방향 데이터 흐름(Unidirectional Data Flow, UDF)**입니다. 데이터가 어디서 왔는지, 어디로 가는지 추적하기 쉽고, 버그가 발생했을 때 "어느 층에서 문제가 생겼는지" 격리하기 쉽습니다.

---

## 9.1 전체 데이터 흐름 개요

```
┌─────────────────────────────────────────────────────────────────┐
│                      SearCam 데이터 흐름                         │
│                                                                  │
│  [하드웨어 / 시스템]                                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐        │
│  │Wi-Fi ARP │  │ Camera   │  │Magnetic  │  │ IR       │        │
│  │ Sensor   │  │  (CameraX)│  │ Sensor   │  │ Camera   │        │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘        │
│       │              │              │              │              │
│  [data/ 계층 — Repository Impl, Sensor, Analyzer]               │
│       │              │              │              │              │
│  ┌────▼──────────────▼──────────────▼──────────────▼──────┐     │
│  │            Repository 인터페이스 (domain/)              │     │
│  │  WifiScanRepository  LensDetectionRepository            │     │
│  │  MagneticRepository  IrDetectionRepository              │     │
│  └────────────────────────┬────────────────────────────────┘     │
│                            │                                      │
│  [domain/ 계층 — UseCase]                                        │
│  ┌─────────────────────────▼──────────────────────────┐          │
│  │  RunQuickScanUseCase  RunFullScanUseCase            │          │
│  │  CalculateRiskUseCase                               │          │
│  └─────────────────────────┬──────────────────────────┘          │
│                             │                                     │
│  [ui/ 계층 — ViewModel]                                          │
│  ┌──────────────────────────▼─────────────────────────┐          │
│  │  ScanViewModel                                      │          │
│  │  StateFlow<ScanUiState>   SharedFlow<ScanUiEvent>  │          │
│  └──────────────────────────┬─────────────────────────┘          │
│                              │                                    │
│  [ui/ 계층 — Compose Screen]                                     │
│  ┌───────────────────────────▼────────────────────────┐          │
│  │  FullScanScreen, QuickScanScreen, ScanResultScreen │          │
│  └────────────────────────────────────────────────────┘          │
└─────────────────────────────────────────────────────────────────┘
```

화살표가 항상 아래를 향한다는 점을 주목하세요. 상위 계층(UI)은 하위 계층(Repository)을 알지만, 하위 계층은 상위 계층을 전혀 모릅니다. `MagneticSensor`는 `ScanViewModel`이 존재하는지 모릅니다. 이것이 Clean Architecture의 의존성 규칙입니다.

---

## 9.2 Flow vs LiveData — SearCam의 선택

Jetpack에는 비동기 데이터 스트림을 다루는 두 가지 도구가 있습니다: LiveData와 Kotlin Flow. SearCam은 전면적으로 **Kotlin Flow**를 선택했습니다. 이유를 비교표로 정리합니다.

| 기준 | LiveData | Kotlin Flow |
|------|---------|-------------|
| 생명주기 인식 | 내장 (Activity/Fragment 전용) | `repeatOnLifecycle()`로 해결 |
| Android 의존성 | 강함 (AAC 필수) | 없음 (pure Kotlin) |
| 백프레셔 처리 | 없음 | `buffer()`, `conflate()`, `sample()` |
| 변환 연산자 | 제한적 | `map`, `filter`, `combine`, `flatMap` 등 풍부 |
| 테스트 | 어려움 (Observer 필요) | `turbine` 라이브러리로 간결 |
| Cold vs Hot | 항상 Hot | Cold(`flow{}`), Hot(`StateFlow`, `SharedFlow`) |
| 멀티플렉싱 | 불가 | `combine()`, `merge()`, `zip()` |
| 코루틴 통합 | 부분적 | 완전 통합 |

SearCam에서 결정적인 이유는 두 가지입니다.

**첫째, Repository 계층이 Android 비의존성.** `domain/repository/` 인터페이스와 `data/repository/` 구현체는 순수 Kotlin입니다. 여기서 LiveData를 쓰면 `androidx.lifecycle` 의존성이 도메인 계층까지 침투합니다. Flow는 순수 Kotlin이므로 의존성 방향을 깨끗하게 유지합니다.

**둘째, 센서 데이터의 백프레셔 제어.** 자기장 센서는 20Hz(초당 20회) 데이터를 발생시킵니다. 렌즈 감지 카메라는 30fps입니다. UI는 60fps로 렌더링되지만 초당 20번 데이터 갱신이면 충분합니다. `sample(100)` 한 줄로 100ms마다 한 번씩만 UI를 갱신할 수 있습니다. LiveData에는 이런 제어 수단이 없습니다.

---

## 9.3 StateFlow + SharedFlow — 두 종류의 Hot Flow

SearCam ViewModel에서는 두 종류의 Hot Flow를 목적에 따라 구분해 사용합니다.

### StateFlow — UI 상태 관리

현재 화면이 어떤 상태인지를 나타냅니다. Compose UI는 이것을 구독해 화면을 그립니다.

```kotlin
// ScanViewModel.kt

// 내부: 변경 가능
private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)

// 외부 노출: 읽기 전용
val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()
```

`StateFlow`의 특징:
- 항상 최신값을 하나 갖고 있습니다 (초기값 필수)
- 새 구독자는 즉시 현재값을 받습니다
- 동일한 값이 연속으로 설정되면 emit하지 않습니다 (`distinctUntilChanged` 내장)
- Compose에서 `collectAsStateWithLifecycle()`로 State 변환

### SharedFlow — 일회성 이벤트 처리

네비게이션, 스낵바 메시지처럼 "한 번만 처리해야 하는" 이벤트에 사용합니다.

```kotlin
// ScanViewModel.kt

// 내부: 변경 가능 (버퍼 없음, replay=0)
private val _events = MutableSharedFlow<ScanUiEvent>()

// 외부 노출: 읽기 전용
val events: SharedFlow<ScanUiEvent> = _events.asSharedFlow()
```

`SharedFlow`의 특징:
- 초기값 없음 (replay 기본 0)
- 이벤트를 놓칠 수 있음 — 구독 전에 emit된 이벤트는 수신 불가
- `replay = 1`로 설정하면 마지막 이벤트를 캐시 (주의해서 사용)

### 왜 구분하는가

`StateFlow`만 써서 이벤트를 처리하면 문제가 생깁니다. 화면 회전 시 새 구독이 시작되면서 StateFlow가 현재값을 다시 emit합니다. "네비게이션 이벤트" 상태가 남아있으면 화면 회전마다 네비게이션이 실행됩니다. `SharedFlow`는 이미 처리된 이벤트를 다시 emit하지 않아 이 문제를 피합니다.

```
ScanUiState (StateFlow)   ScanUiEvent (SharedFlow)
━━━━━━━━━━━━━━━━━━━━━━━   ━━━━━━━━━━━━━━━━━━━━━━━━
Idle                      NavigateToResult(id)
Scanning(progress=0.5)    ShowSnackbar("Wi-Fi 필요")
Success(report)
Error(code, message)
```

---

## 9.4 ViewModel → UseCase → Repository 체인

Quick Scan의 데이터 흐름을 추적하며 각 계층의 역할을 살펴봅니다.

### 1단계: ViewModel이 UseCase를 실행

```kotlin
// ScanViewModel.kt

fun startQuickScan() {
    if (_uiState.value is ScanUiState.Scanning) return  // 중복 실행 방지

    scanJob = viewModelScope.launch {
        // 초기 상태 전환
        _uiState.value = ScanUiState.Scanning(
            progress = 0f,
            currentStep = "Wi-Fi 네트워크 스캔 중...",
        )

        try {
            // UseCase Flow를 collect — 결과가 오면 Success로 전환
            runQuickScanUseCase.invoke().collect { report ->
                _uiState.value = ScanUiState.Success(report)
                _events.emit(ScanUiEvent.NavigateToResult(report.id))  // 일회성 이벤트
            }
        } catch (e: Exception) {
            _uiState.value = ScanUiState.Error(
                code = "E2001",
                message = "스캔 중 오류가 발생했습니다: ${e.message}",
            )
        }
    }
}
```

ViewModel은 "무엇을 할지"만 결정합니다. "어떻게 할지"는 UseCase가 담당합니다. ViewModel은 Wi-Fi 스캐너가 어떻게 동작하는지, ARP 테이블이 뭔지 모릅니다.

### 2단계: UseCase가 비즈니스 로직을 처리

```kotlin
// RunQuickScanUseCase.kt

operator fun invoke(): Flow<ScanReport> = flow {
    val reportId = UUID.randomUUID().toString()
    val startedAt = System.currentTimeMillis()

    // Repository에 Wi-Fi 스캔 위임
    val wifiLayerResult = runWifiLayer()

    val completedAt = System.currentTimeMillis()

    // 위험도 산출 (교차 검증 UseCase에 위임)
    val layerResults = mapOf(LayerType.WIFI to wifiLayerResult)
    val (finalScore, correctionFactor) = calculateRiskUseCase.invokeWithCorrection(layerResults)
    val riskLevel = RiskLevel.fromScore(finalScore)

    // 도메인 모델 조립
    val report = ScanReport(
        id = reportId,
        mode = ScanMode.QUICK,
        startedAt = startedAt,
        completedAt = completedAt,
        riskScore = finalScore,
        riskLevel = riskLevel,
        devices = wifiLayerResult.devices,
        findings = wifiLayerResult.findings,
        layerResults = layerResults,
        correctionFactor = correctionFactor,
        locationNote = "",
        retroPoints = emptyList(),
        irPoints = emptyList(),
        magneticReadings = emptyList(),
    )

    emit(report)  // ViewModel에 전달
}
```

UseCase는 `operator fun invoke()`로 정의해 `useCase()` 함수처럼 호출합니다. Kotlin 관용적인 패턴입니다. 반환 타입이 `Flow<ScanReport>`인 점도 중요합니다 — 스캔 완료 시 단 한 번 emit하고 종료합니다.

### 3단계: Repository가 데이터 소스를 추상화

```kotlin
// ReportRepositoryImpl.kt

override suspend fun saveReport(report: ScanReport): Result<Unit> {
    return try {
        val entity = report.toEntity()  // 도메인 → 엔티티 변환
        reportDao.insert(entity)
        Timber.d("리포트 저장 완료: id=${report.id}, score=${report.riskScore}")
        Result.success(Unit)
    } catch (e: Exception) {
        Timber.e(e, "리포트 저장 실패: id=${report.id}")
        Result.failure(e)
    }
}

override fun observeReports(): Flow<List<ScanReport>> =
    reportDao.observeAll().map { entities ->
        entities.map { it.toDomain() }  // 엔티티 → 도메인 변환
    }
```

Repository는 두 가지를 합니다. **변환(mapping)**과 **에러 처리**. `Result<T>` 반환으로 UseCase가 try-catch를 반복하지 않게 합니다. `observeAll()`이 Room의 `Flow<List<ScanReportEntity>>`를 반환하면, `.map { ... }` 연산자로 도메인 모델 리스트로 변환합니다. UI는 `ScanReportEntity`를 알 필요가 없습니다.

---

## 9.5 병렬 레이어 실행 — coroutineScope + async

Full Scan의 핵심은 4개 레이어(Wi-Fi, 렌즈, IR, EMF)를 **동시에** 실행하는 것입니다. 순서대로 실행하면 30 + 60 + 45 + 30 = 165초가 걸립니다. 병렬로 실행하면 가장 오래 걸리는 레이어(렌즈 60초)만 기다립니다.

```kotlin
// RunFullScanUseCase.kt

/**
 * 4개 레이어를 coroutineScope + async로 병렬 실행한다.
 */
private suspend fun runAllLayersInParallel(
    lifecycleOwner: LifecycleOwner
): List<LayerResult> = coroutineScope {
    val wifiDeferred  = async { runWifiLayer() }
    val lensDeferred  = async { runLensLayer(lifecycleOwner) }
    val irDeferred    = async { runIrLayer(lifecycleOwner) }
    val magneticDeferred = async { runMagneticLayer() }

    listOf(
        wifiDeferred.await(),
        lensDeferred.await(),
        irDeferred.await(),
        magneticDeferred.await(),
    )
}
```

`coroutineScope { }` 블록이 중요합니다. `async { }` 4개를 실행하면 즉시 `Deferred<LayerResult>` 4개를 반환합니다 — 아직 완료되지 않은 "약속"입니다. 이후 `.await()`로 각 결과를 기다립니다. `coroutineScope`는 모든 자식 코루틴이 완료될 때까지 종료되지 않습니다.

### SupervisorJob으로 격리된 실패

ScanViewModel은 `viewModelScope`를 사용합니다. `viewModelScope`는 내부적으로 `SupervisorJob`을 씁니다. 이 덕분에 Wi-Fi 레이어가 실패해도 렌즈 레이어와 EMF 레이어는 계속 실행됩니다.

```
┌─ viewModelScope (SupervisorJob) ─────────────────────────┐
│                                                            │
│  ┌─ scanJob ───────────────────────────────────────────┐  │
│  │                                                      │  │
│  │  async → wifiLayer  ✅ 완료 (30초)                  │  │
│  │  async → lensLayer  ✅ 완료 (58초)                  │  │
│  │  async → irLayer    ❌ 실패 (권한 거부)             │  │
│  │  async → magneticLayer ✅ 완료 (28초)               │  │
│  │                                                      │  │
│  │  → irLayer 실패해도 나머지 3개는 정상 완료          │  │
│  │  → LayerResult(status=FAILED, score=0) 로 처리      │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

각 레이어 함수 내부에서도 에러를 잡아 `FAILED` 상태의 `LayerResult`를 반환합니다. 예외가 위로 전파되지 않습니다.

```kotlin
private suspend fun runWifiLayer(): LayerResult {
    val startAt = System.currentTimeMillis()
    return try {
        val devices = withTimeout(WIFI_LAYER_TIMEOUT_MS) {
            wifiScanRepository.scanDevices().getOrThrow()
        }
        LayerResult(
            layerType = LayerType.WIFI,
            status = ScanStatus.COMPLETED,
            score = devices.maxOfOrNull { it.riskScore } ?: 0,
            devices = devices,
            durationMs = System.currentTimeMillis() - startAt,
            findings = emptyList(),
        )
    } catch (e: Exception) {
        // 실패해도 상위로 예외를 던지지 않고 FAILED 결과 반환
        LayerResult(
            layerType = LayerType.WIFI,
            status = ScanStatus.FAILED,
            score = 0,
            devices = emptyList(),
            durationMs = System.currentTimeMillis() - startAt,
            findings = emptyList(),
        )
    }
}
```

### withTimeout으로 레이어별 제한 시간

각 레이어에 개별 타임아웃이 적용됩니다.

```kotlin
companion object {
    private const val WIFI_LAYER_TIMEOUT_MS     = 30_000L   // 30초
    private const val LENS_LAYER_TIMEOUT_MS     = 60_000L   // 60초
    private const val IR_LAYER_TIMEOUT_MS       = 45_000L   // 45초
    private const val MAGNETIC_LAYER_TIMEOUT_MS = 30_000L   // 30초
}
```

`withTimeout()`은 지정 시간 내에 블록이 완료되지 않으면 `TimeoutCancellationException`을 던집니다. 이것도 catch에서 잡아 `FAILED` 결과를 반환합니다.

---

## 9.6 Flow 연산자 실전 패턴

SearCam에서 자주 쓰이는 Flow 연산자 패턴을 정리합니다.

### map — 타입 변환

```kotlin
// ReportRepositoryImpl.kt

// ScanReportEntity Flow → ScanReport Flow
override fun observeReports(): Flow<List<ScanReport>> =
    reportDao.observeAll().map { entities ->
        entities.map { it.toDomain() }
    }
```

DB 엔티티가 바뀌면 자동으로 도메인 모델로 변환해서 흘려보냅니다.

### combine — 여러 Flow 합류

교차 검증 로직에서 3개 레이어 결과를 하나로 합칠 때 사용합니다.

```kotlin
// 3개 레이어 결과를 하나의 Flow로 합류
combine(
    layer1Flow,     // Flow<Layer1Result>
    layer2Flow,     // Flow<Layer2Result>
    layer3Flow,     // Flow<Layer3Result>
) { l1, l2, l3 ->
    crossValidator.validate(l1, l2, l3)
}
.flowOn(Dispatchers.Default)
.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = ScanUiState.Idle
)
```

`combine`은 3개 Flow 중 어느 하나라도 새 값을 emit하면, 3개의 최신값을 인자로 람다를 실행합니다.

### conflate — 밀린 이벤트 건너뛰기

렌즈 감지 프레임 분석처럼 UI가 처리 속도를 따라가지 못할 때 사용합니다.

```kotlin
lensDetectionRepository
    .observeRetroreflections()
    .conflate()   // 처리 지연 시 중간 emit 건너뜀, 최신값만 처리
    .collect { points ->
        updateUiWithPoints(points)
    }
```

`conflate()`는 처리 중인 동안 새로 들어온 값들을 버리고 가장 최신 값만 남깁니다. 실시간 렌즈 탐지 UI에서 "오래된 프레임 결과"를 처리하느라 현재 프레임 결과를 놓치는 상황을 방지합니다.

### sample — 주기적 샘플링

자기장 센서 20Hz 데이터를 100ms마다 한 번만 UI에 반영합니다.

```kotlin
magneticRepository
    .observeReadings()
    .sample(100)  // 100ms마다 가장 최신값 하나만 통과
    .flowOn(Dispatchers.Default)
    .collect { reading ->
        _magneticState.value = reading.toUiModel()
    }
```

`sample(100)`은 100ms 간격으로 그 시점의 최신값을 뽑아냅니다. 20Hz 센서에서 초당 20개가 들어오지만 UI는 10개만 처리합니다.

### catch — 스트림 에러 처리

```kotlin
lensDetectionRepository
    .observeRetroreflections()
    .catch { e ->
        // 스트림에서 에러 발생 시 에러 상태로 전환, 스트림은 계속
        _uiState.value = ScanUiState.Error("E1001", "카메라 접근 오류: ${e.message}")
        emit(emptyList())  // 빈 결과로 계속 진행
    }
    .collect { points -> ... }
```

`catch`는 Flow 파이프라인 중간에 에러가 발생했을 때 잡아서 처리합니다. `collect` 블록에서 발생한 에러는 잡지 않으므로 주의해야 합니다.

### flowOn — 디스패처 전환

```kotlin
magneticRepository
    .observeReadings()
    .map { it.toUiModel() }       // Default 스레드에서 변환
    .flowOn(Dispatchers.Default)  // map까지 Dispatchers.Default에서 실행
    .collect { model ->           // collect는 호출 스코프(Main)에서 실행
        _state.value = model
    }
```

`flowOn`은 **그 앞의 연산자들**이 실행될 스레드를 결정합니다. `collect`는 호출한 코루틴 스코프의 스레드에서 실행됩니다. CPU 집약적인 변환(`map`, `filter`, `combine`)은 `Dispatchers.Default`에서 실행하고, UI 갱신은 `Dispatchers.Main`에서 실행하는 것이 원칙입니다.

---

## 9.7 스캔 취소 — Job.cancel()

사용자가 스캔 중 뒤로가기 버튼을 누르면 즉시 취소해야 합니다. 코루틴의 `Job.cancel()`이 이를 처리합니다.

```kotlin
// ScanViewModel.kt

private var scanJob: Job? = null

fun startQuickScan() {
    scanJob = viewModelScope.launch {
        // 스캔 로직 ...
    }
}

fun cancelScan() {
    scanJob?.cancel()   // 진행 중인 스캔 즉시 취소
    scanJob = null
    _uiState.value = ScanUiState.Idle
}

override fun onCleared() {
    super.onCleared()
    scanJob?.cancel()   // ViewModel 소멸 시 자동 취소
}
```

`cancel()`을 호출하면 코루틴 안의 모든 `suspend` 함수가 `CancellationException`을 받아 정상 종료됩니다. `coroutineScope + async` 패턴에서는 부모 Job이 취소되면 모든 자식 async도 함께 취소됩니다.

```
cancelScan()
  └─ scanJob.cancel()
       └─ RunFullScanUseCase 코루틴 취소
            └─ coroutineScope 블록 취소
                 ├─ wifiDeferred.cancel()
                 ├─ lensDeferred.cancel()    → CameraX 분석 중단
                 ├─ irDeferred.cancel()      → IR 감지 중단
                 └─ magneticDeferred.cancel() → 센서 리스닝 중단
```

센서 리소스(CameraX, SensorManager)는 각 Repository의 `stopDetection()`에서 해제됩니다. 코루틴 취소 시 `finally` 블록에서 리소스 정리를 보장합니다.

```kotlin
// LensDetectionRepositoryImpl 예시 패턴
private suspend fun runLensLayer(lifecycleOwner: LifecycleOwner): LayerResult {
    return try {
        lensDetectionRepository.startDetection(lifecycleOwner).getOrThrow()
        // ... 분석 실행
    } catch (e: Exception) {
        // CancellationException도 여기서 처리됨
        runCatching { lensDetectionRepository.stopDetection() }
        throw e  // CancellationException은 다시 throw
    }
}
```

---

## 9.8 Compose에서 Flow 구독

Compose UI는 `collectAsStateWithLifecycle()`로 Flow를 State<T>로 변환합니다. 생명주기를 자동으로 인식해 백그라운드 상태에서는 구독을 중단합니다.

```kotlin
// FullScanScreen.kt (패턴)

@Composable
fun FullScanScreen(
    viewModel: ScanViewModel = hiltViewModel()
) {
    // StateFlow → State 변환 (생명주기 인식)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // SharedFlow → LaunchedEffect로 일회성 이벤트 처리
    val navController = LocalNavController.current
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ScanUiEvent.NavigateToResult ->
                    navController.navigate(Screen.Result(event.reportId))
                is ScanUiEvent.ShowSnackbar ->
                    snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    // UI 렌더링
    when (uiState) {
        is ScanUiState.Idle -> IdleContent(onStartScan = viewModel::startFullScan)
        is ScanUiState.Scanning -> ScanningContent(state = uiState as ScanUiState.Scanning)
        is ScanUiState.Success -> {} // LaunchedEffect의 NavigateToResult가 처리
        is ScanUiState.Error -> ErrorContent(error = uiState as ScanUiState.Error)
    }
}
```

`LaunchedEffect(Unit)`은 컴포저블이 처음 컴포지션에 진입할 때 한 번 시작됩니다. `SharedFlow`를 여기서 구독해 이벤트를 처리합니다. 화면 회전으로 컴포저블이 재구성되면 `LaunchedEffect`도 재시작되지만, 이미 emit된 SharedFlow 이벤트는 replay=0이므로 다시 처리되지 않습니다.

---

## 9.9 전체 데이터 흐름 요약 다이어그램

Quick Scan의 데이터가 실제로 어떻게 흘러가는지 처음부터 끝까지 추적합니다.

```
[사용자 탭]
   │
   ▼
ScanViewModel.startQuickScan()
   │ viewModelScope.launch {}
   │ _uiState.value = Scanning
   │
   ▼
RunQuickScanUseCase.invoke()
   │ flow { ... }
   │
   ├─▶ wifiScanRepository.scanDevices()
   │       │ withTimeout(30_000)
   │       │
   │       ├─▶ WifiScanner.getArpTable()
   │       │       └─ ARP 테이블 조회 (~1초)
   │       │
   │       ├─▶ WifiScanner.discoverMdns()
   │       │       └─ mDNS/SSDP 탐색 (~5초)
   │       │
   │       ├─▶ OuiDatabase.match(mac)
   │       │       └─ JSON에서 제조사 조회 (~0.5초)
   │       │
   │       └─▶ PortScanner.scan(suspiciousDevices)
   │               └─ 의심 기기 포트 스캔 (~15초)
   │
   ├─▶ calculateRiskUseCase.invokeWithCorrection(layerResults)
   │       └─ 가중치 계산 → finalScore → RiskLevel
   │
   │ ScanReport 조립
   │ emit(report)
   │
   ▼
ScanViewModel.collect { report →
   │ _uiState.value = Success(report)
   │ _events.emit(NavigateToResult(report.id))
   │
   ├─▶ reportRepository.saveReport(report)
   │       └─ report.toEntity() → reportDao.insert(entity)
   │               └─ SQLCipher 암호화 저장
   │
   └─▶ navController.navigate(Screen.Result(report.id))
}
   │
   ▼
[ScanResultScreen 표시]
```

---

## 핵심 정리

| 개념 | 결론 |
|------|------|
| 데이터 방향 | 센서 → Repository → UseCase → ViewModel → UI (단방향) |
| StateFlow | UI 상태 — 항상 현재값 보유, 화면 재진입 시 즉시 수신 |
| SharedFlow | 일회성 이벤트 — 네비게이션, 스낵바 |
| 병렬 실행 | `coroutineScope + async` — 4개 레이어 동시 실행 |
| 취소 | `Job.cancel()` — 모든 자식 코루틴 일괄 취소 |
| 백프레셔 | `conflate()`, `sample()` — 고빈도 센서 데이터 제어 |
| 에러 격리 | try-catch → FAILED 반환 (상위 전파 없음) |

- ✅ ViewModel은 UseCase만 알고, UseCase는 Repository만 안다
- ✅ UI 상태는 StateFlow, 이벤트는 SharedFlow로 분리
- ✅ 각 레이어에 개별 타임아웃 (`withTimeout`) 적용
- ✅ 레이어 실패는 FAILED LayerResult — 전체 스캔 중단 없음
- ✅ ViewModel 소멸 시 `onCleared()`에서 Job 취소
- ❌ Repository 계층에 LiveData 금지 (Android 의존성 침투)
- ❌ Composable에서 직접 Flow collect 금지 (`collectAsStateWithLifecycle` 사용)

---

## 다음 장 예고

데이터가 흘러가는 경로를 이해했습니다. 이제 그 데이터가 시작되는 지점 — 실제 Android 프로젝트 구조를 Ch10에서 다룹니다. Hilt DI 설정, 모듈 구조, 그리고 Clean Architecture 폴더 구조를 처음부터 세팅하는 과정을 설명합니다.

---
*참고 자료: docs/05-data-flow.md, ScanViewModel.kt, RunQuickScanUseCase.kt, RunFullScanUseCase.kt, ReportRepositoryImpl.kt*
