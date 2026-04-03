# Ch21: 모니터링과 성능 최적화

> **이 장에서 배울 것**: 배포가 끝이 아닙니다. Android Profiler로 병목을 찾고, 배터리 소모를 20Hz 센서와 병렬 포트 스캔으로 줄이고, CameraX 프레임을 즉시 close하여 메모리를 관리합니다. Crashlytics로 오류를 추적하고 ANR을 원천 차단하는 방법까지 다룹니다.

---

## 도입

자동차를 구매할 때 연비, 출력, 내구성을 봅니다. 사면 그걸로 끝이 아닙니다. 주행 중에 계기판을 보고, 정기 점검을 받고, 이상한 소리가 나면 원인을 찾습니다. 차가 멈추기 전에 예방합니다.

앱도 마찬가지입니다. Play Store 출시가 끝이 아니라 관찰과 개선이 시작되는 시점입니다. SearCam은 사용자 안전과 직결되는 앱이므로 크래시 한 건, ANR 한 건도 허용하기 어렵습니다. 이 장에서는 앱이 살아있는 동안 어떻게 모니터링하고 최적화하는지 다룹니다.

---

## 21.1 모니터링 스택 구성

### 무엇을 볼 것인가

SearCam의 모니터링은 Firebase 무료 티어로 모두 구성합니다. 비용 없이 핵심 지표를 확인할 수 있습니다.

| 영역 | 도구 | 목적 |
|------|------|------|
| 크래시 추적 | Firebase Crashlytics | 앱 종료 원인 분석 |
| 성능 측정 | Firebase Performance | 스캔 소요 시간, 화면 렌더링 |
| 사용 분석 | Firebase Analytics | 기능 사용 패턴 |
| 원격 설정 | Firebase Remote Config | 긴급 파라미터 조정 |
| ANR | Crashlytics ANR 리포팅 | 응답 없음 추적 |

**PII 수집 금지 원칙:**

SearCam은 개인 식별 정보를 일절 수집하지 않습니다.

```
수집하지 않는 데이터:
  - GPS 좌표, Wi-Fi SSID
  - 발견된 기기 MAC 주소
  - 스캔 결과 상세 (위험도, 기기 목록)
  - 카메라 촬영 이미지/프레임

수집하는 데이터 (비식별):
  - 크래시 스택 트레이스
  - 스캔 소요 시간
  - 기기 모델, OS 버전
  - 이벤트 (scan_start, scan_complete)
```

---

## 21.2 Firebase Crashlytics 설정

### 문제가 생기면 바로 알아야 한다

자동차 계기판의 경고등은 엔진이 망가지기 전에 켜집니다. Crashlytics는 사용자가 신고하기 전에 문제를 감지합니다.

```kotlin
// build.gradle.kts (app)
dependencies {
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
}

// build.gradle.kts (project)
plugins {
    id("com.google.firebase.crashlytics") version "2.9.9"
}
```

```kotlin
// SearCamApp.kt
class SearCamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 릴리즈 빌드에서만 Crashlytics 활성화
        // 디버그 빌드에서는 비활성화 (개발 중 노이즈 방지)
        FirebaseCrashlytics.getInstance()
            .setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
    }
}
```

**커스텀 컨텍스트 키 설정:**

크래시가 발생했을 때 "어떤 스캔 도중에" 발생했는지 알면 재현이 빠릅니다.

```kotlin
object CrashlyticsHelper {

    fun setScanContext(scanType: ScanType) {
        if (BuildConfig.DEBUG) return  // 디버그에서는 실행하지 않음

        FirebaseCrashlytics.getInstance().apply {
            setCustomKey("scan_type", scanType.name)
            setCustomKey("app_version", BuildConfig.VERSION_NAME)
            setCustomKey("device_model", Build.MODEL)
            setCustomKey("os_version", Build.VERSION.SDK_INT.toString())
        }
    }

    fun logNonFatal(tag: String, message: String, exception: Exception) {
        FirebaseCrashlytics.getInstance().apply {
            log("[$tag] $message")
            recordException(exception)
        }
    }

    // 센서 에러는 치명적이지 않으므로 non-fatal로 기록
    fun logSensorError(sensorType: String, error: Exception) {
        logNonFatal(
            tag = "SensorError",
            message = "센서 초기화 실패: $sensorType",
            exception = error
        )
    }
}
```

