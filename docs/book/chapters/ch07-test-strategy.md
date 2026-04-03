# Ch07: 테스트 전략 — 버그보다 테스트가 먼저

> **이 장에서 배울 것**: TDD RED-GREEN-IMPROVE 사이클이 탐지 앱에서 왜 필수인지, MockK와 Turbine으로 Kotlin Coroutines + Flow를 테스트하는 방법, 탐지 정확도를 코드로 검증하는 방법, 에러 코드 체계(E1xxx/E2xxx/E3xxx)의 테스트 방법을 배웁니다.

---

## 도입

비행기 조종사는 이륙 전에 체크리스트를 읽습니다. 수백 번 같은 기종을 몰았어도 절차를 생략하지 않습니다. 이유는 하나입니다. "이번에는 괜찮겠지"라는 생각이 가장 위험하기 때문입니다.

소프트웨어 테스트도 같습니다. "이 로직은 단순하니까 테스트 없이 넣어도 되겠지"라는 생각이 버그의 시작입니다.

SearCam은 특히 더 그렇습니다. 오탐(false positive)은 사용자를 불필요하게 불안하게 만들고, 미탐(false negative)은 실제 카메라를 "안전하다"고 판정해 물리적 위험을 초래합니다. 탐지 정확도는 코드 품질이 아니라 사용자 안전의 문제입니다.

이 장에서는 TDD로 SearCam을 개발하는 방법을 실제 테스트 케이스와 함께 보여줍니다.

---

## 7.1 테스트 철학 — "동작한다"가 아닌 "정확하게 동작한다"

### 오탐과 미탐 중 어느 것이 더 나쁜가?

이 질문에 대한 답이 SearCam의 테스트 전략을 결정합니다.

```
오탐 (False Positive)
  실제 카메라 없음 → "위험" 판정
  결과: 사용자 불안, 앱 신뢰도 하락

미탐 (False Negative)
  실제 카메라 있음 → "안전" 판정
  결과: 사용자 물리적 위험 노출
```

**미탐이 더 나쁩니다.** 따라서 테스트는 "카메라를 놓치지 않는가"에 더 엄격해야 합니다.

이 판단이 테스트 케이스 설계에 반영됩니다. 경계값 테스트에서 위험도 점수 경계(19/20, 39/40, 59/60, 79/80)를 모두 검증하고, 오류 케이스보다 정상적인 카메라 탐지 케이스를 더 많이 작성합니다.

### 테스트 피라미드

```
          ┌─────────┐
          │   E2E   │  10%  (핵심 사용자 플로우)
          │   UI    │
         ─┼─────────┼─
         │Integration│  20%  (센서→분석→결과 파이프라인)
         │           │
        ─┼───────────┼─
        │    Unit     │  70%  (Domain + Data 레이어)
        │             │
        └─────────────┘
```

| 레벨 | 비율 | 실행 환경 | 목표 실행 시간 | 목적 |
|------|------|---------|-------------|------|
| Unit | 70% | JVM (Robolectric) | 30초 이내 | 로직 정확성 |
| Integration | 20% | Android Instrumented | 2분 이내 | 파이프라인 연동 |
| E2E | 10% | 실기기/에뮬레이터 | 5분 이내 | 사용자 시나리오 |

---

## 7.2 TDD 사이클 — RED-GREEN-IMPROVE

### 자동차 안전벨트처럼

안전벨트를 먼저 채운 다음 운전을 시작합니다. 다 달린 후 채우는 것이 아닙니다. TDD는 코드 전에 테스트를 먼저 작성합니다.

```
RED   → 실패하는 테스트 작성 (아직 구현 없음)
         ↓
GREEN → 테스트를 통과시키는 최소 구현 작성
         ↓
IMPROVE → 코드를 정리하면서 테스트 유지
         ↓
다음 테스트로 반복
```

SearCam에서 `RiskCalculator`를 TDD로 구현하는 예시를 보겠습니다.

### Step 1: RED — 실패하는 테스트 먼저

