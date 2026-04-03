# Ch13: EMF 감지 — 전자기장의 작은 목소리를 듣다

> **이 장에서 배울 것**: 탐지 가중치 15%를 차지하는 EMF 레이어의 원리와 한계를 배웁니다. SensorManager TYPE_MAGNETIC_FIELD를 이용한 20Hz 수집, 이동 평균 노이즈 필터, 캘리브레이션 구현 — 그리고 이 레이어가 절대 단독으로 사용될 수 없는 이유까지 솔직하게 다룹니다.

---

## 도입

지뢰 탐지기는 땅 속에 묻힌 금속의 전자기 특성을 읽어냅니다. 엄청난 고출력 신호를 쏘아서 반응을 감지하죠. 스마트폰 자력계는 그 반대입니다 — 아무것도 발사하지 않고, 그냥 주변의 자기장 변화를 조용히 듣습니다.

IC 보드, 모터, 무선 송신기를 포함한 전자 기기는 주변에 전자기장을 만들어냅니다. 스마트폰 자력계가 아주 민감하다면 숨겨진 카메라 주변의 자기장 변화를 감지할 수 있을까요? 이론적으로는 가능합니다.

하지만 현실은 훨씬 복잡합니다. 이 장에서는 EMF 탐지의 가능성과 한계를 정직하게 살펴봅니다.

---

## 13.1 전자기장(EMF) 탐지의 원리와 한계

### 어떻게 탐지하는가

카메라를 비롯한 전자 기기는 동작 중 세 가지 방식으로 전자기장을 발생시킵니다.

1. **전원 회로**: 배터리 충전, 전압 변환기에서 자기장 발생
2. **무선 통신**: Wi-Fi, Bluetooth 모듈이 RF 신호 방출
3. **모터/구동계**: 팬이 있는 기기는 회전 모터의 자기장 방출

스마트폰의 3축 자력계(Magnetometer)는 TYPE_MAGNETIC_FIELD 센서로 x, y, z축의 자기장 강도를 마이크로테슬라(μT) 단위로 측정합니다.

### 현실적인 한계

```
EMF 탐지로 가능한 것:
  ✅ 전자 기기가 근처에 있을 때의 자기장 변화 감지 (10~30cm 이내)
  ✅ 배경 자기장 대비 이상 수치 탐지

EMF 탐지로 불가능한 것:
  ❌ 특정 기기 종류 식별 (카메라 vs 공유기 vs 충전기)
  ❌ 1m 이상 거리의 소형 기기 탐지
  ❌ 주변 전기 배선, 가전제품 노이즈와 구분
  ❌ 배터리만 사용하는 저전력 카메라 탐지
```

이것이 EMF 레이어에 15%라는 낮은 가중치를 할당한 이유입니다. 단독으로는 신뢰할 수 없지만, Wi-Fi 스캔이나 렌즈 감지와 조합하면 보강 신호가 됩니다.

---

## 13.2 SensorManager TYPE_MAGNETIC_FIELD 20Hz 수집

### Android 자력계 API

Android는 `SensorManager`를 통해 자력계에 접근합니다. `TYPE_MAGNETIC_FIELD` 센서가 3축 자기장을 제공합니다.

```kotlin
// data/sensor/MagneticSensor.kt
class MagneticSensor @Inject constructor(
    private val sensorManager: SensorManager,
    private val noiseFilter: NoiseFilter,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) {
    companion object {
        // 20Hz 샘플링 = SENSOR_DELAY_GAME (약 50ms 간격)
        // SENSOR_DELAY_FASTEST(~1ms)는 배터리 소모가 크고 노이즈가 많아 부적합
        const val SAMPLING_RATE = SensorManager.SENSOR_DELAY_GAME

        // 탐지 임계값 — 배경 대비 이 값 이상 변화하면 이상 신호로 판단
        const val ANOMALY_THRESHOLD_UT = 20f  // 마이크로테슬라
    }

    // 센서 데이터를 Flow로 제공합니다
    fun startMeasurement(): Flow<MagneticReading> = callbackFlow {
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        // 자력계가 없는 기기에서는 빈 Flow (EMF 레이어 비활성화)
        if (magnetometer == null) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_MAGNETIC_FIELD) return

                val rawX = event.values[0]  // x축 자기장 (μT)
                val rawY = event.values[1]  // y축 자기장 (μT)
                val rawZ = event.values[2]  // z축 자기장 (μT)

                // 자기장 벡터의 크기 (magnitude) 계산
                val magnitude = sqrt(rawX * rawX + rawY * rawY + rawZ * rawZ)

                val reading = MagneticReading(
                    timestamp = event.timestamp,
                    x = rawX,
                    y = rawY,
                    z = rawZ,
                    magnitude = magnitude,
                    delta = 0f  // 노이즈 필터 통과 후 계산
                )

                trySend(reading)
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                // 정확도 변화 — 필요 시 처리
            }
        }

        sensorManager.registerListener(
            listener,
            magnetometer,
            SAMPLING_RATE  // 20Hz
        )

        // Flow 취소 시 센서 등록 해제 (배터리 절약)
        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    // 센서 가용성 확인
    fun isAvailable(): Boolean =
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null

    // 센서 정확도 확인 (ACCURACY_HIGH, MEDIUM, LOW, UNRELIABLE)
    fun checkAccuracy(): Int {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            ?: return SensorManager.SENSOR_STATUS_UNRELIABLE
        // 실제 정확도는 onAccuracyChanged 콜백에서 업데이트됨
        return lastAccuracy
    }

    private var lastAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
}
```

