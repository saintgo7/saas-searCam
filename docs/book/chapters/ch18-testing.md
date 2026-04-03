# Ch18: 테스트 구현 — 코드보다 테스트가 먼저

> **이 장에서 배울 것**: 사용자의 안전과 직결된 앱에서 테스트는 선택이 아니라 계약입니다. MockK로 의존성을 격리하고, Turbine으로 Flow를 테스트하고, runTest로 코루틴을 제어하는 법을 배웁니다. PortScanner, NoiseFilter, RiskCalculator의 실전 테스트 코드를 통해 "동작한다"가 아닌 "정확하게 동작한다"를 증명하는 방법을 익힙니다.

---

## 도입

비행기 조종사는 이륙 전에 반드시 체크리스트를 사용합니다. 경험이 10년이 넘어도, 날씨가 완벽해도, 같은 기체를 100번 몰았어도 건너뛰지 않습니다. 그 이유는 단순합니다. 한 번의 실수가 돌이킬 수 없기 때문입니다.

SearCam도 마찬가지입니다. 몰래카메라를 놓치는 미탐(false negative)은 사용자의 프라이버시 침해로 이어집니다. 반대로 멀쩡한 기기를 카메라로 잘못 판단하는 오탐(false positive)은 사용자의 신뢰를 무너뜨립니다. "대충 테스트해봤는데 잘 되는 것 같다"는 말은 "아마 이 비행기는 괜찮을 것 같다"와 같습니다.

이 장에서는 SearCam의 테스트 전략과 실제 코드를 함께 살펴봅니다.

---

## 18.1 테스트 피라미드: 무엇을 얼마나 테스트할까

### 비율이 곧 전략이다

자동차 공장에는 세 종류의 검사가 있습니다. 부품 단위 검사(unit), 조립 라인 검사(integration), 시운전 검사(E2E). 세 가지 모두 하지만 비율이 다릅니다. 부품 검사가 가장 많고, 시운전은 최소화합니다. 시운전이 가장 현실적이지만, 가장 느리고 비싸기 때문입니다.

SearCam의 테스트 피라미드도 같은 논리를 따릅니다.

```
          ┌─────────┐
          │  E2E    │  10% (핵심 사용자 플로우)
          │ (UI)    │
         ─┼─────────┼─
         │Integration│  20% (센서→분석→결과 파이프라인)
         │           │
        ─┼───────────┼─
        │    Unit     │  70% (Domain + Data 레이어)
        │             │
        └─────────────┘
```

| 레벨 | 비율 | 실행 환경 | 실행 시간 목표 |
|------|------|----------|---------------|
| Unit | 70% | JVM (Robolectric 포함) | < 30초 |
| Integration | 20% | Android Instrumented | < 2분 |
| E2E | 10% | 실기기 / 에뮬레이터 | < 5분 |

단위 테스트가 전체의 70%를 차지하는 이유가 있습니다. 빠르고, 안정적이며, 정확하게 실패 지점을 짚어줍니다. Domain 레이어(UseCase, RiskCalculator)는 90% 이상 커버리지를 목표로 합니다. 사용자 안전과 직결된 판단 로직이 여기 있기 때문입니다.

---

## 18.2 테스트 의존성 설정

### 도구함 준비하기

목수가 망치 없이 집을 지을 수 없듯, 좋은 테스트도 올바른 도구가 필요합니다.

```toml
# gradle/libs.versions.toml
[versions]
mockk = "1.13.10"
turbine = "1.1.0"
coroutines-test = "1.8.0"
junit = "4.13.2"
kotest = "5.8.1"

[libraries]
test-mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
test-turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
test-coroutines = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines-test" }
test-junit = { group = "junit", name = "junit", version.ref = "junit" }
test-kotest-assertions = { group = "io.kotest", name = "kotest-assertions-core", version.ref = "kotest" }
```