```kotlin
// RiskCalculatorTest.kt

class RiskCalculatorTest {

    private lateinit var calculator: RiskCalculator

    @Before
    fun setUp() {
        calculator = RiskCalculator()
    }

    // 테스트 1: 카메라 MAC + RTSP 포트 → 위험
    @Test
    fun `카메라 OUI와 RTSP 포트 554 개방 시 위험도 70 이상 반환`() {
        val layer1Result = Layer1Result(
            macRisk = 0.95,  // Hikvision OUI
            openPorts = listOf(554),  // RTSP 포트
            mdnsFound = false
        )

        val score = calculator.calculateLayer1Score(layer1Result)

        assertThat(score).isAtLeast(70)
    }

    // 테스트 2: 안전 MAC + 포트 닫힘 → 안전
    @Test
    fun `Apple MAC과 모든 포트 닫힘 시 위험도 5 이하 반환`() {
        val layer1Result = Layer1Result(
            macRisk = 0.05,  // Apple OUI
            openPorts = emptyList(),
            mdnsFound = false
        )

        val score = calculator.calculateLayer1Score(layer1Result)

        assertThat(score).isAtMost(5)
    }

    // 테스트 3: 경계값 — 점수 0 이하 불가
    @Test
    fun `모든 지표가 낮아도 점수는 0 이상`() {
        val layer1Result = Layer1Result(
            macRisk = 0.0,
            openPorts = emptyList(),
            mdnsFound = false
        )

        val score = calculator.calculateLayer1Score(layer1Result)

        assertThat(score).isAtLeast(0)
    }

    // 테스트 4: 경계값 — 점수 100 초과 불가
    @Test
    fun `모든 지표가 만점이어도 점수는 100 이하`() {
        val layer1Result = Layer1Result(
            macRisk = 1.0,
            openPorts = listOf(554, 80, 8080, 3702, 1935),
            mdnsFound = true
        )

        val score = calculator.calculateLayer1Score(layer1Result)

        assertThat(score).isAtMost(100)
    }
}
```

이 테스트들은 `RiskCalculator` 클래스가 아직 없으므로 컴파일조차 안 됩니다. 이것이 RED 상태입니다.

### Step 2: GREEN — 최소 구현

```kotlin
// RiskCalculator.kt

class RiskCalculator {

    fun calculateLayer1Score(result: Layer1Result): Int {
        var score = 0.0

        // MAC 위험도 가중치 (40점 만점)
        score += result.macRisk * 40

        // RTSP 포트 (554) 개방 시 +30점
        if (554 in result.openPorts) score += 30

        // HTTP 포트 개방 시 +15점
        if (80 in result.openPorts || 8080 in result.openPorts) score += 15

        // ONVIF 포트 개방 시 +20점
        if (3702 in result.openPorts) score += 20

        // mDNS로 카메라 서비스 발견 시 +25점
        if (result.mdnsFound) score += 25

        // 0~100 범위 클램프
        return score.toInt().coerceIn(0, 100)
    }
}
```

테스트 4개가 모두 통과하면 GREEN입니다.

### Step 3: IMPROVE — 리팩토링

```kotlin
// 상수를 명명하고, 점수 계산 로직을 명확하게 분리
class RiskCalculator {

    companion object {
        private const val MAC_RISK_WEIGHT = 40.0
        private const val RTSP_PORT_SCORE = 30
        private const val HTTP_PORT_SCORE = 15
        private const val ONVIF_PORT_SCORE = 20
        private const val MDNS_SCORE = 25
    }

    fun calculateLayer1Score(result: Layer1Result): Int {
        val macScore = result.macRisk * MAC_RISK_WEIGHT
        val portScore = calculatePortScore(result.openPorts)
        val mdnsScore = if (result.mdnsFound) MDNS_SCORE else 0

        val total = macScore + portScore + mdnsScore
        return total.toInt().coerceIn(0, 100)
    }

    private fun calculatePortScore(openPorts: List<Int>): Int {
        var score = 0
        if (554 in openPorts) score += RTSP_PORT_SCORE
        if (80 in openPorts || 8080 in openPorts) score += HTTP_PORT_SCORE
        if (3702 in openPorts) score += ONVIF_PORT_SCORE
        return score
    }
}
```

리팩토링 후에도 테스트가 모두 통과하면 IMPROVE 완료입니다.

---

## 7.3 MockK — Kotlin 스러운 모킹

Mockito는 Java 스타일의 모킹 라이브러리입니다. Kotlin에서 쓰면 `any()`, `verify()` 등의 표현이 어색합니다. MockK는 Kotlin 언어 특성에 맞게 설계된 모킹 라이브러리입니다.

### MockK로 의존성 격리