---

## 13.3 이동 평균(Moving Average) 노이즈 필터 구현

### 왜 노이즈 필터가 필요한가

자력계 원시 데이터는 매우 불안정합니다. 스마트폰 자체의 전기 회로, 사용자의 움직임, 주변 금속의 영향으로 값이 튀는 **노이즈**가 많습니다. 이를 그대로 사용하면 아무것도 없는데도 경보가 울립니다.

이동 평균 필터는 최근 N개 값의 평균을 사용하는 가장 단순하면서도 효과적인 필터입니다. 잠깐 튀는 노이즈는 평균에 묻히고, 진짜 이상 신호는 여러 샘플에 걸쳐 지속됩니다.

```kotlin
// data/analysis/NoiseFilter.kt
class NoiseFilter @Inject constructor() {
    companion object {
        const val WINDOW_SIZE = 10  // 이동 평균 윈도우 크기 (10샘플 = 0.5초 @20Hz)
        const val SPIKE_THRESHOLD_UT = 50f  // 0.3초 내 50μT 이상 급변 = 스파이크 (제거)
        const val SPIKE_WINDOW_FRAMES = 6   // 0.3초에 해당하는 프레임 수 (20Hz × 0.3s)
    }

    // 최근 WINDOW_SIZE 개의 magnitude 값을 저장하는 원형 버퍼
    private val magnitudeBuffer = ArrayDeque<Float>(WINDOW_SIZE)
    private var smoothedMagnitude = 0f

    // 원시 자기장 측정값에 노이즈 필터를 적용합니다
    fun filter(reading: MagneticReading): MagneticReading {
        val magnitude = reading.magnitude

        // 스파이크 감지: 최근 6프레임 내에서 50μT 이상 갑자기 변했으면 제거합니다
        if (isSpike(magnitude)) {
            // 스파이크는 버퍼에 추가하지 않고 이전 평균값으로 대체합니다
            return reading.copy(
                magnitude = smoothedMagnitude,
                delta = 0f
            )
        }

        // 이동 평균 버퍼에 추가
        magnitudeBuffer.addLast(magnitude)
        if (magnitudeBuffer.size > WINDOW_SIZE) {
            magnitudeBuffer.removeFirst()
        }

        // 새 이동 평균 계산
        val newSmoothed = magnitudeBuffer.average().toFloat()
        val delta = newSmoothed - smoothedMagnitude
        smoothedMagnitude = newSmoothed

        return reading.copy(
            magnitude = smoothedMagnitude,
            delta = delta
        )
    }

    // 스파이크 감지 — 최근 평균 대비 급격한 변화를 탐지합니다
    private fun isSpike(newMagnitude: Float): Boolean {
        if (magnitudeBuffer.size < SPIKE_WINDOW_FRAMES) return false
        val recentAvg = magnitudeBuffer.takeLast(SPIKE_WINDOW_FRAMES).average().toFloat()
        return abs(newMagnitude - recentAvg) > SPIKE_THRESHOLD_UT
    }

    // 필터 상태를 초기화합니다 (새 스캔 시작 시 호출)
    fun reset() {
        magnitudeBuffer.clear()
        smoothedMagnitude = 0f
    }
}
```

### 이동 평균의 트레이드오프

윈도우 크기가 클수록 노이즈가 줄지만 반응이 느려집니다. 작을수록 노이즈에 민감하지만 실제 신호에 빠르게 반응합니다.

| 윈도우 크기 | 대기 시간 | 노이즈 감쇠 | 적합한 용도 |
|------------|---------|-----------|-----------|
| 5 (0.25초) | 빠름 | 낮음 | 빠른 움직임 탐지 |
| 10 (0.5초) | 보통 | 보통 | SearCam 선택값 |
| 20 (1.0초) | 느림 | 높음 | 안정적 장소 측정 |

