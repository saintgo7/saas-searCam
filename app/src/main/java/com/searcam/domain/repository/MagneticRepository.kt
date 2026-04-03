package com.searcam.domain.repository

import com.searcam.domain.model.MagneticReading
import kotlinx.coroutines.flow.Flow

/**
 * 자기장 센서 저장소 인터페이스 (Layer 3)
 *
 * Android SensorManager의 TYPE_MAGNETIC_FIELD 센서를 20Hz로 샘플링한다.
 * 구현체는 data 레이어의 MagneticRepositoryImpl에 위치한다.
 */
interface MagneticRepository {

    /**
     * 자기장 측정값을 실시간으로 관찰하는 Flow를 반환한다.
     *
     * 약 20Hz(50ms 간격)로 x, y, z 3축 측정값을 emit한다.
     * 구독 취소 시 자동으로 센서 리스너를 해제한다.
     *
     * @return MagneticReading을 실시간으로 emit하는 Flow
     */
    fun observeReadings(): Flow<MagneticReading>

    /**
     * 자기장 센서를 현재 환경 기준으로 보정한다.
     *
     * 호출 시점의 평균 자기장 값을 기준점(baseline)으로 저장한다.
     * 이후 observeReadings()는 이 기준점과의 delta를 계산한다.
     * 스캔 시작 전 반드시 호출해야 정확한 delta 값을 얻을 수 있다.
     *
     * @return Result.success(Unit) 또는 Result.failure(exception)
     */
    suspend fun calibrate(): Result<Unit>

    /**
     * 현재 자기장 측정값을 단발성으로 읽어 반환한다.
     *
     * Flow 구독 없이 단일 측정값이 필요할 때 사용한다.
     *
     * @return Result.success(reading) 또는 Result.failure(exception)
     */
    suspend fun getCurrentReading(): Result<MagneticReading>
}