UseCase 테스트에서 Repository를 실제로 구현하면 테스트가 DB, 네트워크 등 외부 요소에 의존합니다. MockK로 가짜 Repository를 만들면 순수한 로직만 테스트할 수 있습니다.

```kotlin
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

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        useCase = RunQuickScanUseCase(wifiScanner, ouiDatabase, portScanner, riskCalculator)
    }

    @Test
    fun `Wi-Fi 연결 상태에서 정상 스캔 시 ScanResult 반환`() = runTest {
        // Given
        val mockDevices = listOf(
            NetworkDevice(ip = "192.168.1.100", mac = "28:57:BE:AA:BB:CC")
        )
        val mockOuiInfo = OuiInfo(manufacturer = "Hikvision", riskWeight = 0.95f)

        coEvery { wifiScanner.scanDevices() } returns mockDevices
        every { ouiDatabase.lookup("28:57:BE") } returns mockOuiInfo
        coEvery { portScanner.scanPorts("192.168.1.100") } returns listOf(554, 80)
        every { riskCalculator.calculateLayer1Score(any()) } returns 75

        // When
        val result = useCase.execute()

        // Then
        assertThat(result).isNotNull()
        assertThat(result.overallScore).isEqualTo(75)
        assertThat(result.suspiciousDevices).hasSize(1)

        // 검증: 모든 의존성이 정확히 호출되었는가
        coVerify(exactly = 1) { wifiScanner.scanDevices() }
        coVerify(exactly = 1) { portScanner.scanPorts("192.168.1.100") }
    }

    @Test
    fun `Wi-Fi 미연결 시 Layer1 스킵하고 안전 결과 반환`() = runTest {
        // Given
        coEvery { wifiScanner.scanDevices() } throws WifiNotConnectedException()

        // When
        val result = useCase.execute()

        // Then
        assertThat(result.layer1Skipped).isTrue()
        assertThat(result.overallScore).isLessThan(20)

        // 포트 스캐너는 호출되지 않아야 함
        coVerify(exactly = 0) { portScanner.scanPorts(any()) }
    }
}
```

`coEvery`와 `coVerify`는 `suspend` 함수를 위한 MockK 표현입니다. Kotlin Coroutines와 자연스럽게 통합됩니다.

---

## 7.4 Turbine — Flow 테스트의 해결사

SearCam의 스캔 진행률은 `Flow<ScanProgressState>`로 방출됩니다. Flow 테스트는 까다롭습니다. `collect`를 시작하면 흐름이 끝날 때까지 기다려야 하기 때문입니다.

Turbine은 Flow 테스트를 위한 라이브러리입니다. "터빈처럼 흐름을 제어한다"는 의미로, `awaitItem()`, `awaitComplete()` 등의 직관적인 API를 제공합니다.

```kotlin
class ScanViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()  // TestDispatcher 설정

    @MockK
    private lateinit var runQuickScanUseCase: RunQuickScanUseCase

    private lateinit var viewModel: ScanViewModel

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        viewModel = ScanViewModel(runQuickScanUseCase)
    }

    @Test
    fun `스캔 시작 시 진행률 0에서 100으로 증가`() = runTest {
        // Given
        coEvery { runQuickScanUseCase.progressFlow } returns flow {
            emit(ScanProgressState.Scanning(remainingSeconds = 30, progress = 0f, layers = emptyList(), devicesFound = 0))
            emit(ScanProgressState.Scanning(remainingSeconds = 15, progress = 0.5f, layers = emptyList(), devicesFound = 3))
            emit(ScanProgressState.Completed(result = mockScanResult(score = 25)))
        }

        // When
        viewModel.startScan()

        // Then — Turbine으로 Flow 항목 순서대로 검증
        viewModel.uiState.test {
            val initialState = awaitItem()
            assertThat(initialState).isInstanceOf(ScanUiState.Scanning::class.java)
            assertThat((initialState as ScanUiState.Scanning).progress).isEqualTo(0f)

            val midState = awaitItem()
            assertThat(midState).isInstanceOf(ScanUiState.Scanning::class.java)
            assertThat((midState as ScanUiState.Scanning).progress).isEqualTo(0.5f)

            val finalState = awaitItem()
            assertThat(finalState).isInstanceOf(ScanUiState.Result::class.java)
            assertThat((finalState as ScanUiState.Result).score).isEqualTo(25)

            awaitComplete()
        }
    }

    @Test
    fun `에러 발생 시 에러 상태로 전환`() = runTest {
        // Given
        coEvery { runQuickScanUseCase.progressFlow } returns flow {
            throw ScanException("E1001", "센서 초기화 실패")
        }

        // When
        viewModel.startScan()

        // Then
        viewModel.uiState.test {
            val errorState = awaitItem()
            assertThat(errorState).isInstanceOf(ScanUiState.Error::class.java)
            assertThat((errorState as ScanUiState.Error).code).isEqualTo("E1001")
        }
    }
}
```

