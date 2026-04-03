package com.searcam.data.analysis

import com.searcam.domain.model.MagneticReading
import com.searcam.util.Constants
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 자기장 센서 노이즈 필터
 *
 * 세 가지 기능을 제공한다:
 * 1. 이동 평균(Moving Average) 필터 — 각 축을 독립 큐로 평탄화
 * 2. 급변 필터 — 0.3초 내 magnitude 변화 50μT 초과 시 스마트폰 자체 간섭으로 판단
 * 3. 캘리브레이션 — 최초 샘플로 배경 기준선(baseline) 설정 후 delta 계산
 *
 * 불변성: 내부 상태는 synchronized 블록으로 보호한다.
 */
@Singleton
class NoiseFilter @Inject constructor() {

    // ────────────────────────────────────────────────
    // 이동 평균 윈도우 (각 축 독립)
    // ────────────────────────────────────────────────

    // x, y, z 각 축의 이동 평균용 큐 (window = EMF_MOVING_AVG_WINDOW)
    private val windowX = ArrayDeque<Float>(Constants.EMF_MOVING_AVG_WINDOW)
    private val windowY = ArrayDeque<Float>(Constants.EMF_MOVING_AVG_WINDOW)
    private val windowZ = ArrayDeque<Float>(Constants.EMF_MOVING_AVG_WINDOW)

    // ────────────────────────────────────────────────
    // 급변 필터 상태
    // ────────────────────────────────────────────────

    /** 직전 magnitude 값 (급변 감지용) */
    private var previousMagnitude: Float = 0f

    /** 직전 샘플의 타임스탬프 (ms) */
    private var previousTimestamp: Long = 0L

    /** 급변 판정 임계값 (μT) */
    private val SPIKE_THRESHOLD_UT = 50f

    /** 급변 판정 시간 창 (ms) */
    private val SPIKE_TIME_WINDOW_MS = 300L

    // ────────────────────────────────────────────────
    // 캘리브레이션 상태
    // ────────────────────────────────────────────────

    /** 환경 기준 자기장 크기 (μT) — setBaseline() 후 설정됨 */
    private var baseline: Float = 0f

    /** 노이즈 바닥 = 기준 샘플 stdDev * 2 (μT) */
    private var noiseFloor: Float = 0f

    /** 캘리브레이션 완료 여부 */
    private var calibrated: Boolean = false

    // ────────────────────────────────────────────────
    // 공개 API
    // ────────────────────────────────────────────────

    /**
     * 이동 평균 필터와 급변 필터를 적용한 MagneticReading을 반환한다.
     *
     * 급변 감지(0.3초 내 50μT 초과 변화) 시 이전 값을 유지한다.
     * 각 축을 독립적인 큐로 이동 평균 처리한다.
     * delta와 level은 calibrate() 완료 후에만 의미 있는 값을 가진다.
     *
     * @param reading 원시 센서 측정값
     * @return 필터 적용된 MagneticReading (불변)
     */
    fun filter(reading: MagneticReading): MagneticReading = synchronized(this) {
        // 급변 감지: 0.3초 내 magnitude 변화 50μT 초과 → 이전 값 유지
        if (isSpikeDetected(reading.magnitude, reading.timestamp)) {
            Timber.d("급변 감지: magnitude=${reading.magnitude}, 이전 값 유지")
            return@synchronized buildFilteredReading(
                original = reading,
                filteredX = windowX.averageOrDefault(reading.x),
                filteredY = windowY.averageOrDefault(reading.y),
                filteredZ = windowZ.averageOrDefault(reading.z),
            )
        }

        // 이전 값 갱신
        previousMagnitude = reading.magnitude
        previousTimestamp = reading.timestamp

        // 이동 평균 큐 업데이트 (각 축 독립)
        pushToWindow(windowX, reading.x)
        pushToWindow(windowY, reading.y)
        pushToWindow(windowZ, reading.z)

        val smoothedX = windowX.average().toFloat()
        val smoothedY = windowY.average().toFloat()
        val smoothedZ = windowZ.average().toFloat()

        buildFilteredReading(
            original = reading,
            filteredX = smoothedX,
            filteredY = smoothedY,
            filteredZ = smoothedZ,
        )
    }