**크래시 심각도 분류와 대응 시간:**

| 심각도 | 기준 | 대응 시간 |
|--------|------|----------|
| P0 | ANR, OOM, 크래시율 3%+ | 즉시 (24시간 내 핫픽스) |
| P1 | NPE, SecurityException, 1~3% | 48시간 내 수정 |
| P2 | 센서/네트워크 에러, 0.5~1% | 다음 릴리즈 |
| P3 | PDF 생성 실패, 파싱 에러 | 다음 릴리즈 |

**알림 기준 설정 (Firebase Console):**

```
Firebase Console → Crashlytics → Alerts
  → 새 이슈 발생: Slack 알림
  → 크래시율 > 1% (24h): Slack + 이메일
  → 크래시율 > 3% (24h): Slack + 이메일 + 전화
  → 동일 이슈 100회/시간 초과: Slack 즉시 알림
```

---

## 21.3 Firebase Performance로 성능 추적

### 숫자로 말하는 성능

"빠른 것 같다"는 충분하지 않습니다. Quick Scan이 평균 28초 걸린다면, 목표(30초)를 달성하고 있습니다. 35초라면 뭔가 문제가 있습니다. 숫자가 없으면 개선이 있는지도 모릅니다.

```kotlin
// build.gradle.kts (app)
dependencies {
    implementation("com.google.firebase:firebase-perf-ktx")
}

// build.gradle.kts (project)
plugins {
    id("com.google.firebase.firebase-perf") version "1.4.2"
}
```

**커스텀 성능 트레이스 정의:**

```kotlin
object PerfTraces {

    fun traceQuickScan(): Trace =
        Firebase.performance.newTrace("quick_scan_duration").apply {
            putAttribute("scan_type", "quick")
        }

    fun traceFullScan(): Trace =
        Firebase.performance.newTrace("full_scan_duration").apply {
            putAttribute("scan_type", "full")
        }

    fun traceLayer(layer: String): Trace =
        Firebase.performance.newTrace("layer_${layer}_duration")

    fun traceOuiDbLoad(): Trace =
        Firebase.performance.newTrace("oui_db_load")
}
```

**UseCase에 트레이스 적용:**

```kotlin
class RunQuickScanUseCase @Inject constructor(
    private val wifiScanner: WifiScanner,
    private val riskCalculator: RiskCalculator
) {
    suspend fun execute(): ScanResult {
        val trace = PerfTraces.traceQuickScan()
        trace.start()

        return try {
            val devices = wifiScanner.scanNetwork()
            trace.putMetric("device_count", devices.size.toLong())

            val result = riskCalculator.calculate(devices)
            trace.putMetric("risk_score", result.riskScore.toLong())

            result
        } finally {
            trace.stop()  // 성공/실패 무관하게 항상 기록
        }
    }
}
```

**Firebase Performance가 자동 수집하는 메트릭:**

| 메트릭 | 목표 | 허용 범위 |
|--------|------|----------|
| 앱 콜드 스타트 | < 2초 | < 3초 (저사양) |
| 앱 웜 스타트 | < 1초 | < 1.5초 |
| 느린 프레임 (16ms 초과) | < 5% | < 10% |
| 멈춘 프레임 (700ms 초과) | < 1% | < 2% |

---

## 21.4 Android Profiler로 병목 찾기

### 진단 없는 처방은 없다

성능 문제는 "최적화가 필요한 것 같다"는 직감이 아니라 Profiler의 데이터로 확인해야 합니다. 의사가 진단 없이 처방하지 않듯, 개발자도 프로파일링 없이 최적화를 시작하면 안 됩니다.

**Android Studio Profiler 사용법:**