`viewModel.uiState.test { ... }` 블록 안에서 순서대로 항목을 `awaitItem()`으로 받아 검증합니다. Flow가 방출하는 순서가 보장되므로 타이밍 문제가 없습니다.

---

## 7.5 Coroutine 테스트 — 시간을 제어하다

스캔 타임아웃은 30초입니다. 실제 30초를 기다리는 테스트는 실용적이지 않습니다. `kotlinx-coroutines-test`의 `TestDispatcher`와 `advanceTimeBy()`를 사용하면 시간을 빠르게 돌릴 수 있습니다.

```kotlin
class ScanTimeoutTest {

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun `30초 초과 시 TimeoutException 발생`() = runTest(testDispatcher) {
        val scanner = WifiScanner(
            dispatcher = testDispatcher,
            timeoutMs = 30_000L
        )

        // 30초가 지나도 응답 없는 가짜 네트워크
        coEvery { scanner.scanDevices() } coAnswers {
            delay(Long.MAX_VALUE)  // 절대 끝나지 않는 스캔
            emptyList()
        }

        // 30초 진행 (실제로는 즉시)
        val job = launch {
            assertThrows<TimeoutException> {
                scanner.scanDevices()
            }
        }

        advanceTimeBy(30_001)  // 30초 1밀리초 경과
        job.join()
    }
}
```

`advanceTimeBy(30_001)`은 실제로 시간이 흐르는 것이 아니라, TestDispatcher의 가상 시계를 30001ms 앞으로 이동시킵니다. 테스트는 즉시 완료됩니다.

---

## 7.6 에러 코드 체계 테스트

SearCam의 에러는 3자리 접두사로 구분됩니다.

```
E1xxx — 센서 레이어 에러
  E1001: 자기장 센서 없음 (기기 미지원)
  E1002: 카메라 초기화 실패
  E1003: 센서 데이터 수신 타임아웃

E2xxx — 네트워크 레이어 에러
  E2001: Wi-Fi 미연결
  E2002: ARP 테이블 파싱 실패
  E2003: 포트 스캔 타임아웃
  E2004: 네트워크 연결 끊김

E3xxx — 권한 레이어 에러
  E3001: 위치 권한 거부
  E3002: 카메라 권한 거부
  E3003: 권한 영구 거부 (다시 묻지 않음)
```

### 에러 코드별 테스트

```kotlin
class ErrorHandlingTest {

    @Test
    fun `자기장 센서 없는 기기에서 E1001 에러 코드 반환`() {
        // Given: 자력계가 없는 기기 시뮬레이션
        val mockSensorManager = mockk<SensorManager>()
        every {
            mockSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        } returns null

        val magneticSensor = MagneticSensor(mockSensorManager)

        // When
        val exception = assertThrows<SearCamException> {
            magneticSensor.initialize()
        }

        // Then
        assertThat(exception.errorCode).isEqualTo("E1001")
        assertThat(exception.message).contains("자기장 센서")
    }

    @Test
    fun `Wi-Fi 미연결 시 E2001 에러 코드 반환`() {
        // Given
        val mockWifiManager = mockk<WifiManager>()
        every { mockWifiManager.connectionInfo.networkId } returns -1  // -1 = 미연결

        val wifiScanner = WifiScanner(mockWifiManager)

        // When
        val exception = assertThrows<SearCamException> {
            runBlocking { wifiScanner.scanDevices() }
        }

        // Then
        assertThat(exception.errorCode).isEqualTo("E2001")
    }

    @Test
    fun `위치 권한 거부 시 E3001 에러 코드 반환`() {
        // Given
        val mockPermissionChecker = mockk<PermissionChecker>()
        every {
            mockPermissionChecker.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        } returns false

        val useCase = RunQuickScanUseCase(
            permissionChecker = mockPermissionChecker,
            /* 나머지 의존성 */
        )

        // When
        val exception = assertThrows<SearCamException> {
            runBlocking { useCase.execute() }
        }

        // Then
        assertThat(exception.errorCode).isEqualTo("E3001")
    }
}
```