```kotlin
// build.gradle.kts (app)
dependencies {
    testImplementation(libs.test.junit)
    testImplementation(libs.test.mockk)
    testImplementation(libs.test.turbine)
    testImplementation(libs.test.coroutines)
    testImplementation(libs.test.kotest.assertions)

    // Hilt 테스트 지원
    testImplementation("com.google.dagger:hilt-android-testing:2.51.1")
    kaptTest("com.google.dagger:hilt-android-compiler:2.51.1")
}
```

각 도구의 역할을 정리하면 다음과 같습니다.

| 도구 | 역할 |
|------|------|
| **MockK** | Kotlin 친화적 모킹. 코루틴 suspend 함수 모킹 지원 |
| **Turbine** | Kotlin Flow를 테스트하는 전용 라이브러리 |
| **kotlinx-coroutines-test** | runTest, TestCoroutineDispatcher로 코루틴 테스트 제어 |
| **Kotest** | 표현력 높은 assertion (`shouldBe`, `shouldBeInRange`) |

---

## 18.3 MockK로 의존성 격리하기

### 가짜 협력자를 만드는 법

단위 테스트의 핵심은 테스트 대상만 고립시키는 것입니다. 레스토랑에서 새 레시피를 테스트할 때, 재료 공급업체나 주방 기기 상태와 무관하게 레시피 자체만 검증하고 싶습니다. MockK는 이런 "가짜 재료"를 제공합니다.

### Repository 모킹

```kotlin
// domain/usecase/RunQuickScanUseCaseTest.kt
@ExtendWith(MockKExtension::class)
class RunQuickScanUseCaseTest {

    @MockK
    private lateinit var wifiScanner: WifiScanner

    @MockK
    private lateinit var ouiDatabase: OuiDatabase

    @MockK
    private lateinit var portScanner: PortScanner

    @MockK
    private lateinit var riskCalculator: RiskCalculator

    private lateinit var useCase: RunQuickScanUseCase

    @BeforeEach
    fun setUp() {
        useCase = RunQuickScanUseCase(
            wifiScanner = wifiScanner,
            ouiDatabase = ouiDatabase,
            portScanner = portScanner,
            riskCalculator = riskCalculator
        )
    }

    @Test
    fun `Wi-Fi 연결 상태에서 의심 기기 발견 시 위험도 40 이상 반환`() = runTest {
        // Given: 의심스러운 Hikvision 기기가 네트워크에 있음
        val suspiciousDevice = NetworkDevice(
            ip = "192.168.1.101",
            mac = "28:57:BE:AA:BB:CC",
            hostname = "unknown"
        )
        val ouiEntry = OuiEntry(
            vendor = "Hangzhou Hikvision",
            type = DeviceType.IP_CAMERA,
            riskWeight = 0.95f
        )

        coEvery { wifiScanner.scanNetwork() } returns listOf(suspiciousDevice)
        coEvery { ouiDatabase.lookup("28:57:BE") } returns ouiEntry
        coEvery { portScanner.scanPorts(suspiciousDevice.ip) } returns listOf(
            PortResult(port = 554, isOpen = true, protocol = "RTSP")
        )
        coEvery { riskCalculator.calculateLayer1Score(any()) } returns 75

        // When
        val result = useCase.execute()

        // Then
        result.riskScore shouldBeGreaterThanOrEqual 40
        result.suspiciousDevices.size shouldBe 1
        result.suspiciousDevices.first().mac shouldBe "28:57:BE:AA:BB:CC"
    }

    @Test
    fun `Wi-Fi 미연결 시 Layer1 스킵하고 빈 결과 반환`() = runTest {
        // Given
        coEvery { wifiScanner.isConnected() } returns false

        // When
        val result = useCase.execute()

        // Then
        result.layers[ScanLayer.WIFI]?.status shouldBe LayerStatus.SKIPPED
        coVerify(exactly = 0) { wifiScanner.scanNetwork() }
    }
}
```

