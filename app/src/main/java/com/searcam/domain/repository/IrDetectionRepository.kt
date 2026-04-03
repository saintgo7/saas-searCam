package com.searcam.domain.repository

import com.searcam.domain.model.IrPoint
import kotlinx.coroutines.flow.Flow

/**
 * IR LED 감지 저장소 인터페이스 (Layer 2B)
 *
 * 전면 카메라의 가시광 필터 취약점을 이용해 IR LED를 감지한다.
 * 스마트폰 전면 카메라는 IR 차단 필터가 없거나 약하므로 IR 빛을 자주색/흰색으로 포착한다.
 * 구현체는 data 레이어의 IrDetectionRepositoryImpl에 위치한다.
 *
 * 사용법:
 *   1. startDetection() 호출 (전면 카메라 열기)
 *   2. observeIrPoints()로 결과 수신
 *   3. stopDetection() 호출로 카메라 해제
 */
interface IrDetectionRepository {

    /**
     * IR 포인트를 실시간으로 관찰하는 Flow를 반환한다.
     *
     * startDetection() 이후 프레임마다 감지된 IR 포인트 목록을 emit한다.
     * 포인트가 없는 프레임에는 빈 목록을 emit한다.
     *
     * @return List<IrPoint>를 실시간으로 emit하는 Flow
     */
    fun observeIrPoints(): Flow<List<IrPoint>>

    /**
     * IR 감지를 시작한다.
     *
     * 전면 카메라를 열고 ImageAnalysis 파이프라인을 구성한다.
     * 이미 실행 중이면 아무 동작도 하지 않는다.
     *
     * @return Result.success(Unit) 또는 Result.failure(exception)
     */
    suspend fun startDetection(): Result<Unit>

    /**
     * IR 감지를 중단하고 전면 카메라 리소스를 해제한다.
     *
     * CameraX 바인딩을 해제하고 분석 파이프라인을 종료한다.
     * 이미 중단된 상태이면 아무 동작도 하지 않는다.
     *
     * @return Result.success(Unit) 또는 Result.failure(exception)
     */
    suspend fun stopDetection(): Result<Unit>
}