에러 코드 테스트의 핵심은 **에러가 올바른 코드와 함께 발생하는가**를 검증하는 것입니다. 에러 코드가 없으면 사용자에게 "뭔가 잘못됐어요"만 보여줄 수 있고, 개발자도 원인을 파악할 수 없습니다.

---

## 7.7 탐지 정확도 검증 — 알려진 카메라로 테스트

### 테스트 대상 카메라 (실기기)

| # | 카메라 종류 | 탐지 방식 | 목표 탐지율 |
|---|-----------|---------|-----------|
| 1 | Hikvision Wi-Fi IP 카메라 | OUI + RTSP | 85% |
| 2 | Wyze Cam v3 | OUI + HTTP | 80% |
| 3 | IR LED 야간 카메라 | IR 감지 (암실) | 75% |
| 4 | 핀홀 카메라 (유선) | EMF + 렌즈 | 40% |

### OUI 데이터베이스 정확도 테스트

```kotlin
class OuiDatabaseAccuracyTest {

    private lateinit var database: OuiDatabase

    @Before
    fun setUp() {
        database = OuiDatabase.create(testContext)
    }

    @Test
    fun `Hikvision MAC 주소가 높은 위험도로 분류되는가`() {
        // Hikvision의 실제 OUI 접두사들
        val hikVisionOuis = listOf("28:57:BE", "44:19:B6", "BC:AD:28", "C4:2F:90")

        hikVisionOuis.forEach { oui ->
            val info = database.lookup(oui)
            assertThat(info).isNotNull()
            assertThat(info!!.riskWeight).isAtLeast(0.90f)
            assertThat(info.deviceType).isEqualTo(DeviceType.IP_CAMERA)
        }
    }

    @Test
    fun `Apple MAC 주소가 낮은 위험도로 분류되는가`() {
        val appleOuis = listOf("00:03:93", "00:0A:27", "3C:D0:F8")

        appleOuis.forEach { oui ->
            val info = database.lookup(oui)
            assertThat(info).isNotNull()
            assertThat(info!!.riskWeight).isAtMost(0.10f)
            assertThat(info.deviceType).isEqualTo(DeviceType.CONSUMER)
        }
    }

    @Test
    fun `대소문자 구분 없이 MAC 주소 조회 가능`() {
        val upperCase = database.lookup("28:57:BE")
        val lowerCase = database.lookup("28:57:be")
        val mixed = database.lookup("28:57:Be")

        assertThat(upperCase).isEqualTo(lowerCase)
        assertThat(lowerCase).isEqualTo(mixed)
    }

    @Test
    fun `미등록 MAC 주소 조회 시 null 반환`() {
        val result = database.lookup("FF:FF:FF")
        assertThat(result).isNull()
    }
}
```

### CrossValidator 교차 검증 테스트

교차 검증은 SearCam의 핵심 알고리즘입니다. 여러 레이어가 동시에 양성 반응을 보이면 위험도를 더 높게 평가합니다.

```kotlin
class CrossValidatorTest {

    private val validator = CrossValidator()

    @Test
    fun `기본 가중치 — Wi-Fi 연결 시 Layer1 50%, Layer2 35%, Layer3 15%`() {
        val weights = validator.calculateWeights(
            isWifiConnected = true,
            isIrAvailable = true
        )

        assertThat(weights.layer1).isEqualTo(0.50f)
        assertThat(weights.layer2).isEqualTo(0.35f)
        assertThat(weights.layer3).isEqualTo(0.15f)
        // 합계는 항상 1.0
        assertThat(weights.layer1 + weights.layer2 + weights.layer3).isWithin(0.001f).of(1.0f)
    }

    @Test
    fun `Wi-Fi 없을 때 Layer1 제외 후 가중치 재조정`() {
        val weights = validator.calculateWeights(
            isWifiConnected = false,
            isIrAvailable = true
        )

        assertThat(weights.layer1).isEqualTo(0f)
        assertThat(weights.layer2).isEqualTo(0.75f)
        assertThat(weights.layer3).isEqualTo(0.25f)
    }

    @Test
    fun `3개 레이어 모두 양성 시 보정계수 1_5 적용`() {
        val result = validator.applyCorrection(
            positiveLayerCount = 3,
            baseScore = 60
        )

        // 60 * 1.5 = 90, 단 100 초과 불가
        assertThat(result).isEqualTo(90)
    }

    @Test
    fun `2개 레이어 양성 시 보정계수 1_2 적용`() {
        val result = validator.applyCorrection(
            positiveLayerCount = 2,
            baseScore = 50
        )

        // 50 * 1.2 = 60
        assertThat(result).isEqualTo(60)
    }

    @Test
    fun `EMF만 단독 양성 시 보정계수 0_5 적용`() {
        val result = validator.applyCorrection(
            positiveLayerCount = 1,
            positiveLayer = Layer.EMF_ONLY,
            baseScore = 60
        )

        // 60 * 0.5 = 30
        assertThat(result).isEqualTo(30)
    }
}
```