핵심 패턴은 세 가지입니다.

- `coEvery { ... } returns ...`: suspend 함수의 반환값을 지정합니다
- `coVerify(exactly = N) { ... }`: 함수가 N번 호출되었는지 검증합니다
- `@MockK` + `@ExtendWith(MockKExtension::class)`: 자동 초기화

---

## 18.4 Turbine으로 Flow 테스트하기

### 파이프를 검사하는 방법

Kotlin Flow는 물이 흐르는 파이프와 같습니다. 문제는 파이프 안을 직접 들여다볼 수 없다는 것입니다. Turbine은 파이프에 수집 장치를 달아 흐르는 데이터를 하나씩 꺼내볼 수 있게 해줍니다.

```kotlin
// 스캔 진행 상황을 Flow로 emit하는 UseCase 테스트
class ScanProgressTest {

    @Test
    fun `스캔 진행률이 0에서 100까지 순서대로 emit된다`() = runTest {
        val fakeScanner = FakeWifiScanner(deviceCount = 5)
        val useCase = RunQuickScanUseCase(fakeScanner)

        useCase.progressFlow.test {
            // 첫 번째 emit: 0% (스캔 시작)
            val initial = awaitItem()
            initial.percentage shouldBe 0

            // ARP 스캔 완료: 30%
            val afterArp = awaitItem()
            afterArp.percentage shouldBe 30
            afterArp.phase shouldBe ScanPhase.ARP_SCANNING

            // OUI 매칭 완료: 60%
            val afterOui = awaitItem()
            afterOui.percentage shouldBe 60
            afterOui.phase shouldBe ScanPhase.OUI_MATCHING

            // 포트 스캔 완료: 100%
            val complete = awaitItem()
            complete.percentage shouldBe 100
            complete.phase shouldBe ScanPhase.COMPLETED

            awaitComplete()
        }
    }

    @Test
    fun `에러 발생 시 Flow가 에러로 종료된다`() = runTest {
        val errorScanner = FakeWifiScanner(throwError = true)
        val useCase = RunQuickScanUseCase(errorScanner)

        useCase.progressFlow.test {
            awaitError() shouldBeInstanceOf WifiScanException::class
        }
    }
}
```

Turbine의 핵심 API입니다.

| API | 설명 |
|-----|------|
| `awaitItem()` | 다음 emit된 값을 기다려 반환 |
| `awaitComplete()` | Flow가 정상 종료되길 기다림 |
| `awaitError()` | Flow가 에러로 종료되길 기다림 |
| `cancelAndIgnoreRemainingEvents()` | 남은 이벤트를 무시하고 취소 |

---

## 18.5 Coroutine 테스트: runTest와 TestDispatcher

### 시간을 제어하는 법

코루틴 테스트의 난제는 시간입니다. 실제 코드에서 `delay(2000)`은 2초를 기다립니다. 테스트에서도 2초를 기다리면 안 됩니다. `runTest`와 `TestCoroutineDispatcher`는 이 시간을 가상으로 조작할 수 있게 해줍니다. 마치 영화 촬영에서 타임랩스를 쓰는 것처럼, 2시간짜리 일몰을 30초 영상으로 담을 수 있습니다.

```kotlin
class CoroutineTimingTest {

    @Test
    fun `포트 스캔 타임아웃이 2초 후 정확히 발생한다`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val portScanner = PortScanner(
            dispatcher = testDispatcher,
            timeoutMs = 2_000L
        )

        val startTime = currentTime
        val result = portScanner.scanPort("192.168.1.1", 554)
        val elapsed = currentTime - startTime

        // 실제로 2초를 기다리지 않고 가상 시간으로 검증
        elapsed shouldBe 2_000L
        result.isOpen shouldBe false
    }

    @Test
    fun `20Hz 센서 데이터가 50ms 간격으로 emit된다`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val sensor = FakeMagneticSensor(dispatcher = testDispatcher)

        val readings = mutableListOf<MagneticReading>()
        val job = launch(testDispatcher) {
            sensor.readingFlow.take(5).collect { readings.add(it) }
        }

        // 가상 시간을 250ms 앞으로 이동 (5회 emit 유발)
        advanceTimeBy(250)
        job.join()

        readings.size shouldBe 5
    }
}
```