```
Android Studio → View → Tool Windows → Profiler
  → 앱 실행 상태에서 [+] → 세션 시작

주요 탭:
  CPU: 스캔 중 CPU 사용률, 스레드 상태
  Memory: 힙 사용량, GC 빈도
  Energy: 배터리 소모 추정치
  Network: 네트워크 요청 (Wi-Fi 스캔)
```

**Quick Scan 프로파일링 체크리스트:**

```
1. CPU 프로파일
   ✓ 메인 스레드 블로킹 여부 확인
     → 파란색(Running) 이외 긴 주황색(Waiting) 없어야 함
   ✓ 포트 스캔이 Dispatchers.IO에서 실행되는지 확인
   ✓ RiskCalculator가 Dispatchers.Default에서 실행되는지 확인

2. 메모리 프로파일
   ✓ GC 이벤트 빈도 확인 (스캔 중 3회 이상이면 문제)
   ✓ 힙 사용량이 스캔 완료 후 원래 수준으로 복귀하는지 확인
   ✓ ImageProxy close 누락으로 인한 메모리 누수 확인

3. 에너지 프로파일
   ✓ Wake lock 사용 최소화
   ✓ 스캔 완료 후 센서 리스너 해제 확인
```

---

## 21.5 배터리 최적화: 20Hz 센서와 병렬화

### 배터리를 아끼는 세 가지 원칙

SearCam의 배터리 목표는 Quick Scan 전체(30초)에 2% 이하입니다. 이 목표를 달성하는 세 가지 전략이 있습니다.

**전략 1: 자기장 센서 20Hz 고정**

센서를 가장 빠른 주기로 읽으면 배터리가 빨리 닳습니다. 자기장 감지에 50Hz는 과도합니다. 20Hz(50ms 간격)로 충분합니다.

```kotlin
class MagneticSensorManager @Inject constructor(
    private val sensorManager: SensorManager
) {
    // SENSOR_DELAY_NORMAL = 200ms = 5Hz (너무 느림)
    // SENSOR_DELAY_GAME   = 20ms  = 50Hz (너무 빠름, 배터리 낭비)
    // 직접 50ms = 20Hz 지정
    private val SAMPLING_PERIOD_US = 50_000  // 50ms = 20Hz

    fun startListening(callback: (MagneticReading) -> Unit) {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            ?: throw SensorUnavailableException("자력계 센서 없음")

        sensorManager.registerListener(
            createListener(callback),
            sensor,
            SAMPLING_PERIOD_US  // 마이크로초 단위
        )
    }

    fun stopListening() {
        // 스캔 완료 즉시 해제 — 리스너를 유지하면 배터리 계속 소모
        sensorManager.unregisterListener(listener)
    }
}
```

**전략 2: 포트 스캔 병렬화**

순차 스캔은 기기당 최대 12초(6포트 × 타임아웃 2초)가 걸립니다. 동시 병렬 스캔으로 2초로 단축합니다.

```kotlin
class PortScanner @Inject constructor(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val TIMEOUT_MS = 2_000L

    // 여러 기기 × 여러 포트를 동시에 스캔
    suspend fun scanAll(
        devices: List<NetworkDevice>,
        ports: List<Int>
    ): Map<NetworkDevice, List<PortResult>> = coroutineScope {

        devices
            .filter { it.riskWeight > 0.3f }  // 안전 벤더는 스킵
            .map { device ->
                async(dispatcher) {
                    device to scanDevice(device.ip, ports)
                }
            }
            .awaitAll()
            .toMap()
    }

    private suspend fun scanDevice(
        ip: String,
        ports: List<Int>
    ): List<PortResult> = coroutineScope {

        ports
            .map { port ->
                async {
                    scanSinglePort(ip, port)
                }
            }
            .awaitAll()
    }

    private suspend fun scanSinglePort(ip: String, port: Int): PortResult {
        return withContext(dispatcher) {
            try {
                withTimeout(TIMEOUT_MS) {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(ip, port), TIMEOUT_MS.toInt())
                    socket.close()
                    PortResult(port = port, isOpen = true)
                }
            } catch (e: Exception) {
                PortResult(port = port, isOpen = false)
            }
        }
    }
}
```