---

## 7.8 Room DB 통합 테스트

단위 테스트에서는 DB를 Mock으로 대체했지만, 통합 테스트에서는 실제 Room DB를 사용합니다. 메모리 내 DB를 사용하면 빠르고 독립적입니다.

```kotlin
@RunWith(AndroidJUnit4::class)
class ReportDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var reportDao: ReportDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        reportDao = db.reportDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `리포트 저장 후 조회 시 동일 데이터 반환`() = runTest {
        // Given
        val report = ReportEntity(
            id = 0,
            timestamp = 1_712_134_320_000L,
            scanType = ScanType.QUICK,
            overallScore = 25,
            locationName = "서울 강남 모텔"
        )

        // When
        val insertedId = reportDao.insert(report)
        val retrieved = reportDao.getById(insertedId)

        // Then
        assertThat(retrieved).isNotNull()
        assertThat(retrieved!!.overallScore).isEqualTo(25)
        assertThat(retrieved.locationName).isEqualTo("서울 강남 모텔")
    }

    @Test
    fun `무료 사용자 리포트 10건 초과 시 가장 오래된 것 삭제`() = runTest {
        // Given: 10개 리포트 삽입
        repeat(10) { i ->
            reportDao.insert(createTestReport(timestamp = i.toLong()))
        }

        // 10개 저장 확인
        assertThat(reportDao.getAll().first()).hasSize(10)

        // When: 11번째 저장
        val newestReport = createTestReport(timestamp = 999L, score = 55)
        reportDao.insertWithLimit(newestReport, maxCount = 10)

        // Then: 여전히 10개, 가장 오래된(timestamp=0) 삭제됨
        val reports = reportDao.getAll().first()
        assertThat(reports).hasSize(10)
        assertThat(reports.none { it.id == 0L }).isTrue()
        assertThat(reports.any { it.overallScore == 55 }).isTrue()
    }

    @Test
    fun `리포트 목록은 최신순 정렬`() = runTest {
        // Given
        reportDao.insert(createTestReport(timestamp = 1000L))
        reportDao.insert(createTestReport(timestamp = 3000L))
        reportDao.insert(createTestReport(timestamp = 2000L))

        // When
        val reports = reportDao.getAll().first()

        // Then: 내림차순 (최신 먼저)
        assertThat(reports[0].timestamp).isEqualTo(3000L)
        assertThat(reports[1].timestamp).isEqualTo(2000L)
        assertThat(reports[2].timestamp).isEqualTo(1000L)
    }
}
```

---

## 7.9 Compose UI 테스트

Jetpack Compose는 `createComposeRule()`을 사용한 테스트를 지원합니다. 실제 UI가 올바른 상태를 표시하는지 검증할 수 있습니다.

```kotlin
@RunWith(AndroidJUnit4::class)
class ScanResultScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `안전 등급 결과 화면에서 초록색 게이지와 안전 메시지 표시`() {
        // Given
        val safeResult = ScanResult(overallScore = 12, grade = RiskGrade.SAFE)

        // When
        composeTestRule.setContent {
            SearCamTheme {
                ScanResultScreen(result = safeResult)
            }
        }

        // Then
        composeTestRule
            .onNodeWithText("안전")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("12")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("이번 스캔에서 이상 징후를 발견하지 못했습니다", substring = true)
            .assertIsDisplayed()

        // 한계 고지 문구는 항상 표시되어야 함
        composeTestRule
            .onNodeWithText("이 결과는 참고용입니다", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `위험 등급 결과 화면에서 112 신고 안내 표시`() {
        // Given
        val dangerResult = ScanResult(overallScore = 85, grade = RiskGrade.VERY_DANGEROUS)

        // When
        composeTestRule.setContent {
            SearCamTheme {
                ScanResultScreen(result = dangerResult)
            }
        }

        // Then
        composeTestRule
            .onNodeWithText("매우 위험")
            .assertIsDisplayed()

        // 위험 등급에서는 112 신고 안내 표시
        composeTestRule
            .onNodeWithText("112", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `Quick Scan 후 Full Scan 유도 안내 표시`() {
        // Given
        val quickResult = ScanResult(
            overallScore = 30,
            grade = RiskGrade.ATTENTION,
            scanType = ScanType.QUICK
        )

        // When
        composeTestRule.setContent {
            SearCamTheme {
                ScanResultScreen(result = quickResult)
            }
        }

        // Then: Quick Scan 완료 후 Full Scan 권유
        composeTestRule
            .onNodeWithText("Full Scan", substring = true)
            .assertIsDisplayed()
    }
}
```