`runTest`의 세 가지 TestDispatcher를 이해하면 됩니다.

| Dispatcher | 특성 | 사용 시점 |
|-----------|------|----------|
| `StandardTestDispatcher` | 수동으로 시간 진행 | 순서 검증이 필요할 때 |
| `UnconfinedTestDispatcher` | 즉시 실행 | 순서보다 결과만 필요할 때 |
| `advanceTimeBy(ms)` | 가상 시간 진행 | delay, timeout 테스트 |

---

## 18.6 PortScanner 단위 테스트

### isPrivateIp() 경계값 테스트

"비공개 IP인지 확인하는" 함수는 단순해 보이지만, 경계값(boundary value)에서 버그가 숨습니다. RFC 1918에 따르면 사설 IP 대역은 세 가지입니다. 이 범위의 정확한 경계를 테스트해야 합니다.

```kotlin
class PortScannerTest {

    private val portScanner = PortScanner()

    // isPrivateIp() 경계값 테스트
    @ParameterizedTest
    @MethodSource("privateIpCases")
    fun `isPrivateIp가 사설 IP 대역을 정확히 판별한다`(
        ip: String,
        expected: Boolean,
        description: String
    ) {
        portScanner.isPrivateIp(ip) shouldBe expected
    }

    companion object {
        @JvmStatic
        fun privateIpCases() = listOf(
            // 10.0.0.0/8 대역
            Arguments.of("10.0.0.0",   true,  "10 대역 시작"),
            Arguments.of("10.0.0.1",   true,  "10 대역 내부"),
            Arguments.of("10.255.255.255", true, "10 대역 끝"),
            Arguments.of("11.0.0.0",   false, "10 대역 다음"),

            // 172.16.0.0/12 대역
            Arguments.of("172.15.255.255", false, "172 대역 이전"),
            Arguments.of("172.16.0.0",  true,  "172 대역 시작"),
            Arguments.of("172.31.255.255", true, "172 대역 끝"),
            Arguments.of("172.32.0.0",  false, "172 대역 다음"),

            // 192.168.0.0/16 대역
            Arguments.of("192.167.255.255", false, "192 대역 이전"),
            Arguments.of("192.168.0.0",  true,  "192 대역 시작"),
            Arguments.of("192.168.255.255", true, "192 대역 끝"),
            Arguments.of("192.169.0.0",  false, "192 대역 다음"),

            // 공인 IP
            Arguments.of("8.8.8.8",    false, "Google DNS"),
            Arguments.of("1.1.1.1",    false, "Cloudflare DNS"),

            // 예외 케이스
            Arguments.of("999.999.999.999", false, "잘못된 IP"),
            Arguments.of("",           false, "빈 문자열")
        )
    }

    @Test
    fun `병렬 포트 스캔이 순차 스캔보다 빠르다`() = runTest {
        val mockSocket = mockk<SocketFactory>()
        // 포트당 100ms 지연 시뮬레이션
        coEvery { mockSocket.connect(any(), any()) } coAnswers {
            delay(100)
        }

        val ports = listOf(554, 80, 8080, 443, 3702, 8554)
        val startTime = System.currentTimeMillis()

        // 병렬 스캔: 6개 포트를 동시에
        portScanner.scanPortsConcurrently(
            ip = "192.168.1.1",
            ports = ports
        )
        val elapsed = System.currentTimeMillis() - startTime

        // 병렬이면 100ms 정도, 순차면 600ms
        elapsed shouldBeLessThan 300L
    }
}
```

---