SearCam은 10을 선택했습니다. 30초 스캔 중에 스마트폰 움직임이 있을 수 있어 너무 느린 필터는 부적합하고, 너무 빠른 필터는 사용자의 움직임을 오탐할 수 있기 때문입니다.

---

## 13.4 캘리브레이션 — 배경 기준선 설정

### 왜 캘리브레이션이 필요한가

지구 자체가 자기장을 가지고 있습니다. 위치에 따라 30~60μT 사이입니다. 이 배경 자기장이 없다면 어떤 값이 "이상"인지 판단할 수 없습니다.

캘리브레이션은 스캔 시작 전 3초 동안 "지금 이 환경의 정상 자기장"을 측정합니다. 이후 이 값보다 크게 벗어나는 측정값을 이상 신호로 판단합니다.

```kotlin
// domain/usecase/CalibrateEmfUseCase.kt
class CalibrateEmfUseCase @Inject constructor(
    private val magneticRepository: MagneticRepository
) {
    companion object {
        const val CALIBRATION_DURATION_MS = 3000L   // 3초간 캘리브레이션
        const val CALIBRATION_SAMPLES = 60           // 20Hz × 3초 = 60 샘플
    }

    // 배경 기준선(baseline)과 노이즈 바닥(noise_floor)을 계산합니다
    suspend operator fun invoke(): CalibrationResult {
        val samples = magneticRepository.collectSamples(CALIBRATION_SAMPLES)

        if (samples.isEmpty()) {
            return CalibrationResult.Unavailable
        }

        val magnitudes = samples.map { it.magnitude }
        val baseline = magnitudes.average().toFloat()
        val stdDev = calculateStdDev(magnitudes, baseline)

        // 노이즈 바닥 = 평균 ± 2 표준편차 (95% 신뢰 구간)
        val noiseFloor = stdDev * 2f

        return CalibrationResult.Success(
            baseline = baseline,
            noiseFloor = noiseFloor,
            // 탐지 임계값 = 기준선 + 노이즈 바닥 + 20μT 안전 마진
            detectionThreshold = baseline + noiseFloor + 20f
        )
    }

    private fun calculateStdDev(values: List<Float>, mean: Float): Float {
        val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
        return sqrt(variance)
    }
}

// 캘리브레이션 결과 — 불변 sealed class
sealed class CalibrationResult {
    data class Success(
        val baseline: Float,       // 배경 자기장 기준선 (μT)
        val noiseFloor: Float,     // 노이즈 바닥 (μT)
        val detectionThreshold: Float  // 이상 신호 임계값 (μT)
    ) : CalibrationResult()

    object Unavailable : CalibrationResult()  // 자력계 없는 기기
}
```

### 캘리브레이션 결과 활용

```kotlin
// EMF 이상 탐지 로직
class EmfAnomalyDetector @Inject constructor(
    private val noiseFilter: NoiseFilter
) {
    private var calibration: CalibrationResult.Success? = null

    fun setCalibration(result: CalibrationResult.Success) {
        calibration = result
    }

    // 측정값이 이상 신호인지 판단합니다
    fun isAnomaly(reading: MagneticReading): EmfAnomaly? {
        val cal = calibration ?: return null  // 캘리브레이션 없으면 판단 불가

        val filtered = noiseFilter.filter(reading)
        val deviation = filtered.magnitude - cal.baseline

        return when {
            deviation < cal.noiseFloor -> null  // 정상 범위 내
            deviation < cal.detectionThreshold -> EmfAnomaly.Weak(
                deviation = deviation,
                riskScore = 15  // 약한 이상 — 보조 신호
            )
            else -> EmfAnomaly.Strong(
                deviation = deviation,
                riskScore = 40  // 강한 이상 — 주목 필요
            )
        }
    }
}

// EMF 이상 신호 — 불변 sealed class
sealed class EmfAnomaly(
    open val deviation: Float,
    open val riskScore: Int
) {
    data class Weak(
        override val deviation: Float,
        override val riskScore: Int
    ) : EmfAnomaly(deviation, riskScore)

    data class Strong(
        override val deviation: Float,
        override val riskScore: Int
    ) : EmfAnomaly(deviation, riskScore)
}
```

---

## 13.5 EMF 단독으로 탐지 불가한 이유 — 보조 레이어 역할

### 일상적인 EMF 발생원

이것이 EMF 레이어의 근본적인 한계입니다. 호텔 방에는 자기장 발생원이 넘칩니다.

| EMF 발생원 | 예상 자기장 강도 |
|-----------|--------------|
| 스마트폰 충전기 | 5~50μT (거리 10cm) |
| 노트북 전원 어댑터 | 10~100μT |
| 에어컨 실내기 | 2~20μT |
| TV | 5~30μT |
| 몰래카메라 (소형) | 1~10μT |

