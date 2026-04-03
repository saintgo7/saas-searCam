package com.searcam.data.repository

import com.searcam.data.analysis.NoiseFilter
import com.searcam.data.sensor.MagneticSensor
import com.searcam.domain.model.EmfLevel
import com.searcam.domain.model.MagneticReading
import com.searcam.domain.repository.MagneticRepository
import com.searcam.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MagneticRepository 구현체
 *
 * MagneticSensor(하드웨어 수집)와 NoiseFilter(알고리즘 처리)를 조합하여
 * 정제된 자기장 측정 Flow를 도메인 레이어에 제공한다.
 *
 * 책임:
 *   - MagneticSensor.flow() → NoiseFilter.filter() → delta 계산 → EmfLevel 판정
 *   - calibrate(): EMF_BASELINE_SAMPLES개 샘플 수집 후 NoiseFilter.setBaseline() 호출
 *   - getCurrentReading(): Flow에서 첫 번째 값을 수집 후 정제하여 반환
 */
@Singleton
class MagneticRepositoryImpl @Inject constructor(
    private val magneticSensor: MagneticSensor,
    private val noiseFilter: NoiseFilter,
) : MagneticRepository {

    /**
     * 노이즈 필터와 delta/level 보정이 완료된 자기장 측정 Flow를 반환한다.
     *
     * 파이프라인:
     *   [센서 원시값] → [이동 평균 + 급변 필터] → [delta 계산] → [EmfLevel 판정] → emit
     *
     * magnitude 계산은 Dispatchers.Default에서 수행한다.
     * SensorManager 콜백(Main)에서 callbackFlow로 수신된 값을 즉시 처리한다.
     *
     * @return 정제된 MagneticReading을 실시간으로 emit하는 Flow
     * @throws IllegalStateException E1001: 자력계 미지원 기기
     * @throws IllegalStateException E1002: 센서 리스너 등록 실패
     */
    override fun observeReadings(): Flow<MagneticReading> =
        magneticSensor.flow()
            .map { rawReading ->
                withContext(Dispatchers.Default) {
                    processReading(rawReading)
                }
            }

    /**
     * 환경 기준선 캘리브레이션을 수행한다.
     *
     * EMF_BASELINE_SAMPLES(30개) 샘플을 수집하고 NoiseFilter에 baseline을 설정한다.
     * 이후 observeReadings()에서 반환되는 delta 값이 baseline 기준으로 계산된다.
     *
     * 캘리브레이션 전에는 delta=0, level=NORMAL이 반환된다.
     *
     * @return Result.success(Unit) — 성공
     *         Result.failure(exception) — 센서 없음 또는 샘플 수집 실패
     */
    override suspend fun calibrate(): Result<Unit> = withContext(Dispatchers.Default) {
        if (!magneticSensor.isAvailable()) {
            val msg = "[${Constants.ErrorCode.E1001}] 자력계 미지원 기기 — 캘리브레이션 불가"
            Timber.e(msg)
            return@withContext Result.failure(IllegalStateException(msg))
        }

        try {
            Timber.d("캘리브레이션 시작: ${Constants.EMF_BASELINE_SAMPLES}개 샘플 수집 중...")

            // NoiseFilter 초기화 후 fresh 상태에서 샘플 수집
            noiseFilter.reset()

            val samples = magneticSensor.flow()
                .take(Constants.EMF_BASELINE_SAMPLES)
                .toList()

            if (samples.isEmpty()) {
                val msg = "[${Constants.ErrorCode.E1005}] 센서 샘플 수집 실패 — 빈 결과"
                Timber.e(msg)
                return@withContext Result.failure(IllegalStateException(msg))
            }

            noiseFilter.setBaseline(samples)

            Timber.d(
                "캘리브레이션 완료 — baseline=${"%.2f".format(noiseFilter.getBaseline())}μT, " +
                    "noiseFloor=${"%.2f".format(noiseFilter.getNoiseFloor())}μT"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "캘리브레이션 중 예외 발생")
            Result.failure(e)
        }
    }

    /**
     * 현재 자기장 측정값을 단발성으로 반환한다.
     *
     * Flow를 구독하지 않고 단일 값이 필요한 경우에 사용한다.
     * 센서에서 첫 번째 값을 수집해 필터와 delta 보정을 적용 후 반환한다.
     *
     * @return Result.success(reading) — 성공
     *         Result.failure(exception) — 센서 오류
     */
    override suspend fun getCurrentReading(): Result<MagneticReading> =
        withContext(Dispatchers.Default) {
            try {
                val rawReading = magneticSensor.flow().first()
                val processed = processReading(rawReading)
                Result.success(processed)
            } catch (e: Exception) {
                Timber.e(e, "getCurrentReading 실패")
                Result.failure(e)
            }
        }

    // ────────────────────────────────────────────────
    // Private helpers
    // ────────────────────────────────────────────────

    /**
     * 원시 센서 값에 필터 + delta 계산 + EmfLevel 판정을 적용한다.
     *
     * 단계:
     *   1. NoiseFilter.filter() → 이동 평균 + 급변 필터 적용
     *   2. NoiseFilter.getDelta() → baseline 대비 변화량 계산
     *   3. EmfLevel.fromDelta() → delta 기반 위험 등급 판정
     *   4. 결과 MagneticReading 조합 (copy() 불변 패턴)
     *
     * @param rawReading 센서에서 수신한 원시 측정값
     * @return 정제된 MagneticReading
     */
    private fun processReading(rawReading: MagneticReading): MagneticReading {
        // 1단계: 이동 평균 + 급변 필터
        val filtered = noiseFilter.filter(rawReading)

        // 2단계: baseline 대비 delta 계산 (캘리브레이션 전 = 0)
        val delta = noiseFilter.getDelta(filtered)

        // 3단계: EmfLevel 판정
        val level = EmfLevel.fromDelta(delta)

        // 4단계: 불변 패턴으로 새 객체 생성
        return filtered.copy(
            delta = delta,
            level = level,
        )
    }
}