## 18.7 NoiseFilter 이동 평균 테스트

### 노이즈 속에서 신호 찾기

자기장 센서 데이터는 잡음이 많습니다. 스마트폰을 들고 걸어가기만 해도 자기장 값이 흔들립니다. 이동 평균(moving average)은 이 잡음을 걸러내는 기술입니다. 마치 주식 차트의 이동 평균선처럼, 단기 변동을 평활화합니다.

```kotlin
class NoiseFilterTest {

    private val filter = NoiseFilter(windowSize = 10)

    @Test
    fun `이동 평균이 10개 윈도우에 대해 정확한 평균을 반환한다`() {
        // Given: 1부터 10까지의 데이터
        val inputs = (1..10).map { it.toFloat() }
        var result = 0f

        // When: 순서대로 추가
        inputs.forEach { result = filter.add(it) }

        // Then: 마지막 결과는 (1+2+...+10)/10 = 5.5
        result shouldBe 5.5f
    }

    @Test
    fun `윈도우가 꽉 찬 후 슬라이딩이 올바르게 동작한다`() {
        // Given: 10개로 윈도우를 채우고
        (1..10).forEach { filter.add(it.toFloat()) }

        // When: 11번째 값(100) 추가 시 1이 제거되고 100이 들어옴
        val result = filter.add(100f)

        // Then: (2+3+4+5+6+7+8+9+10+100) / 10 = 15.4
        result shouldBe 15.4f
    }

    @Test
    fun `noise_floor 이하 신호는 필터링된다`() {
        // baseline 설정
        val baseline = 45f
        val noiseFloor = 3f  // std_dev * 2

        filter.calibrate(baseline = baseline, noiseFloor = noiseFloor)

        // delta 2.5 (< noiseFloor 3.0) → 필터링
        val reading = MagneticReading(x = 0f, y = 0f, z = baseline + 2.5f)
        val filtered = filter.applyFilter(reading)

        filtered shouldBe null
    }

    @Test
    fun `급격한 변화(0_3초 내 50+ uT)는 자체 간섭으로 필터링된다`() {
        // Given: 기준값 45 uT 설정 후
        filter.calibrate(baseline = 45f, noiseFloor = 3f)

        // When: 0.3초 내에 50 uT 이상 급변
        val suddenSpike = MagneticReading(x = 60f, y = 60f, z = 60f)
        // magnitude = sqrt(60² + 60² + 60²) ≈ 103.9, delta ≈ 58.9

        val result = filter.applyFilterWithSpikeDetection(
            reading = suddenSpike,
            previousMagnitude = 45f,
            timeDeltaMs = 200L  // 0.2초 내
        )

        result shouldBe null  // 급변으로 인해 필터링
    }

    @Test
    fun `경계값: noise_floor와 정확히 동일한 delta는 통과 처리된다`() {
        filter.calibrate(baseline = 45f, noiseFloor = 3f)

        // delta == noiseFloor (3.0) → 통과 (>= 로 비교)
        val reading = MagneticReading(x = 0f, y = 0f, z = 48f)  // delta = 3.0
        val result = filter.applyFilter(reading)

        result shouldNotBe null
    }
}
```

경계값 테스트(boundary testing)는 특히 중요합니다. `>` 와 `>=` 차이 하나가 탐지 정확도를 바꿉니다.

---

## 18.8 RiskCalculator 점수 검증 테스트

### 위험도 공식을 믿을 수 있는가

RiskCalculator는 SearCam의 핵심 의사결정 엔진입니다. 세 레이어의 점수를 받아 최종 위험도를 계산합니다. 이 공식이 틀리면 모든 탐지가 틀립니다. 종합 위험도 공식은 아래와 같습니다.

```
종합 위험도 = clamp(W1*L1 + W2*L2 + W3*L3 × 보정계수, 0, 100)

기본 가중치: W1=0.50(Wi-Fi), W2=0.35(렌즈), W3=0.15(EMF)
```