몰래카메라가 오히려 충전기보다 약한 신호를 냅니다. EMF만으로는 "방에 카메라가 있다"고 말할 수 없습니다.

### 올바른 사용 방법 — 교차 검증 보강재

```
잘못된 사용:
  EMF 이상 감지 → "카메라 발견!" (오탐률 90%+)

올바른 사용:
  Wi-Fi 스캔: 카메라 의심 기기 발견 (70점)
  + 렌즈 감지: 역반사 포인트 1개 (80점)
  + EMF 이상: 해당 방향에서 자기장 상승 (+보정)
  → 교차 검증 엔진: 종합 위험도 85점
```

EMF 단독 감지에서는 "자기장 이상" 정도만 알려줍니다. 다른 레이어와 결합할 때 의미 있는 신호가 됩니다.

### UI에서의 투명한 표시

```kotlin
// ui/magnetic/MagneticViewModel.kt
class MagneticViewModel @Inject constructor(
    private val magneticRepository: MagneticRepository,
    private val calibrateEmfUseCase: CalibrateEmfUseCase
) : ViewModel() {

    fun buildEmfMessage(anomaly: EmfAnomaly?): String = when (anomaly) {
        null -> "자기장 정상 — 배경 수준 내"
        is EmfAnomaly.Weak ->
            // 솔직하게 한계를 알려줍니다
            "자기장 약한 이상 (+${anomaly.deviation.toInt()}μT)\n" +
            "주의: 충전기, 전자제품도 유사한 반응을 보일 수 있습니다"
        is EmfAnomaly.Strong ->
            "자기장 강한 이상 (+${anomaly.deviation.toInt()}μT)\n" +
            "다른 탐지 레이어 결과와 함께 확인하세요"
    }
}
```

---

## 13.6 전체 EMF 레이어 통합

```kotlin
// domain/repository/MagneticRepository.kt (인터페이스)
interface MagneticRepository {
    fun startMeasurement(): Flow<MagneticReading>
    suspend fun collectSamples(count: Int): List<MagneticReading>
    fun isAvailable(): Boolean
}

// data/repository/MagneticRepositoryImpl.kt (구현체)
class MagneticRepositoryImpl @Inject constructor(
    private val magneticSensor: MagneticSensor,
    private val noiseFilter: NoiseFilter,
    private val emfAnomalyDetector: EmfAnomalyDetector,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) : MagneticRepository {

    override fun startMeasurement(): Flow<MagneticReading> =
        magneticSensor.startMeasurement()
            .map { reading -> noiseFilter.filter(reading) }  // 노이즈 필터 적용
            .flowOn(dispatcher)

    // 캘리브레이션용 샘플 수집
    override suspend fun collectSamples(count: Int): List<MagneticReading> =
        startMeasurement()
            .take(count)
            .toList()

    override fun isAvailable(): Boolean = magneticSensor.isAvailable()
}
```

---

## 실습

> **실습 13-1**: 스마트폰을 테이블 위에 평평하게 놓고, 스마트폰 자력계 앱(Physics Toolbox)으로 자기장 기준선을 측정해보세요. 그 다음 충전기를 가까이 가져가면서 자기장이 얼마나 변하는지 확인해보세요.

> **실습 13-2**: `NoiseFilter`에서 윈도우 크기를 5와 20으로 바꿔가며 스마트폰을 흔들어보세요. 움직임 노이즈가 필터를 통과하는 차이를 로그로 확인해보세요.

---

## 핵심 정리

| 구성 요소 | 역할 |
|---------|------|
| TYPE_MAGNETIC_FIELD | 3축 자기장 20Hz 수집 |
| NoiseFilter | 이동 평균 + 스파이크 제거 |
| CalibrateEmfUseCase | 배경 기준선 + 임계값 산출 |
| EmfAnomalyDetector | 이상 신호 판단 |

- EMF 레이어 가중치 15%는 단독 탐지 불가 + 교차 보강 역할을 반영한다
- 캘리브레이션 없이는 어떤 값이 이상인지 판단할 수 없다
- 이동 평균은 가장 단순한 노이즈 필터 — 단순함이 곧 신뢰성이다
- 한계를 솔직하게 표시하는 것이 오탐보다 낫다

---

## 다음 장 예고

세 개의 탐지 레이어를 모두 만들었습니다. 이제 가장 중요한 질문이 남았습니다 — 이 세 레이어의 결과를 어떻게 합쳐 "위험도 0~100"이라는 하나의 숫자로 만들까요? Ch14에서는 CrossValidator 설계와 위험도 산출 알고리즘을 다룹니다.

---
*참고 문서: docs/02-TRD.md, docs/03-TDD.md*