---

## 7.10 커버리지 목표와 측정

### 레이어별 커버리지 목표

| 레이어 | 최소 커버리지 | 목표 커버리지 | 이유 |
|--------|------------|------------|------|
| domain/ | 90% | 95% | 핵심 비즈니스 로직, 오류 허용 불가 |
| data/analysis/ | 85% | 90% | 탐지 알고리즘, 정확도 직결 |
| data/sensor/ | 70% | 80% | 하드웨어 의존, 에뮬레이션 한계 |
| data/local/ | 80% | 85% | Room DB CRUD |
| ui/ViewModel | 80% | 85% | UI 로직과 상태 관리 |
| ui/Screen | 60% | 70% | Compose UI, 통합 테스트로 보완 |
| **전체** | **80%** | **85%** | - |

### 커버리지 측정 명령

```bash
# Gradle로 커버리지 리포트 생성
./gradlew testDebugUnitTest jacocoTestReport

# 리포트 위치
# app/build/reports/jacoco/jacocoTestReport/html/index.html
```

### 커버리지가 목표 미달 시

```
80% 미만 → PR 머지 차단 (CI/CD 설정)
80~85% → 경고만, 머지 허용
85% 이상 → 통과
```

```yaml
# .github/workflows/ci.yml (일부)
- name: Check Coverage
  run: |
    coverage=$(./gradlew jacocoTestCoverageVerification 2>&1 | grep "Coverage" | awk '{print $2}')
    echo "Coverage: $coverage"
    if (( $(echo "$coverage < 80" | bc -l) )); then
      echo "Coverage below 80%! Blocking merge."
      exit 1
    fi
```

---

## 7.11 TDD 실전 패턴과 함정

### 함정 1: "이건 단순하니까 테스트 안 해도 돼"

```kotlin
// "단순한" 함수
fun riskGrade(score: Int): RiskGrade = when {
    score < 20 -> RiskGrade.SAFE
    score < 40 -> RiskGrade.ATTENTION
    score < 60 -> RiskGrade.CAUTION
    score < 80 -> RiskGrade.DANGEROUS
    else       -> RiskGrade.VERY_DANGEROUS
}
```

이 함수가 단순해 보여도 경계값(19, 20, 39, 40, 79, 80)에서 버그가 납니다. "< 20"이 "<= 20"으로 잘못 쓰이면 점수 20인 사용자가 "안전"으로 잘못 표시됩니다.

```kotlin
@Test
fun `위험도 등급 경계값 정확성 검증`() {
    // 경계값 아래
    assertThat(riskGrade(19)).isEqualTo(RiskGrade.SAFE)
    assertThat(riskGrade(39)).isEqualTo(RiskGrade.ATTENTION)
    assertThat(riskGrade(59)).isEqualTo(RiskGrade.CAUTION)
    assertThat(riskGrade(79)).isEqualTo(RiskGrade.DANGEROUS)

    // 경계값 위
    assertThat(riskGrade(20)).isEqualTo(RiskGrade.ATTENTION)
    assertThat(riskGrade(40)).isEqualTo(RiskGrade.CAUTION)
    assertThat(riskGrade(60)).isEqualTo(RiskGrade.DANGEROUS)
    assertThat(riskGrade(80)).isEqualTo(RiskGrade.VERY_DANGEROUS)

    // 극단값
    assertThat(riskGrade(0)).isEqualTo(RiskGrade.SAFE)
    assertThat(riskGrade(100)).isEqualTo(RiskGrade.VERY_DANGEROUS)
}
```