```kotlin
class RiskCalculatorTest {

    private val calculator = RiskCalculator()

    @Test
    fun `3레이어 가중치 합산이 정확하다`() {
        // Given: 각 레이어 점수
        val layer1Score = 80  // Wi-Fi 레이어 (가중치 0.50)
        val layer2Score = 70  // 렌즈 레이어 (가중치 0.35)
        val layer3Score = 60  // EMF 레이어 (가중치 0.15)

        // When
        val result = calculator.calculateRiskScore(
            layer1 = LayerResult(score = layer1Score, available = true),
            layer2 = LayerResult(score = layer2Score, available = true),
            layer3 = LayerResult(score = layer3Score, available = true)
        )

        // Then: 0.50*80 + 0.35*70 + 0.15*60 = 40 + 24.5 + 9 = 73.5 → 74
        result shouldBe 74
    }

    @Test
    fun `2개 레이어 양성 시 보정계수 1_2 적용된다`() {
        // Layer1, Layer3가 양성(> 60)
        val result = calculator.calculateRiskScore(
            layer1 = LayerResult(score = 80, available = true),
            layer2 = LayerResult(score = 10, available = true),
            layer3 = LayerResult(score = 70, available = true)
        )

        // 보정 전: 0.50*80 + 0.35*10 + 0.15*70 = 40 + 3.5 + 10.5 = 54
        // 보정 후: 54 × 1.2 = 64.8 → 65
        result shouldBe 65
    }

    @Test
    fun `3개 레이어 모두 양성 시 보정계수 1_5 적용된다`() {
        val result = calculator.calculateRiskScore(
            layer1 = LayerResult(score = 70, available = true),
            layer2 = LayerResult(score = 70, available = true),
            layer3 = LayerResult(score = 70, available = true)
        )

        // 보정 전: 0.50*70 + 0.35*70 + 0.15*70 = 70
        // 보정 후: 70 × 1.5 = 105 → clamp(105, 0, 100) = 100
        result shouldBe 100
    }

    @Test
    fun `Wi-Fi 미연결 시 가중치가 재조정된다`() {
        // Wi-Fi OFF → Layer1 사용 불가 → W1=0, W2=0.75, W3=0.25
        val result = calculator.calculateRiskScore(
            layer1 = LayerResult(score = 0, available = false),
            layer2 = LayerResult(score = 80, available = true),
            layer3 = LayerResult(score = 60, available = true)
        )

        // 0.75*80 + 0.25*60 = 60 + 15 = 75
        result shouldBe 75
    }

    // 등급 경계값 테스트: 19/20, 39/40, 59/60, 79/80
    @ParameterizedTest
    @CsvSource(
        "19, SAFE",
        "20, CAUTION",
        "39, CAUTION",
        "40, WARNING",
        "59, WARNING",
        "60, DANGER",
        "79, DANGER",
        "80, HIGH_RISK"
    )
    fun `위험도 등급이 경계값에서 정확히 매핑된다`(
        score: Int,
        expectedLevel: String
    ) {
        val level = calculator.getRiskLevel(score)
        level.name shouldBe expectedLevel
    }

    @Test
    fun `점수는 0 미만이 될 수 없다`() {
        // 모든 레이어 0점 + 음수 보정은 불가능하지만 방어적 테스트
        val result = calculator.calculateRiskScore(
            layer1 = LayerResult(score = 0, available = true),
            layer2 = LayerResult(score = 0, available = true),
            layer3 = LayerResult(score = 0, available = true)
        )

        result shouldBeGreaterThanOrEqual 0
    }

    @Test
    fun `점수는 100을 초과할 수 없다`() {
        val result = calculator.calculateRiskScore(
            layer1 = LayerResult(score = 100, available = true),
            layer2 = LayerResult(score = 100, available = true),
            layer3 = LayerResult(score = 100, available = true)
        )

        result shouldBeLessThanOrEqualTo 100
    }
}
```