**전략 3: 카메라 선택적 프레임 분석**

30fps를 모두 분석하는 것은 CPU와 배터리 낭비입니다. 의심 포인트 발견 전까지 15fps로 운영합니다.

```kotlin
class AdaptiveFrameAnalyzer : ImageAnalysis.Analyzer {

    private var frameCount = 0
    private var isHighPriorityMode = false
    private var lastSuspiciousTime = 0L

    override fun analyze(image: ImageProxy) {
        try {
            // 의심 포인트 없으면 2프레임 중 1개만 처리 (15fps)
            // 의심 포인트 있으면 전체 처리 (30fps)
            val shouldProcess = isHighPriorityMode || (frameCount % 2 == 0)
            frameCount++

            if (shouldProcess) {
                val result = detectSuspiciousPoints(image)
                if (result.hasSuspiciousPoints) {
                    isHighPriorityMode = true
                    lastSuspiciousTime = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - lastSuspiciousTime > 2_000) {
                    // 2초간 포인트 미발견 → 저주파 모드 복귀
                    isHighPriorityMode = false
                }
            }
        } finally {
            // 핵심: 항상 즉시 close → 미호출 시 CameraX 파이프라인 블로킹
            image.close()
        }
    }
}
```

---

## 21.6 메모리 관리: CameraX 프레임 즉시 close

### 메모리 누수는 시한폭탄이다

메모리 누수는 즉시 앱을 망가뜨리지 않습니다. 서서히 메모리를 잠식하다가, 한계에 도달하면 OutOfMemoryError로 앱이 종료됩니다. 가장 흔한 원인은 ImageProxy를 닫지 않는 것입니다.

**ImageProxy 관리 원칙:**

```kotlin
// 잘못된 방법: close를 잊는 경우
class WrongAnalyzer : ImageAnalysis.Analyzer {
    override fun analyze(image: ImageProxy) {
        if (someCondition) {
            process(image)
            image.close()  // 조건이 false이면 close 호출 안 됨!
        }
    }
}

// 올바른 방법: finally 블록으로 반드시 close 보장
class CorrectAnalyzer : ImageAnalysis.Analyzer {
    override fun analyze(image: ImageProxy) {
        try {
            process(image)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal("FrameAnalysis", "분석 실패", e)
        } finally {
            image.close()  // 항상 실행
        }
    }
}
```

**OUI DB 지연 로딩으로 콜드 스타트 최적화:**

OUI JSON 파일 (~500KB)을 앱 시작 시 로드하면 콜드 스타트가 느려집니다.

```kotlin
class OuiDatabaseImpl @Inject constructor(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : OuiDatabase {

    // 첫 조회 시 로딩 (앱 시작 시 X)
    private val database: HashMap<String, OuiEntry> by lazy {
        loadDatabase()
    }

    private fun loadDatabase(): HashMap<String, OuiEntry> {
        return context.assets.open("oui_database.json").use { stream ->
            Json.decodeFromStream<List<OuiEntry>>(stream)
                .associateByTo(HashMap()) { it.ouiPrefix.uppercase() }
        }
    }

    override fun lookup(macPrefix: String): OuiEntry? {
        val normalized = macPrefix.uppercase().replace("-", ":")
        return database[normalized]
    }
}
```

**이동 평균 링 버퍼 구현 (배열 재할당 방지):**

```kotlin
// 매번 새 배열을 만들면 GC 압박이 증가합니다
// 링 버퍼로 같은 배열을 재사용합니다
class IncrementalMovingAverage(private val windowSize: Int) {
    private val buffer = FloatArray(windowSize)  // 한 번만 할당
    private var sum = 0f
    private var index = 0
    private var count = 0

    fun add(value: Float): Float {
        val oldest = buffer[index]
        buffer[index] = value
        sum = sum - oldest + value
        index = (index + 1) % windowSize
        count = minOf(count + 1, windowSize)
        return sum / count
    }

    fun reset() {
        buffer.fill(0f)
        sum = 0f
        index = 0
        count = 0
    }
}
```