### 함정 2: 테스트가 구현 세부사항에 의존

```kotlin
// BAD: 구현 세부사항 검증 (내부 메서드 호출 횟수)
@Test
fun `BAD — calculatePortScore 3번 호출 확인`() {
    verify(exactly = 3) { calculator.calculatePortScore(any()) }
    // 리팩토링하면 테스트 실패
}

// GOOD: 행동 결과 검증
@Test
fun `GOOD — 카메라 포트 3개 개방 시 올바른 점수`() {
    val result = calculator.calculateLayer1Score(
        openPorts = listOf(554, 3702, 80)
    )
    assertThat(result).isAtLeast(60)
    // 내부 구현이 바뀌어도 결과가 맞으면 통과
}
```

### 함정 3: 비결정적 테스트 (Flaky Tests)

```kotlin
// BAD: System.currentTimeMillis() 직접 사용 → 실행 환경마다 다른 결과
@Test
fun `BAD — 현재 시간 기반 리포트 이름`() {
    val reportName = generateReportName()
    assertThat(reportName).contains("2026")  // 2027년에 실패
}

// GOOD: 시간을 파라미터로 주입
@Test
fun `GOOD — 지정된 시간 기반 리포트 이름`() {
    val fixedTime = Instant.parse("2026-04-03T14:32:00Z")
    val reportName = generateReportName(timestamp = fixedTime)
    assertThat(reportName).isEqualTo("SearCam_2026-04-03_14:32.pdf")
}
```

---

## 7.12 E2E 테스트 — 실제 사용자처럼 테스트하기

E2E 테스트는 전체 사용자 플로우를 처음부터 끝까지 검증합니다.

### Quick Scan 전체 플로우

```kotlin
@RunWith(AndroidJUnit4::class)
@LargeTest
class QuickScanE2ETest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun `Quick Scan 시작부터 리포트 저장까지 40초 이내 완료`() {
        val startTime = SystemClock.elapsedRealtime()

        // 홈 화면에서 Quick Scan 버튼 탭
        onView(withText("Quick Scan")).perform(click())

        // 스캔 진행 화면 표시 대기
        onView(withId(R.id.progress_indicator))
            .check(matches(isDisplayed()))

        // 최대 35초 대기 후 결과 화면 확인
        onView(withId(R.id.risk_gauge))
            .withTimeout(35_000)
            .check(matches(isDisplayed()))

        // 리포트 저장 버튼 탭
        onView(withText("리포트 저장")).perform(click())

        // 저장 확인 토스트
        onView(withText("리포트가 저장되었습니다", substring = true))
            .check(matches(isDisplayed()))

        val elapsed = SystemClock.elapsedRealtime() - startTime
        assertThat(elapsed).isLessThan(40_000L)
    }
}
```

---

## 정리: 테스트가 없는 탐지 앱은 장난감

이 장의 결론은 하나입니다. **사용자 안전과 직결된 앱에서 테스트는 선택이 아닙니다.**

위험도 계산 공식이 잘못되면 80점짜리 카메라를 20점으로 잘못 보고할 수 있습니다. 에러 처리가 없으면 Wi-Fi 미연결 상태에서 앱이 죽습니다. 경계값을 검증하지 않으면 점수 20이 "안전"으로 잘못 표시됩니다.

TDD는 이 모든 실수를 코드 작성 단계에서 잡아냅니다. MockK는 외부 의존성을 격리하고, Turbine은 비동기 Flow를 테스트 가능하게 만들고, TestDispatcher는 시간을 제어합니다.

"이 정도면 동작할 것 같다"는 생각은 탐지 앱에서 허용되지 않습니다. 테스트가 통과했다는 증거만이 "동작한다"를 증명합니다.

---

## 테스트 체크리스트

- [ ] 도메인 레이어 커버리지 90% 이상
- [ ] 위험도 경계값(19/20, 39/40, 59/60, 79/80) 모두 테스트
- [ ] 에러 코드(E1xxx/E2xxx/E3xxx) 각 1개 이상 테스트
- [ ] Flow 상태 전환이 Turbine으로 검증됨
- [ ] 타임아웃이 TestDispatcher로 검증됨
- [ ] E2E 테스트가 40초 이내 완료됨
- [ ] CI에서 커버리지 80% 미만 시 머지 차단 설정됨
- [ ] 비결정적 테스트(시간, 랜덤) 없음