    /**
     * 초기 샘플로 환경 기준선(baseline)을 설정한다.
     *
     * baseline = 샘플 magnitude 평균
     * noiseFloor = 샘플 magnitude stdDev * 2
     *
     * @param readings 캘리브레이션용 원시 샘플 (최소 1개 이상)
     */
    fun setBaseline(readings: List<MagneticReading>): Unit = synchronized(this) {
        if (readings.isEmpty()) {
            Timber.e("캘리브레이션 샘플이 비어 있습니다 — baseline 설정 무시")
            return@synchronized
        }

        val magnitudes = readings.map { it.magnitude }
        val mean = magnitudes.average().toFloat()
        val stdDev = magnitudes.stdDev()

        baseline = mean
        noiseFloor = stdDev * 2f
        calibrated = true

        // 캘리브레이션 후 이동 평균 큐 초기화
        windowX.clear()
        windowY.clear()
        windowZ.clear()

        Timber.d("캘리브레이션 완료 — baseline=${"%.2f".format(baseline)}μT, noiseFloor=${"%.2f".format(noiseFloor)}μT")
    }

    /**
     * 캘리브레이션 기준선과의 delta를 계산한다.
     *
     * noiseFloor 미만인 미세 변화는 0으로 처리(노이즈 제거)한다.
     * calibrate() 호출 전에는 0을 반환한다.
     *
     * @param reading 필터 적용된 MagneticReading
     * @return baseline 대비 magnitude 차이 (μT), 항상 0 이상
     */
    fun getDelta(reading: MagneticReading): Float = synchronized(this) {
        if (!calibrated) return@synchronized 0f

        val rawDelta = abs(reading.magnitude - baseline)
        // noiseFloor 미만은 노이즈로 간주 → 0 처리
        return@synchronized if (rawDelta < noiseFloor) 0f else rawDelta
    }

    /**
     * 캘리브레이션 완료 여부를 반환한다.
     */
    fun isCalibrated(): Boolean = synchronized(this) { calibrated }

    /**
     * 캘리브레이션 기준선 값을 반환한다 (μT).
     */
    fun getBaseline(): Float = synchronized(this) { baseline }

    /**
     * 노이즈 바닥 값을 반환한다 (μT).
     */
    fun getNoiseFloor(): Float = synchronized(this) { noiseFloor }

    /**
     * 내부 상태를 초기화한다 (재캘리브레이션 전 호출).
     */
    fun reset(): Unit = synchronized(this) {
        windowX.clear()
        windowY.clear()
        windowZ.clear()
        previousMagnitude = 0f
        previousTimestamp = 0L
        baseline = 0f
        noiseFloor = 0f
        calibrated = false
        Timber.d("NoiseFilter 상태 초기화 완료")
    }

    // ────────────────────────────────────────────────
    // Private helpers
    // ────────────────────────────────────────────────

    /**
     * 급변 여부를 감지한다.
     *
     * 조건: 직전 샘플과의 magnitude 차이 > 50μT AND 경과 시간 < 300ms
     */
    private fun isSpikeDetected(magnitude: Float, timestamp: Long): Boolean {
        if (previousTimestamp == 0L) return false // 첫 샘플은 급변 아님
        val timeDiff = timestamp - previousTimestamp
        val magnitudeDiff = abs(magnitude - previousMagnitude)
        return magnitudeDiff > SPIKE_THRESHOLD_UT && timeDiff < SPIKE_TIME_WINDOW_MS
    }

    /**
     * 이동 평균 큐에 새 값을 추가한다.
     *
     * 윈도우 크기를 초과하면 가장 오래된 값을 제거한다.
     */
    private fun pushToWindow(window: ArrayDeque<Float>, value: Float) {
        if (window.size >= Constants.EMF_MOVING_AVG_WINDOW) {
            window.removeFirst()
        }
        window.addLast(value)
    }

    /**
     * 큐가 비어 있을 경우 기본값을 반환하는 평균 계산.
     */
    private fun ArrayDeque<Float>.averageOrDefault(default: Float): Float =
        if (isEmpty()) default else average().toFloat()

    /**
     * 필터 적용된 x, y, z 값으로 새 MagneticReading을 생성한다.
     *
     * magnitude는 필터 적용 좌표값 기반으로 재계산한다.
     * delta와 level은 Repository에서 별도 설정한다.
     */
    private fun buildFilteredReading(
        original: MagneticReading,
        filteredX: Float,
        filteredY: Float,
        filteredZ: Float,
    ): MagneticReading {
        val smoothedMagnitude = sqrt(filteredX * filteredX + filteredY * filteredY + filteredZ * filteredZ)
        return original.copy(
            x = filteredX,
            y = filteredY,
            z = filteredZ,
            magnitude = smoothedMagnitude,
        )
    }

    /**
     * List<Float>의 표준편차를 계산한다.
     */
    private fun List<Float>.stdDev(): Float {
        if (size < 2) return 0f
        val mean = average().toFloat()
        val variance = sumOf { ((it - mean) * (it - mean)).toDouble() } / size
        return sqrt(variance.toFloat())
    }
}