---

## 21.7 ANR 방지 전략

### "앱이 응답하지 않습니다"를 막아라

ANR(Application Not Responding)은 메인 스레드가 5초 이상 블로킹될 때 발생합니다. 사용자에게 "앱 종료" 팝업을 보여주는 최악의 UX입니다.

**ANR 원인 TOP 5:**

1. 메인 스레드에서 네트워크 요청
2. 메인 스레드에서 파일 I/O
3. 메인 스레드에서 DB 쿼리
4. Mutex/Lock 데드락
5. 오래 걸리는 Binder 호출

**ANR 방지 패턴:**

```kotlin
// 잘못된 방법: 메인 스레드에서 직접 실행
class WrongViewModel : ViewModel() {
    fun loadData() {
        // 메인 스레드에서 실행 → ANR 위험!
        val result = repository.fetchFromNetwork()  // 블로킹!
        _uiState.value = UiState.Success(result)
    }
}

// 올바른 방법: 코루틴으로 백그라운드에서 실행
class CorrectViewModel @Inject constructor(
    private val repository: ScanRepository
) : ViewModel() {

    fun startScan() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            // withContext로 백그라운드 스레드에서 실행
            val result = withContext(Dispatchers.IO) {
                repository.fetchFromNetwork()
            }

            // collect 후 메인 스레드에서 UI 업데이트
            _uiState.value = UiState.Success(result)
        }
    }
}
```

**Room DB에 메인 스레드 접근 금지 강제:**

```kotlin
@Database(
    entities = [ScanReportEntity::class, DetectedDeviceEntity::class],
    version = 1
)
abstract class SearCamDatabase : RoomDatabase() {
    // Room은 기본적으로 메인 스레드 접근을 차단합니다
    // allowMainThreadQueries()는 절대 사용하지 않습니다

    companion object {
        fun create(context: Context): SearCamDatabase {
            return Room.databaseBuilder(
                context,
                SearCamDatabase::class.java,
                "searcam.db"
            )
            // .allowMainThreadQueries() // 절대 사용 금지
            .build()
        }
    }
}
```

**StrictMode로 개발 중 위반 감지:**

```kotlin
// SearCamApp.kt
class SearCamApp : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            // 개발 중에만 활성화: 메인 스레드 위반을 즉시 감지
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()       // 디스크 읽기 감지
                    .detectDiskWrites()      // 디스크 쓰기 감지
                    .detectNetwork()         // 네트워크 감지
                    .penaltyLog()            // Logcat에 기록
                    .penaltyDeath()          // 크래시로 강제 감지 (개발 중만!)
                    .build()
            )
        }
    }
}
```

---

## 21.8 Firebase Remote Config: 긴급 파라미터 조정

### 앱 업데이트 없이 동작을 바꾸는 법

프로덕션에서 포트 스캔 타임아웃이 너무 짧아 오탐이 발생한다면? 앱 업데이트를 기다리는 동안 수만 명이 잘못된 결과를 받습니다. Remote Config는 서버에서 파라미터를 변경하면 앱이 즉시 반영합니다.

```kotlin
// build.gradle.kts
implementation("com.google.firebase:firebase-config-ktx")
```

```kotlin
// RemoteConfigManager.kt
object RemoteConfigManager {

    private val remoteConfig = Firebase.remoteConfig

    // 기본값 (서버 연결 실패 시 사용)
    private val defaults = mapOf(
        "port_scan_timeout_ms" to 2_000L,
        "emf_noise_threshold" to 3.0f,
        "max_concurrent_port_scans" to 4L,
        "enable_ir_detection" to true,
        "oui_risk_threshold" to 0.7f
    )

    suspend fun fetchAndActivate(): Boolean {
        remoteConfig.setDefaultsAsync(defaults).await()

        return try {
            remoteConfig.fetchAndActivate().await()
        } catch (e: Exception) {
            // 실패 시 기본값 사용 (앱 동작 지속)
            false
        }
    }

    val portScanTimeoutMs: Long
        get() = remoteConfig.getLong("port_scan_timeout_ms")

    val emfNoiseThreshold: Float
        get() = remoteConfig.getDouble("emf_noise_threshold").toFloat()

    val maxConcurrentPortScans: Int
        get() = remoteConfig.getLong("max_concurrent_port_scans").toInt()

    val enableIrDetection: Boolean
        get() = remoteConfig.getBoolean("enable_ir_detection")
}
```