---

## 18.9 ViewModel 테스트: StateFlow와 UI State

### 화면과 비즈니스 로직 사이의 계약

ViewModel은 UI와 Domain 사이의 번역가입니다. Domain에서 온 데이터를 UI가 이해할 수 있는 형태(UiState)로 변환합니다. 이 번역이 틀리면 사용자에게 잘못된 정보가 표시됩니다.

```kotlin
class ScanViewModelTest {

    private val mockUseCase = mockk<RunQuickScanUseCase>()
    private lateinit var viewModel: ScanViewModel

    @BeforeEach
    fun setUp() {
        // Hilt 없이 직접 주입 (단위 테스트는 가볍게)
        viewModel = ScanViewModel(quickScanUseCase = mockUseCase)
    }

    @Test
    fun `스캔 시작 시 Loading 상태로 전환된다`() = runTest {
        // Given: 스캔이 느리게 완료됨
        coEvery { mockUseCase.execute() } coAnswers {
            delay(1000)  // 1초 지연
            ScanResult(riskScore = 0, suspiciousDevices = emptyList())
        }

        viewModel.uiState.test {
            // Initial: Idle
            awaitItem() shouldBeInstanceOf ScanUiState.Idle::class

            // When: 스캔 시작
            viewModel.startQuickScan()

            // Then: Loading으로 즉시 전환
            awaitItem() shouldBeInstanceOf ScanUiState.Loading::class

            // 완료 대기
            advanceTimeBy(1000)
            awaitItem() shouldBeInstanceOf ScanUiState.Success::class

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `에러 발생 시 Error 상태와 에러 코드가 반환된다`() = runTest {
        coEvery { mockUseCase.execute() } throws WifiScanException("E1001")

        viewModel.uiState.test {
            awaitItem()  // Idle
            viewModel.startQuickScan()
            awaitItem()  // Loading

            val errorState = awaitItem()
            errorState shouldBeInstanceOf ScanUiState.Error::class
            (errorState as ScanUiState.Error).errorCode shouldBe "E1001"

            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

---

## 18.10 테스트 커버리지 측정

### 커버리지는 목표가 아니라 척도다

80% 커버리지를 달성했다고 해서 20%의 버그가 없다는 의미가 아닙니다. 커버리지는 "이 코드는 테스트되지 않았다"를 가리키는 지표일 뿐입니다. SearCam의 커버리지 목표는 다음과 같습니다.

```kotlin
// build.gradle.kts
jacoco {
    toolVersion = "0.8.11"
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
            // 제외: DI 모듈, Room 생성 코드, Hilt 생성 코드
            exclude(
                "**/di/**",
                "**/*_Hilt*",
                "**/*Dao_Impl*",
                "**/BuildConfig.*"
            )
        }
    )
}

// 커버리지 기준 미달 시 빌드 실패
tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
        rule {
            element = "CLASS"
            includes = listOf("*.domain.*")
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}
```

---

## 정리

테스트는 "작동한다"가 아닌 "언제나, 어떤 조건에서도, 정확하게 작동한다"를 증명하는 도구입니다. SearCam에서 배운 핵심 원칙을 정리합니다.

1. **MockK로 격리**: 테스트 대상 외의 모든 의존성은 가짜로 대체합니다
2. **Turbine으로 Flow 검증**: emit 순서와 완료/에러 상태를 정확히 검증합니다
3. **runTest로 시간 제어**: delay와 timeout을 가상 시간으로 테스트합니다
4. **경계값 우선**: 버그는 항상 경계(`>` vs `>=`, 0, 100)에서 태어납니다
5. **실패 케이스 우선 설계**: 성공 경로는 누구나 테스트합니다. 실패 경로가 안전망입니다

다음 장에서는 이 테스트들을 자동으로 실행하는 CI/CD 파이프라인을 구축합니다.
