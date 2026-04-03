package com.searcam.domain.repository

import com.searcam.domain.model.RetroreflectionPoint
import kotlinx.coroutines.flow.Flow

/**
 * Retroreflection 기반 렌즈 감지 저장소 인터페이스 (Layer 2A)
 *
 * 플래시를 켠 상태에서 CameraX로 프레임을 분석하여 역반사 포인트를 감지한다.
 * 구현체는 data 레이어의 LensDetectionRepositoryImpl에 위치한다.
 *
 * 사용법:
 *   1. startDetection() 호출
 *   2. observeRetroreflections()로 결과 수신
 *   3. stopDetection() 호출로 카메라 및 플래시 해제
 */
interface LensDetectionRepository {

    /**
     * Retroreflection 감지 포인트를 실시간으로 관찰하는 Flow를 반환한다.
     *
     * startDetection() 이후 프레임마다 감지된 포인트 목록을 emit한다.
     * 포인트가 없는 프레임에는 빈 목록을 emit한다.
     * 구독 취소 시 startDetection() 이전 상태로 자동 복귀하지 않는다 —
     * 반드시 stopDetection()을 별도로 호출해야 한다.
     *
     * @return List<RetroreflectionPoint>를 실시간으로 emit하는 Flow
     */
    fun observeRetroreflections(): Flow<List<RetroreflectionPoint>>

    /**
     * 렌즈 감지를 시작한다.
     *
     * 후면 카메라를 열고 플래시를 켠다.
     * 이미 실행 중이면 아무 동작도 하지 않는다.
     *
     * @return Result.success(Unit) 또는 Result.failure(exception)
     */
    suspend fun startDetection(): Result<Unit>

    /**
     * 렌즈 감지를 중단하고 카메라 리소스를 해제한다.
     *
     * 플래시를 끄고 CameraX 바인딩을 해제한다.
     * 이미 중단된 상태이면 아무 동작도 하지 않는다.
     *
     * @return Result.success(Unit) 또는 Result.failure(exception)
     */
    suspend fun stopDetection(): Result<Unit>
}