---

## 21.9 성능 KPI 대시보드

### 한눈에 보는 앱 건강 상태

SearCam의 핵심 성능 지표를 정기적으로 확인해야 합니다.

| 지표 | 목표 | 경고 | 위험 |
|------|------|------|------|
| 콜드 스타트 | < 2초 | 2~3초 | > 3초 |
| Quick Scan 완료 | < 30초 | 30~40초 | > 40초 |
| Full Scan 완료 | < 3분 | 3~4분 | > 4분 |
| 메모리 peak | < 150MB | 150~200MB | > 200MB |
| 크래시율 | < 0.3% | 0.3~1% | > 1% |
| ANR 비율 | < 0.2% | 0.2~0.5% | > 0.5% |
| Quick Scan 배터리 | < 2% | 2~3% | > 3% |

**주간 성능 리뷰 절차:**

```
매주 월요일 오전 체크:
  1. Firebase Crashlytics → 새 이슈 확인
  2. Firebase Performance → Quick Scan 평균 시간
  3. Google Play Console → Android Vitals (ANR, 크래시)
  4. Battery Historian → 배터리 소모 패턴 (출시 후 월 1회)
```

---

## 21.10 Battery Historian로 배터리 분석

### 배터리를 어디서 쓰는지 알아야 아낀다

Battery Historian은 `adb bugreport`를 분석해 배터리 소모 원인을 시각화합니다.

```bash
# 배터리 리포트 수집
adb shell dumpsys batterystats --reset
# (Quick Scan 실행)
adb bugreport > bugreport.zip

# Battery Historian으로 분석
# https://bathist.ef.lc/ 에 업로드 또는 로컬 실행
docker run -d -p 9999:9999 gcr.io/android-battery-historian/battery-historian
# localhost:9999 에서 bugreport.zip 업로드
```

**분석 시 확인할 항목:**

```
Wake Locks:
  → SearCam이 보유한 Wake Lock 시간 확인
  → 스캔 완료 후 Wake Lock 해제되는지 확인

Sensor Usage:
  → MagneticSensor 활성 시간 확인
  → 스캔 외 시간에 센서가 켜져 있으면 버그

Network Activity:
  → mDNS, SSDP, 포트 스캔 네트워크 사용량 확인
  → 불필요한 재시도 요청 없는지 확인
```

---

## 정리

모니터링과 성능 최적화는 배포 후 시작되는 지속적인 작업입니다. SearCam에서 적용한 핵심 원칙을 요약합니다.

1. **Crashlytics 우선**: 사용자 신고 전에 크래시를 먼저 알아야 합니다. P0 이슈는 24시간 내 핫픽스
2. **측정 후 최적화**: Android Profiler와 Firebase Performance로 병목을 확인한 후 코드를 수정합니다
3. **배터리 3원칙**: 센서 20Hz 고정, 포트 스캔 병렬화, 카메라 프레임 선택적 분석
4. **메모리 원칙**: ImageProxy는 `finally` 블록에서 반드시 `close()`, GC 압박 최소화
5. **ANR 원천 차단**: 메인 스레드는 UI만, 모든 I/O는 `Dispatchers.IO`, StrictMode로 위반 즉시 감지
6. **Remote Config 활용**: 긴급 파라미터 조정은 앱 업데이트 없이 서버에서 처리

좋은 앱은 출시할 때가 아니라 사용자가 쓰는 동안 만들어집니다. 모니터링이 그 길을 밝혀줍니다.
